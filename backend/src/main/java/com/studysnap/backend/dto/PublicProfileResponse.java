package com.studysnap.backend.dto;

import java.util.List;

public record PublicProfileResponse(
        String displayName,
        String bio,
        String learnerLevel,
        String courseProgram,
        String profileType,
        boolean isOfficial,
        boolean publicProfileVisible,
        int publicNotesCount,
        long totalCopies,
        List<PublicProfileNoteResponse> publicNotes
) {
}
