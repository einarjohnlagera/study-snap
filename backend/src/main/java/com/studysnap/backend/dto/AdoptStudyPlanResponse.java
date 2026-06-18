package com.studysnap.backend.dto;

import java.util.UUID;

public record AdoptStudyPlanResponse(
        UUID collectionId,
        int copiedCount,
        int skippedCount,
        boolean alreadyAdopted
) {
}
