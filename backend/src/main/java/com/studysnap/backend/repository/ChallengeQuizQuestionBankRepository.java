package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeQuizQuestionBankRepository extends JpaRepository<ChallengeQuizQuestionBankEntity, UUID> {
    @Modifying
    @Query("delete from ChallengeQuizQuestionBankEntity e where e.studyPackId = :studyPackId")
    void bulkDeleteAllForStudyPack(@Param("studyPackId") UUID studyPackId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select question
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
              and ((:learnerLevel is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
              and (question.claimedSessionId is null or question.claimedSessionId = :sessionId)
              and (question.generationStamp is null or question.generationStamp =
                   (select pack.generationStamp from StudyPackEntity pack where pack.id = :studyPackId))
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
              and (question.generationStamp is null or question.generationStamp =
                   (select pack.generationStamp from StudyPackEntity pack where pack.id = :studyPackId))
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
              and (question.generationStamp is null or question.generationStamp =
                   (select pack.generationStamp from StudyPackEntity pack where pack.id = :studyPackId))
            """)
    long countIncorrectEligibleQuestions(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("learnerLevel") String learnerLevel,
            @Param("outcome") String outcome
    );

    // ⚠️ NOT stamp-filtered. This is a write-side guard ("has a template already been seeded for this
    // owner+pack?"), not a read that hands out content — filtering it would make a stale-but-present
    // row invisible to the check, triggering a re-seed that reinserts the same question_key and collides
    // with uq_challenge_quiz_question_bank_user_pack_key on the still-present stale row.
    @Query("""
            select (count(question) > 0) from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId and question.studyPackId = :studyPackId
            """)
    boolean existsByUserIdAndStudyPackId(@Param("userId") UUID userId, @Param("studyPackId") UUID studyPackId);

    /**
     * The batched form of {@link #existsByUserIdAndStudyPackId}: one query answering "which of these
     * packs already has bank rows, and for whom?" instead of one existence check per candidate.
     *
     * <p>⚠️ IT FILTERS ON {@code studyPackId} ALONE AND MATCHES THE PAIR IN JAVA. A pack id is the
     * selective half, and filtering on both columns independently would be a cross-product rather
     * than a pair match — a row belonging to some OTHER user's copy of a pack would then satisfy the
     * check for the Official author.
     *
     * <p>⚠️ NOT stamp-filtered, for the same reason as {@link #existsByUserIdAndStudyPackId}: this
     * guards a write (skip re-seeding), it does not hand out content.
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

    // Stamp-filtered: this hands out TEMPLATE SOURCE content to copy into an adopter's bank, so a
    // template regenerated since must not be copied.
    @Query("""
            select question from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId and question.studyPackId = :studyPackId
              and (question.generationStamp is null or question.generationStamp =
                   (select pack.generationStamp from StudyPackEntity pack where pack.id = :studyPackId))
            order by question.generatedAt asc
            """)
    List<ChallengeQuizQuestionBankEntity> findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(
            @Param("userId") UUID userId, @Param("studyPackId") UUID studyPackId);

    // ⚠️ NOT stamp-filtered. This is the caller's own key-collision guard before an insert (excludes
    // question_key values the caller's bank already holds) — it must see stale rows too, or a fresh
    // generation/copy can reuse a key a stale row still occupies and hit the unique constraint.
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

    @Modifying
    @Query("""
            update ChallengeQuizQuestionBankEntity question set question.claimedSessionId = null
            where question.userId = :userId and question.studyPackId = :studyPackId
              and question.claimedSessionId = :sessionId
            """)
    int releaseClaims(@Param("userId") UUID userId, @Param("studyPackId") UUID studyPackId,
                      @Param("sessionId") UUID sessionId);

}
