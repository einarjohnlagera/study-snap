package com.studysnap.backend.service;

import com.studysnap.backend.config.ExamGoalConfig;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CollectionExamGoalResolver {
    private final NoteCollectionRepository collectionRepository;
    private final CourseProgramCatalogRepository courseProgramCatalogRepository;

    public String resolve(NoteCollectionEntity collection) {
        NoteCollectionEntity root = collection;
        UUID ownerUserId = collection.getOwnerUserId();
        Set<UUID> visited = new HashSet<>();
        while (root.getParentCollectionId() != null) {
            if (!visited.add(root.getId())) return null;
            root = collectionRepository.findById(root.getParentCollectionId()).orElse(null);
            if (root == null) return null;
            if (!ownerUserId.equals(root.getOwnerUserId())) return null;
        }
        return resolveWithoutInheritance(root);
    }

    /**
     * Resolves purely from the given collection's OWN {@code learnerLevel}/{@code courseProgram} —
     * never walks to a parent. For an already-known-PUBLIC collection (the anonymous/public response
     * path), this is the only safe form: walking to a parent via {@link #resolve} could read a
     * PRIVATE ancestor's fields and leak a derived classification of that private row to an
     * anonymous caller (a PUBLIC collection may have a non-null {@code parentCollectionId} — see
     * {@code NoteCollectionService.toPublicDetailResponse}'s equivalent note for
     * {@code resolvedLearnerLevel}). A public collection whose own fields don't qualify returns
     * {@code null} rather than inheriting a parent's exam flavor.
     */
    public String resolveWithoutInheritance(NoteCollectionEntity collection) {
        if (collection.getLearnerLevel() != LearnerLevel.BOARD_EXAM_REVIEW
                || collection.getCourseProgram() == null) return null;
        return courseProgramCatalogRepository.findExamGoalSlugByName(collection.getCourseProgram())
                .filter(ExamGoalConfig::isValidSlug).orElse(null);
    }
}
