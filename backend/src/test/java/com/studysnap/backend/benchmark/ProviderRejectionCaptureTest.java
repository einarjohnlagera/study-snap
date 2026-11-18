package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRejectionCaptureTest {
    private static final String CASE = "rejected-case";
    private static final String CELL = "NOTE_ADAPTIVE:synthetic";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String MODEL = "gpt-6-luna";
    private static final String ARM = "LUNA";
    private static final String HASH = "a".repeat(64);
    private static final String HEAD = "b".repeat(40);
    private static final String MARKER = "MARKER_SECRET_PROMPT_ECHO";
    private static final String RECHECKS = "rechecks";
    private static final String RESERVATION = "0.002";
    private static final String SHA256_PREFIX = "sha256:";
    private static final String ERROR_CODE = "C";
    private static final String ERROR_TYPE = "T";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @TempDir Path temp;
    private int originalIndex;

    private record Original(Stage1Receipt receipt, Path output) {}

    private static final class Call implements StageRunner.CaseCall {
        @Override public String id() { return CASE; }
        @Override public String cellId() { return CELL; }
        @Override public String generationId() { return CASE; }
        @Override public Object deliveredContent() { return null; }
        @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                                                          ArmAssertingInterceptor path) {
            client.post().uri("/responses").body(Map.of("model", MODEL, "input", "cells have membranes"))
                    .retrieve().body(String.class);
            return new StageRunner.CallResult(false, Set.of(), "HTTP_ERROR");
        }
    }

    private static FreezeGuard.Verified freeze() {
        FreezeRecord record = new FreezeRecord(HEAD, HASH, HASH, Map.of(), Map.of(),
                Map.of(ARM, new FreezeRecord.Arm(MODEL, List.of(WORKFLOW))),
                Map.of(WORKFLOW, MODEL), LocalDate.of(2026, 11, 18),
                Set.of(RecheckPlan.PROVIDER_REJECTION));
        return FreezeGuard.dryEvidence(record, HASH, HEAD);
    }

    private StageRunner.RunInput input() {
        return new StageRunner.RunInput(MODEL, "fake-key", StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE),
                Map.of(CELL, new ForecastReservation(1_000, 1_000, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE)), BigDecimal.ONE, BigDecimal.ONE,
                null, temp.resolve("unused"));
    }

    private static ClientHttpRequestFactory stub(AtomicInteger dispatches, String... bodies) {
        return stub(dispatches, 400, bodies);
    }

    private static ClientHttpRequestFactory stub(AtomicInteger dispatches, int status, String... bodies) {
        return (uri, method) -> new MockClientHttpRequest(method, uri) {
            @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                String body = bodies[dispatches.getAndIncrement()];
                return new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
            }
        };
    }

    private Original original(StageCapLedger ledger, ClientHttpRequestFactory transport) throws Exception {
        StageRunner.RunSummary run = StageRunner.run(input(), transport, List.of(new Call()), ledger);
        Path path = temp.resolve("original-" + ++originalIndex + "/delivered-outputs.jsonl");
        Files.createDirectories(path.getParent());
        DeliveredOutputs.Written written = DeliveredOutputs.write(path, ARM, 0, run.outcomes(),
                Map.of(CASE, WORKFLOW), Map.of(CASE, HASH), run.deliveredByCase());
        Stage1Receipt receipt = Stage1Receipt.fromRun(freeze(), ARM, MODEL, StageCapLedger.Stage.STAGE_1,
                Instant.now(), HASH, Map.of(CASE, HASH), Map.of(CASE, WORKFLOW), run, written);
        return new Original(receipt, path);
    }

    @Test void capturesCodeTypeStatusDigestWithoutPersistingMessage() throws Exception {
        String body = "{\"error\":{\"code\":\"C\",\"type\":\"T\",\"message\":\"" + MARKER + "\"}}";
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = stub(dispatches, body, body, body);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, transport);
        assertThat(original.receipt().cases().getFirst().llmCalls().getFirst().errorCode()).isEqualTo(ERROR_CODE);
        assertThat(original.receipt().cases().getFirst().llmCalls().getFirst().errorType()).isEqualTo(ERROR_TYPE);
        RecheckReceipt receipt = RecheckRunner.runOffline(input(), () -> List.of(new Call()),
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, new BigDecimal(RESERVATION)), temp.resolve(RECHECKS), transport, freeze());
        RecheckReceipt.RejectedCall rejected = receipt.cases().getFirst().repeats().getFirst()
                .rejectedCalls().getFirst();
        assertThat(rejected.status()).isEqualTo(400);
        assertThat(rejected.errorCode()).isEqualTo(ERROR_CODE);
        assertThat(rejected.errorType()).isEqualTo(ERROR_TYPE);
        assertThat(rejected.modelRemovedDigest())
                .isEqualTo(original.receipt().cases().getFirst().llmCalls().getFirst().modelRemovedDigest());
        assertThat(JSON.writeValueAsString(original.receipt())).doesNotContain(MARKER);
        assertThat(JSON.writeValueAsString(receipt)).doesNotContain(MARKER);
        try (var files = Files.walk(temp.resolve(RECHECKS))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (file.toString().contains("/raw/")) continue;
                assertThat(Files.readString(file)).doesNotContain(MARKER);
            }
        }
    }

    @Test void errorObjectAbsenceAndNullFieldsHaveDifferentEvidence() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = stub(dispatches, "not-json", "{\"error\":{\"code\":null}}",
                "{\"error\":{\"code\":\"bad code\",\"type\":\"T\"}}");
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, transport);
        Stage1Receipt.Signal signal = original.receipt().cases().getFirst().llmCalls().getFirst();
        assertThat(signal.errorCode()).isNull();
        assertThat(signal.errorType()).isNull();
        RecheckReceipt receipt = RecheckRunner.runOffline(input(), () -> List.of(new Call()),
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, new BigDecimal(RESERVATION)), temp.resolve(RECHECKS), transport, freeze());
        assertThat(receipt.cases().getFirst().repeats().getFirst().rejectedCalls().getFirst().errorCode())
                .isEmpty();
        assertThat(receipt.cases().getFirst().repeats().getFirst().rejectedCalls().getFirst().errorType())
                .isEmpty();
        assertThat(receipt.cases().getFirst().repeats().get(1).rejectedCalls().getFirst().errorCode())
                .startsWith(SHA256_PREFIX).doesNotContain("bad code");
    }

    @Test void differentUnparseableCodesHaveDifferentHashes() throws Exception {
        String originalBody = "{\"error\":{\"code\":\"bad code one\",\"type\":\"T\"}}";
        String otherBody = "{\"error\":{\"code\":\"bad code two\",\"type\":\"T\"}}";
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = stub(dispatches, originalBody, otherBody, otherBody);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, transport);
        RecheckReceipt receipt = RecheckRunner.runOffline(input(), () -> List.of(new Call()),
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, new BigDecimal(RESERVATION)), temp.resolve(RECHECKS), transport, freeze());
        String first = original.receipt().cases().getFirst().llmCalls().getFirst().errorCode();
        String second = receipt.cases().getFirst().repeats().getFirst().rejectedCalls().getFirst().errorCode();
        assertThat(first).startsWith(SHA256_PREFIX).doesNotContain("bad code one");
        assertThat(second).startsWith(SHA256_PREFIX).doesNotContain("bad code two").isNotEqualTo(first);
    }

    @Test void only400And422SignalsContainRejectedCallEvidence() throws Exception {
        String rejectedBody = "{\"error\":{\"code\":\"C\",\"type\":\"T\"}}";
        StageCapLedger okLedger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original ok = original(okLedger, stub(new AtomicInteger(), 200,
                "{\"error\":{\"code\":\"C\",\"type\":\"T\"},\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}"));
        assertThat(ok.receipt().cases().getFirst().llmCalls().getFirst().errorCode()).isNull();
        assertThat(ok.receipt().cases().getFirst().llmCalls().getFirst().modelRemovedDigest()).isNull();
        StageCapLedger rejectedLedger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original rejected = original(rejectedLedger, stub(new AtomicInteger(), 422, rejectedBody));
        assertThat(rejected.receipt().cases().getFirst().llmCalls().getFirst().errorCode()).isEqualTo(ERROR_CODE);
        assertThat(rejected.receipt().cases().getFirst().llmCalls().getFirst().modelRemovedDigest()).isNotNull();
    }

    @Test void originalModelMismatchRefusesBeforeReservationOrDispatch() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        String body = "{\"error\":{\"code\":\"C\",\"type\":\"T\"}}";
        ClientHttpRequestFactory transport = stub(dispatches, body);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, transport);
        Stage1Receipt prior = original.receipt();
        Stage1Receipt wrongModel = new Stage1Receipt(prior.head(), prior.freezeSha256(), prior.arm(), "other",
                prior.stage(), prior.startedAt(), prior.endedAt(), prior.ledgerRecordCount(),
                prior.unreconciled(), prior.caseSetSha256(), prior.cases(), prior.triggers(),
                prior.deliveredOutputsFileSha256());
        int attempts = ledger.attempts(StageCapLedger.Stage.STAGE_1);
        StageRunner.RunInput input = input();
        Path output = temp.resolve("refused");
        Map<String, BigDecimal> reservations = Map.of(CASE, new BigDecimal(RESERVATION));
        assertThatThrownBy(() -> RecheckRunner.runOffline(input, () -> List.of(new Call()),
                Map.of(CASE, HASH), wrongModel, original.output(), ledger, reservations, output,
                transport, freeze())).isInstanceOf(RecheckRunner.InvalidRecheck.class);
        assertThat(dispatches.get()).isEqualTo(1);
        assertThat(ledger.attempts(StageCapLedger.Stage.STAGE_1)).isEqualTo(attempts);
        assertThat(Files.exists(output)).isFalse();
    }
    @Test void errorEvidenceIsCapturedOnlyForRejectionStatuses() throws Exception {
        String body = "{\"error\":{\"code\":\"C\",\"type\":\"T\"}}";
        for (int status : List.of(401, 429, 500)) {
            StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
            StageRunner.RunSummary run = StageRunner.run(input(), stub(new AtomicInteger(), status, body),
                    List.of(new Call()), ledger);
            var call = run.callsByCase().get(CASE).getFirst().call();
            assertThat(call.status()).isEqualTo(status);
            assertThat(call.errorCode()).isNull();
            assertThat(call.errorType()).isNull();
        }
    }

    @Test void nonRejectionRepeatsWriteNoRejectedCallsField() throws Exception {
        String rejected = "{\"error\":{\"code\":\"C\",\"type\":\"T\"}}";
        String ok = "{\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
        AtomicInteger dispatches = new AtomicInteger();
        ClientHttpRequestFactory transport = (uri, method) -> new MockClientHttpRequest(method, uri) {
            @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                boolean first = dispatches.getAndIncrement() == 0;
                return new MockClientHttpResponse((first ? rejected : ok).getBytes(StandardCharsets.UTF_8),
                        first ? 400 : 200);
            }
        };
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        Original original = original(ledger, transport);
        RecheckReceipt receipt = RecheckRunner.runOffline(input(), () -> List.of(new Call()),
                Map.of(CASE, HASH), original.receipt(), original.output(), ledger,
                Map.of(CASE, new BigDecimal(RESERVATION)), temp.resolve(RECHECKS), transport, freeze());
        for (RecheckReceipt.Repeat repeat : receipt.cases().getFirst().repeats()) {
            assertThat(repeat.providerRejected()).isFalse();
            assertThat(repeat.rejectedCalls()).isNull();
        }
        assertThat(JSON.writeValueAsString(receipt)).doesNotContain("rejectedCalls");
        assertThat(Files.readString(temp.resolve(RECHECKS).resolve("recheck-receipt.json")))
                .doesNotContain("rejectedCalls");
    }
}
