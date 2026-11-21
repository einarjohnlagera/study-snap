package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Boundary controls for the provider-bound table and request allowlist (added by the 5h audit). */
class ProviderBoundGateBoundaryTest {
    private static final String MODEL = "gpt-6-luna";
    private static final String CELL = "synthetic-cell";
    private static final String TEXT = "\"text\":{\"format\":{\"type\":\"json_schema\",\"name\":\"t\",\"schema\":{},\"strict\":true}}";
    private static final String PART = "{\"type\":\"input_text\",\"text\":\"x\"}";
    private static final String USAGE = "{\"usage\":{\"input_tokens\":2,\"output_tokens\":3}}";
    private static final StageCapLedger.Stage STAGE = StageCapLedger.Stage.STAGE_1;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 19);
    private static final String UNPRICEABLE_CONTENT = "UNPRICEABLE_REQUEST_CONTENT";
    private static final String UNPRICEABLE = "UNPRICEABLE_REQUEST";
    private static final String FORBIDDEN_TYPE = "FORBIDDEN_REQUEST_CONTENT_TYPE";
    @TempDir Path temp;

    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static ProviderBoundReservation.ModelPrice price(long max, String in, String out) {
        return new ProviderBoundReservation.ModelPrice(max, d(in), d(out));
    }
    private static ProviderBoundReservation table(long threshold, long margin) {
        return new ProviderBoundReservation(Map.of(MODEL, price(10, "1", "2")), threshold, margin, DATE);
    }
    private static String body(String input) {
        return "{\"model\":\"" + MODEL + "\",\"input\":" + input + "," + TEXT + "}";
    }
    private static String message(String content) { return "{\"role\":\"user\",\"content\":" + content + "}"; }
    private static String okBody() { return body("[" + message("[" + PART + "]") + "]"); }

    private final class Fixture {
        final StageCapLedger ledger;
        final ArmAssertingInterceptor path;
        final List<byte[]> forwarded = new ArrayList<>();
        Fixture(ProviderBoundReservation bounds) {
            ledger = new StageCapLedger(Map.of(STAGE, d("1")));
            path = new ArmAssertingInterceptor(new ObjectMapper(), MODEL, "fake-offline-api-key", temp, ledger,
                    STAGE, d("1"), d("2"), Map.of(), bounds, null);
        }
        ClientHttpResponse send(String json, HttpStatus status, String result) throws IOException {
            path.begin("synthetic-generation", CELL);
            try {
                return path.intercept(new MockClientHttpRequest(HttpMethod.POST, URI.create("https://offline.invalid")),
                        json.getBytes(StandardCharsets.UTF_8), (request, outgoing) -> {
                            forwarded.add(outgoing);
                            return new MockClientHttpResponse(result.getBytes(StandardCharsets.UTF_8), status);
                        });
            } finally { path.end(); }
        }
    }

    @Test void tableAndPriceValidationRejectsZeroNegativeAndMissingValues() {
        assertThatThrownBy(() -> price(0, "1", "2")).isInstanceOf(StageCapLedger.SpendStop.class);
        assertThatThrownBy(() -> price(-1, "1", "2")).isInstanceOf(StageCapLedger.SpendStop.class);
        assertThatThrownBy(() -> price(1, "0", "2")).isInstanceOf(StageCapLedger.SpendStop.class);
        assertThatThrownBy(() -> price(1, "-1", "2")).isInstanceOf(StageCapLedger.SpendStop.class);
        assertThatThrownBy(() -> price(1, "1", "0")).isInstanceOf(StageCapLedger.SpendStop.class);
        assertThatThrownBy(() -> price(1, "1", "-1")).isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(price(1, "0.000001", "0.000001").maximumOutputTokens()).isEqualTo(1);
        assertThatThrownBy(() -> new ProviderBoundReservation(Map.of(MODEL, price(1, "1", "1")), 0, 0, DATE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThatThrownBy(() -> new ProviderBoundReservation(Map.of(MODEL, price(1, "1", "1")), 10, 10, DATE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThatThrownBy(() -> new ProviderBoundReservation(Map.of(MODEL, price(1, "1", "1")), 10, -1, DATE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThatThrownBy(() -> new ProviderBoundReservation(Map.of(MODEL, price(1, "1", "1")), 10, 1, null))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThatThrownBy(() -> new ProviderBoundReservation(Map.of(), 10, 1, DATE))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(table(10, 9).framingMargin()).isEqualTo(9);
        assertThat(table(10, 0).framingMargin()).isZero();
        assertThat(table(1, 0).longContextThreshold()).isEqualTo(1);
    }

    @Test void feasibilityEqualityAndZeroConcurrencyAreAccepted() {
        assertThat(ProviderBoundReservation.stageFeasible(d("5"), d("3"), d("1"), 2)).isTrue();
        assertThat(ProviderBoundReservation.stageFeasible(d("4.999999999"), d("3"), d("1"), 2)).isFalse();
        assertThat(ProviderBoundReservation.stageFeasible(d("3"), d("3"), d("1"), 0)).isTrue();
        assertThatThrownBy(() -> ProviderBoundReservation.stageFeasible(d("3"), d("3"), d("1"), -1))
                .isInstanceOf(StageCapLedger.SpendStop.class);
    }

    @Test void outputTextPartsAndMultipleMessagesAreAllowed() throws Exception {
        String json = body("[" + message("[" + PART + "]") + ","
                + message("[{\"type\":\"output_text\",\"text\":\"y\"}," + PART + "]") + "]");
        Fixture fixture = new Fixture(table(100_000, 10));
        fixture.send(json, HttpStatus.OK, USAGE);
        assertThat(fixture.forwarded).hasSize(1);
        assertThat(fixture.ledger.stopped()).isFalse();
    }

    @Test void everyMessageAndEveryPartIsChecked() {
        String badPart = "{\"type\":\"input_image\"}";
        String[] forbidden = {
                body("[" + message("[" + PART + "]") + "," + message("[" + badPart + "]") + "]"),
                body("[" + message("[" + PART + "," + badPart + "]") + "]")};
        for (String json : forbidden) {
            Fixture fixture = new Fixture(table(100_000, 10));
            assertThatThrownBy(() -> fixture.send(json, HttpStatus.OK, USAGE))
                    .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(FORBIDDEN_TYPE);
            assertThat(fixture.forwarded).isEmpty();
            assertThat(fixture.ledger.attempts(STAGE)).isZero();
        }
    }

    @Test void structurallyUnpriceableRequestsRefuseBeforeReserveAndDispatch() {
        Map<String, String> bad = new java.util.LinkedHashMap<>();
        bad.put("message not an object", UNPRICEABLE_CONTENT + "|" + body("[\"x\"]"));
        bad.put("content missing", UNPRICEABLE_CONTENT + "|" + body("[{\"role\":\"user\"}]"));
        bad.put("content not an array", UNPRICEABLE_CONTENT + "|" + body("[" + message("\"x\"") + "]"));
        bad.put("input not an array", UNPRICEABLE + "|" + body("{}"));
        bad.put("input missing", UNPRICEABLE + "|" + okBody().replace("\"input\":", "\"inputs\":"));
        bad.put("text not an object", UNPRICEABLE + "|" + okBody().replace(TEXT, "\"text\":[]"));
        bad.put("blank model", UNPRICEABLE + "|" + okBody().replace("\"model\":\"" + MODEL + "\"", "\"model\":\" \""));
        bad.put("non-text model", UNPRICEABLE + "|" + okBody().replace("\"model\":\"" + MODEL + "\"", "\"model\":5"));
        bad.put("not an object", UNPRICEABLE + "|[]");
        bad.forEach((name, spec) -> {
            String reason = spec.substring(0, spec.indexOf('|'));
            String json = spec.substring(spec.indexOf('|') + 1);
            Fixture fixture = new Fixture(table(100_000, 10));
            assertThatThrownBy(() -> fixture.send(json, HttpStatus.OK, USAGE)).as(name)
                    .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage(reason);
            assertThat(fixture.forwarded).as(name).isEmpty();
            assertThat(fixture.ledger.attempts(STAGE)).as(name).isZero();
            assertThat(fixture.ledger.stopReason()).as(name).isEqualTo(reason);
        });
    }

    @Test void bodyExactlyAtThresholdMinusMarginDispatches() throws Exception {
        long length = okBody().getBytes(StandardCharsets.UTF_8).length;
        Fixture exact = new Fixture(table(length + 5, 5));
        exact.send(okBody(), HttpStatus.OK, USAGE);
        assertThat(exact.forwarded).hasSize(1);
        Fixture over = new Fixture(table(length + 4, 5));
        assertThatThrownBy(() -> over.send(okBody(), HttpStatus.OK, USAGE))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessage("PROVIDER_BOUND_INPUT_MARGIN_EXCEEDED");
    }

    @Test void nonSuccessWithoutUsageSettlesStrictWithoutAnUnmeteredStopAndBooksNoBilledSpend() throws Exception {
        var bounds = table(100_000, 10);
        BigDecimal strict = bounds.usd(MODEL, okBody().getBytes(StandardCharsets.UTF_8));
        Fixture fixture = new Fixture(bounds);
        fixture.send(okBody(), HttpStatus.BAD_REQUEST, "{}");
        assertThat(fixture.ledger.spent(STAGE)).isEqualByComparingTo(strict);
        assertThat(fixture.ledger.stopped()).isFalse();
        assertThat(fixture.ledger.billed(STAGE)).isZero();
        Fixture transport = new Fixture(bounds);
        transport.path.begin("synthetic-generation", CELL);
        try {
            assertThatThrownBy(() -> transport.path.intercept(
                    new MockClientHttpRequest(HttpMethod.POST, URI.create("https://offline.invalid")),
                    okBody().getBytes(StandardCharsets.UTF_8),
                    (request, outgoing) -> { throw new IOException("synthetic transport failure"); }))
                    .isInstanceOf(IOException.class);
        } finally { transport.path.end(); }
        assertThat(transport.ledger.spent(STAGE)).isEqualByComparingTo(strict);
        assertThat(transport.ledger.billed(STAGE)).isZero();
    }
}
