package com.studysnap.backend.service;

import com.studysnap.backend.config.ExamGoalConfig;
import com.studysnap.backend.dto.ExamGoalResponse;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserExamGoalEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserExamGoalRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserExamGoalService {
    private final UserExamGoalRepository repository;
    private final UserRepository userRepository;
    private final UserExamGoalResolver resolver;

    @Transactional
    public List<ExamGoalResponse> addAndActivate(UUID userId, String slug) {
        String normalized = requireSlug(slug);
        UserEntity user = lockUser(userId);
        UserExamGoalEntity existing = repository.findByUserIdAndExamGoalSlug(userId, normalized).orElse(null);
        if (existing != null && existing.isActive()) {
            user.setStudyGoal(null);
            user.setFocusSubjects(new String[0]);
            return resolver.goalList(user);
        }
        deactivateAll(userId);
        if (existing == null) {
            UserExamGoalEntity created = new UserExamGoalEntity();
            created.setId(UUID.randomUUID());
            created.setUserId(userId);
            created.setExamGoalSlug(normalized);
            created.setActive(true);
            created.setCreatedAt(OffsetDateTime.now());
            repository.saveAndFlush(created);
        } else {
            existing.setActive(true);
            repository.flush();
        }
        user.setStudyGoal(null);
        user.setFocusSubjects(new String[0]);
        user.setUpdatedAt(OffsetDateTime.now());
        return resolver.goalList(user);
    }

    @Transactional
    public List<ExamGoalResponse> setActive(UUID userId, String slug) {
        String normalized = requireSlug(slug);
        UserEntity user = lockUser(userId);
        UserExamGoalEntity target = repository.findByUserIdAndExamGoalSlug(userId, normalized)
                .orElseThrow(() -> new AppException("EXAM_GOAL_NOT_FOUND", "Exam goal not found.", HttpStatus.NOT_FOUND));
        if (!target.isActive()) {
            deactivateAll(userId);
            target.setActive(true);
            repository.flush();
        }
        user.setStudyGoal(null);
        user.setFocusSubjects(new String[0]);
        return resolver.goalList(user);
    }

    @Transactional
    public List<ExamGoalResponse> remove(UUID userId, String slug) {
        String normalized = requireSlug(slug);
        UserEntity user = lockUser(userId);
        UserExamGoalEntity target = repository.findByUserIdAndExamGoalSlug(userId, normalized)
                .orElseThrow(() -> new AppException("EXAM_GOAL_NOT_FOUND", "Exam goal not found.", HttpStatus.NOT_FOUND));
        boolean wasActive = target.isActive();
        repository.delete(target);
        repository.flush(); // DELETE must reach PostgreSQL before activating the replacement.
        if (wasActive) {
            List<UserExamGoalEntity> remaining = repository.findByUserIdOrderByCreatedAtDescIdAsc(userId);
            if (!remaining.isEmpty()) {
                remaining.getFirst().setActive(true);
                repository.flush();
            }
        }
        return resolver.goalList(user);
    }

    @Transactional
    public void clearAll(UUID userId) {
        lockUser(userId);
        repository.deleteByUserId(userId);
        repository.flush();
    }

    private void deactivateAll(UUID userId) {
        repository.findByUserIdAndActiveTrue(userId).ifPresent(active -> {
            active.setActive(false);
            repository.flush(); // UPDATE precedes any active INSERT/UPDATE under the partial index.
        });
    }

    private UserEntity lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
    }

    private String requireSlug(String slug) {
        if (!ExamGoalConfig.isValidSlug(slug)) {
            throw new AppException("INVALID_EXAM_GOAL", "Choose a recognized exam goal.", HttpStatus.BAD_REQUEST);
        }
        return slug.trim().toLowerCase(Locale.ROOT);
    }

}
