package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.studysnap.backend.config.StudySnapProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline, one-variable controls for the provider-bound dispatch gate. */
class ProviderBoundCostGateTest {
    private static final String MODEL = "gpt-6-luna";
    private static final String API_KEY = "fake-offline-api-key";
    private static final String CELL = "synthetic-cell";
    private static final String ARM_NAME = "LUNA";
    private static final String TEXT_FIELD = "\"text\":{\"format\":{\"type\":\"json_schema\",\"name\":\"test_schema\",\"schema\":{},\"strict\":true}}";
    private static final String BODY = "{\"model\":\"gpt-6-luna\",\"input\":[{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":\"x\"}]}],"
            + TEXT_FIELD + "}";
    private static final String USAGE = "{\"usage\":{\"input_tokens\":2,\"output_tokens\":3}}";
    private static final String PARTIAL = "{\"usage\":{\"input_tokens\":2}}";
    private static final String NO_USAGE = "{}";
    private static final String ONE = "1";
    private static final String TWO = "2";
    private static final String OFFLINE_URL = "https://offline.invalid";
    private static final String UNKNOWN_MODEL = "unknown-model";
    private static final String TWO_PRICE = "2.00";
    private static final String UNKNOWN_MODEL_REASON = "UNKNOWN_PROVIDER_BOUND_MODEL";
    private static final String ROUNDING_PRICE = "0.0001";
    private static final String ONE_NANODOLLAR = "0.000000001";
    private static final String ACTUAL_COST = "0.000008000";
    private static final String UNMETERED_REASON = "UNMETERED response: ";
    private static final String INPUT_TEXT = "input_text";
    private static final String BOUND_EXCEEDED = "PROVIDER_BOUND_EXCEEDED";
    private static final String PRICE_MISMATCH = "PROVIDER_BOUND_PRICE_MISMATCH";
    private static final String UNPRICEABLE = "UNPRICEABLE_REQUEST";
    private static final String SYNTHETIC = "synthetic";
    private static final String TABLE_REQUIRED = "PROVIDER_BOUND_TABLE_REQUIRED";
    private static final String RECHECK_EXHAUSTED = "Recheck reservation exhausted";
    private static final StageCapLedger.Stage STAGE = StageCapLedger.Stage.STAGE_1;
    private static final LocalDate READ_DATE = LocalDate.of(2026, 11, 19);
    @TempDir Path temp;

    private static BigDecimal amount(String value) { return new BigDecimal(value); }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static ProviderBoundReservation bounds(long threshold, long margin) {
        return new ProviderBoundReservation(Map.of(MODEL,
                new ProviderBoundReservation.ModelPrice(10, amount(ONE), amount(TWO))),
                threshold, margin, READ_DATE);
    }
    private static ProviderBoundReservation bounds() { return bounds(1000, 10); }
    private static BigDecimal strict(ProviderBoundReservation table) { return table.usd(MODEL, bytes(BODY)); }
    private static StageCapLedger ledger(BigDecimal cap) { return new StageCapLedger(Map.of(STAGE, cap)); }
    private static StageCapLedger ledgerWithHeadroom(BigDecimal cap) {
        return new StageCapLedger(Map.of(STAGE, cap, StageCapLedger.Stage.STAGE_2, amount(ONE)));
    }
    private ArmAssertingInterceptor interceptor(StageCapLedger ledger, ProviderBoundReservation table) {
        return interceptor(ledger, table, null);
    }
    private ArmAssertingInterceptor interceptor(StageCapLedger ledger, ProviderBoundReservation table,
                                                StageCapLedger.Reservation budget) {
        return new ArmAssertingInterceptor(new ObjectMapper(), MODEL, API_KEY, temp, ledger, STAGE,
                amount(ONE), amount(TWO), Map.of(CELL,
                new ForecastReservation(1, 1, amount(ONE), amount(TWO), amount(ONE))), table, budget);
    }
    private static MockClientHttpResponse response(HttpStatus status, String body) {
        return new MockClientHttpResponse(bytes(body), status);
    }
    private static final class Fixture {
        final StageCapLedger ledger;
        final ArmAssertingInterceptor path;
        final List<byte[]> forwarded = new ArrayList<>();
        Fixture(StageCapLedger ledger, ArmAssertingInterceptor path) {
            this.ledger = ledger; this.path = path;
        }
        ClientHttpResponse send(String body, ClientHttpRequestExecution transport) throws IOException {
            path.begin("synthetic-generation", CELL);
            try {
                return path.intercept(new MockClientHttpRequest(HttpMethod.POST, URI.create(OFFLINE_URL)),
                        bytes(body), (request, outgoing) -> {
                            forwarded.add(outgoing);
                            return transport.execute(request, outgoing);
                        });
            } finally { path.end(); }
        }
        ClientHttpResponse send(String body, HttpStatus status, String result) throws IOException {
            return send(body, (request, outgoing) -> response(status, result));
        }
    }
    private Fixture fixture(BigDecimal cap, ProviderBoundReservation table) {
        StageCapLedger value = ledger(cap);
        return new Fixture(value, interceptor(value, table));
    }
    private Fixture fixtureWithHeadroom(BigDecimal cap, ProviderBoundReservation table) {
        StageCapLedger value = ledgerWithHeadroom(cap);
        return new Fixture(value, interceptor(value, table));
    }
    private static ClientHttpResponse precheck(ArmAssertingInterceptor path,
                                               MockClientHttpRequest request) throws IOException {
        return path.intercept(request, bytes(BODY),
                (outgoing, body) -> response(HttpStatus.OK, USAGE));
    }
    private ArmAssertingInterceptor mismatched(StageCapLedger value, ProviderBoundReservation table) {
        return new ArmAssertingInterceptor(new ObjectMapper(), MODEL, API_KEY, temp,
                value, STAGE, amount(TWO), amount(TWO), Map.of(), table, null);
    }
    private ArmAssertingInterceptor mismatchedOutput(StageCapLedger value, ProviderBoundReservation table) {
        return new ArmAssertingInterceptor(new ObjectMapper(), MODEL, API_KEY, temp,
                value, STAGE, amount(ONE), amount(ONE), Map.of(), table, null);
    }
    private ArmAssertingInterceptor unknownModel(StageCapLedger value, ProviderBoundReservation table) {
        return new ArmAssertingInterceptor(new ObjectMapper(), UNKNOWN_MODEL, API_KEY, temp,
                value, STAGE, amount(ONE), amount(TWO), Map.of(), table, null);
    }

    @Test void documentedFourModelFormulaUsesBytesMaximumOutputAndBothPrices() {
        var rows = Map.of(
                "gpt-4.1-mini", new ProviderBoundReservation.ModelPrice(32_768, amount("0.40"), amount("1.60")),
                "gpt-4.1", new ProviderBoundReservation.ModelPrice(32_768, amount(TWO_PRICE), amount("8.00")),
                MODEL, new ProviderBoundReservation.ModelPrice(128_000, amount("0.10"), amount("0.50")),
                "gpt-6.1-sol", new ProviderBoundReservation.ModelPrice(128_000, amount(TWO_PRICE), amount("10.00")));
        var table = new ProviderBoundReservation(rows, 272_000, 100, READ_DATE);
        byte[] body = bytes(BODY);
        rows.forEach((model, row) -> {
            BigDecimal expected = BigDecimal.valueOf(body.length).multiply(row.inputPerMillion())
                    .add(BigDecimal.valueOf(row.maximumOutputTokens()).multiply(row.outputPerMillion()))
                    .divide(BigDecimal.valueOf(1_000_000), 9, java.math.RoundingMode.UP);
            assertThat(table.usd(model, body)).isEqualByComparingTo(expected);
        });
        assertThatThrownBy(() -> table.usd(UNKNOWN_MODEL, body))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(UNKNOWN_MODEL_REASON);
    }

    @Test void exactThresholdPricesAndOneExtraByteRefuses() {
        var table = bounds(500, 0);
        byte[] exact = new byte[500];
        byte[] extra = new byte[501];
        assertThat(table.usd(MODEL, exact)).isEqualByComparingTo("0.000520000");
        assertThatThrownBy(() -> table.usd(MODEL, extra))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage("PROVIDER_BOUND_INPUT_TOO_LARGE");
    }

    @Test void roundingUsesUpAtNinthDecimal() {
        var table = new ProviderBoundReservation(Map.of(MODEL,
                new ProviderBoundReservation.ModelPrice(1, amount(ROUNDING_PRICE), amount(ROUNDING_PRICE))),
                1000, 0, READ_DATE);
        assertThat(table.usd(MODEL, new byte[1])).isEqualByComparingTo(ONE_NANODOLLAR);
    }

    @Test void exactFitDispatchesAndAbsurdlyLowForecastIsIgnored() throws Exception {
        var table = bounds();
        BigDecimal exact = strict(table);
        Fixture fixture = fixture(exact, table);
        fixture.send(BODY, (request, outgoing) -> {
            assertThat(fixture.ledger.inFlight(STAGE)).isEqualByComparingTo(exact);
            return response(HttpStatus.OK, USAGE);
        });
        assertThat(fixture.forwarded).hasSize(1);
        assertThat(fixture.ledger.spent(STAGE)).isEqualByComparingTo(ACTUAL_COST);
        assertThat(fixture.ledger.inFlight(STAGE)).isZero();
    }

    @Test void capRefusalStopsThisRunAndNextCallBeforeDispatch() {
        var table = bounds();
        Fixture fixture = fixtureWithHeadroom(strict(table).subtract(amount(ONE_NANODOLLAR)), table);
        assertThatThrownBy(() -> fixture.send(BODY, HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(fixture.forwarded).isEmpty();
        assertThat(fixture.ledger.stopped()).isTrue();
        assertThatThrownBy(() -> fixture.send(BODY, HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(fixture.forwarded).isEmpty();
    }

    @Test void inFlightReservationCountsAgainstSecondConcurrentAttempt() {
        var table = bounds();
        BigDecimal exact = strict(table);
        Fixture fixture = fixtureWithHeadroom(exact, table);
        StageCapLedger.Reservation first = fixture.ledger.reserve(STAGE, CELL, exact);
        assertThatThrownBy(() -> fixture.send(BODY, HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(fixture.forwarded).isEmpty();
        assertThat(fixture.ledger.inFlight(STAGE)).isEqualByComparingTo(exact);
        first.abandon();
    }

    @Test void twoConcurrentReservationsBothCountAgainstTheCap() {
        var table = bounds();
        BigDecimal exact = strict(table);
        Fixture fixture = fixtureWithHeadroom(exact.multiply(amount(TWO)), table);
        StageCapLedger.Reservation first = fixture.ledger.reserve(STAGE, CELL, exact);
        StageCapLedger.Reservation second = fixture.ledger.reserve(STAGE, CELL, exact);
        assertThat(fixture.ledger.inFlight(STAGE)).isEqualByComparingTo(exact.multiply(amount(TWO)));
        assertThatThrownBy(() -> fixture.send(BODY, HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(fixture.forwarded).isEmpty();
        first.abandon();
        second.abandon();
    }

    @Test void reserveFromTransfersOnlyWhenParentCoversStrictWorstCall() throws Exception {
        var table = bounds();
        BigDecimal exact = strict(table);
        StageCapLedger admitted = ledger(exact);
        StageCapLedger.Reservation enough = admitted.reserve(STAGE, CELL, exact);
        Fixture child = new Fixture(admitted, interceptor(admitted, table, enough));
        child.send(BODY, HttpStatus.OK, USAGE);
        assertThat(child.forwarded).hasSize(1);
        assertThat(admitted.inFlight(STAGE)).isZero();
        assertThat(admitted.spent(STAGE)).isEqualByComparingTo(ACTUAL_COST);
        enough.abandon();

        StageCapLedger denied = ledger(exact);
        StageCapLedger.Reservation shortParent = denied.reserve(STAGE, CELL,
                exact.subtract(amount(ONE_NANODOLLAR)));
        Fixture refused = new Fixture(denied, interceptor(denied, table, shortParent));
        assertThatThrownBy(() -> refused.send(BODY, HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessageContaining(RECHECK_EXHAUSTED);
        assertThat(refused.forwarded).isEmpty();
        assertThat(denied.stopped()).isTrue();
        shortParent.abandon();
    }

    private void assertFullCharge(ClientHttpRequestExecution transport, boolean throwsIOException,
                                  boolean unmetered) throws Exception {
        var table = bounds();
        BigDecimal exact = strict(table);
        Fixture fixture = fixture(exact, table);
        if (throwsIOException) assertThatThrownBy(() -> fixture.send(BODY, transport)).isInstanceOf(IOException.class);
        else fixture.send(BODY, transport);
        assertThat(fixture.forwarded).hasSize(1);
        assertThat(fixture.ledger.spent(STAGE)).isEqualByComparingTo(exact);
        assertThat(fixture.ledger.inFlight(STAGE)).isZero();
        if (unmetered) assertThat(fixture.ledger.stopReason()).isEqualTo(UNMETERED_REASON + STAGE);
    }

    @Test void transportFailureChargesFullStrict() throws Exception {
        assertFullCharge((request, body) -> { throw new IOException("synthetic transport failure"); }, true, false);
    }

    @Test void timeoutChargesFullStrict() throws Exception {
        assertFullCharge((request, body) -> { throw new SocketTimeoutException("synthetic timeout"); }, true, false);
    }

    @Test void bodyReadResetChargesFullStrict() throws Exception {
        assertFullCharge((request, body) -> new MockClientHttpResponse(bytes(USAGE), HttpStatus.OK) {
            @Override public InputStream getBody() { return new InputStream() {
                @Override public int read() throws IOException { throw new IOException("synthetic reset"); }
            }; }
        }, true, false);
    }

    @Test void successfulUnmeteredChargesFullStrict() throws Exception {
        assertFullCharge((request, body) -> response(HttpStatus.OK, NO_USAGE), false, true);
    }

    @Test void non2xxNoUsageChargesFullStrict() throws Exception {
        assertFullCharge((request, body) -> response(HttpStatus.BAD_REQUEST, NO_USAGE), false, false);
    }

    @Test void non2xxPartialUsageChargesFullStrict() throws Exception {
        assertFullCharge((request, body) -> response(HttpStatus.BAD_REQUEST, PARTIAL), false, false);
    }

    @Test void unmeteredStopPersistsAcrossReplay() throws Exception {
        Path file = temp.resolve("unmetered-ledger.jsonl");
        var table = bounds();
        BigDecimal exact = strict(table);
        try (StageCapLedger first = StageCapLedger.open(file, Map.of(STAGE, exact), true)) {
            new Fixture(first, interceptor(first, table)).send(BODY, HttpStatus.OK, NO_USAGE);
        }
        try (StageCapLedger reopened = StageCapLedger.open(file, Map.of(STAGE, exact), false)) {
            assertThat(reopened.spent(STAGE)).isEqualByComparingTo(exact);
            assertThat(reopened.stopped()).isTrue();
            assertThat(reopened.stopReason()).isEqualTo(UNMETERED_REASON + STAGE);
        }
    }

    @Test void meteringPrecheckBeforeDispatchAbandonsWithoutSecondCharge() throws Exception {
        var table = bounds();
        BigDecimal exact = strict(table);
        StageCapLedger value = ledger(exact.multiply(amount(TWO)));
        ArmAssertingInterceptor path = interceptor(value, table);
        var request = new MockClientHttpRequest(HttpMethod.POST, URI.create(OFFLINE_URL));
        path.begin("same-context", CELL);
        try {
            path.intercept(request, bytes(BODY), (outgoing, body) -> response(HttpStatus.OK, NO_USAGE));
            value.resume();
            assertThatThrownBy(() -> precheck(path, request))
                    .isInstanceOf(MeteringInterceptor.SpendStop.class);
        } finally { path.end(); }
        assertThat(value.spent(STAGE)).isEqualByComparingTo(exact);
        assertThat(value.inFlight(STAGE)).isZero();
        assertThat(value.attempts(STAGE)).isEqualTo(2);
    }

    @Test void forbiddenFieldsAndContentTypesRefuseBeforeReservationAndDispatch() {
        var table = bounds();
        String[] forbidden = {
                BODY.replace(TEXT_FIELD, TEXT_FIELD + ",\"max_output_tokens\":1"),
                BODY.replace(TEXT_FIELD, TEXT_FIELD + ",\"service_tier\":\"fast\""),
                BODY.replace(TEXT_FIELD, TEXT_FIELD + ",\"tools\":[]"),
                BODY.replace(TEXT_FIELD, TEXT_FIELD + ",\"previous_response_id\":\"x\""),
                BODY.replace(TEXT_FIELD, TEXT_FIELD + ",\"unknown\":true"),
                BODY.replace(INPUT_TEXT, "input_image"),
                BODY.replace(INPUT_TEXT, "input_file")};
        for (String body : forbidden) {
            Fixture fixture = fixture(strict(table), table);
            assertThatThrownBy(() -> fixture.send(body, HttpStatus.OK, USAGE))
                    .isInstanceOf(StageCapLedger.SpendStop.class).hasMessageStartingWith("FORBIDDEN_REQUEST");
            assertThat(fixture.forwarded).isEmpty();
            assertThat(fixture.ledger.attempts(STAGE)).isZero();
            assertThat(fixture.ledger.stopped()).isTrue();
        }
    }

    @Test void marginRefusesBeforeReserveWhileProductionShapePasses() throws Exception {
        var table = bounds(bytes(BODY).length + 1, 1);
        Fixture good = fixture(strict(table), table);
        good.send(BODY, HttpStatus.OK, USAGE);
        assertThat(good.forwarded).hasSize(1);
        var narrow = bounds(bytes(BODY).length, 1);
        Fixture refused = fixture(strict(narrow), narrow);
        assertThatThrownBy(() -> refused.send(BODY, HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage("PROVIDER_BOUND_INPUT_MARGIN_EXCEEDED");
        assertThat(refused.ledger.attempts(STAGE)).isZero();
        assertThat(refused.forwarded).isEmpty();
    }

    @Test void unpriceableAndLedgerFailureStopBeforeDispatch() {
        var table = bounds();
        Fixture badBody = fixture(strict(table), table);
        assertThatThrownBy(() -> badBody.send("{", HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(UNPRICEABLE);
        assertThat(badBody.ledger.stopReason()).isEqualTo(UNPRICEABLE);
        assertThat(badBody.forwarded).isEmpty();
        assertThat(badBody.ledger.attempts(STAGE)).isZero();

        Fixture badModel = fixture(strict(table), table);
        String numericModel = BODY.replace("\"model\":\"gpt-6-luna\"", "\"model\":7");
        assertThatThrownBy(() -> badModel.send(numericModel, HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(UNPRICEABLE);
        assertThat(badModel.forwarded).isEmpty();
        assertThat(badModel.ledger.attempts(STAGE)).isZero();

        StageCapLedger closed = ledger(strict(table));
        Fixture unavailable = new Fixture(closed, interceptor(closed, table));
        closed.close();
        assertThatThrownBy(() -> unavailable.send(BODY, HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.LedgerFailure.class);
        assertThat(closed.stopReason()).isEqualTo("LEDGER_FAILURE");
        assertThat(unavailable.forwarded).isEmpty();
    }

    @Test void priceMismatchRefusesConstructionAndEqualPricePasses() {
        StageCapLedger value = ledger(amount(ONE));
        var table = bounds();
        assertThat(interceptor(value, table)).isNotNull();
        assertThatThrownBy(() -> mismatched(value, table))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(PRICE_MISMATCH);
        StageCapLedger outputMismatch = ledger(amount(ONE));
        assertThatThrownBy(() -> mismatchedOutput(outputMismatch, table))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(PRICE_MISMATCH);
        assertThat(outputMismatch.stopped()).isTrue();
        StageCapLedger unknownLedger = ledger(amount(ONE));
        assertThatThrownBy(() -> unknownModel(unknownLedger, table))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(UNKNOWN_MODEL_REASON);
        assertThat(unknownLedger.stopped()).isTrue();
    }

    @Test void outputAndNonAdditiveOverrunsPersistButReasoningOnlyDoesNot() throws Exception {
        var table = bounds();
        BigDecimal exact = strict(table);
        String[] overrun = {
                "{\"usage\":{\"input_tokens\":1,\"output_tokens\":11}}",
                "{\"usage\":{\"input_tokens\":1000,\"output_tokens\":1}}"};
        for (int index = 0; index < overrun.length; index++) {
            Path file = temp.resolve("overrun-" + index + ".jsonl");
            try (StageCapLedger first = StageCapLedger.open(file, Map.of(STAGE, amount(ONE)), true)) {
                new Fixture(first, interceptor(first, table)).send(BODY, HttpStatus.OK, overrun[index]);
                assertThat(first.stopReason()).isEqualTo(BOUND_EXCEEDED);
            }
            try (StageCapLedger replayed = StageCapLedger.open(file, Map.of(STAGE, amount(ONE)), false)) {
                assertThat(replayed.stopReason()).isEqualTo(BOUND_EXCEEDED);
            }
        }
        Fixture reasoning = fixture(amount(ONE), table);
        String reasoningUsage = "{\"usage\":{\"input_tokens\":" + bytes(BODY).length
                + ",\"output_tokens\":10,\"output_tokens_details\":{\"reasoning_tokens\":10}}}";
        reasoning.send(BODY, HttpStatus.OK, reasoningUsage);
        assertThat(reasoning.ledger.stopped()).isFalse();
        assertThat(reasoning.ledger.billed(STAGE)).isEqualByComparingTo(exact);
        assertThat(reasoning.ledger.spent(STAGE)).isGreaterThan(exact);
    }

    @Test void feasibilityRuleUsesFrozenInputsWithoutCeilingLiterals() {
        BigDecimal scheduled = amount("10.35");
        BigDecimal worst = amount("0.30");
        assertThat(ProviderBoundReservation.stageFeasible(amount("10.50"), scheduled, worst, 2)).isFalse();
        assertThat(ProviderBoundReservation.stageFeasible(amount("11.25"), scheduled, worst, 2)).isTrue();
        BigDecimal solScheduled = amount("61.56");
        BigDecimal solWorst = amount("1.40");
        assertThat(ProviderBoundReservation.stageFeasible(amount("63"), solScheduled, solWorst, 2)).isFalse();
        assertThat(ProviderBoundReservation.stageFeasible(amount("65.50"), solScheduled, solWorst, 2)).isTrue();
    }

    @Test void realEntryRequiresTableAfterRealFreezeCheckAndBeforeOutputCreation() throws Exception {
        var constructor = FreezeGuard.Verified.class.getDeclaredConstructor(
                FreezeRecord.class, String.class, String.class, boolean.class);
        constructor.setAccessible(true);
        FreezeGuard.Verified realBranch = constructor.newInstance(null, SYNTHETIC, SYNTHETIC, true);
        FreezeGuard.Verified dry = FreezeGuard.dryEvidence(null, SYNTHETIC, SYNTHETIC);
        Path output = temp.resolve("real-entry-output");
        var without = new StageRunner.RunInput(MODEL, API_KEY, STAGE, null, Map.of(),
                amount(ONE), amount(TWO), null, output);
        StageCapLedger value = ledger(amount(ONE));
        AtomicInteger dispatches = new AtomicInteger();
        List<StageRunner.CaseCall> oneCase = List.of(new StageRunner.CaseCall() {
            @Override public String id() { return CELL; }
            @Override public String cellId() { return CELL; }
            @Override public String generationId() { return CELL; }
            @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                                                              ArmAssertingInterceptor path) {
                dispatches.incrementAndGet();
                return new StageRunner.CallResult(true, Set.of(), null);
            }
        });
        List<StageRunner.CaseCall> noCalls = List.of();
        assertThatThrownBy(() -> StageRunner.runVerified(without, oneCase, value, dry))
                .isInstanceOf(FreezeRecord.InvalidFreeze.class);
        assertThatThrownBy(() -> StageRunner.runVerified(without, oneCase, value, realBranch))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(TABLE_REQUIRED);
        assertThatThrownBy(() -> StageRunner.runVerified(without, oneCase, value, realBranch, null))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(TABLE_REQUIRED);
        assertThat(Files.exists(output)).isFalse();
        assertThat(value.attempts(STAGE)).isZero();
        assertThat(dispatches.get()).isZero();
        var withTable = new StageRunner.RunInput(MODEL, API_KEY, STAGE, null, null,
                amount(ONE), amount(TWO), null, output, bounds());
        StageRunner.RunSummary summary = StageRunner.runVerified(withTable, noCalls, value, realBranch);
        assertThat(summary.outcomes()).isEmpty();
        assertThat(Files.exists(output.resolve("summary.json"))).isTrue();
    }

    @Test void operatorRunFileReadsFrozenTableWithoutForecasts() throws Exception {
        var table = bounds();
        Path file = temp.resolve("run-plan.json");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("arm", ARM_NAME);
        row.put("model", MODEL);
        row.put("stage", STAGE);
        row.put("priceIn", amount(ONE));
        row.put("priceOut", amount(TWO));
        row.put("providerBounds", table);
        row.put("outputDir", temp.resolve("operator-output"));
        row.put("receiptFile", temp.resolve("receipt.json"));
        row.put("freezeFile", temp.resolve("freeze.json"));
        row.put("parametersFile", temp.resolve("parameters.json"));
        row.put("manifests", Map.of());
        row.put("explicitlySelected", false);
        row.put("baselineReceipts", List.of());
        new ObjectMapper().findAndRegisterModules().writeValue(file.toFile(), row);
        Stage1RunPlan.Config parsed = Stage1RunPlan.readConfig(file, API_KEY);
        assertThat(parsed.input().providerBounds()).isEqualTo(table);
        assertThat(parsed.input().forecasts()).isEmpty();
    }

    @Test void recheckRepeatKeepsTheTableAndFailsClosedWhenSecondStrictTransferWillNotFit() throws Exception {
        String caseId = "recheck-case";
        String workflow = "NOTE_ADAPTIVE";
        String arm = ARM_NAME;
        String failure = "SCHEMA_OR_PARSE";
        String hash = "a".repeat(64);
        String head = "b".repeat(40);
        var table = bounds();
        BigDecimal strict = strict(table);
        StageCapLedger value = ledger(amount(ONE));
        AtomicInteger forwarded = new AtomicInteger();
        ClientHttpRequestFactory stub = (uri, method) -> new MockClientHttpRequest(method, uri) {
            @Override protected ClientHttpResponse executeInternal() {
                forwarded.incrementAndGet();
                return response(HttpStatus.OK, USAGE);
            }
        };
        StageRunner.RunInput input = new StageRunner.RunInput(MODEL, API_KEY, STAGE,
                Map.of(STAGE, amount(ONE)), Map.of(CELL,
                new ForecastReservation(1, 1, amount(ONE), amount(TWO), amount(ONE))),
                amount(ONE), amount(TWO), null, temp.resolve("original-run"), table);
        StageRunner.CaseCall call = new StageRunner.CaseCall() {
            @Override public String id() { return caseId; }
            @Override public String cellId() { return CELL; }
            @Override public String generationId() { return caseId; }
            @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                                                              ArmAssertingInterceptor path) {
                client.post().uri("/responses").body(BODY).retrieve().body(String.class);
                return new StageRunner.CallResult(false, Set.of(), failure);
            }
        };
        FreezeRecord record = new FreezeRecord(head, hash, hash, Map.of(), Map.of(),
                Map.of(arm, new FreezeRecord.Arm(MODEL, List.of(workflow))),
                Map.of(workflow, MODEL), READ_DATE, Set.of(failure));
        FreezeGuard.Verified dry = FreezeGuard.dryEvidence(record, hash, head);
        StageRunner.RunSummary original = StageRunner.run(input, stub, List.of(call), value);
        Path output = temp.resolve("original-run/delivered-outputs.jsonl");
        DeliveredOutputs.Written written = DeliveredOutputs.write(output, arm, 0, original.outcomes(),
                Map.of(caseId, workflow), Map.of(caseId, hash), original.deliveredByCase());
        Stage1Receipt receipt = Stage1Receipt.fromRun(dry, arm, MODEL, STAGE, Instant.now(), hash,
                Map.of(caseId, hash), Map.of(caseId, workflow), original, written);
        RecheckRunner.runOffline(input, () -> List.of(call), Map.of(caseId, hash), receipt, output,
                value, Map.of(caseId, strict.subtract(amount(ONE_NANODOLLAR))),
                temp.resolve("rechecks"), stub, dry);
        assertThat(forwarded.get()).isEqualTo(2);
        assertThat(value.stopped()).isTrue();
        assertThat(value.stopReason()).contains(RECHECK_EXHAUSTED);
    }
}
