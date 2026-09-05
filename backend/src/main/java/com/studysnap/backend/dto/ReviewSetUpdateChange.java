package com.studysnap.backend.dto;

import java.util.UUID;

public record ReviewSetUpdateChange(
        String type,
        UUID sourcePlanId,
        UUID sourceNoteId,
        String subjectTitle,
        String noteTitle,
        String previousValue,
        String currentValue,
        boolean applied
) {
}
