package com.studysnap.backend.dto;

import java.util.List;

public record PublicProfileResponse(
        String displayName,
        String profileType,
        boolean isOfficial,
        int publicNotesCount,
        long totalCopies,
        List<PublicProfileNoteResponse> publicNotes
) {
}
