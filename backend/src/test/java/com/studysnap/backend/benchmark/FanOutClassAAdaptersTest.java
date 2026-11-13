package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.repository.ExamQuestionPoolRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.service.StudyPackGenerationContextResolver;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/** Offline production fan-out and key guards for preregistration sections 3, 5, 6 and 7. */
class FanOutClassAAdaptersTest {
    private static final String MODEL = "gpt-6-luna";
    private static final String KEY = "offline-fake-key";
    private static final String MEMBRANE = "Membrane";
    private static final String BIOLOGY = "Biology";
    private static final String QUESTIONS = "questions";
    private static final String OUTPUT_TEXT = "output_text";
    private static final String INPUT_TOKENS = "input_tokens";
    private static final String OUTPUT_TOKENS = "output_tokens";
    private static final String BATCH_ONE = "-batch-1";
    private static final String BATCH_TWO = "-batch-2";
    private static final String SEQUENTIAL_ONE = "-seq-1";
    private static final BigDecimal PRICE = BigDecimal.ONE;
    private final ObjectMapper json = new ObjectMapper();
    @TempDir Path tempDir;

    @Test void leanDirectOneAndTwoSourceCallsAndKeys() throws Exception {
        assertLean(ClassACaseManifest.Subgroup.DIRECT, 1, 2);
        assertLean(ClassACaseManifest.Subgroup.DIRECT, 2, 4);
    }

    @Test void leanLongPoolCallsAndKeys() throws Exception {
        assertLean(ClassACaseManifest.Subgroup.LONG_EXAM_POOL, 1, 2);
    }

    @Test void leanBoardPoolCallsAndKeys() throws Exception {
        assertLean(ClassACaseManifest.Subgroup.BOARD_EXAM_POOL, 1, 2);
    }

    private void assertLean(ClassACaseManifest.Subgroup subgroup, int sources, int calls) throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(subgroup, sources), 2);
        StubFactory transport = new StubFactory(Set.of(), Set.of());
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(adapter.signals()).hasSize(calls);
        assertThat(transport.forwarded).hasSize(calls);
        assertThat(outcome.attempts()).hasSize(calls);
        assertThat(outcome.firstAttemptClean()).isTrue();
        assertThat(outcome.attempts().stream().map(AttemptOutcome::generationId).distinct()).hasSize(calls);
        assertThat(adapter.deliveredQuestions()).hasSize(adapter.item.requestedQuestionCount());
        assertThat(adapter.deliveredQuestions()).allSatisfy(question ->
                assertThat(question.getCorrectIndex() != null || question.getCorrectIndices() != null)
                        .as("persisted delivered question has an answer key").isTrue());
        for (byte[] body : transport.forwarded) {
            JsonNode request = json.readTree(body);
            assertThat(request.path("model").asText()).isEqualTo(MODEL);
            assertThat(request.has("max_output_tokens")).isFalse();
            assertThat(request.has("temperature")).isFalse();
            assertThat(request.has("reasoning")).isFalse();
            assertThat(adapter.requestBodyDigests()).contains(ArmAssertingInterceptor.digest(body));
        }
        assertThat(adapter.signals()).allSatisfy(signal -> {
            assertThat(signal.operation()).isEqualTo(subgroup == ClassACaseManifest.Subgroup.BOARD_EXAM_POOL
                    ? "note_lib_board_exam" : "note_lib_long_exam");
            assertThat(signal.route()).isEqualTo("INTERACTIVE_QUIZ");
            assertThat(signal.model()).isEqualTo(MODEL);
        });
        if (subgroup != ClassACaseManifest.Subgroup.BOARD_EXAM_POOL) {
            assertThat(adapter.signals()).allSatisfy(signal ->
                    assertThat(signal.batchId()).startsWith(adapter.id() + "-batch-"));
            assertThat(adapter.harness().batchExecutor().submissions()).isEqualTo(calls);
            assertThat(transport.forwardedByBatch.get(adapter.id() + BATCH_ONE).stream()
                    .map(body -> new String(body, StandardCharsets.UTF_8))).allSatisfy(body ->
                    assertThat(body).contains("first half of the material"));
            assertThat(transport.forwardedByBatch.get(adapter.id() + BATCH_TWO).stream()
                    .map(body -> new String(body, StandardCharsets.UTF_8))).allSatisfy(body ->
                    assertThat(body).contains("second half of the material"));
        }
        verify(adapter.harness().mocked(StudyPackGenerationContextResolver.class), atLeastOnce())
                .resolveForStudyPack(any(), any());
        if (subgroup == ClassACaseManifest.Subgroup.DIRECT)
            verify(adapter.harness().mocked(QuickReviewSessionRepository.class), atLeastOnce()).save(any());
        else verify(adapter.harness().mocked(ExamQuestionPoolRepository.class), atLeastOnce()).save(any());
    }

    @Test void singleWorkerKeepsBatchOneRetrySeparateFromBatchTwo() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Subgroup.DIRECT, 1), 1);
        CaseArmOutcome outcome = run(adapter, new StubFactory(Set.of(1), Set.of()));
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(outcome.attempts()).extracting(AttemptOutcome::generationId)
                .containsExactly(adapter.id() + BATCH_ONE, adapter.id() + BATCH_ONE,
                        adapter.id() + BATCH_TWO);
        assertThat(outcome.attempts()).extracting(AttemptOutcome::attempt).containsExactly(1, 2, 1);
        assertThat(outcome.firstAttemptClean()).isFalse();
        assertThat(outcome.finalSuccess()).isTrue();
    }

    @Test void parallelWorkersKeepBatchOneRetrySeparateFromBatchTwo() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Subgroup.DIRECT, 1), 2);
        CaseArmOutcome outcome = run(adapter, new StubFactory(Set.of(1), Set.of()));
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(outcome.attempts().stream().filter(attempt ->
                attempt.generationId().equals(adapter.id() + BATCH_ONE)).toList())
                .extracting(AttemptOutcome::attempt).containsExactly(1, 2);
        assertThat(outcome.attempts().stream().filter(attempt ->
                attempt.generationId().equals(adapter.id() + BATCH_TWO)).toList())
                .extracting(AttemptOutcome::attempt).containsExactly(1);
        assertThat(outcome.firstAttemptClean()).isFalse();
        assertThat(outcome.finalSuccess()).isTrue();
    }

    @Test void secondBatchRetryStaysInSecondGroup() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Subgroup.LONG_EXAM_POOL, 1), 2);
        CaseArmOutcome outcome = run(adapter, new StubFactory(Set.of(2), Set.of()));
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(outcome.attempts().stream().filter(attempt ->
                attempt.generationId().equals(adapter.id() + BATCH_ONE)).toList())
                .extracting(AttemptOutcome::attempt).containsExactly(1);
        assertThat(outcome.attempts().stream().filter(attempt ->
                attempt.generationId().equals(adapter.id() + BATCH_TWO)).toList())
                .extracting(AttemptOutcome::attempt).containsExactly(1, 2);
        assertThat(outcome.firstAttemptClean()).isFalse();
    }

    @Test void failedSecondBatchSalvagesWithDistinctSequentialGroup() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Subgroup.DIRECT, 1), 2);
        CaseArmOutcome outcome = run(adapter, new StubFactory(Set.of(), Set.of(2)));
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(outcome.attempts().stream().filter(attempt ->
                attempt.generationId().equals(adapter.id() + BATCH_ONE)).toList()).hasSize(1);
        assertThat(outcome.attempts().stream().filter(attempt ->
                attempt.generationId().equals(adapter.id() + BATCH_TWO)).toList())
                .extracting(AttemptOutcome::attempt).containsExactly(1, 2);
        assertThat(outcome.attempts().getLast().generationId()).isEqualTo(adapter.id() + SEQUENTIAL_ONE);
        assertThat(outcome.firstAttemptClean()).isFalse();
        assertThat(outcome.finalSuccess()).isTrue();
    }

    @Test void bothFailedBatchesUseFullSequentialFallback() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Subgroup.DIRECT, 1), 1);
        CaseArmOutcome outcome = run(adapter, new StubFactory(Set.of(), Set.of(1, 2)));
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(outcome.attempts()).extracting(AttemptOutcome::generationId)
                .containsExactly(adapter.id() + BATCH_ONE, adapter.id() + BATCH_ONE,
                        adapter.id() + BATCH_TWO, adapter.id() + BATCH_TWO,
                        adapter.id() + SEQUENTIAL_ONE);
        assertThat(outcome.firstAttemptClean()).isFalse();
    }

    @Test void boardPoolTwoSequentialCallsAndRetryOnlyFirst() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Subgroup.BOARD_EXAM_POOL, 1), 2);
        CaseArmOutcome outcome = run(adapter, new StubFactory(Set.of(0), Set.of()));
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(outcome.attempts()).extracting(AttemptOutcome::generationId)
                .containsExactly(adapter.id() + SEQUENTIAL_ONE, adapter.id() + SEQUENTIAL_ONE,
                        adapter.id() + "-seq-2");
        assertThat(outcome.attempts()).extracting(AttemptOutcome::attempt).containsExactly(1, 2, 1);
        assertThat(outcome.firstAttemptClean()).isFalse();
    }

    @Test void workerTransportTimeoutIsNotClean() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Subgroup.DIRECT, 1), 2);
        StubFactory transport = new StubFactory(Set.of(), Set.of()) {
            @Override boolean timeout(int batch, int attempt) { return batch == 1; }
        };
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(outcome.firstAttemptClean()).isFalse();
        assertThat(outcome.attempts()).extracting(AttemptOutcome::kind)
                .contains(AttemptOutcome.Kind.TIMEOUT);
    }

    @Test void workerHttpErrorIsNotClean() throws Exception {
        AbstractClassAAdapter adapter = adapter(caseFor(ClassACaseManifest.Subgroup.DIRECT, 1), 2);
        StubFactory transport = new StubFactory(Set.of(), Set.of()) {
            @Override int status(int batch, int attempt) { return batch == 1 ? 500 : 200; }
        };
        CaseArmOutcome outcome = run(adapter, transport);
        assertThat(outcome.firstAttemptClean()).isFalse();
        assertThat(outcome.attempts()).extracting(AttemptOutcome::kind)
                .contains(AttemptOutcome.Kind.HTTP_ERROR);
    }

    private CaseArmOutcome run(AbstractClassAAdapter adapter, StubFactory transport) throws Exception {
        StageRunner.RunInput input = new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN),
                Map.of(adapter.cellId(), new ForecastReservation(5_000, 5_000, PRICE, PRICE, PRICE)),
                PRICE, PRICE, null, tempDir);
        return StageRunner.run(input, transport, List.of(adapter)).outcomes().getFirst();
    }

    private ClassACaseManifest.Case caseFor(ClassACaseManifest.Subgroup subgroup, int sources) {
        List<ClassACaseManifest.Source> sourceList = new ArrayList<>();
        for (int index = 0; index < sources; index++)
            sourceList.add(new ClassACaseManifest.Source("Cells " + index,
                    "Cells have membranes. Synthetic source " + index + ".",
                    "Cells contain a membrane. Synthetic source " + index + ".",
                    List.of(MEMBRANE), List.of("cells"), BIOLOGY, BIOLOGY, null,
                    LearnerLevel.COLLEGE));
        int count = switch (subgroup) {
            case DIRECT -> 25;
            case LONG_EXAM_POOL -> 48;
            case BOARD_EXAM_POOL -> 24;
        };
        return new ClassACaseManifest.Case("synthetic-" + subgroup + "-" + sources,
                ClassACaseManifest.Workflow.LONG_EXAM, "synthetic", LearnerLevel.COLLEGE,
                null, BIOLOGY, false, List.copyOf(sourceList), count, null, false, subgroup);
    }

    private AbstractClassAAdapter adapter(ClassACaseManifest.Case item, int workers) {
        return switch (item.subgroup()) {
            case DIRECT -> new LongExamDirectAdapter(item, workers);
            case LONG_EXAM_POOL -> new LongExamPoolAdapter(item, workers);
            case BOARD_EXAM_POOL -> new BoardExamPoolAdapter(item);
        };
    }

    private class StubFactory implements ClientHttpRequestFactory {
        final List<byte[]> forwarded = new CopyOnWriteArrayList<>();
        final Map<String, List<byte[]>> forwardedByBatch = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final Set<Integer> badFirstBatches;
        private final Set<Integer> badAllBatches;
        private final AtomicInteger sequence = new AtomicInteger();

        StubFactory(Set<Integer> badFirstBatches, Set<Integer> badAllBatches) {
            this.badFirstBatches = badFirstBatches;
            this.badAllBatches = badAllBatches;
        }

        boolean timeout(int batch, int attempt) { return false; }
        int status(int batch, int attempt) { return 200; }

        @Override public MockClientHttpRequest createRequest(java.net.URI uri, HttpMethod method) {
            return new MockClientHttpRequest(method, uri) {
                @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                    byte[] body = getBodyAsBytes();
                    assertThat(body).isEqualTo(ArmAssertingInterceptor.inFlightRequestBody());
                    forwarded.add(body.clone());
                    String batch = BatchTaggingExecutor.currentBatch();
                    if (batch != null) forwardedByBatch.computeIfAbsent(batch,
                            ignored -> new CopyOnWriteArrayList<>()).add(body.clone());
                    int batchNumber = batch == null ? 0 : Integer.parseInt(batch.substring(batch.lastIndexOf('-') + 1));
                    int attempt = attempts.computeIfAbsent(batch == null ? "sequential" : batch,
                            ignored -> new AtomicInteger()).incrementAndGet();
                    if (timeout(batchNumber, attempt)) throw new java.net.SocketTimeoutException("offline timeout");
                    int call = sequence.incrementAndGet();
                    String response = badAllBatches.contains(batchNumber)
                            || badFirstBatches.contains(batchNumber) && attempt == 1
                            ? broken() : valid(json.readTree(body), call);
                    return new MockClientHttpResponse(response.getBytes(StandardCharsets.UTF_8),
                            status(batchNumber, attempt));
                }
            };
        }

        private String broken() { return "{\"output_text\":\"broken\",\"usage\":{\"input_tokens\":100,\"output_tokens\":100}}"; }

        private String valid(JsonNode body, int call) throws IOException {
            int count = body.path("text").path("format").path("schema").path("properties")
                    .path(QUESTIONS).path("minItems").asInt();
            ObjectNode payload = json.createObjectNode();
            ArrayNode questions = payload.putArray(QUESTIONS);
            for (int index = 0; index < count; index++) {
                ObjectNode question = questions.addObject();
                question.put("question", "Synthetic generated question " + call + "-" + index + "?");
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
