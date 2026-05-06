package com.studysnap.backend.dto;

import java.util.List;

public record PublicProfileResponse(
        String displayName,
        String username,
        String bio,
        String learnerLevel,
        String courseProgram,
        String profileType,
        boolean isOfficial,
        boolean publicProfileVisible,
        boolean isCurrentUser,
        int publicNotesCount,
        long totalCopies,
        long totalShares,
        long totalViews,
        List<PublicProfileNoteResponse> publicNotes
) {
}
