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
        return courseProgramsBySlug.computeIfAbsent(normalizedSlug, this::loadCoursePrograms);
    }

    private List<String> loadCoursePrograms(String slug) {
        try {
            List<String> coursePrograms = courseProgramCatalogRepository.findNamesByExamGoalSlug(slug);
            if (coursePrograms != null && !coursePrograms.isEmpty()) {
                return List.copyOf(coursePrograms);
            }
            log.warn("exam_goal_course_program_catalog_empty slug={} using=fallback", slug);
        } catch (RuntimeException ex) {
            log.warn("exam_goal_course_program_catalog_unavailable slug={} using=fallback reason={}", slug, ex.getMessage());
        }
        return ExamGoalConfig.getFallbackCoursePrograms(slug);
    }

    private String normalizeSlug(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }
}
