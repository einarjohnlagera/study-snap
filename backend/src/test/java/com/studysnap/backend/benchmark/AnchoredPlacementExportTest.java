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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A receipt pins the verified pre-dispatch freeze, so F1 generation cannot be exported under F2. */
class AnchoredPlacementExportTest {
    private static final String EXPORT_ID = "CLASS_B_PRIMARY";
    private static final String CASE_ID = "case-1";
    private static final String WORKFLOW = "STUDY_TIP";
    @TempDir Path temp;

    private record Evidence(Path freeze, List<Stage1Receipt> receipts, List<Path> delivered,
                            List<ClassBCaseManifest.Case> fixtures,
                            List<ClassBBlindPacketExport.Output> outputs) {}

    private Evidence evidence(Path root, Map<String, String> commitments) throws Exception {
        Path freeze = PlacementFreezeRecordTest.write(root.resolve("frozen"),
                PlacementFreezeRecordTest.record(commitments));
        String freezeHash = FreezeRecord.sha256(freeze);
        List<Stage1Receipt> receipts = new ArrayList<>();
        List<Path> delivered = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (ClassBScoreAggregator.Arm arm : ClassBScoreAggregator.Arm.values()) {
            String body = arm == ClassBScoreAggregator.Arm.BASELINE ? "Answer one." : "Answer two.";
            JsonNode content = IntegritySupport.JSON.getNodeFactory().textNode(body);
            DeliveredOutputs.Line line = new DeliveredOutputs.Line(CASE_ID, WORKFLOW, arm.name(),
                    "d".repeat(64), 0, CaseArmOutcome.State.COMPLETE, null, content);
            String text = IntegritySupport.JSON.writeValueAsString(line);
            Path path = root.resolve(arm.name() + ".jsonl");
            Files.writeString(path, text + "\n");
            String lineHash = FreezeRecord.sha256(text.getBytes(StandardCharsets.UTF_8));
            Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(CASE_ID, WORKFLOW,
                    "d".repeat(64), CaseArmOutcome.State.COMPLETE, true, true, null,
                    BigDecimal.ONE, BigDecimal.ONE, List.of(), List.of(), lineHash);
            receipts.add(new Stage1Receipt("a".repeat(40), freezeHash, arm.name(), "synthetic",
                    StageCapLedger.Stage.STAGE_1, Instant.EPOCH, Instant.EPOCH, 1, false,
                    "c".repeat(64), List.of(row), Map.of(), FreezeRecord.sha256(path)));
            delivered.add(path);
            outputs.add(new ClassBBlindPacketExport.Output(CASE_ID, arm, body));
        }
        var fixture = new ClassBCaseManifest.Case(CASE_ID, ClassBCaseManifest.Workflow.STUDY_TIP,
                "synthetic", false, LearnerLevel.COLLEGE,
                List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                null, null, null, null, null, null, null, null);
        return new Evidence(freeze, receipts, delivered, List.of(fixture), outputs);
    }

    private ClassBBlindPacketExport.Export export(Evidence evidence, Path output,
            Path freeze, List<Stage1Receipt> receipts, List<ClassBBlindPacketExport.Output> outputs,
            PlacementSecret secret) {
        return ClassBBlindPacketExport.export(evidence.fixtures(), outputs, output.resolve("packets"),
                output.resolve("arm.jsonl"), evidence.delivered(), freeze, receipts, EXPORT_ID, secret);
    }

    @Test void faithfulReceiptAndFreezeProduceSidecarHashAndThreeSealComponents() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Evidence evidence = evidence(temp.resolve("input"), Map.of(EXPORT_ID, secret.commitment()));
        Path output = temp.resolve("output");
        export(evidence, output, evidence.freeze(), evidence.receipts(), evidence.outputs(), secret);
        Path sidecar = PlacementSidecar.beside(output.resolve("arm.jsonl"));
        assertThat(PlacementSidecar.load(sidecar).freezeSha256()).isEqualTo(FreezeRecord.sha256(evidence.freeze()));
        ArmKeySeal.Seal seal = ArmKeySeal.seal(output.resolve("arm.jsonl"), output.resolve("packets"),
                output.resolve("seal.json"), ArmKeyGateFixtures.SEALED, sidecar);
        assertThat(seal.placementExportId()).isEqualTo(EXPORT_ID);
        assertThat(seal.placementCommitmentSha256()).isEqualTo(secret.commitment());
        assertThat(seal.placementSidecarSha256()).isEqualTo(FreezeRecord.sha256(sidecar));
        String secretHex = secret.hex();
        String seed = Long.toString(secret.seedLong());
        for (Path file : List.of(evidence.freeze(), output.resolve("arm.jsonl"), output.resolve("seal.json")))
            assertThat(Files.readString(file)).doesNotContain(secretHex, seed);
        assertThat(evidence.receipts().toString()).doesNotContain(secretHex, seed,
                Long.toHexString(secret.seedLong()));
    }

    @Test void generationUnderF1CannotBeExportedUnderLaterF2() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Evidence first = evidence(temp.resolve("first"), null);
        Path later = PlacementFreezeRecordTest.write(temp.resolve("later"),
                PlacementFreezeRecordTest.record(Map.of(EXPORT_ID, secret.commitment())));
        Path output = temp.resolve("output");
        List<Stage1Receipt> firstReceipts = first.receipts();
        List<ClassBBlindPacketExport.Output> firstOutputs = first.outputs();
        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> export(first, output,
                later, firstReceipts, firstOutputs, secret));
        assertThat(failure).isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertRedacted(failure, secret);
        assertThat(Files.exists(output)).isFalse();
    }

    @Test void mixedReceiptsMissingCommitmentAndWrongSecretRefuseBeforeWrites() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Evidence first = evidence(temp.resolve("first"), Map.of(EXPORT_ID, secret.commitment()));
        Evidence other = evidence(temp.resolve("other"), Map.of(EXPORT_ID, secret.commitment(),
                "CLASS_B_RESERVE:STUDY_TIP", "2".repeat(64)));
        Path output = temp.resolve("output");
        List<Stage1Receipt> mixed = List.of(first.receipts().getFirst(), other.receipts().getLast());
        Path firstFreeze = first.freeze();
        List<Stage1Receipt> firstReceipts = first.receipts();
        List<ClassBBlindPacketExport.Output> firstOutputs = first.outputs();
        assertThatThrownBy(() -> export(first, output, firstFreeze, mixed, firstOutputs, secret))
                .isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(output)).isFalse();
        Evidence missing = evidence(temp.resolve("missing"), null);
        Path missingFreeze = missing.freeze();
        List<Stage1Receipt> missingReceipts = missing.receipts();
        List<ClassBBlindPacketExport.Output> missingOutputs = missing.outputs();
        assertThatThrownBy(() -> export(missing, output, missingFreeze, missingReceipts,
                missingOutputs, secret)).isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(output)).isFalse();
        byte[] changed = secret.raw();
        changed[0] ^= 1;
        PlacementSecret wrong = new PlacementSecret(changed);
        assertThatThrownBy(() -> export(first, output, firstFreeze, firstReceipts,
                firstOutputs, wrong)).isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(output)).isFalse();
    }

    @Test void classBOutputWithoutReceiptRowIsRefusedBeforeWrites() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Evidence evidence = evidence(temp.resolve("input"), Map.of(EXPORT_ID, secret.commitment()));
        List<ClassBBlindPacketExport.Output> extra = new ArrayList<>(evidence.outputs());
        extra.add(new ClassBBlindPacketExport.Output("unreceipted", ClassBScoreAggregator.Arm.BASELINE,
                "Other answer."));
        Path output = temp.resolve("output");
        Path freeze = evidence.freeze();
        List<Stage1Receipt> receipts = evidence.receipts();
        assertThatThrownBy(() -> export(evidence, output, freeze, receipts, extra, secret))
                .isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(output)).isFalse();
    }

    @Test void missingFreezeCommitmentEntryAloneRefusesBeforeWrites() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Evidence evidence = evidence(temp.resolve("input"), null);
        Path output = temp.resolve("output");
        Path freeze = evidence.freeze();
        List<Stage1Receipt> receipts = evidence.receipts();
        List<ClassBBlindPacketExport.Output> outputs = evidence.outputs();
        assertThatThrownBy(() -> export(evidence, output, freeze, receipts, outputs, secret))
                .isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertThat(Files.exists(output)).isFalse();
    }

    @Test void wrongSecretAloneRefusesBeforeWrites() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Evidence evidence = evidence(temp.resolve("input"), Map.of(EXPORT_ID, secret.commitment()));
        Path output = temp.resolve("output");
        Path freeze = evidence.freeze();
        List<Stage1Receipt> receipts = evidence.receipts();
        List<ClassBBlindPacketExport.Output> outputs = evidence.outputs();
        byte[] changed = secret.raw();
        changed[0] ^= 1;
        PlacementSecret wrong = new PlacementSecret(changed);
        assertThatThrownBy(() -> export(evidence, output, freeze, receipts, outputs, wrong))
                .isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertThat(Files.exists(output)).isFalse();
    }

    @Test void classAAnchoredExportBuildsOutputsThroughReceiptValidatedLoader() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        String id = "CLASS_A:BASELINE_MINI-LUNA";
        Path root = temp.resolve("class-a");
        Path freeze = PlacementFreezeRecordTest.write(root.resolve("frozen"),
                PlacementFreezeRecordTest.record(Map.of(id, secret.commitment())));
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
        for (String arm : List.of("BASELINE_MINI", "LUNA")) {
            DeliveredOutputs.Line line = new DeliveredOutputs.Line(CASE_ID, "NOTE_ADAPTIVE", arm,
                    "d".repeat(64), 0, CaseArmOutcome.State.COMPLETE, null, body);
            String text = IntegritySupport.JSON.writeValueAsString(line);
            Path path = root.resolve(arm + ".jsonl");
            Files.writeString(path, text + "\n");
            Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(CASE_ID, "NOTE_ADAPTIVE",
                    "d".repeat(64), CaseArmOutcome.State.COMPLETE, true, true, null,
                    BigDecimal.ONE, BigDecimal.ONE, List.of(), List.of(),
                    FreezeRecord.sha256(text.getBytes(StandardCharsets.UTF_8)));
            receipts.add(new Stage1Receipt("a".repeat(40), FreezeRecord.sha256(freeze), arm, "synthetic",
                    StageCapLedger.Stage.STAGE_1, Instant.EPOCH, Instant.EPOCH, 1, false,
                    "c".repeat(64), List.of(row), Map.of(), FreezeRecord.sha256(path)));
            delivered.add(path);
            outputs.add(new ClassABlindPacketExport.Output(CASE_ID, arm, body));
        }
        Path output = temp.resolve("class-a-output");
        ClassABlindPacketExport.export(List.of(fixture), outputs, "BASELINE_MINI", "LUNA",
                output.resolve("packets"), output.resolve("arm.jsonl"), delivered, freeze, receipts, id, secret);
        assertThat(PlacementSidecar.load(PlacementSidecar.beside(output.resolve("arm.jsonl")))
                .freezeSha256()).isEqualTo(FreezeRecord.sha256(freeze));
        Files.writeString(delivered.getFirst(), "tampered\n");
        Path refused = temp.resolve("class-a-refused");
        List<ClassACaseManifest.Case> fixtures = List.of(fixture);
        Path refusedPackets = refused.resolve("packets");
        Path refusedArm = refused.resolve("arm.jsonl");
        assertThatThrownBy(() -> ClassABlindPacketExport.export(fixtures, outputs,
                "BASELINE_MINI", "LUNA", refusedPackets, refusedArm,
                delivered, freeze, receipts, id, secret)).isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(refused)).isFalse();
    }

    @Test void freezeModeAuditVerifiesAnchorAndRefusesOtherFreezeOrMissingEntry() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Evidence evidence = evidence(temp.resolve("input"), Map.of(EXPORT_ID, secret.commitment()));
        Path output = temp.resolve("output");
        export(evidence, output, evidence.freeze(), evidence.receipts(), evidence.outputs(), secret);
        Path sidecar = PlacementSidecar.beside(output.resolve("arm.jsonl"));
        Path seal = output.resolve("seal.json");
        ArmKeySeal.seal(output.resolve("arm.jsonl"), output.resolve("packets"), seal,
                ArmKeyGateFixtures.SEALED, sidecar);
        Path finding = ArmKeyGateFixtures.findings(output);
        Path lock = ArmKeyGateFixtures.lock(output, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(output.resolve("arm.jsonl"), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, finding)), Set.of(LockRecord.Kind.PRIMARY),
                output.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "anchored audit");
        var report = PlacementAudit.run(output.resolve("packets"), seal, null, opened,
                Rational.of(1, 20), EXPORT_ID, Map.of(WORKFLOW, Set.of(CASE_ID)), evidence.freeze());
        assertThat(report.seedCheck()).isEqualTo("VERIFIED");
        assertThat(report.commitmentAnchor()).isEqualTo("FREEZE");
        assertThat(report.toString()).doesNotContain(CASE_ID, "blind-", secret.hex(),
                Long.toString(secret.seedLong()), Long.toHexString(secret.seedLong()));
        assertThat(IntegritySupport.JSON.writeValueAsString(report)).doesNotContain(CASE_ID, "blind-",
                secret.hex(), Long.toString(secret.seedLong()), Long.toHexString(secret.seedLong()));
        Path other = PlacementFreezeRecordTest.write(temp.resolve("other-freeze"),
                PlacementFreezeRecordTest.record(Map.of(EXPORT_ID, secret.commitment(),
                        "CLASS_B_RESERVE:STUDY_TIP", "2".repeat(64))));
        var mismatch = PlacementAudit.run(output.resolve("packets"), seal, null, opened,
                Rational.of(1, 20), EXPORT_ID, Map.of(WORKFLOW, Set.of(CASE_ID)), other);
        assertThat(mismatch.seedCheck()).isEqualTo("MISMATCH");
        assertThat(mismatch.toString()).doesNotContain(secret.hex(), Long.toString(secret.seedLong()),
                Long.toHexString(secret.seedLong()), CASE_ID, "blind-");
        Path missing = PlacementFreezeRecordTest.write(temp.resolve("missing-freeze"),
                PlacementFreezeRecordTest.record(null));
        var absent = PlacementAudit.run(output.resolve("packets"), seal, null, opened,
                Rational.of(1, 20), EXPORT_ID, Map.of(WORKFLOW, Set.of(CASE_ID)), missing);
        assertThat(absent.seedCheck()).isEqualTo("COMMITMENT_NOT_PROVIDED");
        assertThat(absent.commitmentAnchor()).isEqualTo("FREEZE");
    }

    @Test void freezeModeAuditRefusesUnanchoredSidecarEvenWithMatchingCommitment() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Evidence evidence = evidence(temp.resolve("input"), Map.of(EXPORT_ID, secret.commitment()));
        Path output = temp.resolve("unanchored");
        ClassBBlindPacketExport.export(evidence.fixtures(), evidence.outputs(), output.resolve("packets"),
                output.resolve("arm.jsonl"), EXPORT_ID, secret, secret.commitment());
        Path sidecar = PlacementSidecar.beside(output.resolve("arm.jsonl"));
        assertThat(PlacementSidecar.load(sidecar).freezeSha256()).isNull();
        Path seal = output.resolve("seal.json");
        ArmKeySeal.seal(output.resolve("arm.jsonl"), output.resolve("packets"), seal,
                ArmKeyGateFixtures.SEALED, sidecar);
        Path finding = ArmKeyGateFixtures.findings(output);
        Path lock = ArmKeyGateFixtures.lock(output, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(output.resolve("arm.jsonl"), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, finding)), Set.of(LockRecord.Kind.PRIMARY),
                output.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "unanchored audit");
        var report = PlacementAudit.run(output.resolve("packets"), seal, null, opened,
                Rational.of(1, 20), EXPORT_ID, Map.of(WORKFLOW, Set.of(CASE_ID)), evidence.freeze());
        assertThat(report.seedCheck()).isEqualTo("MISMATCH");
        assertThat(report.commitmentAnchor()).isEqualTo("FREEZE");
    }

    @Test void anchoredFailureMessagesAndCauseChainsNeverExposeSecretOrSeed() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Evidence evidence = evidence(temp.resolve("input"), Map.of(EXPORT_ID, secret.commitment()));
        Path output = temp.resolve("output");
        Path freeze = evidence.freeze();
        List<Stage1Receipt> receipts = evidence.receipts();
        List<ClassBBlindPacketExport.Output> outputs = evidence.outputs();
        byte[] changed = secret.raw();
        changed[0] ^= 1;
        PlacementSecret wrong = new PlacementSecret(changed);
        Throwable mismatch = org.assertj.core.api.Assertions.catchThrowable(() ->
                export(evidence, output, freeze, receipts, outputs, wrong));
        assertThat(mismatch).isInstanceOf(InvalidPlacementSeal.class);
        assertRedacted(mismatch, secret);
        Path missing = PlacementFreezeRecordTest.write(temp.resolve("missing"),
                PlacementFreezeRecordTest.record(null));
        Throwable absent = org.assertj.core.api.Assertions.catchThrowable(() ->
                export(evidence, output, missing, receipts, outputs, secret));
        assertThat(absent).isInstanceOf(InvalidPlacementSeal.class);
        assertRedacted(absent, secret);
        List<ClassBBlindPacketExport.Output> extra = new ArrayList<>(outputs);
        extra.add(new ClassBBlindPacketExport.Output("unreceipted", ClassBScoreAggregator.Arm.BASELINE,
                "Other answer."));
        Throwable noRow = org.assertj.core.api.Assertions.catchThrowable(() ->
                export(evidence, output, freeze, receipts, extra, secret));
        assertThat(noRow).isInstanceOf(InvalidPlacementSeal.class);
        assertRedacted(noRow, secret);
        assertThat(Files.exists(output)).isFalse();
    }

    private static void assertRedacted(Throwable failure, PlacementSecret secret) {
        for (Throwable current = failure; current != null; current = current.getCause())
            assertThat(current.getMessage()).doesNotContain(secret.hex(),
                    Long.toString(secret.seedLong()), Long.toHexString(secret.seedLong()));
    }
}
