package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.util.List;

/** Note Adaptive public path for preregistration sections 3 and 7. */
final class NoteAdaptiveAdapter extends AbstractClassAAdapter {
    NoteAdaptiveAdapter(ClassACaseManifest.Case item) {
        super(item, ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
    }

    @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
        harness.prepareNoteAdaptive();
        harness.service(QuickReviewAdaptivePracticeService.class)
                .generateAdaptiveQuiz(harness.pack(0).getId().toString(), harness.userId);
        // The public DTO redacts answer keys (QuizItem.withoutAnswerKey); the blind key review needs the keys, so
        // read the production-persisted session state through its owner, QuizSessionStateUtils.
        QuickReviewSessionEntity saved = harness.savedSessions().isEmpty() ? null : harness.savedSessions().getFirst();
        return saved == null ? List.of() : QuizSessionStateUtils.extractQuiz(saved.getSessionState());
    }
}
