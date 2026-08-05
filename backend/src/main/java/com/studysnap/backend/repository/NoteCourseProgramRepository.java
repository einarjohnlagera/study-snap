package com.studysnap.backend.repository;

import com.studysnap.backend.dto.ApplicableProgramResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class NoteCourseProgramRepository {
    private static final String FIND_BY_NOTE_ID = """
            SELECT course_programs.id, course_programs.name
            FROM note_course_program
            JOIN course_programs ON course_programs.id = note_course_program.course_program_id
            WHERE note_course_program.note_id = ?
            ORDER BY course_programs.name
            """;
    private static final String INSERT = """
            INSERT INTO note_course_program (id, note_id, course_program_id)
            VALUES (?, ?, ?)
            ON CONFLICT (note_id, course_program_id) DO NOTHING
            """;
    private static final String FIND_NAMES_BY_OWNER_USER_ID = """
            SELECT course_programs.name
            FROM note_course_program
            JOIN course_programs ON course_programs.id = note_course_program.course_program_id
            JOIN notes ON notes.id = note_course_program.note_id
            WHERE notes.owner_user_id = ?
            """;
    private static final String FIND_NAMES_BY_VISIBILITY = """
            SELECT course_programs.name
            FROM note_course_program
            JOIN course_programs ON course_programs.id = note_course_program.course_program_id
            JOIN notes ON notes.id = note_course_program.note_id
            WHERE notes.visibility = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<ApplicableProgramResponse> findByNoteId(UUID noteId) {
        return jdbcTemplate.query(FIND_BY_NOTE_ID, (resultSet, rowNumber) -> new ApplicableProgramResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name")
        ), noteId);
    }

    public Set<UUID> findIdsByNoteId(UUID noteId) {
        return jdbcTemplate.queryForList(
                        "SELECT course_program_id FROM note_course_program WHERE note_id = ?",
                        UUID.class,
                        noteId
                ).stream()
                .collect(Collectors.toSet());
    }

    public List<String> findNamesByOwnerUserId(UUID ownerUserId) {
        return jdbcTemplate.queryForList(FIND_NAMES_BY_OWNER_USER_ID, String.class, ownerUserId);
    }

    public List<String> findNamesByVisibility(String visibility) {
        return jdbcTemplate.queryForList(FIND_NAMES_BY_VISIBILITY, String.class, visibility);
    }

    public Map<UUID, List<ApplicableProgramResponse>> findByNoteIds(Collection<UUID> noteIds) {
        Map<UUID, List<ApplicableProgramResponse>> result = new LinkedHashMap<>();
        noteIds.forEach(noteId -> result.put(noteId, new java.util.ArrayList<>()));
        if (noteIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", Collections.nCopies(noteIds.size(), "?"));
        List<NoteProgramRow> rows = jdbcTemplate.query(
                """
                        SELECT note_course_program.note_id, course_programs.id, course_programs.name
                        FROM note_course_program
                        JOIN course_programs ON course_programs.id = note_course_program.course_program_id
                        WHERE note_course_program.note_id IN (%s)
                        ORDER BY course_programs.name
                        """.formatted(placeholders),
                (resultSet, rowNumber) -> new NoteProgramRow(
                        resultSet.getObject("note_id", UUID.class),
                        new ApplicableProgramResponse(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("name")
                        )
                ),
                noteIds.toArray()
        );
        rows.forEach(row -> result.computeIfAbsent(row.noteId(), ignored -> new java.util.ArrayList<>())
                .add(row.program()));
        return result;
    }

    public void delete(UUID noteId, Collection<UUID> courseProgramIds) {
        if (courseProgramIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(courseProgramIds.size(), "?"));
        Object[] arguments = new Object[courseProgramIds.size() + 1];
        arguments[0] = noteId;
        int index = 1;
        for (UUID courseProgramId : courseProgramIds) {
            arguments[index++] = courseProgramId;
        }
        jdbcTemplate.update(
                "DELETE FROM note_course_program WHERE note_id = ? AND course_program_id IN (" + placeholders + ")",
                arguments
        );
    }

    public void insert(UUID noteId, Collection<UUID> courseProgramIds) {
        for (UUID courseProgramId : courseProgramIds) {
            jdbcTemplate.update(INSERT, UUID.randomUUID(), noteId, courseProgramId);
        }
    }

    public void replace(UUID noteId, Set<UUID> desiredIds) {
        Set<UUID> existingIds = findIdsByNoteId(noteId);
        Set<UUID> removedIds = new java.util.HashSet<>(existingIds);
        removedIds.removeAll(desiredIds);
        Set<UUID> addedIds = new java.util.HashSet<>(desiredIds);
        addedIds.removeAll(existingIds);
        delete(noteId, removedIds);
        insert(noteId, addedIds);
    }

    private record NoteProgramRow(UUID noteId, ApplicableProgramResponse program) {
    }
}
