package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline mutation guards for preregistration sections 5 and 7. */
class StageRunnerTest {
    private static final String MODEL = "gpt-6-luna";
    private static final String KEY = "fake-authorization-secret-do-not-write";
    private static final String CELL = "critique-cell";
    private static final String FIRST = "first";
    private static final String SECOND = "second";
    private static final String ONE_CAP = "1";
    private static final String TWO_CAP = "2";
    private static final String THREE_TEXT = "3";
    private static final String ONE_POINT_FIVE = "1.5";
    private static final String NINE_TENTHS = "0.9";
    private static final String RESPONSES_PATH = "/responses";
    private static final String HTTP_ERROR = "HTTP_ERROR";
    private static final BigDecimal ONE = new BigDecimal(ONE_CAP);
    @TempDir Path temp;

    @Test void exactEqualReservationAllowedAndOneAboveStops() {
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1,
                new BigDecimal(TWO_CAP), StageCapLedger.Stage.STAGE_2, new BigDecimal("10")));
        var first = ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE);
        var second = ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE);
        assertThat(ledger.inFlight(StageCapLedger.Stage.STAGE_1)).isEqualByComparingTo(TWO_CAP);
        assertThatThrownBy(() -> ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        first.settle(ONE, ONE); second.settle(ONE, ONE);
        assertThat(ledger.spent(StageCapLedger.Stage.STAGE_1)).isEqualByComparingTo(TWO_CAP);
        assertThat(ledger.inFlight(StageCapLedger.Stage.STAGE_1)).isZero();
    }

    @Test void cumulativeApprovedCapsIncludePriorOvershoot() {
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, ONE,
                StageCapLedger.Stage.STAGE_2, ONE));
        var first = ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE);
        first.settle(new BigDecimal(ONE_POINT_FIVE), ONE);
        BigDecimal nextForecast = new BigDecimal("0.6");
        assertThatThrownBy(() -> ledger.reserve(StageCapLedger.Stage.STAGE_2, CELL, nextForecast))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessageContaining("Sum of approved");
    }

    @Test void runnerCanCarryLedgerAcrossStageInvocations() throws Exception {
        Map<StageCapLedger.Stage, BigDecimal> caps = Map.of(StageCapLedger.Stage.STAGE_1, ONE,
                StageCapLedger.Stage.STAGE_2, ONE);
        StageCapLedger ledger = new StageCapLedger(caps);
        var first = ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE);
        first.settle(new BigDecimal(ONE_POINT_FIVE), ONE);
        var secondInput = new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_2,
                caps, Map.of(CELL, new ForecastReservation(600_000, 0, ONE, ONE, ONE)),
                ONE, ONE, null, temp);
        StubFactory factory = new StubFactory(200, response());
        var summary = StageRunner.run(secondInput, factory, List.of(call(FIRST, body(MODEL), true)), ledger);
        assertThat(factory.dispatches.get()).isZero();
        assertThat(summary.outcomes().getFirst().state()).isEqualTo(CaseArmOutcome.State.INCONCLUSIVE);
        assertThat(summary.ledger().spent(StageCapLedger.Stage.STAGE_1)).isEqualByComparingTo(ONE_POINT_FIVE);
    }

    @Test void settleReleasesOnceAndBooksBothFiguresAndOvershoot() {
        StageCapLedger ledger = ledger(THREE_TEXT);
        var reservation = ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE);
        reservation.settle(new BigDecimal("1.25"), new BigDecimal(NINE_TENTHS));
        assertThat(ledger.inFlight(StageCapLedger.Stage.STAGE_1)).isZero();
        assertThat(ledger.cellSpent(CELL)).isEqualByComparingTo("1.25");
        assertThat(ledger.billed(StageCapLedger.Stage.STAGE_1)).isEqualByComparingTo(NINE_TENTHS);
        assertThat(ledger.largestOvershoot()).isEqualByComparingTo("0.25");
        assertThatThrownBy(() -> reservation.settle(ONE, ONE)).isInstanceOf(IllegalStateException.class);
    }

    @Test void settleAfterStopStillBooksMoneyAlreadySpent() {
        StageCapLedger ledger = ledger(TWO_CAP);
        var reservation = ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE);
        ledger.stop("Stopped by another call");
        reservation.settle(ONE);
        assertThat(ledger.spent(StageCapLedger.Stage.STAGE_1)).isEqualByComparingTo(ONE_CAP);
        assertThat(ledger.billed(StageCapLedger.Stage.STAGE_1)).isEqualByComparingTo(ONE_CAP);
        assertThat(ledger.inFlight(StageCapLedger.Stage.STAGE_1)).isZero();
    }

    @Test void abandonReleasesAndStillCountsAttempt() {
        StageCapLedger ledger = ledger(ONE_CAP);
        var reservation = ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE);
        reservation.abandon();
        assertThat(ledger.inFlight(StageCapLedger.Stage.STAGE_1)).isZero();
        assertThat(ledger.attempts(StageCapLedger.Stage.STAGE_1)).isEqualTo(1);
        assertThatThrownBy(reservation::abandon).isInstanceOf(IllegalStateException.class);
    }

    @Test void unmeteredCannotBecomeZeroAndStopsFutureCalls() {
        StageCapLedger ledger = ledger(TWO_CAP);
        var reservation = ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE);
        reservation.settle(null, null);
        assertThat(ledger.stopped()).isTrue();
        assertThat(ledger.stopReason()).contains("UNMETERED");
        assertThatThrownBy(() -> ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
    }

    @Test void nonPositiveApprovedCapsAreRejected() {
        Map<StageCapLedger.Stage, BigDecimal> zero = Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ZERO);
        assertThatThrownBy(() -> new StageCapLedger(zero)).isInstanceOf(IllegalArgumentException.class);
        Map<StageCapLedger.Stage, BigDecimal> negative = Map.of(StageCapLedger.Stage.STAGE_1, ONE.negate());
        assertThatThrownBy(() -> new StageCapLedger(negative)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void simultaneousReservationsNeverOverbook() throws Exception {
        for (int repeat = 0; repeat < 30; repeat++) {
            StageCapLedger ledger = ledger(ONE_CAP);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(8);
            var pool = Executors.newFixedThreadPool(8);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) futures.add(pool.submit(() -> {
                start.await();
                try {
                    var reservation = ledger.reserve(StageCapLedger.Stage.STAGE_1, CELL, ONE);
                    finish.countDown();
                    finish.await(2, TimeUnit.SECONDS);
                    reservation.settle(ONE, ONE);
                    return true;
                } catch (StageCapLedger.SpendStop ex) {
                    finish.countDown();
                    return false;
                }
            }));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) if (future.get()) successes++;
            pool.shutdownNow();
            assertThat(successes).isEqualTo(1);
            assertThat(ledger.spent(StageCapLedger.Stage.STAGE_1)).isEqualByComparingTo(ONE_CAP);
        }
    }

    @Test void allFiveArmSlotsUseArmModel() {
        StudySnapProperties properties = ArmRouting.forModel(MODEL, KEY);
        var settings = properties.getSettings();
        assertThat(List.of(settings.getModelFree(), settings.getModelPremium(),
                settings.getModelInteractiveQuiz(), settings.getModelPaidInteractive(), settings.getModelCritique()))
                .containsExactly(MODEL, MODEL, MODEL, MODEL, MODEL);
    }

    @Test void defaultClientCarriesProductionTimeouts() {
        StudySnapProperties properties = ArmRouting.forModel(MODEL, KEY);
        var factory = StageRunner.productionFactory(properties);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(factory, "connectTimeout"))
                .isEqualTo((int) java.time.Duration.ofSeconds(properties.getLlm().getApi().getConnectTimeoutSeconds()).toMillis());
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(factory, "readTimeout"))
                .isEqualTo((int) java.time.Duration.ofSeconds(properties.getLlm().getApi().getReadTimeoutSeconds()).toMillis());
    }

    @Test void forecastUsesEveryCallerSuppliedValue() {
        ForecastReservation forecast = new ForecastReservation(3400, 3600,
                new BigDecimal("0.1"), new BigDecimal("0.5"), new BigDecimal(THREE_TEXT));
        assertThat(forecast.usd()).isEqualByComparingTo("0.005740000");
    }

    @Test void armMismatchAndPrefixMismatchNeverDispatchOrReserve() throws Exception {
        for (String bodyModel : List.of("gpt-4.1-mini", MODEL + "-x", "GPT-6-LUNA")) {
            StubFactory factory = new StubFactory(200, response());
            var summary = StageRunner.run(input(ONE_CAP), factory,
                    List.of(call(FIRST, body(bodyModel), true)));
            assertThat(factory.dispatches.get()).isZero();
            assertThat(summary.ledger().attempts(StageCapLedger.Stage.STAGE_1)).isZero();
            assertThat(summary.ledger().stopped()).isTrue();
        }
    }

    @Test void askCompanionShapeIsForwardedByteIdentically() throws Exception {
        String body = "{\"model\":\"" + MODEL + "\",\"input\":[{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":\"hello\"}]}],\"text\":{\"format\":{\"type\":\"json_schema\",\"strict\":true,\"schema\":{}}}}";
        StubFactory factory = new StubFactory(200, response());
        StageRunner.run(input(ONE_CAP), factory, List.of(call(FIRST, body, true)));
        assertThat(factory.forwarded).isEqualTo(body);
        assertThat(factory.forwarded).doesNotContain("max_output_tokens", "temperature", "reasoning");
    }

    @Test void recoveredParseIsFirstFailureAndFinalSuccess() {
        var attempts = List.of(attempt(FIRST, 1, true), attempt(FIRST, 2, false));
        CaseArmOutcome result = CaseArmOutcome.classify(CELL, attempts, true, false, null);
        assertThat(result.firstAttemptClean()).isFalse();
        assertThat(result.finalSuccess()).isTrue();
        assertThat(result.retried()).isTrue();
    }

    @Test void concurrentFirstBatchesAreSeparateGroups() {
        var attempts = List.of(attempt(FIRST, 1, false), attempt(SECOND, 1, false));
        CaseArmOutcome result = CaseArmOutcome.classify(CELL, attempts, true, false, null);
        assertThat(result.firstAttemptClean()).isTrue();
        assertThat(result.retried()).isFalse();
    }

    @Test void parallelHttpBatchesKeepDistinctGenerationGroups() throws Exception {
        StubFactory factory = new StubFactory(200, response());
        StageRunner.CaseCall parallel = new StageRunner.CaseCall() {
            @Override public String id() { return FIRST; }
            @Override public String cellId() { return CELL; }
            @Override public String generationId() { return FIRST; }
            @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                    ArmAssertingInterceptor path) throws Exception {
                var pool = Executors.newFixedThreadPool(2);
                CountDownLatch start = new CountDownLatch(1);
                try {
                    List<Future<?>> tasks = new ArrayList<>();
                    for (int index = 0; index < 2; index++) tasks.add(pool.submit(() -> {
                        start.await();
                        client.post().uri(RESPONSES_PATH).body(body(MODEL)).retrieve().body(String.class);
                        return null;
                    }));
                    start.countDown();
                    for (Future<?> task : tasks) task.get();
                } finally { pool.shutdownNow(); }
                return new StageRunner.CallResult(true, Set.of(), null);
            }
        };
        var summary = StageRunner.run(input(ONE_CAP), factory, List.of(parallel));
        var outcome = summary.outcomes().getFirst();
        assertThat(factory.dispatches.get()).isEqualTo(2);
        assertThat(outcome.attempts()).extracting(AttemptOutcome::generationId).doesNotHaveDuplicates();
        assertThat(outcome.retried()).isFalse();
        assertThat(outcome.firstAttemptClean()).isTrue();
    }

    @Test void stoppedCaseAndLaterCaseAreInconclusive() throws Exception {
        StubFactory factory = new StubFactory(200, response());
        var summary = StageRunner.run(input("0.000001"), factory,
                List.of(call(FIRST, body(MODEL), true), call(SECOND, body(MODEL), true)));
        assertThat(summary.outcomes()).extracting(CaseArmOutcome::state)
                .containsExactly(CaseArmOutcome.State.INCONCLUSIVE, CaseArmOutcome.State.INCONCLUSIVE);
        assertThat(factory.dispatches.get()).isZero();
        assertThat(Files.readString(temp.resolve("summary.json"))).contains("INCONCLUSIVE", "Stage cap exceeded");
    }

    @Test void stageWithoutCapRefusesBeforeFactory() {
        var input = new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_2,
                Map.of(StageCapLedger.Stage.STAGE_1, ONE), forecasts(), ONE, ONE, null, temp);
        StubFactory factory = new StubFactory(200, response());
        List<StageRunner.CaseCall> calls = List.of(call(FIRST, body(MODEL), true));
        assertThatThrownBy(() -> StageRunner.run(input, factory, calls))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessageContaining("STAGE_2");
        assertThat(factory.dispatches.get()).isZero();
    }

    @Test void missingKeyAndRepoPathRefuseBeforeCall() {
        var noKey = new StageRunner.RunInput(MODEL, " ", StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, ONE), forecasts(), ONE, ONE, null, temp);
        StubFactory noKeyFactory = new StubFactory(200, response());
        List<StageRunner.CaseCall> noCalls = List.of();
        assertThatThrownBy(() -> StageRunner.run(noKey, noKeyFactory, noCalls))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("LLM_API_KEY");
        Path repo = Path.of("").toAbsolutePath().normalize().getParent();
        var repoOutput = new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, ONE), forecasts(), ONE, ONE, null, repo.resolve("benchmark-private"));
        StubFactory repoFactory = new StubFactory(200, response());
        assertThatThrownBy(() -> StageRunner.run(repoOutput, repoFactory, noCalls))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("backend/target");
    }

    @Test void unmeteredResponseStopsAndSummaryNeverContainsKeyOrRequest() throws Exception {
        String secretBody = body(MODEL);
        StubFactory factory = new StubFactory(200, "{\"output_text\":\"" + KEY + "\",\"echo\":"
                + new ObjectMapper().writeValueAsString(secretBody) + "}");
        var summary = StageRunner.run(input(ONE_CAP), factory,
                List.of(call(FIRST, secretBody, true), call(SECOND, secretBody, true)));
        assertThat(summary.ledger().stopped()).isTrue();
        assertThat(summary.outcomes().get(1).state()).isEqualTo(CaseArmOutcome.State.INCONCLUSIVE);
        assertThat(factory.dispatches.get()).isEqualTo(1);
        try (var files = Files.walk(temp)) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                assertThat(Files.readString(file)).doesNotContain(KEY, secretBody);
        }
    }

    @Test void negativeAndNonnumericUsageStopAsUnmetered() throws Exception {
        for (String usage : List.of("{\"input_tokens\":-1,\"output_tokens\":100}",
                "{\"input_tokens\":\"unknown\",\"output_tokens\":100}",
                "{\"input_tokens\":100,\"output_tokens\":100,\"output_tokens_details\":{\"reasoning_tokens\":-1}}")) {
            StubFactory factory = new StubFactory(200, "{\"output_text\":\"ok\",\"usage\":" + usage + "}");
            var summary = StageRunner.run(input(ONE_CAP), factory, List.of(call(FIRST, body(MODEL), true)));
            assertThat(summary.ledger().stopped()).isTrue();
            assertThat(summary.outcomes().getFirst().attempts().getFirst().kind())
                    .isEqualTo(AttemptOutcome.Kind.UNMETERED);
            assertThat(summary.outcomes().getFirst().state()).isEqualTo(CaseArmOutcome.State.INCONCLUSIVE);
        }
    }

    @Test void timeoutAbandonsReservationAndThreeHttpErrorsStop() throws Exception {
        ClientHttpRequestFactory timeout = (uri, method) -> new MockClientHttpRequest(method, uri) {
            @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                throw new SocketTimeoutException("offline timeout");
            }
        };
        var timed = StageRunner.run(input(ONE_CAP), timeout, List.of(call(FIRST, body(MODEL), false)));
        assertThat(timed.ledger().attempts(StageCapLedger.Stage.STAGE_1)).isEqualTo(1);
        assertThat(timed.ledger().inFlight(StageCapLedger.Stage.STAGE_1)).isZero();
        assertThat(timed.outcomes().getFirst().attempts().getFirst().kind()).isEqualTo(AttemptOutcome.Kind.TIMEOUT);
        StubFactory errors = new StubFactory(400, "{\"error\":{\"message\":\"bad\"}}");
        var stopped = StageRunner.run(input(ONE_CAP), errors, List.of(call(FIRST, body(MODEL), false),
                call(SECOND, body(MODEL), false), call("third", body(MODEL), false), call("fourth", body(MODEL), false)));
        assertThat(errors.dispatches.get()).isEqualTo(3);
        assertThat(stopped.outcomes().get(3).state()).isEqualTo(CaseArmOutcome.State.INCONCLUSIVE);
    }

    @Test void fourthHttpAttemptInsideOneCaseIsBlocked() throws Exception {
        StubFactory errors = new StubFactory(400, "{\"error\":{\"message\":\"bad\"}}");
        StageRunner.CaseCall loop = new StageRunner.CaseCall() {
            @Override public String id() { return FIRST; }
            @Override public String cellId() { return CELL; }
            @Override public String generationId() { return FIRST; }
            @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                    ArmAssertingInterceptor path) {
                for (int attempt = 0; attempt < 4; attempt++) {
                    try { client.post().uri(RESPONSES_PATH).body(body(MODEL)).retrieve().body(String.class); }
                    catch (org.springframework.web.client.RestClientResponseException ignored) { /* next production attempt */ }
                }
                return new StageRunner.CallResult(false, Set.of(), HTTP_ERROR);
            }
        };
        var summary = StageRunner.run(input(ONE_CAP), errors, List.of(loop));
        assertThat(errors.dispatches.get()).isEqualTo(3);
        assertThat(summary.ledger().stopReason()).contains("Three consecutive HTTP errors");
    }

    private StageCapLedger ledger(String cap) {
        return new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, new BigDecimal(cap)));
    }
    private Map<String, ForecastReservation> forecasts() {
        return Map.of(CELL, new ForecastReservation(100, 100, ONE, ONE, ONE));
    }
    private StageRunner.RunInput input(String cap) {
        return new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, new BigDecimal(cap)), forecasts(), ONE, ONE, null, temp);
    }
    private String body(String model) {
        return "{\"model\":\"" + model + "\",\"input\":[],\"text\":{\"format\":{\"type\":\"json_schema\",\"strict\":true}}}";
    }
    private String response() {
        return "{\"output_text\":\"ok\",\"usage\":{\"input_tokens\":100,\"output_tokens\":100,"+
                "\"output_tokens_details\":{\"reasoning_tokens\":30}}}";
    }
    private AttemptOutcome attempt(String generationId, int number, boolean parseFailure) {
        return new AttemptOutcome(generationId, number, AttemptOutcome.Kind.OK, parseFailure, ONE, ONE);
    }
    private StageRunner.CaseCall call(String id, String body, boolean delivered) {
        return new StageRunner.CaseCall() {
            @Override public String id() { return id; }
            @Override public String cellId() { return CELL; }
            @Override public String generationId() { return id; }
            @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                    ArmAssertingInterceptor path) {
                client.post().uri(RESPONSES_PATH).body(body).retrieve().body(String.class);
                return new StageRunner.CallResult(delivered, Set.of(), delivered ? null : HTTP_ERROR);
            }
        };
    }
    private static final class StubFactory implements ClientHttpRequestFactory {
        private final int status;
        private final String response;
        private final AtomicInteger dispatches = new AtomicInteger();
        private String forwarded;
        private StubFactory(int status, String response) { this.status = status; this.response = response; }
        @Override public MockClientHttpRequest createRequest(java.net.URI uri, HttpMethod method) {
            return new MockClientHttpRequest(method, uri) {
                @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                    dispatches.incrementAndGet();
                    forwarded = new String(getBodyAsBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    return new MockClientHttpResponse(response.getBytes(java.nio.charset.StandardCharsets.UTF_8), status);
                }
            };
        }
    }
}
