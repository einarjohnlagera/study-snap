package com.studysnap.backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteCourseProgramRepositoryTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    private NoteCourseProgramRepository repository;

    @BeforeEach
    void setUp() {
        repository = new NoteCourseProgramRepository(jdbcTemplate);
    }

    @Test
    void replaceAddsAndRemovesOnlyTheSetDifference() {
        UUID noteId = UUID.randomUUID();
        UUID programA = UUID.randomUUID();
        UUID programB = UUID.randomUUID();
        UUID programC = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), eq(noteId)))
                .thenReturn(List.of(programA, programB));

        repository.replace(noteId, Set.of(programB, programC));

        verify(jdbcTemplate).update(
                "DELETE FROM note_course_program WHERE note_id = ? AND course_program_id IN (?)",
                noteId,
                programA
        );
        verify(jdbcTemplate).update(
                anyString(),
                any(UUID.class),
                eq(noteId),
                eq(programC)
        );
    }

    @Test
    void replaceWithIdenticalSetDoesNotWriteRows() {
        UUID noteId = UUID.randomUUID();
        UUID programA = UUID.randomUUID();
        UUID programB = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), eq(noteId)))
                .thenReturn(List.of(programA, programB));

        repository.replace(noteId, Set.of(programA, programB));

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
