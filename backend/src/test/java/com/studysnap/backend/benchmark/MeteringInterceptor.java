package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

/** Meters the actual HTTP attempts, including attempts made by service retry logic. */
final class MeteringInterceptor implements ClientHttpRequestInterceptor {
    static final class SpendStop extends RuntimeException {
        SpendStop(String message) { super(message); }
    }
    record Call(String generationId, String leg, int attempt, long latencyMs, int status,
                JsonNode usage, int outputItems, List<String> outputTypes, int outputTextItems, boolean topLevelOutputText,
                long inputTokens, long outputTokens, long reasoningTokens,
                BigDecimal conservativeUsd, BigDecimal nonAdditiveUsd, String errorMessage) {}

    private final ObjectMapper mapper;
    private final Path rawDir;
    private final String model;
    private final BigDecimal inPrice;
    private final BigDecimal outPrice;
    private final BigDecimal spendStop;
    private final int maxCalls;
    private final List<Call> calls = new ArrayList<>();
    private BigDecimal total = BigDecimal.ZERO;
    private String generationId;
    private String leg;
    private int attempt;
    private boolean stopped;
    private boolean unmetered;

    MeteringInterceptor(ObjectMapper mapper, Path outDir, String model, BigDecimal inPrice,
                        BigDecimal outPrice, BigDecimal spendStop, int maxCalls) throws IOException {
        this.mapper = mapper;
        this.rawDir = outDir.resolve("raw");
        Files.createDirectories(rawDir);
        this.model = model;
        this.inPrice = inPrice;
        this.outPrice = outPrice;
        this.spendStop = spendStop;
        this.maxCalls = maxCalls;
    }

    void begin(String id, String leg) {
        this.generationId = id;
        this.leg = leg;
        this.attempt = 0;
    }

    List<Call> calls() { return List.copyOf(calls); }
    BigDecimal total() { return total; }
    boolean stopped() { return stopped; }
    boolean unmetered() { return unmetered; }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (unmetered) { stopped = true; throw new SpendStop("Successful response had no usable token counts"); }
        if (calls.size() >= maxCalls) { stopped = true; throw new SpendStop("maxCalls reached: " + maxCalls); }
        if (total.compareTo(spendStop) >= 0) { stopped = true; throw new SpendStop("spendStopUsd reached: " + spendStop); }
        int thisAttempt = ++attempt;
        long start = System.nanoTime();
        // The wrapper is never serialized or logged. Authorization remains only in the outgoing request.
        ClientHttpResponse response;
        byte[] bytes;
        try {
            response = execution.execute(request, body);
            bytes = StreamUtils.copyToByteArray(response.getBody());
        } catch (IOException ex) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            Call failed = new Call(generationId, leg, thisAttempt, elapsed, 0, null, 0, List.of(), 0, false,
                    0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, ex.getClass().getSimpleName());
            calls.add(failed);
            System.out.printf("model=%s leg=%s generation=%s attempt=%d latencyMs=%d costUsd=0 runningConservativeUsd=%s%n",
                    model, leg, generationId, thisAttempt, elapsed, total);
            throw ex;
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        JsonNode json;
        try { json = mapper.readTree(bytes); }
        catch (Exception ignored) { json = mapper.createObjectNode(); }
        JsonNode usage = json.path("usage");
        if (response.getStatusCode().is2xxSuccessful()
                && (!usage.path("input_tokens").isNumber() || !usage.path("output_tokens").isNumber()))
            unmetered = true;
        long input = usage.path("input_tokens").asLong(0);
        long output = usage.path("output_tokens").asLong(0);
        long reasoning = usage.path("output_tokens_details").path("reasoning_tokens").asLong(0);
        BigDecimal conservative = price(input, output + reasoning);
        BigDecimal nonAdditive = price(input, output);
        total = total.add(conservative);
        JsonNode items = json.path("output");
        List<String> types = new ArrayList<>();
        int texts = 0;
        if (items.isArray()) for (JsonNode item : items) {
            types.add(item.path("type").asText(""));
            for (JsonNode content : item.path("content"))
                if ("output_text".equals(content.path("type").asText())) texts++;
        }
        String error = json.path("error").path("message").asText("");
        String safeId = generationId.replaceAll("[^A-Za-z0-9_-]", "_");
        Files.write(rawDir.resolve(safeId + "-" + thisAttempt + ".json"), bytes);
        Call call = new Call(generationId, leg, thisAttempt, elapsed, response.getStatusCode().value(),
                usage.isMissingNode() ? null : usage.deepCopy(), items.isArray() ? items.size() : 0,
                List.copyOf(types), texts, json.path("output_text").isTextual(), input, output, reasoning, conservative, nonAdditive, error);
        calls.add(call);
        System.out.printf("model=%s leg=%s generation=%s attempt=%d latencyMs=%d costUsd=%s runningConservativeUsd=%s%n",
                model, leg, generationId, thisAttempt, elapsed, conservative, total);
        return new org.springframework.http.client.ClientHttpResponse() {
            @Override public org.springframework.http.HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
            @Override public String getStatusText() throws IOException { return response.getStatusText(); }
            @Override public void close() { response.close(); }
            @Override public java.io.InputStream getBody() { return new java.io.ByteArrayInputStream(bytes); }
            @Override public org.springframework.http.HttpHeaders getHeaders() { return response.getHeaders(); }
        };
    }

    private BigDecimal price(long input, long output) {
        return inPrice.multiply(BigDecimal.valueOf(input))
                .add(outPrice.multiply(BigDecimal.valueOf(output)))
                .divide(BigDecimal.valueOf(1_000_000), 9, RoundingMode.HALF_UP);
    }
}
