package com.studysnap.backend.service;

import com.studysnap.backend.dto.RecentQuizSessionHistoryResponse;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizSessionHistoryServiceTest {
    private static final String SESSION_STATE_SOURCE_NOTE_REFS = "sourceNoteRefs";

    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;

    private QuizSessionHistoryService quizSessionHistoryService;

    @BeforeEach
    void setUp() {
        quizSessionHistoryService = new QuizSessionHistoryService(quickReviewSessionRepository);
    }

    @Test
    void findLatestSessionCompletedAtByNoteIds_includesParticipatingLongExamNotes() {
        UUID userId = UUID.randomUUID();
        UUID originNoteId = UUID.randomUUID();
        UUID participatingNoteId = UUID.randomUUID();
        UUID quickReviewNoteId = UUID.randomUUID();
        OffsetDateTime oldest = OffsetDateTime.parse("2026-05-20T10:00:00Z");
        OffsetDateTime newest = OffsetDateTime.parse("2026-05-21T10:00:00Z");
        QuickReviewSessionEntity quickReview = buildSession(
                userId,
                quickReviewNoteId,
                QuickReviewSessionMode.QUICK_REVIEW,
                oldest,
                null
        );
        QuickReviewSessionEntity longExam = buildSession(
                userId,
                originNoteId,
                QuickReviewSessionMode.LONG_EXAM,
                newest,
                Map.of(SESSION_STATE_SOURCE_NOTE_REFS, List.of(
                        Map.of("noteId", originNoteId.toString()),
                        Map.of("noteId", participatingNoteId.toString())
                ))
        );
        when(quickReviewSessionRepository.findByUserIdAndStatusAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).thenReturn(List.of(longExam, quickReview));

        Map<UUID, OffsetDateTime> completedAtByNoteId = quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(
                userId,
                List.of(originNoteId, participatingNoteId, quickReviewNoteId)
        );

        assertThat(completedAtByNoteId)
                .containsEntry(originNoteId, newest)
                .containsEntry(participatingNoteId, newest)
                .containsEntry(quickReviewNoteId, oldest);
    }

    @Test
    void findLatestSessionCompletedAtByNoteIds_includesParticipatingBoardExamNotes() {
        UUID userId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-05-21T10:00:00Z");
        QuickReviewSessionEntity boardExam = buildSession(
                userId,
                primaryNoteId,
                QuickReviewSessionMode.CHALLENGE,
                completedAt,
                Map.of(
                        "mode", "board_exam",
                        SESSION_STATE_SOURCE_NOTE_REFS, List.of(
                                Map.of("noteId", primaryNoteId.toString()),
                                Map.of("noteId", additionalNoteId.toString())
                        )
                )
        );
        when(quickReviewSessionRepository.findByUserIdAndStatusAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).thenReturn(List.of(boardExam));

        Map<UUID, OffsetDateTime> completedAtByNoteId = quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(
                userId,
                List.of(primaryNoteId, additionalNoteId)
        );

        assertThat(completedAtByNoteId)
                .containsEntry(primaryNoteId, completedAt)
                .containsEntry(additionalNoteId, completedAt);
    }

    @Test
    void listRecentSessions_surfacesBoardExamSessionOnAdditionalSourceNote() {
        UUID userId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-05-21T10:00:00Z");
        QuickReviewSessionEntity boardExam = buildSession(
                userId,
                primaryNoteId,
                QuickReviewSessionMode.CHALLENGE,
                completedAt,
                Map.of(
                        "mode", "board_exam",
                        SESSION_STATE_SOURCE_NOTE_REFS, List.of(
                                Map.of("noteId", primaryNoteId.toString()),
                                Map.of("noteId", additionalNoteId.toString())
                        )
                )
        );
        when(quickReviewSessionRepository.findByUserIdAndStatusAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).thenReturn(List.of(boardExam));

        List<RecentQuizSessionHistoryResponse> sessions = quizSessionHistoryService.listRecentSessions(
                additionalNoteId.toString(),
                userId,
                10
        );

        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().sessionMode()).isEqualTo("BOARD_EXAM");
        assertThat(sessions.getFirst().participatingNoteCount()).isEqualTo(2);
    }

    @Test
    void listRecentSessions_mapsEveryStoredModeAndLongExamParticipationCount() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID otherNoteId = UUID.randomUUID();
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-05-21T10:00:00Z");
        QuickReviewSessionEntity quickReview = buildSession(userId, noteId, QuickReviewSessionMode.QUICK_REVIEW, completedAt, null);
        QuickReviewSessionEntity challenge = buildSession(userId, noteId, QuickReviewSessionMode.CHALLENGE, completedAt, null);
        QuickReviewSessionEntity boardExam = buildSession(
                userId,
                noteId,
                QuickReviewSessionMode.CHALLENGE,
                completedAt,
                Map.of("mode", "board_exam")
        );
        QuickReviewSessionEntity adaptive = buildSession(userId, noteId, QuickReviewSessionMode.ADAPTIVE, completedAt, null);
        QuickReviewSessionEntity interview = buildSession(
                userId,
                noteId,
                QuickReviewSessionMode.ADAPTIVE,
                completedAt,
                Map.of("subMode", "INTERVIEW")
        );
        QuickReviewSessionEntity longExam = buildSession(
                userId,
                otherNoteId,
                QuickReviewSessionMode.LONG_EXAM,
                completedAt,
                Map.of(SESSION_STATE_SOURCE_NOTE_REFS, List.of(
                        Map.of("noteId", otherNoteId.toString()),
                        Map.of("noteId", noteId.toString())
                ))
        );
        when(quickReviewSessionRepository.findByUserIdAndStatusAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).thenReturn(List.of(quickReview, challenge, boardExam, adaptive, interview, longExam));

        List<RecentQuizSessionHistoryResponse> sessions = quizSessionHistoryService.listRecentSessions(
                noteId.toString(),
                userId,
                10
        );

        assertThat(sessions).extracting(RecentQuizSessionHistoryResponse::sessionMode)
                .containsExactly(
                        "QUICK_REVIEW",
                        "CHALLENGE",
                        "BOARD_EXAM",
                        "ADAPTIVE",
                        "INTERVIEW_PRACTICE",
                        "LONG_EXAM"
                );
        assertThat(sessions.getLast().participatingNoteCount()).isEqualTo(2);
    }

    private QuickReviewSessionEntity buildSession(
            UUID userId,
            UUID noteId,
            QuickReviewSessionMode sessionMode,
            OffsetDateTime completedAt,
            Map<String, Object> sessionState
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(UUID.randomUUID());
        session.setNoteId(noteId);
        session.setSessionMode(sessionMode);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(5);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(5);
        session.setCorrectAnswers(4);
        session.setScorePercentage(BigDecimal.valueOf(80));
        session.setRetryCount(0);
        session.setSessionState(sessionState);
        session.setCreatedAt(completedAt.minusMinutes(5));
        session.setCompletedAt(completedAt);
        return session;
    }
}
