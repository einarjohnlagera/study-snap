package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.InterviewPracticeStartRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.service.InterviewPracticeService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.util.List;
import java.util.UUID;

/** Interview generation public start path for preregistration sections 3 and 7. */
final class InterviewPracticeAdapter extends AbstractClassAAdapter {
    InterviewPracticeAdapter(ClassACaseManifest.Case item) {
        super(item, ClassACaseManifest.Workflow.INTERVIEW_PRACTICE);
    }

    @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
        List<UUID> extra = java.util.stream.IntStream.range(1, item.sources().size())
                .mapToObj(index -> harness.note(index).getId()).toList();
        var response = harness.service(InterviewPracticeService.class).startSession(harness.userId,
                new InterviewPracticeStartRequest(harness.note(0).getId(),
                        item.requestedQuestionCount(), extra));
        QuickReviewSessionEntity saved = harness.session(response.sessionId());
        return saved == null ? List.of() : QuizSessionStateUtils.extractQuiz(saved.getSessionState());
    }
}
