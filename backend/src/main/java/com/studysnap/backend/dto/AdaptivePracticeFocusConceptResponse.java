package com.studysnap.backend.dto;

public record AdaptivePracticeFocusConceptResponse(
        String concept,
        String sourceStudyPackId,
        String sourceTitle,
        String selectionReason
) {
}
