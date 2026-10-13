package com.studysnap.backend.service;

import com.studysnap.backend.config.ExamGoalConfig;
import com.studysnap.backend.dto.ExamGoalResponse;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserExamGoalEntity;
import com.studysnap.backend.repository.UserExamGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserExamGoalResolver {
    private final UserExamGoalRepository repository;
    private final ExamGoalCourseProgramProvider courseProgramProvider;

    public String activeExamGoal(UserEntity user) {
        return repository.findByUserIdAndActiveTrue(user.getId())
                .map(UserExamGoalEntity::getExamGoalSlug)
                .orElseGet(() -> legacyExamGoal(user.getStudyGoal()));
    }

    public String effectiveStudyGoal(UserEntity user) {
        String active = activeExamGoal(user);
        return active != null ? active : user.getStudyGoal();
    }

    public List<UserExamGoalEntity> adoptedGoals(UserEntity user) {
        return repository.findByUserIdOrderByCreatedAtDescIdAsc(user.getId());
    }

    public List<ExamGoalResponse> goalList(UserEntity user) {
        return adoptedGoals(user).stream()
                .map(goal -> new ExamGoalResponse(goal.getExamGoalSlug(), goal.isActive()))
                .toList();
    }

    public List<ExamGoalResponse> goalList(java.util.UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDescIdAsc(userId).stream()
                .map(goal -> new ExamGoalResponse(goal.getExamGoalSlug(), goal.isActive()))
                .toList();
    }

    public boolean matchesProgram(String goal, String program) {
        if (goal == null || program == null) return false;
        String key = normalizeProgramKey(program);
        List<String> candidates = isExamGoal(goal)
                ? courseProgramProvider.getCoursePrograms(goal) : List.of(goal);
        return candidates.stream().anyMatch(candidate -> normalizeProgramKey(candidate).equals(key));
    }

    public static String normalizeProgramKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isExamGoal(String value) {
        return ExamGoalConfig.isValidSlug(value);
    }

    public static String legacyExamGoal(String value) {
        return ExamGoalConfig.isValidSlug(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }
}
