package com.studysnap.backend.repository;

public record NoteLibrarySubjectProjection(
        String subject,
        String courseProgram
) implements NoteLibrarySubjectView {
}
