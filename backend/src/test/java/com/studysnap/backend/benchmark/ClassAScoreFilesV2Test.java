package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassAScoreFilesV2Test {
    private static final String FIRST = "blind-00001";
    private static final String SECOND = "blind-00002";
    @TempDir Path temp;
    private Path key;
    private ClassAPacketIndex packets;
    private Path pFile;
    private Path sFile;
    private Path hFile;

    private void setup() {
        var exported = ClassAPacketIndexTest.export(temp);
        key = exported.armKey();
        packets = ClassAPacketIndex.load(temp.resolve("packets"));
        pFile = temp.resolve("p.jsonl");
        sFile = temp.resolve("s.jsonl");
        hFile = temp.resolve("h.jsonl");
    }
    private void write(Path file, ObjectNode... rows) throws Exception {
        Files.write(file, java.util.Arrays.stream(rows).map(ObjectNode::toString).toList());
    }
    private Map<String, ClassAScoreFilesV2.ItemScores> load() {
        return ClassAScoreFilesV2.load(key, packets, pFile, sFile, hFile, ClassAScoreSchemaV2Test.MAPPING);
    }
    private ObjectNode p(String id) { return ClassAScoreSchemaV2Test.scorer(id, "P"); }
    private ObjectNode s(String id) { return ClassAScoreSchemaV2Test.scorer(id, "S"); }
    private ObjectNode h(String id) { return ClassAScoreSchemaV2Test.human(id); }

    @Test void realPacketHappyPathAndCleanAgreement() throws Exception {
        setup();
        write(pFile, p(FIRST), p(SECOND));
        write(sFile, s(FIRST), s(SECOND));
        write(hFile);
        Map<String, ClassAScoreFilesV2.ItemScores> loaded = load();
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(FIRST).p().status()).isEqualTo(ClassAScoreFilesV2.Status.OK);
        assertThat(loaded.get(FIRST).s().status()).isEqualTo(ClassAScoreFilesV2.Status.OK);
        assertThat(ClassAScoreFilesV2.needsHumanReview(loaded.get(FIRST), ClassAScoreSchemaV2Test.MAPPING)).isFalse();
    }

    @Test void missingAndInvalidSecondScorerAreDataAndRequireHumanReview() throws Exception {
        setup();
        write(pFile, p(FIRST), p(SECOND));
        write(sFile, s(SECOND));
        write(hFile);
        var missing = load();
        assertThat(missing.get(FIRST).s().status()).isEqualTo(ClassAScoreFilesV2.Status.MISSING);
        assertThat(ClassAScoreFilesV2.needsHumanReview(missing.get(FIRST), ClassAScoreSchemaV2Test.MAPPING)).isTrue();
        ObjectNode invalid = s(FIRST);
        invalid.put("keyDefensible", false);
        write(sFile, invalid, s(SECOND));
        var loaded = load();
        assertThat(loaded.get(FIRST).s().status()).isEqualTo(ClassAScoreFilesV2.Status.INVALID);
        assertThat(loaded.get(FIRST).s().code()).isEqualTo(ClassAScoreSchemaV2.Code.STRUCTURE);
        assertThat(loaded.get(FIRST).s().message()).contains("keyDefensible");
        assertThat(ClassAScoreFilesV2.needsHumanReview(loaded.get(FIRST), ClassAScoreSchemaV2Test.MAPPING)).isTrue();
    }

    @Test void duplicateRowsUnknownIdAndRoleMismatchAreHardFailures() throws Exception {
        setup();
        write(pFile, p(FIRST), p(FIRST));
        write(sFile);
        write(hFile);
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("Duplicate P score");
        write(pFile, p(FIRST));
        write(hFile, h("unknown"));
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("Unknown score id");
        write(hFile);
        write(sFile, p(SECOND));
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("scorerRole differs");
    }

    @Test void reviewerIdCannotCrossRolesButMayRepeatWithinOneRole() throws Exception {
        setup();
        ObjectNode p1 = p(FIRST), p2 = p(SECOND);
        p2.put("invocationId", "P-call-2");
        write(pFile, p1, p2);
        write(sFile, s(FIRST), s(SECOND));
        write(hFile);
        assertThat(load()).hasSize(2);
        ObjectNode s1 = s(FIRST);
        s1.put("reviewerId", "P_REVIEWER");
        write(sFile, s1, s(SECOND));
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("reviewerId appears under two roles");
    }

    @Test void blankUnparsableAndInvalidHumanRowsAreHardFailures() throws Exception {
        setup();
        write(pFile, p(FIRST));
        write(sFile, s(FIRST));
        Files.writeString(hFile, "\n");
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("Blank JSONL");
        Files.writeString(hFile, "{bad");
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("Cannot parse score file");
        ObjectNode human = h(FIRST);
        human.put("keyDefensible", false);
        write(hFile, human);
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("Invalid H row");
    }

    @Test void humanClearAndDowngradeReasonsFollowCriticalScorerVerdict() throws Exception {
        setup();
        ObjectNode critical = p(FIRST);
        ClassAScoreSchemaV2Test.defect(critical, "KEY_NOT_DEFENSIBLE");
        write(pFile, critical);
        write(sFile, s(FIRST));
        ObjectNode human = h(FIRST);
        write(hFile, human);
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("humanClearReason");
        human.put("humanClearReason", "REVIEWED_NOT_DEFECT");
        ClassAScoreSchemaV2Test.evidence(human);
        write(hFile, human);
        assertThat(load().get(FIRST).h().verdict()).isEqualTo(ClassAScoreSchemaV2.Verdict.CLEAN);
        human.put("humanVerdict", "QUALITY_FAIL").put("ambiguityClass", "MINOR_WORDING")
                .putNull("humanClearReason");
        write(hFile, human);
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("humanClearReason");
        human.put("humanClearReason", "DOWNGRADED_NONCRITICAL");
        write(hFile, human);
        assertThat(load().get(FIRST).h().verdict()).isEqualTo(ClassAScoreSchemaV2.Verdict.QUALITY_FAIL);
        write(pFile, p(FIRST));
        write(hFile, human);
        assertThatThrownBy(this::load).isInstanceOf(ClassAScoreFilesV2.InvalidScoreFiles.class)
                .hasMessageContaining("humanClearReason");
    }

    @Test void disagreementsBorderlinesAndCriticalFlagsRequireHumanReview() throws Exception {
        setup();
        ObjectNode p = p(FIRST), s = s(FIRST);
        write(hFile);
        write(pFile, p);
        s.put("factuallyCorrect", "NO");
        ClassAScoreSchemaV2Test.evidence(s);
        write(sFile, s);
        assertThat(ClassAScoreFilesV2.needsHumanReview(load().get(FIRST), ClassAScoreSchemaV2Test.MAPPING)).isTrue();
        s = s(FIRST);
        s.put("borderline", true);
        ClassAScoreSchemaV2Test.evidence(s);
        write(sFile, s);
        assertThat(ClassAScoreFilesV2.needsHumanReview(load().get(FIRST), ClassAScoreSchemaV2Test.MAPPING)).isTrue();
        s = s(FIRST);
        ClassAScoreSchemaV2Test.defect(s, "KEY_NOT_DEFENSIBLE");
        write(sFile, s);
        assertThat(ClassAScoreFilesV2.needsHumanReview(load().get(FIRST), ClassAScoreSchemaV2Test.MAPPING)).isTrue();
    }
}
