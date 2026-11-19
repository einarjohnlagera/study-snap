package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/** Pins behaviors the first anchored-export tests did not reach. */
class AnchoredPlacementGapTest {
    private static final String CASE_ID = "case-1";
    private static final String CLASS_A_ID = "CLASS_A:BASELINE_MINI-LUNA";
    private static final String CLASS_B_ID = "CLASS_B_PRIMARY";
    private static final String WORKFLOW_A = "NOTE_ADAPTIVE";
    private static final String WORKFLOW_B = "STUDY_TIP";
    private static final String DIGEST = "d".repeat(64);
    private static final String BASELINE_MINI = "BASELINE_MINI";
    private static final String LUNA = "LUNA";
    @TempDir Path temp;

    private record Evidence<F, O>(Path freeze, List<Stage1Receipt> receipts, List<Path> delivered,
                                  List<F> fixtures, List<O> outputs) {}

    private static void rehash(Path file) throws Exception {
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(file) + "\n");
    }

    private static final List<String> LEGACY_FIELDS = List.of("gitCommit", "preregistrationSha256",
            "parametersSha256", "manifests", "prompts", "arms", "baselineModelsByRoute", "ownerApprovalDate",
            "recheckEligibleFailureClasses", "evidenceMappingSha256", "compatibilitySuites",
            "costForecastUsdPerDeliveredCase");

    @Test void legacyFreezeMissingAnyRequiredFieldIsRefused() throws Exception {
        for (String field : LEGACY_FIELDS) {
            Path file = PlacementFreezeRecordTest.write(temp.resolve("legacy-" + field),
                    PlacementFreezeRecordTest.record(null));
            ObjectNode tree = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(file));
            assertThat(tree.has(field)).as(field).isTrue();
            tree.remove(field);
            Files.writeString(file, tree.toString());
            rehash(file);
            assertThatThrownBy(() -> FreezeRecord.load(file)).as(field)
                    .isInstanceOf(FreezeRecord.InvalidFreeze.class);
        }
    }

    @Test void anchoredFreezeMissingARequiredFieldIsRefused() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Path file = PlacementFreezeRecordTest.write(temp.resolve("anchored"),
                PlacementFreezeRecordTest.record(Map.of(CLASS_B_ID, secret.commitment())));
        ObjectNode tree = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(file));
        tree.remove("arms");
        Files.writeString(file, tree.toString());
        rehash(file);
        assertThatThrownBy(() -> FreezeRecord.load(file)).isInstanceOf(FreezeRecord.InvalidFreeze.class);
    }

    @Test void freezeWithTrailingContentIsRefused() throws Exception {
        Path file = PlacementFreezeRecordTest.write(temp.resolve("trailing"), PlacementFreezeRecordTest.record(null));
        Files.writeString(file, Files.readString(file) + "{}");
        rehash(file);
        assertThatThrownBy(() -> FreezeRecord.load(file)).isInstanceOf(FreezeRecord.InvalidFreeze.class);
    }

    private Evidence<ClassACaseManifest.Case, ClassABlindPacketExport.Output> classA(Path root,
            PlacementSecret secret) throws Exception {
        Path freeze = PlacementFreezeRecordTest.write(root.resolve("frozen"),
                PlacementFreezeRecordTest.record(Map.of(CLASS_A_ID, secret.commitment())));
        var source = new ClassACaseManifest.Source("Cells", "Cells have membranes.", "Membrane",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        var fixture = new ClassACaseManifest.Case(CASE_ID, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                List.of(source), 1, null, false, null);
        ObjectNode question = IntegritySupport.JSON.createObjectNode().put("question", "What surrounds cells?")
                .put("explanation", "The membrane surrounds cells.").put("correctIndex", 0);
        question.putArray("choices").add("membrane").add("nucleus").add("wall").add("cytoplasm");
        JsonNode body = IntegritySupport.JSON.createArrayNode().add(question);
        List<Stage1Receipt> receipts = new ArrayList<>();
        List<Path> delivered = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        for (String arm : List.of(BASELINE_MINI, LUNA)) {
            DeliveredOutputs.Line line = new DeliveredOutputs.Line(CASE_ID, WORKFLOW_A, arm, DIGEST, 0,
                    CaseArmOutcome.State.COMPLETE, null, body);
            String text = IntegritySupport.JSON.writeValueAsString(line);
            Path path = root.resolve(arm + ".jsonl");
            Files.writeString(path, text + "\n");
            Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(CASE_ID, WORKFLOW_A, DIGEST,
                    CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ONE, BigDecimal.ONE,
                    List.of(), List.of(), FreezeRecord.sha256(text.getBytes(StandardCharsets.UTF_8)));
            receipts.add(new Stage1Receipt("a".repeat(40), FreezeRecord.sha256(freeze), arm, "synthetic",
                    StageCapLedger.Stage.STAGE_1, Instant.EPOCH, Instant.EPOCH, 1, false,
                    "c".repeat(64), List.of(row), Map.of(), FreezeRecord.sha256(path)));
            delivered.add(path);
            outputs.add(new ClassABlindPacketExport.Output(CASE_ID, arm, body));
        }
        return new Evidence<>(freeze, receipts, delivered, List.of(fixture), outputs);
    }

    @Test void classAUnreceiptedOutputAndExtraDeliveredPathAreRefusedBeforeWrites() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        var evidence = classA(temp.resolve("class-a"), secret);
        List<ClassABlindPacketExport.Output> extra = new ArrayList<>(evidence.outputs());
        extra.add(new ClassABlindPacketExport.Output("unreceipted", BASELINE_MINI, evidence.outputs().getFirst().questions()));
        Path first = temp.resolve("refused-output");
        Path firstPackets = first.resolve("packets");
        Path firstArm = first.resolve("arm.jsonl");
        assertThatThrownBy(() -> ClassABlindPacketExport.export(evidence.fixtures(), extra, BASELINE_MINI, LUNA,
                firstPackets, firstArm, evidence.delivered(), evidence.freeze(), evidence.receipts(),
                CLASS_A_ID, secret)).isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(first)).isFalse();
        List<Path> paths = new ArrayList<>(evidence.delivered());
        paths.add(evidence.delivered().getFirst());
        Path second = temp.resolve("refused-path");
        Path secondPackets = second.resolve("packets");
        Path secondArm = second.resolve("arm.jsonl");
        assertThatThrownBy(() -> ClassABlindPacketExport.export(evidence.fixtures(), evidence.outputs(),
                BASELINE_MINI, LUNA, secondPackets, secondArm, paths, evidence.freeze(), evidence.receipts(),
                CLASS_A_ID, secret)).isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(second)).isFalse();
    }

    private Evidence<ClassBCaseManifest.Case, ClassBBlindPacketExport.Output> classB(Path root,
            PlacementSecret secret) throws Exception {
        Path freeze = PlacementFreezeRecordTest.write(root.resolve("frozen"),
                PlacementFreezeRecordTest.record(Map.of(CLASS_B_ID, secret.commitment())));
        List<Stage1Receipt> receipts = new ArrayList<>();
        List<Path> delivered = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        Map<String, ClassBScoreAggregator.Arm> labels = Map.of(BASELINE_MINI, ClassBScoreAggregator.Arm.BASELINE,
                LUNA, ClassBScoreAggregator.Arm.CANDIDATE);
        for (var entry : labels.entrySet()) {
            String arm = entry.getKey();
            String body = entry.getValue() == ClassBScoreAggregator.Arm.BASELINE ? "Answer one." : "Answer two.";
            DeliveredOutputs.Line line = new DeliveredOutputs.Line(CASE_ID, WORKFLOW_B, arm, DIGEST, 0,
                    CaseArmOutcome.State.COMPLETE, null, IntegritySupport.JSON.getNodeFactory().textNode(body));
            String text = IntegritySupport.JSON.writeValueAsString(line);
            Path path = root.resolve(arm + ".jsonl");
            Files.writeString(path, text + "\n");
            Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(CASE_ID, WORKFLOW_B, DIGEST,
                    CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ONE, BigDecimal.ONE,
                    List.of(), List.of(), FreezeRecord.sha256(text.getBytes(StandardCharsets.UTF_8)));
            receipts.add(new Stage1Receipt("a".repeat(40), FreezeRecord.sha256(freeze), arm, "synthetic",
                    StageCapLedger.Stage.STAGE_1, Instant.EPOCH, Instant.EPOCH, 1, false,
                    "c".repeat(64), List.of(row), Map.of(), FreezeRecord.sha256(path)));
            delivered.add(path);
            outputs.add(new ClassBBlindPacketExport.Output(CASE_ID, entry.getValue(), body));
        }
        var fixture = new ClassBCaseManifest.Case(CASE_ID, ClassBCaseManifest.Workflow.STUDY_TIP,
                "synthetic", false, LearnerLevel.COLLEGE,
                List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                null, null, null, null, null, null, null, null);
        return new Evidence<>(freeze, receipts, delivered, List.of(fixture), outputs);
    }

    @Test void classBAnchoredExportAcceptsRealFrozenArmNamesThroughALabelMap() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        var evidence = classB(temp.resolve("class-b"), secret);
        Map<String, ClassBScoreAggregator.Arm> labels = Map.of(BASELINE_MINI, ClassBScoreAggregator.Arm.BASELINE,
                LUNA, ClassBScoreAggregator.Arm.CANDIDATE);
        Path output = temp.resolve("class-b-output");
        ClassBBlindPacketExport.export(evidence.fixtures(), evidence.outputs(), output.resolve("packets"),
                output.resolve("arm.jsonl"), evidence.delivered(), evidence.freeze(), evidence.receipts(),
                CLASS_B_ID, secret, labels);
        assertThat(PlacementSidecar.load(PlacementSidecar.beside(output.resolve("arm.jsonl"))).freezeSha256())
                .isEqualTo(FreezeRecord.sha256(evidence.freeze()));
    }

    @Test void classBAnchoredExportRefusesAnUnmappedOrMissingArmLabelBeforeWrites() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        var evidence = classB(temp.resolve("class-b-refused"), secret);
        Map<String, ClassBScoreAggregator.Arm> partial = Map.of(BASELINE_MINI, ClassBScoreAggregator.Arm.BASELINE);
        Path output = temp.resolve("class-b-refused-output");
        Path packets = output.resolve("packets");
        Path arm = output.resolve("arm.jsonl");
        assertThatThrownBy(() -> ClassBBlindPacketExport.export(evidence.fixtures(), evidence.outputs(), packets,
                arm, evidence.delivered(), evidence.freeze(), evidence.receipts(), CLASS_B_ID, secret, partial))
                .isInstanceOf(InvalidPlacementSeal.class);
        assertThatThrownBy(() -> ClassBBlindPacketExport.export(evidence.fixtures(), evidence.outputs(), packets,
                arm, evidence.delivered(), evidence.freeze(), evidence.receipts(), CLASS_B_ID, secret))
                .isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(output)).isFalse();
    }
}
