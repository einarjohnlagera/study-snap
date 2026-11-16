package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Preregistration section 7 every-question correctness and blind-score coverage. */
class ScoreEvidenceTest {
    private static final String BLIND_ONE = "blind-1";
    private static final String BLIND_TWO = "blind-2";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String WORKFLOW_FIELD = "workflow";
    private static final String TEACHER_WORKFLOW = "TEACHER_QUIZ";
    private static final String QUESTION_FORMAT_FIELD = "questionFormat";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CASE_ID = "two-question-case";
    private static final String ARM = "LUNA";
    @TempDir Path temp;

    @Test void oneDeficitAmongTwoValidatedQuestionsMakesTheWholeCaseAdverse() throws Exception {
        Path mappingFile = temp.resolve("mapping.json");
        byte[] mapping = Files.readAllBytes(Path.of("src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json"));
        Files.write(mappingFile, mapping);
        Files.writeString(temp.resolve("mapping.json.sha256"), FreezeRecord.sha256(mapping));
        Path key = temp.resolve("key.jsonl");
        Path primary = temp.resolve("scores.jsonl");
        Files.writeString(key, JSON.writeValueAsString(Map.of("id", BLIND_ONE, "arm", ARM,
                CASE_ID_FIELD, CASE_ID, WORKFLOW_FIELD, TEACHER_WORKFLOW, QUESTION_FORMAT_FIELD, "MCQ")) + "\n"
                + JSON.writeValueAsString(Map.of("id", BLIND_TWO, "arm", ARM,
                CASE_ID_FIELD, CASE_ID, WORKFLOW_FIELD, TEACHER_WORKFLOW, QUESTION_FORMAT_FIELD, "MCQ")) + "\n");
        Files.writeString(primary, JSON.writeValueAsString(score(BLIND_ONE, 5)) + "\n"
                + JSON.writeValueAsString(score(BLIND_TWO, 1)) + "\n");
        Path reviews = write("reviews.jsonl");
        Path adjudications = write("adjudications.jsonl");
        Path exclusions = write("exclusions.jsonl");
        Path bKey = write("b-key.jsonl");
        Path bScore = write("b-scores.jsonl");
        ScoreEvidence.FilesA a = new ScoreEvidence.FilesA(key, primary, reviews, adjudications, exclusions);
        ScoreEvidence.FilesB b = new ScoreEvidence.FilesB(bKey, bScore,
                Map.of("HUMAN_A", ClassBScoreFiles.ScorerKind.HUMAN));
        EvidenceMapping frozen = EvidenceMapping.load(mappingFile);
        ScoreEvidence.Loaded evidence = ScoreEvidence.load(a, b, frozen);
        assertThat(evidence.get(CASE_ID, ARM).scoredQuestions()).isEqualTo(2);
        assertThat(evidence.get(CASE_ID, ARM).quality()).isTrue();
        assertThat(evidence.get(CASE_ID, ARM).critical()).isFalse();
        ObjectNode disagreement = score(BLIND_TWO, 5);
        disagreement.put("reviewerId", "HUMAN_B").put("round", "INDEPENDENT_REVIEW");
        Files.writeString(reviews, JSON.writeValueAsString(disagreement) + "\n");
        assertThatThrownBy(() -> ScoreEvidence.load(a, b, frozen))
                .isInstanceOf(ScoreEvidence.InvalidScores.class)
                .hasMessageContaining("Unresolved class-A score disagreement");
    }

    private Path write(String name) throws Exception {
        Path path = temp.resolve(name);
        Files.writeString(path, "");
        return path;
    }

    private static ObjectNode score(String id, int clarity) {
        ObjectNode row = JSON.createObjectNode();
        row.put("id", id).put("reviewerId", "HUMAN_A").put("round", "PRIMARY");
        row.put("factuallyCorrect", "YES").put("grounding", "GROUNDED");
        row.put("critical", false).putNull("criticalType");
        row.put("distractorPlausibility", 5).put("modeAlignment", 5).put("cognitiveDemand", 5);
        row.put("clarity", clarity).put("faithfulness", 5).put("seriousGiveaway", false);
        row.putNull("giveawayPattern");
        return row;
    }
}
