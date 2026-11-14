package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sections 5, 7 and 10 paired-body and content-free receipt mutants. */
class Stage1ReceiptTest {
    private static final String CASE = "synthetic-case";
    private static final String HASH = "a".repeat(64);
    private static final String FREEZE = "b".repeat(64);
    private static final String HEAD = "c".repeat(40);
    private static final String CASE_SET = "d".repeat(64);
    private static final String MINI = "gpt-4.1-mini";
    private static final String LUNA = "gpt-6-luna";
    private static final String BASELINE_MINI = "BASELINE_MINI";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String LUNA_ARM = "LUNA";
    private static final String CALL_INDEX_ZERO = "call index 0";
    private static final String FAKE_KEY = "fake-secret-key";
    private static final String CELL = "synthetic-cell";
    private static final String NOTE = "note";
    private static final String BASE_BODY = "{\"model\":\"gpt-4.1-mini\",\"input\":\"note\"}";
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();

    private Stage1Receipt receipt(String arm, String model, String bodyHash) {
        Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(CASE, WORKFLOW, HASH,
                CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ONE, BigDecimal.ONE,
                List.of(new Stage1Receipt.Signal("OK", 1, 4)), List.of(bodyHash));
        return new Stage1Receipt(HEAD, FREEZE, arm, model, StageCapLedger.Stage.STAGE_1,
                Instant.now(), Instant.now(), 3, false, CASE_SET, List.of(row), Map.of());
    }

    @Test void modelFieldAloneDoesNotChangeForwardedInputHash() throws Exception {
        String baseline = ArmAssertingInterceptor.modelRemovedDigest(
                json.readTree(BASE_BODY));
        String candidate = ArmAssertingInterceptor.modelRemovedDigest(
                json.readTree("{\"model\":\"gpt-6-luna\",\"input\":\"note\"}"));
        assertThat(baseline).isEqualTo(candidate);
        assertThat(Stage1Receipt.compare(receipt(BASELINE_MINI, MINI, baseline),
                receipt(LUNA_ARM, LUNA, candidate)).getFirst().result())
                .isEqualTo(Stage1Receipt.Comparison.MATCH);
    }

    @Test void oneCharacterInputDifferenceIsInconclusiveAndNamesCallIndex() throws Exception {
        String baseline = ArmAssertingInterceptor.modelRemovedDigest(
                json.readTree(BASE_BODY));
        String candidate = ArmAssertingInterceptor.modelRemovedDigest(
                json.readTree("{\"model\":\"gpt-6-luna\",\"input\":\"notf\"}"));
        Stage1Receipt.CaseComparison result = Stage1Receipt.compare(
                receipt(BASELINE_MINI, MINI, baseline), receipt(LUNA_ARM, LUNA, candidate)).getFirst();
        assertThat(result.result()).isEqualTo(Stage1Receipt.Comparison.INCONCLUSIVE);
        assertThat(result.deviation()).isInstanceOf(Stage1Receipt.ProtocolDeviation.class)
                .hasMessageContaining(CASE).hasMessageContaining(CALL_INDEX_ZERO);
    }

    @Test void stubbedForwardedRequestsCompareAfterRemovingOnlyModel() throws Exception {
        Stage1Receipt baseline = forwarded(MINI, NOTE, "baseline");
        Stage1Receipt modelOnly = forwarded(LUNA, NOTE, "candidate-same");
        Stage1Receipt oneCharacter = forwarded(LUNA, "notf", "candidate-different");
        assertThat(Stage1Receipt.compare(baseline, modelOnly).getFirst().result())
                .isEqualTo(Stage1Receipt.Comparison.MATCH);
        Stage1Receipt.CaseComparison mismatch = Stage1Receipt.compare(baseline, oneCharacter).getFirst();
        assertThat(mismatch.result()).isEqualTo(Stage1Receipt.Comparison.INCONCLUSIVE);
        assertThat(mismatch.deviation()).hasMessageContaining(CASE).hasMessageContaining(CALL_INDEX_ZERO);
    }

    private Stage1Receipt forwarded(String model, String inputText, String folder) throws Exception {
        var input = new StageRunner.RunInput(model, FAKE_KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN),
                Map.of(CELL, new ForecastReservation(1_000, 1_000,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
                BigDecimal.ONE, BigDecimal.ONE, null, temp.resolve(folder));
        var factory = (org.springframework.http.client.ClientHttpRequestFactory) (uri, method) ->
                new MockClientHttpRequest(method, uri) {
                    @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        String response = "{\"output_text\":\"ok\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
                        return new MockClientHttpResponse(response.getBytes(java.nio.charset.StandardCharsets.UTF_8), 200);
                    }
                };
        StageRunner.CaseCall call = new StageRunner.CaseCall() {
            @Override public String id() { return CASE; }
            @Override public String cellId() { return CELL; }
            @Override public String generationId() { return CASE; }
            @Override public StageRunner.CallResult execute(org.springframework.web.client.RestClient client,
                    com.studysnap.backend.config.StudySnapProperties properties,
                    ArmAssertingInterceptor path) {
                client.post().uri("/responses").body(Map.of("model", model, "input", inputText))
                        .retrieve().toBodilessEntity();
                return new StageRunner.CallResult(true, Set.of(), null);
            }
        };
        StageRunner.RunSummary run = StageRunner.run(input, factory, List.of(call));
        var freeze = new FreezeRecord(HEAD, HASH, HASH, Map.of(), Map.of(), Map.of(), Map.of(),
                java.time.LocalDate.of(2026, 11, 14));
        return Stage1Receipt.fromRun(FreezeGuard.dryEvidence(freeze, FREEZE, HEAD), BASELINE_MINI,
                model, StageCapLedger.Stage.STAGE_1, Instant.now(), CASE_SET,
                Map.of(CASE, HASH), Map.of(CASE, WORKFLOW), run);
    }

    @Test void missingAttemptOneHashCannotPass() {
        Stage1Receipt baseline = receipt(BASELINE_MINI, MINI, HASH);
        Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(CASE, WORKFLOW, HASH,
                CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ONE, BigDecimal.ONE,
                List.of(), List.of());
        Stage1Receipt candidate = new Stage1Receipt(HEAD, FREEZE, LUNA_ARM, LUNA,
                StageCapLedger.Stage.STAGE_1, Instant.now(), Instant.now(), 3, false,
                CASE_SET, List.of(row), Map.of());
        assertThat(Stage1Receipt.compare(baseline, candidate).getFirst().result())
                .isEqualTo(Stage1Receipt.Comparison.INCONCLUSIVE);
    }

    @Test void lunaNeedsCompleteMatchingBaselineCoverage() {
        Stage1Receipt baseline = receipt(BASELINE_MINI, MINI, HASH);
        var freeze = new FreezeRecord(HEAD, HASH, HASH, Map.of(), Map.of(),
                Map.of(BASELINE_MINI, new FreezeRecord.Arm(MINI, List.of(WORKFLOW))),
                Map.of("INTERACTIVE_QUIZ", MINI),
                java.time.LocalDate.of(2026, 11, 14));
        List<Stage1Receipt> none = List.of();
        List<Stage1Receipt> one = List.of(baseline);
        Map<String, String> hashes = Map.of(CASE, HASH);
        assertThatThrownBy(() -> Stage1Receipt.requireCompleteBaselines(none, CASE_SET,
                hashes, freeze, FREEZE, HEAD))
                .isInstanceOf(Stage1Receipt.ProtocolDeviation.class);
        assertThatThrownBy(() -> Stage1Receipt.requireCompleteBaselines(one, "wrong",
                hashes, freeze, FREEZE, HEAD))
                .isInstanceOf(Stage1Receipt.ProtocolDeviation.class);
        Stage1Receipt.requireCompleteBaselines(one, CASE_SET, hashes, freeze, FREEZE, HEAD);
    }

    @Test void receiptContainsRequiredProvenanceAndNoPrivateMarkers() throws Exception {
        String privateBody = "{\"model\":\"gpt-4.1-mini\",\"input\":\"request-body-marker private-note-marker fake-secret-key\"}";
        String normalized = ArmAssertingInterceptor.modelRemovedDigest(json.readTree(privateBody));
        MeteringInterceptor.Call raw = new MeteringInterceptor.Call(CASE, WORKFLOW + ":synthetic",
                1, 4, 200, json.readTree("{\"input_tokens\":1,\"output_tokens\":1}"),
                0, List.of(), 0, true, 1, 1, 0, BigDecimal.ONE, BigDecimal.ONE,
                FAKE_KEY + " private-note-marker request-body-marker", false);
        var call = new ArmAssertingInterceptor.CallReceipt(raw, 1,
                ArmAssertingInterceptor.digest(privateBody.getBytes(java.nio.charset.StandardCharsets.UTF_8)), normalized);
        var attempt = new AttemptOutcome(CASE, 1, AttemptOutcome.Kind.OK, false,
                BigDecimal.ONE, BigDecimal.ONE);
        var outcome = CaseArmOutcome.classify(CASE, List.of(attempt), true, false, null);
        var freeze = new FreezeRecord(HEAD, HASH, HASH, Map.of(), Map.of(), Map.of(), Map.of(),
                java.time.LocalDate.of(2026, 11, 14));
        var run = new StageRunner.RunSummary(List.of(outcome),
                new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN)),
                Map.of(CASE, List.of(call)));
        Stage1Receipt receipt = Stage1Receipt.fromRun(FreezeGuard.dryEvidence(freeze, FREEZE, HEAD),
                BASELINE_MINI, MINI, StageCapLedger.Stage.STAGE_1, Instant.now(), CASE_SET,
                Map.of(CASE, HASH), Map.of(CASE, WORKFLOW), run);
        Path file = temp.resolve("receipt.json");
        receipt.write(file);
        String written = Files.readString(file);
        assertThat(written).contains(HEAD, FREEZE, "ledgerRecordCount", "manifestRowSha256",
                "attempt1BodyHashes", "firstAttemptClean", "conservativeUsd", "billedUsd");
        assertThat(written).doesNotContain(FAKE_KEY, "request-body-marker", "private-note-marker");
        assertThat(Stage1Receipt.read(file)).isEqualTo(receipt);
    }

    @Test void receiptRejectsUnknownField() throws Exception {
        Path file = temp.resolve("unknown-receipt.json");
        receipt(BASELINE_MINI, MINI, HASH).write(file);
        String content = Files.readString(file);
        Files.writeString(file, content.substring(0, content.length() - 1) + ",\"unknown\":true}");
        assertThatThrownBy(() -> Stage1Receipt.read(file))
                .isInstanceOf(Stage1Receipt.ProtocolDeviation.class)
                .hasMessageContaining("malformed baseline receipt");
    }
}
