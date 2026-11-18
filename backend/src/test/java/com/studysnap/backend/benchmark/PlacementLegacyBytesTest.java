package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementLegacyBytesTest {
    @TempDir Path temp;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASELINE = "BASELINE_MINI";
    private static final String CANDIDATE = "LUNA";

    @Test void classASeed42FilesArePinned() throws Exception {
        var source = new ClassACaseManifest.Source("Cells", "Membranes surround cells.", "Boundary",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        ObjectNode row = JSON.createObjectNode();
        row.put("question", "What surrounds cells?").put("questionFormat", "MCQ")
                .put("explanation", "The membrane is the boundary.");
        row.putArray("choices").add("membrane").add("nucleus").add("cytoplasm").add("cell wall");
        row.put("correctIndex", 0);
        JsonNode questions = JSON.createArrayNode().add(row);
        List<ClassACaseManifest.Case> fixtures = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        for (ClassACaseManifest.Subgroup subgroup : ClassACaseManifest.Subgroup.values()) {
            String id = "long-" + subgroup.name();
            fixtures.add(new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.LONG_EXAM,
                    "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                    List.of(source), 1, null, false, subgroup));
            outputs.add(new ClassABlindPacketExport.Output(id, BASELINE, questions));
            outputs.add(new ClassABlindPacketExport.Output(id, CANDIDATE, questions));
        }
        var export = ClassABlindPacketExport.export(fixtures, outputs, BASELINE, CANDIDATE,
                temp.resolve("a-packets"), temp.resolve("a-key.jsonl"), 42L);
        List<String> actual = new ArrayList<>();
        for (Path path : export.packets())
            actual.add(path.getFileName() + ":" + FreezeRecord.sha256(path));
        actual.add("key:" + FreezeRecord.sha256(export.armKey()));
        actual.add("exclusions:" + FreezeRecord.sha256(export.exclusions()));
        actual.add("exclusions-hash:" + FreezeRecord.sha256(IntegritySupport.detached(export.exclusions())));
        assertThat(actual).containsExactly(
                "LONG_EXAM_BOARD_EXAM_POOL.jsonl:dabd113455d9af600e7ded21ecfdc5eb2272b8e9d34f8039b1804c806924b7a5",
                "LONG_EXAM_DIRECT.jsonl:c361df094404a9ad98760dc6fafc126e415071a9e096fbf068d5ad949a84214d",
                "LONG_EXAM_LONG_EXAM_POOL.jsonl:cf0bec01a1b32f8b434f58cda8a006673789093c2eaed172dda28a6c114c4a89",
                "key:e162f9ca3439d819f553a362130de012f086f62b2d815b20d8dc34ec24633013",
                "exclusions:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "exclusions-hash:38acb15d02d5ac0f2a2789602e9df950c380d2799b4bdb59394e4eeabdd3a662");
    }

    @Test void classBSeed182FilesArePinned() throws Exception {
        List<ClassBCaseManifest.Case> fixtures = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String id = "synthetic-" + i;
            fixtures.add(new ClassBCaseManifest.Case(id, ClassBCaseManifest.Workflow.STUDY_TIP,
                    "synthetic", false, LearnerLevel.COLLEGE,
                    List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                    null, null, null, null, null, null, null, null));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE,
                    "Synthetic answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE,
                    "Synthetic answer two."));
        }
        ClassBBlindPacketExport.export(fixtures, outputs, temp.resolve("b-packets"),
                temp.resolve("b-key.jsonl"), 182L);
        List<String> actual = new ArrayList<>();
        for (ClassBCaseManifest.Workflow workflow : ClassBCaseManifest.Workflow.values())
            actual.add(workflow.name() + ":" + FreezeRecord.sha256(temp.resolve("b-packets")
                    .resolve(workflow.name() + ".jsonl")));
        actual.add("key:" + FreezeRecord.sha256(temp.resolve("b-key.jsonl")));
        assertThat(actual).containsExactly(
                "STUDY_TIP:f4f9092d428b324f7291338a396810d1beea0e022cd2cfcfcaadb3d8a15b23d4",
                "COLLECTION_COMPANION:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "ASK_COMPANION:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "INTERVIEW_CRITIQUE:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "key:220d70ec5f6239bc71f0ab04378c367ebda8e55c91b3555e59cb50f446c1da35");
    }
}
