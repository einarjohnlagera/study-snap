package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.ChallengeQuizStartRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.util.List;
import java.util.UUID;

/** Direct Board Exam public start path for preregistration sections 3 and 7. */
final class BoardExamDirectAdapter extends AbstractClassAAdapter {
    BoardExamDirectAdapter(ClassACaseManifest.Case item) {
        super(item, ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT);
    }

    @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
        harness.prepareBoardExam();
        List<String> extra = java.util.stream.IntStream.range(1, item.sources().size())
                .mapToObj(index -> harness.pack(index).getId().toString()).toList();
        var response = harness.service(ChallengeQuizService.class).startSession(
                harness.pack(0).getId().toString(), harness.userId,
                new ChallengeQuizStartRequest(ChallengeQuizService.MODE_BOARD_EXAM, extra, null));
        QuickReviewSessionEntity saved = harness.session(UUID.fromString(response.sessionId()));
        return saved == null ? List.of() : QuizSessionStateUtils.extractQuiz(saved.getSessionState());
    }
}
