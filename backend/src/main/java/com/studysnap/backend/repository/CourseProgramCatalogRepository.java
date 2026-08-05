package com.studysnap.backend.repository;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CourseProgramCatalogRepository {
    private static final String BSED_ALIAS = "Bsed";
    private static final String BSED_CATALOG_NAME = "Education";
    private static final String FIND_ALL = """
            SELECT course_programs.id,
                   course_programs.name,
                   program_families.id AS program_family_id,
                   program_families.name AS program_family_name
            FROM course_programs
            LEFT JOIN program_families
              ON program_families.id = course_programs.program_family_id
            ORDER BY course_programs.name
            """;
    private static final String FIND_ID_BY_NAME = """
            SELECT id
            FROM course_programs
            WHERE name = ?
            """;
    private static final String FIND_NAMES_BY_EXAM_GOAL_SLUG = """
            SELECT name
            FROM course_programs
            WHERE exam_goal_slug = ?
            ORDER BY name
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<String> findNamesByExamGoalSlug(String examGoalSlug) {
        return jdbcTemplate.queryForList(FIND_NAMES_BY_EXAM_GOAL_SLUG, String.class, examGoalSlug);
    }

    public List<CourseProgramCatalogItemResponse> findAll() {
        return jdbcTemplate.query(FIND_ALL, (resultSet, rowNumber) -> new CourseProgramCatalogItemResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getObject("program_family_id", UUID.class),
                resultSet.getString("program_family_name")
        ));
    }

    public Optional<UUID> resolveIdForLegacyName(String courseProgram) {
        if (courseProgram == null) {
            return Optional.empty();
        }
        String catalogName = BSED_ALIAS.equals(courseProgram) ? BSED_CATALOG_NAME : courseProgram;
        return jdbcTemplate.query(FIND_ID_BY_NAME, (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class), catalogName)
                .stream()
                .findFirst();
    }

    public List<UUID> findExistingIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.queryForList(
                "SELECT id FROM course_programs WHERE id IN (" + placeholders + ")",
                UUID.class,
                ids.toArray()
        );
    }

    public List<String> findNamesByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.queryForList(
                "SELECT name FROM course_programs WHERE id IN (" + placeholders + ") ORDER BY name",
                String.class,
                ids.toArray()
        );
    }
}
