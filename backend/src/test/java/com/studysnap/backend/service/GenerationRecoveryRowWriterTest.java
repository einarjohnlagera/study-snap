package com.studysnap.backend.service;

import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.repository.ExamQuestionPoolRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationRecoveryRowWriterTest {
    @Mock private ExamQuestionPoolRepository poolRepository;
    @Mock private QuickReviewSessionRepository sessionRepository;
    @Mock private NoteRepository noteRepository;
    @Mock private StudyPackService studyPackService;
    @Mock private UserUsageService userUsageService;
    private GenerationRecoveryRowWriter writer;

    @BeforeEach
    void setUp() {
        writer = new GenerationRecoveryRowWriter(poolRepository, sessionRepository, noteRepository, studyPackService, userUsageService);
    }

    @Test
    void failLongExamSession_refundsOnceAndRecordsTheIdempotencyFlag() {
        QuickReviewSessionEntity session = generatingSession(true);
        when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        writer.failLongExamSession(session.getId());
        writer.failLongExamSession(session.getId());

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        assertThat(session.getSessionState()).containsEntry(LongExamService.SESSION_STATE_LONG_EXAM_QUOTA_REVERSED, true);
        verify(userUsageService, times(1)).reverseLongExamGenerationBy(
                session.getUserId(), LongExamService.QUOTA_UNITS_PER_SESSION, session.getCreatedAt());
    }

    @Test
    void recoverLongExamSession_refundsStaleReservedSession() {
        QuickReviewSessionEntity session = generatingSession(true);
        when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        writer.recoverLongExamSession(session.getId(), OffsetDateTime.now().plusMinutes(1));

        verify(userUsageService).reverseLongExamGenerationBy(
                session.getUserId(), LongExamService.QUOTA_UNITS_PER_SESSION, session.getCreatedAt());
    }

    @Test
    void failLongExamSession_doesNotRefundPreReleaseSessionWithoutReservation() {
        QuickReviewSessionEntity session = generatingSession(false);
        when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        writer.failLongExamSession(session.getId());

        verify(userUsageService, times(0)).reverseLongExamGenerationBy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failBoardExamSession_refundsBothMetersOnceAndRecordsTheIdempotencyFlag() {
        QuickReviewSessionEntity session = generatingBoardExamSession(true, false);
        when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        writer.failBoardExamSession(session.getId());

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        assertThat(session.getSessionState())
                .containsEntry(ChallengeQuizService.SESSION_STATE_BOARD_EXAM_QUOTA_REVERSED, true);
        // ONE call reverses BOTH meters; a split refund is exactly what the single stamp exists to prevent.
        verify(userUsageService, times(1)).reverseBoardExamGenerationBy(
                session.getUserId(), ChallengeQuizService.BOARD_EXAM_QUOTA_UNITS_PER_SESSION, session.getCreatedAt());
    }

    @Test
    void failBoardExamSession_doesNotRefundASessionAlreadyStampedReversed() {
        // ⚠️ THE SESSION IS STILL GENERATING ON PURPOSE. Calling the method twice proves nothing here: the
        // second call is dropped by the GENERATING filter, so that test passes even with the stamp check
        // deleted. Only a reserved-and-already-reversed row that is still eligible isolates the stamp.
        QuickReviewSessionEntity session = generatingBoardExamSession(true, true);
        when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        writer.failBoardExamSession(session.getId());

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        verify(userUsageService, never()).reverseBoardExamGenerationBy(any(), anyInt(), any());
    }

    @Test
    void failBoardExamSession_doesNotRefundASessionThatNeverReservedQuota() {
        QuickReviewSessionEntity session = generatingBoardExamSession(false, false);
        when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        writer.failBoardExamSession(session.getId());

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        verify(userUsageService, never()).reverseBoardExamGenerationBy(any(), anyInt(), any());
    }

    @Test
    void failBoardExamSession_ignoresAnOrdinaryChallengeSession() {
        // Board Exam rides the CHALLENGE session mode, so the board-exam marker in session state — not the
        // mode column — is what separates a refundable row from an ordinary Challenge Quiz.
        QuickReviewSessionEntity session = generatingBoardExamSession(true, false);
        session.setSessionState(Map.of(ChallengeQuizService.SESSION_STATE_MODE, "challenge"));
        when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        writer.failBoardExamSession(session.getId());

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        verify(userUsageService, never()).reverseBoardExamGenerationBy(any(), anyInt(), any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void recoverBoardExamSession_refundsBothMetersForAStaleReservedSession() {
        QuickReviewSessionEntity session = generatingBoardExamSession(true, false);
        when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        writer.recoverBoardExamSession(session.getId(), OffsetDateTime.now().plusMinutes(1));

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        verify(userUsageService).reverseBoardExamGenerationBy(
                session.getUserId(), ChallengeQuizService.BOARD_EXAM_QUOTA_UNITS_PER_SESSION, session.getCreatedAt());
    }

    @Test
    void recoverBoardExamSessionAndTheAsyncCatchTogetherRefundExactlyOnce() {
        // The two refund paths meet on the same row; between them they must charge back once, never twice.
        QuickReviewSessionEntity session = generatingBoardExamSession(true, false);
        when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        writer.failBoardExamSession(session.getId());
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        writer.recoverBoardExamSession(session.getId(), OffsetDateTime.now().plusMinutes(1));

        verify(userUsageService, times(1)).reverseBoardExamGenerationBy(
                session.getUserId(), ChallengeQuizService.BOARD_EXAM_QUOTA_UNITS_PER_SESSION, session.getCreatedAt());
    }

    private QuickReviewSessionEntity generatingBoardExamSession(boolean reserved, boolean reversed) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(UUID.randomUUID());
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(2));
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put(ChallengeQuizService.SESSION_STATE_MODE, ChallengeQuizService.MODE_BOARD_EXAM);
        if (reserved) {
            state.put(ChallengeQuizService.SESSION_STATE_BOARD_EXAM_QUOTA_RESERVED, true);
        }
        if (reversed) {
            state.put(ChallengeQuizService.SESSION_STATE_BOARD_EXAM_QUOTA_REVERSED, true);
        }
        session.setSessionState(state);
        return session;
    }

    private QuickReviewSessionEntity generatingSession(boolean reserved) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(UUID.randomUUID());
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(2));
        session.setSessionMode(QuickReviewSessionMode.LONG_EXAM);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setSessionState(reserved ? Map.of(LongExamService.SESSION_STATE_LONG_EXAM_QUOTA_RESERVED, true) : Map.of());
        return session;
    }

    @Test
    void failLongExamSession_ignoresANonLongExamSession() {
        // ⚠️ failLongExamSession is a PUBLIC method on the SHARED recovery writer. Its mode filter is the
        // only thing stopping it failing a Quick Review / Challenge / Board Exam / Adaptive / Interview
        // session AND refunding a Long Exam quota unit for it. Deleting that filter left the suite green.
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity challenge = generatingSession(true);
        challenge.setId(sessionId);
        challenge.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(challenge));

        writer.failLongExamSession(sessionId);

        assertThat(challenge.getStatus()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        verify(userUsageService, never()).reverseLongExamGenerationBy(any(), anyInt(), any());
        verify(sessionRepository, never()).save(any(QuickReviewSessionEntity.class));
    }

    @Test
    void failLongExamSession_ignoresASessionPastGeneration() {
        // ⚠️ Unlike its sibling recoverLongExamSession, this method had NO status guard. Called with an
        // IN_PROGRESS exam it would destroy a live session and refund it.
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity live = generatingSession(true);
        live.setId(sessionId);
        live.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(live));

        writer.failLongExamSession(sessionId);

        assertThat(live.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        verify(userUsageService, never()).reverseLongExamGenerationBy(any(), anyInt(), any());
    }
}