package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mutation checks for preregistration section 7 human class-B scoring. */
class ClassBScoreFilesTest {
    private static final String FIRST = "blind-00001";
    private static final String SECOND = "blind-00002";
    private static final String HUMAN_OWNER = "HUMAN_OWNER";
    private static final String HUMAN_ADJUDICATOR = "HUMAN_ADJUDICATOR";
    private static final String MODEL_CODEX = "MODEL_CODEX";
    private static final String TASK_FULFILLED = "taskFulfilled";
    private static final String GROUNDED = "grounded";
    private static final String NO_CRITICAL = "noCriticalMisleading";
    private static final String CRITICAL = "critical";
    private static final String REASON_CODE = "reasonCode";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ITEM_ID_FIELD = "itemId";
    private static final String WORKFLOW_FIELD = "workflow";
    private static final String STUDY_TIP = "STUDY_TIP";
    private static final String SCORER_ID_FIELD = "scorerId";
    private static final String SCORER_KIND_FIELD = "scorerKind";
    private static final String HUMAN_KIND = "HUMAN";
    private static final String ADJUDICATION_FIELD = "adjudication";
    private static final String TASK_REASON = "TASK_NOT_FULFILLED";
    private static final String COST_COMPONENT = "COST";
    private static final String QUALITY_COMPONENT = "QUALITY";
    private static final String CASE_ID = "case-1";
    @TempDir Path temp;

    private List<ClassBScoreFiles.BlindItem> manifest() {
        return List.of(new ClassBScoreFiles.BlindItem(FIRST, ClassBCaseManifest.Workflow.STUDY_TIP, false),
                new ClassBScoreFiles.BlindItem(SECOND, ClassBCaseManifest.Workflow.STUDY_TIP, false));
    }
    private Map<String, ClassBScoreFiles.ScorerKind> scorers() {
        return Map.of(HUMAN_OWNER, ClassBScoreFiles.ScorerKind.HUMAN,
                HUMAN_ADJUDICATOR, ClassBScoreFiles.ScorerKind.HUMAN,
                MODEL_CODEX, ClassBScoreFiles.ScorerKind.MODEL);
    }
    private ObjectNode row(String item, String scorer, int round) {
        ObjectNode node = JSON.createObjectNode();
        node.put(ITEM_ID_FIELD, item).put(WORKFLOW_FIELD, STUDY_TIP).put("round", round)
                .put(SCORER_ID_FIELD, scorer).put(SCORER_KIND_FIELD, scorer.startsWith("HUMAN_") ? HUMAN_KIND : "MODEL")
                .put(TASK_FULFILLED, true).put(GROUNDED, true).put("actionable", true)
                .put(NO_CRITICAL, true).put(CRITICAL, false).putNull(REASON_CODE)
                .put("borderline", false).put(ADJUDICATION_FIELD, false);
        return node;
    }
    private Path write(List<ObjectNode> rows) throws Exception {
        Path file = temp.resolve("scores.jsonl");
        List<String> lines = new ArrayList<>();
        for (ObjectNode row : rows) lines.add(JSON.writeValueAsString(row));
        Files.write(file, lines);
        return file;
    }
    private ClassBScoreFiles.Validated validate(Path file) {
        return ClassBScoreFiles.validate(file, manifest(), scorers());
    }

    @Test void criticalMustExactlyMatchFailedFourthDimension() throws Exception {
        ObjectNode row = row(FIRST, HUMAN_OWNER, 1);
        row.put(CRITICAL, true).put(REASON_CODE, "CRITICAL_MISLEADING");
        Path file = write(List.of(row));
        assertThatThrownBy(() -> validate(file)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        row.put(CRITICAL, false).put(NO_CRITICAL, false);
        Path reverse = write(List.of(row));
        assertThatThrownBy(() -> validate(reverse)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }

    @Test void unknownFieldIsRejected() throws Exception {
        ObjectNode row = row(FIRST, HUMAN_OWNER, 1);
        row.put("sourceText", "synthetic");
        Path file = write(List.of(row));
        assertThatThrownBy(() -> validate(file)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }
    @Test void unknownScorerAndKindMismatchAreRejected() throws Exception {
        ObjectNode row = row(FIRST, "HUMAN_UNKNOWN", 1);
        Path file = write(List.of(row));
        assertThatThrownBy(() -> validate(file)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        row.put(SCORER_ID_FIELD, MODEL_CODEX).put(SCORER_KIND_FIELD, HUMAN_KIND);
        Path mismatch = write(List.of(row));
        assertThatThrownBy(() -> validate(mismatch)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }
    @Test void duplicateScoreIsRejected() throws Exception {
        ObjectNode row = row(FIRST, HUMAN_OWNER, 1);
        Path file = write(List.of(row, row));
        assertThatThrownBy(() -> validate(file)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }
    @Test void unmappedItemAndWrongWorkflowAreRejected() throws Exception {
        ObjectNode row = row("unmapped", HUMAN_OWNER, 1);
        Path file = write(List.of(row));
        assertThatThrownBy(() -> validate(file)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        row.put(ITEM_ID_FIELD, FIRST).put(WORKFLOW_FIELD, "ASK_COMPANION");
        Path wrongWorkflow = write(List.of(row));
        assertThatThrownBy(() -> validate(wrongWorkflow)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }
    @Test void modelOnlyScoresCannotSatisfyPrimaryHumanCoverage() throws Exception {
        Path file = write(List.of(row(FIRST, MODEL_CODEX, 1), row(SECOND, MODEL_CODEX, 1)));
        ClassBScoreFiles.Validated valid = validate(file);
        assertThatThrownBy(valid::requireFullHumanCoverage)
                .isInstanceOf(ClassBScoreFiles.MissingHumanCoverage.class);
        List<ClassBScoreFiles.BlindItem> narrowedManifest = List.of();
        assertThatThrownBy(() -> valid.requireFullHumanCoverage(narrowedManifest))
                .isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        ClassBScoreAggregator.Result result = ClassBScoreAggregator.aggregate(valid, key(), structural());
        assertThat(result.inconclusiveItems()).containsExactlyInAnyOrder(FIRST, SECOND);
        assertThat(result.primary().getFirst().candidate().evidence())
                .isEqualTo(RouteVerdictCalculator.Evidence.UNSCORABLE);
    }
    @Test void humanDisagreementIsUnresolvedUntilLaterAdjudication() throws Exception {
        ObjectNode second = row(FIRST, HUMAN_ADJUDICATOR, 2);
        second.put(GROUNDED, false).put(REASON_CODE, "UNGROUNDED");
        Path file = write(List.of(row(FIRST, HUMAN_OWNER, 1), second));
        ClassBScoreFiles.Validated unresolved = validate(file);
        assertThat(unresolved.finalHumanScores()).doesNotContainKey(FIRST);
        ClassBScoreAggregator.Result unresolvedPair = ClassBScoreAggregator.aggregate(unresolved, key(), structural());
        assertThat(unresolvedPair.primary().getFirst().baseline().evidence())
                .isEqualTo(RouteVerdictCalculator.Evidence.UNSCORABLE);
        ObjectNode adjudication = row(FIRST, HUMAN_OWNER, 3);
        adjudication.put(TASK_FULFILLED, false).put(REASON_CODE, TASK_REASON)
                .put(ADJUDICATION_FIELD, true);
        Path adjudicated = write(List.of(row(FIRST, HUMAN_OWNER, 1), second, adjudication));
        ClassBScoreFiles.Validated resolved = validate(adjudicated);
        assertThat(resolved.finalHumanScores().get(FIRST).round()).isEqualTo(3);
        assertThat(resolved.finalHumanScores().get(FIRST).taskFulfilled()).isFalse();
        assertThat(resolved.finalHumanScores().get(FIRST).grounded()).isTrue();
        ClassBScoreAggregator.Result adjudicatedPair = ClassBScoreAggregator.aggregate(resolved, key(), structural());
        assertThat(adjudicatedPair.primary().getFirst().baseline().t3Components())
                .containsExactlyInAnyOrder(COST_COMPONENT, QUALITY_COMPONENT);
    }
    @Test void missingBooleanAndReasonCodeAreRejected() throws Exception {
        ObjectNode row = row(FIRST, HUMAN_OWNER, 1);
        row.put(TASK_FULFILLED, "true");
        Path file = write(List.of(row));
        assertThatThrownBy(() -> validate(file)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        row = row(FIRST, HUMAN_OWNER, 1);
        row.put(GROUNDED, false);
        Path missingReason = write(List.of(row));
        assertThatThrownBy(() -> validate(missingReason)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }

    @Test void missingDimensionUnknownKindAndUnexpectedReasonAreRejected() throws Exception {
        ObjectNode row = row(FIRST, HUMAN_OWNER, 1);
        row.remove(TASK_FULFILLED);
        Path missing = write(List.of(row));
        assertThatThrownBy(() -> validate(missing)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        row = row(FIRST, HUMAN_OWNER, 1);
        row.put(SCORER_KIND_FIELD, "ALIEN");
        Path unknownKind = write(List.of(row));
        assertThatThrownBy(() -> validate(unknownKind)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        row = row(FIRST, HUMAN_OWNER, 1);
        row.put(REASON_CODE, TASK_REASON);
        Path unexpectedReason = write(List.of(row));
        assertThatThrownBy(() -> validate(unexpectedReason)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }

    @Test void modelRowsNeverControlTruthOrQuality() throws Exception {
        ObjectNode model = row(FIRST, MODEL_CODEX, 1);
        model.put(TASK_FULFILLED, false).put(REASON_CODE, TASK_REASON);
        Path file = write(List.of(row(FIRST, HUMAN_OWNER, 1), row(SECOND, HUMAN_OWNER, 1), model));
        ClassBScoreFiles.Validated valid = validate(file);
        valid.requireFullHumanCoverage();
        ClassBScoreAggregator.Result result = ClassBScoreAggregator.aggregate(valid, key(), structural());
        assertThat(result.primary().getFirst().baseline().t3Components()).containsExactly(COST_COMPONENT);
        assertThat(result.primary().getFirst().candidate().t3Components()).containsExactly(COST_COMPONENT);
        assertThat(result.agreementReport().get(STUDY_TIP).disagreement()).isEqualTo(1);
        assertThat(result.agreementReport().get(STUDY_TIP).humanOverrideRate()).isEqualTo(1.0);
    }

    @Test void humanDimensionsBecomeQualityAndCriticalWithStructuralEventsPreserved() throws Exception {
        ObjectNode candidate = row(SECOND, HUMAN_OWNER, 1);
        candidate.put(TASK_FULFILLED, false).put(NO_CRITICAL, false).put(CRITICAL, true)
                .put(REASON_CODE, "MULTIPLE_DEFICITS");
        ClassBScoreFiles.Validated valid = validate(write(List.of(row(FIRST, HUMAN_OWNER, 1), candidate)));
        ClassBScoreAggregator.Result result = ClassBScoreAggregator.aggregate(valid, key(), structural());
        RouteVerdictCalculator.ArmResult arm = result.primary().getFirst().candidate();
        assertThat(arm.t3Components()).containsExactlyInAnyOrder(COST_COMPONENT, QUALITY_COMPONENT);
        assertThat(arm.critical()).isTrue();
        assertThat(result.primary().getFirst().baseline().critical()).isFalse();
    }

    private List<ClassBScoreAggregator.Key> key() {
        return List.of(new ClassBScoreAggregator.Key(FIRST, ClassBCaseManifest.Workflow.STUDY_TIP,
                    CASE_ID, ClassBScoreAggregator.Arm.BASELINE, false),
                new ClassBScoreAggregator.Key(SECOND, ClassBCaseManifest.Workflow.STUDY_TIP,
                    CASE_ID, ClassBScoreAggregator.Arm.CANDIDATE, false));
    }
    private Map<String, RouteVerdictCalculator.ArmResult> structural() {
        RouteVerdictCalculator.ArmResult clean = new RouteVerdictCalculator.ArmResult(
                RouteVerdictCalculator.Evidence.SCORED, Set.of(COST_COMPONENT), false, null, List.of(), false, false);
        return Map.of(FIRST, clean, SECOND, clean);
    }
}
