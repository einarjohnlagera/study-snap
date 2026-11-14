package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
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

/** Two-execution, index-order, same-input and ledger mutants for preregistration section 1. */
class RecheckRunnerTest {
    private static final String CASE = "synthetic-case";
    private static final String CELL = "NOTE_ADAPTIVE:synthetic";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String ARM = "LUNA";
    private static final String MODEL = "gpt-6-luna";
    private static final String KEY = "fake-secret-key";
    private static final String FAILURE = "SCHEMA_OR_PARSE";
    private static final String HASH = "a".repeat(64);
    private static final String HEAD = "b".repeat(40);
    private static final String INPUT = "cells have membranes";
    @TempDir Path temp;

    private FreezeGuard.Verified freeze() {
        FreezeRecord record = new FreezeRecord(HEAD, HASH, HASH, Map.of(), Map.of(),
                Map.of(ARM, new FreezeRecord.Arm(MODEL, List.of(WORKFLOW))),
                Map.of(WORKFLOW, MODEL), LocalDate.of(2026, 11, 14), Set.of(FAILURE));
        return FreezeGuard.dryEvidence(record, HASH, HEAD);
    }

    private StageRunner.RunInput input(Path output) {
        return new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE),
                Map.of(CELL, new ForecastReservation(1_000, 1_000,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
                BigDecimal.ONE, BigDecimal.ONE, null, output);
    }

    private ClientHttpRequestFactory transport(AtomicInteger dispatches) {
        return (uri, method) -> new MockClientHttpRequest(method, uri) {
            @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                dispatches.incrementAndGet();
                String response = "{\"output_text\":\"ok\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
                return new MockClientHttpResponse(response.getBytes(StandardCharsets.UTF_8), 200);
            }
        };
    }

    private static final class SyntheticCall implements StageRunner.CaseCall {
        private final boolean delivered;
        private final String input;
        SyntheticCall(boolean delivered, String input) { this.delivered = delivered; this.input = input; }
        @Override public String id() { return CASE; }
        @Override public String cellId() { return CELL; }
        @Override public String generationId() { return CASE; }
        @Override public Object deliveredContent() {
            return delivered ? List.of(Map.of("question", "Which structure surrounds cells?",
                    "choices", List.of("membrane", "nucleus"), "correctIndex", 0,
                    "explanation", "Membranes bound cells.", "questionFormat", "MCQ")) : null;
        }
        @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                                                        ArmAssertingInterceptor path) {
            client.post().uri("/responses").body(Map.of("model", MODEL, "input", input))
                    .retrieve().body(String.class);
            return new StageRunner.CallResult(delivered, Set.of(), delivered ? null : FAILURE);
        }
    }

    private record Original(Stage1Receipt receipt, Path output) {}
    private Original original(StageCapLedger ledger, ClientHttpRequestFactory transport) throws Exception {
        StageRunner.RunSummary run = StageRunner.run(input(temp.resolve("original-run")), transport,
                List.of(new SyntheticCall(false, INPUT)), ledger);
        Path path = temp.resolve("original-run/delivered-outputs.jsonl");
        DeliveredOutputs.Written written = DeliveredOutputs.write(path, ARM, 0, run.outcomes(),
                Map.of(CASE, WORKFLOW), Map.of(CASE, HASH), run.deliveredByCase());
        Stage1Receipt receipt = Stage1Receipt.fromRun(freeze(), ARM, MODEL,
                StageCapLedger.Stage.STAGE_1, Instant.now(), HASH, Map.of(CASE, HASH),
                Map.of(CASE, WORKFLOW), run, written);
        return new Original(receipt, path);
    }

    @Test void executesBothRechecksEvenAfterFirstSuccessAndKeepsIndexOrder() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory stub = transport(dispatches);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, stub);
        AtomicInteger construction = new AtomicInteger();
        Supplier<List<StageRunner.CaseCall>> calls = () -> List.of(
                new SyntheticCall(construction.incrementAndGet() == 2, INPUT));
        Path outputRoot = temp.resolve("rechecks");
        RecheckReceipt rechecks = RecheckRunner.runOffline(input(temp.resolve("unused")), calls,
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, new BigDecimal("0.002")), outputRoot, stub, freeze());
        assertThat(dispatches.get()).isEqualTo(3);
        assertThat(rechecks.cases()).hasSize(1);
        assertThat(rechecks.cases().getFirst().repeats())
                .extracting(RecheckReceipt.Repeat::recheckIndex).containsExactly(1, 2);
        assertThat(rechecks.cases().getFirst().repeats())
                .extracting(RecheckReceipt.Repeat::evidence)
                .containsExactly(RouteVerdictCalculator.Evidence.SCORED,
                        RouteVerdictCalculator.Evidence.UNSCORABLE);
        assertThat(RecheckReceipt.read(outputRoot.resolve("recheck-receipt.json")))
                .isEqualTo(rechecks);
        assertThat(Files.readString(outputRoot.resolve("recheck-receipt.json")))
                .doesNotContain(INPUT, "Which structure surrounds cells?", KEY);
        assertThat(ledger.attempts(StageCapLedger.Stage.STAGE_1)).isEqualTo(4);
        assertThat(ClassABlindPacketExport.loadScored(original.receipt(), original.output(),
                rechecks, outputRoot)).hasSize(1);
    }

    @Test void changedAttemptOneInputIsProtocolDeviationAndNotCounted() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory stub = transport(dispatches);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, stub);
        AtomicInteger construction = new AtomicInteger();
        Supplier<List<StageRunner.CaseCall>> calls = () -> List.of(
                new SyntheticCall(true, construction.incrementAndGet() == 3 ? "different input" : INPUT));
        RecheckReceipt rechecks = RecheckRunner.runOffline(input(temp.resolve("unused")), calls,
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, new BigDecimal("0.002")), temp.resolve("rechecks"), stub, freeze());
        assertThat(dispatches.get()).isEqualTo(3);
        assertThat(rechecks.cases().getFirst().repeats().get(1).evidence())
                .isEqualTo(RouteVerdictCalculator.Evidence.MISSING);
        assertThat(rechecks.cases().getFirst().repeats().get(1).protocolDeviation())
                .contains(CASE, "call index 0");
    }

    @Test void stoppedOrExhaustedLedgerRefusesBeforeDispatch() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory stub = transport(dispatches);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, stub);
        int before = dispatches.get();
        Supplier<List<StageRunner.CaseCall>> calls = () -> List.of(new SyntheticCall(true, INPUT));
        assertThatThrownBy(() -> RecheckRunner.runOffline(input(temp.resolve("unused")), calls,
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, BigDecimal.TEN), temp.resolve("rechecks"), stub, freeze()))
                .isInstanceOf(StageCapLedger.SpendStop.class);
        assertThat(dispatches.get()).isEqualTo(before);
    }

    @Test void realRecheckWithoutVerifiedFreezeRefusesBeforeAnyDispatch() {
        StageRunner.RunInput input = input(temp.resolve("unused"));
        var config = new Stage1RunPlan.Config(input, ARM, null, null, Map.of(), null,
                false, List.of(), temp.resolve("receipt.json"), null);
        Stage1RunPlan plan = new Stage1RunPlan(config);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        assertThatThrownBy(() -> RecheckRunner.run(plan, null, null, ledger,
                Map.of(), temp.resolve("rechecks")))
                .isInstanceOf(FreezeRecord.InvalidFreeze.class);
        assertThat(ledger.attempts(StageCapLedger.Stage.STAGE_1)).isZero();
    }
}
