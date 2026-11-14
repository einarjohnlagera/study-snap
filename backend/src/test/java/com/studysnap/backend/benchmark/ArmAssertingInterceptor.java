package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Section 5 body-level arm guard and reservation path for every workflow. */
final class ArmAssertingInterceptor implements ClientHttpRequestInterceptor {
    private static final ThreadLocal<byte[]> IN_FLIGHT_REQUEST_BODY = new ThreadLocal<>();
    private static final String OUTPUT_TOKENS_DETAILS = "output_tokens_details";
    private static final String REASONING_TOKENS = "reasoning_tokens";
    static final class ArmMismatch extends RuntimeException {
        ArmMismatch(String message) { super(message); }
    }
    private record CaseContext(long token, String generationId, String cellId) {}
    private record Context(long token, String cellId, String generationId, MeteringInterceptor meter) {}
    record CallReceipt(MeteringInterceptor.Call call, int questionCount, String bodyDigest,
                       String modelRemovedDigest) {
        CallReceipt(MeteringInterceptor.Call call, int questionCount, String bodyDigest) {
            this(call, questionCount, bodyDigest, bodyDigest);
        }
    }
    private final ObjectMapper mapper;
    private final String model;
    private final String key;
    private final Path outDir;
    private final StageCapLedger ledger;
    private final StageCapLedger.Stage stage;
    private final BigDecimal priceIn;
    private final BigDecimal priceOut;
    private final java.util.Map<String, ForecastReservation> forecasts;
    private final ThreadLocal<Context> context = new ThreadLocal<>();
    private final AtomicReference<CaseContext> activeCase = new AtomicReference<>();
    private final AtomicLong caseSequence = new AtomicLong();
    private final Queue<String> bodyDigests = new ConcurrentLinkedQueue<>();
    private final Queue<Integer> requestQuestionCounts = new ConcurrentLinkedQueue<>();
    private final Queue<CallReceipt> receipts = new ConcurrentLinkedQueue<>();
    private final AtomicInteger consecutiveHttpErrors = new AtomicInteger();

    ArmAssertingInterceptor(ObjectMapper mapper, String model, String key, Path outDir,
            StageCapLedger ledger, StageCapLedger.Stage stage, BigDecimal priceIn, BigDecimal priceOut,
            java.util.Map<String, ForecastReservation> forecasts) {
        this.mapper = mapper; this.model = model; this.key = key; this.outDir = outDir;
        this.ledger = ledger; this.stage = stage; this.priceIn = priceIn; this.priceOut = priceOut;
        this.forecasts = java.util.Map.copyOf(forecasts);
    }

    void begin(String generationId, String cellId) throws IOException {
        if (generationId.contains(key) || cellId.contains(key))
            throw new IllegalArgumentException("Case identifiers must not contain the API key");
        CaseContext current = new CaseContext(caseSequence.incrementAndGet(), generationId, cellId);
        activeCase.set(current);
        context.set(newContext(current, generationId));
    }
    void end() { activeCase.set(null); context.remove(); }
    void stopRun(String reason) { ledger.stopRunOnly(reason); }
    List<MeteringInterceptor.Call> calls() { return receipts().stream().map(CallReceipt::call).toList(); }
    List<String> bodyDigests() { return List.copyOf(bodyDigests); }
    List<Integer> requestQuestionCounts() { return List.copyOf(requestQuestionCounts); }
    List<CallReceipt> receipts() { return List.copyOf(receipts); }
    static byte[] inFlightRequestBody() { return IN_FLIGHT_REQUEST_BODY.get(); }

    @Override public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        String bodyDigest = digest(body);
        bodyDigests.add(bodyDigest);
        JsonNode json = mapper.readTree(body);
        String modelRemovedDigest = modelRemovedDigest(json);
        int questionCount = json.path("text").path("format").path("schema")
                .path("properties").path("questions").path("minItems").asInt(0);
        requestQuestionCounts.add(questionCount);
        if (!model.equals(json.path("model").asText(null))) {
            ledger.stopRunOnly("Arm model mismatch");
            throw new ArmMismatch("Outgoing model differs from arm model");
        }
        Context active = contextForThread();
        ForecastReservation forecast = forecasts.get(active.cellId());
        if (forecast == null) throw new IllegalArgumentException("No forecast for cell " + active.cellId());
        StageCapLedger.Reservation reservation = ledger.reserve(stage, active.cellId(), forecast.usd());
        String requestBody = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        active.meter().redactRaw(requestBody);
        String escapedBody = mapper.writeValueAsString(requestBody);
        active.meter().redactRaw(escapedBody.substring(1, escapedBody.length() - 1));
        boolean closed = false;
        int before = active.meter().calls().size();
        IN_FLIGHT_REQUEST_BODY.set(body.clone());
        try {
            ClientHttpResponse response = active.meter().intercept(request, body, execution);
            MeteringInterceptor.Call call = active.meter().calls().get(before);
            receipts.add(new CallReceipt(call, questionCount, bodyDigest, modelRemovedDigest));
            if (response.getStatusCode().is2xxSuccessful() && !usable(call.usage())) {
                reservation.settle(null, null);
            } else {
                reservation.settle(call.conservativeUsd(), call.nonAdditiveUsd());
            }
            closed = true;
            if (call.status() >= 400) noteHttpError();
            else consecutiveHttpErrors.set(0);
            return response;
        } catch (IOException | RuntimeException ex) {
            List<MeteringInterceptor.Call> recorded = active.meter().calls();
            if (recorded.size() > before) {
                MeteringInterceptor.Call call = recorded.get(before);
                receipts.add(new CallReceipt(call, questionCount, bodyDigest, modelRemovedDigest));
            }
            if (ex instanceof IOException) noteHttpError();
            throw ex;
        } finally {
            IN_FLIGHT_REQUEST_BODY.remove();
            if (!closed && !reservation.closed()) {
                try { reservation.abandon(); }
                catch (StageCapLedger.LedgerFailure ignored) { /* unresolved reservation remains charged on replay */ }
            }
        }
    }

    static String digest(byte[] body) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(body);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String modelRemovedDigest(JsonNode body) throws IOException {
        com.fasterxml.jackson.databind.node.ObjectNode copy = body.deepCopy();
        copy.remove("model");
        return digest(new ObjectMapper().writeValueAsBytes(copy));
    }

    private Context contextForThread() throws IOException {
        CaseContext current = activeCase.get();
        if (current == null) throw new IllegalStateException("No case-call context");
        Context local = context.get();
        String batch = BatchTaggingExecutor.currentBatch();
        String generationId = batch == null
                ? current.generationId() + "-worker-" + Thread.currentThread().threadId() : batch;
        if (local == null || local.token() != current.token()
                || batch != null && !local.generationId().equals(batch)
                || batch == null && local.generationId().startsWith(current.generationId() + "-batch-")) {
            local = newContext(current, generationId);
            context.set(local);
        }
        return local;
    }

    private Context newContext(CaseContext current, String generationId) throws IOException {
        MeteringInterceptor meter = new MeteringInterceptor(mapper, outDir, model, priceIn, priceOut,
                ledger.cap(stage), Integer.MAX_VALUE, List.of(key));
        meter.begin(generationId, current.cellId());
        return new Context(current.token(), current.cellId(), generationId, meter);
    }

    private void noteHttpError() {
        if (consecutiveHttpErrors.incrementAndGet() >= 3)
            ledger.stopRunOnly("Three consecutive HTTP errors");
    }

    private static boolean usable(JsonNode usage) {
        return usage != null && nonnegative(usage.path("input_tokens"))
                && nonnegative(usage.path("output_tokens"))
                && (usage.path(OUTPUT_TOKENS_DETAILS).path(REASONING_TOKENS).isMissingNode()
                    || nonnegative(usage.path(OUTPUT_TOKENS_DETAILS).path(REASONING_TOKENS)));
    }
    private static boolean nonnegative(JsonNode token) {
        return token.isIntegralNumber() && token.canConvertToLong() && token.asLong() >= 0;
    }
}
