package com.studysnap.backend.dto;

import java.util.List;

public record DashboardFocusAreasResponse(
        List<DashboardConceptInsightResponse> concepts,
        String practiceNoteId,
        /**
         * The plan to practise across, when the weakest note belongs to one.
         *
         * <p>Resolved by the rule {@code v0.78.0} already ratified for post-session next steps: the
         * learner's Primary Review Set if it contains the note, else the most recently updated
         * containing collection. It is deliberately NOT a weakness-ranking model -- ranking plans by
         * weakness is the recommendation engine's own unratified scope.
         */
        String practiceCollectionId,
        String practiceCollectionTitle,
        boolean adaptivePracticeAvailable
) {
}
