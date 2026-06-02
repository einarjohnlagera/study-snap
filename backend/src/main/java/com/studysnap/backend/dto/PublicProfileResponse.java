package com.studysnap.backend.dto;

import com.studysnap.backend.dto.NoteStatsResponse.SubjectCount;

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
        String userId,
        int publicNotesCount,
        long totalCopies,
        long totalShares,
        long totalViews,
        long totalProfileShares,
        List<SubjectCount> notesBySubject,
        int totalPublicSubjectCount,
        List<PublicProfileNoteResponse> publicNotes
) {
}
