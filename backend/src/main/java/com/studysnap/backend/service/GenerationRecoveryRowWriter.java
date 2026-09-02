package com.studysnap.backend.service;

import com.studysnap.backend.entity.ExamQuestionPoolEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.repository.ExamQuestionPoolRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Commits one recovered row per transaction, on purpose.
 *
 * <p>The sweep methods in {@link GenerationRecoveryService} catch per row and continue, so that one
 * unreadable or contended row cannot stop the rest of a batch. That guarantee is only real if each
 * row commits independently. With a single transaction spanning the loop, an exception escaping a
 * Spring Data repository call marks the shared transaction rollback-only — the {@code catch} still
 * swallows it and the loop still finishes, but the final commit fails and **every row the sweep just
 * recovered is discarded**. Because candidates are ordered oldest-first, one row that reliably throws
 * would poison every batch forever and permanently block the sweeper behind it.
 *
 * <p><strong>This is NOT the {@code REQUIRES_NEW} pattern reverted in v0.81.0.</strong> That failure
 * was a nested transaction opened <em>inside</em> an outer one: a second connection could not see the
 * uncommitted {@code quick_review_sessions} row, so every FK check failed. Here the caller holds no
 * transaction at all, so these methods open the only one — plain {@code REQUIRED} propagation, one
 * connection, nothing invisible. Do not "simplify" this back onto the sweep methods.
 */
@Service
@RequiredArgsConstructor
public class GenerationRecoveryRowWriter {
    private static final String POOL_STATUS_PENDING = "PENDING";
    private static final String POOL_STATUS_GENERATING = "GENERATING";
    private static final String POOL_STATUS_FAILED = "FAILED";

    private final ExamQuestionPoolRepository examQuestionPoolRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final NoteRepository noteRepository;
    private final StudyPackService studyPackService;
    private final UserUsageService userUsageService;

    /**
     * @return when recovered, the instant the row became stale, for the caller's age reporting
     */
    @Transactional
    public Optional<OffsetDateTime> recoverPool(UUID poolId, OffsetDateTime pendingCutoff, OffsetDateTime generatingCutoff) {
        return examQuestionPoolRepository.findByIdForUpdate(poolId)
                .filter(pool -> isRecoverablePool(pool, pendingCutoff, generatingCutoff))
                .map(pool -> {
                    OffsetDateTime staleSince = pool.getGenerationStatusAt();
                    pool.setGenerationStatus(POOL_STATUS_FAILED);
                    pool.setGenerationStatusAt(OffsetDateTime.now(ZoneOffset.UTC));
                    examQuestionPoolRepository.save(pool);
                    return staleSince;
                });
    }

    @Transactional
    public Optional<OffsetDateTime> recoverLongExamSession(UUID sessionId, OffsetDateTime cutoff) {
        return quickReviewSessionRepository.findByIdForUpdate(sessionId)
                .filter(session -> isRecoverableLongExamSession(session, cutoff))
                .map(session -> {
                    OffsetDateTime staleSince = session.getCreatedAt();
                    markLongExamSessionFailed(session);
                    return staleSince;
                });
    }

    /**
     * Fails a GENERATING Long Exam session from the async generation catch, refunding its quota unit once.
     *
     * <p>⚠️ The shared piece is {@link #markLongExamSessionFailed}, NOT this method. This one has a single
     * caller ({@code LongExamService}'s async catch); the stale-session sweeper reaches the same private
     * writer through {@code recoverLongExamSession}. An earlier javadoc claimed both callers used this
     * method, which would let a reader reason wrongly about sweeper behaviour.
     */
    @Transactional
    public void failLongExamSession(UUID sessionId) {
        quickReviewSessionRepository.findByIdForUpdate(sessionId)
                .filter(session -> session.getSessionMode() == QuickReviewSessionMode.LONG_EXAM)
                // ⚠️ STATUS GUARD, matching recoverLongExamSession. Without it this public method would
                // fail and refund an IN_PROGRESS or COMPLETED exam — destroying a live or finished session
                // — if any future caller passed one. The mode filter alone protects other quiz modes; this
                // protects Long Exam sessions that are past generation.
                .filter(session -> session.getStatus() == QuickReviewSessionStatus.GENERATING)
                .ifPresent(this::markLongExamSessionFailed);
    }

    private void markLongExamSessionFailed(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FAILED);
        Map<String, Object> state = new LinkedHashMap<>(session.getSessionState() == null ? Map.of() : session.getSessionState());
        boolean quotaReserved = Boolean.TRUE.equals(state.get(LongExamService.SESSION_STATE_LONG_EXAM_QUOTA_RESERVED));
        boolean quotaReversed = Boolean.TRUE.equals(state.get(LongExamService.SESSION_STATE_LONG_EXAM_QUOTA_REVERSED));
        if (quotaReserved && !quotaReversed) {
            userUsageService.reverseLongExamGenerationBy(
                    session.getUserId(),
                    LongExamService.QUOTA_UNITS_PER_SESSION,
                    session.getCreatedAt()
            );
            state.put(LongExamService.SESSION_STATE_LONG_EXAM_QUOTA_REVERSED, true);
        }
        session.setSessionState(state);
        quickReviewSessionRepository.save(session);
    }

    @Transactional
    public Optional<OffsetDateTime> recoverNote(UUID noteId, OffsetDateTime cutoff) {
        return noteRepository.findByIdForUpdate(noteId)
                .filter(note -> isRecoverableNote(note, cutoff))
                .map(note -> {
                    OffsetDateTime staleSince = note.getGenerationEnqueuedAt();
                    studyPackService.markNoteGenerationFailed(note);
                    return note.getStatus() == NoteStatus.FAILED ? staleSince : null;
                })
                .filter(staleSince -> staleSince != null);
    }

    private boolean isRecoverablePool(
            ExamQuestionPoolEntity pool,
            OffsetDateTime pendingCutoff,
            OffsetDateTime generatingCutoff
    ) {
        if (pool.getGenerationStatusAt() == null) {
            return false;
        }
        if (POOL_STATUS_PENDING.equals(pool.getGenerationStatus())) {
            return pool.getGenerationStatusAt().isBefore(pendingCutoff);
        }
        if (POOL_STATUS_GENERATING.equals(pool.getGenerationStatus())) {
            return pool.getGenerationStatusAt().isBefore(generatingCutoff);
        }
        return false;
    }

    private boolean isRecoverableLongExamSession(QuickReviewSessionEntity session, OffsetDateTime cutoff) {
        return session.getStatus() == QuickReviewSessionStatus.GENERATING
                && session.getSessionMode() == QuickReviewSessionMode.LONG_EXAM
                && session.getCreatedAt() != null
                && session.getCreatedAt().isBefore(cutoff);
    }

    private boolean isRecoverableNote(NoteEntity note, OffsetDateTime cutoff) {
        return note.getStatus() == NoteStatus.GENERATING
                && note.getGenerationEnqueuedAt() != null
                && note.getGenerationEnqueuedAt().isBefore(cutoff);
    }
}
