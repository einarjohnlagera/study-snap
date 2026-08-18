package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.repository.ExamQuestionPoolRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Finds rows stranded in a non-terminal generation status and hands each to
 * {@link GenerationRecoveryRowWriter}, which commits them one at a time.
 *
 * <p><strong>These sweep methods are deliberately NOT {@code @Transactional}.</strong> The per-row
 * {@code catch} below is only meaningful if each row commits independently; wrapping the loop in one
 * transaction would let a single failing row mark it rollback-only and silently discard every row the
 * sweep had already recovered. See {@link GenerationRecoveryRowWriter} for the full reasoning and for
 * why this is not the {@code REQUIRES_NEW} pattern reverted in v0.81.0.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationRecoveryService {
    private static final String POOL_STATUS_PENDING = "PENDING";
    private static final String POOL_STATUS_GENERATING = "GENERATING";

    private final ExamQuestionPoolRepository examQuestionPoolRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final NoteRepository noteRepository;
    private final GenerationRecoveryRowWriter rowWriter;
    private final StudySnapProperties properties;

    public SurfaceRecoveryResult recoverStaleExamQuestionPools() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        StudySnapProperties.Generation generation = properties.getGeneration();
        OffsetDateTime pendingCutoff = now.minusMinutes(generation.getPoolPendingBoundMinutes());
        OffsetDateTime generatingCutoff = now.minusMinutes(generation.getPoolGeneratingBoundMinutes());
        List<UUID> candidateIds = examQuestionPoolRepository.findStaleNonTerminalIds(
                POOL_STATUS_PENDING,
                pendingCutoff,
                POOL_STATUS_GENERATING,
                generatingCutoff,
                PageRequest.of(0, normalizedBatchSize())
        );

        return sweep(
                candidateIds,
                poolId -> rowWriter.recoverPool(poolId, pendingCutoff, generatingCutoff),
                now,
                "pool"
        );
    }

    public SurfaceRecoveryResult recoverStaleLongExamSessions() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = now.minusMinutes(properties.getGeneration().getLongExamSessionBoundMinutes());
        List<UUID> candidateIds = quickReviewSessionRepository.findStaleSessionIds(
                QuickReviewSessionStatus.GENERATING,
                QuickReviewSessionMode.LONG_EXAM,
                cutoff,
                PageRequest.of(0, normalizedBatchSize())
        );

        return sweep(
                candidateIds,
                sessionId -> rowWriter.recoverLongExamSession(sessionId, cutoff),
                now,
                "long-exam-session"
        );
    }

    public SurfaceRecoveryResult recoverStaleNotes() {
        long missingStampCount = noteRepository.countByStatusAndGenerationEnqueuedAtIsNull(NoteStatus.GENERATING);
        if (missingStampCount > 0) {
            log.warn(
                    "generation.recovery.note found {} GENERATING rows without generation_enqueued_at; leaving them untouched",
                    missingStampCount
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = now.minusMinutes(properties.getGeneration().getNoteBoundMinutes());
        List<UUID> candidateIds = noteRepository.findStaleGenerationIds(
                NoteStatus.GENERATING,
                cutoff,
                PageRequest.of(0, normalizedBatchSize())
        );

        return sweep(candidateIds, noteId -> rowWriter.recoverNote(noteId, cutoff), now, "note");
    }

    private SurfaceRecoveryResult sweep(
            List<UUID> candidateIds,
            Function<UUID, Optional<OffsetDateTime>> recoverRow,
            OffsetDateTime now,
            String surface
    ) {
        int recoveredCount = 0;
        Duration maxRecoveredAge = Duration.ZERO;
        for (UUID candidateId : candidateIds) {
            try {
                Optional<OffsetDateTime> staleSince = recoverRow.apply(candidateId);
                if (staleSince.isEmpty()) {
                    continue;
                }
                recoveredCount++;
                Duration recoveredAge = Duration.between(staleSince.get(), now);
                if (recoveredAge.compareTo(maxRecoveredAge) > 0) {
                    maxRecoveredAge = recoveredAge;
                }
            } catch (RuntimeException ex) {
                // Safe to continue only because the row committed in its own transaction.
                log.warn("generation.recovery.{} row failed id={}", surface, candidateId, ex);
            }
        }
        return new SurfaceRecoveryResult(recoveredCount, maxRecoveredAge);
    }

    private int normalizedBatchSize() {
        return Math.max(1, properties.getGeneration().getRecoveryBatchSize());
    }

    public record SurfaceRecoveryResult(int recoveredCount, Duration maxRecoveredAge) {
        public static SurfaceRecoveryResult empty() {
            return new SurfaceRecoveryResult(0, Duration.ZERO);
        }
    }
}
