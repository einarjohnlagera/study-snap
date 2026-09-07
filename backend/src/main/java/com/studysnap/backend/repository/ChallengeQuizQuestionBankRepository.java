package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeQuizQuestionBankRepository extends JpaRepository<ChallengeQuizQuestionBankEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select question
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
              and ((:learnerLevel is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
              and (question.claimedSessionId is null or question.claimedSessionId = :sessionId)
            order by question.generatedAt asc
            """)
    List<ChallengeQuizQuestionBankEntity> findClaimableForUpdate(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("learnerLevel") String learnerLevel,
            @Param("sessionId") UUID sessionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select question
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
              and ((:learnerLevel is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
              and question.lastKnownOutcome = :outcome
              and question.claimedSessionId is null
            order by question.generatedAt asc
            """)
    List<ChallengeQuizQuestionBankEntity> findIncorrectClaimableForUpdate(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("learnerLevel") String learnerLevel,
            @Param("outcome") String outcome
    );

    @Query("""
            select count(question)
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
              and ((:learnerLevel is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
              and question.lastKnownOutcome = :outcome
              and question.claimedSessionId is null
            """)
    long countIncorrectEligibleQuestions(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("learnerLevel") String learnerLevel,
            @Param("outcome") String outcome
    );

    boolean existsByUserIdAndStudyPackId(UUID userId, UUID studyPackId);

    /**
     * The batched form of {@link #existsByUserIdAndStudyPackId}: one query answering "which of these
     * packs already has bank rows, and for whom?" instead of one existence check per candidate.
     *
     * <p>⚠️ IT FILTERS ON {@code studyPackId} ALONE AND MATCHES THE PAIR IN JAVA. A pack id is the
     * selective half, and filtering on both columns independently would be a cross-product rather
     * than a pair match — a row belonging to some OTHER user's copy of a pack would then satisfy the
     * check for the Official author.
     */
    @Query("""
            select distinct new com.studysnap.backend.repository.ChallengeQuizQuestionBankOwnerProjection(
                question.userId,
                question.studyPackId
            )
            from ChallengeQuizQuestionBankEntity question
            where question.studyPackId in :studyPackIds
            """)
    List<ChallengeQuizQuestionBankOwnerProjection> findOwnerStudyPackPairsByStudyPackIdIn(
            @Param("studyPackIds") Collection<UUID> studyPackIds
    );

    List<ChallengeQuizQuestionBankEntity> findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(
            UUID userId,
            UUID studyPackId
    );

    @Query("""
            select question.questionKey
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
            """)
    List<String> findQuestionKeysByUserIdAndStudyPackId(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ChallengeQuizQuestionBankEntity> findByUserIdAndStudyPackIdAndClaimedSessionId(
            UUID userId,
            UUID studyPackId,
            UUID claimedSessionId
    );
}
