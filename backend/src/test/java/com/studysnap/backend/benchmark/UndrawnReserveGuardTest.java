package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the FRB anchored export guard for undrawn class-B reserves.
 * Limits: the seed and three 5h-1 overloads still export any reserve; their non-use is unenforced.
 * A 5h-1 export with the frozen commitment can pass provenance without its sidecar freeze hash being checked,
 * and provenance lists only drawn reserves. A re-hashed forged decision can pass the load-only guard because
 * primary-evidence verification belongs to FrbReserveStatus and FrbProvenance.
 */
class UndrawnReserveGuardTest {
    private static final String CASE_ID = "case-1";
    private static final String WORKFLOW = "STUDY_TIP";
    private static final String OTHER_WORKFLOW = "ASK_COMPANION";
    private static final String RESERVE_ID = "CLASS_B_RESERVE:" + WORKFLOW;
    private static final String PRIMARY_ID = "CLASS_B_PRIMARY";
    private static final String BASELINE = "BASELINE";
    private static final String CANDIDATE = "CANDIDATE";
    private static final String DIGEST = "d".repeat(64);
    private static final String PACKETS = "packets";
    private static final String ARM_FILE = "arm.jsonl";
    private static final String PACKET_FILE = "packets/STUDY_TIP.jsonl";
    private static final String INPUT = "input";
    private static final String CONTROL = "control";
    private static final String DRAWN = "drawn";
    private static final String SYNTHETIC = "synthetic";
    private static final String UNDRAWN = "undrawn";
    private static final String NEWLINE = "\n";
    private static final String DRAWN_JSON = "\"reserveDrawn\":true";
    private static final String UNDRAWN_JSON = "\"reserveDrawn\":false";
    private static final Map<String, ClassBScoreAggregator.Arm> LABELS = Map.of(
            BASELINE, ClassBScoreAggregator.Arm.BASELINE,
            CANDIDATE, ClassBScoreAggregator.Arm.CANDIDATE);
    @TempDir Path temp;

    private record Evidence(Path freeze, List<Stage1Receipt> receipts, List<Path> delivered,
            List<ClassBCaseManifest.Case> fixtures, List<ClassBBlindPacketExport.Output> outputs,
            PlacementSecret secret, String exportId) {}

    private Evidence classB(Path root, String exportId, boolean reserve) throws Exception {
        Files.createDirectories(root);
        PlacementSecret secret = PlacementSecretTest.fixed();
        Path freeze = PlacementFreezeRecordTest.write(root.resolve("frozen"),
                PlacementFreezeRecordTest.record(Map.of(exportId, secret.commitment())));
        List<Stage1Receipt> receipts = new ArrayList<>();
        List<Path> delivered = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (var entry : LABELS.entrySet()) {
            String arm = entry.getKey();
            String body = entry.getValue() == ClassBScoreAggregator.Arm.BASELINE ? "Answer one." : "Answer two.";
            DeliveredOutputs.Line line = new DeliveredOutputs.Line(CASE_ID, WORKFLOW, arm, DIGEST, 0,
                    CaseArmOutcome.State.COMPLETE, null, IntegritySupport.JSON.getNodeFactory().textNode(body));
            String text = IntegritySupport.JSON.writeValueAsString(line);
            Path path = root.resolve(arm + ".jsonl");
            Files.writeString(path, text + NEWLINE);
            Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(CASE_ID, WORKFLOW, DIGEST,
                    CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ONE, BigDecimal.ONE,
                    List.of(), List.of(), FreezeRecord.sha256(text.getBytes(StandardCharsets.UTF_8)));
            receipts.add(new Stage1Receipt("a".repeat(40), FreezeRecord.sha256(freeze), arm, SYNTHETIC,
                    StageCapLedger.Stage.STAGE_1, Instant.EPOCH, Instant.EPOCH, 1, false,
                    "c".repeat(64), List.of(row), Map.of(), FreezeRecord.sha256(path)));
            delivered.add(path);
            outputs.add(new ClassBBlindPacketExport.Output(CASE_ID, entry.getValue(), body));
        }
        var fixture = new ClassBCaseManifest.Case(CASE_ID, ClassBCaseManifest.Workflow.STUDY_TIP,
                SYNTHETIC, reserve, LearnerLevel.COLLEGE,
                List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                null, null, null, null, null, null, null, null);
        return new Evidence(freeze, receipts, delivered, List.of(fixture), outputs, secret, exportId);
    }

    private Path decision(String name, String workflow, boolean drawn) throws Exception {
        Path file = temp.resolve(name + ".json");
        var row = new ReserveDecisionRecord.WorkflowDecision(workflow, 1, drawn, false, DIGEST);
        var record = new ReserveDecisionRecord(1, List.of(row), DIGEST, DIGEST, "2026-11-19T00:00:00Z");
        IntegritySupport.create(file, IntegritySupport.JSON.writeValueAsBytes(record));
        assertThat(ReserveDecisionRecord.load(file).reserveDrawnByWorkflow()).containsEntry(workflow, drawn);
        return file;
    }

    private static ClassBBlindPacketExport.Export aware(Evidence evidence, Path root, Path record) {
        return ClassBBlindPacketExport.export(evidence.fixtures(), evidence.outputs(), root.resolve(PACKETS),
                root.resolve(ARM_FILE), evidence.delivered(), evidence.freeze(), evidence.receipts(),
                evidence.exportId(), evidence.secret(), LABELS, record);
    }

    private static ClassBBlindPacketExport.Export oldNine(Evidence evidence, Path root) {
        return ClassBBlindPacketExport.export(evidence.fixtures(), evidence.outputs(), root.resolve(PACKETS),
                root.resolve(ARM_FILE), evidence.delivered(), evidence.freeze(), evidence.receipts(),
                evidence.exportId(), evidence.secret());
    }

    private static ClassBBlindPacketExport.Export oldLabels(Evidence evidence, Path root) {
        return ClassBBlindPacketExport.export(evidence.fixtures(), evidence.outputs(), root.resolve(PACKETS),
                root.resolve(ARM_FILE), evidence.delivered(), evidence.freeze(), evidence.receipts(),
                evidence.exportId(), evidence.secret(), LABELS);
    }

    private static void assertNoOutput(Path root) {
        assertThat(Files.exists(root.resolve(PACKETS))).isFalse();
        assertThat(Files.exists(root.resolve(ARM_FILE))).isFalse();
        assertThat(Files.exists(root)).isFalse();
    }

    private static void assertRefused(Evidence evidence, Path root, Path record) {
        assertThatThrownBy(() -> aware(evidence, root, record))
                .isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertNoOutput(root);
    }

    @Test void drawnReserveExportsWithReserveIdInSidecarAndSeal() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), RESERVE_ID, true);
        Path record = decision(DRAWN, WORKFLOW, true);
        Path root = temp.resolve("drawn-output");
        aware(evidence, root, record);
        Path sidecar = PlacementSidecar.beside(root.resolve(ARM_FILE));
        assertThat(PlacementSidecar.load(sidecar).exportId()).isEqualTo(RESERVE_ID);
        ArmKeySeal.Seal seal = ArmKeySeal.seal(root.resolve(ARM_FILE), root.resolve(PACKETS),
                root.resolve("seal.json"), ArmKeyGateFixtures.SEALED, sidecar);
        assertThat(seal.placementExportId()).isEqualTo(RESERVE_ID);
    }

    @Test void falseDecisionRefusesOtherwiseValidReserve() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), RESERVE_ID, true);
        aware(evidence, temp.resolve(CONTROL), decision(DRAWN, WORKFLOW, true));
        assertRefused(evidence, temp.resolve("false-output"), decision("false", WORKFLOW, false));
    }

    @Test void knownWorkflowAbsentFromRecordRefusesOtherwiseValidReserve() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), RESERVE_ID, true);
        aware(evidence, temp.resolve(CONTROL), decision(DRAWN, WORKFLOW, true));
        assertRefused(evidence, temp.resolve("absent-output"), decision("other", OTHER_WORKFLOW, true));
    }

    @Test void missingTamperedAndNullDecisionRecordsRefuseOtherwiseValidReserve() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), RESERVE_ID, true);
        Path record = decision(DRAWN, WORKFLOW, true);
        aware(evidence, temp.resolve(CONTROL), record);
        assertRefused(evidence, temp.resolve("missing-output"), temp.resolve("missing.json"));
        Files.writeString(record, Files.readString(record).replace(DRAWN_JSON,
                UNDRAWN_JSON));
        assertRefused(evidence, temp.resolve("tampered-output"), record);
        assertRefused(evidence, temp.resolve("null-output"), null);
    }

    @Test void undrawnRecordFlippedTowardDrawnWithoutRehashIsRefused() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), RESERVE_ID, true);
        aware(evidence, temp.resolve(CONTROL), decision(DRAWN, WORKFLOW, true));
        Path record = decision(UNDRAWN, WORKFLOW, false);
        Files.writeString(record, Files.readString(record).replace(UNDRAWN_JSON, DRAWN_JSON));
        assertRefused(evidence, temp.resolve("flipped-output"), record);
    }

    @Test void primaryAcceptsNullRecordAndRefusesSuppliedRecord() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), PRIMARY_ID, false);
        Path record = decision(DRAWN, WORKFLOW, true);
        aware(evidence, temp.resolve("primary-control"), null);
        assertRefused(evidence, temp.resolve("primary-refused"), record);
    }

    @Test void oldNineArgumentAndArmLabelOverloadsRefuseReserveIds() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), RESERVE_ID, true);
        aware(evidence, temp.resolve(CONTROL), decision(DRAWN, WORKFLOW, true));
        Path nine = temp.resolve("nine-refused");
        assertThatThrownBy(() -> oldNine(evidence, nine))
                .isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertNoOutput(nine);
        Path labels = temp.resolve("labels-refused");
        assertThatThrownBy(() -> oldLabels(evidence, labels))
                .isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertNoOutput(labels);
    }

    @Test void drawnPathStillChecksReceiptFreezeSecretAndDeliveredOutputs() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), RESERVE_ID, true);
        Path record = decision(DRAWN, WORKFLOW, true);
        aware(evidence, temp.resolve(CONTROL), record);
        Path otherFreeze = PlacementFreezeRecordTest.write(temp.resolve("other-freeze"),
                PlacementFreezeRecordTest.record(Map.of(RESERVE_ID, evidence.secret().commitment(),
                        PRIMARY_ID, DIGEST)));
        Evidence wrongReceipt = new Evidence(otherFreeze, evidence.receipts(), evidence.delivered(),
                evidence.fixtures(), evidence.outputs(), evidence.secret(), RESERVE_ID);
        Path receiptRoot = temp.resolve("receipt-refused");
        assertRefused(wrongReceipt, receiptRoot, record);
        byte[] altered = evidence.secret().raw();
        altered[0] ^= 1;
        PlacementSecret wrong = new PlacementSecret(altered);
        Evidence wrongSecret = new Evidence(evidence.freeze(), evidence.receipts(), evidence.delivered(),
                evidence.fixtures(), evidence.outputs(), wrong, RESERVE_ID);
        Path secretRoot = temp.resolve("secret-refused");
        assertRefused(wrongSecret, secretRoot, record);
        List<ClassBBlindPacketExport.Output> changed = new ArrayList<>(evidence.outputs());
        changed.set(0, new ClassBBlindPacketExport.Output(CASE_ID, changed.getFirst().arm(), "Other answer."));
        Evidence wrongOutputs = new Evidence(evidence.freeze(), evidence.receipts(), evidence.delivered(),
                evidence.fixtures(), changed, evidence.secret(), RESERVE_ID);
        Path deliveredRoot = temp.resolve("delivered-refused");
        assertRefused(wrongOutputs, deliveredRoot, record);
    }

    @Test void rehashedForgedDecisionIsAcceptedByLoadOnlyGuard() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), RESERVE_ID, true);
        Path record = decision(UNDRAWN, WORKFLOW, false);
        assertRefused(evidence, temp.resolve("before-forgery"), record);
        Files.writeString(record, Files.readString(record).replace(UNDRAWN_JSON,
                DRAWN_JSON));
        Files.writeString(IntegritySupport.detached(record), FreezeRecord.sha256(record) + NEWLINE);
        aware(evidence, temp.resolve("forged-output"), record);
        assertThat(Files.exists(temp.resolve("forged-output/packets/STUDY_TIP.jsonl"))).isTrue();
    }

    @Test void seedAndFiveHOneStillExportUndrawnReserve() throws Exception {
        Evidence evidence = classB(temp.resolve(INPUT), RESERVE_ID, true);
        Path record = decision(UNDRAWN, WORKFLOW, false);
        assertRefused(evidence, temp.resolve("anchored-refused"), record);
        Path seed = temp.resolve("seed-output");
        ClassBBlindPacketExport.export(evidence.fixtures(), evidence.outputs(), seed.resolve(PACKETS),
                seed.resolve(ARM_FILE), 17L);
        assertThat(Files.exists(seed.resolve(PACKET_FILE))).isTrue();
        // PlacementSealIntegrationTest also proves the seven-argument 5h-1 reserve path remains available.
        Path fiveHOne = temp.resolve("five-h-one-output");
        ClassBBlindPacketExport.export(evidence.fixtures(), evidence.outputs(), fiveHOne.resolve(PACKETS),
                fiveHOne.resolve(ARM_FILE), RESERVE_ID, evidence.secret(), evidence.secret().commitment());
        assertThat(PlacementSidecar.load(PlacementSidecar.beside(fiveHOne.resolve(ARM_FILE))).exportId())
                .isEqualTo(RESERVE_ID);
    }
}
