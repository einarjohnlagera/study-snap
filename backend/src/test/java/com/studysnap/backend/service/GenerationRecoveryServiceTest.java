package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.ExamQuestionPoolEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.repository.ExamQuestionPoolRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class GenerationRecoveryServiceTest {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_GENERATING = "GENERATING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    @Mock
    private ExamQuestionPoolRepository examQuestionPoolRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private StudyPackService studyPackService;
    @Mock
    private UserUsageService userUsageService;

    private StudySnapProperties properties;
    private GenerationRecoveryService service;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        // A REAL row writer over the mocked repositories, so these cases still exercise the
        // recoverability predicates and the status transition end to end. Only the transaction
        // boundary moved; the behaviour under test did not.
        GenerationRecoveryRowWriter rowWriter = new GenerationRecoveryRowWriter(
                examQuestionPoolRepository,
                quickReviewSessionRepository,
                noteRepository,
                studyPackService,
                userUsageService
        );
        service = new GenerationRecoveryService(
                examQuestionPoolRepository,
                quickReviewSessionRepository,
                noteRepository,
                rowWriter,
                properties
        );
        lenient().when(noteRepository.countByStatusAndGenerationEnqueuedAtIsNull(NoteStatus.GENERATING)).thenReturn(0L);
        lenient().doAnswer(invocation -> {
            NoteEntity note = invocation.getArgument(0);
            note.setStatus(NoteStatus.FAILED);
            return null;
        }).when(studyPackService).markNoteGenerationFailed(any(NoteEntity.class));
    }

    @Test
    void recoverStaleExamQuestionPools_generatingPastBoundBecomesFailed() {
        ExamQuestionPoolEntity pool = pool(STATUS_GENERATING, OffsetDateTime.now().minusHours(2));
        stubPoolCandidate(pool);
        OffsetDateTime sweepStartedAt = OffsetDateTime.now();

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleExamQuestionPools();
        OffsetDateTime sweepFinishedAt = OffsetDateTime.now();

        ArgumentCaptor<ExamQuestionPoolEntity> savedPoolCaptor = ArgumentCaptor.forClass(ExamQuestionPoolEntity.class);
        verify(examQuestionPoolRepository).save(savedPoolCaptor.capture());
        ExamQuestionPoolEntity persistedPool = savedPoolCaptor.getValue();
        assertThat(persistedPool.getGenerationStatus()).isEqualTo(STATUS_FAILED);
        assertThat(persistedPool.getGenerationStatusAt()).isBetween(sweepStartedAt, sweepFinishedAt);
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(result.maxRecoveredAge().toMinutes()).isGreaterThanOrEqualTo(119);
    }

    @Test
    void recoverStaleExamQuestionPools_pendingPastBoundBecomesFailed() {
        ExamQuestionPoolEntity pool = pool(STATUS_PENDING, OffsetDateTime.now().minusHours(2));
        stubPoolCandidate(pool);

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleExamQuestionPools();

        assertThat(pool.getGenerationStatus()).isEqualTo(STATUS_FAILED);
        assertThat(result.recoveredCount()).isEqualTo(1);
    }

    @Test
    void recoverStaleExamQuestionPools_withinEitherBoundRemainUntouched() {
        ExamQuestionPoolEntity pending = pool(STATUS_PENDING, OffsetDateTime.now().minusMinutes(10));
        ExamQuestionPoolEntity generating = pool(STATUS_GENERATING, OffsetDateTime.now().minusMinutes(10));
        when(examQuestionPoolRepository.findStaleNonTerminalIds(any(), any(), any(), any(), any()))
                .thenReturn(List.of(pending.getId(), generating.getId()));
        when(examQuestionPoolRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(examQuestionPoolRepository.findByIdForUpdate(generating.getId())).thenReturn(Optional.of(generating));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleExamQuestionPools();

        assertThat(pending.getGenerationStatus()).isEqualTo(STATUS_PENDING);
        assertThat(generating.getGenerationStatus()).isEqualTo(STATUS_GENERATING);
        assertThat(result.recoveredCount()).isZero();
        verify(examQuestionPoolRepository, never()).save(any());
    }

    @Test
    void recoverStaleExamQuestionPools_appliesEachBoundToItsOwnStatus() {
        // Both pool bounds default to 60 minutes, so pendingCutoff and generatingCutoff are the SAME
        // value and every other test passes with the two arguments swapped, or with the branches of
        // isRecoverablePool swapped. The release plans to tighten these from config after production
        // observation — which is exactly when a latent swap would start misfiring silently. Setting
        // them apart is what makes the pairing testable at all.
        properties.getGeneration().setPoolPendingBoundMinutes(10);
        properties.getGeneration().setPoolGeneratingBoundMinutes(180);

        // 30 minutes old: past the 10-minute PENDING bound, inside the 180-minute GENERATING one.
        ExamQuestionPoolEntity pending = pool(STATUS_PENDING, OffsetDateTime.now().minusMinutes(30));
        ExamQuestionPoolEntity generating = pool(STATUS_GENERATING, OffsetDateTime.now().minusMinutes(30));
        when(examQuestionPoolRepository.findStaleNonTerminalIds(any(), any(), any(), any(), any()))
                .thenReturn(List.of(pending.getId(), generating.getId()));
        when(examQuestionPoolRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(examQuestionPoolRepository.findByIdForUpdate(generating.getId())).thenReturn(Optional.of(generating));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleExamQuestionPools();

        assertThat(pending.getGenerationStatus()).isEqualTo(STATUS_FAILED);
        assertThat(generating.getGenerationStatus()).isEqualTo(STATUS_GENERATING);
        assertThat(result.recoveredCount()).isEqualTo(1);
    }

    @Test
    void recoverStaleExamQuestionPools_readyAndFailedAreNeverTouched() {
        ExamQuestionPoolEntity ready = pool(STATUS_READY, OffsetDateTime.now().minusDays(2));
        ExamQuestionPoolEntity failed = pool(STATUS_FAILED, OffsetDateTime.now().minusDays(2));
        when(examQuestionPoolRepository.findStaleNonTerminalIds(any(), any(), any(), any(), any()))
                .thenReturn(List.of(ready.getId(), failed.getId()));
        when(examQuestionPoolRepository.findByIdForUpdate(ready.getId())).thenReturn(Optional.of(ready));
        when(examQuestionPoolRepository.findByIdForUpdate(failed.getId())).thenReturn(Optional.of(failed));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleExamQuestionPools();

        assertThat(result.recoveredCount()).isZero();
        verify(examQuestionPoolRepository, never()).save(any());
    }

    @Test
    void recoverStaleExamQuestionPools_oldCreatedAtButRecentGenerationStatusAtIsNotSwept() {
        OffsetDateTime originalStatusAt = OffsetDateTime.now().minusMinutes(5);
        ExamQuestionPoolEntity reusedPool = pool(STATUS_PENDING, originalStatusAt);
        reusedPool.setCreatedAt(OffsetDateTime.now().minusMonths(6));
        stubPoolCandidate(reusedPool);

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleExamQuestionPools();

        assertThat(reusedPool.getGenerationStatus()).isEqualTo(STATUS_PENDING);
        assertThat(reusedPool.getGenerationStatusAt()).isEqualTo(originalStatusAt);
        assertThat(result.recoveredCount()).isZero();
        verify(examQuestionPoolRepository, never()).save(any());
    }

    @Test
    void recoverStaleLongExamSessions_onlyPastBoundLongExamBecomesFailed() {
        QuickReviewSessionEntity session = session(QuickReviewSessionMode.LONG_EXAM, OffsetDateTime.now().minusHours(1));
        when(quickReviewSessionRepository.findStaleSessionIds(any(), any(), any(), any()))
                .thenReturn(List.of(session.getId()));
        when(quickReviewSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleLongExamSessions();

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        assertThat(result.recoveredCount()).isEqualTo(1);
    }

    @Test
    void recoverStaleLongExamSessions_withinBoundRemainsGenerating() {
        QuickReviewSessionEntity session = session(QuickReviewSessionMode.LONG_EXAM, OffsetDateTime.now().minusMinutes(5));
        when(quickReviewSessionRepository.findStaleSessionIds(any(), any(), any(), any()))
                .thenReturn(List.of(session.getId()));
        when(quickReviewSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleLongExamSessions();

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        assertThat(result.recoveredCount()).isZero();
    }

    @Test
    void recoverStaleLongExamSessions_challengeAdaptiveAndInterviewPracticeAreNotSwept() {
        QuickReviewSessionEntity challenge = session(QuickReviewSessionMode.CHALLENGE, OffsetDateTime.now().minusDays(1));
        QuickReviewSessionEntity adaptive = session(QuickReviewSessionMode.ADAPTIVE, OffsetDateTime.now().minusDays(1));
        QuickReviewSessionEntity interview = session(QuickReviewSessionMode.ADAPTIVE, OffsetDateTime.now().minusDays(1));
        interview.setSessionState(java.util.Map.of("subMode", "interview"));
        when(quickReviewSessionRepository.findStaleSessionIds(any(), any(), any(), any()))
                .thenReturn(List.of(challenge.getId(), adaptive.getId(), interview.getId()));
        when(quickReviewSessionRepository.findByIdForUpdate(challenge.getId())).thenReturn(Optional.of(challenge));
        when(quickReviewSessionRepository.findByIdForUpdate(adaptive.getId())).thenReturn(Optional.of(adaptive));
        when(quickReviewSessionRepository.findByIdForUpdate(interview.getId())).thenReturn(Optional.of(interview));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleLongExamSessions();

        assertThat(result.recoveredCount()).isZero();
        assertThat(List.of(challenge, adaptive, interview))
                .allMatch(item -> item.getStatus() == QuickReviewSessionStatus.GENERATING);
        verify(quickReviewSessionRepository, never()).save(any());
    }

    @Test
    void recoverStaleBoardExamSessions_sweepsAStaleBoardExamAndRefundsBothMeters() {
        QuickReviewSessionEntity session = boardExamSession(OffsetDateTime.now().minusHours(1));
        when(quickReviewSessionRepository.findStaleSessionIds(any(), any(), any(), any()))
                .thenReturn(List.of(session.getId()));
        when(quickReviewSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleBoardExamSessions();

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        assertThat(result.recoveredCount()).isEqualTo(1);
        verify(userUsageService).reverseBoardExamGenerationBy(
                eq(session.getUserId()), eq(ChallengeQuizService.BOARD_EXAM_QUOTA_UNITS_PER_SESSION), any());
    }


    @Test
    void recoverStaleBoardExamSessions_selectsGeneratingChallengeRowsOlderThanTheCutoff() {
        // ⚠️ THE CANDIDATE QUERY'S OWN ARGUMENTS WERE NEVER ASSERTED. Every other Board Exam sweep test
        // stubs findStaleSessionIds(any(), any(), any(), any()), so switching the mode to LONG_EXAM left
        // the whole suite green — the row-level filter then rejects every candidate and the refund-on-crash
        // path silently recovers NOTHING. The guarantee that a crashed Board Exam is ever refunded rests
        // entirely on these four arguments, so they are pinned here.
        QuickReviewSessionEntity session = boardExamSession(OffsetDateTime.now().minusHours(1));
        when(quickReviewSessionRepository.findStaleSessionIds(any(), any(), any(), any()))
                .thenReturn(List.of(session.getId()));
        when(quickReviewSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        service.recoverStaleBoardExamSessions();

        ArgumentCaptor<QuickReviewSessionStatus> status = ArgumentCaptor.forClass(QuickReviewSessionStatus.class);
        ArgumentCaptor<QuickReviewSessionMode> mode = ArgumentCaptor.forClass(QuickReviewSessionMode.class);
        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(quickReviewSessionRepository).findStaleSessionIds(
                status.capture(), mode.capture(), cutoff.capture(), any());

        // Board Exam IS a CHALLENGE row — selecting LONG_EXAM here would sweep nothing at all.
        assertThat(mode.getValue()).isEqualTo(QuickReviewSessionMode.CHALLENGE);
        assertThat(status.getValue()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        // The cutoff must be in the past, or a freshly started exam would be swept mid-generation.
        assertThat(cutoff.getValue()).isBefore(OffsetDateTime.now());
    }

    @Test
    void recoverStaleBoardExamSessions_leavesAnOrdinaryChallengeSessionAlone() {
        // ⚠️ THE CANDIDATE QUERY CANNOT TELL THESE APART — Board Exam IS a CHALLENGE session — so the
        // discrimination happens under the row lock. Without it, the sweep would fail every learner's
        // in-flight Challenge Quiz and refund a Board Exam meter they never spent.
        QuickReviewSessionEntity boardExam = boardExamSession(OffsetDateTime.now().minusHours(1));
        QuickReviewSessionEntity challenge = session(QuickReviewSessionMode.CHALLENGE, OffsetDateTime.now().minusHours(1));
        challenge.setSessionState(java.util.Map.of(ChallengeQuizService.SESSION_STATE_MODE, "challenge"));
        when(quickReviewSessionRepository.findStaleSessionIds(any(), any(), any(), any()))
                .thenReturn(List.of(boardExam.getId(), challenge.getId()));
        when(quickReviewSessionRepository.findByIdForUpdate(boardExam.getId())).thenReturn(Optional.of(boardExam));
        when(quickReviewSessionRepository.findByIdForUpdate(challenge.getId())).thenReturn(Optional.of(challenge));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleBoardExamSessions();

        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(boardExam.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        assertThat(challenge.getStatus()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        verify(userUsageService, times(1)).reverseBoardExamGenerationBy(any(), anyInt(), any());
    }

    @Test
    void recoverStaleBoardExamSessions_withinBoundRemainsGenerating() {
        QuickReviewSessionEntity session = boardExamSession(OffsetDateTime.now().minusMinutes(5));
        when(quickReviewSessionRepository.findStaleSessionIds(any(), any(), any(), any()))
                .thenReturn(List.of(session.getId()));
        when(quickReviewSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleBoardExamSessions();

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        assertThat(result.recoveredCount()).isZero();
        verify(userUsageService, never()).reverseBoardExamGenerationBy(any(), anyInt(), any());
    }

    private QuickReviewSessionEntity boardExamSession(OffsetDateTime createdAt) {
        QuickReviewSessionEntity session = session(QuickReviewSessionMode.CHALLENGE, createdAt);
        session.setSessionState(java.util.Map.of(
                ChallengeQuizService.SESSION_STATE_MODE, ChallengeQuizService.MODE_BOARD_EXAM,
                ChallengeQuizService.SESSION_STATE_BOARD_EXAM_QUOTA_RESERVED, true
        ));
        return session;
    }

    @Test
    void recoverStaleNotes_pastBoundUsesSharedFailedTransition() {
        NoteEntity note = note(OffsetDateTime.now().minusHours(3));
        when(noteRepository.findStaleGenerationIds(any(), any(), any())).thenReturn(List.of(note.getId()));
        when(noteRepository.findByIdForUpdate(note.getId())).thenReturn(Optional.of(note));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleNotes();

        assertThat(note.getStatus()).isEqualTo(NoteStatus.FAILED);
        assertThat(result.recoveredCount()).isEqualTo(1);
        verify(studyPackService).markNoteGenerationFailed(note);
    }

    @Test
    void recoverStaleNotes_nullStampIsLeftAloneAndWarned(CapturedOutput output) {
        NoteEntity note = note(null);
        when(noteRepository.countByStatusAndGenerationEnqueuedAtIsNull(NoteStatus.GENERATING)).thenReturn(1L);
        when(noteRepository.findStaleGenerationIds(any(), any(), any())).thenReturn(List.of(note.getId()));
        when(noteRepository.findByIdForUpdate(note.getId())).thenReturn(Optional.of(note));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleNotes();

        assertThat(result.recoveredCount()).isZero();
        assertThat(note.getStatus()).isEqualTo(NoteStatus.GENERATING);
        assertThat(output).contains("without generation_enqueued_at; leaving them untouched");
        verify(studyPackService, never()).markNoteGenerationFailed(any(NoteEntity.class));
    }

    @Test
    void recoverStalePools_isIdempotent() {
        ExamQuestionPoolEntity pool = pool(STATUS_GENERATING, OffsetDateTime.now().minusHours(2));
        when(examQuestionPoolRepository.findStaleNonTerminalIds(any(), any(), any(), any(), any()))
                .thenReturn(List.of(pool.getId()), List.of(pool.getId()));
        when(examQuestionPoolRepository.findByIdForUpdate(pool.getId())).thenReturn(Optional.of(pool));

        GenerationRecoveryService.SurfaceRecoveryResult first = service.recoverStaleExamQuestionPools();
        GenerationRecoveryService.SurfaceRecoveryResult second = service.recoverStaleExamQuestionPools();

        assertThat(first.recoveredCount()).isEqualTo(1);
        assertThat(second.recoveredCount()).isZero();
        verify(examQuestionPoolRepository, times(1)).save(pool);
    }

    @Test
    void recoverStalePools_oneBadRowDoesNotAbortBatch() {
        UUID badId = UUID.randomUUID();
        ExamQuestionPoolEntity good = pool(STATUS_GENERATING, OffsetDateTime.now().minusHours(2));
        when(examQuestionPoolRepository.findStaleNonTerminalIds(any(), any(), any(), any(), any()))
                .thenReturn(List.of(badId, good.getId()));
        when(examQuestionPoolRepository.findByIdForUpdate(badId)).thenThrow(new IllegalStateException("concurrent delete"));
        when(examQuestionPoolRepository.findByIdForUpdate(good.getId())).thenReturn(Optional.of(good));

        GenerationRecoveryService.SurfaceRecoveryResult result = service.recoverStaleExamQuestionPools();

        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(good.getGenerationStatus()).isEqualTo(STATUS_FAILED);
    }

    @Test
    void recoverStalePools_usesConfiguredBatchBound() {
        properties.getGeneration().setRecoveryBatchSize(7);
        when(examQuestionPoolRepository.findStaleNonTerminalIds(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.recoverStaleExamQuestionPools();

        verify(examQuestionPoolRepository).findStaleNonTerminalIds(
                eq(STATUS_PENDING),
                any(),
                eq(STATUS_GENERATING),
                any(),
                eq(Pageable.ofSize(7))
        );
    }

    @Test
    void transactionBoundarySitsOnTheRowWriterAndNotOnTheSweepMethods() throws NoSuchMethodException {
        // This is the invariant the per-row catch depends on, and no behavioural test can prove it:
        // a Mockito test has no transaction manager, so a loop-wide @Transactional would still let
        // every assertion above pass while production silently discarded the whole batch. If the
        // sweep loop were transactional, one failing row would mark it rollback-only and every row
        // already recovered in that batch would be lost — and because candidates are ordered
        // oldest-first, one reliably-failing row would block the sweeper behind it forever.
        assertThat(GenerationRecoveryService.class.getMethod("recoverStaleExamQuestionPools")
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(GenerationRecoveryService.class.getMethod("recoverStaleLongExamSessions")
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(GenerationRecoveryService.class.getMethod("recoverStaleNotes")
                .isAnnotationPresent(Transactional.class)).isFalse();

        // Method-level absence is not enough: a CLASS-level @Transactional on the sweep service —
        // precisely the "simplification" its Javadoc warns against — would wrap the loop in one
        // transaction while every method-level assertion above still passed.
        assertThat(GenerationRecoveryService.class.isAnnotationPresent(Transactional.class)).isFalse();

        assertThat(GenerationRecoveryRowWriter.class
                .getMethod("recoverPool", UUID.class, OffsetDateTime.class, OffsetDateTime.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(GenerationRecoveryRowWriter.class
                .getMethod("recoverLongExamSession", UUID.class, OffsetDateTime.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(GenerationRecoveryRowWriter.class
                .getMethod("recoverNote", UUID.class, OffsetDateTime.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private void stubPoolCandidate(ExamQuestionPoolEntity pool) {
        when(examQuestionPoolRepository.findStaleNonTerminalIds(any(), any(), any(), any(), any()))
                .thenReturn(List.of(pool.getId()));
        when(examQuestionPoolRepository.findByIdForUpdate(pool.getId())).thenReturn(Optional.of(pool));
    }

    private ExamQuestionPoolEntity pool(String status, OffsetDateTime generationStatusAt) {
        ExamQuestionPoolEntity pool = new ExamQuestionPoolEntity();
        pool.setId(UUID.randomUUID());
        pool.setGenerationStatus(status);
        pool.setGenerationStatusAt(generationStatusAt);
        pool.setCreatedAt(OffsetDateTime.now().minusDays(1));
        return pool;
    }

    private QuickReviewSessionEntity session(QuickReviewSessionMode mode, OffsetDateTime createdAt) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setSessionMode(mode);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCreatedAt(createdAt);
        return session;
    }

    private NoteEntity note(OffsetDateTime generationEnqueuedAt) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setStatus(NoteStatus.GENERATING);
        note.setGenerationEnqueuedAt(generationEnqueuedAt);
        return note;
    }
}
