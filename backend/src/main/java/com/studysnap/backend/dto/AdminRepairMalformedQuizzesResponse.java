package com.studysnap.backend.dto;

public record AdminRepairMalformedQuizzesResponse(
        int queued,
        int skipped
) {
}
