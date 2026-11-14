package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic schema mutation checks for preregistration section 6, Revision 2.2. */
class ClassBCaseManifestTest {
    private static final String FIXTURE_ID = "fixtureId";
    private static final String INCORRECT_QUESTIONS = "incorrectQuestions";
    private static final String PRIOR_TURNS = "priorTurns";
    private static final String REQUESTED_SECTIONS = "requestedSections";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WORKFLOW_FIELD = "workflow";
    private static final String SYNTHETIC = "synthetic";
    private static final String RESERVE_FIELD = "reserve";
    private static final String STUDY_TIP = "STUDY_TIP";
    private static final String QUESTION_FIELD = "question";
    private static final String SYNTHETIC_QUESTION = "Synthetic question?";
    private static final String ANSWER_A = "A";
    private static final String EXPLANATION_FIELD = "explanation";
    private static final String SYNTHETIC_EXPLANATION = "Synthetic explanation.";
    private static final String ASK_COMPANION = "ASK_COMPANION";
    private static final String COLLECTION_COMPANION = "COLLECTION_COMPANION";
    private static final String TITLE_FIELD = "title";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String SYNTHETIC_DESCRIPTION = "Synthetic description";
    private static final String INTERVIEW_CRITIQUE = "INTERVIEW_CRITIQUE";
    @TempDir Path temp;

    private FrozenGateParameters parameters() throws Exception {
        byte[] bytes = Files.readAllBytes(GateTestFixture.SOURCE);
        Path file = temp.resolve("gates.json");
        Files.write(file, bytes);
        Files.writeString(temp.resolve("gates.json.sha256"),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return FrozenGateParameters.load(file);
    }
    private Path write(ObjectNode... rows) throws Exception {
        Path file = temp.resolve("fixtures.jsonl");
        Files.write(file, List.of(rows).stream().map(ObjectNode::toString).toList());
        return file;
    }
    private ObjectNode base(String workflow) {
        return JSON.createObjectNode().put(FIXTURE_ID, "case-1").put(WORKFLOW_FIELD, workflow)
                .put("stratum", SYNTHETIC).put(RESERVE_FIELD, false);
    }
    private ObjectNode tip() {
        ObjectNode row = base(STUDY_TIP).put("sourceLevel", "COLLEGE");
        ArrayNode questions = row.putArray(INCORRECT_QUESTIONS);
        questions.addObject().put(QUESTION_FIELD, SYNTHETIC_QUESTION)
                .put("correctAnswer", ANSWER_A).put(EXPLANATION_FIELD, SYNTHETIC_EXPLANATION);
        return row;
    }
    private ObjectNode ask() {
        ObjectNode row = base(ASK_COMPANION);
        row.putObject("companion").put("overview", "Synthetic context.")
                .putArray("faq");
        row.putArray(PRIOR_TURNS);
        row.put(QUESTION_FIELD, SYNTHETIC_QUESTION).put("answerableFromCompanion", true);
        return row;
    }
    private ObjectNode collection() {
        ObjectNode row = base(COLLECTION_COMPANION);
        row.putArray(REQUESTED_SECTIONS).add("OVERVIEW");
        ObjectNode context = row.putObject("companionContext");
        context.put(TITLE_FIELD, "Synthetic collection").put(DESCRIPTION_FIELD, SYNTHETIC_DESCRIPTION)
                .put("courseProgram", "Synthetic program");
        context.putArray("subjectPlans");
        context.putArray("notes").addObject().put(TITLE_FIELD, "Synthetic note")
                .put(DESCRIPTION_FIELD, SYNTHETIC_DESCRIPTION);
        return row;
    }
    private ObjectNode interview() {
        ObjectNode row = base(INTERVIEW_CRITIQUE);
        ObjectNode question = row.putObject("interviewQuestion");
        question.put(QUESTION_FIELD, "Synthetic scenario?").put("correctIndex", 0)
                .put("concept", "Synthetic concept").put(EXPLANATION_FIELD, SYNTHETIC_EXPLANATION);
        question.putArray("choices").add(ANSWER_A).add("B").add("C").add("D");
        row.put("selectedChoiceIndex", 1);
        return row;
    }
    private void rejected(ObjectNode row) throws Exception {
        Path file = write(row);
        FrozenGateParameters p = parameters();
        assertThatThrownBy(() -> ClassBCaseManifest.load(file, p))
                .isInstanceOf(ClassBCaseManifest.InvalidManifest.class);
    }
    private void rejectedAs(ObjectNode row, String message) throws Exception {
        Path file = write(row);
        FrozenGateParameters p = parameters();
        assertThatThrownBy(() -> ClassBCaseManifest.load(file, p))
                .isInstanceOf(ClassBCaseManifest.InvalidManifest.class)
                .hasMessageContaining(message);
    }

    @Test void fourthStudyTipSummaryIsRejected() throws Exception {
        ObjectNode row = tip();
        ArrayNode questions = (ArrayNode) row.get(INCORRECT_QUESTIONS);
        while (questions.size() < 4) questions.add(questions.get(0).deepCopy());
        rejectedAs(row, "Invalid study-tip fixture");
    }
    @Test void sixthAskTurnIsRejected() throws Exception {
        ObjectNode row = ask();
        ArrayNode turns = (ArrayNode) row.get(PRIOR_TURNS);
        for (int i = 0; i < 6; i++) turns.addObject().put(QUESTION_FIELD, SYNTHETIC_QUESTION)
                .put("answer", "Synthetic answer.").put("createdAt", "2026-11-13T00:00:00Z");
        rejectedAs(row, "Invalid Ask Companion fixture");
    }
    @Test void emptySectionSetIsRejected() throws Exception {
        ObjectNode row = collection();
        row.putArray(REQUESTED_SECTIONS);
        rejectedAs(row, "Invalid collection fixture");
    }
    @Test void duplicateFixtureIdIsRejected() throws Exception {
        FrozenGateParameters p = parameters();
        List<ObjectNode> rows = fullRows(p);
        rows.get(1).put(FIXTURE_ID, rows.get(0).path(FIXTURE_ID).asText());
        Path file = write(rows.toArray(ObjectNode[]::new));
        assertThatThrownBy(() -> ClassBCaseManifest.load(file, p))
                .isInstanceOf(ClassBCaseManifest.InvalidManifest.class)
                .hasMessageContaining("Duplicate fixtureId");
    }
    @Test void repositoryPathIsRejected() throws Exception {
        FrozenGateParameters p = parameters();
        Path path = GateTestFixture.SOURCE;
        assertThatThrownBy(() -> ClassBCaseManifest.load(path, p))
                .isInstanceOf(ClassBCaseManifest.InvalidManifest.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
    @Test void frozenCountMismatchIsRejected() throws Exception {
        Path file = write(tip());
        FrozenGateParameters p = parameters();
        assertThatThrownBy(() -> ClassBCaseManifest.load(file, p))
                .isInstanceOf(ClassBCaseManifest.InvalidManifest.class)
                .hasMessageContaining("count differs");
    }
    @Test void unknownFieldAndWrongWorkflowAreRejected() throws Exception {
        ObjectNode row = tip();
        row.put("unexpected", SYNTHETIC);
        Path unknownField = write(row);
        FrozenGateParameters p = parameters();
        assertThatThrownBy(() -> ClassBCaseManifest.load(unknownField, p))
                .isInstanceOf(ClassBCaseManifest.InvalidManifest.class)
                .hasCauseInstanceOf(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class);
        row = tip();
        row.put(WORKFLOW_FIELD, "UNKNOWN");
        Path wrongWorkflow = write(row);
        assertThatThrownBy(() -> ClassBCaseManifest.load(wrongWorkflow, p))
                .isInstanceOf(ClassBCaseManifest.InvalidManifest.class)
                .hasCauseInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }

    @Test void fullFrozenCountsLoadInStoredOrder() throws Exception {
        FrozenGateParameters p = parameters();
        List<ObjectNode> rows = fullRows(p);
        Path file = write(rows.toArray(ObjectNode[]::new));
        List<ClassBCaseManifest.Case> loaded = ClassBCaseManifest.load(file, p);
        assertThat(loaded).hasSize(4 * p.reserveSize + p.workflows.get(STUDY_TIP).primarySize()
                + p.workflows.get(COLLECTION_COMPANION).primarySize()
                + p.workflows.get(ASK_COMPANION).primarySize()
                + p.workflows.get(INTERVIEW_CRITIQUE).primarySize());
        assertThat(loaded.getFirst().fixtureId()).isEqualTo("STUDY_TIP-0");
        assertThat(loaded.getLast().workflow()).isEqualTo(ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE);
    }

    private List<ObjectNode> fullRows(FrozenGateParameters p) {
        List<ObjectNode> rows = new java.util.ArrayList<>();
        for (ClassBCaseManifest.Workflow workflow : ClassBCaseManifest.Workflow.values()) {
            int primary = p.workflows.get(workflow.name()).primarySize();
            for (int i = 0; i < primary + p.reserveSize; i++) {
                ObjectNode row = switch (workflow) {
                    case STUDY_TIP -> tip();
                    case COLLECTION_COMPANION -> collection();
                    case ASK_COMPANION -> ask();
                    case INTERVIEW_CRITIQUE -> interview();
                };
                row.put(FIXTURE_ID, workflow.name() + "-" + i).put(RESERVE_FIELD, i >= primary);
                rows.add(row);
            }
        }
        return rows;
    }

    @Test void missingReserveAndUnknownNestedFieldAreRejected() throws Exception {
        ObjectNode row = tip();
        row.remove(RESERVE_FIELD);
        rejected(row);
        row = tip();
        ((ObjectNode) row.path(INCORRECT_QUESTIONS).get(0)).put("learnerMistake", SYNTHETIC);
        rejected(row);
        row = interview();
        ((ObjectNode) row.path("interviewQuestion")).remove("correctIndex");
        rejectedAs(row, "Missing or invalid interview correctIndex");
    }
}
