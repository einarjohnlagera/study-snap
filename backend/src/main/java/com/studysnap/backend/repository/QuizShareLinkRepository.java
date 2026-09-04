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

    /**
     * ⚠️ ALL rows for a quiz, not just the newest. {@code createShareLink} mints a NEW row whenever the
     * latest one is inactive, only {@code token} is unique, and {@code findActiveLink} accepts ANY active
     * token — so several live links can point at one quiz, and a caller that only handles the newest would
     * leave the others serving.
     */
    List<QuizShareLinkEntity> findByGeneratedQuizIdAndOwnerUserId(UUID generatedQuizId, UUID ownerUserId);

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
