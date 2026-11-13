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
    static final class ArmMismatch extends RuntimeException {
        ArmMismatch(String message) { super(message); }
    }
    private record CaseContext(long token, String generationId, String cellId) {}
    private record Context(long token, String cellId, MeteringInterceptor meter) {}
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
    private final Queue<MeteringInterceptor.Call> calls = new ConcurrentLinkedQueue<>();
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
    List<MeteringInterceptor.Call> calls() { return List.copyOf(calls); }

    @Override public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        JsonNode json = mapper.readTree(body);
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
        try {
            ClientHttpResponse response = active.meter().intercept(request, body, execution);
            MeteringInterceptor.Call call = active.meter().calls().get(before);
            calls.add(call);
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
            if (recorded.size() > before) calls.add(recorded.get(before));
            if (ex instanceof IOException) noteHttpError();
            throw ex;
        } finally {
            if (!closed && !reservation.closed()) {
                try { reservation.abandon(); }
                catch (StageCapLedger.LedgerFailure ignored) { /* unresolved reservation remains charged on replay */ }
            }
        }
    }

    private Context contextForThread() throws IOException {
        CaseContext current = activeCase.get();
        if (current == null) throw new IllegalStateException("No case-call context");
        Context local = context.get();
        if (local == null || local.token() != current.token()) {
            local = newContext(current, current.generationId() + "-worker-" + Thread.currentThread().threadId());
            context.set(local);
        }
        return local;
    }

    private Context newContext(CaseContext current, String generationId) throws IOException {
        MeteringInterceptor meter = new MeteringInterceptor(mapper, outDir, model, priceIn, priceOut,
                ledger.cap(stage), Integer.MAX_VALUE, List.of(key));
        meter.begin(generationId, current.cellId());
        return new Context(current.token(), current.cellId(), meter);
    }

    private void noteHttpError() {
        if (consecutiveHttpErrors.incrementAndGet() >= 3)
            ledger.stopRunOnly("Three consecutive HTTP errors");
    }

    private static boolean usable(JsonNode usage) {
        return usage != null && nonnegative(usage.path("input_tokens"))
                && nonnegative(usage.path("output_tokens"))
                && (usage.path("output_tokens_details").path("reasoning_tokens").isMissingNode()
                    || nonnegative(usage.path("output_tokens_details").path("reasoning_tokens")));
    }
    private static boolean nonnegative(JsonNode token) {
        return token.isIntegralNumber() && token.canConvertToLong() && token.asLong() >= 0;
    }
}
