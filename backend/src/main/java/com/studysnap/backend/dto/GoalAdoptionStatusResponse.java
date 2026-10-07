package com.studysnap.backend.dto;

import com.studysnap.backend.entity.GoalAdoptionJobEntity;

import java.util.UUID;

public record GoalAdoptionStatusResponse(
        UUID collectionId,
        UUID jobId,
        String status,
        int processedSubjectCount,
        int totalSubjectCount,
        int adoptedSubjectCount,
        int skippedSubjectCount,
        int totalNotesCopied,
        int totalNotesSkipped
) {
    public static GoalAdoptionStatusResponse from(GoalAdoptionJobEntity job) {
        return new GoalAdoptionStatusResponse(job.getGoalId(), job.getId(), job.getStatus().name(),
                job.getProcessedSubjectCount(), job.getSourceChildIds().size(), job.getAdoptedSubjectCount(),
                job.getSkippedSubjectCount(), job.getTotalNotesCopied(), job.getTotalNotesSkipped());
    }
}
