package com.studysnap.backend.dto;

public record CreatorImpactSummaryResponse(
        long distinctLearnersHelped,
        long publicNoteCount
) {
}
