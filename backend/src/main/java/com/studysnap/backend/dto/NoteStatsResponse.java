package com.studysnap.backend.dto;

import java.util.List;

public record NoteStatsResponse(
        List<SubjectCount> topSubjects,
        int otherSubjectsCount,
        int totalNotes
) {
    public record SubjectCount(
            String subject,
            int count
    ) {
    }
}
