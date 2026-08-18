package com.studysnap.backend.repository;

import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuickReviewSessionRepository extends JpaRepository<QuickReviewSessionEntity, UUID> {

    @Query("""
            select min(q.completedAt)
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.studyPackId = :studyPackId
              and q.sessionMode = com.studysnap.backend.entity.QuickReviewSessionMode.QUICK_REVIEW
              and q.status = com.studysnap.backend.entity.QuickReviewSessionStatus.COMPLETED
              and q.completedAt is not null
              and q.verifiedCorrectAnswers = :quizSize
            """)
    OffsetDateTime findQuizMasteredAt(UUID userId, UUID studyPackId, int quizSize);
    String SESSION_SUMMARY_PROJECTION = """
             new com.studysnap.backend.repository.QuickReviewSessionSummaryProjection(
                q.id,
                q.userId,
                q.studyPackId,
                q.noteId,
                q.sessionMode,
                q.status,
                q.totalQuestions,
                q.correctAnswers,
                q.scorePercentage,
                q.retryCount,
                q.durationSeconds,
                q.createdAt,
                q.completedAt
            )
            """;
    String SESSION_METADATA_PROJECTION = """
             new com.studysnap.backend.repository.QuickReviewSessionMetadataProjection(
                q.id,
                q.userId,
                q.studyPackId,
                q.noteId,
                q.sessionMode,
                q.status,
                q.totalQuestions,
                q.correctAnswers,
                q.scorePercentage,
                q.retryCount,
                q.durationSeconds,
                q.sessionMetadata,
                q.createdAt,
                q.completedAt
            )
            """;
    String NOTE_LATEST_COMPLETION_PROJECTION = """
             new com.studysnap.backend.repository.NoteLatestCompletionProjection(
                q.noteId,
                max(q.completedAt)
            )
            """;
    String STUDY_PACK_LATEST_COMPLETION_PROJECTION = """
             new com.studysnap.backend.repository.StudyPackLatestCompletionProjection(
                q.studyPackId,
                max(q.completedAt)
            )
            """;

    Optional<QuickReviewSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuickReviewSessionEntity q where q.id = :id")
    Optional<QuickReviewSessionEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select q.id
            from QuickReviewSessionEntity q
            where q.status = :status
              and q.sessionMode = :sessionMode
              and q.createdAt < :cutoff
            order by q.createdAt asc
            """)
    List<UUID> findStaleSessionIds(
            @Param("status") QuickReviewSessionStatus status,
            @Param("sessionMode") QuickReviewSessionMode sessionMode,
            @Param("cutoff") OffsetDateTime cutoff,
            Pageable pageable
    );

    boolean existsByUserIdAndStatusAndCompletedAtIsNotNull(UUID userId, QuickReviewSessionStatus status);

    long countByUserIdAndStatusAndCompletedAtIsNotNull(UUID userId, QuickReviewSessionStatus status);

    void deleteByUserId(UUID userId);

    Optional<QuickReviewSessionEntity> findByIdAndUserIdAndSessionMode(UUID id, UUID userId, QuickReviewSessionMode sessionMode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select q
            from QuickReviewSessionEntity q
            where q.id = :id
              and q.userId = :userId
              and q.sessionMode = :sessionMode
            """)
    Optional<QuickReviewSessionEntity> findByIdAndUserIdAndSessionModeForUpdate(UUID id, UUID userId, QuickReviewSessionMode sessionMode);

    @Deprecated
    List<QuickReviewSessionEntity> findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            UUID studyPackId,
            Pageable pageable
    );

    List<QuickReviewSessionEntity> findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode,
            Pageable pageable
    );

    List<QuickReviewSessionEntity> findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            QuickReviewSessionMode sessionMode,
            Pageable pageable
    );

    List<QuickReviewSessionEntity> findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            QuickReviewSessionMode sessionMode
    );

    List<QuickReviewSessionEntity> findByUserIdAndSessionModeInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            Collection<QuickReviewSessionMode> sessionModes
    );

    List<QuickReviewSessionEntity> findByUserIdAndStatusAndSessionModeInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            QuickReviewSessionStatus status,
            Collection<QuickReviewSessionMode> sessionModes
    );

    @Deprecated
    List<QuickReviewSessionEntity> findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            Pageable pageable
    );

    List<QuickReviewSessionEntity> findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(UUID userId);

    @Query("""
            select """ + NOTE_LATEST_COMPLETION_PROJECTION + """
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.status = :status
              and q.completedAt is not null
              and q.noteId in :noteIds
            group by q.noteId
            """)
    List<NoteLatestCompletionProjection> findLatestCompletedAtByUserIdAndNoteIdIn(
            @Param("userId") UUID userId,
            @Param("status") QuickReviewSessionStatus status,
            @Param("noteIds") Collection<UUID> noteIds
    );

    @Query("""
            select """ + STUDY_PACK_LATEST_COMPLETION_PROJECTION + """
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.status = :status
              and q.completedAt is not null
              and q.sessionMode = :sessionMode
              and q.studyPackId in :studyPackIds
            group by q.studyPackId
            """)
    List<StudyPackLatestCompletionProjection> findLatestCompletedAtByUserIdAndStudyPackIdInAndSessionMode(
            @Param("userId") UUID userId,
            @Param("status") QuickReviewSessionStatus status,
            @Param("sessionMode") QuickReviewSessionMode sessionMode,
            @Param("studyPackIds") Collection<UUID> studyPackIds
    );

    @Query("""
            select q
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.status = :status
              and q.completedAt is not null
              and q.sessionMode <> :excludedSessionMode
              and (
                  q.noteId = :noteId
                  or q.sessionMode in :multiNoteSessionModes
              )
            order by q.completedAt desc
            """)
    List<QuickReviewSessionEntity> findRecentHistoryCandidatesByUserIdAndNoteIdOrderByCompletedAtDesc(
            @Param("userId") UUID userId,
            @Param("noteId") UUID noteId,
            @Param("status") QuickReviewSessionStatus status,
            @Param("excludedSessionMode") QuickReviewSessionMode excludedSessionMode,
            @Param("multiNoteSessionModes") Collection<QuickReviewSessionMode> multiNoteSessionModes
    );

    @Query("""
            select """ + SESSION_SUMMARY_PROJECTION + """
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.sessionMode = :sessionMode
              and q.completedAt is not null
            order by q.completedAt desc
            """)
    List<QuickReviewSessionSummaryProjection> findCompletedSessionSummariesByUserIdAndSessionModeOrderByCompletedAtDesc(
            @Param("userId") UUID userId,
            @Param("sessionMode") QuickReviewSessionMode sessionMode,
            Pageable pageable
    );

    @Query("""
            select """ + SESSION_SUMMARY_PROJECTION + """
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.sessionMode in :sessionModes
              and q.completedAt is not null
            order by q.completedAt desc
            """)
    List<QuickReviewSessionSummaryProjection> findCompletedSessionSummariesByUserIdAndSessionModeInOrderByCompletedAtDesc(
            @Param("userId") UUID userId,
            @Param("sessionModes") Collection<QuickReviewSessionMode> sessionModes
    );

    @Query("""
            select new com.studysnap.backend.repository.QuickReviewSessionScoreAggregate(
                count(q),
                sum(coalesce(q.scorePercentage, 0))
            )
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.sessionMode in :sessionModes
              and q.completedAt is not null
            """)
    QuickReviewSessionScoreAggregate getCompletedQuizSessionScoreAggregate(
            @Param("userId") UUID userId,
            @Param("sessionModes") Collection<QuickReviewSessionMode> sessionModes
    );

    @Query("""
            select """ + SESSION_SUMMARY_PROJECTION + """
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.studyPackId = :studyPackId
              and q.sessionMode = :sessionMode
              and q.completedAt is not null
            order by q.completedAt desc
            """)
    List<QuickReviewSessionSummaryProjection> findCompletedSessionSummariesByUserIdAndStudyPackIdAndSessionModeOrderByCompletedAtDesc(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("sessionMode") QuickReviewSessionMode sessionMode,
            Pageable pageable
    );

    @Query("""
            select """ + SESSION_SUMMARY_PROJECTION + """
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.sessionMode in :sessionModes
              and q.completedAt >= :fromInclusive
              and q.completedAt < :toExclusive
            order by q.completedAt desc
            """)
    List<QuickReviewSessionSummaryProjection> findCompletedSessionSummariesByUserIdAndSessionModeInAndCompletedAtBetweenOrderByCompletedAtDesc(
            @Param("userId") UUID userId,
            @Param("sessionModes") Collection<QuickReviewSessionMode> sessionModes,
            @Param("fromInclusive") OffsetDateTime fromInclusive,
            @Param("toExclusive") OffsetDateTime toExclusive
    );

    @Query("""
            select """ + SESSION_METADATA_PROJECTION + """
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.sessionMode = :sessionMode
              and q.completedAt is not null
            order by q.completedAt desc
            """)
    List<QuickReviewSessionMetadataProjection> findCompletedSessionMetadataByUserIdAndSessionModeOrderByCompletedAtDesc(
            @Param("userId") UUID userId,
            @Param("sessionMode") QuickReviewSessionMode sessionMode
    );

    @Query("""
            select """ + SESSION_METADATA_PROJECTION + """
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.sessionMode = :sessionMode
              and q.completedAt is not null
            order by q.completedAt desc
            """)
    List<QuickReviewSessionMetadataProjection> findCompletedSessionMetadataByUserIdAndSessionModeOrderByCompletedAtDesc(
            @Param("userId") UUID userId,
            @Param("sessionMode") QuickReviewSessionMode sessionMode,
            Pageable pageable
    );

    @Query("""
            select """ + SESSION_METADATA_PROJECTION + """
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.studyPackId = :studyPackId
              and q.sessionMode = :sessionMode
              and q.completedAt is not null
            order by q.completedAt desc
            """)
    List<QuickReviewSessionMetadataProjection> findCompletedSessionMetadataByUserIdAndStudyPackIdAndSessionModeOrderByCompletedAtDesc(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("sessionMode") QuickReviewSessionMode sessionMode,
            Pageable pageable
    );

    Optional<QuickReviewSessionEntity> findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode,
            QuickReviewSessionStatus status
    );

    boolean existsByStudyPackIdAndSessionModeAndStatus(
            UUID studyPackId,
            QuickReviewSessionMode sessionMode,
            QuickReviewSessionStatus status
    );

    boolean existsByUserIdAndSessionMode(UUID userId, QuickReviewSessionMode sessionMode);

    Optional<QuickReviewSessionEntity> findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode,
            Collection<QuickReviewSessionStatus> statuses
    );

    @Deprecated
    Optional<QuickReviewSessionEntity> findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionStatus status
    );

    Optional<QuickReviewSessionEntity> findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
            UUID userId,
            QuickReviewSessionMode sessionMode,
            QuickReviewSessionStatus status
    );

    @Deprecated
    Optional<QuickReviewSessionEntity> findTopByUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId,
            QuickReviewSessionStatus status
    );

    long countByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNull(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode
    );

    @Deprecated
    long countByUserIdAndStudyPackIdAndCompletedAtIsNotNull(UUID userId, UUID studyPackId);

    long countByUserIdAndSessionModeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID userId,
            QuickReviewSessionMode sessionMode,
            OffsetDateTime createdAtStart,
            OffsetDateTime createdAtEnd
    );

    long countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID userId,
            QuickReviewSessionMode sessionMode,
            Collection<QuickReviewSessionStatus> statuses,
            OffsetDateTime createdAtStart,
            OffsetDateTime createdAtEnd
    );

    long countByUserIdAndSessionModeAndStatusInAndQuotaExemptTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID userId,
            QuickReviewSessionMode sessionMode,
            Collection<QuickReviewSessionStatus> statuses,
            OffsetDateTime createdAtStart,
            OffsetDateTime createdAtEnd
    );

    @Query("""
            select max(q.scorePercentage)
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.studyPackId = :studyPackId
              and q.sessionMode = :sessionMode
              and q.completedAt is not null
            """)
    java.math.BigDecimal findBestScorePercentageByUserIdAndStudyPackIdAndSessionMode(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode
    );

    @Deprecated
    @Query("""
            select max(q.scorePercentage)
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.studyPackId = :studyPackId
              and q.completedAt is not null
            """)
    java.math.BigDecimal findBestScorePercentageByUserIdAndStudyPackId(UUID userId, UUID studyPackId);

}
