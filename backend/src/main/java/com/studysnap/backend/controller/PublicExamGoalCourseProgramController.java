package com.studysnap.backend.controller;

import com.studysnap.backend.config.ExamGoalConfig;
import com.studysnap.backend.service.ExamGoalCourseProgramProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/public/exam-goals/course-programs")
@RequiredArgsConstructor
public class PublicExamGoalCourseProgramController {
    private final ExamGoalCourseProgramProvider courseProgramProvider;

    @GetMapping
    public Map<String, List<String>> listCourseProgramsByExamGoal() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        ExamGoalConfig.getValidSlugs().forEach(slug -> result.put(slug, courseProgramProvider.getCoursePrograms(slug)));
        return result;
    }
}
