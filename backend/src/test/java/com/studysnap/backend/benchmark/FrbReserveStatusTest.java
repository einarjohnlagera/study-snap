package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrbReserveStatusTest {
    private static final String TIP = "STUDY_TIP";
    private static final String COLLECTION = "COLLECTION_COMPANION";
    private static final String HUMAN = "HUMAN_OWNER";
    private static final String MODEL = "MODEL_REVIEWER";
    private static final String EXPECTED_CAUSE = "RESERVE_NOT_SCORED";
    private static final String DECIDED = "2026-11-16T00:02:00Z";
    private static final String RESERVE_SEALED = "2026-11-16T00:03:00Z";
    private static final String RESERVE_LOCKED = "2026-11-16T00:04:00Z";
    private static final Path MAPPING = Path.of("src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json");
    private static final Path PARAMETERS = Path.of("src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json");
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS = Map.of(
            HUMAN, ClassBScoreFiles.ScorerKind.HUMAN, MODEL, ClassBScoreFiles.ScorerKind.MODEL);
    @TempDir Path temp;

    private record Fixture(FrbReserveStatus.Input input, Path record, Path primaryScores,
            Map<String, Boolean> decisions) {}

    private Fixture fixture() throws Exception {
        Path root = temp.resolve("primary");
        Files.createDirectories(root);
        ClassBBlindPacketExport.Export export = BlindReviewFixtures.classBAll(root, false);
        PairMap pairs = PairMap.fromPackets(root.resolve("packets"));
        Set<String> critical = Set.of(
                pairs.pairs().stream().filter(pair -> pair.workflow().equals(TIP)).findFirst().orElseThrow()
                        .items().get("A").getFirst(),
                pairs.pairs().stream().filter(pair -> pair.workflow().equals(COLLECTION)).findFirst().orElseThrow()
                        .items().get("A").getFirst());
        Path scores = root.resolve("findings/scores.jsonl");
        writeScores(scores, export.manifest(), critical, HUMAN);
        Path sealFile = root.resolve("seal.json");
        ArmKeySeal.Seal seal = ArmKeySeal.seal(root.resolve("arm-key.jsonl"), root.resolve("packets"),
                sealFile, ArmKeyGateFixtures.SEALED);
        Path lockFile = root.resolve("primary-lock.json");
        LockRecord primary = LockRecord.create(lockFile, LockRecord.Kind.PRIMARY, seal.workflowStems(),
                List.of(scores), seal, sealFile, files -> {}, ArmKeyGateFixtures.LOCKED);
        var validated = ClassBScoreFiles.validate(scores, export.manifest(), SCORERS);
        Path parametersFile = copy(PARAMETERS, "parameters.json");
        Path mappingFile = copy(MAPPING, "mapping.json");
        Set<String> names = FrozenGateParameters.load(parametersFile).frbClassBWorkflows;
        var decisions = ReserveDecisionRecord.compute(root.resolve("packets"), validated,
                EvidenceMapping.load(mappingFile), names);
        Path record = root.resolve("decision.json");
        ReserveDecisionRecord.create(record, decisions, primary, lockFile, scores,
                root.resolve("packets"), sealFile, DECIDED);
        Files.delete(root.resolve("arm-key.jsonl"));
        var check = new ResultsPipeline.ReserveVerification(root.resolve("packets"), sealFile,
                scores, SCORERS, names, lockFile);
        var input = new FrbReserveStatus.Input(record, check, mappingFile, parametersFile, Map.of());
        return new Fixture(input, record, scores, ReserveDecisionRecord.load(record).reserveDrawnByWorkflow());
    }

    private Path copy(Path source, String name) throws Exception {
        Path target = temp.resolve(name);
        Files.copy(source, target);
        Files.copy(IntegritySupport.detached(source), IntegritySupport.detached(target));
        return target;
    }

    private FrbReserveStatus.ReserveArtifacts reserve(String workflow, boolean score,
            boolean firstLock, boolean secondLock, boolean human) throws Exception {
        Path root = temp.resolve("reserve-" + workflow);
        Files.createDirectories(root);
        List<ClassBCaseManifest.Case> cases = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        var context = new CompanionGenerationContext("Collection", "Description", "Program",
                List.of(), List.of(new CompanionGenerationContext.CompanionContextItem("Note", "Description")));
        for (int index = 0; index < 30; index++) {
            String id = workflow + "-reserve-" + index;
            if (workflow.equals(TIP)) cases.add(new ClassBCaseManifest.Case(id,
                    ClassBCaseManifest.Workflow.STUDY_TIP, "synthetic", true, LearnerLevel.COLLEGE,
                    List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                    null, null, null, null, null, null, null, null));
            else cases.add(new ClassBCaseManifest.Case(id,
                    ClassBCaseManifest.Workflow.COLLECTION_COMPANION, "synthetic", true, null, null,
                    List.of(CompanionSection.OVERVIEW), context, null, null, null, null, null, null));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE, "Answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."));
        }
        var export = ClassBBlindPacketExport.export(cases, outputs, root.resolve("packets"),
                root.resolve("arm-key.jsonl"), 19L);
        Path sealFile = root.resolve("seal.json");
        ArmKeySeal.Seal seal = ArmKeySeal.seal(root.resolve("arm-key.jsonl"), root.resolve("packets"),
                sealFile, RESERVE_SEALED);
        Path scores = root.resolve("findings/scores.jsonl");
        if (score) writeScores(scores, List.of(export.manifest().getFirst()), Set.of(), human ? HUMAN : MODEL);
        Path first = root.resolve("reserve-lock.json");
        Path second = root.resolve("primary-lock.json");
        if (firstLock) LockRecord.create(first, LockRecord.Kind.RESERVE, Set.of(workflow), List.of(scores),
                seal, sealFile, files -> {}, RESERVE_LOCKED);
        if (secondLock) LockRecord.create(second, LockRecord.Kind.PRIMARY, Set.of(workflow), List.of(scores),
                seal, sealFile, files -> {}, RESERVE_LOCKED);
        Files.delete(root.resolve("arm-key.jsonl"));
        return new FrbReserveStatus.ReserveArtifacts(root.resolve("packets"), sealFile, scores, SCORERS,
                first, second, scores.getParent());
    }

    private static void writeScores(Path file, List<ClassBScoreFiles.BlindItem> items,
            Set<String> critical, String scorer) throws Exception {
        Files.createDirectories(file.getParent());
        List<String> rows = new ArrayList<>();
        for (ClassBScoreFiles.BlindItem item : items) {
            ObjectNode row = IntegritySupport.JSON.createObjectNode();
            boolean bad = critical.contains(item.itemId());
            row.put("itemId", item.itemId()).put("workflow", item.workflow().name()).put("round", 1)
                    .put("scorerId", scorer).put("scorerKind", scorer.startsWith("HUMAN") ? "HUMAN" : "MODEL")
                    .put("taskFulfilled", true).put("grounded", true).put("actionable", true)
                    .put("noCriticalMisleading", !bad).put("critical", bad)
                    .put("borderline", false).put("adjudication", false);
            if (bad) row.put("reasonCode", "CRITICAL_MISLEADING");
            else row.putNull("reasonCode");
            rows.add(row.toString());
        }
        Files.write(file, rows);
    }

    private FrbReserveStatus.Input with(Fixture fixture, Map<String, FrbReserveStatus.ReserveArtifacts> reserves) {
        var old = fixture.input();
        return new FrbReserveStatus.Input(old.reserveDecisionRecordFile(), old.verification(),
                old.mappingFile(), old.parametersFile(), reserves);
    }
    private FrbReserveStatus.Result status(FrbReserveStatus.Input input, String file) {
        return FrbReserveStatus.write(input, temp.resolve(file));
    }

    private static void rewrite(Path file, Consumer<ObjectNode> edit) throws Exception {
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(file));
        edit.accept(row);
        Files.writeString(file, row.toString());
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(file));
    }

    @Test void absentAndEmptyScoresAreNotScoredWithoutOpeningAnArm() throws Exception {
        Fixture fixture = fixture();
        assertThat(fixture.decisions()).containsEntry(TIP, true).containsEntry(COLLECTION, true);
        byte[] before = Files.readAllBytes(fixture.record());
        var absent = status(fixture.input(), "absent-status.json");
        assertThat(absent.status()).isEqualTo("INCONCLUSIVE");
        assertThat(absent.causes()).containsOnlyKeys(TIP, COLLECTION);
        assertThat(absent.causes().get(TIP)).containsExactly(EXPECTED_CAUSE);
        assertThat(absent.attributable()).isFalse();
        var artifacts = reserve(TIP, true, false, false, true);
        Files.writeString(artifacts.scoresFile(), "");
        var empty = status(with(fixture, Map.of(TIP, artifacts)), "empty-status.json");
        assertThat(empty.causes()).containsKey(TIP);
        assertThat(Files.readAllBytes(fixture.record())).isEqualTo(before);
        assertThat(Files.exists(temp.resolve("primary/access.jsonl"))).isFalse();
        assertThat(Files.exists(temp.resolve("reserve-" + TIP + "/access.jsonl"))).isFalse();
        assertThat(CauseTable.kind(FrbReserveStatus.NOT_SCORED)).isEqualTo(CauseTable.Kind.RETURN_TO_OWNER);
    }

    @Test void modelOnlyScoresDoNotCountButPartialHumanScoresDo() throws Exception {
        Fixture fixture = fixture();
        var model = reserve(TIP, true, false, false, false);
        var modelResult = status(with(fixture, Map.of(TIP, model)), "model-status.json");
        assertThat(modelResult.causes()).containsKey(TIP);
        var human = reserve(COLLECTION, true, false, false, true);
        var partial = status(with(fixture, Map.of(COLLECTION, human)), "partial-status.json");
        assertThat(partial.workflows().get(COLLECTION).scored()).isTrue();
        assertThat(partial.causes()).containsOnlyKeys(TIP);
        assertThat(partial.awaitingLock()).containsExactly(COLLECTION);
    }

    @Test void bothLocksGloballyEnableAttributionAndOneLockDoesNot() throws Exception {
        Fixture fixture = fixture();
        var full = reserve(TIP, true, true, true, true);
        var onlyReserve = reserve(COLLECTION, true, true, false, true);
        var one = status(with(fixture, Map.of(TIP, full, COLLECTION, onlyReserve)), "one-status.json");
        assertThat(one.causes()).isEmpty();
        assertThat(one.awaitingLock()).containsExactly(COLLECTION);
        assertThat(one.attributable()).isFalse();
        Path second = onlyReserve.lockForReserveOpening();
        ArmKeySeal.Seal seal = ArmKeySeal.load(onlyReserve.sealFile());
        LockRecord.create(second, LockRecord.Kind.PRIMARY, Set.of(COLLECTION), List.of(onlyReserve.scoresFile()),
                seal, onlyReserve.sealFile(), files -> {}, RESERVE_LOCKED);
        var both = status(with(fixture, Map.of(TIP, full, COLLECTION, onlyReserve)), "both-status.json");
        assertThat(both.status()).isNull();
        assertThat(both.causes()).isEmpty();
        assertThat(both.attributable()).isTrue();
        assertThat(both.reserveDrawnByWorkflow()).isEqualTo(fixture.decisions());
        assertThat(both.workflows().get("ASK_COMPANION")).isEqualTo(new FrbReserveStatus.Workflow(false, false, false));
        String json = Files.readString(temp.resolve("both-status.json"));
        assertThat(json).doesNotContain("armKeySha256", "BASELINE", "CANDIDATE", "LUNA");
        assertThat(IntegritySupport.JSON.readTree(json).path("workflows").path(TIP).has("attributable")).isFalse();
    }

    @Test void onlyPrimaryKindReserveLockAlsoAwaitsTheOtherLock() throws Exception {
        Fixture fixture = fixture();
        var first = reserve(TIP, true, true, true, true);
        var onlyPrimary = reserve(COLLECTION, true, false, true, true);
        var result = status(with(fixture, Map.of(TIP, first, COLLECTION, onlyPrimary)), "primary-only.json");
        assertThat(result.attributable()).isFalse();
        assertThat(result.causes()).isEmpty();
        assertThat(result.awaitingLock()).containsExactly(COLLECTION);
    }

    @Test void oneScoredAndLockedDoesNotReleaseAnyRouteWhenAnotherIsUnscored() throws Exception {
        Fixture fixture = fixture();
        var full = reserve(TIP, true, true, true, true);
        var result = status(with(fixture, Map.of(TIP, full)), "global-status.json");
        assertThat(result.attributable()).isFalse();
        assertThat(result.causes()).containsOnlyKeys(COLLECTION);
        assertThat(result.workflows().get("ASK_COMPANION").drawn()).isFalse();
        String json = Files.readString(temp.resolve("global-status.json"));
        assertThat(json).doesNotContain("RESERVE_EVIDENCE_UNRESOLVED");
        var parsed = IntegritySupport.JSON.readTree(json);
        assertThat(parsed.has("routes")).isFalse();
        assertThat(parsed.path("workflows").path(TIP).has("attributable")).isFalse();
    }

    @Test void malformedPresentArtifactsThrowWithoutWritingStatus() throws Exception {
        Fixture fixture = fixture();
        var artifacts = reserve(TIP, true, false, false, true);
        Files.writeString(artifacts.scoresFile(), "not-json\n");
        var input = with(fixture, Map.of(TIP, artifacts));
        Path out = temp.resolve("invalid-status.json");
        assertThatThrownBy(() -> FrbReserveStatus.write(input, out))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasCauseInstanceOf(ClassBScoreFiles.InvalidScore.class);
        assertThat(Files.exists(out)).isFalse();
        Files.writeString(fixture.record(), "{}\n");
        assertThatThrownBy(() -> FrbReserveStatus.write(fixture.input(), out))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasCauseInstanceOf(InvalidReserveRecord.class);
    }

    @Test void wrongBlindItemAndNonReserveManifestThrow() throws Exception {
        Fixture fixture = fixture();
        var artifacts = reserve(TIP, true, false, false, true);
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllLines(artifacts.scoresFile()).getFirst());
        row.put("itemId", "blind-999999");
        Files.writeString(artifacts.scoresFile(), row + "\n");
        var wrong = with(fixture, Map.of(TIP, artifacts));
        assertThatThrownBy(() -> FrbReserveStatus.write(wrong, temp.resolve("wrong-item.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        var primaryPackets = new FrbReserveStatus.ReserveArtifacts(fixture.input().verification().packetDirectory(),
                null, null, null, null, null, null);
        var nonReserve = with(fixture, Map.of(TIP, primaryPackets));
        assertThatThrownBy(() -> FrbReserveStatus.write(nonReserve, temp.resolve("non-reserve.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        var otherWorkflow = with(fixture, Map.of(TIP, reserve(COLLECTION, false, false, false, true)));
        assertThatThrownBy(() -> FrbReserveStatus.write(otherWorkflow, temp.resolve("other-workflow.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void unboundSealAndChangedFindingsThrow() throws Exception {
        Fixture fixture = fixture();
        var artifacts = reserve(TIP, true, true, true, true);
        rewrite(artifacts.lockForPrimaryOpening(), row -> row.put("sealSha256", "0".repeat(64)));
        var input = with(fixture, Map.of(TIP, artifacts));
        assertThatThrownBy(() -> FrbReserveStatus.write(input, temp.resolve("unbound.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        var tampered = reserve(COLLECTION, true, true, true, true);
        Files.writeString(tampered.sealFile(), "{}\n");
        var tamperedInput = with(fixture, Map.of(COLLECTION, tampered));
        assertThatThrownBy(() -> FrbReserveStatus.write(tamperedInput, temp.resolve("tampered-seal.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasCauseInstanceOf(ArmKeySeal.SealBroken.class);
    }

    @Test void unequalWorkflowsAndFindingsBetweenLocksThrow() throws Exception {
        Fixture fixture = fixture();
        var artifacts = reserve(TIP, true, true, true, true);
        rewrite(artifacts.lockForReserveOpening(), row -> row.withArray("workflows").insert(0, "OTHER"));
        var input = with(fixture, Map.of(TIP, artifacts));
        assertThatThrownBy(() -> FrbReserveStatus.write(input, temp.resolve("different-workflows.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void unequalValidFindingListsBetweenLocksThrow() throws Exception {
        Fixture fixture = fixture();
        var artifacts = reserve(TIP, true, true, true, true);
        Path extra = artifacts.findingsDirectory().resolve("review.txt");
        Files.writeString(extra, "review complete\n");
        rewrite(artifacts.lockForReserveOpening(), row -> {
            ObjectNode digest = IntegritySupport.JSON.createObjectNode();
            digest.put("name", "review.txt").put("sha256", FreezeRecord.sha256("review complete\n".getBytes()));
            row.withArray("findings").insert(0, digest);
        });
        var input = with(fixture, Map.of(TIP, artifacts));
        assertThatThrownBy(() -> FrbReserveStatus.write(input, temp.resolve("different-findings.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("differ in workflows or findings");
    }

    @Test void reserveLockMustFollowDecisionAndFindingsMustBeBesideScores() throws Exception {
        Fixture fixture = fixture();
        var artifacts = reserve(TIP, true, true, true, true);
        rewrite(artifacts.lockForPrimaryOpening(), row -> row.put("lockedAtUtc", DECIDED));
        var early = with(fixture, Map.of(TIP, artifacts));
        assertThatThrownBy(() -> FrbReserveStatus.write(early, temp.resolve("early.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        var wrongDirectory = new FrbReserveStatus.ReserveArtifacts(artifacts.packetDirectory(),
                artifacts.sealFile(), artifacts.scoresFile(), artifacts.scorers(),
                artifacts.lockForPrimaryOpening(), artifacts.lockForReserveOpening(), temp);
        var directoryInput = with(fixture, Map.of(TIP, wrongDirectory));
        assertThatThrownBy(() -> FrbReserveStatus.write(directoryInput, temp.resolve("wrong-dir.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void aLockMustListTheScoresFile() throws Exception {
        Fixture fixture = fixture();
        var artifacts = reserve(TIP, true, true, true, true);
        rewrite(artifacts.lockForPrimaryOpening(), row -> ((ObjectNode) row.withArray("findings").get(0))
                .put("name", "other.jsonl"));
        var input = with(fixture, Map.of(TIP, artifacts));
        assertThatThrownBy(() -> FrbReserveStatus.write(input, temp.resolve("not-listed.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void mappingAndFrozenWorkflowNamesAreVerified() throws Exception {
        Fixture fixture = fixture();
        var old = fixture.input().verification();
        var changedCheck = new ResultsPipeline.ReserveVerification(old.packetDirectory(), old.sealFile(),
                old.scoresFile(), old.scorers(), Set.of(TIP), old.primaryLockFile());
        var namesInput = new FrbReserveStatus.Input(fixture.record(), changedCheck,
                fixture.input().mappingFile(), fixture.input().parametersFile(), Map.of());
        assertThatThrownBy(() -> FrbReserveStatus.write(namesInput, temp.resolve("wrong-names.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("workflows");
        Path mapping = fixture.input().mappingFile();
        Files.writeString(mapping, "{}\n");
        assertThatThrownBy(() -> FrbReserveStatus.write(fixture.input(), temp.resolve("bad-mapping.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        Files.copy(MAPPING, mapping, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path parameters = fixture.input().parametersFile();
        Files.writeString(parameters, "{}\n");
        assertThatThrownBy(() -> FrbReserveStatus.write(fixture.input(), temp.resolve("bad-parameters.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void undrawnAndUnknownEntriesAreRejected() throws Exception {
        Fixture fixture = fixture();
        var absent = new FrbReserveStatus.ReserveArtifacts(null, null, null, null, null, null, null);
        var undrawn = with(fixture, Map.of("ASK_COMPANION", absent));
        assertThatThrownBy(() -> FrbReserveStatus.write(undrawn, temp.resolve("undrawn.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        var unknown = with(fixture, Map.of("UNKNOWN", absent));
        assertThatThrownBy(() -> FrbReserveStatus.write(unknown, temp.resolve("unknown.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void changedFindingsAndWrongLockKindThrow() throws Exception {
        Fixture fixture = fixture();
        var artifacts = reserve(TIP, true, true, true, true);
        ObjectNode changed = (ObjectNode) IntegritySupport.JSON.readTree(
                Files.readAllLines(artifacts.scoresFile()).getFirst());
        changed.put("critical", true).put("noCriticalMisleading", false)
                .put("reasonCode", "CRITICAL_MISLEADING");
        Files.writeString(artifacts.scoresFile(), changed + "\n");
        var input = with(fixture, Map.of(TIP, artifacts));
        assertThatThrownBy(() -> FrbReserveStatus.write(input, temp.resolve("changed.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        var other = reserve(COLLECTION, true, false, true, true);
        ArmKeySeal.Seal seal = ArmKeySeal.load(other.sealFile());
        LockRecord.create(other.lockForPrimaryOpening(), LockRecord.Kind.PRIMARY, Set.of(COLLECTION),
                List.of(other.scoresFile()), seal, other.sealFile(), files -> {}, RESERVE_LOCKED);
        var bad = with(fixture, Map.of(COLLECTION, other));
        assertThatThrownBy(() -> FrbReserveStatus.write(bad, temp.resolve("wrong-kind.json")))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void deterministicBytesAndNoIdentityOrAccessLog() throws Exception {
        Fixture fixture = fixture();
        var input = with(fixture, Map.of(TIP, reserve(TIP, true, true, true, true)));
        status(input, "status-a.json");
        status(input, "status-b.json");
        assertThat(Files.readAllBytes(temp.resolve("status-a.json")))
                .isEqualTo(Files.readAllBytes(temp.resolve("status-b.json")));
        assertThat(Files.readString(temp.resolve("status-a.json")))
                .contains("reserveDecisionRecordSha256", "reserveDrawnByWorkflow")
                .doesNotContain("armKeySha256", "BASELINE", "CANDIDATE", "LUNA");
        assertThat(Files.exists(temp.resolve("primary/arm-key.jsonl"))).isFalse();
        assertThat(Files.exists(temp.resolve("reserve-" + TIP + "/arm-key.jsonl"))).isFalse();
        assertThat(Files.exists(temp.resolve("primary/access.jsonl"))).isFalse();
    }
    @Test void existingStatusFileIsNeverOverwritten() throws Exception {
        Fixture fixture = fixture();
        var input = with(fixture, Map.of(TIP, reserve(TIP, true, true, true, true)));
        Path out = temp.resolve("existing-status.json");
        Files.writeString(out, "keep");
        assertThatThrownBy(() -> FrbReserveStatus.write(input, out))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        assertThat(Files.readString(out)).isEqualTo("keep");
    }

    @Test void statusPathInsideEvidenceDirectoriesIsRefused() throws Exception {
        Fixture fixture = fixture();
        var artifacts = reserve(TIP, true, true, true, true);
        var input = with(fixture, Map.of(TIP, artifacts));
        Path inReservePackets = artifacts.packetDirectory().resolve("status.json");
        Path inPrimaryPackets = fixture.input().verification().packetDirectory().resolve("status.json");
        Path inFindings = artifacts.findingsDirectory().resolve("status.json");
        assertThatThrownBy(() -> FrbReserveStatus.write(input, inReservePackets))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        assertThatThrownBy(() -> FrbReserveStatus.write(input, inPrimaryPackets))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        assertThatThrownBy(() -> FrbReserveStatus.write(input, inFindings))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        assertThat(Files.exists(inReservePackets)).isFalse();
        assertThat(Files.exists(inPrimaryPackets)).isFalse();
        assertThat(Files.exists(inFindings)).isFalse();
    }

    @Test void statusFileCarriesTheReturnToOwnerCauseClass() throws Exception {
        Fixture fixture = fixture();
        var input = with(fixture, Map.of(TIP, reserve(TIP, false, false, false, true)));
        status(input, "class-status.json");
        assertThat(IntegritySupport.JSON.readTree(Files.readAllBytes(temp.resolve("class-status.json")))
                .path("causeClass").asText()).isEqualTo("RETURN_TO_OWNER");
    }

    @Test void scoresOrSealWithoutPacketsAreAnIntegrityError() throws Exception {
        Fixture fixture = fixture();
        var full = reserve(TIP, true, false, false, true);
        var scoresOnly = new FrbReserveStatus.ReserveArtifacts(null, null, full.scoresFile(), SCORERS,
                null, null, full.findingsDirectory());
        var sealOnly = new FrbReserveStatus.ReserveArtifacts(null, full.sealFile(), null, null,
                null, null, null);
        var scoresInput = with(fixture, Map.of(TIP, scoresOnly));
        var sealInput = with(fixture, Map.of(TIP, sealOnly));
        Path scoresOut = temp.resolve("scores-only.json");
        Path sealOut = temp.resolve("seal-only.json");
        assertThatThrownBy(() -> FrbReserveStatus.write(scoresInput, scoresOut))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        assertThatThrownBy(() -> FrbReserveStatus.write(sealInput, sealOut))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        assertThat(Files.exists(scoresOut)).isFalse();
        assertThat(Files.exists(sealOut)).isFalse();
    }
}
