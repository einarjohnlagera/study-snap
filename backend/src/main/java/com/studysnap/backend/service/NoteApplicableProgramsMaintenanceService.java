package com.studysnap.backend.service;

import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteApplicableProgramsMaintenanceService {
    private final CourseProgramCatalogRepository courseProgramCatalogRepository;
    private final NoteCourseProgramRepository noteCourseProgramRepository;

    public void seedDerivedSet(UUID noteId, String courseProgram) {
        courseProgramCatalogRepository.resolveIdForLegacyName(courseProgram)
                .ifPresent(courseProgramId -> noteCourseProgramRepository.insert(noteId, Set.of(courseProgramId)));
    }

    /**
     * A set is "derived" when it is exactly what the pre-edit legacy string resolves to -- including the
     * empty set, which is the correct derivation of a null or catalog-excluded string. Treating an
     * unresolvable pre-edit string as "not derived" would strand every note that gains a catalog-resolvable
     * course/program after creation (created with no program, or with an excluded value, then corrected)
     * with a legacy string and zero join rows, which is exactly the one-row-per-note invariant Slice 2's
     * facet-count equivalence test depends on.
     */
    public boolean isDerivedSet(UUID noteId, String courseProgram) {
        return noteCourseProgramRepository.findIdsByNoteId(noteId).equals(deriveSet(courseProgram));
    }

    public void replaceWithDerivedSet(UUID noteId, String courseProgram) {
        noteCourseProgramRepository.replace(noteId, deriveSet(courseProgram));
    }

    private Set<UUID> deriveSet(String courseProgram) {
        return courseProgramCatalogRepository.resolveIdForLegacyName(courseProgram)
                .<Set<UUID>>map(Set::of)
                .orElseGet(Set::of);
    }
}
