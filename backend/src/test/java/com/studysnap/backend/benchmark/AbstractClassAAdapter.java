package com.studysnap.backend.benchmark;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import java.time.Duration;
import java.util.List;
import org.springframework.web.client.RestClient;

/** Production caller bridge and signal receipt for preregistration sections 3 and 7. */
abstract class AbstractClassAAdapter implements StageRunner.CaseCall {
    private static final String NON_QUIESCENT = "INCONCLUSIVE_NON_QUIESCENT_BATCH";
    protected final ClassACaseManifest.Case item;
    private List<QuizItem> deliveredQuestions = List.of();
    private List<ProductionCallSignals.Signal> signals = List.of();
    private List<String> requestBodyDigests = List.of();
    private ClassAServiceHarness harness;

    AbstractClassAAdapter(ClassACaseManifest.Case item, ClassACaseManifest.Workflow workflow) {
        if (item.workflow() != workflow) throw new IllegalArgumentException("Wrong class-A workflow");
        this.item = item;
    }

    @Override public String id() { return item.caseId(); }
    @Override public String cellId() { return item.workflow().name() + ":" + item.stratum(); }
    @Override public String generationId() { return item.caseId(); }
    List<QuizItem> deliveredQuestions() { return deliveredQuestions; }
    List<ProductionCallSignals.Signal> signals() { return signals; }
    List<String> requestBodyDigests() { return requestBodyDigests; }
    ClassAServiceHarness harness() { return harness; }

    @Override public final StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
            ArmAssertingInterceptor path) {
        harness = new ClassAServiceHarness(item, client, properties);
        int before = path.receipts().size();
        String failureClass = null;
        try (ProductionCallSignals production = new ProductionCallSignals()) {
            try {
                deliveredQuestions = List.copyOf(invoke(harness));
            } catch (RuntimeException exception) {
                failureClass = exception.getClass().getSimpleName();
            }
            BatchTaggingExecutor executor = harness.batchExecutor();
            if (executor != null) {
                if (!executor.awaitQuiescence(quiescenceBound())) {
                    failureClass = NON_QUIESCENT;
                    path.stopRun(failureClass);
                }
                executor.close();
            }
            signals = production.signals();
            List<ArmAssertingInterceptor.CallReceipt> receipts = path.receipts()
                    .subList(before, path.receipts().size());
            requestBodyDigests = receipts.stream().map(ArmAssertingInterceptor.CallReceipt::bodyDigest).toList();
            if (NON_QUIESCENT.equals(failureClass))
                return new StageRunner.CallResult(false, java.util.Set.of(), failureClass);
            StageRunner.CallResult result = production.result(generationId(),
                    failureClass == null && !deliveredQuestions.isEmpty(), failureClass, receipts);
            if ((result.failureClass() == null || !result.failureClass().startsWith("INCONCLUSIVE_"))
                    && !requestCountsMatch(receipts, result))
                return production.result(generationId(), false, "INPUT_COUNT_MISMATCH", receipts);
            return result;
        }
    }

    protected Duration quiescenceBound() { return Duration.ofSeconds(10); }

    protected boolean requestCountsMatch(List<ArmAssertingInterceptor.CallReceipt> receipts,
            StageRunner.CallResult result) {
        int requested = 0;
        for (int index = 0; index < receipts.size(); index++)
            if (result.signalAttempts().get(index) == 1) requested += receipts.get(index).questionCount();
        return requested == item.requestedQuestionCount();
    }

    protected abstract List<QuizItem> invoke(ClassAServiceHarness harness);
}
