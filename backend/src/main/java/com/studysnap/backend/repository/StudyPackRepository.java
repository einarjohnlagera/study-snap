package com.studysnap.backend.repository;

import com.studysnap.backend.dto.AdminSubjectMetricItemResponse;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.InputType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyPackRepository extends JpaRepository<StudyPackEntity, UUID> {
    List<StudyPackEntity> findByOwnerUserIdOrderByCreatedAtDescIdDesc(UUID ownerUserId, Pageable pageable);
    @Query("""
            select s
            from StudyPackEntity s
            where s.ownerUserId = :ownerUserId
              and (s.createdAt < :cursorCreatedAt or (s.createdAt = :cursorCreatedAt and s.id < :cursorId))
            order by s.createdAt desc, s.id desc
            """)
    List<StudyPackEntity> findByOwnerUserIdAndCursor(
            UUID ownerUserId,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            Pageable pageable
    );
    Optional<StudyPackEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    Optional<StudyPackEntity> findByOwnerUserIdAndNoteId(UUID ownerUserId, UUID noteId);
    Optional<StudyPackEntity> findByNoteId(UUID noteId);
    long countByOwnerUserId(UUID ownerUserId);

    @Query("select count(distinct s.ownerUserId) from StudyPackEntity s")
    long countDistinctOwnerUserIds();

    @Query(value = """
            SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (
                ORDER BY EXTRACT(EPOCH FROM (fp.first_at - u.created_at)) / 86400.0
            )
            FROM users u
            JOIN (
                SELECT sp.owner_user_id, MIN(sp.created_at) AS first_at
                FROM study_packs sp
                GROUP BY sp.owner_user_id
            ) fp ON u.id = fp.owner_user_id
            WHERE u.email_verified_at IS NOT NULL
            """, nativeQuery = true)
    Double findMedianDaysFromVerifiedSignupToFirstPack();

    List<StudyPackEntity> findByNoteIdIn(Collection<UUID> noteIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudyPackEntity s where s.id = :id and s.ownerUserId = :ownerUserId")
    Optional<StudyPackEntity> findByIdAndOwnerUserIdForUpdate(UUID id, UUID ownerUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudyPackEntity s where s.ownerUserId = :ownerUserId and s.noteId = :noteId")
    Optional<StudyPackEntity> findByOwnerUserIdAndNoteIdForUpdate(UUID ownerUserId, UUID noteId);
    Optional<StudyPackEntity> findTopByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<StudyPackEntity> findByShareToken(String shareToken);
    boolean existsByShareToken(String shareToken);

    long countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID ownerUserId,
            OffsetDateTime createdAtFromInclusive,
            OffsetDateTime createdAtToExclusive
    );
    long countByCreatedAtGreaterThanEqual(OffsetDateTime createdAtFromInclusive);
    long countByOwnerUserIdAndInputTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID ownerUserId,
            InputType inputType,
            OffsetDateTime createdAtFromInclusive,
            OffsetDateTime createdAtToExclusive
    );

    @Query("""
            select new com.studysnap.backend.dto.AdminSubjectMetricItemResponse(n.subject, count(s.id))
            from StudyPackEntity s, NoteEntity n
            where n.id = s.noteId
            group by n.subject
            order by count(s.id) desc
            """)
    List<AdminSubjectMetricItemResponse> findTopSubjectsByStudyPackCount(Pageable pageable);
}
