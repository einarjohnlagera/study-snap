package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline boundary proofs; all exports and locks are synthetic fixture files. */
class ArmKeyBoundaryTest {
    private static final String FIRST = "blind-00001";
    private static final String SECOND = "blind-00002";
    private static final String MINI = "BASELINE_MINI";
    private static final String LUNA = "LUNA";
    @TempDir Path temp;

    @Test void exportRecordsDetachedHashForEmptyAndNonemptyExclusions() throws Exception {
        var empty = ClassAPacketIndexTest.export(temp.resolve("empty"));
        assertThat(Files.readString(empty.exclusions())).isEmpty();
        assertThat(empty.exclusionsSha256()).isEqualTo(FreezeRecord.sha256(empty.exclusions()));
        assertThat(empty.exclusionsSha256()).isEqualTo(FreezeRecord.sha256(new byte[0]));
        assertThat(IntegritySupport.detachedHash(empty.exclusions())).isEqualTo(empty.exclusionsSha256());

        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        var included = new ClassACaseManifest.Case("included", ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                "synthetic", LearnerLevel.COLLEGE, null, "Biology", false, List.of(source), 1, null, false, null);
        var excluded = new ClassACaseManifest.Case("excluded", ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                "synthetic", LearnerLevel.COLLEGE, null, "Biology", false, List.of(source), 1, null, false, null);
        ObjectNode question = ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy();
        var questions = ClassAScoreSchemaV2Test.JSON.createArrayNode().add(question);
        var output = ClassABlindPacketExport.export(List.of(included, excluded), List.of(
                new ClassABlindPacketExport.Output("included", MINI, questions),
                new ClassABlindPacketExport.Output("included", LUNA, questions)), MINI, LUNA,
                temp.resolve("excluded/packets"), temp.resolve("excluded/key.jsonl"), 17L);
        assertThat(output.excluded()).containsEntry("excluded", "MISSING_ARM_OUTPUT");
        assertThat(output.exclusionsSha256()).isEqualTo(FreezeRecord.sha256(output.exclusions()))
                .isNotEqualTo(empty.exclusionsSha256());
        assertThat(IntegritySupport.detachedHash(output.exclusions())).isEqualTo(output.exclusionsSha256());
    }

    @Test void blindLoaderEqualsIdOnlyLoaderWithAllThreeRolesAndNeedsNoKey() throws Exception {
        Path root = temp.resolve("scores");
        var exported = ClassAPacketIndexTest.export(root);
        ClassAPacketIndex packets = ClassAPacketIndex.load(root.resolve("packets"));
        Path p = root.resolve("p.jsonl"), s = root.resolve("s.jsonl"), h = root.resolve("h.jsonl");
        Files.write(p, List.of(ClassAScoreSchemaV2Test.scorer(FIRST, "P").toString(),
                ClassAScoreSchemaV2Test.scorer(SECOND, "P").toString()));
        Files.write(s, List.of(ClassAScoreSchemaV2Test.scorer(FIRST, "S").toString(),
                ClassAScoreSchemaV2Test.scorer(SECOND, "S").toString()));
        Files.write(h, List.of(ClassAScoreSchemaV2Test.human(FIRST).toString()));
        Path idOnly = root.resolve("ids.jsonl");
        PairMap.fromPackets(root.resolve("packets")).idOnlyKey(idOnly);
        var expected = ClassAScoreFilesV2.load(idOnly, packets, p, s, h, ClassAScoreSchemaV2Test.MAPPING);
        Files.delete(exported.armKey());
        Files.delete(idOnly);
        var actual = ClassAScoreFilesV2.loadBlind(packets, p, s, h, ClassAScoreSchemaV2Test.MAPPING);
        assertThat(actual).isEqualTo(expected);
        assertThat(Files.exists(exported.armKey())).isFalse();
    }

    @Test void frbVerdictSurvivesDeletionOfEveryOpenedKeyIncludingReserve() throws Exception {
        FrbPipelineTest.Fixture fixture = frbFixture(true);
        var before = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        List<Path> keys = new ArrayList<>();
        fixture.frb().provenance().primaries().forEach(export -> {
            Path parent = export.sealFile().getParent();
            keys.add(parent.resolve("key.jsonl"));
            keys.add(parent.resolve("arm-key.jsonl"));
        });
        fixture.frb().provenance().reserves().values().forEach(export -> {
            Path parent = export.sealFile().getParent();
            keys.add(parent.resolve("key.jsonl"));
            keys.add(parent.resolve("arm-key.jsonl"));
        });
        int deleted = 0;
        for (Path key : keys) if (Files.deleteIfExists(key)) deleted++;
        assertThat(deleted).as("opened class-A and class-B primary and reserve keys").isEqualTo(4);
        var after = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(after.routes()).isEqualTo(before.routes());
    }

    @Test void legacyRunRejectsFrbParametersBeforeAnyKeyRead() throws Exception {
        FrbPipelineTest.Fixture fixture = frbFixture(false);
        var frb = fixture.run();
        Path missing = temp.resolve("no-key.jsonl");
        ResultsPipeline.Input input = new ResultsPipeline.Input(frb.repoRoot(), frb.freeze(), frb.parameters(),
                frb.mapping(), frb.manifests(), frb.baselineMini(), frb.baseline41(), frb.candidate(),
                new ScoreEvidence.FilesA(missing, missing, missing, missing, missing),
                new ScoreEvidence.FilesB(missing, missing, Map.of()), Map.of(), frb.compatibilityReports(),
                frb.headCommitTime(), frb.ownerDispositions(), temp.resolve("legacy-report.json"),
                temp.resolve("legacy-trigger.json"), true);
        assertThatThrownBy(() -> ResultsPipeline.run(input)).isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining("FRB parameters require runFrb");
        assertThat(Files.exists(missing)).isFalse();
    }

    private FrbPipelineTest.Fixture frbFixture(boolean reserve) throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        Field field = FrbPipelineTest.class.getDeclaredField("temp");
        field.setAccessible(true);
        field.set(builder, temp.resolve(reserve ? "with-reserve" : "no-reserve"));
        return builder.fixture(reserve, 0);
    }
}
