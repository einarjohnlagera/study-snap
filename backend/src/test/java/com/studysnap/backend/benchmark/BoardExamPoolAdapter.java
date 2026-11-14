package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.ExamQuestionPoolService;
import java.util.List;
import java.util.UUID;

/** Board Exam pool generation path for preregistration sections 3, 5, 6 and 7. */
final class BoardExamPoolAdapter extends AbstractClassAAdapter {
    BoardExamPoolAdapter(ClassACaseManifest.Case item) {
        super(item, ClassACaseManifest.Workflow.LONG_EXAM);
        if (item.subgroup() != ClassACaseManifest.Subgroup.BOARD_EXAM_POOL)
            throw new IllegalArgumentException("Wrong Long Exam subgroup");
    }

    @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
        UUID poolId = harness.preparePool(ExamQuestionPoolService.MODE_BOARD_EXAM);
        harness.service(ExamQuestionPoolService.class).generatePoolAsync(poolId);
        return harness.persistedPoolQuestions(poolId);
    }

    @Override protected boolean requestCountsMatch(List<ArmAssertingInterceptor.CallReceipt> receipts,
            StageRunner.CallResult result) {
        if (item.requestedQuestionCount() != harness().properties.getPricing().getBoardExamPoolSize()) return false;
        int firstAttempts = 0;
        for (int index = 0; index < receipts.size(); index++) {
            if (result.signalAttempts().get(index) != 1) continue;
            firstAttempts++;
            if (receipts.get(index).questionCount() != 12) return false;
        }
        return firstAttempts == 2;
    }
}
