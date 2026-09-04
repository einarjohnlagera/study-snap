package com.studysnap.backend.repository;

import com.studysnap.backend.entity.QuizShareLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizShareLinkRepository extends JpaRepository<QuizShareLinkEntity, UUID> {
    Optional<QuizShareLinkEntity> findByToken(String token);

    Optional<QuizShareLinkEntity> findFirstByGeneratedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(
            UUID generatedQuizId,
            UUID ownerUserId
    );

    Optional<QuizShareLinkEntity> findFirstByCombinedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(
            UUID combinedQuizId,
            UUID ownerUserId
    );

    List<QuizShareLinkEntity> findByCombinedQuizIdInAndOwnerUserId(
            Collection<UUID> combinedQuizIds,
            UUID ownerUserId
    );

    void deleteByOwnerUserId(UUID ownerUserId);

}
