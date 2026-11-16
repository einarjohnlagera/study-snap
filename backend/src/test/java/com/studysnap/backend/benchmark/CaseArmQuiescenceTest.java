package com.studysnap.backend.benchmark;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.service.impl.OpenAiLlmStudyPackService;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Late worker isolation and bounded wait for preregistration sections 5 and 7. */
class CaseArmQuiescenceTest {
    private static final String MODEL = "gpt-6-luna";
    private static final String KEY = "offline-fake-key";
    private static final String BIOLOGY = "Biology";
    private static final String CELL = "LONG_EXAM:synthetic";
    private static final String SECOND = "second";
    private static final String SOURCE_TEXT = "Cells have membranes.";
    private static final String SIGNAL = "action=llm_call operation=note_lib_long_exam route=INTERACTIVE_QUIZ "
            + "model=gpt-6-luna attempt=1 outcome=SUCCESS";
    @TempDir Path tempDir;

    @Test void waitsForLateTaggedTaskBeforeNextCaseBegins() throws Exception {
        CountDownLatch lateLog = new CountDownLatch(1);
        AbstractClassAAdapter first = syntheticAdapter("first", Duration.ofSeconds(2), () -> {
            sleep(200);
            LoggerFactory.getLogger(OpenAiLlmStudyPackService.class).info(SIGNAL);
            lateLog.countDown();
        });
        StageRunner.CaseCall second = new StageRunner.CaseCall() {
            @Override public String id() { return SECOND; }
            @Override public String cellId() { return CELL; }
            @Override public String generationId() { return SECOND; }
            @Override public StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
                    ArmAssertingInterceptor path) throws Exception {
                try (var signals = new ProductionCallSignals()) {
                    if (!lateLog.await(2, TimeUnit.SECONDS))
                        return new StageRunner.CallResult(false, Set.of(), "LATE_TASK_MISSING");
                    return new StageRunner.CallResult(signals.signals().isEmpty(), Set.of(), null);
                }
            }
        };
        var outcomes = run(List.of(first, second));
        assertThat(outcomes.get(1).state()).isEqualTo(CaseArmOutcome.State.COMPLETE);
    }

    @Test void nonQuiescentWorkerMakesCaseArmInconclusiveWithNamedReason() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        AbstractClassAAdapter adapter = syntheticAdapter("blocked", Duration.ofMillis(30), () -> {
            try { hold.await(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
        var outcome = run(List.of(adapter)).getFirst();
        hold.countDown();
        assertThat(outcome.state()).isEqualTo(CaseArmOutcome.State.INCONCLUSIVE);
        assertThat(outcome.failureClass()).isEqualTo("INCONCLUSIVE_NON_QUIESCENT_BATCH");
    }

    private AbstractClassAAdapter syntheticAdapter(String id, Duration bound, Runnable task) {
        var source = new ClassACaseManifest.Source("Cells", SOURCE_TEXT,
                SOURCE_TEXT, List.of("Membrane"), List.of(), BIOLOGY, BIOLOGY,
                null, LearnerLevel.COLLEGE);
        var item = new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.LONG_EXAM,
                "synthetic", LearnerLevel.COLLEGE, null, BIOLOGY, false,
                List.of(source), 25, null, false, ClassACaseManifest.Subgroup.DIRECT);
        return new AbstractClassAAdapter(item, ClassACaseManifest.Workflow.LONG_EXAM) {
            @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
                harness.installBatchExecutor(1);
                harness.batchExecutor().execute(task);
                return harness.pack(0).getQuiz();
            }
            @Override protected Duration quiescenceBound() { return bound; }
            @Override protected boolean requestCountsMatch(List<ArmAssertingInterceptor.CallReceipt> receipts,
                    StageRunner.CallResult result) { return true; }
        };
    }

    private List<CaseArmOutcome> run(List<StageRunner.CaseCall> cases) throws Exception {
        StageRunner.RunInput input = new StageRunner.RunInput(MODEL, KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN),
                Map.of(CELL, new ForecastReservation(5_000, 5_000,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
                BigDecimal.ONE, BigDecimal.ONE, null, tempDir);
        return StageRunner.run(input, (uri, method) -> new MockClientHttpRequest(method, uri), cases).outcomes();
    }

    private static void sleep(long millis) {
        long deadline = System.nanoTime() + Duration.ofMillis(millis).toNanos();
        boolean interrupted = false;
        while (System.nanoTime() < deadline) {
            try { Thread.sleep(10); }
            catch (InterruptedException exception) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
