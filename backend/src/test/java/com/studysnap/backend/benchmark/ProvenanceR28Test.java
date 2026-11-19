package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic, key-free checks of the reserve R28 provenance boundary. */
class ProvenanceR28Test {
    private static final String WORKFLOW = "STUDY_TIP";
    private static final String HUMAN_OWNER = "HUMAN_OWNER";
    private static final String MODEL_X = "MODEL_X";
    private static final String LABEL = "reserve." + WORKFLOW;
    private static final String SCORE_HASH_KEY = LABEL + ".scores";
    private static final String SCORE_NAME = "scores.jsonl";
    private static final String INCOMPLETE_EVIDENCE = "Reserve R28 evidence incomplete";
    private static final String UNBOUND_EVIDENCE = "not bound to the reserve locks";
    private static final String UNSCORED = "unscored";
    private static final String UNKNOWN_HASH = "0".repeat(64);
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS =
            Map.of(HUMAN_OWNER, ClassBScoreFiles.ScorerKind.HUMAN);
    @TempDir Path temp;

    private FrbPipelineTest.Fixture fixture(boolean unresolved) throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        return builder.fixture(true, 0, 1, false, unresolved);
    }

    private static ResultsPipeline.ReserveExport supplied(FrbPipelineTest.Fixture fixture) {
        ResultsPipeline.ReserveExport old = fixture.frb().provenance().reserves().get(WORKFLOW);
        Path root = old.findingsDirectory().getParent();
        return withEvidence(old, root.resolve("packets"), old.findingsDirectory().resolve(SCORE_NAME), SCORERS);
    }

    private static ResultsPipeline.ReserveExport withEvidence(ResultsPipeline.ReserveExport old,
            Path packets, Path scores, Map<String, ClassBScoreFiles.ScorerKind> scorers) {
        return new ResultsPipeline.ReserveExport(old.workflow(), old.reserveKeyAccessLog(), old.sealFile(),
                old.lockForPrimaryOpening(), old.lockForReserveOpening(), old.findingsDirectory(),
                packets, scores, scorers);
    }

    private static ResultsPipeline.FrbInput withReserve(FrbPipelineTest.Fixture fixture,
            ResultsPipeline.ReserveExport export) {
        ResultsPipeline.FrbInput old = fixture.frb();
        ResultsPipeline.ProvenanceLogs logs = new ResultsPipeline.ProvenanceLogs(
                old.provenance().primaries(), Map.of(WORKFLOW, export));
        return new ResultsPipeline.FrbInput(old.bundle(), old.reserveRecordFile(), old.verification(), logs);
    }

    private static ArmKeySeal.Seal seal(ResultsPipeline.ReserveExport export) {
        return ArmKeySeal.load(export.sealFile());
    }

    private static void direct(ResultsPipeline.ReserveExport export, boolean synthetic,
            Map<String, String> hashes) {
        FrbProvenance.verifyReserveR28(WORKFLOW, export, seal(export), synthetic, hashes, LABEL);
    }

    private static void rewriteLog(Path file, java.util.function.Consumer<ObjectNode> edit) throws Exception {
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllLines(file).getFirst());
        edit.accept(row);
        Files.writeString(file, row + "\n");
    }

    private static void rewriteLock(Path file, java.util.function.Consumer<ObjectNode> edit) throws Exception {
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(file.toFile());
        edit.accept(row);
        Files.writeString(file, row.toString());
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(file));
    }

    @Test void suppliedCompleteReservePassesAndRecordsScoreHash() throws Exception {
        var fixture = fixture(false);
        var verified = FrbProvenance.verify(fixture.run(), withReserve(fixture, supplied(fixture)));
        assertThat(verified.hashes()).containsEntry(SCORE_HASH_KEY,
                FreezeRecord.sha256(supplied(fixture).scoresFile()));
    }

    @Test void absentSyntheticInputsSkipWithoutScoreHash() throws Exception {
        var fixture = fixture(false);
        var old = fixture.frb().provenance().reserves().get(WORKFLOW);
        Map<String, String> hashes = new HashMap<>();
        direct(old, true, hashes);
        assertThat(hashes).doesNotContainKey(SCORE_HASH_KEY);
    }

    @Test void incompleteInputsAlwaysRefuseWithInputMessage() throws Exception {
        var fixture = fixture(false);
        var base = supplied(fixture);
        for (int mask = 0; mask < 7; mask++) {
            Path packets = (mask & 1) == 0 ? null : base.reservePacketDirectory();
            Path scores = (mask & 2) == 0 ? null : base.scoresFile();
            Map<String, ClassBScoreFiles.ScorerKind> scorers =
                    (mask & 4) == 0 ? null : base.scorers();
            var changed = withEvidence(base, packets, scores, scorers);
            for (boolean synthetic : List.of(false, true)) {
                if (mask == 0 && synthetic) continue;
                Map<String, String> hashes = new HashMap<>();
                assertThatThrownBy(() -> direct(changed, synthetic, hashes))
                        .isInstanceOf(ResultsPipeline.InvalidInput.class)
                        .hasMessageContaining("Incomplete reserve R28 inputs");
            }
        }
    }

    @Test void unresolvedDrawnReserveRefusesThroughProvenance() throws Exception {
        var fixture = fixture(true);
        var input = withReserve(fixture, supplied(fixture));
        var run = fixture.run();
        assertThatThrownBy(() -> FrbProvenance.verify(run, input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining(INCOMPLETE_EVIDENCE)
                .hasCauseInstanceOf(InvalidEvidence.class).cause().hasMessageContaining(UNSCORED);
    }

    @Test void pendingHumanDisagreementRefuses() throws Exception {
        var fixture = fixture(false);
        var export = supplied(fixture);
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(
                Files.readAllLines(export.scoresFile()).getFirst());
        row.put("round", 2).put("grounded", false).put("reasonCode", "UNGROUNDED");
        Files.writeString(export.scoresFile(), row + "\n", java.nio.file.StandardOpenOption.APPEND);
        Map<String, String> hashes = new HashMap<>();
        assertThatThrownBy(() -> direct(export, true, hashes))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasCauseInstanceOf(InvalidEvidence.class).cause().hasMessageContaining("pending");
    }

    @Test void modelScoreDoesNotCompleteMissingHumanStatus() throws Exception {
        var fixture = fixture(true);
        var export = supplied(fixture);
        Set<String> scored = new HashSet<>();
        for (String line : Files.readAllLines(export.scoresFile()))
            scored.add(IntegritySupport.JSON.readTree(line).path("itemId").asText());
        String missing = ReserveDecisionRecord.manifestFromPackets(export.reservePacketDirectory()).stream()
                .map(ClassBScoreFiles.BlindItem::itemId).filter(id -> !scored.contains(id)).findFirst().orElseThrow();
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(
                Files.readAllLines(export.scoresFile()).getFirst());
        row.put("itemId", missing).put("scorerId", MODEL_X).put("scorerKind", "MODEL");
        Files.writeString(export.scoresFile(), row + "\n", java.nio.file.StandardOpenOption.APPEND);
        var changed = withEvidence(export, export.reservePacketDirectory(), export.scoresFile(),
                Map.of(HUMAN_OWNER, ClassBScoreFiles.ScorerKind.HUMAN,
                        MODEL_X, ClassBScoreFiles.ScorerKind.MODEL));
        Map<String, String> hashes = new HashMap<>();
        assertThatThrownBy(() -> direct(changed, true, hashes))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasCauseInstanceOf(InvalidEvidence.class).cause().hasMessageContaining(UNSCORED);
    }

    @Test void changedPacketOutputBreaksSealBinding() throws Exception {
        var fixture = fixture(false);
        var export = supplied(fixture);
        Path copy = temp.resolve("copied-packets");
        Files.createDirectories(copy);
        try (var paths = Files.list(export.reservePacketDirectory())) {
            for (Path path : paths.toList()) Files.copy(path, copy.resolve(path.getFileName()));
        }
        Path packet = copy.resolve("STUDY_TIP.jsonl");
        String original = Files.readString(packet);
        assertThat(original).contains("Answer one.");
        Files.writeString(packet, original.replaceFirst("Answer one\\.", "Changed one."));
        var changed = withEvidence(export, copy, export.scoresFile(), export.scorers());
        Map<String, String> hashes = new HashMap<>();
        assertThatThrownBy(() -> direct(changed, true, hashes))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining(UNBOUND_EVIDENCE)
                .hasRootCauseInstanceOf(ArmKeySeal.SealBroken.class);
    }

    @Test void siblingScoreFileIsNotBoundToFindingsDirectory() throws Exception {
        var fixture = fixture(false);
        var export = supplied(fixture);
        Path sibling = temp.resolve("sibling").resolve(SCORE_NAME);
        Files.createDirectories(sibling.getParent());
        Files.copy(export.scoresFile(), sibling);
        var changed = withEvidence(export, export.reservePacketDirectory(), sibling, export.scorers());
        Map<String, String> hashes = new HashMap<>();
        assertThatThrownBy(() -> direct(changed, true, hashes))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining(UNBOUND_EVIDENCE);
    }

    @Test void nonJsonlScoreCopyInFindingsNeedsLockFindingName() throws Exception {
        var fixture = fixture(false);
        var export = supplied(fixture);
        Path copy = export.findingsDirectory().resolve("scores.txt");
        Files.copy(export.scoresFile(), copy);
        var changed = withEvidence(export, export.reservePacketDirectory(), copy, export.scorers());
        Map<String, String> hashes = new HashMap<>();
        assertThatThrownBy(() -> direct(changed, true, hashes))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining(UNBOUND_EVIDENCE);
    }

    @Test void wrongScorerRegistryCannotValidateLockedScores() throws Exception {
        var fixture = fixture(false);
        var export = supplied(fixture);
        var changed = withEvidence(export, export.reservePacketDirectory(), export.scoresFile(),
                Map.of("HUMAN_OTHER", ClassBScoreFiles.ScorerKind.HUMAN));
        Map<String, String> hashes = new HashMap<>();
        assertThatThrownBy(() -> direct(changed, true, hashes))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining(INCOMPLETE_EVIDENCE)
                .hasCauseInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }

    @Test void wrongReserveLockKindPrecedesR28Refusal() throws Exception {
        var fixture = fixture(true);
        var export = supplied(fixture);
        rewriteLock(export.lockForReserveOpening(), row -> row.put("kind", "RESERVE"));
        var input = withReserve(fixture, export);
        var run = fixture.run();
        assertThatThrownBy(() -> FrbProvenance.verify(run, input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("Two reserve locks");
    }

    @Test void primaryLogReserveKindPrecedesR28Refusal() throws Exception {
        var fixture = fixture(true);
        var export = supplied(fixture);
        Path log = fixture.frb().provenance().primaries().getFirst().accessLog();
        rewriteLog(log, row -> ((ArrayNode) row.get("requiredKinds")).remove(1));
        var input = withReserve(fixture, export);
        var run = fixture.run();
        assertThatThrownBy(() -> FrbProvenance.verify(run, input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("RESERVE kind");
    }

    @Test void reserveKeyArmHashPrecedesR28Refusal() throws Exception {
        var fixture = fixture(true);
        var export = supplied(fixture);
        rewriteLog(export.reserveKeyAccessLog(), row -> row.put("armKeySha256", UNKNOWN_HASH));
        var input = withReserve(fixture, export);
        var run = fixture.run();
        assertThatThrownBy(() -> FrbProvenance.verify(run, input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("arm-key hash");
    }

    @Test void reserveLogUnsuppliedLockPrecedesR28Refusal() throws Exception {
        var fixture = fixture(true);
        var export = supplied(fixture);
        rewriteLog(export.reserveKeyAccessLog(), row -> ((ArrayNode) row.get("lockSha256s")).add(UNKNOWN_HASH));
        var input = withReserve(fixture, export);
        var run = fixture.run();
        assertThatThrownBy(() -> FrbProvenance.verify(run, input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("unsupplied lock");
    }
}
