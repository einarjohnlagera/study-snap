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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.studysnap.backend.service.ChallengeQuizService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegEBenchmarkTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String KEY = "fake-secret-never-persist";

    @Test void profilesAndAllocationMatchProductionConstants() throws Exception {
        assertThat(LlmModelBenchmark.ECell.of("S10").questions()).isEqualTo(productionCount("LOW_SCORE_QUESTION_COUNT"));
        assertThat(LlmModelBenchmark.ECell.of("S12").questions()).isEqualTo(productionCount("MID_SCORE_QUESTION_COUNT"));
        assertThat(LlmModelBenchmark.ECell.of("S15").questions()).isEqualTo(productionCount("HIGH_SCORE_QUESTION_COUNT"));
        assertThat(LlmModelBenchmark.ECell.of("S12").difficulty()).isEqualTo(productionString("DEFAULT_SELECTED_DIFFICULTY"));
        assertThat(LlmModelBenchmark.ECell.of("S10").difficulty()).isEqualTo(productionString("DIFFICULTY_EASY"));
        assertThat(LlmModelBenchmark.ECell.of("S15").difficulty()).isEqualTo(productionString("DIFFICULTY_HARD"));
        for (String cell : List.of("M6x3","M3x6")) {
            var profile = LlmModelBenchmark.ECell.of(cell);
            assertThat(profile.questions()).isEqualTo(productionCount("MULTI_NOTE_CHALLENGE_QUESTION_COUNT"));
            for (int i=0;i<profile.sourceCount();i++)
                assertThat(profile.allocation(i)).isEqualTo(18/profile.sourceCount() + (i == 0 ? 18%profile.sourceCount() : 0));
        }
    }
    private int productionCount(String field) throws Exception {
        var value = ChallengeQuizService.class.getDeclaredField(field); value.setAccessible(true); return value.getInt(null);
    }
    private String productionString(String field) throws Exception {
        var value = ChallengeQuizService.class.getDeclaredField(field); value.setAccessible(true); return (String)value.get(null);
    }

    @Test void lowEffortIsInjectedOnRetryAndNeverSaved() throws Exception {
        ObjectNode empty = mapper.createObjectNode(); empty.putArray("output");
        empty.putObject("usage").put("input_tokens",100).put("output_tokens",0);
        List<MockClientHttpRequest> requests = run(List.of(line("VLOW",1,1)),List.of(empty,response(payload("low",15))));
        assertThat(requests).hasSize(2);
        for (MockClientHttpRequest request : requests) {
            byte[] body = request.getBodyAsBytes();
            assertThat(request.getHeaders().getContentLength()).isEqualTo(body.length);
            assertThat(mapper.readTree(body).path("reasoning").path("effort").asText()).isEqualTo("low");
        }
        assertThat(result().path("reasoningEffort").asText()).isEqualTo("low");
        assertThat(Files.readString(temp.resolve("results.jsonl"))).doesNotContain(KEY);
        try (var raw = Files.list(temp.resolve("raw"))) {
            for (Path path : raw.toList()) assertThat(Files.readString(path)).doesNotContain(KEY);
        }
    }

    @Test void singleNoteUsesSavedQuizAvoidListAndDeduplicates() throws Exception {
        ObjectNode line = line("S15",1,1);
        ObjectNode payload = payload("single",15);
        ((ObjectNode)payload.path("questions").get(0)).put("question","Already saved question?");
        List<MockClientHttpRequest> requests = run(List.of(line),List.of(response(payload)));
        String body = mapper.readTree(requests.getFirst().getBodyAsBytes()).toString();
        assertThat(body).contains("Already saved question?").doesNotContain("\"reasoning\"");
        assertThat(result().path("acceptedQuestions").asInt()).isEqualTo(14);
        assertThat(result().path("acceptedAtLeastRequested").asBoolean()).isFalse();
    }

    @Test void multiNoteUsesRunningAvoidListAndRecordsLatencies() throws Exception {
        List<MockClientHttpRequest> requests = run(List.of(line("M3x6",1,3)),List.of(
                response(payload("first",6)),response(payload("second",6)),response(payload("third",6))));
        assertThat(requests).hasSize(3);
        String first = mapper.readTree(requests.get(0).getBodyAsBytes()).toString();
        String second = mapper.readTree(requests.get(1).getBodyAsBytes()).toString();
        assertThat(first).doesNotContain("Excluded questions (must not be repeated): first question 0");
        assertThat(second).contains("Excluded questions (must not be repeated): first question 0");
        JsonNode row = result();
        long sum = 0, max = 0;
        for (JsonNode source : row.path("sources")) {
            long latency = source.path("latencyMs").asLong(); sum += latency; max = Math.max(max,latency);
        }
        assertThat(row.path("startLatencyMs").asLong()).isEqualTo(sum);
        assertThat(row.path("maxSourceCallLatencyMs").asLong()).isEqualTo(max);
        List<String> summary = Files.readAllLines(temp.resolve("summary.csv"));
        String cellRow = summary.stream().filter(s -> s.contains(",M3x6,")).findFirst().orElseThrow();
        assertThat(cellRow.split(",",-1)).hasSameSizeAs(summary.getFirst().split(",",-1));
        assertThat(summary.getFirst()).contains("startLatencyP90Ms","meanMaxSourceCallLatencyMs","conservativeUsd","nonAdditiveUsd");
    }

    @Test void multiNoteFinalSetExcludesPrimarySavedQuizTexts() throws Exception {
        ObjectNode first = payload("first",6);
        ((ObjectNode)first.path("questions").get(0)).put("question","Already saved question?");
        run(List.of(line("M3x6",1,3)),List.of(response(first),response(payload("second",6)),response(payload("third",6))));
        assertThat(result().path("acceptedQuestions").asInt()).isEqualTo(17);
        assertThat(result().path("acceptedAtLeastRequested").asBoolean()).isFalse();
    }

    @Test void failedSecondSourceAbortsAndRecordsPartialResult() throws Exception {
        ObjectNode error = mapper.createObjectNode(); error.putObject("error").put("message","rejected");
        List<MockClientHttpRequest> requests = run(List.of(line("M3x6",1,3)),List.of(response(payload("first",6)),error));
        assertThat(requests).hasSize(2);
        assertThat(result().path("failedSourceIndex").asInt()).isEqualTo(1);
        assertThat(result().path("acceptedQuestions").asInt()).isEqualTo(6);
        assertThat(result().path("failureClass").asText()).isEqualTo("HTTP_ERROR");
    }

    @Test void spendStopBetweenSourcesKeepsCompletedSources() throws Exception {
        Path input = temp.resolve("spend-input.jsonl");
        Files.writeString(input,mapper.writeValueAsString(line("M3x6",1,3)) + "\n");
        byte[] responseBytes = mapper.writeValueAsBytes(response(payload("first",6)));
        AtomicInteger attempts = new AtomicInteger();
        var factory = (org.springframework.http.client.ClientHttpRequestFactory)(uri,method) -> {
            attempts.incrementAndGet();
            MockClientHttpRequest request = new MockClientHttpRequest(method,uri);
            request.setResponse(new MockClientHttpResponse(responseBytes,200));
            return request;
        };
        var config = new LlmModelBenchmark.Config("test",input,temp,EnumSet.of(LlmModelBenchmark.Leg.E),
                10,new BigDecimal("0.0001"),BigDecimal.ONE,BigDecimal.ONE,KEY);
        LlmModelBenchmark.run(config,factory);
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(result().path("failureClass").asText()).isEqualTo("SPEND_STOP");
        assertThat(result().path("acceptedQuestions").asInt()).isEqualTo(6);
        assertThat(result().path("failedSourceIndex").asInt()).isEqualTo(1);
        // A spend-stopped run is not a real start, so it must not reach the cell's latency row.
        assertThat(Files.readString(temp.resolve("summary.csv"))).doesNotContain("M3x6");
    }

    @Test void readTimeoutIsHttpErrorWithTimeoutFlag() throws Exception {
        Path input = temp.resolve("timeout-input.jsonl");
        Files.writeString(input,mapper.writeValueAsString(line("S15",1,1)) + "\n");
        var config = new LlmModelBenchmark.Config("test",input,temp,EnumSet.of(LlmModelBenchmark.Leg.E),
                10,new BigDecimal("8"),BigDecimal.ONE,BigDecimal.ONE,KEY);
        LlmModelBenchmark.run(config,(uri,method) -> { throw new java.net.SocketTimeoutException("Read timed out"); });
        assertThat(result().path("failureClass").asText()).isEqualTo("HTTP_ERROR");
        assertThat(result().path("timeout").asBoolean()).isTrue();
    }

    @Test void filtersAndPreflightRunBeforeHttp() throws Exception {
        System.setProperty("llm.benchmark.runs","2-10");
        System.setProperty("llm.benchmark.cells","M3x6");
        System.setProperty("llm.benchmark.cellRunCap","M3x6:4");
        try {
            List<ObjectNode> lines = new ArrayList<>();
            for (int i=1;i<=6;i++) lines.add(line("M3x6",i,3));
            run(lines,List.of(response(payload("a",6)),response(payload("b",6)),response(payload("c",6)),
                    response(payload("d",6)),response(payload("e",6)),response(payload("f",6)),
                    response(payload("g",6)),response(payload("h",6)),response(payload("i",6))));
            assertThat(Files.readAllLines(temp.resolve("results.jsonl"))).hasSize(3);
            assertThat(result().path("run").asInt()).isEqualTo(2);
        } finally {
            System.clearProperty("llm.benchmark.runs"); System.clearProperty("llm.benchmark.cells");
            System.clearProperty("llm.benchmark.cellRunCap");
        }
        Path other = temp.resolve("preflight"); Files.createDirectories(other);
        ObjectNode shortLine = line("M3x6",1,2);
        AtomicInteger calls = new AtomicInteger();
        Files.writeString(other.resolve("input.jsonl"),mapper.writeValueAsString(line("S15",1,1)) + "\n"
                + mapper.writeValueAsString(shortLine) + "\n");
        var config = new LlmModelBenchmark.Config("test",other.resolve("input.jsonl"),other,
                EnumSet.of(LlmModelBenchmark.Leg.E),10,new BigDecimal("8"),BigDecimal.ONE,BigDecimal.ONE,KEY);
        assertThatThrownBy(() -> runAndCount(config, calls))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Leg E preflight failed");
        assertThat(calls.get()).isZero();
        assertThat(Files.readString(other.resolve("summary.csv"))).contains("E");
    }

    private static void runAndCount(LlmModelBenchmark.Config config, AtomicInteger calls) throws Exception {
        LlmModelBenchmark.run(config,(uri,method) -> { calls.incrementAndGet(); throw new AssertionError("HTTP called"); });
    }

    private List<MockClientHttpRequest> run(List<ObjectNode> lines,List<ObjectNode> responses) throws Exception {
        Path input = temp.resolve("input.jsonl");
        StringBuilder text = new StringBuilder();
        for (ObjectNode line : lines) text.append(mapper.writeValueAsString(line)).append('\n');
        Files.writeString(input,text.toString());
        List<MockClientHttpRequest> requests = new ArrayList<>();
        AtomicInteger next = new AtomicInteger();
        var factory = (org.springframework.http.client.ClientHttpRequestFactory)(uri,method) -> {
            MockClientHttpRequest request = new MockClientHttpRequest(method,uri);
            int index = next.getAndIncrement();
            ObjectNode response = responses.get(index);
            request.setResponse(new MockClientHttpResponse(mapper.writeValueAsBytes(response), response.has("error") ? 400 : 200));
            requests.add(request);
            return request;
        };
        var config = new LlmModelBenchmark.Config("test",input,temp,EnumSet.of(LlmModelBenchmark.Leg.E),
                100,new BigDecimal("8"),BigDecimal.ONE,BigDecimal.ONE,KEY);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream(); PrintStream old = System.out;
        try { System.setOut(new PrintStream(stdout)); LlmModelBenchmark.run(config,factory); }
        finally { System.setOut(old); }
        assertThat(stdout.toString(StandardCharsets.UTF_8)).doesNotContain(KEY);
        return requests;
    }
    private JsonNode result() throws Exception { return mapper.readTree(Files.readAllLines(temp.resolve("results.jsonl")).getFirst()); }
    private ObjectNode line(String cell,int run,int count) {
        ObjectNode line = mapper.createObjectNode(); line.put("id",cell + "-" + run); line.put("leg","E");
        line.put("cell",cell); line.put("run",run); ArrayNode sources = line.putArray("sources");
        for (int i=0;i<count;i++) {
            ObjectNode source = sources.addObject(); source.put("studyPackTitle","Respiration");
            source.put("studyPackSummary","Cells produce ATP by respiration.");
            source.put("sourceNoteText","Cellular respiration uses oxygen.");
            source.putArray("keyConcepts").add("ATP").add("Glycolysis");
            source.putArray("disallowedQuestions").add("Already saved question?");
            source.putArray("tags").add("cells"); source.put("learnerLevel","COLLEGE");
            source.put("noteLearnerLevel","COLLEGE"); source.put("subject","Biology");
        }
        return line;
    }
    private ObjectNode payload(String prefix,int count) {
        ObjectNode payload = mapper.createObjectNode(); ArrayNode questions = payload.putArray("questions");
        for (int i=0;i<count;i++) {
            ObjectNode q = questions.addObject(); q.put("question",prefix + " question " + i + "?");
            q.putArray("choices").add("ATP").add("DNA").add("RNA").add("Glucose");
            q.put("answer","A"); q.put("explanation","ATP is produced in respiration."); q.put("concept","ATP");
        }
        return payload;
    }
    private ObjectNode response(ObjectNode payload) throws Exception {
        ObjectNode response = mapper.createObjectNode(); response.put("output_text",mapper.writeValueAsString(payload));
        response.putObject("usage").put("input_tokens",100).put("output_tokens",100);
        return response;
    }
}
