package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReserveDecisionRecordTest {
    private static final String TIP = "STUDY_TIP";
    private static final String HUMAN = "HUMAN_OWNER";
    private static final String REVIEWER = "HUMAN_REVIEWER";
    private static final String DECIDED = "2026-11-16T00:01:01Z";
    private static final String DIGEST_ZERO = "0".repeat(64);
    private static final EvidenceMapping MAPPING = ClassAScoreSchemaV2Test.MAPPING;
    @TempDir Path temp;

    private ClassBBlindPacketExport.Export export(int primary, int reserves, long seed) {
        List<ClassBCaseManifest.Case> fixtures = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (int i = 0; i < primary + reserves; i++) {
            String id = "synthetic-" + i;
            fixtures.add(new ClassBCaseManifest.Case(id, ClassBCaseManifest.Workflow.STUDY_TIP,
                    "synthetic", i >= primary, LearnerLevel.COLLEGE,
                    List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                    null, null, null, null, null, null, null, null));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE, "Answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."));
        }
        return ClassBBlindPacketExport.export(fixtures, outputs, temp.resolve("packets"),
                temp.resolve("key.jsonl"), seed);
    }

    private static ObjectNode row(String id, String workflow, String scorer, int round, boolean critical) {
        ObjectNode node = IntegritySupport.JSON.createObjectNode();
        node.put("itemId", id).put("workflow", workflow).put("round", round)
                .put("scorerId", scorer).put("scorerKind", "HUMAN")
                .put("taskFulfilled", true).put("grounded", true).put("actionable", true)
                .put("noCriticalMisleading", !critical).put("critical", critical)
                .put("borderline", false).put("adjudication", false);
        if (critical) node.put("reasonCode", "CRITICAL_MISLEADING");
        else node.putNull("reasonCode");
        return node;
    }

    private Path writeScores(List<ObjectNode> rows) throws Exception {
        Path directory = temp.resolve("findings");
        Files.createDirectories(directory);
        Path file = directory.resolve("class-b-scores.jsonl");
        Files.write(file, rows.stream().map(ObjectNode::toString).toList());
        return file;
    }

    private ClassBScoreFiles.Validated validated(Path scores, List<ClassBScoreFiles.BlindItem> manifest) {
        return ClassBScoreFiles.validate(scores, manifest, Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN,
                REVIEWER, ClassBScoreFiles.ScorerKind.HUMAN));
    }

    private List<ObjectNode> rows(ClassBBlindPacketExport.Export export, Set<String> critical,
            Set<String> absent) {
        List<ObjectNode> result = new ArrayList<>();
        for (ClassBScoreFiles.BlindItem item : export.manifest())
            if (!absent.contains(item.itemId()))
                result.add(row(item.itemId(), item.workflow().name(), HUMAN, 1, critical.contains(item.itemId())));
        return result;
    }

    private List<ReserveDecisionRecord.WorkflowDecision> compute(ClassBScoreFiles.Validated validated,
            Set<String> workflows) {
        return ReserveDecisionRecord.compute(temp.resolve("packets"), validated, MAPPING, workflows);
    }

    private record Locked(Path scores, Path seal, Path lockFile, LockRecord lock) {}
    private Locked lock(Path scores, LockRecord.Kind kind) {
        Path sealFile = temp.resolve("seal.json");
        Path key = Files.exists(temp.resolve("key.jsonl")) ? temp.resolve("key.jsonl")
                : temp.resolve("arm-key.jsonl");
        ArmKeySeal.Seal seal = ArmKeySeal.seal(key, temp.resolve("packets"),
                sealFile, ArmKeyGateFixtures.SEALED);
        Path lockFile = temp.resolve("primary-lock.json");
        LockRecord record = LockRecord.create(lockFile, kind, seal.workflowStems(), List.of(scores), seal,
                sealFile, files -> {}, ArmKeyGateFixtures.LOCKED);
        return new Locked(scores, sealFile, lockFile, record);
    }

    private ReserveDecisionRecord create(List<ReserveDecisionRecord.WorkflowDecision> decisions, Locked locked) {
        return ReserveDecisionRecord.create(temp.resolve("decision.json"), decisions, locked.lock(),
                locked.lockFile(), locked.scores(), temp.resolve("packets"), locked.seal(), DECIDED);
    }

    private void verify(ReserveDecisionRecord record, ClassBScoreFiles.Validated validated, Locked locked) {
        ReserveDecisionRecord.verify(record, temp.resolve("packets"), locked.seal(), validated, MAPPING,
                Set.of(TIP), locked.scores(), locked.lock(), locked.lockFile());
    }

    @Test void packetManifestMatchesExporterAndFrozenWorkflowKeys() throws Exception {
        ClassBBlindPacketExport.Export export = BlindReviewFixtures.classBAll(temp, false);
        assertThat(ReserveDecisionRecord.manifestFromPackets(temp.resolve("packets")))
                .isEqualTo(export.manifest());
        Path frozen = Path.of("src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json");
        Path copy = temp.resolve("frozen-route-gates-frb.json");
        Files.copy(frozen, copy);
        Files.copy(IntegritySupport.detached(frozen), IntegritySupport.detached(copy));
        Set<String> names = FrozenGateParameters.load(copy).frbClassBWorkflows;
        Path scores = writeScores(rows(export, Set.of(), Set.of()));
        var decisions = compute(validated(scores, ReserveDecisionRecord.manifestFromPackets(temp.resolve("packets"))), names);
        assertThat(decisions.stream().map(ReserveDecisionRecord.WorkflowDecision::workflow).toList())
                .isSorted();
        Locked locked = lock(scores, LockRecord.Kind.PRIMARY);
        ReserveDecisionRecord record = create(decisions, locked);
        assertThat(record.reserveDrawnByWorkflow().keySet()).isEqualTo(names);
    }

    @Test void symmetricTriggerRejectsBothCriticalAndIgnoresReservePairs() throws Exception {
        ClassBBlindPacketExport.Export export = export(3, 1, 17L);
        List<ClassBScoreFiles.BlindItem> manifest = ReserveDecisionRecord.manifestFromPackets(temp.resolve("packets"));
        assertThat(manifest).isEqualTo(export.manifest());
        PairMap pairs = PairMap.fromPackets(temp.resolve("packets"));
        List<PairMap.Pair> primary = pairs.pairs().stream().filter(p ->
                !manifest.stream().filter(i -> i.itemId().equals(p.items().get("A").getFirst()))
                        .findFirst().orElseThrow().reserve()).toList();
        Set<String> critical = new HashSet<>();
        critical.add(primary.get(2).items().get("A").getFirst());
        critical.add(primary.get(2).items().get("B").getFirst());
        PairMap.Pair reserve = pairs.pairs().stream().filter(p -> !primary.contains(p)).findFirst().orElseThrow();
        critical.add(reserve.items().get("A").getFirst());
        Path scores = writeScores(rows(export, critical, Set.of()));
        var clean = compute(validated(scores, manifest), Set.of(TIP)).getFirst();
        assertThat(clean.primaryPairs()).isEqualTo(3);
        assertThat(clean.reserveDrawn()).isFalse();
        critical.add(primary.getFirst().items().get("A").getFirst());
        Path changedScores = writeScores(rows(export, critical, Set.of()));
        var discordant = compute(validated(changedScores, manifest), Set.of(TIP)).getFirst();
        assertThat(discordant.reserveDrawn()).isTrue();
        assertThat(discordant.pairDigestSha256()).isNotEqualTo(clean.pairDigestSha256());
        critical.remove(primary.getFirst().items().get("A").getFirst());
        critical.add(primary.get(1).items().get("B").getFirst());
        Path reverseScores = writeScores(rows(export, critical, Set.of()));
        assertThat(compute(validated(reverseScores, manifest), Set.of(TIP)).getFirst().reserveDrawn()).isTrue();
    }

    @Test void missingHumanAndDisagreementAreUnresolvedWithoutAborting() throws Exception {
        ClassBBlindPacketExport.Export export = export(1, 0, 17L);
        List<ClassBScoreFiles.BlindItem> manifest = export.manifest();
        String first = manifest.getFirst().itemId(), second = manifest.get(1).itemId();
        Path scores = writeScores(rows(export, Set.of(first), Set.of(second)));
        var missing = compute(validated(scores, manifest), Set.of(TIP)).getFirst();
        assertThat(missing.primaryUnresolved()).isTrue();
        assertThat(missing.reserveDrawn()).isFalse();
        List<ObjectNode> disagreement = rows(export, Set.of(first), Set.of());
        disagreement.add(row(first, TIP, REVIEWER, 2, false));
        scores = writeScores(disagreement);
        var unresolved = compute(validated(scores, manifest), Set.of(TIP)).getFirst();
        assertThat(unresolved.primaryUnresolved()).isTrue();
        assertThat(unresolved.reserveDrawn()).isFalse();
    }

    @Test void shuffledScoresAreDeterministicAndMixedReservePairIsRejected() throws Exception {
        ClassBBlindPacketExport.Export export = export(2, 1, 17L);
        List<ClassBScoreFiles.BlindItem> manifest = export.manifest();
        List<ObjectNode> rows = rows(export, Set.of(manifest.getFirst().itemId()), Set.of());
        Path scores = writeScores(rows);
        var first = compute(validated(scores, manifest), Set.of(TIP));
        Collections.shuffle(rows, new Random(182L));
        scores = writeScores(rows);
        assertThat(compute(validated(scores, manifest), Set.of(TIP))).isEqualTo(first);
        List<ClassBScoreFiles.BlindItem> corrupted = new ArrayList<>(manifest);
        var old = corrupted.getFirst();
        corrupted.set(0, new ClassBScoreFiles.BlindItem(old.itemId(), old.workflow(), !old.reserve()));
        ClassBScoreFiles.Validated mixed = new ClassBScoreFiles.Validated(corrupted, validated(scores, manifest).scores());
        assertThatThrownBy(() -> compute(mixed, Set.of(TIP))).isInstanceOf(InvalidReserveRecord.class);
    }

    @Test void classAShapedOutputCannotMasqueradeAsClassB() throws Exception {
        export(1, 0, 17L);
        Path packetFile = temp.resolve("packets/STUDY_TIP.jsonl");
        ObjectNode packet = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(packetFile));
        for (var output : packet.withArray("outputs")) {
            ObjectNode node = (ObjectNode) output;
            String id = node.path("itemId").asText();
            node.remove("itemId");
            node.putArray("items").addObject().put("itemId", id);
        }
        Files.writeString(packetFile, packet + "\n");
        assertThatThrownBy(() -> ReserveDecisionRecord.manifestFromPackets(temp.resolve("packets")))
                .isInstanceOf(InvalidReserveRecord.class);
    }

    @Test void createLoadVerifyAndTamperControls() throws Exception {
        ClassBBlindPacketExport.Export export = export(1, 0, 17L);
        Path scores = writeScores(rows(export, Set.of(export.manifest().getFirst().itemId()), Set.of()));
        ClassBScoreFiles.Validated validated = validated(scores, export.manifest());
        var decisions = compute(validated, Set.of(TIP));
        Locked locked = lock(scores, LockRecord.Kind.PRIMARY);
        ReserveDecisionRecord record = create(decisions, locked);
        Path file = temp.resolve("decision.json");
        assertThat(ReserveDecisionRecord.load(file)).isEqualTo(record);
        verify(record, validated, locked);
        assertThat(record.reserveDrawnByWorkflow()).containsEntry(TIP, true);
        assertThatThrownBy(() -> create(decisions, locked)).isInstanceOf(InvalidReserveRecord.class);
        Files.writeString(IntegritySupport.detached(file), DIGEST_ZERO);
        assertThatThrownBy(() -> ReserveDecisionRecord.load(file)).isInstanceOf(InvalidReserveRecord.class);
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(file));
        Files.writeString(file, Files.readString(file).replace("\"reserveDrawn\":true", "\"reserveDrawn\":false"));
        assertThatThrownBy(() -> ReserveDecisionRecord.load(file)).isInstanceOf(InvalidReserveRecord.class);
        var flipped = new ReserveDecisionRecord(1, List.of(new ReserveDecisionRecord.WorkflowDecision(TIP, 1,
                false, false, decisions.getFirst().pairDigestSha256())), record.findingsSha256(),
                record.lockSha256(), record.createdAtUtc());
        assertThatThrownBy(() -> verify(flipped, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
        var wrongFindingsHash = new ReserveDecisionRecord(1, decisions, DIGEST_ZERO,
                record.lockSha256(), record.createdAtUtc());
        assertThatThrownBy(() -> verify(wrongFindingsHash, validated, locked))
                .isInstanceOf(InvalidReserveRecord.class);
        var wrongLockHash = new ReserveDecisionRecord(1, decisions, record.findingsSha256(),
                DIGEST_ZERO, record.createdAtUtc());
        assertThatThrownBy(() -> verify(wrongLockHash, validated, locked))
                .isInstanceOf(InvalidReserveRecord.class);
        var missing = new ReserveDecisionRecord(1, List.of(), record.findingsSha256(), record.lockSha256(),
                record.createdAtUtc());
        assertThatThrownBy(() -> verify(missing, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
        var extra = new ReserveDecisionRecord(1, List.of(decisions.getFirst(),
                new ReserveDecisionRecord.WorkflowDecision("ASK_COMPANION", 1, false, false,
                        decisions.getFirst().pairDigestSha256())), record.findingsSha256(),
                record.lockSha256(), record.createdAtUtc());
        assertThatThrownBy(() -> verify(extra, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
        Files.writeString(IntegritySupport.detached(locked.lockFile()), DIGEST_ZERO);
        assertThatThrownBy(() -> verify(record, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
        Files.writeString(IntegritySupport.detached(locked.lockFile()), FreezeRecord.sha256(locked.lockFile()));
        Files.writeString(scores, Files.readString(scores) + "\n");
        assertThatThrownBy(() -> verify(record, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
    }

    @Test void lockKindFindingAndTimeMustBeValid() throws Exception {
        ClassBBlindPacketExport.Export export = export(1, 0, 17L);
        Path scores = writeScores(rows(export, Set.of(), Set.of()));
        var decisions = compute(validated(scores, export.manifest()), Set.of(TIP));
        Locked locked = lock(scores, LockRecord.Kind.RESERVE);
        assertThatThrownBy(() -> create(decisions, locked)).isInstanceOf(InvalidReserveRecord.class);
        LockRecord wrongKind = new LockRecord(1, LockRecord.Kind.PRIMARY, locked.lock().workflows(),
                locked.lock().findings(), locked.lock().armKeySha256(), locked.lock().packetFiles(),
                locked.lock().lockedAtUtc(), locked.lock().sealSha256());
        assertThatThrownBy(() -> ReserveDecisionRecord.create(temp.resolve("wrong.json"), decisions, wrongKind,
                locked.lockFile(), scores, temp.resolve("packets"), locked.seal(), DECIDED))
                .isInstanceOf(InvalidReserveRecord.class);
        Path other = scores.resolveSibling("other.jsonl");
        Files.writeString(other, "");
        assertThatThrownBy(() -> ReserveDecisionRecord.create(temp.resolve("other.json"), decisions,
                locked.lock(), locked.lockFile(), other, temp.resolve("packets"), locked.seal(), DECIDED))
                .isInstanceOf(InvalidReserveRecord.class);
        Files.delete(other);
        Path nonJsonlScore = scores.resolveSibling("unlisted-score.json");
        Files.copy(scores, nonJsonlScore);
        assertThatThrownBy(() -> ReserveDecisionRecord.create(temp.resolve("unlisted.json"), decisions,
                locked.lock(), locked.lockFile(), nonJsonlScore, temp.resolve("packets"), locked.seal(), DECIDED))
                .isInstanceOf(InvalidReserveRecord.class);
        LockRecord primary = new LockRecord(1, LockRecord.Kind.PRIMARY, locked.lock().workflows(),
                locked.lock().findings(), locked.lock().armKeySha256(), locked.lock().packetFiles(),
                locked.lock().lockedAtUtc(), locked.lock().sealSha256());
        // A real PRIMARY lock is needed for the timestamp checks.
        Path primaryFile = temp.resolve("actual-primary.json");
        LockRecord.create(primaryFile, LockRecord.Kind.PRIMARY, Set.of(TIP), List.of(scores),
                ArmKeySeal.load(locked.seal()), locked.seal(), files -> {}, ArmKeyGateFixtures.LOCKED);
        primary = LockRecord.load(primaryFile);
        LockRecord finalPrimary = primary;
        assertThatThrownBy(() -> ReserveDecisionRecord.create(temp.resolve("unlisted-primary.json"), decisions,
                finalPrimary, primaryFile, nonJsonlScore, temp.resolve("packets"), locked.seal(), DECIDED))
                .isInstanceOf(InvalidReserveRecord.class);
        assertThatThrownBy(() -> ReserveDecisionRecord.create(temp.resolve("equal.json"), decisions,
                finalPrimary, primaryFile, scores, temp.resolve("packets"), locked.seal(), ArmKeyGateFixtures.LOCKED))
                .isInstanceOf(InvalidReserveRecord.class);
        assertThatThrownBy(() -> ReserveDecisionRecord.create(temp.resolve("early.json"), decisions,
                finalPrimary, primaryFile, scores, temp.resolve("packets"), locked.seal(), ArmKeyGateFixtures.SEALED))
                .isInstanceOf(InvalidReserveRecord.class);
        assertThatThrownBy(() -> ReserveDecisionRecord.create(temp.resolve("badtime.json"), decisions,
                finalPrimary, primaryFile, scores, temp.resolve("packets"), locked.seal(), "tomorrow"))
                .isInstanceOf(InvalidReserveRecord.class);
    }

    @Test void sealAndLockRejectPacketChangesAtCreateAndVerify() throws Exception {
        ClassBBlindPacketExport.Export export = export(1, 0, 17L);
        Path scores = writeScores(rows(export, Set.of(), Set.of()));
        var validated = validated(scores, export.manifest());
        var decisions = compute(validated, Set.of(TIP));
        Locked locked = lock(scores, LockRecord.Kind.PRIMARY);
        ReserveDecisionRecord record = create(decisions, locked);
        Path packet = temp.resolve("packets/STUDY_TIP.jsonl");
        String original = Files.readString(packet);
        Files.writeString(packet, original.replace("Answer one.", "Answer won."));
        assertThatThrownBy(() -> verify(record, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
        Files.writeString(packet, original);
        Files.writeString(packet, original + "\n");
        assertThatThrownBy(() -> verify(record, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
        Files.writeString(packet, original);
        Path added = temp.resolve("packets/EXTRA.jsonl");
        Files.writeString(added, "");
        assertThatThrownBy(() -> verify(record, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
        Files.delete(added);
        Files.delete(packet);
        assertThatThrownBy(() -> verify(record, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
        Files.writeString(packet, original);
        Files.writeString(IntegritySupport.detached(locked.seal()), DIGEST_ZERO);
        assertThatThrownBy(() -> verify(record, validated, locked)).isInstanceOf(InvalidReserveRecord.class);
        Files.writeString(IntegritySupport.detached(locked.seal()), FreezeRecord.sha256(locked.seal()));
        Files.writeString(packet, original + "\n");
        assertThatThrownBy(() -> ReserveDecisionRecord.create(temp.resolve("late.json"), decisions,
                locked.lock(), locked.lockFile(), scores, temp.resolve("packets"), locked.seal(), DECIDED))
                .isInstanceOf(InvalidReserveRecord.class);
    }

    @Test void keyFreeSurfaceHasNoArmInputs() throws Exception {
        for (Method method : ReserveDecisionRecord.class.getDeclaredMethods()) {
            if (!Set.of("compute", "verify", "manifestFromPackets").contains(method.getName())) continue;
            for (Class<?> type : method.getParameterTypes()) {
                assertThat(type).isNotEqualTo(ArmKeyAccess.KeyRow.class).isNotEqualTo(ArmKeyAccess.OpenedKey.class);
                assertThat(type.getSimpleName()).doesNotContain("Arm");
            }
        }
        assertThat(ReserveDecisionRecord.class.getDeclaredMethods()).filteredOn(method ->
                method.getName().equals("compute")).hasSize(1);
        assertThat(ReserveDecisionRecord.class.getDeclaredMethod("compute", Path.class,
                ClassBScoreFiles.Validated.class, EvidenceMapping.class, Set.class).getParameterTypes())
                .containsExactly(Path.class, ClassBScoreFiles.Validated.class, EvidenceMapping.class, Set.class);
        assertThat(ReserveDecisionRecord.class.getDeclaredMethod("verify", ReserveDecisionRecord.class,
                Path.class, Path.class, ClassBScoreFiles.Validated.class, EvidenceMapping.class,
                Set.class, Path.class, LockRecord.class, Path.class).getParameterTypes())
                .containsExactly(ReserveDecisionRecord.class, Path.class, Path.class,
                        ClassBScoreFiles.Validated.class, EvidenceMapping.class, Set.class,
                        Path.class, LockRecord.class, Path.class);
    }

    @Test void placementAndOpenedArmDecisionsAgreeAcrossWorkflowsAndPatterns() throws Exception {
        List<int[]> fixed = List.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1},
                new int[]{1, 1}, new int[]{1, 1});
        Random random = new Random(182L);
        for (int index = 0; index < 205; index++) {
            int[] status = index < fixed.size() ? fixed.get(index)
                    : new int[]{random.nextInt(2), random.nextInt(2)};
            boolean byPlacement = ClassBReserve.decideReserve(List.of(
                    new ClassBReserve.PairCritical(status[0], status[1])));
            assertThat(ReserveDecisionRecord.decisionFromArmCounts(List.of(status[0]), List.of(status[1])))
                    .isEqualTo(byPlacement);
            assertThat(ReserveDecisionRecord.decisionFromArmCounts(List.of(status[1]), List.of(status[0])))
                    .isEqualTo(byPlacement);
        }
        Map<String, Set<String>> observedFirstArms = new HashMap<>();
        for (long seed : List.of(17L, 182L, 1_000_000L, 99_999_999L)) {
            Path root = temp.resolve("orientation-" + seed);
            List<ClassBCaseManifest.Case> fixtures = allWorkflows();
            List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
            for (ClassBCaseManifest.Case fixture : fixtures) {
                outputs.add(new ClassBBlindPacketExport.Output(fixture.fixtureId(),
                        ClassBScoreAggregator.Arm.BASELINE, "Answer one."));
                outputs.add(new ClassBBlindPacketExport.Output(fixture.fixtureId(),
                        ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."));
            }
            var export = ClassBBlindPacketExport.export(fixtures, outputs, root.resolve("packets"),
                    root.resolve("key.jsonl"), seed);
            Set<String> names = Set.of(TIP, "COLLECTION_COMPANION", "ASK_COMPANION", "INTERVIEW_CRITIQUE");
            Map<String, Boolean> criticalById = new HashMap<>();
            for (ClassBScoreAggregator.Key key : export.armKey()) {
                boolean critical = switch (key.workflow()) {
                    case STUDY_TIP -> false;
                    case COLLECTION_COMPANION -> key.arm() == ClassBScoreAggregator.Arm.CANDIDATE;
                    case ASK_COMPANION -> key.arm() == ClassBScoreAggregator.Arm.BASELINE;
                    case INTERVIEW_CRITIQUE -> true;
                };
                criticalById.put(key.itemId(), critical);
            }
            Path scores = root.resolve("findings/class-b-scores.jsonl");
            Files.createDirectories(scores.getParent());
            Files.write(scores, export.manifest().stream().map(item -> row(item.itemId(), item.workflow().name(),
                    HUMAN, 1, criticalById.get(item.itemId())).toString()).toList());
            var validated = validated(scores, ReserveDecisionRecord.manifestFromPackets(root.resolve("packets")));
            var decisions = ReserveDecisionRecord.compute(root.resolve("packets"), validated, MAPPING, names);
            Path sealFile = root.resolve("seal.json");
            var seal = ArmKeySeal.seal(root.resolve("key.jsonl"), root.resolve("packets"), sealFile,
                    ArmKeyGateFixtures.SEALED);
            Path lockFile = root.resolve("primary-lock.json");
            LockRecord.create(lockFile, LockRecord.Kind.PRIMARY, names, List.of(scores), seal,
                    sealFile, files -> {}, ArmKeyGateFixtures.LOCKED);
            var opened = ArmKeyAccess.open(root.resolve("key.jsonl"), sealFile,
                    List.of(new ArmKeyAccess.LockBinding(lockFile, sealFile, scores.getParent())),
                    Set.of(LockRecord.Kind.PRIMARY), root.resolve("access.jsonl"),
                    ArmKeyGateFixtures.OPENED, "orientation test");
            PairMap pairMap = PairMap.fromPackets(root.resolve("packets"));
            for (String workflow : names) {
                List<Integer> candidate = new ArrayList<>(), baseline = new ArrayList<>();
                List<ClassBReserve.PairCritical> placement = new ArrayList<>();
                for (PairMap.Pair pair : pairMap.pairs().stream().filter(p -> p.workflow().equals(workflow)).toList()) {
                    String a = pair.items().get("A").getFirst(), b = pair.items().get("B").getFirst();
                    observedFirstArms.computeIfAbsent(workflow, ignored -> new HashSet<>()).add(opened.armOf(a));
                    int ca = criticalById.get(a) ? 1 : 0, cb = criticalById.get(b) ? 1 : 0;
                    placement.add(new ClassBReserve.PairCritical(ca, cb));
                    candidate.add("CANDIDATE".equals(opened.armOf(a)) ? ca : cb);
                    baseline.add("BASELINE".equals(opened.armOf(a)) ? ca : cb);
                }
                boolean recorded = decisions.stream().filter(d -> d.workflow().equals(workflow))
                        .findFirst().orElseThrow().reserveDrawn();
                assertThat(recorded).isEqualTo(ReserveDecisionRecord.decisionFromArmCounts(candidate, baseline));
                assertThat(recorded).isEqualTo(ClassBReserve.decideReserve(placement));
                assertThat(recorded).isEqualTo(ClassBReserve.decideReserve(placement.stream()
                        .map(p -> new ClassBReserve.PairCritical(p.criticalB(), p.criticalA())).toList()));
            }
        }
        for (String workflow : List.of(TIP, "COLLECTION_COMPANION", "ASK_COMPANION", "INTERVIEW_CRITIQUE"))
            assertThat(observedFirstArms.get(workflow)).containsExactlyInAnyOrder("BASELINE", "CANDIDATE");
    }

    private static List<ClassBCaseManifest.Case> allWorkflows() {
        var context = new CompanionGenerationContext("Collection", "Description", "Program",
                List.of(), List.of(new CompanionGenerationContext.CompanionContextItem("Note", "Description")));
        var companion = new CompanionContent("Overview", "Strategy", "Mistakes", "Resources", List.of(), List.of());
        var question = new ClassBCaseManifest.Question("Interview question?", List.of("A", "B", "C", "D"),
                0, "Concept", "Explanation");
        return List.of(
                new ClassBCaseManifest.Case("tip", ClassBCaseManifest.Workflow.STUDY_TIP, "synthetic", false,
                        LearnerLevel.COLLEGE, List.of(new QuickReviewIncorrectQuestionRequest(
                        "Question?", "Answer", "Explanation.")), null, null, null, null, null, null, null, null),
                new ClassBCaseManifest.Case("collection", ClassBCaseManifest.Workflow.COLLECTION_COMPANION,
                        "synthetic", false, null, null, List.of(CompanionSection.OVERVIEW), context,
                        null, null, null, null, null, null),
                new ClassBCaseManifest.Case("ask", ClassBCaseManifest.Workflow.ASK_COMPANION,
                        "synthetic", false, null, null, null, null, companion, List.of(),
                        "Current question?", true, null, null),
                new ClassBCaseManifest.Case("interview", ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE,
                        "synthetic", false, null, null, null, null, null, null, null, null, question, 1));
    }
}
