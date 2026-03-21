package com.studysnap.backend.repository;

import com.studysnap.backend.entity.QuizQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestionEntity, UUID> {
    List<QuizQuestionEntity> findByStudyPackIdOrderByPositionAsc(UUID studyPackId);
}
