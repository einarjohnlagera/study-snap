package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ExamQuestionPoolEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ExamQuestionPoolRepository extends JpaRepository<ExamQuestionPoolEntity, UUID> {
    Optional<ExamQuestionPoolEntity> findByStudyPackIdAndMode(UUID studyPackId, String mode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExamQuestionPoolEntity p where p.id = :id")
    Optional<ExamQuestionPoolEntity> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExamQuestionPoolEntity p where p.studyPackId = :studyPackId and p.mode = :mode")
    Optional<ExamQuestionPoolEntity> findByStudyPackIdAndModeForUpdate(UUID studyPackId, String mode);
}
