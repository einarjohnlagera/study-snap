package com.studysnap.backend.dto;

import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.QuickReviewSessionMode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DataExportResponse(
        Meta meta,
        Account account,
        List<Note> notes,
        List<StudyPack> studyPacks,
        List<Collection> collections,
        PracticeSummary practiceSummary
) {
    public record Meta(
            OffsetDateTime exportedAt,
            String schemaVersion
    ) {
    }

    public record Account(
            String email,
            String firstName,
            String lastName,
            String displayName,
            String username,
            ProfileType profileType,
            LearnerLevel learnerLevel,
            String courseProgram,
            String studyGoal,
            List<String> focusSubjects,
            LocalDate examDate,
            Integer birthYear,
            List<ProvisionalBirthYear> provisionalBirthYears,
            OffsetDateTime createdAt
    ) {
    }

    /**
     * A birth year declared while redeeming an invitation link, held against ONE relationship and
     * not yet promoted to the account.
     *
     * <p>⚠️ DELIBERATELY SEPARATE FROM {@code Account.birthYear}, never merged into it.
     * {@code users.birth_year} is account-global and write-once; a provisional declaration is
     * neither, and only becomes the account year if the link's creator confirms. Merging the two
     * would make this export — the one surface that exists to state what is held accurately —
     * assert an account-global value that was never written.
     */
    public record ProvisionalBirthYear(
            UUID relationshipId,
            Integer birthYear,
            OffsetDateTime declaredAt
    ) {
    }

    public record Note(
            UUID id,
            String title,
            String subject,
            DomainContext domainContext,
            LearnerLevel learnerLevel,
            String content,
            NoteVisibility visibility,
            String copiedFromTitle,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record StudyPack(
            UUID noteId,
            String title,
            String summary,
            List<String> keyConcepts,
            List<QuizItem> quiz
    ) {
    }

    public record Collection(
            String name,
            List<CollectionNoteReference> notes
    ) {
    }

    public record CollectionNoteReference(
            UUID id,
            String title
    ) {
    }

    public record PracticeSummary(
            long totalCompletedSessions,
            Map<QuickReviewSessionMode, Long> completedSessionsByMode,
            OffsetDateTime lastSessionCompletedAt
    ) {
    }
}
