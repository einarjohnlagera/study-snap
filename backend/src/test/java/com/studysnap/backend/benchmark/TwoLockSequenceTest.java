package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic FRB two-lock ordering; all opening attempts use the real byte seal and lock gate. */
class TwoLockSequenceTest {
    private static final String TIP = "STUDY_TIP";
    private static final String COLLECTION = "COLLECTION_COMPANION";
    private static final String HUMAN = "HUMAN_OWNER";
    private static final String BASELINE = "BASELINE";
    private static final String CANDIDATE = "CANDIDATE";
    private static final String PACKETS = "packets";
    private static final String ARM_KEY_FILE = "arm-key.jsonl";
    private static final String SEAL_FILE = "seal.json";
    private static final String FINDINGS = "findings";
    private static final String PRIMARY_LOCK_FILE = "primary-lock.json";
    private static final String RESERVE_LOCK_FILE = "reserve-lock.json";
    private static final String RESERVE_PREFIX = "reserve-";
    private static final String PRIMARY_ACCESS_FILE = "primary-access.jsonl";
    private static final String RESERVE_ACCESS_FILE = "reserve-access.jsonl";
    private static final String UNSCORED_STATUS = "unscored.json";
    private static final String ONE_LOCK_STATUS = "one-lock.json";
    private static final String BOTH_LOCKS_STATUS = "both-locks.json";
    private static final String SYNTHETIC = "synthetic";
    private static final String DESCRIPTION = "Description";
    private static final String REASON_CODE = "reasonCode";
    private static final String FIXTURE_DIGEST = "d".repeat(64);
    private static final String PRIMARY_SEALED = "2026-11-16T00:00:00Z";
    private static final String PRIMARY_LOCKED = "2026-11-16T00:01:00Z";
    private static final String DECIDED = "2026-11-16T00:02:00Z";
    private static final String RESERVE_SEALED = "2026-11-16T00:03:00Z";
    private static final String RESERVE_LOCKED = "2026-11-16T00:04:00Z";
    private static final String OPENED = "2026-11-16T00:05:00Z";
    private static final String RESERVE_OPENED = "2026-11-16T00:06:00Z";
    private static final String SCORES = "scores.jsonl";
    private static final Path MAPPING = Path.of("src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json");
    private static final Path PARAMETERS = Path.of("src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json");
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS =
            Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN);
    private static final Map<String, ClassBScoreAggregator.Arm> LABELS = Map.of(
            BASELINE, ClassBScoreAggregator.Arm.BASELINE, CANDIDATE, ClassBScoreAggregator.Arm.CANDIDATE);
    @TempDir Path temp;

    private static void writeScores(Path file, List<ClassBScoreFiles.BlindItem> items,
            Set<String> critical) throws Exception {
        Files.createDirectories(file.getParent());
        List<String> rows = new ArrayList<>();
        for (ClassBScoreFiles.BlindItem item : items) {
            boolean bad = critical.contains(item.itemId());
            ObjectNode row = IntegritySupport.JSON.createObjectNode();
            row.put("itemId", item.itemId()).put("workflow", item.workflow().name()).put("round", 1)
                    .put("scorerId", HUMAN).put("scorerKind", "HUMAN")
                    .put("taskFulfilled", true).put("grounded", true).put("actionable", true)
                    .put("noCriticalMisleading", !bad).put("critical", bad)
                    .put("borderline", false).put("adjudication", false);
            if (bad) row.put(REASON_CODE, "CRITICAL_MISLEADING");
            else row.putNull(REASON_CODE);
            rows.add(row.toString());
        }
        Files.write(file, rows);
    }

    private static Path copyWithHash(Path source, Path target) throws Exception {
        Files.copy(source, target);
        Files.copy(IntegritySupport.detached(source), IntegritySupport.detached(target));
        return target;
    }

    private final class Scenario {
        final Path primary = temp.resolve("primary");
        final Path primaryKey = primary.resolve(ARM_KEY_FILE);
        final Path primarySeal = primary.resolve(SEAL_FILE);
        final Path primaryLock = primary.resolve(PRIMARY_LOCK_FILE);
        final Path primaryScores = primary.resolve(FINDINGS).resolve(SCORES);
        final Path decisionFile = primary.resolve("decision.json");
        final Path mapping;
        final Path parameters;
        final Set<String> names;
        final ClassBBlindPacketExport.Export export;
        final ArmKeySeal.Seal seal;
        final LockRecord lock;
        final Map<Path, Path> held = new HashMap<>();
        final Map<Path, byte[]> originalBytes = new HashMap<>();

        Scenario(boolean twoDrawn) throws Exception {
            Files.createDirectories(primary);
            export = BlindReviewFixtures.classBAll(primary, false);
            PairMap pairs = PairMap.fromPackets(primary.resolve(PACKETS));
            Set<String> critical = new java.util.HashSet<>();
            critical.add(firstA(pairs, TIP));
            if (twoDrawn) critical.add(firstA(pairs, COLLECTION));
            writeScores(primaryScores, export.manifest(), critical);
            seal = ArmKeySeal.seal(primaryKey, primary.resolve(PACKETS), primarySeal,
                    PRIMARY_SEALED);
            lock = LockRecord.create(primaryLock, LockRecord.Kind.PRIMARY, seal.workflowStems(),
                    List.of(primaryScores), seal, primarySeal, files -> {}, PRIMARY_LOCKED);
            mapping = copyWithHash(MAPPING, temp.resolve("mapping.json"));
            parameters = copyWithHash(PARAMETERS, temp.resolve("parameters.json"));
            names = FrozenGateParameters.load(parameters).frbClassBWorkflows;
        }

        private String firstA(PairMap pairs, String workflow) {
            return pairs.pairs().stream().filter(pair -> pair.workflow().equals(workflow)).findFirst()
                    .orElseThrow().items().get("A").getFirst();
        }

        void decide() {
            var validated = ClassBScoreFiles.validate(primaryScores, export.manifest(), SCORERS);
            var decisions = ReserveDecisionRecord.compute(primary.resolve(PACKETS), validated,
                    EvidenceMapping.load(mapping), names);
            ReserveDecisionRecord.create(decisionFile, decisions, lock, primaryLock, primaryScores,
                    primary.resolve(PACKETS), primarySeal, DECIDED);
        }

        FrbReserveStatus.Input input(Map<String, FrbReserveStatus.ReserveArtifacts> reserves) {
            var check = new ResultsPipeline.ReserveVerification(primary.resolve(PACKETS), primarySeal,
                    primaryScores, SCORERS, names, primaryLock);
            return new FrbReserveStatus.Input(decisionFile, check, mapping, parameters, reserves);
        }

        FrbReserveStatus.Result status(String name, Map<String, FrbReserveStatus.ReserveArtifacts> reserves) {
            holdKeys();
            return FrbReserveStatus.write(input(reserves), temp.resolve(name));
        }

        void holdKeys() {
            try {
                Path directory = temp.resolve("held-keys");
                Files.createDirectories(directory);
                List<Path> keys = new ArrayList<>(List.of(primaryKey, temp.resolve(RESERVE_PREFIX + TIP)
                        .resolve(ARM_KEY_FILE), temp.resolve(RESERVE_PREFIX + COLLECTION)
                        .resolve(ARM_KEY_FILE)));
                for (Path key : keys) if (Files.exists(key)) {
                    Path destination = directory.resolve(key.getParent().getFileName() + "-" + ARM_KEY_FILE);
                    originalBytes.putIfAbsent(key, Files.readAllBytes(key));
                    Files.move(key, destination);
                    held.put(key, destination);
                }
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        }

        void restoreKeys() throws Exception {
            for (var entry : held.entrySet()) {
                Files.move(entry.getValue(), entry.getKey());
                assertThat(Files.readAllBytes(entry.getKey())).isEqualTo(originalBytes.get(entry.getKey()));
            }
            held.clear();
        }

        ArmKeyAccess.LockBinding primaryBinding() {
            return new ArmKeyAccess.LockBinding(primaryLock, primarySeal, primaryScores.getParent());
        }
    }

    private record Reserve(Path root, Path key, Path sealFile, ArmKeySeal.Seal seal,
            Path scores, Path forPrimary, Path forReserve, ClassBBlindPacketExport.Export export) {
        FrbReserveStatus.ReserveArtifacts artifacts() {
            return new FrbReserveStatus.ReserveArtifacts(root.resolve(PACKETS), sealFile, scores,
                    SCORERS, forPrimary, forReserve, scores.getParent());
        }
        ArmKeyAccess.LockBinding primaryOpeningBinding() {
            return ArmKeyGateFixtures.binding(forPrimary, sealFile, scores);
        }
        ArmKeyAccess.LockBinding reserveOpeningBinding() {
            return ArmKeyGateFixtures.binding(forReserve, sealFile, scores);
        }
    }

    private record Prepared(List<ClassBCaseManifest.Case> cases, List<ClassBBlindPacketExport.Output> outputs,
            List<Path> delivered, List<Stage1Receipt> receipts, Path freeze, PlacementSecret secret,
            String exportId, Path root) {}

    private Prepared prepare(String workflow) throws Exception {
        Path evidence = temp.resolve("evidence-" + workflow);
        Files.createDirectories(evidence);
        String exportId = "CLASS_B_RESERVE:" + workflow;
        byte[] secretBytes = new byte[32];
        for (int index = 0; index < secretBytes.length; index++)
            secretBytes[index] = (byte) (index + (workflow.equals(TIP) ? 0 : 41));
        PlacementSecret secret = new PlacementSecret(secretBytes);
        Path freeze = PlacementFreezeRecordTest.write(evidence,
                PlacementFreezeRecordTest.record(Map.of(exportId, secret.commitment())));
        String id = workflow.toLowerCase() + "-reserve";
        ClassBCaseManifest.Case item;
        if (workflow.equals(TIP)) item = new ClassBCaseManifest.Case(id,
                ClassBCaseManifest.Workflow.STUDY_TIP, SYNTHETIC, true, LearnerLevel.COLLEGE,
                List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                null, null, null, null, null, null, null, null);
        else {
            var context = new CompanionGenerationContext("Collection", DESCRIPTION, "Program",
                    List.of(), List.of(new CompanionGenerationContext.CompanionContextItem("Note", DESCRIPTION)));
            item = new ClassBCaseManifest.Case(id, ClassBCaseManifest.Workflow.COLLECTION_COMPANION,
                    SYNTHETIC, true, null, null, List.of(CompanionSection.OVERVIEW), context,
                    null, null, null, null, null, null);
        }
        List<Path> delivered = new ArrayList<>();
        List<Stage1Receipt> receipts = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (String arm : List.of(BASELINE, CANDIDATE)) {
            String body = arm.equals(BASELINE) ? "Answer one." : "Answer two.";
            var line = new DeliveredOutputs.Line(id, workflow, arm, FIXTURE_DIGEST, 0,
                    CaseArmOutcome.State.COMPLETE, null, IntegritySupport.JSON.getNodeFactory().textNode(body));
            String text = IntegritySupport.JSON.writeValueAsString(line);
            Path deliveredFile = evidence.resolve(arm + ".jsonl");
            Files.writeString(deliveredFile, text + "\n");
            var row = new Stage1Receipt.CaseRow(id, workflow, FIXTURE_DIGEST,
                    CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ONE, BigDecimal.ONE,
                    List.of(), List.of(), FreezeRecord.sha256(text.getBytes(StandardCharsets.UTF_8)));
            receipts.add(new Stage1Receipt("a".repeat(40), FreezeRecord.sha256(freeze), arm,
                    SYNTHETIC, StageCapLedger.Stage.STAGE_2, Instant.EPOCH, Instant.EPOCH, 1,
                    false, "c".repeat(64), List.of(row), Map.of(), FreezeRecord.sha256(deliveredFile)));
            delivered.add(deliveredFile);
            outputs.add(new ClassBBlindPacketExport.Output(id, LABELS.get(arm), body));
        }
        return new Prepared(List.of(item), outputs, delivered, receipts, freeze, secret, exportId,
                temp.resolve(RESERVE_PREFIX + workflow));
    }

    private ClassBBlindPacketExport.Export export(Prepared prepared, Path decision) {
        return ClassBBlindPacketExport.export(prepared.cases(), prepared.outputs(),
                prepared.root().resolve(PACKETS), prepared.root().resolve(ARM_KEY_FILE),
                prepared.delivered(), prepared.freeze(), prepared.receipts(), prepared.exportId(),
                prepared.secret(), LABELS, decision);
    }

    private Reserve reserve(Prepared prepared, Path decision) {
        ClassBBlindPacketExport.Export result = export(prepared, decision);
        Path root = prepared.root();
        Path key = root.resolve(ARM_KEY_FILE);
        Path sealFile = root.resolve(SEAL_FILE);
        ArmKeySeal.Seal seal = ArmKeySeal.seal(key, root.resolve(PACKETS), sealFile,
                RESERVE_SEALED, PlacementSidecar.beside(key));
        return new Reserve(root, key, sealFile, seal, root.resolve(FINDINGS).resolve(SCORES),
                root.resolve(RESERVE_LOCK_FILE), root.resolve(PRIMARY_LOCK_FILE), result);
    }

    private static void lock(Reserve reserve, LockRecord.Kind kind, Path file,
            LockRecord.FindingsValidator validator) {
        LockRecord.create(file, kind, Set.of(TIP), List.of(reserve.scores()), reserve.seal(),
                reserve.sealFile(), validator, RESERVE_LOCKED);
    }

    @Test void twoLockSequenceKeepsBlindScoringBeforeAuthorizedOpen() throws Exception {
        Scenario scenario = new Scenario(false);
        var empty = scenario.input(Map.of());
        Path missing = temp.resolve("before-decision.json");
        scenario.holdKeys();
        assertThatThrownBy(() -> FrbReserveStatus.write(empty, missing))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasCauseInstanceOf(InvalidReserveRecord.class);
        assertThat(Files.exists(missing)).isFalse();

        scenario.decide();
        assertThat(ReserveDecisionRecord.load(scenario.decisionFile).reserveDrawnByWorkflow())
                .containsEntry(TIP, true).containsEntry(COLLECTION, false);
        var unscored = scenario.status(UNSCORED_STATUS, Map.of());
        assertThat(unscored.status()).isEqualTo("INCONCLUSIVE");
        assertThat(unscored.causes().get(TIP)).containsExactly(FrbReserveStatus.NOT_SCORED);
        assertThat(unscored.attributable()).isFalse();
        scenario.restoreKeys();
        Path prematureLog = temp.resolve("premature-access.jsonl");
        List<ArmKeyAccess.LockBinding> onlyPrimaryBinding = List.of(scenario.primaryBinding());
        Set<LockRecord.Kind> bothKinds = Set.of(LockRecord.Kind.PRIMARY, LockRecord.Kind.RESERVE);
        assertThatThrownBy(() -> ArmKeyAccess.open(scenario.primaryKey, scenario.primarySeal,
                onlyPrimaryBinding, bothKinds, prematureLog, OPENED, "premature primary"))
                .isInstanceOf(ArmKeyAccess.KeyLocked.class);
        assertThat(Files.exists(prematureLog)).isFalse();
        scenario.holdKeys();

        Prepared drawn = prepare(TIP);
        Reserve reserve = reserve(drawn, scenario.decisionFile);
        writeScores(reserve.scores(), List.of(reserve.export().manifest().getFirst()), Set.of());
        var validator = ReserveLockReadiness.asValidator(reserve.root().resolve(PACKETS), SCORERS, SCORES);
        Set<String> reserveWorkflow = Set.of(TIP);
        List<Path> reserveFindings = List.of(reserve.scores());
        Path reserveLockFile = reserve.forPrimary();
        ArmKeySeal.Seal reserveSeal = reserve.seal();
        Path reserveSealFile = reserve.sealFile();
        assertThatThrownBy(() -> LockRecord.create(reserveLockFile, LockRecord.Kind.RESERVE,
                reserveWorkflow, reserveFindings, reserveSeal, reserveSealFile, validator,
                RESERVE_LOCKED)).isInstanceOf(LockRecord.InvalidLock.class)
                .hasCauseInstanceOf(InvalidEvidence.class);
        assertThat(Files.exists(reserve.forPrimary())).isFalse();

        writeScores(reserve.scores(), reserve.export().manifest(), Set.of());
        lock(reserve, LockRecord.Kind.RESERVE, reserve.forPrimary(), validator);
        var artifacts = Map.of(TIP, reserve.artifacts());
        var oneLock = scenario.status(ONE_LOCK_STATUS, artifacts);
        assertThat(oneLock.attributable()).isFalse();
        assertThat(oneLock.awaitingLock()).containsExactly(TIP);
        lock(reserve, LockRecord.Kind.PRIMARY, reserve.forReserve(), validator);
        var bothLocks = scenario.status(BOTH_LOCKS_STATUS, artifacts);
        assertThat(bothLocks.attributable()).isTrue();
        assertThat(bothLocks.awaitingLock()).isEmpty();
        assertThat(bothLocks.workflows().get(COLLECTION))
                .isEqualTo(new FrbReserveStatus.Workflow(false, false, false));
        for (String statusFile : List.of(UNSCORED_STATUS, ONE_LOCK_STATUS, BOTH_LOCKS_STATUS))
            assertThat(Files.readString(temp.resolve(statusFile)))
                    .doesNotContain("armKeySha256", BASELINE, CANDIDATE);
        assertThat(Files.exists(temp.resolve(PRIMARY_ACCESS_FILE))).isFalse();
        assertThat(Files.exists(temp.resolve(RESERVE_ACCESS_FILE))).isFalse();

        scenario.restoreKeys();
        List<ArmKeyAccess.LockBinding> primaryBindings = List.of(scenario.primaryBinding(),
                reserve.primaryOpeningBinding());
        Path primaryLog = temp.resolve(PRIMARY_ACCESS_FILE);
        var opened = ArmKeyAccess.open(scenario.primaryKey, scenario.primarySeal, primaryBindings,
                bothKinds, primaryLog, OPENED, "primary attribution");
        assertThat(opened.rows()).isNotEmpty();
        var entry = ArmKeyAccess.validateAccessLog(primaryLog).getFirst();
        assertThat(entry.requiredKinds()).contains(LockRecord.Kind.RESERVE);
        assertThat(entry.lockSha256s()).contains(IntegritySupport.detachedHash(reserve.forPrimary()));
        Path equalLog = temp.resolve("equal-time-access.jsonl");
        assertThatThrownBy(() -> ArmKeyAccess.open(scenario.primaryKey, scenario.primarySeal,
                primaryBindings, bothKinds,
                equalLog, RESERVE_LOCKED, "equal time"))
                .isInstanceOf(ArmKeyAccess.KeyLocked.class);
        Path earlierLog = temp.resolve("early-access.jsonl");
        assertThatThrownBy(() -> ArmKeyAccess.open(scenario.primaryKey, scenario.primarySeal,
                primaryBindings, bothKinds,
                earlierLog, "2026-11-16T00:03:30Z", "early time"))
                .isInstanceOf(ArmKeyAccess.KeyLocked.class);
        assertThat(Files.exists(equalLog)).isFalse();
        assertThat(Files.exists(earlierLog)).isFalse();
        assertThat(ArmKeyAccess.validateAccessLog(primaryLog)).hasSize(1);

        Path reserveLog = temp.resolve(RESERVE_ACCESS_FILE);
        Set<LockRecord.Kind> primaryKind = Set.of(LockRecord.Kind.PRIMARY);
        List<ArmKeyAccess.LockBinding> reservePrimaryBinding = List.of(reserve.reserveOpeningBinding());
        Path reserveKey = reserve.key();
        var reserveOpened = ArmKeyAccess.open(reserveKey, reserveSealFile,
                reservePrimaryBinding, primaryKind,
                reserveLog, RESERVE_OPENED, "reserve attribution");
        assertThat(reserveOpened.rows()).hasSize(2);
        Path wrongKindLog = temp.resolve("wrong-kind-access.jsonl");
        List<ArmKeyAccess.LockBinding> wrongKindBinding = List.of(reserve.primaryOpeningBinding());
        assertThatThrownBy(() -> ArmKeyAccess.open(reserveKey, reserveSealFile,
                wrongKindBinding, primaryKind,
                wrongKindLog, RESERVE_OPENED, "wrong reserve kind"))
                .isInstanceOf(ArmKeyAccess.KeyLocked.class);
        Path bothKindsLog = temp.resolve("both-kinds-access.jsonl");
        List<ArmKeyAccess.LockBinding> selfBoundBindings = List.of(reserve.reserveOpeningBinding(),
                reserve.primaryOpeningBinding());
        assertThatThrownBy(() -> ArmKeyAccess.open(reserveKey, reserveSealFile,
                selfBoundBindings, bothKinds, bothKindsLog,
                RESERVE_OPENED, "self-bound reserve"))
                .isInstanceOf(ArmKeyAccess.KeyLocked.class)
                .hasMessageContaining("Reserve lock must use its own sealed key");
        assertThat(Files.exists(wrongKindLog)).isFalse();
        assertThat(Files.exists(bothKindsLog)).isFalse();
        scenario.holdKeys();

        Prepared undrawn = prepare(COLLECTION);
        assertThatThrownBy(() -> export(undrawn, scenario.decisionFile))
                .isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(undrawn.root())).isFalse();
        var falseEntry = Map.of(COLLECTION, new FrbReserveStatus.ReserveArtifacts(null, null, null,
                null, null, null, null));
        Path undrawnStatus = temp.resolve("undrawn-status.json");
        var undrawnInput = scenario.input(falseEntry);
        assertThatThrownBy(() -> FrbReserveStatus.write(undrawnInput, undrawnStatus))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
        assertThat(Files.exists(undrawnStatus)).isFalse();
        assertThat(Files.exists(undrawn.root().resolve(SEAL_FILE))).isFalse();
        assertThat(Files.exists(undrawn.root().resolve(RESERVE_LOCK_FILE))).isFalse();
        assertThat(Files.exists(undrawn.root().resolve(PRIMARY_LOCK_FILE))).isFalse();
        assertThat(Files.exists(undrawn.root().resolve("access.jsonl"))).isFalse();
    }

    /** Caller preflight is required: the gate trusts declared kinds, even when a reserve was drawn. */
    @Test void omittedReserveKindOpensOnlyWithoutCallerStatusPreflightAndProvenanceRefuses() throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        FrbPipelineTest.Fixture fixture = builder.fixture(true, 0);
        Path key = temp.resolve("class-b-primary/arm-key.jsonl");
        Path seal = temp.resolve("class-b-primary/seal.json");
        Path lock = temp.resolve("class-b-primary/primary-lock.json");
        Path log = temp.resolve("omitted-kind.jsonl");
        var opened = ArmKeyAccess.open(key, seal, List.of(new ArmKeyAccess.LockBinding(lock, seal,
                fixture.primaryFindings())), Set.of(LockRecord.Kind.PRIMARY), log,
                "2026-11-16T00:07:00Z", "omitted reserve kind");
        assertThat(opened.rows()).isNotEmpty();
        assertThat(ArmKeyAccess.validateAccessLog(log).getFirst().requiredKinds())
                .containsExactly(LockRecord.Kind.PRIMARY);
        List<ResultsPipeline.PrimaryExport> primaries = new ArrayList<>();
        for (var item : fixture.frb().provenance().primaries()) {
            if (item.lockFile().equals(lock)) primaries.add(new ResultsPipeline.PrimaryExport(log,
                    item.sealFile(), item.lockFile(), item.findingsDirectory(),
                    item.exclusionsFileOrNull(), item.expectedExclusionsSha256OrNull()));
            else primaries.add(item);
        }
        var logs = new ResultsPipeline.ProvenanceLogs(primaries, fixture.frb().provenance().reserves());
        var changed = new ResultsPipeline.FrbInput(fixture.frb().bundle(), fixture.frb().reserveRecordFile(),
                fixture.frb().verification(), logs);
        var run = fixture.run();
        assertThatThrownBy(() -> FrbProvenance.verify(run, changed))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("RESERVE kind");
    }

    /** Caller preflight is required: open checks one RESERVE kind, not every drawn workflow.
     * The pipeline fixture supports one drawn workflow, so the full provenance refusal is unverified here;
     * FrbProvenance.verify requires each drawn lock hash in every primary log. */
    @Test void oneReserveLockCanOpenAgainstTwoDrawnWithoutCallerStatusPreflight() throws Exception {
        Scenario scenario = new Scenario(true);
        scenario.decide();
        assertThat(ReserveDecisionRecord.load(scenario.decisionFile).reserveDrawnByWorkflow())
                .containsEntry(TIP, true).containsEntry(COLLECTION, true);
        Reserve reserve = reserve(prepare(TIP), scenario.decisionFile);
        writeScores(reserve.scores(), reserve.export().manifest(), Set.of());
        var validator = ReserveLockReadiness.asValidator(reserve.root().resolve(PACKETS), SCORERS, SCORES);
        lock(reserve, LockRecord.Kind.RESERVE, reserve.forPrimary(), validator);
        var status = scenario.status("two-drawn-one-lock.json", Map.of(TIP, reserve.artifacts()));
        assertThat(status.attributable()).isFalse();
        assertThat(status.causes()).containsKey(COLLECTION);
        scenario.restoreKeys();
        Path log = temp.resolve("one-of-two-access.jsonl");
        var opened = ArmKeyAccess.open(scenario.primaryKey, scenario.primarySeal,
                List.of(scenario.primaryBinding(), reserve.primaryOpeningBinding()),
                Set.of(LockRecord.Kind.PRIMARY, LockRecord.Kind.RESERVE), log, OPENED,
                "one of two drawn reserves");
        assertThat(opened.rows()).isNotEmpty();
        assertThat(ArmKeyAccess.validateAccessLog(log)).hasSize(1);
        scenario.holdKeys();
    }

    /** Caller preflight is required: open does not run R28 against a permissively created lock. */
    @Test void incompleteReserveLockOpensReserveKeyWithoutCallerStatusPreflight() throws Exception {
        Scenario scenario = new Scenario(false);
        scenario.decide();
        Reserve reserve = reserve(prepare(TIP), scenario.decisionFile);
        writeScores(reserve.scores(), List.of(reserve.export().manifest().getFirst()), Set.of());
        lock(reserve, LockRecord.Kind.PRIMARY, reserve.forReserve(), files -> {});
        Path log = temp.resolve("half-scored-reserve-access.jsonl");
        var opened = ArmKeyAccess.open(reserve.key(), reserve.sealFile(),
                List.of(reserve.reserveOpeningBinding()), Set.of(LockRecord.Kind.PRIMARY),
                log, RESERVE_OPENED, "half-scored reserve");
        assertThat(opened.rows()).hasSize(2);
        assertThat(ArmKeyAccess.validateAccessLog(log)).hasSize(1);
        var input = scenario.input(Map.of(TIP, reserve.artifacts()));
        scenario.holdKeys();
        Path statusFile = temp.resolve("half-scored-status.json");
        assertThatThrownBy(() -> FrbReserveStatus.write(input, statusFile))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasCauseInstanceOf(InvalidEvidence.class);
    }
}
