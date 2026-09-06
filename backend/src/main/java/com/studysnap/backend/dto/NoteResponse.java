package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NoteResponse(
        String id,
        String title,
        String subject,
        String courseProgram,
        String domainContext,
        String learnerLevel,
        List<String> tags,
        String content,
        String visibility,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String copiedFromNoteId,
        String copiedFromUserId,
        String copiedFromTitle,
        Boolean copiedFromPublic,
        OffsetDateTime copiedAt,
        String studyPackId,
        String studyPackStatus,
        String summary,
        List<String> keyConcepts,
        List<QuizItem> quiz,
        boolean quizMastered,
        OffsetDateTime quizMasteredAt,
        GeneratedQuizResponse generatedQuiz,
        String lastUsedTargetLearnerLevel,
        Integer quizCount,
        boolean quickReviewAvailable,
        boolean challengeQuizAvailable,
        boolean adaptivePracticeAvailable,
        /**
         * The Study Pack's own generated title, or null when the note has no pack.
         *
         * <p>⚠️ Added by v0.120.0 and it is NOT the note's title. Bulk Generate used to overwrite
         * notes.title with this value; it no longer does, so the two can legitimately differ and the
         * note detail page offers this one as a dismissible, opt-in suggestion.
         *
         * <p>⚠️ It is carried HERE rather than fetched, because GET /study-packs/{id} records an
         * OPENED_STUDY_PACK activity event that drives the Dashboard's last-opened pack -- fetching it
         * just to compare titles would make merely VIEWING a note rewrite that recommendation.
         * Populated on the detail response only; list responses pass null.
         */
        String studyPackTitle
) {
    public NoteResponse(
            String id,
            String title,
            String subject,
            String courseProgram,
            String domainContext,
            String learnerLevel,
            List<String> tags,
            String content,
            String visibility,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String copiedFromNoteId,
            String copiedFromUserId,
            String copiedFromTitle,
            Boolean copiedFromPublic,
            OffsetDateTime copiedAt,
            String studyPackId,
            String studyPackStatus,
            String summary,
            List<String> keyConcepts,
            List<QuizItem> quiz,
            GeneratedQuizResponse generatedQuiz,
            String lastUsedTargetLearnerLevel,
            Integer quizCount,
            boolean quickReviewAvailable,
            boolean challengeQuizAvailable,
            boolean adaptivePracticeAvailable
    ) {
        this(
                id,
                title,
                subject,
                courseProgram,
                domainContext,
                learnerLevel,
                tags,
                content,
                visibility,
                createdAt,
                updatedAt,
                copiedFromNoteId,
                copiedFromUserId,
                copiedFromTitle,
                copiedFromPublic,
                copiedAt,
                studyPackId,
                studyPackStatus,
                summary,
                keyConcepts,
                quiz,
                false,
                null,
                generatedQuiz,
                lastUsedTargetLearnerLevel,
                quizCount,
                quickReviewAvailable,
                challengeQuizAvailable,
                adaptivePracticeAvailable,
                null
        );
    }

    public NoteResponse(
            String id,
            String title,
            String subject,
            String courseProgram,
            String domainContext,
            String learnerLevel,
            List<String> tags,
            String content,
            String visibility,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String copiedFromNoteId,
            String copiedFromUserId,
            String copiedFromTitle,
            Boolean copiedFromPublic,
            OffsetDateTime copiedAt,
            String studyPackId,
            String studyPackStatus,
            String summary,
            List<String> keyConcepts,
            List<QuizItem> quiz,
            GeneratedQuizResponse generatedQuiz,
            Integer quizCount,
            boolean quickReviewAvailable,
            boolean challengeQuizAvailable,
            boolean adaptivePracticeAvailable
    ) {
        this(
                id,
                title,
                subject,
                courseProgram,
                domainContext,
                learnerLevel,
                tags,
                content,
                visibility,
                createdAt,
                updatedAt,
                copiedFromNoteId,
                copiedFromUserId,
                copiedFromTitle,
                copiedFromPublic,
                copiedAt,
                studyPackId,
                studyPackStatus,
                summary,
                keyConcepts,
                quiz,
                false,
                null,
                generatedQuiz,
                null,
                quizCount,
                quickReviewAvailable,
                challengeQuizAvailable,
                adaptivePracticeAvailable,
                null
        );
    }
}
