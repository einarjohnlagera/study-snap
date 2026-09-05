package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.model.NoteLibraryReadiness;

import java.util.List;
import java.util.UUID;

public record NoteLibraryFilterCriteria(
        UUID ownerUserId,
        String searchPattern,
        NoteLibraryReadiness readiness,
        String courseProgram,
        List<String> tags,
        NoteVisibility visibility,
        /**
         * Review Set membership. Null means "no collection filter" -- it is NOT a sentinel for
         * "notes in no collection", which the Library has never offered and this does not add.
         *
         * <p>Added for curator bulk regeneration: the motivating workflow is rebuilding one specific
         * Review Set, and the Library's three existing axes (subject, course/program, tags) cannot
         * express membership. Independently useful, and the only new filter axis this feature owes.
         */
        UUID collectionId
) {
}
