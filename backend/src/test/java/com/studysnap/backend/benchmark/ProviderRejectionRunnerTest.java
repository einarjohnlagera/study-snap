package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.StudySnapProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRejectionRunnerTest {
    private static final String CASE = "rejected-case";
    private static final String SECOND = "second-case";
    private static final String CELL = "NOTE_ADAPTIVE:synthetic";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String ARM = "LUNA";
    private static final String MODEL = "gpt-6-luna";
    private static final String KEY = "fake-key";
    private static final String HASH = "a".repeat(64);
    private static final String HEAD = "b".repeat(40);
    private static final String INPUT = "cells have membranes";
    private static final BigDecimal RESERVATION = new BigDecimal("0.002");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @TempDir Path temp;

    private record Original(Stage1Receipt receipt, Path output) {}

    private static final class Call implements StageRunner.CaseCall {
        private final String id;
        private final String input;
        private final boolean delivered;
        Call(String id, String input, boolean delivered) {
            this.id = id; this.input = input; this.delivered = delivered;
        }
        @Override public String id() { return id; }
        @Override public String cellId() { return CELL; }
        @Override public String generationId() { return id; }
        @Override public Object deliveredContent() {
            return delivered ? List.of(Map.of("question", "What surrounds cells?")) : null;
        }
        @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                                                         ArmAssertingInterceptor path) {
            client.post().uri("/responses").body(Map.of("model", MODEL, "input", input))
                    .retrieve().body(String.class);
            return new StageRunner.CallResult(delivered, Set.of(), delivered ? null : "HTTP_ERROR");
        }
    }

    private static FreezeGuard.Verified freeze() {
        FreezeRecord record = new FreezeRecord(HEAD, HASH, HASH, Map.of(), Map.of(),
                Map.of(ARM, new FreezeRecord.Arm(MODEL, List.of(WORKFLOW))),
                Map.of(WORKFLOW, MODEL), LocalDate.of(2026, 11, 17),
                Set.of(RecheckPlan.PROVIDER_REJECTION));
        return FreezeGuard.dryEvidence(record, HASH, HEAD);
    }

    private StageRunner.RunInput input(BigDecimal cap) {
        return input(Map.of(StageCapLedger.Stage.STAGE_1, cap));
    }

    private StageRunner.RunInput input(Map<StageCapLedger.Stage, BigDecimal> caps) {
        return new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_1,
                caps,
                Map.of(CELL, new ForecastReservation(1_000, 1_000,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
                BigDecimal.ONE, BigDecimal.ONE, null, temp.resolve("unused"));
    }

    private static ClientHttpRequestFactory stub(AtomicInteger dispatches, int... statuses) {
        return (uri, method) -> new MockClientHttpRequest(method, uri) {
            @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                int index = dispatches.getAndIncrement();
                int status = statuses[index];
                String body = status >= 400 ? "{\"error\":{\"message\":\"rejected\"}}"
                        : "{\"output_text\":\"ok\",\"usage\":{\"input_tokens\":1,\"output_tokens\":4000}}";
                return new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
            }
        };
    }

    private Original original(StageCapLedger ledger, ClientHttpRequestFactory transport,
                              List<String> ids) throws Exception {
        List<StageRunner.CaseCall> calls = ids.stream()
                .map(id -> (StageRunner.CaseCall) new Call(id, INPUT, false)).toList();
        StageRunner.RunSummary run = StageRunner.run(input(ledger.approvedCaps()),
                transport, calls, ledger);
        Path path = temp.resolve("original/delivered-outputs.jsonl");
        Files.createDirectories(path.getParent());
        Map<String, String> workflows = ids.stream().collect(java.util.stream.Collectors.toMap(
                id -> id, ignored -> WORKFLOW));
        Map<String, String> hashes = ids.stream().collect(java.util.stream.Collectors.toMap(
                id -> id, ignored -> HASH));
        DeliveredOutputs.Written written = DeliveredOutputs.write(path, ARM, 0, run.outcomes(),
                workflows, hashes, run.deliveredByCase());
        Stage1Receipt receipt = Stage1Receipt.fromRun(freeze(), ARM, MODEL, StageCapLedger.Stage.STAGE_1,
                Instant.now(), HASH, hashes, workflows, run, written);
        return new Original(receipt, path);
    }

    @Test void eachRepeatRecordsItsOwnStatusAndOldReceiptDefaultsFalse() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = stub(dispatches, 400, 422, 200);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, transport, List.of(CASE));
        Supplier<List<StageRunner.CaseCall>> calls = () -> List.of(new Call(CASE, INPUT, true));
        RecheckReceipt receipt = RecheckRunner.runOffline(input(BigDecimal.ONE), calls,
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, RESERVATION), temp.resolve("rechecks"), transport, freeze());
        assertThat(receipt.cases().getFirst().repeats())
                .extracting(RecheckReceipt.Repeat::providerRejected).containsExactly(true, false);
        assertThat(dispatches.get()).isEqualTo(3);
        Path old = temp.resolve("old-receipt.json");
        ObjectNode node = (ObjectNode) JSON.valueToTree(receipt);
        for (var repeat : node.path("cases").get(0).path("repeats"))
            ((ObjectNode) repeat).remove("providerRejected");
        JSON.writeValue(old.toFile(), node);
        assertThat(RecheckReceipt.read(old).cases().getFirst().repeats())
                .extracting(RecheckReceipt.Repeat::providerRejected).containsExactly(false, false);
    }

    @Test void rejectionRecheckWithChangedBodyIsMissing() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = stub(dispatches, 400, 200, 200);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, transport, List.of(CASE));
        AtomicInteger constructions = new AtomicInteger();
        Supplier<List<StageRunner.CaseCall>> calls = () -> List.of(new Call(CASE,
                constructions.incrementAndGet() == 3 ? "different" : INPUT, true));
        RecheckReceipt receipt = RecheckRunner.runOffline(input(BigDecimal.ONE), calls,
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, RESERVATION), temp.resolve("rechecks"), transport, freeze());
        RecheckReceipt.Repeat changed = receipt.cases().getFirst().repeats().get(1);
        assertThat(changed.evidence()).isEqualTo(RouteVerdictCalculator.Evidence.MISSING);
        assertThat(changed.protocolDeviation()).contains(CASE, "call index 0");
    }

    @Test void otherHttpErrorsDoNotSetProviderRejectionFlag() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = stub(dispatches, 400, 500, 200);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, transport, List.of(CASE));
        Supplier<List<StageRunner.CaseCall>> calls = () -> List.of(new Call(CASE, INPUT, true));
        RecheckReceipt receipt = RecheckRunner.runOffline(input(BigDecimal.ONE), calls,
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, RESERVATION), temp.resolve("rechecks"), transport, freeze());
        assertThat(receipt.cases().getFirst().repeats())
                .extracting(RecheckReceipt.Repeat::providerRejected).containsExactly(false, false);
        assertThat(receipt.cases().getFirst().repeats().getFirst().finalFailureClass())
                .isEqualTo("HTTP_ERROR");
    }

    @Test void missingReservationAndAggregateCapRefuseBeforeAnyRecheckDispatch() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = stub(dispatches, 400, 400);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1,
                new BigDecimal("0.007")));
        Original original = original(ledger, transport, List.of(CASE, SECOND));
        int before = dispatches.get();
        Supplier<List<StageRunner.CaseCall>> calls = () -> List.of(new Call(CASE, INPUT, true),
                new Call(SECOND, INPUT, true));
        StageRunner.RunInput runInput = input(new BigDecimal("0.007"));
        Stage1Receipt receipt = original.receipt();
        Path originalOutput = original.output();
        Map<String, String> hashes = Map.of(CASE, HASH, SECOND, HASH);
        FreezeGuard.Verified verified = freeze();
        Map<String, BigDecimal> missing = Map.of(CASE, RESERVATION);
        Path missingPath = temp.resolve("missing");
        assertThatThrownBy(() -> RecheckRunner.runOffline(runInput, calls, hashes, receipt,
                originalOutput, ledger, missing, missingPath, transport, verified))
                .isInstanceOf(RecheckRunner.InvalidRecheck.class);
        Map<String, BigDecimal> nonpositive = Map.of(CASE, BigDecimal.ZERO, SECOND, RESERVATION);
        Path nonpositivePath = temp.resolve("nonpositive");
        assertThatThrownBy(() -> RecheckRunner.runOffline(runInput, calls, hashes, receipt,
                originalOutput, ledger, nonpositive, nonpositivePath, transport, verified))
                .isInstanceOf(RecheckRunner.InvalidRecheck.class);
        Map<String, BigDecimal> over = Map.of(CASE, RESERVATION, SECOND, RESERVATION);
        Path overPath = temp.resolve("over");
        assertThatThrownBy(() -> RecheckRunner.runOffline(runInput, calls, hashes, receipt,
                originalOutput, ledger, over, overPath, transport, verified))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(dispatches.get()).isEqualTo(before);
    }

    @Test void midRunCapStopWritesOnlyCompletedCasesToPartialReceipt() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = stub(dispatches, 400, 400, 200, 200);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1,
                new BigDecimal("0.010")));
        Original original = original(ledger, transport, List.of(CASE, SECOND));
        Supplier<List<StageRunner.CaseCall>> calls = () -> List.of(new Call(CASE, INPUT, true),
                new Call(SECOND, INPUT, true));
        Path output = temp.resolve("rechecks");
        StageRunner.RunInput runInput = input(new BigDecimal("0.010"));
        Stage1Receipt receipt = original.receipt();
        Path originalOutput = original.output();
        Map<String, String> hashes = Map.of(CASE, HASH, SECOND, HASH);
        Map<String, BigDecimal> reservations = Map.of(CASE, RESERVATION, SECOND, RESERVATION);
        FreezeGuard.Verified verified = freeze();
        assertThatThrownBy(() -> RecheckRunner.runOffline(runInput, calls, hashes,
                receipt, originalOutput, ledger, reservations, output, transport, verified))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(dispatches.get()).isEqualTo(4);
        assertThat(RecheckReceipt.read(output.resolve("recheck-receipt.partial.json")).cases())
                .extracting(RecheckReceipt.Case::caseId).containsExactly(CASE);
        assertThat(Files.exists(output.resolve("recheck-receipt.json"))).isFalse();
    }

    @Test void totalHeadroomIncludesOverspendInAnotherStage() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = stub(dispatches, 400, 400);
        BigDecimal cap = new BigDecimal("0.020");
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, cap,
                StageCapLedger.Stage.STAGE_2, cap));
        Original original = original(ledger, transport, List.of(CASE, SECOND));
        StageCapLedger.Reservation prior = ledger.reserve(StageCapLedger.Stage.STAGE_2,
                "OTHER:synthetic", new BigDecimal("0.010"));
        prior.settle(new BigDecimal("0.030"));
        assertThat(ledger.remaining(StageCapLedger.Stage.STAGE_1)).isGreaterThan(new BigDecimal("0.012"));
        int before = dispatches.get();
        Supplier<List<StageRunner.CaseCall>> calls = () -> List.of(new Call(CASE, INPUT, true),
                new Call(SECOND, INPUT, true));
        BigDecimal one = new BigDecimal("0.003");
        StageRunner.RunInput runInput = input(ledger.approvedCaps());
        Stage1Receipt receipt = original.receipt();
        Path originalOutput = original.output();
        Map<String, String> hashes = Map.of(CASE, HASH, SECOND, HASH);
        Map<String, BigDecimal> reservations = Map.of(CASE, one, SECOND, one);
        Path output = temp.resolve("total-headroom");
        FreezeGuard.Verified verified = freeze();
        assertThatThrownBy(() -> RecheckRunner.runOffline(runInput, calls, hashes,
                receipt, originalOutput, ledger, reservations, output, transport, verified))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(dispatches.get()).isEqualTo(before);
    }

    @Test void offlineEntryRejectsMissingStubBeforeAnyCaseAccess() {
        AtomicInteger caseAccesses = new AtomicInteger();
        Supplier<List<StageRunner.CaseCall>> calls = () -> {
            caseAccesses.incrementAndGet();
            return List.of();
        };
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        StageRunner.RunInput runInput = input(BigDecimal.ONE);
        Map<String, String> hashes = Map.of();
        Map<String, BigDecimal> reservations = Map.of();
        Path output = temp.resolve("no-stub");
        FreezeGuard.Verified verified = freeze();
        assertThatThrownBy(() -> RecheckRunner.runOffline(runInput, calls, hashes,
                null, null, ledger, reservations, output, null, verified))
                .isInstanceOf(RecheckRunner.InvalidRecheck.class)
                .hasMessageContaining("stub transport");
        assertThat(caseAccesses.get()).isZero();
    }
}
