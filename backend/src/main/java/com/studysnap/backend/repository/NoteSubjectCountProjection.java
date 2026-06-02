package com.studysnap.backend.repository;

public interface NoteSubjectCountProjection {
    String getSubject();

    long getNoteCount();
}
