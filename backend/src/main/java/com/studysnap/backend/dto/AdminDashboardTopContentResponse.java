package com.studysnap.backend.dto;

import java.util.List;

public record AdminDashboardTopContentResponse(
        List<AdminPublicNoteMetricItemResponse> mostViewedPublicNotes,
        List<AdminPublicNoteMetricItemResponse> mostCopiedPublicNotes,
        List<AdminSubjectMetricItemResponse> topSubjectsByStudyPackGeneration
) {
}
