package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.util.List;
import java.util.UUID;

/** Plan Adaptive public path for preregistration sections 3 and 7. */
final class PlanAdaptiveAdapter extends AbstractClassAAdapter {
    PlanAdaptiveAdapter(ClassACaseManifest.Case item) {
        super(item, ClassACaseManifest.Workflow.PLAN_ADAPTIVE);
    }

    @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
        UUID collectionId = harness.preparePlanAdaptive();
        harness.service(QuickReviewAdaptivePracticeService.class)
                .generateAdaptiveQuizForCollection(collectionId.toString(), harness.userId, null);
        // The public DTO redacts answer keys; read the production-persisted session state (see NoteAdaptiveAdapter).
        QuickReviewSessionEntity saved = harness.savedSessions().isEmpty() ? null : harness.savedSessions().getFirst();
        return saved == null ? List.of() : QuizSessionStateUtils.extractQuiz(saved.getSessionState());
    }
}
