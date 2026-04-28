package com.studysnap.backend.repository;

import com.studysnap.backend.entity.UserUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserUsageRepository extends JpaRepository<UserUsageEntity, UUID> {

    Optional<UserUsageEntity> findByUserIdAndPeriodStart(UUID userId, OffsetDateTime periodStart);

    @Modifying
    @Query(value = """
            INSERT INTO user_usage (
                id,
                user_id,
                month,
                year,
                period_start,
                period_end,
                study_pack_generations,
                challenge_quiz_generations,
                adaptive_quiz_generations,
                ocr_extractions,
                note_generations,
                created_at
            )
            VALUES (
                gen_random_uuid(),
                :userId,
                :month,
                :year,
                :periodStart,
                :periodEnd,
                :studyPackDelta,
                :challengeDelta,
                :adaptiveDelta,
                :ocrDelta,
                :noteGenerationDelta,
                :createdAt
            )
            ON CONFLICT (user_id, period_start)
            DO UPDATE SET
                period_end = EXCLUDED.period_end,
                study_pack_generations = user_usage.study_pack_generations + EXCLUDED.study_pack_generations,
                challenge_quiz_generations = user_usage.challenge_quiz_generations + EXCLUDED.challenge_quiz_generations,
                adaptive_quiz_generations = user_usage.adaptive_quiz_generations + EXCLUDED.adaptive_quiz_generations,
                ocr_extractions = user_usage.ocr_extractions + EXCLUDED.ocr_extractions,
                note_generations = user_usage.note_generations + EXCLUDED.note_generations
            """, nativeQuery = true)
    int incrementUsage(
            @Param("userId") UUID userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("periodStart") OffsetDateTime periodStart,
            @Param("periodEnd") OffsetDateTime periodEnd,
            @Param("studyPackDelta") Integer studyPackDelta,
            @Param("challengeDelta") Integer challengeDelta,
            @Param("adaptiveDelta") Integer adaptiveDelta,
            @Param("ocrDelta") Integer ocrDelta,
            @Param("noteGenerationDelta") Integer noteGenerationDelta,
            @Param("createdAt") OffsetDateTime createdAt
    );
}
