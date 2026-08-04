package com.studysnap.backend.service;

import com.studysnap.backend.config.ExamGoalConfig;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExamGoalCourseProgramProvider {
    private final CourseProgramCatalogRepository courseProgramCatalogRepository;
    private final Map<String, List<String>> courseProgramsBySlug = new ConcurrentHashMap<>();

    public List<String> getCoursePrograms(String slug) {
        String normalizedSlug = normalizeSlug(slug);
        if (!ExamGoalConfig.isValidSlug(normalizedSlug)) {
            return List.of();
        }
        List<String> cached = courseProgramsBySlug.get(normalizedSlug);
        if (cached != null) {
            return cached;
        }
        // Only a successful, non-empty catalog read is memoized. Caching the fail-open fallback would
        // pin the literal lists for the JVM's lifetime after a single transient DB failure, so a later
        // catalog edit would stay invisible until redeploy — AGENTS.md:677 sanctions failing open, not
        // failing open permanently. The load also runs outside the map so a blocking JDBC call never
        // holds a ConcurrentHashMap bin monitor; two concurrent cold loads for one slug are harmless.
        List<String> coursePrograms = loadCatalogCoursePrograms(normalizedSlug);
        if (coursePrograms == null) {
            return ExamGoalConfig.getFallbackCoursePrograms(normalizedSlug);
        }
        courseProgramsBySlug.putIfAbsent(normalizedSlug, coursePrograms);
        return coursePrograms;
    }

    /**
     * @return the catalog's programs for this slug, or {@code null} when the caller should fail open to
     *         the literal fallback list. Never returns an empty list — an empty catalog read is a
     *         fallback signal, not an answer.
     */
    private List<String> loadCatalogCoursePrograms(String slug) {
        try {
            List<String> coursePrograms = courseProgramCatalogRepository.findNamesByExamGoalSlug(slug);
            if (coursePrograms != null && !coursePrograms.isEmpty()) {
                return List.copyOf(coursePrograms);
            }
            log.warn("exam_goal_course_program_catalog_empty slug={} using=fallback", slug);
        } catch (RuntimeException ex) {
            log.warn("exam_goal_course_program_catalog_unavailable slug={} using=fallback reason={}", slug, ex.getMessage());
        }
        return null;
    }

    private String normalizeSlug(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }
}
