package com.studysnap.backend.dto;

import java.util.List;

public record DashboardFocusAreasResponse(
        List<DashboardConceptInsightResponse> concepts,
        String practiceNoteId,
        boolean adaptivePracticeAvailable
) {
}
