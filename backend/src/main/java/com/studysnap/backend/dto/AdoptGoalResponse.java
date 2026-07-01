package com.studysnap.backend.dto;

import java.util.UUID;

public record AdoptGoalResponse(
        UUID goalCollectionId,
        int adoptedSubjectCount,
        int skippedSubjectCount,
        int totalNotesCopied,
        int totalNotesSkipped,
        boolean alreadyAdopted
) {
}
