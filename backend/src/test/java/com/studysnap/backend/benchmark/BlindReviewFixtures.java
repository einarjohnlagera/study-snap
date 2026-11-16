package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionMentorTip;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.entity.AskCompanionTurn;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Synthetic inputs passed through the real exporters; no scorer or model is invoked. */
final class BlindReviewFixtures {
    static final String SEED = "0123456789abcdef-master";
    static final String WORKFLOW_A = "NOTE_ADAPTIVE";
    static final String WORKFLOW_B = "STUDY_TIP";
    static final String CLOCK = "2026-11-16T00:00:00Z";
    private static final String PACKETS = "packets";
    private static final String ARM_KEY_FILE = "arm-key.jsonl";
    private static final String SYNTHETIC = "synthetic";
    private static final String ANSWER_ONE = "Answer one.";
    private static final String ANSWER_TWO = "Answer two.";
    private static final ObjectMapper JSON = new ObjectMapper();
    private BlindReviewFixtures() {}

    static Path registry(Path temp) throws Exception {
        Path file = temp.resolve("seeds.json");
        Files.writeString(file, """
                {"seed":"0123456789abcdef-master","streams":[
                  {"workflow":"NOTE_ADAPTIVE","purpose":"SPOT_CHECK","rounds":2},
                  {"workflow":"NOTE_ADAPTIVE","purpose":"EXPANSION","rounds":2},
                  {"workflow":"STUDY_TIP","purpose":"SPOT_CHECK","rounds":2}
                ]}
                """);
        seal(file);
        return file;
    }
    static void seal(Path file) throws Exception {
        Files.writeString(file.resolveSibling(file.getFileName() + ".sha256"), FreezeRecord.sha256(file));
    }
    static ClassABlindPacketExport.Export classA(Path temp, int count) {
        List<ClassACaseManifest.Case> fixtures = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = "case-" + i;
            var source = new ClassACaseManifest.Source("Cells", "Cells have membranes.", "Membrane",
                    List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
            fixtures.add(new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                    SYNTHETIC, LearnerLevel.COLLEGE, null, "Biology", false,
                    List.of(source), 1, null, false, null));
            ObjectNode question = JSON.createObjectNode().put("question", "What surrounds cells?")
                    .put("explanation", "The membrane surrounds cells.").put("correctIndex", 0);
            question.putArray("choices").add("membrane").add("nucleus").add("wall").add("cytoplasm");
            outputs.add(new ClassABlindPacketExport.Output(id, "BASELINE_MINI", JSON.createArrayNode().add(question)));
            outputs.add(new ClassABlindPacketExport.Output(id, "LUNA", JSON.createArrayNode().add(question)));
        }
        return ClassABlindPacketExport.export(fixtures, outputs, "BASELINE_MINI", "LUNA",
                temp.resolve(PACKETS), temp.resolve(ARM_KEY_FILE), 17L);
    }
    static ClassBBlindPacketExport.Export classB(Path temp, int count) {
        List<ClassBCaseManifest.Case> fixtures = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = "case-" + i;
            fixtures.add(new ClassBCaseManifest.Case(id, ClassBCaseManifest.Workflow.STUDY_TIP,
                    SYNTHETIC, false, LearnerLevel.COLLEGE,
                    List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                    null, null, null, null, null, null, null, null));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE, ANSWER_ONE));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE, ANSWER_TWO));
        }
        return ClassBBlindPacketExport.export(fixtures, outputs, temp.resolve(PACKETS),
                temp.resolve(ARM_KEY_FILE), 17L);
    }
    static ClassBBlindPacketExport.Export classBAll(Path temp, boolean withTurn) {
        List<ClassBCaseManifest.Case> fixtures = new ArrayList<>();
        fixtures.add(new ClassBCaseManifest.Case("tip", ClassBCaseManifest.Workflow.STUDY_TIP,
                SYNTHETIC, false, LearnerLevel.COLLEGE,
                List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                null, null, null, null, null, null, null, null));
        var context = new CompanionGenerationContext("Collection", "Description", "Program",
                List.of(), List.of(new CompanionGenerationContext.CompanionContextItem("Note", "Description")));
        fixtures.add(new ClassBCaseManifest.Case("collection", ClassBCaseManifest.Workflow.COLLECTION_COMPANION,
                SYNTHETIC, false, null, null, List.of(CompanionSection.OVERVIEW), context,
                null, null, null, null, null, null));
        var content = new CompanionContent("Overview", "Strategy", "Mistakes", "Resources", List.of(),
                List.of(new CompanionMentorTip(new java.util.UUID(0, 0), "Tip", "Body", null, null)));
        List<AskCompanionTurn> turns = withTurn
                ? List.of(new AskCompanionTurn("Earlier question?", "Earlier answer.",
                    OffsetDateTime.parse(CLOCK))) : List.of();
        fixtures.add(new ClassBCaseManifest.Case("ask", ClassBCaseManifest.Workflow.ASK_COMPANION,
                SYNTHETIC, false, null, null, null, null, content, turns,
                "Current question?", true, null, null));
        var interview = new ClassBCaseManifest.Question("Interview question?",
                List.of("A", "B", "C", "D"), 0, "Concept", "Explanation");
        fixtures.add(new ClassBCaseManifest.Case("interview", ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE,
                SYNTHETIC, false, null, null, null, null, null, null, null, null, interview, 1));
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (ClassBCaseManifest.Case fixture : fixtures) {
            outputs.add(new ClassBBlindPacketExport.Output(fixture.fixtureId(),
                    ClassBScoreAggregator.Arm.BASELINE, ANSWER_ONE));
            outputs.add(new ClassBBlindPacketExport.Output(fixture.fixtureId(),
                    ClassBScoreAggregator.Arm.CANDIDATE, ANSWER_TWO));
        }
        return ClassBBlindPacketExport.export(fixtures, outputs, temp.resolve(PACKETS),
                temp.resolve(ARM_KEY_FILE), 17L);
    }
}
