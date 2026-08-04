package com.studysnap.backend.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CourseProgramCatalogRepository {
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
}
