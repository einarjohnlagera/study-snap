package com.studysnap.backend.repository;

import java.util.UUID;

public record NoteLibrarySubjectIdProjection(
        UUID id,
        String subject,
        String courseProgram
) implements NoteLibrarySubjectView {
}
