package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.AskCompanionTurn;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.config.OpenAiLlmConfig;
import com.studysnap.backend.repository.AskCompanionSessionRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.impl.OpenAiAskCompanionLlmService;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/** Offline class-B public-entry guards for preregistration sections 3, 6 and 7, Revision 2.2. */
class ClassBWorkflowAdaptersTest {
    private static final String MODEL = "gpt-6-luna";
    private static final String KEY = "offline-fake-key";
    private static final String QUESTION = "What should I review first?";
    private static final String ANSWER = "Review cell membranes first.";
    private static final String TIP = "Review membrane transport with a short recall drill.";
    private static final String CRITIQUE_RATIONALE = "The chosen answer follows the supplied explanation.";
    private static final String CRITIQUE_FOLLOW_UP = "Which assumption would you revisit?";
    private static final String PLAN_TITLE = "Cell Biology Plan";
    private static final String PLAN_CHILD_DESCRIPTION = "Organelles and membranes";
    private static final String PROGRAM = "Nursing";
    private static final String SCHEMA_OR_PARSE = "SCHEMA_OR_PARSE";
    private static final String HTTP_ERROR = "HTTP_ERROR";
    private static final String INPUT_DEFECT = "INPUT_DEFECT";
    private static final String STUDY_TIP_OPERATION = "note_lib_study_tip";
    private static final String COMPANION_OPERATION = "note_lib_companion_draft";
    private static final String CRITIQUE_OPERATION = "note_lib_interview_critique";
    private static final String BAD_PAYLOAD = "{broken";
    private static final String CHILD_TITLE = "Cell Structure";
    private static final String NOTE_TITLE = "Membrane Transport";
    private static final String NOTE_SUBJECT = "Diffusion and osmosis";
    private static final String VERDICT = "verdict";
    private static final String STRONG = "STRONG";
    private static final String RATIONALE = "rationale";
    private static final String FOLLOW_UP = "followUp";
    private static final BigDecimal PRICE = BigDecimal.ONE;
    private final ObjectMapper json = new ObjectMapper();
    @TempDir Path tempDir;

    @Test void studyTipUsesProductionEntryRoutingAndSanitizedDelivery() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.STUDY_TIP);
        StubFactory stub = new StubFactory();
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(adapter.deliveredOutput()).isEqualTo(TIP);
        verify(adapter.harness().mocked(StudyPackRepository.class))
                .findByIdAndOwnerUserId(adapter.harness().sourceId, adapter.harness().userId);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::operation)
                .containsExactly(STUDY_TIP_OPERATION);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::route)
                .containsExactly("INTERACTIVE_QUIZ");
        assertForwarded(adapter, stub);
    }

    @Test void swallowedStudyTipParseFailureIsFailed() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.STUDY_TIP);
        StubFactory stub = new StubFactory() {
            @Override String payload(JsonNode request) { return BAD_PAYLOAD; }
        };
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.FAILED);
        assertThat(outcome.failureClass()).isEqualTo(SCHEMA_OR_PARSE);
        assertThat(adapter.deliveredOutput()).isNull();
    }

    @Test void swallowedStudyTipHttpFailureIsFailed() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.STUDY_TIP);
        StubFactory stub = new StubFactory() {
            @Override int status() { return 500; }
        };
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.FAILED);
        assertThat(outcome.failureClass()).isEqualTo(HTTP_ERROR);
        assertThat(outcome.attempts()).extracting(AttemptOutcome::kind)
                .containsExactly(AttemptOutcome.Kind.HTTP_ERROR);
    }

    @Test void collectionUsesRealBuilderAndPaidRoute() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.COLLECTION_COMPANION);
        StubFactory stub = new StubFactory();
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::operation)
                .containsExactly(COMPANION_OPERATION);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::route)
                .containsExactly("PAID_INTERACTIVE");
        assertThat(adapter.deliveredOutput()).contains("Review cell membranes first");
        assertThat(adapter.deliveredOutput()).doesNotContain("resources");
        assertThat(new String(stub.forwarded.getFirst(), StandardCharsets.UTF_8))
                .contains(PLAN_TITLE, CHILD_TITLE, PLAN_CHILD_DESCRIPTION);
        verify(adapter.harness().mocked(NoteCollectionRepository.class))
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(
                        adapter.harness().sourceId, adapter.harness().userId);
        assertForwarded(adapter, stub);
    }

    @Test void collectionWithoutChildrenReadsOwnNoteProjections() throws Exception {
        var original = fixture(ClassBCaseManifest.Workflow.COLLECTION_COMPANION);
        var notesOnly = new CompanionGenerationContext(PLAN_TITLE, "Foundations",
                PROGRAM, List.of(), List.of(new CompanionGenerationContext.CompanionContextItem(
                        NOTE_TITLE, NOTE_SUBJECT)));
        var adapter = new CollectionCompanionAdapter(copy(original, null, notesOnly, null));
        StubFactory stub = new StubFactory();
        assertThat(run(adapter, stub).state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(new String(stub.forwarded.getFirst(), StandardCharsets.UTF_8))
                .contains(NOTE_TITLE, NOTE_SUBJECT);
        verify(adapter.harness().mocked(com.studysnap.backend.repository.NoteCollectionItemRepository.class))
                .findByCollectionIdOrderByPositionAsc(adapter.harness().sourceId);
    }

    @Test void collectionAllSectionsUsesProductionNormalizer() throws Exception {
        var original = fixture(ClassBCaseManifest.Workflow.COLLECTION_COMPANION);
        var all = new ClassBCaseManifest.Case(original.fixtureId(), original.workflow(),
                original.stratum(), original.reserve(), original.sourceLevel(), original.incorrectQuestions(),
                List.of(CompanionSection.values()), original.companionContext(), original.companion(),
                original.priorTurns(), original.question(), original.answerableFromCompanion(),
                original.interviewQuestion(), original.selectedChoiceIndex());
        var adapter = new CollectionCompanionAdapter(all);
        assertThat(run(adapter, new StubFactory()).state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(adapter.deliveredOutput()).contains("faq", "mentorTips", "linkedAction");
    }

    @Test void companionTerminalParseFailureUsesProductionSignal() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.COLLECTION_COMPANION);
        StubFactory stub = new StubFactory() {
            @Override String payload(JsonNode request) { return BAD_PAYLOAD; }
        };
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.FAILED);
        assertThat(outcome.failureClass()).isEqualTo(SCHEMA_OR_PARSE);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::outcome)
                .containsExactly(SCHEMA_OR_PARSE, SCHEMA_OR_PARSE);
    }

    @Test void askUsesServiceResultWithoutLlmCallSignal() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.ASK_COMPANION);
        StubFactory stub = new StubFactory();
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(adapter.signals()).isEmpty();
        assertThat(adapter.deliveredOutput()).isEqualTo(ANSWER);
        assertThat(outcome.attempts()).hasSize(1);
        assertThat(json.readTree(stub.forwarded.getFirst()).path("text").path("format")
                .path("name").asText()).isEqualTo("note_lib_ask_companion_answer");
        verify(adapter.harness().mocked(AskCompanionSessionRepository.class)).save(any());
        assertForwarded(adapter, stub);
    }

    @Test void askUnavailableFromBadPayloadIsFailureWithoutReparsing() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.ASK_COMPANION);
        StubFactory stub = new StubFactory() {
            @Override String payload(JsonNode request) { return BAD_PAYLOAD; }
        };
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.FAILED);
        assertThat(outcome.failureClass()).isEqualTo("ASK_COMPANION_UNAVAILABLE");
        assertThat(adapter.signals()).isEmpty();
    }

    @Test void askFifthPriorTurnStillReachesOneModelCall() throws Exception {
        var original = fixture(ClassBCaseManifest.Workflow.ASK_COMPANION);
        List<AskCompanionTurn> turns = new ArrayList<>();
        for (int index = 0; index < 5; index++)
            turns.add(new AskCompanionTurn("Earlier question " + index, ANSWER,
                    OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5 - index)));
        var adapter = new AskCompanionAdapter(copy(original, null, null, turns));
        StubFactory stub = new StubFactory();
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(outcome.attempts()).hasSize(1);
        assertThat(new String(stub.forwarded.getFirst(), StandardCharsets.UTF_8))
                .contains("Earlier question 0", "Earlier question 4");
    }

    @Test void askHttpFailureUsesMeteredStatus() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.ASK_COMPANION);
        StubFactory stub = new StubFactory() {
            @Override int status() { return 503; }
        };
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.FAILED);
        assertThat(outcome.failureClass()).isEqualTo(HTTP_ERROR);
    }

    @Test void askTimeoutUsesMeteredTimeout() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.ASK_COMPANION);
        StubFactory stub = new StubFactory() {
            @Override boolean timeout() { return true; }
        };
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.FAILED);
        assertThat(outcome.failureClass()).isEqualTo("TIMEOUT");
    }

    @Test void interviewUsesCritiqueSlotAndRecordsUnredactedFeedback() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE);
        StubFactory stub = new StubFactory();
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::operation)
                .containsExactly(CRITIQUE_OPERATION);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::route)
                .containsExactly("CRITIQUE");
        assertThat(adapter.deliveredOutput()).contains(CRITIQUE_RATIONALE, CRITIQUE_FOLLOW_UP);
        assertThat(adapter.deliveredOutput()).doesNotContain("nextQuestion");
        verify(adapter.harness().mocked(QuickReviewSessionRepository.class), atLeastOnce())
                .findByIdAndUserIdAndSessionModeForUpdate(any(), any(), eq(
                        com.studysnap.backend.entity.QuickReviewSessionMode.ADAPTIVE));
        assertForwarded(adapter, stub);
    }

    @Test void interviewTerminalParseFailureUsesProductionSignal() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE);
        StubFactory stub = new StubFactory() {
            @Override String payload(JsonNode request) { return BAD_PAYLOAD; }
        };
        var outcome = run(adapter, stub);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.FAILED);
        assertThat(outcome.failureClass()).isEqualTo(SCHEMA_OR_PARSE);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::outcome)
                .containsExactly(SCHEMA_OR_PARSE);
    }

    @Test void allFourForwardOnlyProductionFieldsByteForByte() throws Exception {
        for (ClassBCaseManifest.Workflow workflow : ClassBCaseManifest.Workflow.values()) {
            var adapter = adapter(workflow);
            StubFactory stub = new StubFactory();
            assertThat(run(adapter, stub).state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
            assertForwarded(adapter, stub);
        }
    }

    @Test void fixtureBelowStudyTipMinimumIsRefused() throws Exception {
        var original = fixture(ClassBCaseManifest.Workflow.STUDY_TIP);
        var invalid = copy(original, List.of(), original.companionContext(), original.priorTurns());
        var adapter = new QuickReviewStudyTipAdapter(invalid);
        assertThat(run(adapter, new StubFactory()).failureClass()).isEqualTo(INPUT_DEFECT);
    }

    @Test void askTurnLimitFixtureIsRefusedBeforeTransport() throws Exception {
        var original = fixture(ClassBCaseManifest.Workflow.ASK_COMPANION);
        List<AskCompanionTurn> turns = new ArrayList<>();
        for (int index = 0; index < 6; index++)
            turns.add(new AskCompanionTurn("Earlier question " + index, ANSWER,
                    OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(6 - index)));
        var adapter = new AskCompanionAdapter(copy(original, null, null, turns));
        StubFactory stub = new StubFactory();
        assertThat(run(adapter, stub).failureClass()).isEqualTo(INPUT_DEFECT);
        assertThat(stub.forwarded).isEmpty();
    }

    @Test void interviewChoiceOutsideQuestionIsRefusedBeforeTransport() throws Exception {
        var original = fixture(ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE);
        var invalid = new ClassBCaseManifest.Case(original.fixtureId(), original.workflow(),
                original.stratum(), original.reserve(), original.sourceLevel(), original.incorrectQuestions(),
                original.requestedSections(), original.companionContext(), original.companion(),
                original.priorTurns(), original.question(), original.answerableFromCompanion(),
                original.interviewQuestion(), 4);
        var adapter = new InterviewCritiqueAdapter(invalid);
        StubFactory stub = new StubFactory();
        assertThat(run(adapter, stub).failureClass()).isEqualTo(INPUT_DEFECT);
        assertThat(stub.forwarded).isEmpty();
    }

    @Test void nonAdminCompanionFixtureIsRefusedBeforeGeneration() {
        UserEntity user = new UserEntity();
        user.setRole(UserRole.USER);
        user.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        assertThatThrownBy(() -> CollectionCompanionAdapter.requireAdmin(user))
                .isInstanceOf(AbstractClassBAdapter.InputDefect.class);
    }

    @Test void cachedInterviewFeedbackIsAnInputDefect() {
        var item = fixture(ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE);
        var harness = new ClassBServiceHarness(item, RestClient.create(), ArmRouting.forModel(MODEL, KEY));
        var session = harness.interviewSession(item);
        session.setSessionState(QuizSessionStateUtils.withInterviewFeedback(session.getSessionState(), 0,
                Map.of(VERDICT, STRONG, RATIONALE, CRITIQUE_RATIONALE,
                        FOLLOW_UP, CRITIQUE_FOLLOW_UP)));
        assertThatThrownBy(() -> InterviewCritiqueAdapter.requireNoCachedFeedback(session))
                .isInstanceOf(AbstractClassBAdapter.InputDefect.class);
    }

    @Test void askWrongModelIsRefusedBeforeTransportAndReservation() throws Exception {
        var item = fixture(ClassBCaseManifest.Workflow.ASK_COMPANION);
        StubFactory stub = new StubFactory();
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN));
        var path = new ArmAssertingInterceptor(json, MODEL, KEY, tempDir, ledger,
                StageCapLedger.Stage.STAGE_1, PRICE, PRICE,
                Map.of(item.workflow() + ":" + item.stratum(),
                        new ForecastReservation(1_000, 1_000, PRICE, PRICE, PRICE)));
        RestClient client = RestClient.builder().requestFactory(stub).baseUrl("https://example.invalid")
                .requestInterceptor(path).build();
        var wrong = ArmRouting.forModel("gpt-6-sol", KEY);
        var llm = new OpenAiAskCompanionLlmService(wrong, json, client,
                new OpenAiLlmConfig().openAiPromptResources(json, wrong));
        path.begin(item.fixtureId(), item.workflow() + ":" + item.stratum());
        try {
            assertThatThrownBy(() -> llm.answer(item.companion(), item.priorTurns(), item.question()))
                    .isInstanceOf(ArmAssertingInterceptor.ArmMismatch.class);
        } finally {
            path.end();
        }
        assertThat(stub.forwarded).isEmpty();
        assertThat(ledger.stopped()).isTrue();
        assertThat(ledger.attempts(StageCapLedger.Stage.STAGE_1)).isZero();
    }

    @Test void blindBridgeKeepsArmAndModelOutOfPreparedOutput() throws Exception {
        var adapter = adapter(ClassBCaseManifest.Workflow.ASK_COMPANION);
        var outcome = run(adapter, new StubFactory());
        var prepared = ClassBOutcomeBridge.prepare(fixture(ClassBCaseManifest.Workflow.ASK_COMPANION),
                adapter, outcome);
        assertThat(prepared.blindedOutput()).isEqualTo(ANSWER);
        assertThat(prepared.blindedOutput()).doesNotContain(MODEL, "BASELINE", "CANDIDATE");
        assertThat(prepared.forArm(ClassBScoreAggregator.Arm.BASELINE).text()).isEqualTo(ANSWER);
        assertThat(prepared.structural().evidence()).isEqualTo(RouteVerdictCalculator.Evidence.SCORED);
    }

    @Test void blindBridgePreservesSwallowedParseAsStructuralFailure() throws Exception {
        var fixture = fixture(ClassBCaseManifest.Workflow.STUDY_TIP);
        var adapter = new QuickReviewStudyTipAdapter(fixture);
        StubFactory stub = new StubFactory() {
            @Override String payload(JsonNode request) { return BAD_PAYLOAD; }
        };
        var outcome = run(adapter, stub);
        var prepared = ClassBOutcomeBridge.prepare(fixture, adapter, outcome);
        assertThat(prepared.blindedOutput()).isNull();
        assertThat(prepared.structural().finalFailureClass()).isEqualTo(SCHEMA_OR_PARSE);
        assertThat(prepared.structural().t3Components()).contains(SCHEMA_OR_PARSE);
        assertThatThrownBy(() -> prepared.forArm(ClassBScoreAggregator.Arm.BASELINE))
                .isInstanceOf(ClassBOutcomeBridge.UnscorableOutput.class);
    }

    private void assertForwarded(AbstractClassBAdapter adapter, StubFactory stub) throws Exception {
        assertThat(stub.forwarded).hasSize(1);
        JsonNode body = json.readTree(stub.forwarded.getFirst());
        assertThat(body.path("model").asText()).isEqualTo(MODEL);
        assertThat(body.has("max_output_tokens")).isFalse();
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("reasoning")).isFalse();
        assertThat(adapter.requestBodyDigests()).containsExactly(
                ArmAssertingInterceptor.digest(stub.forwarded.getFirst()));
    }

    private CaseArmOutcome run(AbstractClassBAdapter adapter, ClientHttpRequestFactory stub) throws Exception {
        var input = new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN),
                Map.of(adapter.cellId(), new ForecastReservation(1_000, 1_000, PRICE, PRICE, PRICE)),
                PRICE, PRICE, null, tempDir);
        return StageRunner.run(input, stub, List.of(adapter)).outcomes().getFirst();
    }

    private AbstractClassBAdapter adapter(ClassBCaseManifest.Workflow workflow) {
        var item = fixture(workflow);
        return switch (workflow) {
            case STUDY_TIP -> new QuickReviewStudyTipAdapter(item);
            case COLLECTION_COMPANION -> new CollectionCompanionAdapter(item);
            case ASK_COMPANION -> new AskCompanionAdapter(item);
            case INTERVIEW_CRITIQUE -> new InterviewCritiqueAdapter(item);
        };
    }

    private ClassBCaseManifest.Case fixture(ClassBCaseManifest.Workflow workflow) {
        var context = new CompanionGenerationContext(PLAN_TITLE, "Foundations for the unit exam",
                PROGRAM, List.of(new CompanionGenerationContext.CompanionContextItem(
                        CHILD_TITLE, PLAN_CHILD_DESCRIPTION)), List.of());
        return new ClassBCaseManifest.Case("synthetic-" + workflow, workflow, "synthetic", false,
                workflow == ClassBCaseManifest.Workflow.STUDY_TIP ? LearnerLevel.COLLEGE : null,
                workflow == ClassBCaseManifest.Workflow.STUDY_TIP
                        ? List.of(new QuickReviewIncorrectQuestionRequest("What is a membrane?", "Cell boundary",
                                "The membrane bounds the cell.")) : null,
                workflow == ClassBCaseManifest.Workflow.COLLECTION_COMPANION
                        ? List.of(CompanionSection.OVERVIEW) : null,
                workflow == ClassBCaseManifest.Workflow.COLLECTION_COMPANION ? context : null,
                workflow == ClassBCaseManifest.Workflow.ASK_COMPANION
                        ? new CompanionContent("Cell membranes protect cells.", null, null, null,
                                List.of(), List.of()) : null,
                workflow == ClassBCaseManifest.Workflow.ASK_COMPANION ? List.of() : null,
                workflow == ClassBCaseManifest.Workflow.ASK_COMPANION ? QUESTION : null,
                workflow == ClassBCaseManifest.Workflow.ASK_COMPANION ? true : null,
                workflow == ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE
                        ? new ClassBCaseManifest.Question("Which answer fits?", List.of("A", "B", "C", "D"),
                                1, "Membranes", "B follows the supplied evidence.") : null,
                workflow == ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE ? 1 : null);
    }

    private ClassBCaseManifest.Case copy(ClassBCaseManifest.Case source,
            List<QuickReviewIncorrectQuestionRequest> incorrect,
            CompanionGenerationContext context, List<com.studysnap.backend.entity.AskCompanionTurn> turns) {
        return new ClassBCaseManifest.Case(source.fixtureId(), source.workflow(), source.stratum(),
                source.reserve(), source.sourceLevel(), incorrect, source.requestedSections(), context,
                source.companion(), turns, source.question(), source.answerableFromCompanion(),
                source.interviewQuestion(), source.selectedChoiceIndex());
    }

    private class StubFactory implements ClientHttpRequestFactory {
        final List<byte[]> forwarded = new ArrayList<>();
        int status() { return 200; }
        boolean timeout() { return false; }
        @Override public MockClientHttpRequest createRequest(java.net.URI uri, HttpMethod method) {
            return new MockClientHttpRequest(method, uri) {
                @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                    if (timeout()) throw new java.net.SocketTimeoutException("offline timeout");
                    byte[] body = getBodyAsBytes();
                    assertThat(java.util.Arrays.equals(body, ArmAssertingInterceptor.inFlightRequestBody())).isTrue();
                    forwarded.add(body.clone());
                    JsonNode request = json.readTree(body);
                    ObjectNode response = json.createObjectNode();
                    response.put("output_text", payload(request));
                    response.putObject("usage").put("input_tokens", 100).put("output_tokens", 100);
                    return new MockClientHttpResponse(json.writeValueAsBytes(response), status());
                }
            };
        }
        String payload(JsonNode request) throws IOException {
            String name = request.path("text").path("format").path("name").asText();
            return switch (name) {
                case STUDY_TIP_OPERATION -> json.writeValueAsString(Map.of("tip", "  " + TIP + "  "));
                case "note_lib_ask_companion_answer" ->
                        json.writeValueAsString(Map.of("answer", "  " + ANSWER + "  "));
                case CRITIQUE_OPERATION -> json.writeValueAsString(Map.of(
                        VERDICT, STRONG, RATIONALE, CRITIQUE_RATIONALE,
                        FOLLOW_UP, CRITIQUE_FOLLOW_UP));
                case COMPANION_OPERATION -> {
                    ObjectNode value = json.createObjectNode();
                    value.put("overview", ANSWER);
                    value.put("studyStrategy", "Practice one subject each day.");
                    value.put("commonMistakes", "Avoid passive rereading.");
                    var faq = value.putArray("faq");
                    faq.addObject().put("question", "Where to start?").put("answer", "Begin with cells.");
                    faq.addObject().put("question", "How to review?").put("answer", "Use short recall blocks.");
                    faq.addObject().put("question", "When to stop?").put("answer", "After a self-check.");
                    value.putArray("mentorTips").addObject().put("title", "Focus on one item")
                            .put("body", "Use a short recall drill.");
                    yield json.writeValueAsString(value);
                }
                default -> throw new IllegalArgumentException("Unexpected production schema " + name);
            };
        }
    }
}
