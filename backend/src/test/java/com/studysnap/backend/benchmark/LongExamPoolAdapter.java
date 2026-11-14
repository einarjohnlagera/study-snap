package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.ExamQuestionPoolService;
import java.util.List;
import java.util.UUID;

/** Long Exam pool generation path for preregistration sections 3, 5, 6 and 7. */
final class LongExamPoolAdapter extends AbstractClassAAdapter {
    private final int workers;

    LongExamPoolAdapter(ClassACaseManifest.Case item) { this(item, 2); }
    LongExamPoolAdapter(ClassACaseManifest.Case item, int workers) {
        super(item, ClassACaseManifest.Workflow.LONG_EXAM);
        if (item.subgroup() != ClassACaseManifest.Subgroup.LONG_EXAM_POOL)
            throw new IllegalArgumentException("Wrong Long Exam subgroup");
        this.workers = workers;
    }

    @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
        harness.installBatchExecutor(workers);
        UUID poolId = harness.preparePool(ExamQuestionPoolService.MODE_LONG_EXAM);
        harness.service(ExamQuestionPoolService.class).generatePoolAsync(poolId);
        return harness.persistedPoolQuestions(poolId);
    }

    @Override protected boolean requestCountsMatch(List<ArmAssertingInterceptor.CallReceipt> receipts,
            StageRunner.CallResult result) {
        int count = harness().properties.getPricing().getLongExamPoolSize();
        if (item.requestedQuestionCount() != count || harness().batchExecutor().submissions() != 2) return false;
        int batchCount = (count + 1) / 2 + 2;
        for (int index = 0; index < receipts.size(); index++)
            if (result.signalAttempts().get(index) == 1
                    && result.callGroupIds().get(index).startsWith(item.caseId() + "-batch-")
                    && receipts.get(index).questionCount() != batchCount) return false;
        return true;
    }
}
