package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline durability guards for preregistration section 5. */
class StageCapLedgerDurabilityTest {
    private static final StageCapLedger.Stage STAGE = StageCapLedger.Stage.STAGE_1;
    private static final String COST_230 = "0.000230000";
    private static final String ONE_AMOUNT = "1";
    private static final String SEVEN_TENTHS = "0.7";
    private static final String TWO_TENTHS = "0.2";
    private static final String MISSING = "missing";
    private static final String TWO_AMOUNT = "2";
    private static final String CAPS_DIFFER = "caps differ";
    private static final String OUTSIDE_REPO = "outside repository";
    private static final String RESERVE_RECORD = "RESERVE";
    private static final String OWNER_STOP = "owner stopped stage";
    private static final String ONE_TENTH = "0.1";
    private static final String ONE_ID = "one";
    private static final String CELL = "durable-cell";
    private static final String MODEL = "gpt-6-luna";
    private static final String KEY = "FAKE_KEY_never_write";
    private static final String BODY = "{\"model\":\"gpt-6-luna\",\"input\":[\"private-request-marker\"]}";
    private static final String RESPONSE = "{\"output_text\":\"ok\",\"usage\":{\"input_tokens\":100,\"output_tokens\":100,\"output_tokens_details\":{\"reasoning_tokens\":30}}}";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test void persistenceSkippedMutantKilledByTwoSeparateRuns() throws Exception {
        Path file = ledgerFile();
        Map<StageCapLedger.Stage, BigDecimal> caps = caps("0.0004");
        DiskCheckingFactory firstFactory = new DiskCheckingFactory(file);
        try (StageCapLedger first = StageCapLedger.open(file, caps, true)) {
            StageRunner.run(input(), firstFactory, List.of(call()), first);
            assertThat(first.spent(STAGE)).isEqualByComparingTo(COST_230);
        }
        DiskCheckingFactory secondFactory = new DiskCheckingFactory(file);
        try (StageCapLedger second = StageCapLedger.open(file, caps, false)) {
            StageRunner.RunSummary summary = StageRunner.run(input(), secondFactory, List.of(call()), second);
            assertThat(summary.outcomes().getFirst().state()).isEqualTo(CaseArmOutcome.State.INCONCLUSIVE);
            assertThat(second.spent(STAGE)).isEqualByComparingTo(COST_230);
            assertThat(second.stopped()).isTrue();
        }
        assertThat(firstFactory.dispatches.get()).isEqualTo(1);
        assertThat(secondFactory.dispatches.get()).isZero();
    }

    @Test void unsettledReservationReadAsZeroMutantKilledByCrashReplayAndReconcile() {
        Path file = ledgerFile();
        Map<StageCapLedger.Stage, BigDecimal> caps = caps(ONE_AMOUNT);
        String id;
        try (StageCapLedger first = StageCapLedger.open(file, caps, true)) {
            id = first.reserve(STAGE, CELL, amount(SEVEN_TENTHS)).id();
        }
        try (StageCapLedger reopened = StageCapLedger.open(file, caps, false)) {
            assertThat(reopened.unreconciled()).isTrue();
            assertThat(reopened.spent(STAGE)).isEqualByComparingTo(SEVEN_TENTHS);
            assertThat(reopened.remaining(STAGE)).isEqualByComparingTo("0.3");
            assertThat(reopened.attempts(STAGE)).isEqualTo(1);
            assertThatThrownBy(() -> reopened.reserve(STAGE, CELL, amount("0.4")))
                    .isInstanceOf(StageCapLedger.SpendStop.class);
            reopened.reconcile(id, amount(TWO_TENTHS));
            assertThat(reopened.unreconciled()).isFalse();
            assertThat(reopened.spent(STAGE)).isEqualByComparingTo(TWO_TENTHS);
        }
        try (StageCapLedger verified = StageCapLedger.open(file, caps, false)) {
            assertThat(verified.unreconciled()).isFalse();
            assertThat(verified.spent(STAGE)).isEqualByComparingTo(TWO_TENTHS);
        }
    }

    @Test void initRefusalAndMissingFileMutantsKilled() throws Exception {
        Path file = ledgerFile();
        Map<StageCapLedger.Stage, BigDecimal> caps = caps(ONE_AMOUNT);
        assertThatThrownBy(() -> StageCapLedger.open(file, caps, false))
                .isInstanceOf(StageCapLedger.LedgerFailure.class).hasMessageContaining(MISSING);
        try (StageCapLedger ignored = StageCapLedger.open(file, caps, true)) {
            assertThat(Files.readString(file)).contains("INIT", "createdAt", "caps");
        }
        assertThatThrownBy(() -> StageCapLedger.open(file, caps, true))
                .isInstanceOf(StageCapLedger.LedgerFailure.class).hasMessageContaining("already exists");
        Files.delete(file);
        assertThatThrownBy(() -> StageCapLedger.open(file, caps, false))
                .isInstanceOf(StageCapLedger.LedgerFailure.class).hasMessageContaining(MISSING);
    }

    @Test void capMismatchRefusesAndAmendRecordsOldAndNew() throws Exception {
        Path file = ledgerFile();
        Map<StageCapLedger.Stage, BigDecimal> oldCaps = caps(ONE_AMOUNT);
        Map<StageCapLedger.Stage, BigDecimal> newCaps = caps(TWO_AMOUNT);
        try (StageCapLedger ignored = StageCapLedger.open(file, oldCaps, true)) { /* initial file */ }
        assertThatThrownBy(() -> StageCapLedger.open(file, newCaps, false))
                .isInstanceOf(StageCapLedger.LedgerFailure.class).hasMessageContaining(CAPS_DIFFER);
        try (StageCapLedger ledger = StageCapLedger.open(file, oldCaps, false)) {
            ledger.amendCaps(newCaps);
        }
        assertThat(Files.readString(file)).contains("AMEND", "old", "new");
        try (StageCapLedger reopened = StageCapLedger.open(file, newCaps, false)) {
            assertThat(reopened.cap(STAGE)).isEqualByComparingTo(TWO_AMOUNT);
            assertThat(reopened.recordCount()).isEqualTo(2);
        }
        assertThatThrownBy(() -> StageCapLedger.open(file, oldCaps, false))
                .isInstanceOf(StageCapLedger.LedgerFailure.class).hasMessageContaining(CAPS_DIFFER);
    }

    @Test void missingLockMutantKilledBySecondOpenRefusal() {
        Path file = ledgerFile();
        Map<StageCapLedger.Stage, BigDecimal> caps = caps(ONE_AMOUNT);
        try (StageCapLedger ignored = StageCapLedger.open(file, caps, true)) {
            assertThatThrownBy(() -> StageCapLedger.open(file, caps, false))
                    .isInstanceOf(StageCapLedger.LedgerFailure.class).hasMessageContaining("lock held");
        }
    }

    @Test void corruptAndTruncatedRecordsRefuseWithoutGuessing() throws Exception {
        Path file = ledgerFile();
        try (StageCapLedger ignored = StageCapLedger.open(file, caps(ONE_AMOUNT), true)) { /* initial file */ }
        Files.writeString(file, "not-json\n", java.nio.file.StandardOpenOption.APPEND);
        assertThatThrownBy(() -> StageCapLedger.open(file, caps(ONE_AMOUNT), false))
                .isInstanceOf(StageCapLedger.LedgerFailure.class).hasMessageContaining("Corrupt");
        Path truncated = temp.resolve("truncated.jsonl");
        String init = Files.readAllLines(file).getFirst();
        Files.writeString(truncated, init + "\n{\"type\":\"RESERVE\"");
        assertThatThrownBy(() -> StageCapLedger.open(truncated, caps(ONE_AMOUNT), false))
                .isInstanceOf(StageCapLedger.LedgerFailure.class).hasMessageContaining("truncated");
    }

    @Test void backendTargetPathAllowedMutantKilledByPathRefusals() {
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        List<Path> forbidden = List.of(repo.resolve("ledger.jsonl"), repo.resolve("backend/target/ledger.jsonl"));
        for (Path path : forbidden)
            assertThatThrownBy(() -> StageCapLedger.open(path, caps(ONE_AMOUNT), true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(OUTSIDE_REPO);
    }

    @Test void llmBenchmarkPathRejectedBeforeFilesystemAccess() {
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        Path forbidden = repo.resolve("backend/target/llm-benchmark/ledger.jsonl");
        assertThatThrownBy(() -> StageCapLedger.open(forbidden, caps(ONE_AMOUNT), true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(OUTSIDE_REPO);
    }

    @Test void reserveAfterDispatchMutantKilledAndSecretsAbsent() throws Exception {
        Path file = ledgerFile();
        DiskCheckingFactory factory = new DiskCheckingFactory(file);
        try (StageCapLedger ledger = StageCapLedger.open(file, caps(ONE_AMOUNT), true)) {
            StageRunner.RunSummary summary = StageRunner.run(input(), factory, List.of(call()), ledger);
            assertThat(summary.ledger().recordCount()).isEqualTo(3);
        }
        assertThat(factory.dispatches.get()).isEqualTo(1);
        String contents = Files.readString(file);
        assertThat(contents).contains(RESERVE_RECORD, "SETTLE").doesNotContain(KEY, BODY, "private-request-marker");
    }

    @Test void summaryReportsEffectiveTimeoutsAndUnreconciled() throws Exception {
        Path file = ledgerFile();
        Map<StageCapLedger.Stage, BigDecimal> caps = caps(ONE_AMOUNT);
        try (StageCapLedger first = StageCapLedger.open(file, caps, true)) {
            first.reserve(STAGE, CELL, amount(TWO_TENTHS));
        }
        try (StageCapLedger reopened = StageCapLedger.open(file, caps, false)) {
            StageRunner.run(input(), new DiskCheckingFactory(file), List.of(), reopened);
            JsonNode summary = JSON.readTree(Files.readString(temp.resolve("summary.json")));
            StudySnapProperties properties = ArmRouting.forModel(MODEL, KEY);
            assertThat(summary.get("connectTimeoutSeconds").asInt())
                    .isEqualTo(properties.getLlm().getApi().getConnectTimeoutSeconds());
            assertThat(summary.get("readTimeoutSeconds").asInt())
                    .isEqualTo(properties.getLlm().getApi().getReadTimeoutSeconds());
            assertThat(summary.get("unreconciled").asBoolean()).isTrue();
            assertThat(summary.get("ledgerRecordCount").asInt()).isEqualTo(2);
            assertThat(summary.get("ledgerCreatedAt").asText()).isNotBlank();
        }
    }

    @Test void capRefusalIsRunOnlyAndSmallerForecastFitsAfterReopen() {
        Path file = ledgerFile();
        try (StageCapLedger first = StageCapLedger.open(file, caps(ONE_AMOUNT), true)) {
            first.reserve(STAGE, CELL, amount(SEVEN_TENTHS)).settle(amount(SEVEN_TENTHS));
            assertThatThrownBy(() -> first.reserve(STAGE, CELL, amount("0.4")))
                    .isInstanceOf(StageCapLedger.SpendStop.class);
            assertThat(first.stopped()).isTrue();
        }
        try (StageCapLedger reopened = StageCapLedger.open(file, caps(ONE_AMOUNT), false)) {
            assertThat(reopened.stopped()).isFalse();
            reopened.reserve(STAGE, CELL, amount(TWO_TENTHS)).abandon();
            assertThatThrownBy(() -> reopened.reserve(STAGE, CELL, amount("0.4")))
                    .isInstanceOf(StageCapLedger.SpendStop.class);
        }
    }

    @Test void unmeteredStopPersistsUntilOwnerResume() {
        Path file = ledgerFile();
        try (StageCapLedger first = StageCapLedger.open(file, caps(ONE_AMOUNT), true)) {
            first.reserve(STAGE, CELL, amount(ONE_TENTH)).settle(null, null);
            assertThat(first.stopped()).isTrue();
        }
        try (StageCapLedger reopened = StageCapLedger.open(file, caps(ONE_AMOUNT), false)) {
            assertThat(reopened.stopped()).isTrue();
            reopened.resume();
            assertThat(reopened.stopped()).isFalse();
        }
        try (StageCapLedger again = StageCapLedger.open(file, caps(ONE_AMOUNT), false)) {
            assertThat(again.stopped()).isFalse();
            assertThat(again.reserve(STAGE, CELL, amount(ONE_TENTH)).id()).isNotBlank();
        }
    }

    @Test void stopRecordSurvivesReopen() {
        Path file = ledgerFile();
        try (StageCapLedger first = StageCapLedger.open(file, caps(ONE_AMOUNT), true)) {
            first.stop(OWNER_STOP);
        }
        try (StageCapLedger reopened = StageCapLedger.open(file, caps(ONE_AMOUNT), false)) {
            assertThat(reopened.stopped()).isTrue();
            assertThat(reopened.stopReason()).isEqualTo(OWNER_STOP);
            assertThatThrownBy(() -> reopened.reserve(STAGE, CELL, amount(ONE_TENTH)))
                    .isInstanceOf(StageCapLedger.SpendStop.class);
        }
    }

    @Test void failedReserveWriteCannotDispatch() throws Exception {
        Path file = ledgerFile();
        try (StageCapLedger ledger = StageCapLedger.open(file, caps(ONE_AMOUNT), true)) {
            java.nio.channels.FileChannel channel = (java.nio.channels.FileChannel)
                    org.springframework.test.util.ReflectionTestUtils.getField(ledger, "channel");
            channel.close();
            assertThatThrownBy(() -> ledger.reserve(STAGE, CELL, amount(ONE_TENTH)))
                    .isInstanceOf(StageCapLedger.LedgerFailure.class).hasMessageContaining("no dispatch allowed");
            assertThat(ledger.attempts(STAGE)).isZero();
        }
        try (StageCapLedger reopened = StageCapLedger.open(file, caps(ONE_AMOUNT), false)) {
            assertThat(reopened.recordCount()).isEqualTo(1);
        }
    }

    @Test void malformedPropertyCapsRefuse() {
        for (String value : List.of("", "STAGE_1:", "STAGE_1:0", "STAGE_1:1,STAGE_1:2", "STAGE_4:1"))
            assertThatThrownBy(() -> StageRunner.parseCaps(value)).isInstanceOf(IllegalArgumentException.class);
        assertThat(StageRunner.parseCaps("STAGE_1:12.00,STAGE_2:80.00"))
                .containsKeys(StageCapLedger.Stage.STAGE_1, StageCapLedger.Stage.STAGE_2);
    }

    private Path ledgerFile() {
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        assertThat(temp.toAbsolutePath().normalize().startsWith(repo)).isFalse();
        assertThat(temp.toAbsolutePath().normalize().startsWith(repo.resolve("backend/target"))).isFalse();
        return temp.resolve("stage-ledger.jsonl");
    }
    private static BigDecimal amount(String text) { return new BigDecimal(text); }
    private static Map<StageCapLedger.Stage, BigDecimal> caps(String value) { return Map.of(STAGE, amount(value)); }
    private StageRunner.RunInput input() {
        return new StageRunner.RunInput(MODEL, KEY, STAGE,
                Map.of(CELL, new ForecastReservation(100, 100, amount(ONE_AMOUNT), amount(ONE_AMOUNT), amount(ONE_AMOUNT))),
                amount(ONE_AMOUNT), amount(ONE_AMOUNT), null, temp);
    }
    private static StageRunner.CaseCall call() {
        return new StageRunner.CaseCall() {
            @Override public String id() { return ONE_ID; }
            @Override public String cellId() { return CELL; }
            @Override public String generationId() { return ONE_ID; }
            @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                    ArmAssertingInterceptor path) {
                client.post().uri("/responses").body(BODY).retrieve().body(String.class);
                return new StageRunner.CallResult(true, Set.of(), null);
            }
        };
    }
    private static final class DiskCheckingFactory implements org.springframework.http.client.ClientHttpRequestFactory {
        private final Path file;
        private final AtomicInteger dispatches = new AtomicInteger();
        DiskCheckingFactory(Path file) { this.file = file; }
        @Override public MockClientHttpRequest createRequest(java.net.URI uri, HttpMethod method) {
            return new MockClientHttpRequest(method, uri) {
                @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                    assertThat(Files.readString(file)).contains(RESERVE_RECORD);
                    dispatches.incrementAndGet();
                    return new MockClientHttpResponse(RESPONSE.getBytes(StandardCharsets.UTF_8), 200);
                }
            };
        }
    }
}
