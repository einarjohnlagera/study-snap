package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.LearnerLevel;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline adapter execution with synthetic delivered lists and no HTTP request. */
class ShortDeliveryAdapterTest {
    private static final String CASE = "synthetic-short";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String ARM = "BASELINE_MINI";
    private static final String MODEL = "gpt-4.1-mini";
    private static final String HASH = "a".repeat(64);
    private static final String OFFLINE_TOKEN = "offline-fake-token";
    private static final int REQUESTED = 25;
    private static final int OVER_GENERATION_BUFFER = 2;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String SHORT_DELIVERY = "SHORT_DELIVERY";
    private static final String SUBJECT = "Biology";
    @TempDir Path temp;
    private StageRunner.CallResult lastCallResult;

    private static List<QuizItem> questions(int count) throws Exception {
        List<QuizItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) items.add(JSON.readValue(
                "{\"question\":\"Question " + index + "?\",\"choices\":[\"yes\",\"no\"],"
                        + "\"correctIndex\":0,\"explanation\":\"because\",\"questionFormat\":\"MCQ\"}",
                QuizItem.class));
        return List.copyOf(items);
    }

    private static ClassACaseManifest.Case manifest() {
        var source = new ClassACaseManifest.Source("Cells", "Synthetic cell source.", "Cell boundary.",
                List.of("membrane"), List.of("cells"), SUBJECT, SUBJECT, null, LearnerLevel.COLLEGE);
        return new ClassACaseManifest.Case(CASE, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                "synthetic", LearnerLevel.COLLEGE, null, SUBJECT, false,
                List.of(source), REQUESTED, null, false);
    }

    private StageRunner.RunSummary run(List<QuizItem> delivered, boolean failOperation,
                                       boolean stop) throws Exception {
        return run(delivered, failOperation, stop, false);
    }

    private StageRunner.RunSummary run(List<QuizItem> delivered, boolean failOperation,
                                       boolean stop, boolean inputMismatch) throws Exception {
        ClassACaseManifest.Case item = manifest();
        AbstractClassAAdapter adapter = new AbstractClassAAdapter(item, item.workflow()) {
            @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
                if (failOperation) throw new SyntheticOperationFailure();
                return delivered;
            }
            @Override protected boolean requestCountsMatch(List<ArmAssertingInterceptor.CallReceipt> receipts,
                    StageRunner.CallResult result) { return !inputMismatch; }
        };
        StageRunner.CaseCall call = stop ? new StageRunner.CaseCall() {
            @Override public String id() { return adapter.id(); }
            @Override public String cellId() { return adapter.cellId(); }
            @Override public String generationId() { return adapter.generationId(); }
            @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                    ArmAssertingInterceptor path) {
                path.stopRun("Synthetic ledger stop");
                return new StageRunner.CallResult(false, Set.of(), SHORT_DELIVERY,
                        List.of(), Set.of(), List.of(), List.of(), 24);
            }
        } : new StageRunner.CaseCall() {
            @Override public String id() { return adapter.id(); }
            @Override public String cellId() { return adapter.cellId(); }
            @Override public String generationId() { return adapter.generationId(); }
            @Override public Object deliveredContent() { return adapter.deliveredContent(); }
            @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                    ArmAssertingInterceptor path) {
                lastCallResult = adapter.execute(client, properties, path);
                return lastCallResult;
            }
        };
        ClientHttpRequestFactory transport = (uri, method) -> new MockClientHttpRequest(method, uri);
        StageRunner.RunInput input = new StageRunner.RunInput(MODEL, OFFLINE_TOKEN,
                StageCapLedger.Stage.STAGE_1, Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN),
                Map.of(call.cellId(), new ForecastReservation(1_000, 1_000,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
                BigDecimal.ONE, BigDecimal.ONE, null, temp.resolve("run-" + System.nanoTime()));
        return StageRunner.run(input, transport, List.of(call));
    }

    private static final class SyntheticOperationFailure extends RuntimeException {}

    private Stage1Receipt receipt(StageRunner.RunSummary run, DeliveredOutputs.Written written) {
        return Stage1Receipt.fromRun(FreezeGuard.dryEvidence(null, HASH, "b".repeat(40)), ARM, MODEL,
                StageCapLedger.Stage.STAGE_1, Instant.EPOCH, HASH, Map.of(CASE, HASH),
                Map.of(CASE, WORKFLOW), run, written);
    }

    @Test void smallAndSubstantialShortfallsCrossEveryHopWithoutDeliveredText() throws Exception {
        for (int count : List.of(24, 10)) {
            StageRunner.RunSummary summary = run(questions(count), false, false);
            CaseArmOutcome outcome = summary.outcomes().getFirst();
            assertThat(outcome.failureClass()).isEqualTo(SHORT_DELIVERY);
            assertThat(lastCallResult.deliveredQuestionCount()).isEqualTo(count);
            assertThat(outcome.deliveredQuestionCount()).isEqualTo(count);
            Path output = temp.resolve("short-" + count + ".jsonl");
            DeliveredOutputs.Written written = DeliveredOutputs.write(output, ARM, 0,
                    summary.outcomes(), Map.of(CASE, WORKFLOW), Map.of(CASE, HASH), summary.deliveredByCase());
            Stage1Receipt receipt = receipt(summary, written);
            assertThat(receipt.cases().getFirst().deliveredQuestionCount()).isEqualTo(count);
            assertThat(DeliveredOutputs.read(output, receipt).getFirst().content().isNull()).isTrue();
            assertThat(receipt.deliveredOutputsFileSha256()).isEqualTo(FreezeRecord.sha256(output));
            assertThat(Files.readString(output)).doesNotContain("Question 0?");
        }
    }

    @Test void trimmedOverDeliveryAndOtherFailuresCarryNoCount() throws Exception {
        List<QuizItem> generated = questions(REQUESTED + OVER_GENERATION_BUFFER);
        CaseArmOutcome trimmed = run(generated.subList(0, REQUESTED), false, false).outcomes().getFirst();
        assertThat(trimmed.state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
        assertThat(trimmed.deliveredQuestionCount()).isNull();
        assertThat(lastCallResult.deliveredQuestionCount()).isNull();
        QuizItem duplicate = questions(1).getFirst();
        CaseArmOutcome duplicates = run(List.of(duplicate, duplicate), false, false).outcomes().getFirst();
        assertThat(duplicates.failureClass()).isEqualTo("DUPLICATE_DELIVERY");
        assertThat(duplicates.deliveredQuestionCount()).isNull();
        assertThat(lastCallResult.deliveredQuestionCount()).isNull();
        CaseArmOutcome mismatched = run(questions(24), false, false, true).outcomes().getFirst();
        assertThat(mismatched.failureClass()).isEqualTo("INPUT_COUNT_MISMATCH");
        assertThat(mismatched.deliveredQuestionCount()).isNull();
        assertThat(lastCallResult.deliveredQuestionCount()).isNull();
        CaseArmOutcome exception = run(List.of(), true, false).outcomes().getFirst();
        assertThat(exception.failureClass()).isEqualTo("SyntheticOperationFailure");
        assertThat(exception.deliveredQuestionCount()).isNull();
        assertThat(lastCallResult.deliveredQuestionCount()).isNull();
        assertThat(AbstractClassAAdapter.structuralFailure(questions(REQUESTED), REQUESTED)).isNull();
    }

    @Test void stoppedLedgerDropsCountBeforeReceiptConstruction() throws Exception {
        StageRunner.RunSummary summary = run(List.of(), false, true);
        CaseArmOutcome outcome = summary.outcomes().getFirst();
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.INCONCLUSIVE);
        assertThat(outcome.deliveredQuestionCount()).isNull();
        assertThat(receipt(summary, new DeliveredOutputs.Written(null, null, Map.of()))
                .cases().getFirst().deliveredQuestionCount()).isNull();
    }

    @Test void countDoesNotChangeFailedDeliveredOutputBytesOrHash() throws Exception {
        CaseArmOutcome withCount = CaseArmOutcome.classify(CASE, List.of(), false, false,
                SHORT_DELIVERY, 24);
        CaseArmOutcome legacy = CaseArmOutcome.classify(CASE, List.of(), false, false,
                SHORT_DELIVERY);
        Path counted = temp.resolve("counted.jsonl");
        Path uncounted = temp.resolve("uncounted.jsonl");
        DeliveredOutputs.Written countedOutput = DeliveredOutputs.write(counted, ARM, 0,
                List.of(withCount), Map.of(CASE, WORKFLOW), Map.of(CASE, HASH), Map.of());
        DeliveredOutputs.Written legacyOutput = DeliveredOutputs.write(uncounted, ARM, 0,
                List.of(legacy), Map.of(CASE, WORKFLOW), Map.of(CASE, HASH), Map.of());
        assertThat(Files.readAllBytes(counted)).isEqualTo(Files.readAllBytes(uncounted));
        assertThat(countedOutput.fileSha256()).isEqualTo(legacyOutput.fileSha256());
        assertThat(countedOutput.lineSha256()).isEqualTo(legacyOutput.lineSha256());
    }
}
