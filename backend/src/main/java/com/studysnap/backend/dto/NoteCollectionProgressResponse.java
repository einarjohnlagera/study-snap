package com.studysnap.backend.dto;

public record NoteCollectionProgressResponse(
        int totalNotes,
        int notesWithStudyPack,
        int notesPracticed
) {
}
