package com.studysnap.backend.dto;

import java.util.List;

public record SubjectStatsResponse(
        List<SubjectFacetCount> topSubjects,
        long otherSubjectsCount,
        long total
) {
}
