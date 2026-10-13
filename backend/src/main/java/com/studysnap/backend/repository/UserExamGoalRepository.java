package com.studysnap.backend.repository;

import com.studysnap.backend.entity.UserExamGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserExamGoalRepository extends JpaRepository<UserExamGoalEntity, UUID> {
    List<UserExamGoalEntity> findByUserIdOrderByCreatedAtDescIdAsc(UUID userId);

    Optional<UserExamGoalEntity> findByUserIdAndExamGoalSlug(UUID userId, String slug);

    Optional<UserExamGoalEntity> findByUserIdAndActiveTrue(UUID userId);

    void deleteByUserId(UUID userId);
}
