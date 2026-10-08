package com.studysnap.backend.service;

import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectionExamGoalResolverTest {
    private static final UUID OWNER_ID = UUID.randomUUID();
    private final NoteCollectionRepository collections = mock(NoteCollectionRepository.class);
    private final CourseProgramCatalogRepository catalog = mock(CourseProgramCatalogRepository.class);
    private final CollectionExamGoalResolver resolver = new CollectionExamGoalResolver(collections, catalog);

    @Test
    void licensureRootAndProgramlessChildResolveFromCatalog() {
        NoteCollectionEntity root = collection("Civil Engineering", LearnerLevel.BOARD_EXAM_REVIEW, null);
        NoteCollectionEntity child = collection(null, null, root.getId());
        when(collections.findById(root.getId())).thenReturn(Optional.of(root));
        when(catalog.findExamGoalSlugByName("Civil Engineering")).thenReturn(Optional.of("ce"));
        assertThat(resolver.resolve(root)).isEqualTo("ce");
        assertThat(resolver.resolve(child)).isEqualTo("ce");
    }

    @Test
    void academicRootWithTheSameProgramDoesNotBecomeAnExam() {
        NoteCollectionEntity root = collection("Civil Engineering", LearnerLevel.COLLEGE, null);
        when(catalog.findExamGoalSlugByName("Civil Engineering")).thenReturn(Optional.of("ce"));
        assertThat(resolver.resolve(root)).isNull();
    }

    @Test
    void missingCatalogRowOrProgramFallsBack() {
        NoteCollectionEntity root = collection("Unlisted", LearnerLevel.BOARD_EXAM_REVIEW, null);
        when(catalog.findExamGoalSlugByName("Unlisted")).thenReturn(Optional.empty());
        assertThat(resolver.resolve(root)).isNull();
        root.setCourseProgram(null);
        assertThat(resolver.resolve(root)).isNull();
    }

    private NoteCollectionEntity collection(String program, LearnerLevel level, UUID parent) {
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(UUID.randomUUID());
        collection.setOwnerUserId(OWNER_ID);
        collection.setCourseProgram(program);
        collection.setLearnerLevel(level);
        collection.setParentCollectionId(parent);
        return collection;
    }
}
