package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Answer-key, format, placement and strict-score mutants for preregistration section 7. */
class ClassABlindPacketExportTest {
    private static final String BASELINE = "BASELINE_MINI";
    private static final String CANDIDATE = "LUNA";
    private static final String CASE = "case-1";
    private static final String NOTE = "Membranes surround cells.";
    private static final String EXPLANATION = "The membrane is the boundary.";
    private static final String QUESTION = "What surrounds cells?";
    private static final String MCQ = "MCQ";
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();

    private ClassACaseManifest.Case fixture(String id, ClassACaseManifest.Subgroup subgroup) {
        var source = new ClassACaseManifest.Source("Cells", NOTE, "Boundary", List.of("membrane"),
                List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        return new ClassACaseManifest.Case(id,
                subgroup == null ? ClassACaseManifest.Workflow.NOTE_ADAPTIVE
                        : ClassACaseManifest.Workflow.LONG_EXAM,
                "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                List.of(source), 1, null, false, subgroup);
    }

    private JsonNode question(String format) {
        ObjectNode row = json.createObjectNode();
        row.put("question", QUESTION);
        row.put("questionFormat", format);
        row.put("explanation", EXPLANATION);
        if (List.of("IDENTIFICATION", "SHORT_ANSWER", "FILL_IN_THE_BLANK", "FREE_TEXT").contains(format)) {
            row.putArray("choices");
            row.putArray("acceptableAnswers").add("membrane");
        } else if ("ENUMERATION".equals(format)) {
            row.putArray("choices");
            row.putArray("acceptableAnswerGroups").addArray().add("membrane");
        } else {
            var choices = row.putArray("choices").add("membrane").add("nucleus");
            if (!"TRUE_FALSE".equals(format)) choices.add("cytoplasm").add("cell wall");
            row.put("correctIndex", 0);
            if ("MULTI_SELECT".equals(format)) row.putArray("correctIndices").add(0).add(2);
        }
        return row;
    }

    private List<ClassABlindPacketExport.Output> outputs(String id, String format) {
        JsonNode questions = json.createArrayNode().add(question(format));
        return List.of(new ClassABlindPacketExport.Output(id, BASELINE, questions),
                new ClassABlindPacketExport.Output(id, CANDIDATE, questions));
    }

    @ParameterizedTest @ValueSource(strings = {MCQ, "MULTI_SELECT", "MATCHING", "ASSERTION_REASON",
            "TRUE_FALSE", "IDENTIFICATION", "ENUMERATION", "SHORT_ANSWER", "FILL_IN_THE_BLANK", "FREE_TEXT"})
    void everyDeliveredQuestionFormatKeepsItsKeyAndExplanationVisible(String format) throws Exception {
        Path packetDir = temp.resolve("packets-" + format);
        Path keyPath = temp.resolve("keys-" + format + ".jsonl");
        var exported = ClassABlindPacketExport.export(List.of(fixture(CASE, null)), outputs(CASE, format),
                BASELINE, CANDIDATE, packetDir, keyPath, 7L);
        String body = Files.readString(exported.packets().getFirst());
        assertThat(body).contains(NOTE, QUESTION, EXPLANATION, "keyedAnswer", format);
        assertThat(body).doesNotContain(BASELINE, CANDIDATE, "gpt-", "tokenCount");
        assertThat(Files.readString(keyPath)).contains(BASELINE, CANDIDATE, format);
        assertThat(exported.itemCount()).isEqualTo(2);
    }

    @Test void aFailedOrShortArmIsExcludedAndRecordedWithoutAbortingTheOtherPairs() throws Exception {
        List<ClassACaseManifest.Case> fixtures = List.of(fixture("ok-1", null), fixture("failed-2", null), fixture("short-3", null));
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>(outputs("ok-1", MCQ));
        outputs.add(new ClassABlindPacketExport.Output("failed-2", BASELINE, json.createArrayNode().add(question(MCQ))));
        outputs.add(new ClassABlindPacketExport.Output("short-3", BASELINE, json.createArrayNode().add(question(MCQ))));
        outputs.add(new ClassABlindPacketExport.Output("short-3", CANDIDATE, json.createArrayNode()));
        var exported = ClassABlindPacketExport.export(fixtures, outputs, BASELINE, CANDIDATE,
                temp.resolve("excl"), temp.resolve("excl-key.jsonl"), 9L);
        assertThat(exported.excluded()).containsEntry("failed-2", "MISSING_ARM_OUTPUT").containsEntry("short-3", "SHORT_DELIVERY");
        assertThat(exported.itemCount()).isEqualTo(2);
        String packet = Files.readString(exported.packets().getFirst());
        assertThat(packet).doesNotContain("failed-2", "short-3");
        String exclusions = Files.readString(exported.exclusions());
        assertThat(exclusions).contains("failed-2", "short-3", "MISSING_ARM_OUTPUT", "SHORT_DELIVERY");
        assertThat(exported.exclusions().getParent()).isEqualTo(exported.armKey().getParent());
        assertThat(exported.exclusions().startsWith(temp.resolve("excl"))).isFalse();
    }

    @Test void sameSeedIsByteIdenticalAndLongExamSubgroupsStaySeparate() throws Exception {
        List<ClassACaseManifest.Case> fixtures = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        for (ClassACaseManifest.Subgroup subgroup : ClassACaseManifest.Subgroup.values()) {
            String id = "long-" + subgroup.name();
            fixtures.add(fixture(id, subgroup));
            outputs.addAll(outputs(id, MCQ));
        }
        var one = ClassABlindPacketExport.export(fixtures, outputs, BASELINE, CANDIDATE,
                temp.resolve("first"), temp.resolve("first-key.jsonl"), 42L);
        var two = ClassABlindPacketExport.export(fixtures.reversed(), outputs.reversed(), BASELINE, CANDIDATE,
                temp.resolve("second"), temp.resolve("second-key.jsonl"), 42L);
        assertThat(one.packets()).hasSize(3);
        for (int index = 0; index < 3; index++)
            assertThat(Files.readString(one.packets().get(index)))
                    .isEqualTo(Files.readString(two.packets().get(index)));
        assertThat(Files.readString(one.armKey())).isEqualTo(Files.readString(two.armKey()));
        assertThat(one.packets().stream().map(path -> path.getFileName().toString()))
                .containsExactlyInAnyOrder("LONG_EXAM_DIRECT.jsonl", "LONG_EXAM_LONG_EXAM_POOL.jsonl",
                        "LONG_EXAM_BOARD_EXAM_POOL.jsonl");
    }

    @Test void packetLeaksAndSupplementalScoreFieldsAreRefused() throws Exception {
        var fixture = fixture(CASE, null);
        var output = ClassABlindPacketExport.export(List.of(fixture), outputs(CASE, MCQ),
                BASELINE, CANDIDATE, temp.resolve("packet"), temp.resolve("key.jsonl"), 1L);
        List<String> lines = Files.readAllLines(output.armKey());
        List<String> scores = new ArrayList<>();
        for (String line : lines) {
            String id = json.readTree(line).path("id").asText();
            ObjectNode score = json.createObjectNode();
            score.put("id", id).put("reviewerId", "reviewer").put("round", "PRIMARY")
                    .put("factuallyCorrect", "YES").put("grounding", "GROUNDED")
                    .put("critical", false).putNull("criticalType");
            score.put("distractorPlausibility", 3).put("modeAlignment", 3)
                    .put("cognitiveDemand", 3).put("clarity", 3).put("faithfulness", 3)
                    .put("seriousGiveaway", false).putNull("giveawayPattern");
            scores.add(score.toString());
        }
        Path scoreFile = temp.resolve("scores.jsonl");
        Files.write(scoreFile, scores);
        assertThat(ClassABlindPacketExport.loadAndValidateScores(output.armKey(), scoreFile)).hasSize(2);
        ObjectNode extra = (ObjectNode) json.readTree(scores.getFirst());
        extra.put("teachingValue", 5);
        scores.set(0, extra.toString());
        Files.write(scoreFile, scores);
        assertThatThrownBy(() -> ClassABlindPacketExport.loadAndValidateScores(output.armKey(), scoreFile))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Score fields differ");
        for (String leak : List.of("The model chose this answer", "baseline arm selected",
                "tokenCount was 50")) {
            JsonNode leaking = json.createArrayNode().add(question(MCQ).deepCopy());
            ((ObjectNode) leaking.get(0)).put("question", leak);
            List<ClassABlindPacketExport.Output> leaky = List.of(
                    new ClassABlindPacketExport.Output(CASE, BASELINE, leaking),
                    new ClassABlindPacketExport.Output(CASE, CANDIDATE, leaking));
            assertThatThrownBy(() -> ClassABlindPacketExport.export(List.of(fixture), leaky,
                    BASELINE, CANDIDATE, temp.resolve("leaky"), temp.resolve("leaky-key.jsonl"), 1L))
                    .isInstanceOf(ClassABlindPacketExport.InvalidPacket.class);
        }
        Path repoFile = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize())
                .resolve("backend/src/test/java/com/studysnap/backend/benchmark/leaky.jsonl");
        assertThatThrownBy(() -> ClassABlindPacketExport.export(List.of(fixture), outputs(CASE, MCQ),
                BASELINE, CANDIDATE, repoFile, temp.resolve("repo-key.jsonl"), 1L))
                .isInstanceOf(ClassABlindPacketExport.InvalidPacket.class);
    }
}
