package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostUnlockAuditsTest {
    private static final Rational ALPHA = Rational.of(1, 20);
    private static final String CANDIDATE = "CANDIDATE";
    private static final String WORKFLOW_B = "STUDY_TIP";
    @TempDir Path temp;
    private record AuditFixture(Path root, Path packets, Path seal, Path key,
                                ArmKeyAccess.OpenedKey opened) {}
    private AuditFixture classB(Path root, int count, boolean candidateInA, int extraChars) throws Exception {
        Files.createDirectories(root);
        var export = BlindReviewFixtures.classB(root, count);
        Map<String, String> arms = export.armKey().stream().collect(Collectors.toMap(
                ClassBScoreAggregator.Key::itemId, row -> row.arm().name()));
        Path packet = root.resolve("packets/STUDY_TIP.jsonl");
        List<String> changed = new ArrayList<>();
        for (String line : Files.readAllLines(packet)) {
            ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(line);
            for (JsonNode value : node.path("outputs")) {
                ObjectNode output = (ObjectNode) value;
                boolean candidate = CANDIDATE.equals(arms.get(output.path("itemId").asText()));
                if (candidateInA) output.put("placement", candidate ? "A" : "B");
                if (candidate && extraChars > 0) output.put("output", "x".repeat(extraChars));
            }
            changed.add(node.toString());
        }
        Files.writeString(packet, String.join("\n", changed) + "\n");
        Path key = root.resolve("arm-key.jsonl");
        Path seal = ArmKeyGateFixtures.seal(root, key);
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(key, seal, List.of(ArmKeyGateFixtures.binding(lock, seal, finding)),
                Set.of(LockRecord.Kind.PRIMARY), root.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED,
                "post-unlock audit test");
        return new AuditFixture(root, root.resolve("packets"), seal, key, opened);
    }
    private AuditFixture classA(Path root) throws Exception {
        Files.createDirectories(root);
        var export = BlindReviewFixtures.classA(root, 2);
        Path key = export.armKey(), seal = ArmKeyGateFixtures.seal(root, key);
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(key, seal, List.of(ArmKeyGateFixtures.binding(lock, seal, finding)),
                Set.of(LockRecord.Kind.PRIMARY), root.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED,
                "post-unlock audit test");
        return new AuditFixture(root, root.resolve("packets"), seal, key, opened);
    }
    private AuditFixture classBWithReserve(Path root) throws Exception {
        Files.createDirectories(root);
        List<ClassBCaseManifest.Case> fixtures = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String id = "case-" + i;
            fixtures.add(new ClassBCaseManifest.Case(id, ClassBCaseManifest.Workflow.STUDY_TIP,
                    "synthetic", i == 1, LearnerLevel.COLLEGE,
                    List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                    null, null, null, null, null, null, null, null));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE, "Answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."));
        }
        ClassBBlindPacketExport.export(fixtures, outputs, root.resolve("packets"),
                root.resolve("arm-key.jsonl"), 17L);
        Path key = root.resolve("arm-key.jsonl"), seal = ArmKeyGateFixtures.seal(root, key);
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(key, seal, List.of(ArmKeyGateFixtures.binding(lock, seal, finding)),
                Set.of(LockRecord.Kind.PRIMARY), root.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED,
                "reserve audit test");
        return new AuditFixture(root, root.resolve("packets"), seal, key, opened);
    }
    private AuditFixture malformedClassA(Path root, List<String> arms, boolean wrongWorkflow) throws Exception {
        Files.createDirectories(root);
        Path packets = Files.createDirectory(root.resolve("packets"));
        Files.writeString(packets.resolve("NOTE_ADAPTIVE.jsonl"), """
                {"outputs":[{"placement":"A","items":[{"itemId":"blind-00001"},{"itemId":"blind-00002"}]},
                {"placement":"B","items":[{"itemId":"blind-00003"},{"itemId":"blind-00004"}]}]}
                """);
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ObjectNode row = IntegritySupport.JSON.createObjectNode();
            row.put("id", "blind-%05d".formatted(i + 1)).put("arm", arms.get(i))
                    .put("caseId", "case-1").put("workflow", wrongWorkflow && i == 0 ? "OTHER" : "NOTE_ADAPTIVE")
                    .put("questionFormat", "MCQ");
            rows.add(row.toString());
        }
        Path key = root.resolve("key.jsonl");
        Files.writeString(key, String.join("\n", rows) + "\n");
        Path seal = ArmKeyGateFixtures.seal(root, key);
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(key, seal, List.of(ArmKeyGateFixtures.binding(lock, seal, finding)),
                Set.of(LockRecord.Kind.PRIMARY), root.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED,
                "malformed audit test");
        return new AuditFixture(root, packets, seal, key, opened);
    }
    @Test void binomialTableAndRequiredAlpha() throws Exception {
        assertThat(BlindAuditSupport.binomial(0, 0).p()).isEqualTo(Rational.of(1, 1));
        assertThat(BlindAuditSupport.binomial(0, 3).p()).isEqualTo(Rational.of(1, 4));
        assertThat(BlindAuditSupport.binomial(1, 3).p()).isEqualTo(Rational.of(1, 1));
        assertThat(BlindAuditSupport.binomial(0, 8).p()).isEqualTo(Rational.of(1, 128));
        AuditFixture f = classA(temp.resolve("a"));
        for (Rational bad : new Rational[] { null, Rational.of(0, 1), Rational.of(1, 1) }) {
            assertThatThrownBy(() -> PlacementAudit.run(f.packets(), f.seal(), "LUNA", f.opened(), bad))
                    .isInstanceOf(PlacementAudit.InvalidAudit.class);
            assertThatThrownBy(() -> PacketFeatureAudit.run(f.packets(), f.seal(), "LUNA", f.opened(), bad))
                    .isInstanceOf(PacketFeatureAudit.InvalidAudit.class);
        }
    }
    @Test void classAPlacementAndSeedGapAreReported() throws Exception {
        AuditFixture f = classA(temp.resolve("a"));
        var report = PlacementAudit.run(f.packets(), f.seal(), "LUNA", f.opened(), ALPHA);
        assertThat(report.overall().pairs()).isEqualTo(2);
        assertThat(report.overall().flagged()).isFalse();
        assertThat(report.seedCheck()).isEqualTo("UNVERIFIABLE_NO_SEALED_SEED");
        assertThat(report.seedReason()).isNotBlank();
        assertThat(report.seedReason()).contains("unsealed seed");
        assertThat(report.reserveOverall().undefined()).isTrue();
        assertThatThrownBy(() -> PlacementAudit.run(f.packets(), f.seal(), null, f.opened(), ALPHA))
                .isInstanceOf(PlacementAudit.InvalidAudit.class);
        Files.delete(f.key());
        assertThat(PlacementAudit.run(f.packets(), f.seal(), "LUNA", f.opened(), ALPHA).overall().pairs()).isEqualTo(2);
    }
    @Test void classBAllCandidateInAAndSignTestFlag() throws Exception {
        AuditFixture f = classB(temp.resolve("b"), 8, true, 100);
        var placement = PlacementAudit.run(f.packets(), f.seal(), null, f.opened(), ALPHA);
        assertThat(placement.overall().candidateInA()).isEqualTo(8);
        assertThat(placement.overall().twoSidedP()).isEqualTo(Rational.of(1, 128));
        assertThat(placement.overall().flagged()).isTrue();
        var features = PacketFeatureAudit.run(f.packets(), f.seal(), null, f.opened(), ALPHA);
        var length = features.overall().get("outputChars");
        assertThat(length.positive()).isEqualTo(8);
        assertThat(length.negative()).isZero();
        assertThat(length.twoSidedP()).isEqualTo(Rational.of(1, 128));
        assertThat(length.flagged()).isTrue();
        assertThat(length.candidateMean()).isEqualByComparingTo("100");
        assertThat(length.baselineMean()).isEqualByComparingTo("11");
        assertThat(features.overall().get("newlines").ties()).isEqualTo(8);
        assertThat(features.overall().get("newlines").flagged()).isFalse();
        Path reportFile = temp.resolve("features.json");
        PacketFeatureAudit.write(reportFile, features);
        assertThat(IntegritySupport.detachedHash(reportFile)).isEqualTo(FreezeRecord.sha256(reportFile));
        assertThat(IntegritySupport.JSON.writeValueAsBytes(features))
                .containsExactly(IntegritySupport.JSON.writeValueAsBytes(PacketFeatureAudit.run(
                        f.packets(), f.seal(), null, f.opened(), ALPHA)));
    }
    @Test void changedPacketOrDifferentExportKeyFails() throws Exception {
        AuditFixture first = classB(temp.resolve("first"), 1, false, 0);
        AuditFixture second = classB(temp.resolve("second"), 2, false, 0);
        assertThatThrownBy(() -> PlacementAudit.run(second.packets(), second.seal(), null, first.opened(), ALPHA))
                .isInstanceOf(PlacementAudit.InvalidAudit.class);
        Path packet = first.packets().resolve("STUDY_TIP.jsonl");
        Files.writeString(packet, Files.readString(packet) + "\n");
        assertThatThrownBy(() -> PlacementAudit.run(first.packets(), first.seal(), null, first.opened(), ALPHA))
                .isInstanceOf(PlacementAudit.InvalidAudit.class);
        assertThatThrownBy(() -> PacketFeatureAudit.run(first.packets(), first.seal(), null, first.opened(), ALPHA))
                .isInstanceOf(PacketFeatureAudit.InvalidAudit.class);
    }
    @Test void equalLengthsAndLargeDifferenceUseFastSignTest() throws Exception {
        AuditFixture equal = classB(temp.resolve("equal"), 8, false, 0);
        var same = PacketFeatureAudit.run(equal.packets(), equal.seal(), null, equal.opened(), ALPHA);
        assertThat(same.overall().get("outputChars").ties()).isEqualTo(8);
        assertThat(same.overall().get("outputChars").flagged()).isFalse();
        List<Integer> largeDifferences = java.util.Collections.nCopies(300, 30_000);
        assertThatThrownBy(() -> ExactPairedTest.test(largeDifferences)).isInstanceOf(InvalidInput.class)
                .hasMessageContaining("polynomial exceeds supported bit index");
        AuditFixture large = classB(temp.resolve("large"), 300, false, 30_011);
        long started = System.nanoTime();
        var report = PacketFeatureAudit.run(large.packets(), large.seal(), null, large.opened(), ALPHA);
        assertThat(report.overall().get("outputChars").positive()).isEqualTo(300);
        assertThat(report.overall().get("outputChars").flagged()).isTrue();
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(30_000);
    }
    @Test void bonferroniAcrossFeaturesKeepsBorderlineSignTestUnflagged() throws Exception {
        AuditFixture f = classB(temp.resolve("six"), 6, false, 100);
        var feature = PacketFeatureAudit.run(f.packets(), f.seal(), null, f.opened(), ALPHA)
                .overall().get("outputChars");
        assertThat(feature.twoSidedP()).isEqualTo(Rational.of(1, 32));
        assertThat(feature.positive()).isEqualTo(6);
        assertThat(feature.flagged()).isFalse();
    }
    @Test void classBReserveIsReportedSeparately() throws Exception {
        AuditFixture f = classBWithReserve(temp.resolve("reserve"));
        var placement = PlacementAudit.run(f.packets(), f.seal(), null, f.opened(), ALPHA);
        var features = PacketFeatureAudit.run(f.packets(), f.seal(), null, f.opened(), ALPHA);
        assertThat(placement.overall().pairs()).isEqualTo(1);
        assertThat(placement.reserveOverall().pairs()).isEqualTo(1);
        assertThat(features.overall().get("outputChars").pairs()).isEqualTo(1);
        assertThat(features.reserveOverall().get("outputChars").pairs()).isEqualTo(1);
    }
    @Test void workflowMismatchMixedOutputAndWrongPairAreRefused() throws Exception {
        AuditFixture mismatch = malformedClassA(temp.resolve("mismatch"),
                List.of("LUNA", "LUNA", "BASELINE_MINI", "BASELINE_MINI"), true);
        assertThatThrownBy(() -> PlacementAudit.run(mismatch.packets(), mismatch.seal(), "LUNA", mismatch.opened(), ALPHA))
                .isInstanceOf(PlacementAudit.InvalidAudit.class);
        AuditFixture mixed = malformedClassA(temp.resolve("mixed"),
                List.of("LUNA", "BASELINE_MINI", "BASELINE_MINI", "BASELINE_MINI"), false);
        assertThatThrownBy(() -> PlacementAudit.run(mixed.packets(), mixed.seal(), "LUNA", mixed.opened(), ALPHA))
                .isInstanceOf(PlacementAudit.InvalidAudit.class);
        AuditFixture wrongPair = malformedClassA(temp.resolve("wrong"),
                List.of("BASELINE_MINI", "BASELINE_MINI", "BASELINE_41", "BASELINE_41"), false);
        assertThatThrownBy(() -> PlacementAudit.run(wrongPair.packets(), wrongPair.seal(), "LUNA", wrongPair.opened(), ALPHA))
                .isInstanceOf(PlacementAudit.InvalidAudit.class);
    }
}
