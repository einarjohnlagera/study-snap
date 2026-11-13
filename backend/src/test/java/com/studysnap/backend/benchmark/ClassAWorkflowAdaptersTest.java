package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.StudyPackGenerationContextResolver;
import com.studysnap.backend.util.LlmResponseUtils;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/** Offline public-entry guards for preregistration sections 3, 7 and Addendum 2. */
class ClassAWorkflowAdaptersTest {
    private static final String MODEL = "gpt-6-luna";
    private static final String KEY = "offline-fake-key";
    private static final String INTERACTIVE_ROUTE = "INTERACTIVE_QUIZ";
    private static final String ADAPTIVE_OPERATION = "note_lib_adaptive_quiz";
    private static final String SCHEMA_OR_PARSE = "SCHEMA_OR_PARSE";
    private static final String SUCCESS = "SUCCESS";
    private static final String COMMENTARY = "plain commentary";
    private static final String OUTPUT_TEXT = "output_text";
    private static final String INPUT_TOKENS = "input_tokens";
    private static final String OUTPUT_TOKENS = "output_tokens";
    private static final String QUESTIONS = "questions";
    private static final String MEMBRANE = "Membrane";
    private static final String BIOLOGY = "Biology";
    private static final BigDecimal PRICE = BigDecimal.ONE;
    private final ObjectMapper json = new ObjectMapper();
    @TempDir Path tempDir;

    @Test void allArmSlotsUseTheCandidateIncludingCritique() {
        var settings = ArmRouting.forModel(MODEL, KEY).getSettings();
        assertThat(List.of(settings.getModelFree(), settings.getModelPremium(),
                settings.getModelInteractiveQuiz(), settings.getModelPaidInteractive(),
                settings.getModelCritique())).containsExactly(MODEL, MODEL, MODEL, MODEL, MODEL);
    }

    @Test void noteAdaptiveUsesProductionOperationAndRoute() throws Exception {
        assertWorkflow(ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                ADAPTIVE_OPERATION, INTERACTIVE_ROUTE);
    }

    @Test void planAdaptiveUsesProductionOperationAndRoute() throws Exception {
        assertWorkflow(ClassACaseManifest.Workflow.PLAN_ADAPTIVE,
                ADAPTIVE_OPERATION, INTERACTIVE_ROUTE);
    }

    @Test void teacherUsesProductionOperationAndRoute() throws Exception {
        assertWorkflow(ClassACaseManifest.Workflow.TEACHER_QUIZ,
                "note_lib_teacher_quiz", INTERACTIVE_ROUTE);
    }

    @Test void teacherPublicOverrideChangesTheProductionRequest() throws Exception {
        ClassACaseManifest.Case base = caseFor(ClassACaseManifest.Workflow.TEACHER_QUIZ, 1);
        ClassACaseManifest.Case override = new ClassACaseManifest.Case(base.caseId(), base.workflow(),
                base.stratum(), LearnerLevel.BOARD_EXAM_REVIEW, base.domainContext(), base.subject(),
                base.quantitative(), base.sources(), base.requestedQuestionCount(), base.difficulty(), base.reserve());
        StubFactory baseTransport = new StubFactory(false);
        StubFactory overrideTransport = new StubFactory(false);
        assertThat(run(adapter(base), baseTransport).state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(run(adapter(override), overrideTransport).state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(overrideTransport.forwarded.getFirst()).isNotEqualTo(baseTransport.forwarded.getFirst());
    }

    @Test void boardExamUsesProductionOperationAndRoute() throws Exception {
        assertWorkflow(ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT,
                "note_lib_board_exam", INTERACTIVE_ROUTE);
    }

    @Test void interviewUsesProductionOperationAndRoute() throws Exception {
        assertWorkflow(ClassACaseManifest.Workflow.INTERVIEW_PRACTICE,
                "note_lib_interview_practice", "PAID_INTERACTIVE");
    }

    private void assertWorkflow(ClassACaseManifest.Workflow workflow, String operation, String route) throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(workflow, 1));
        StubFactory transport = new StubFactory(false);
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(adapter.signals()).hasSize(1);
        assertThat(adapter.signals().getFirst().operation()).isEqualTo(operation);
        assertThat(adapter.signals().getFirst().route()).isEqualTo(route);
        assertThat(adapter.signals().getFirst().model()).isEqualTo(MODEL);
        assertThat(transport.forwarded).hasSize(1);
        assertThat(adapter.requestBodyDigests()).containsExactly(
                ArmAssertingInterceptor.digest(transport.forwarded.getFirst()));
        JsonNode body = json.readTree(transport.forwarded.getFirst());
        assertThat(body.path("model").asText()).isEqualTo(MODEL);
        assertThat(body.has("max_output_tokens")).isFalse();
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("reasoning")).isFalse();
        assertThat(adapter.deliveredQuestions()).isNotEmpty();
        // Blind key review needs the answer key of every delivered question; a redacted DTO would hide it.
        assertThat(adapter.deliveredQuestions()).allSatisfy(question ->
                assertThat(question.getCorrectIndex() != null || question.getCorrectIndices() != null)
                        .as("delivered question carries its answer key").isTrue());
        ClassAServiceHarness harness = adapter.harness();
        StudyPackGenerationContextResolver resolver = harness.mocked(StudyPackGenerationContextResolver.class);
        verify(harness.mocked(UserRepository.class), atLeastOnce()).findById(harness.userId);
        if (workflow == ClassACaseManifest.Workflow.TEACHER_QUIZ) {
            verify(resolver, atLeastOnce()).resolve(harness.userId, harness.note(0));
            verify(harness.mocked(GeneratedQuizRepository.class)).save(any());
        } else {
            verify(resolver, atLeastOnce()).resolveForStudyPack(eq(harness.userId), any());
            assertThat(harness.savedSessions()).hasSize(1);
            var saved = harness.savedSessions().getFirst();
            if (workflow == ClassACaseManifest.Workflow.NOTE_ADAPTIVE) {
                assertThat(saved.getStudyPackId()).isEqualTo(harness.pack(0).getId());
                assertThat(saved.getSourceCollectionId()).isNull();
            } else if (workflow == ClassACaseManifest.Workflow.PLAN_ADAPTIVE) {
                assertThat(saved.getStudyPackId()).isNull();
                assertThat(saved.getSourceCollectionId()).isNotNull();
            } else if (workflow == ClassACaseManifest.Workflow.INTERVIEW_PRACTICE) {
                assertThat(saved.getSessionState()).containsEntry("subMode", "INTERVIEW");
            } else {
                assertThat(saved.getSessionState()).containsEntry("mode", "board_exam");
            }
        }
    }

    @Test void sequentialSourcesAreDistinctCallsNotRetries() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Workflow.INTERVIEW_PRACTICE, 2));
        StubFactory transport = new StubFactory(false);
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(transport.forwarded).hasSize(2);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::attempt)
                .containsExactly(1, 1);
        assertThat(outcome.retried()).isFalse();
        assertThat(outcome.firstAttemptClean()).isTrue();
        assertThat(outcome.attempts()).extracting(AttemptOutcome::attempt).containsExactly(1, 1);
    }

    @Test void planAdaptiveTwoSourcesMakeTwoProductionCalls() throws Exception {
        assertTwoSourceCalls(ClassACaseManifest.Workflow.PLAN_ADAPTIVE);
    }

    @Test void productionDerivedCountMustMatchManifestReceipt() throws Exception {
        ClassACaseManifest.Case base = caseFor(ClassACaseManifest.Workflow.NOTE_ADAPTIVE, 1);
        ClassACaseManifest.Case wrong = new ClassACaseManifest.Case(base.caseId(), base.workflow(),
                base.stratum(), base.noteLevel(), base.domainContext(), base.subject(),
                base.quantitative(), base.sources(), 7, base.difficulty(), base.reserve());
        CaseArmOutcome outcome = run(adapter(wrong), new StubFactory(false));
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.FAILED);
        assertThat(outcome.failureClass()).isEqualTo("INPUT_COUNT_MISMATCH");
    }

    @Test void boardExamTwoSourcesMakeTwoProductionCalls() throws Exception {
        assertTwoSourceCalls(ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT);
    }

    private void assertTwoSourceCalls(ClassACaseManifest.Workflow workflow) throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(workflow, 2));
        StubFactory transport = new StubFactory(false);
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(transport.forwarded).hasSize(2);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::attempt)
                .containsExactly(1, 1);
        assertThat(outcome.retried()).isFalse();
        assertThat(outcome.firstAttemptClean()).isTrue();
        assertThat(outcome.attempts()).extracting(AttemptOutcome::attempt).containsExactly(1, 1);
    }

    @Test void recoveredParseFailureRemainsFirstAttemptFailure() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Workflow.TEACHER_QUIZ, 1));
        StubFactory transport = new StubFactory(true);
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::outcome)
                .containsExactly(SCHEMA_OR_PARSE, SUCCESS);
        assertThat(outcome.firstAttemptClean()).isFalse();
        assertThat(outcome.finalSuccess()).isTrue();
        assertThat(outcome.retried()).isTrue();
    }

    @Test void productionHttpErrorSignalMapsToAttemptKind() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Workflow.TEACHER_QUIZ, 1));
        StubFactory transport = new StubFactory(false) {
            @Override int status() { return 500; }
        };
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::outcome)
                .containsExactly("HTTP_ERROR");
        assertThat(outcome.attempts()).extracting(AttemptOutcome::kind)
                .containsExactly(AttemptOutcome.Kind.HTTP_ERROR);
        assertThat(outcome.finalSuccess()).isFalse();
    }

    @Test void productionTimeoutSignalMapsToAttemptKind() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Workflow.TEACHER_QUIZ, 1));
        StubFactory transport = new StubFactory(false) {
            @Override boolean timeout() { return true; }
        };
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::outcome)
                .containsExactly("TIMEOUT");
        assertThat(outcome.attempts()).extracting(AttemptOutcome::kind)
                .containsExactly(AttemptOutcome.Kind.TIMEOUT);
        assertThat(outcome.finalSuccess()).isFalse();
    }

    @Test void lunaTwoItemShapeFeedsCommentaryToRealExtractorThenRetries() throws Exception {
        StubFactory validProducer = new StubFactory(false);
        ObjectNode request = json.createObjectNode();
        request.putObject("text").putObject("format").putObject("schema").putObject("properties")
                .putObject(QUESTIONS).put("minItems", 10);
        String validFinalAnswer = json.readTree(validProducer.valid(request)).path(OUTPUT_TEXT).asText();
        ObjectNode envelope = twoItemEnvelope(COMMENTARY, validFinalAnswer);
        assertThat(LlmResponseUtils.findOutputJson(envelope)).contains(COMMENTARY);
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Workflow.TEACHER_QUIZ, 1));
        StubFactory transport = new StubFactory(true) {
            @Override String first(JsonNode body) throws IOException {
                String finalAnswer = json.readTree(valid(body)).path(OUTPUT_TEXT).asText();
                return json.writeValueAsString(twoItemEnvelope(COMMENTARY, finalAnswer));
            }
        };
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(adapter.signals()).extracting(ProductionCallSignals.Signal::outcome)
                .containsExactly(SCHEMA_OR_PARSE, SUCCESS);
        assertThat(outcome.firstAttemptClean()).isFalse();
        assertThat(outcome.finalSuccess()).isTrue();
    }

    private ObjectNode twoItemEnvelope(String commentary, String finalAnswer) {
        ObjectNode response = json.createObjectNode();
        ArrayNode output = response.putArray("output");
        output.addObject().put("type", "message").put("channel", "commentary")
                .putArray("content").addObject().put("type", OUTPUT_TEXT).put("text", commentary);
        output.addObject().put("type", "message").put("channel", "final_answer")
                .putArray("content").addObject().put("type", OUTPUT_TEXT).put("text", finalAnswer);
        response.putObject("usage").put(INPUT_TOKENS, 100).put(OUTPUT_TOKENS, 100);
        return response;
    }

    private CaseArmOutcome run(AbstractClassAAdapter adapter, StubFactory transport) throws Exception {
        StageRunner.RunInput input = new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN),
                Map.of(adapter.cellId(), new ForecastReservation(1_000, 1_000, PRICE, PRICE, PRICE)),
                PRICE, PRICE, null, tempDir);
        return StageRunner.run(input, transport, List.of(adapter)).outcomes().getFirst();
    }

    private ClassACaseManifest.Case caseFor(ClassACaseManifest.Workflow workflow, int sources) {
        List<ClassACaseManifest.Source> sourceList = new ArrayList<>();
        for (int index = 0; index < sources; index++) {
            sourceList.add(new ClassACaseManifest.Source("Cells " + index,
                    "Cells have membranes. Synthetic source " + index + ".",
                    "Cells contain a membrane. Synthetic source " + index + ".",
                    List.of(MEMBRANE), List.of("cells"), BIOLOGY, BIOLOGY, null,
                    LearnerLevel.COLLEGE));
        }
        int count = workflow == ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT ? 12 * sources
                : workflow == ClassACaseManifest.Workflow.TEACHER_QUIZ ? 10 : 5;
        return new ClassACaseManifest.Case("synthetic-" + workflow + "-" + sources,
                workflow, "synthetic", LearnerLevel.COLLEGE, null, BIOLOGY, false,
                List.copyOf(sourceList), count,
                workflow == ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT ? "mixed" : null, false);
    }

    private AbstractClassAAdapter adapter(ClassACaseManifest.Case item) {
        return (AbstractClassAAdapter) ClassAAdapters.from(item);
    }

    private class StubFactory implements ClientHttpRequestFactory {
        final List<byte[]> forwarded = new ArrayList<>();
        private final boolean badFirst;
        StubFactory(boolean badFirst) { this.badFirst = badFirst; }
        int status() { return 200; }
        boolean timeout() { return false; }
        @Override public MockClientHttpRequest createRequest(java.net.URI uri, HttpMethod method) {
            return new MockClientHttpRequest(method, uri) {
                @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                    if (timeout()) throw new java.net.SocketTimeoutException("offline timeout");
                    byte[] body = getBodyAsBytes();
                    assertThat(java.util.Arrays.equals(body,
                            ArmAssertingInterceptor.inFlightRequestBody())).isTrue();
                    forwarded.add(body.clone());
                    JsonNode request = json.readTree(body);
                    String response = badFirst && forwarded.size() == 1 ? first(request) : valid(request);
                    return new MockClientHttpResponse(response.getBytes(StandardCharsets.UTF_8), status());
                }
            };
        }
        String first(JsonNode body) throws IOException { return "{\"output_text\":\"broken\",\"usage\":{\"input_tokens\":100,\"output_tokens\":100}}"; }
        String valid(JsonNode body) throws IOException {
            int count = body.path("text").path("format").path("schema").path("properties")
                    .path(QUESTIONS).path("minItems").asInt(5);
            ObjectNode payload = json.createObjectNode();
            ArrayNode questions = payload.putArray(QUESTIONS);
            for (int index = 0; index < count; index++) {
                ObjectNode question = questions.addObject();
                question.put("question", "Synthetic generated question " + forwarded.size() + "-" + index + "?");
                question.putArray("choices").add("Correct").add("Wrong B").add("Wrong C").add("Wrong D");
                question.put("answer", "A");
                question.put("explanation", "Correct is supported by the synthetic note.");
                question.put("concept", MEMBRANE);
                question.put("keyConcept", MEMBRANE);
            }
            ObjectNode response = json.createObjectNode();
            response.put(OUTPUT_TEXT, json.writeValueAsString(payload));
            response.putObject("usage").put(INPUT_TOKENS, 100).put(OUTPUT_TOKENS, 100);
            return json.writeValueAsString(response);
        }
    }
}
