package com.studysnap.backend.dto;

import com.studysnap.backend.service.UnsubscribeCategory;

public record EmailUnsubscribeResponse(
        UnsubscribeCategory category,
        String displayName,
        String message
) {
}
