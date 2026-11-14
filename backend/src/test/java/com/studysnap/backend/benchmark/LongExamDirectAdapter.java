package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.LongExamStartRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.service.LongExamService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.util.List;

/** Direct Long Exam public path for preregistration sections 3, 5, 6 and 7. */
final class LongExamDirectAdapter extends AbstractClassAAdapter {
    private final int workers;

    LongExamDirectAdapter(ClassACaseManifest.Case item) { this(item, 2); }
    LongExamDirectAdapter(ClassACaseManifest.Case item, int workers) {
        super(item, ClassACaseManifest.Workflow.LONG_EXAM);
        if (item.subgroup() != ClassACaseManifest.Subgroup.DIRECT)
            throw new IllegalArgumentException("Wrong Long Exam subgroup");
        this.workers = workers;
    }

    @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
        harness.prepareLongExamDirect();
        harness.installBatchExecutor(workers);
        List<String> extra = java.util.stream.IntStream.range(1, item.sources().size())
                .mapToObj(index -> harness.pack(index).getId().toString()).toList();
        var response = harness.service(LongExamService.class).startSession(
                harness.pack(0).getId().toString(), harness.userId,
                new LongExamStartRequest("mixed", extra));
        QuickReviewSessionEntity saved = harness.session(response.sessionId());
        return saved == null ? List.of() : QuizSessionStateUtils.extractQuiz(saved.getSessionState());
    }

    @Override protected boolean requestCountsMatch(List<ArmAssertingInterceptor.CallReceipt> receipts,
            StageRunner.CallResult result) {
        int total = harness().properties.getPricing().resolveLongExamQuestionCount(item.noteLevel());
        if (total != item.requestedQuestionCount()) return false;
        for (int index = 0; index < receipts.size(); index++) {
            if (result.signalAttempts().get(index) != 1) continue;
            String group = result.callGroupIds().get(index);
            if (!group.startsWith(item.caseId() + "-batch-")) continue;
            int batch = Integer.parseInt(group.substring(group.lastIndexOf('-') + 1));
            int source = (batch - 1) / 2;
            if (source >= item.sources().size()) return false;
            int perSource = total / item.sources().size() + (source == 0 ? total % item.sources().size() : 0);
            if (receipts.get(index).questionCount() != (perSource + 1) / 2 + 2) return false;
        }
        return harness().batchExecutor().submissions() == 2 * item.sources().size();
    }
}
