package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.client.ClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementPairStage2BenchmarkTest {
    private static final Path ROOT = Path.of("target/llm-benchmark/verifier");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preflightRejectsMalformedFixtureBeforeAnyHttpCall() throws Exception {
        Files.createDirectories(ROOT);
        Path input = Files.createTempFile(ROOT, "bad-stage2-", ".jsonl");
        Path out = Files.createTempDirectory(ROOT, "bad-stage2-out-");
        Files.writeString(input, "{\"id\":\"bad\"}\n");
        AtomicInteger calls = new AtomicInteger();
        ClientHttpRequestFactory factory = (uri, method) -> {
            calls.incrementAndGet();
            throw new AssertionError("preflight must stop before HTTP");
        };
        StatementPairStage2Benchmark.Config config = config("P", input, out, BigDecimal.ONE);
        assertThatThrownBy(() -> StatementPairStage2Benchmark.run(config, factory))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("preflight line");
        assertThat(calls).hasValue(0);
        assertThat(Files.exists(out.resolve("results.jsonl"))).isFalse();
    }

    @Test
    void legPUsesRealMorePathAndExportsBlindArmsWithMeteredStop() throws Exception {
        Files.createDirectories(ROOT);
        Path input = Files.createTempFile(ROOT, "p-stage2-", ".jsonl");
        Path out = Files.createTempDirectory(ROOT, "p-stage2-out-");
        Files.writeString(input, caseLine(false));
        AtomicInteger calls = new AtomicInteger();
        List<MockClientHttpRequest> requests = new ArrayList<>();
        ClientHttpRequestFactory factory = stubFactory(5, calls, requests);
        StatementPairStage2Benchmark.run(config("P", input, out, BigDecimal.ONE), factory);
        JsonNode summary = mapper.readTree(Files.readString(out.resolve("summary.json")));
        assertThat(summary.path("rows").asInt()).isEqualTo(2);
        assertThat(summary.path("meteredCalls").asInt()).isEqualTo(2);
        assertThat(calls).hasValue(2);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getBodyAsString()).contains("For this batch only");
        assertThat(requests.get(1).getBodyAsString()).doesNotContain("For this batch only");
        List<String> resultLines = Files.readAllLines(out.resolve("results.jsonl"));
        assertThat(resultLines).hasSize(2);
        assertThat(mapper.readTree(resultLines.getFirst()).path("returned").asInt()).isEqualTo(5);
        assertThat(mapper.readTree(resultLines.get(1)).path("returned").asInt()).isEqualTo(5);
        assertThat(Files.readString(out.resolve("blind-items.jsonl"))).doesNotContain("\"arm\"", "\"model\"");
    }

    @Test
    void legRStubsTheGoldAndCheckersThenMetersOnlyTheProductionReplacement() throws Exception {
        Files.createDirectories(ROOT);
        Path input = Files.createTempFile(ROOT, "r-stage2-", ".jsonl");
        Path out = Files.createTempDirectory(ROOT, "r-stage2-out-");
        Files.writeString(input, caseLine(true));
        AtomicInteger calls = new AtomicInteger();
        StatementPairStage2Benchmark.run(config("R", input, out, BigDecimal.ONE), stubFactory(1, calls));
        JsonNode summary = mapper.readTree(Files.readString(out.resolve("summary.json")));
        assertThat(summary.path("meteredCalls").asInt()).isEqualTo(1);
        assertThat(summary.path("requestedReplacements").asInt()).isEqualTo(1);
        assertThat(summary.path("returnedReplacements").asInt()).isEqualTo(1);
        assertThat(calls).hasValue(1);
        assertThat(Files.readString(out.resolve("blind-items.jsonl")))
                .contains("rejectedQuestion", "replacement", "sourceConcept");
    }

    @Test
    void meterStopsBeforeTheNextPaidCall() throws Exception {
        Files.createDirectories(ROOT);
        Path input = Files.createTempFile(ROOT, "stop-stage2-", ".jsonl");
        Path out = Files.createTempDirectory(ROOT, "stop-stage2-out-");
        Files.writeString(input, caseLine(false));
        AtomicInteger calls = new AtomicInteger();
        StatementPairStage2Benchmark.Config config = config("P", input, out, new BigDecimal("0.0001"));
        StatementPairStage2Benchmark.run(config, stubFactory(5, calls));
        JsonNode summary = mapper.readTree(Files.readString(out.resolve("summary.json")));
        assertThat(summary.path("stopped").asBoolean()).isTrue();
        assertThat(summary.path("meteredCalls").asInt()).isEqualTo(1);
        assertThat(calls).hasValue(1);
    }

    @Test
    void spendingStopCannotExceedThreeFifty() throws Exception {
        Files.createDirectories(ROOT);
        Path input = Files.createTempFile(ROOT, "cap-stage2-", ".jsonl");
        Path out = Files.createTempDirectory(ROOT, "cap-stage2-out-");
        Files.writeString(input, caseLine(false));
        AtomicInteger calls = new AtomicInteger();
        StatementPairStage2Benchmark.Config config = config("P", input, out, new BigDecimal("3.51"));
        ClientHttpRequestFactory factory = stubFactory(5, calls);
        assertThatThrownBy(() -> StatementPairStage2Benchmark.run(config, factory))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("spending limit");
        assertThat(calls).hasValue(0);
    }

    private StatementPairStage2Benchmark.Config config(String leg, Path input, Path out, BigDecimal stop) {
        return new StatementPairStage2Benchmark.Config(leg, "stub-generator", input, out,
                1, "P".equals(leg) ? 1 : 0, 3, BigDecimal.ONE, BigDecimal.ONE, stop, "fake-key");
    }

    private String caseLine(boolean replacement) throws Exception {
        ObjectNode row = mapper.createObjectNode().put("id", "water-case")
                .put("noteText", "Water expands when it freezes. Water is not a metal.")
                .put("summary", "Water expands on freezing.").put("subject", "Science")
                .put("learnerLevel", "COLLEGE");
        row.putArray("keyConcepts").add("Water");
        if (replacement) row.putArray("rejected").addObject()
                .put("question", "Statement 1: Water expands when frozen. Statement 2: Water is a metal. Which is correct?")
                .put("concept", "Water").put("statement1Correct", true).put("statement2Correct", false);
        return mapper.writeValueAsString(row) + "\n";
    }

    private ClientHttpRequestFactory stubFactory(int count, AtomicInteger calls) throws Exception {
        return stubFactory(count, calls, new ArrayList<>());
    }

    private ClientHttpRequestFactory stubFactory(int count, AtomicInteger calls,
            List<MockClientHttpRequest> requests) throws Exception {
        ObjectNode payload = mapper.createObjectNode();
        for (int i = 0; i < count; i++) {
            payload.withArray("questions").addObject().put("question", "What happens when water freezes? " + i)
                    .put("answer", "A").put("explanation", "Water expands when it freezes.")
                    .put("concept", "Water").put("questionFormat", "MCQ")
                    .putArray("choices").add("It expands").add("It burns").add("It vanishes").add("It becomes metal");
        }
        ObjectNode envelope = mapper.createObjectNode().put("model", "stub-generator")
                .put("output_text", mapper.writeValueAsString(payload));
        envelope.putObject("usage").put("input_tokens", 100).put("output_tokens", 100);
        byte[] bytes = mapper.writeValueAsBytes(envelope);
        return (uri, method) -> {
            calls.incrementAndGet();
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            requests.add(request);
            request.setResponse(new MockClientHttpResponse(bytes, HttpStatus.OK));
            return request;
        };
    }
}
