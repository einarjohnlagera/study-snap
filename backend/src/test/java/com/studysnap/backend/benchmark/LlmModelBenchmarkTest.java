package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmModelBenchmarkTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String FAKE_KEY = "fake-authorization-secret-do-not-write";

    @Test void rejectsSpendLimitAboveTen() {
        assertThatThrownBy(() -> LlmModelBenchmark.Config.checkedSpendStop("10.01"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("<= 10.00");
    }

    @Test void refusesRepoOutputPath() {
        Path repo = Path.of("").toAbsolutePath().normalize().getParent();
        assertThatThrownBy(() -> LlmModelBenchmark.Config.rejectInsideRepoForTest(repo.resolve("notes.jsonl"),repo))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("backend/target");
    }

    @Test void noKeyFailsBeforeAnyCall() {
        // Config.system checks the environment before it can construct a client. This test remains
        // deterministic on machines with a key by checking the same validation entry point directly.
        assertThatThrownBy(() -> LlmModelBenchmark.Config.requireKeyForTest(" "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("LLM_API_KEY");
    }

    @Test void reasoningThenMessageIsSuccessAndBothPricesAreRecorded() throws Exception {
        ObjectNode response = response(validPayload("first"), 200);
        response.remove("output_text");
        ArrayNode output = response.putArray("output");
        output.addObject().put("type","reasoning");
        output.addObject().put("type","message").putArray("content").addObject().put("type","output_text").put("text",mapper.writeValueAsString(validPayload("first")));
        run(List.of(response), 1, new BigDecimal("8"), 10);
        JsonNode result = firstResult();
        assertThat(result.path("failureClass").isNull()).isTrue();
        assertThat(result.path("calls").get(0).path("outputTypes").get(0).asText()).isEqualTo("reasoning");
        assertThat(result.path("reasoningTokens").asLong()).isEqualTo(30);
        assertThat(result.path("conservativeUsd").decimalValue()).isEqualByComparingTo("0.00046");
        assertThat(result.path("nonAdditiveUsd").decimalValue()).isEqualByComparingTo("0.00040");
        assertThat(Files.readString(temp.resolve("raw/g1-note-1.json"))).doesNotContain(FAKE_KEY);
    }

    @Test void twoOutputTextsAreFlaggedWhileRealServiceUsesFirst() throws Exception {
        ObjectNode response = response(validPayload("first"), 200);
        response.remove("output_text");
        ArrayNode output = response.putArray("output");
        output.addObject().put("type","message").putArray("content").addObject().put("type","output_text").put("text",mapper.writeValueAsString(validPayload("first")));
        output.addObject().put("type","message").putArray("content").addObject().put("type","output_text").put("text",mapper.writeValueAsString(validPayload("second")));
        run(List.of(response), 1, new BigDecimal("8"), 10);
        JsonNode result = firstResult();
        assertThat(result.path("failureClass").asText()).isEqualTo("MULTI_OUTPUT_TEXT");
        assertThat(result.path("questions").get(0).path("question").asText()).startsWith("first");
    }

    @Test void emptyOutputAndRetryAreRecorded() throws Exception {
        ObjectNode empty = mapper.createObjectNode(); empty.putArray("output");
        empty.putObject("usage").put("input_tokens",100).put("output_tokens",0);
        run(List.of(empty, response(validPayload("valid"),200)), 1, new BigDecimal("8"), 10);
        JsonNode result = firstResult();
        assertThat(result.path("attempts").asInt()).isEqualTo(2);
        assertThat(Files.exists(temp.resolve("raw/g1-note-1.json"))).isTrue();
        assertThat(Files.exists(temp.resolve("raw/g1-note-2.json"))).isTrue();
    }

    @Test void emptyOutputFailsAfterServiceRetry() throws Exception {
        ObjectNode empty = mapper.createObjectNode(); empty.putArray("output");
        empty.putObject("usage").put("input_tokens",100).put("output_tokens",0);
        run(List.of(empty,empty), 1, new BigDecimal("8"), 10);
        assertThat(firstResult().path("failureClass").asText()).isEqualTo("EMPTY");
    }

    @Test void spendStopAndMaxCallsWriteSummaryBeforeNextHttpCall() throws Exception {
        run(List.of(response(validPayload("one"),200), response(validPayload("two"),200)), 2, new BigDecimal("0.0001"), 10);
        assertThat(results()).hasSize(2);
        assertThat(results().get(1).path("failureClass").asText()).isEqualTo("SPEND_STOP");
        assertThat(Files.readString(temp.resolve("summary.csv"))).contains("SPEND_STOP");
        assertThat(Files.list(temp.resolve("raw")).count()).isEqualTo(1);
    }

    @Test void maxCallsStopsBeforeSecondHttpCall() throws Exception {
        run(List.of(response(validPayload("one"),200), response(validPayload("two"),200)), 2, new BigDecimal("8"), 1);
        assertThat(results()).hasSize(2);
        assertThat(results().get(1).path("failureClass").asText()).isEqualTo("SPEND_STOP");
        assertThat(Files.list(temp.resolve("raw")).count()).isEqualTo(1);
    }

    @Test void malformedLineIsSkippedAndCounted() throws Exception {
        Path input = temp.resolve("malformed.jsonl");
        Files.writeString(input, "{\"id\":\"broken\",\"leg\":\"A\",\"tags\":[]}\n");
        ClientHttpRequestFactory noCalls = (uri, method) -> { throw new AssertionError("no HTTP call expected"); };
        LlmModelBenchmark.Config config = new LlmModelBenchmark.Config("test-model",input,temp,EnumSet.of(LlmModelBenchmark.Leg.A),10,new BigDecimal("8"),BigDecimal.ONE,BigDecimal.ONE,FAKE_KEY);
        LlmModelBenchmark.run(config,noCalls);
        assertThat(Files.readString(temp.resolve("summary.csv"))).contains(",1\n");
    }

    @Test void threeConsecutiveHttpErrorsStopRun() throws Exception {
        ObjectNode error = mapper.createObjectNode(); error.putObject("error").put("message","unknown model");
        run(List.of(error,error,error,error), 4, new BigDecimal("8"), 10, 400);
        assertThat(results()).hasSize(3);
        assertThat(results()).allSatisfy(r -> assertThat(r.path("failureClass").asText()).isEqualTo("HTTP_ERROR"));
        assertThat(firstResult().path("errorMessage").asText()).isEqualTo("unknown model");
    }

    @Test void transportFailuresCountAsHttpErrorsAndStopRun() throws Exception {
        Path input = temp.resolve("notes.jsonl");
        Files.writeString(input, "{\"id\":\"note\",\"leg\":\"A\",\"noteText\":\"Cell respiration\",\"tags\":[]}\n".repeat(4));
        ClientHttpRequestFactory factory = (uri, method) -> { throw new java.net.ConnectException("connection refused"); };
        LlmModelBenchmark.Config config = new LlmModelBenchmark.Config("test-model",input,temp,EnumSet.of(LlmModelBenchmark.Leg.A),20,new BigDecimal("8"),new BigDecimal("2"),new BigDecimal("2"),FAKE_KEY);
        LlmModelBenchmark.run(config,factory);
        assertThat(results()).hasSize(3);
        assertThat(results()).allSatisfy(r -> assertThat(r.path("failureClass").asText()).isEqualTo("HTTP_ERROR"));
    }

    private JsonNode firstResult() throws Exception { return results().getFirst(); }
    private List<JsonNode> results() throws Exception {
        try (var lines = Files.lines(temp.resolve("results.jsonl"))) { return lines.map(s -> { try { return mapper.readTree(s); } catch (Exception ex) { throw new RuntimeException(ex); } }).toList(); }
    }
    private void run(List<ObjectNode> responses, int lines, BigDecimal stop, int maxCalls) throws Exception { run(responses,lines,stop,maxCalls,200); }
    private void run(List<ObjectNode> responses, int lines, BigDecimal stop, int maxCalls, int status) throws Exception {
        Path input = temp.resolve("notes.jsonl");
        StringBuilder text = new StringBuilder();
        for (int i=0;i<lines;i++) text.append("{\"id\":\"note\",\"leg\":\"A\",\"noteText\":\"Cell respiration\",\"tags\":[]}").append('\n');
        Files.writeString(input,text.toString());
        Queue<ObjectNode> queue = new ArrayDeque<>(responses);
        ClientHttpRequestFactory factory = (uri, method) -> {
            MockClientHttpRequest request = new MockClientHttpRequest(method,uri);
            ObjectNode response = queue.remove();
            request.setResponse(new MockClientHttpResponse(mapper.writeValueAsBytes(response),status));
            return request;
        };
        LlmModelBenchmark.Config config = new LlmModelBenchmark.Config("test-model",input,temp,EnumSet.of(LlmModelBenchmark.Leg.A),maxCalls,stop,new BigDecimal("2"),new BigDecimal("2"),FAKE_KEY);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream before = System.out;
        try { System.setOut(new PrintStream(stdout)); LlmModelBenchmark.run(config,factory); }
        finally { System.setOut(before); }
        assertThat(stdout.toString(StandardCharsets.UTF_8)).doesNotContain(FAKE_KEY);
    }
    private ObjectNode response(ObjectNode payload,int status) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        result.put("model","test-model");
        result.put("output_text",mapper.writeValueAsString(payload));
        ObjectNode usage = result.putObject("usage"); usage.put("input_tokens",100); usage.put("output_tokens",100);
        usage.putObject("output_tokens_details").put("reasoning_tokens",30);
        return result;
    }
    private ObjectNode validPayload(String prefix) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("title","Cell Respiration Review"); payload.put("summary","Cell respiration produces ATP."); payload.put("subject","Biology");
        payload.putArray("tags").add("cells");
        ArrayNode concepts = payload.putArray("keyConcepts");
        for (String concept : List.of("Glycolysis","Pyruvate oxidation","Citric acid cycle","Electron transport chain","ATP synthesis","NADH","Oxygen","Mitochondria")) concepts.add(concept);
        ArrayNode quiz = payload.putArray("quiz");
        for (int i=0;i<5;i++) {
            ObjectNode q = quiz.addObject(); q.put("question",prefix + " question about respiration " + i + "?");
            q.putArray("choices").add("ATP").add("DNA").add("RNA").add("Glucose");
            q.put("answer","A"); q.put("concept","Concept " + i); q.put("explanation","ATP is the energy output of cell respiration.");
        }
        return payload;
    }
}
