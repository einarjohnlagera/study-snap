package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeQuizQuestionBankRepository extends JpaRepository<ChallengeQuizQuestionBankEntity, UUID> {
    // Flush the caller's pending session FK and claim changes before native SQL; never clear the
    // persistence context, which can still hold managed claim/outcome changes for this request.
    // The only other unique index is the random-UUID primary key, so bare ON CONFLICT can only
    // skip an already occupied (user, pack, question key, learner level) bank slot.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into challenge_quiz_question_bank
                (id, user_id, study_pack_id, generation_stamp, origin_session_id,
                 question_key, question, learner_level, last_known_outcome,
                 claimed_session_id, generated_at)
            values (:id, :userId, :studyPackId, :generationStamp, :originSessionId,
                    :questionKey, cast(:question as jsonb), :learnerLevel, :lastKnownOutcome,
                    :claimedSessionId, :generatedAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId, @Param("generationStamp") Long generationStamp,
            @Param("originSessionId") UUID originSessionId, @Param("questionKey") String questionKey,
            @Param("question") String question, @Param("learnerLevel") String learnerLevel,
            @Param("lastKnownOutcome") String lastKnownOutcome,
            @Param("claimedSessionId") UUID claimedSessionId,
            @Param("generatedAt") OffsetDateTime generatedAt);

    @Modifying
    @Query("delete from ChallengeQuizQuestionBankEntity e where e.studyPackId = :studyPackId")
    void bulkDeleteAllForStudyPack(@Param("studyPackId") UUID studyPackId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select question
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
              and ((cast(:learnerLevel as string) is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
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
              and ((cast(:learnerLevel as string) is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
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
            select question
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
              and ((cast(:learnerLevel as string) is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
              and question.lastKnownOutcome = :outcome
              and question.claimedSessionId is null
              and (question.generationStamp is null or question.generationStamp =
                   (select pack.generationStamp from StudyPackEntity pack where pack.id = :studyPackId))
            order by question.generatedAt asc
            """)
    List<ChallengeQuizQuestionBankEntity> findIncorrectClaimable(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("learnerLevel") String learnerLevel,
            @Param("outcome") String outcome
    );

    // ⚠️ NOT stamp-filtered. This is a write-side guard ("has a template already been seeded for this
    // owner+pack?"), not a read that hands out content — filtering it would make a stale-but-present
    // row invisible to the check, triggering a re-seed whose duplicate insert is silently skipped.
    // The guard still avoids needless generation and preserves template seed idempotence.
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

    // ⚠️ NOT stamp-filtered. This pre-read excludes template keys the caller's bank already holds,
    // including stale rows. The insert silently skips a concurrent duplicate, but this guard still
    // avoids re-serving keys known before the copy begins.
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
    List<ChallengeQuizQuestionBankEntity> findByUserIdAndClaimedSessionId(
            UUID userId,
            UUID claimedSessionId
    );

    @Modifying
    @Query("""
            update ChallengeQuizQuestionBankEntity question set question.claimedSessionId = null
            where question.userId = :userId and question.claimedSessionId = :sessionId
            """)
    int releaseClaims(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId);

}
