package com.studysnap.backend.service;

import com.studysnap.backend.dto.RecentQuizSessionHistoryResponse;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.testutil.builders.QuickReviewSessionEntityBuilder;
import com.studysnap.backend.util.QuizSessionStateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class QuizSessionHistoryServiceTest {
    private static final String SESSION_STATE_MODE = "mode";
    private static final String SESSION_STATE_SOURCE_NOTE_REFS = "sourceNoteRefs";
    private static final String SOURCE_NOTE_ID_KEY = "noteId";
    private static final String SESSION_METADATA_WEAK_CONCEPTS = "weakConcepts";
    private static final String MODE_BOARD_EXAM = "board_exam";
    private static final String SUB_MODE_INTERVIEW = "INTERVIEW";
    private static final String HISTORY_MODE_BOARD_EXAM = "BOARD_EXAM";
    private static final String HISTORY_MODE_INTERVIEW_PRACTICE = "INTERVIEW_PRACTICE";
    private static final String PERFORMANCE_LEVEL_GOOD = "Good";

    @Autowired
    private QuizSessionHistoryService quizSessionHistoryService;
    @Autowired
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists quick_review_sessions (
                    id uuid primary key,
                    user_id uuid not null,
                    study_pack_id uuid not null,
                    note_id uuid not null,
                    session_mode varchar(32) not null,
                    status varchar(32) not null,
                    current_question_index integer not null,
                    current_round varchar(16) not null,
                    total_questions integer not null,
                    correct_answers integer,
                    score_percentage numeric(5,2),
                    retry_count integer,
                    duration_seconds integer,
                    confidence_level varchar(16),
                    session_metadata json,
                    session_state json,
                    quota_exempt boolean not null default false,
                    model_used varchar(64),
                    input_tokens integer,
                    output_tokens integer,
                    cached_input_tokens integer,
                    created_at timestamp with time zone not null,
                    completed_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("delete from quick_review_sessions");
        jdbcTemplate.execute("alter table quick_review_sessions alter column note_id set not null");
        jdbcTemplate.execute("alter table quick_review_sessions add column if not exists quota_exempt boolean not null default false");
    }

    @Test
    void findLatestSessionCompletedAtByNoteIds_mergesDirectAndMultiNoteParticipation() {
        UUID userId = UUID.randomUUID();
        UUID quickReviewNoteId = UUID.randomUUID();
        UUID challengeNoteId = UUID.randomUUID();
        UUID adaptiveNoteId = UUID.randomUUID();
        UUID unpracticedNoteId = UUID.randomUUID();
        UUID otherPrimaryNoteId = UUID.randomUUID();
        OffsetDateTime base = OffsetDateTime.parse("2026-05-21T10:00:00Z");
        OffsetDateTime quickReviewCompletedAt = base.minusHours(6);
        OffsetDateTime directChallengeCompletedAt = base.minusHours(5);
        OffsetDateTime adaptiveCompletedAt = base.minusHours(4);
        OffsetDateTime longExamCompletedAt = base.minusHours(2);
        OffsetDateTime boardExamCompletedAt = base.minusHours(1);

        saveSession(userId, quickReviewNoteId, QuickReviewSessionMode.QUICK_REVIEW, quickReviewCompletedAt, null, null);
        saveSession(userId, challengeNoteId, QuickReviewSessionMode.CHALLENGE, directChallengeCompletedAt, null, null);
        saveSession(userId, adaptiveNoteId, QuickReviewSessionMode.ADAPTIVE, adaptiveCompletedAt, null, null);
        saveSession(
                userId,
                otherPrimaryNoteId,
                QuickReviewSessionMode.LONG_EXAM,
                longExamCompletedAt,
                sourceRefsState(otherPrimaryNoteId, quickReviewNoteId),
                null
        );
        saveSession(
                userId,
                otherPrimaryNoteId,
                QuickReviewSessionMode.CHALLENGE,
                boardExamCompletedAt,
                boardExamState(otherPrimaryNoteId, challengeNoteId),
                null
        );
        saveSession(
                userId,
                unpracticedNoteId,
                QuickReviewSessionMode.CHALLENGE,
                base.plusHours(1),
                null,
                null,
                QuickReviewSessionStatus.IN_PROGRESS
        );

        Map<UUID, OffsetDateTime> completedAtByNoteId = quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(
                userId,
                List.of(quickReviewNoteId, challengeNoteId, adaptiveNoteId, unpracticedNoteId)
        );

        assertThat(completedAtByNoteId)
                .containsEntry(quickReviewNoteId, longExamCompletedAt)
                .containsEntry(challengeNoteId, boardExamCompletedAt)
                .containsEntry(adaptiveNoteId, adaptiveCompletedAt)
                .doesNotContainKey(unpracticedNoteId);
        assertThat(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, List.of())).isEmpty();
    }

    @Test
    void listRecentSessions_filtersCandidatesThenPreservesModesLimitOrderAndMetadata() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID otherNoteId = UUID.randomUUID();
        OffsetDateTime base = OffsetDateTime.parse("2026-05-21T10:00:00Z");
        saveSession(userId, noteId, QuickReviewSessionMode.QUICK_REVIEW, base.plusMinutes(5), null, null);
        saveSession(userId, noteId, QuickReviewSessionMode.CHALLENGE, base.plusMinutes(4), null, null);
        saveSession(userId, noteId, QuickReviewSessionMode.ADAPTIVE, base.plusMinutes(3), null, null);
        saveSession(
                userId,
                otherNoteId,
                QuickReviewSessionMode.LONG_EXAM,
                base.plusMinutes(2),
                sourceRefsState(otherNoteId, noteId),
                Map.of(SESSION_METADATA_WEAK_CONCEPTS, List.of("Respiration", "", "Genetics"))
        );
        saveSession(
                userId,
                otherNoteId,
                QuickReviewSessionMode.CHALLENGE,
                base.plusMinutes(1),
                boardExamState(otherNoteId, noteId),
                null
        );
        saveSession(
                userId,
                noteId,
                QuickReviewSessionMode.ADAPTIVE,
                base,
                Map.of("subMode", SUB_MODE_INTERVIEW),
                null
        );

        List<RecentQuizSessionHistoryResponse> sessions = quizSessionHistoryService.listRecentSessions(
                noteId.toString(),
                userId,
                10
        );
        List<RecentQuizSessionHistoryResponse> limitedSessions = quizSessionHistoryService.listRecentSessions(
                noteId.toString(),
                userId,
                3
        );

        assertThat(sessions).extracting(RecentQuizSessionHistoryResponse::sessionMode)
                .containsExactly(
                        QuickReviewSessionMode.CHALLENGE.name(),
                        QuickReviewSessionMode.ADAPTIVE.name(),
                        QuickReviewSessionMode.LONG_EXAM.name(),
                        HISTORY_MODE_BOARD_EXAM,
                        HISTORY_MODE_INTERVIEW_PRACTICE
                );
        assertThat(sessions).extracting(RecentQuizSessionHistoryResponse::completedAt)
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(sessions.get(2).participatingNoteCount()).isEqualTo(2);
        assertThat(sessions.get(2).weakConcepts()).containsExactly("Respiration", "Genetics");
        assertThat(sessions.get(3).performanceLevel()).isEqualTo(PERFORMANCE_LEVEL_GOOD);
        assertThat(limitedSessions).extracting(RecentQuizSessionHistoryResponse::sessionMode)
                .containsExactly(
                        QuickReviewSessionMode.CHALLENGE.name(),
                        QuickReviewSessionMode.ADAPTIVE.name(),
                        QuickReviewSessionMode.LONG_EXAM.name()
                );
    }

    @Test
    void optimizedMethodsMatchLegacyLoadAllThenFilterResultsForMixedModeFixture() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID secondaryNoteId = UUID.randomUUID();
        UUID unrelatedNoteId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        OffsetDateTime base = OffsetDateTime.parse("2026-05-21T10:00:00Z");
        saveSession(userId, noteId, QuickReviewSessionMode.QUICK_REVIEW, base.plusMinutes(6), null, null);
        saveSession(userId, noteId, QuickReviewSessionMode.CHALLENGE, base.plusMinutes(5), null, null);
        saveSession(userId, noteId, QuickReviewSessionMode.ADAPTIVE, base.plusMinutes(4), Map.of("subMode", SUB_MODE_INTERVIEW), null);
        saveSession(userId, unrelatedNoteId, QuickReviewSessionMode.LONG_EXAM, base.plusMinutes(3), sourceRefsState(unrelatedNoteId, secondaryNoteId), null);
        saveSession(userId, unrelatedNoteId, QuickReviewSessionMode.CHALLENGE, base.plusMinutes(2), boardExamState(unrelatedNoteId, noteId), null);
        saveSession(userId, unrelatedNoteId, QuickReviewSessionMode.CHALLENGE, base.plusMinutes(1), null, null);
        saveSession(otherUserId, noteId, QuickReviewSessionMode.CHALLENGE, base.plusMinutes(10), null, null);
        saveSession(userId, noteId, QuickReviewSessionMode.CHALLENGE, base.plusMinutes(20), null, null, QuickReviewSessionStatus.IN_PROGRESS);

        List<UUID> requestedNoteIds = List.of(noteId, secondaryNoteId, unrelatedNoteId);

        assertThat(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, requestedNoteIds))
                .isEqualTo(legacyLatestSessionCompletedAtByNoteIds(userId, requestedNoteIds));
        assertThat(quizSessionHistoryService.listRecentSessions(noteId.toString(), userId, 10))
                .isEqualTo(legacyListRecentSessions(noteId, userId, 10));
        assertThat(quizSessionHistoryService.listRecentSessions(noteId.toString(), userId, 2))
                .isEqualTo(legacyListRecentSessions(noteId, userId, 2));
    }

    private QuickReviewSessionEntity saveSession(
            UUID userId,
            UUID noteId,
            QuickReviewSessionMode sessionMode,
            OffsetDateTime completedAt,
            Map<String, Object> sessionState,
            Map<String, Object> sessionMetadata
    ) {
        return saveSession(userId, noteId, sessionMode, completedAt, sessionState, sessionMetadata, QuickReviewSessionStatus.COMPLETED);
    }

    private QuickReviewSessionEntity saveSession(
            UUID userId,
            UUID noteId,
            QuickReviewSessionMode sessionMode,
            OffsetDateTime completedAt,
            Map<String, Object> sessionState,
            Map<String, Object> sessionMetadata,
            QuickReviewSessionStatus status
    ) {
        QuickReviewSessionEntity session = QuickReviewSessionEntityBuilder.anInProgressSession()
                .withId(UUID.randomUUID())
                .withUserId(userId)
                .withStudyPackId(UUID.randomUUID())
                .withNoteId(noteId)
                .withSessionMode(sessionMode)
                .withStatus(status)
                .withCurrentQuestionIndex(status == QuickReviewSessionStatus.COMPLETED ? 5 : 0)
                .withCurrentRound(status == QuickReviewSessionStatus.COMPLETED ? QuickReviewRound.RETRY : QuickReviewRound.INITIAL)
                .withTotalQuestions(5)
                .withCorrectAnswers(status == QuickReviewSessionStatus.COMPLETED ? 4 : null)
                .withScorePercentage(status == QuickReviewSessionStatus.COMPLETED ? BigDecimal.valueOf(80).setScale(2) : null)
                .withRetryCount(status == QuickReviewSessionStatus.COMPLETED ? 1 : 0)
                .withDurationSeconds(120)
                .withSessionState(sessionState)
                .withSessionMetadata(sessionMetadata)
                .withCreatedAt(completedAt.minusMinutes(5))
                .withCompletedAt(status == QuickReviewSessionStatus.COMPLETED ? completedAt : null)
                .build();
        return quickReviewSessionRepository.saveAndFlush(session);
    }

    private Map<String, Object> sourceRefsState(UUID... noteIds) {
        return Map.of(
                SESSION_STATE_SOURCE_NOTE_REFS,
                List.of(noteIds).stream()
                        .map(noteId -> Map.of(SOURCE_NOTE_ID_KEY, noteId.toString()))
                        .toList()
        );
    }

    private Map<String, Object> boardExamState(UUID... noteIds) {
        Map<String, Object> state = new HashMap<>(sourceRefsState(noteIds));
        state.put(SESSION_STATE_MODE, MODE_BOARD_EXAM);
        return state;
    }

    private Map<UUID, OffsetDateTime> legacyLatestSessionCompletedAtByNoteIds(UUID userId, List<UUID> noteIds) {
        Set<UUID> requestedNoteIds = new HashSet<>(noteIds);
        Map<UUID, OffsetDateTime> latestByNoteId = new HashMap<>();
        for (QuickReviewSessionEntity session : legacyCompletedSessions(userId)) {
            for (UUID noteId : findParticipatingNoteIds(session)) {
                if (requestedNoteIds.contains(noteId)) {
                    latestByNoteId.merge(noteId, session.getCompletedAt(), this::latest);
                }
            }
        }
        return latestByNoteId;
    }

    private List<RecentQuizSessionHistoryResponse> legacyListRecentSessions(UUID noteId, UUID userId, int limit) {
        int normalizedLimit = Math.clamp(limit, 1, 10);
        return legacyCompletedSessions(userId).stream()
                .filter(session -> session.getSessionMode() != QuickReviewSessionMode.QUICK_REVIEW)
                .filter(session -> findParticipatingNoteIds(session).contains(noteId))
                .limit(normalizedLimit)
                .map(this::toLegacyResponse)
                .toList();
    }

    private List<QuickReviewSessionEntity> legacyCompletedSessions(UUID userId) {
        return quickReviewSessionRepository.findAll().stream()
                .filter(session -> userId.equals(session.getUserId()))
                .filter(session -> session.getStatus() == QuickReviewSessionStatus.COMPLETED)
                .filter(session -> session.getCompletedAt() != null)
                .sorted(Comparator.comparing(QuickReviewSessionEntity::getCompletedAt).reversed())
                .toList();
    }

    private RecentQuizSessionHistoryResponse toLegacyResponse(QuickReviewSessionEntity session) {
        String historyMode = resolveHistoryMode(session);
        return new RecentQuizSessionHistoryResponse(
                session.getId().toString(),
                historyMode,
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.getCorrectAnswers() == null ? 0 : session.getCorrectAnswers(),
                session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage(),
                session.getRetryCount() == null ? 0 : session.getRetryCount(),
                resolvePerformanceLevel(historyMode, session.getScorePercentage()),
                extractWeakConcepts(session.getSessionMetadata()),
                findParticipatingNoteIds(session).size(),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private Set<UUID> findParticipatingNoteIds(QuickReviewSessionEntity session) {
        Set<UUID> noteIds = new HashSet<>();
        noteIds.add(session.getNoteId());
        boolean isMultiNoteMode = session.getSessionMode() == QuickReviewSessionMode.LONG_EXAM
                || (session.getSessionMode() == QuickReviewSessionMode.CHALLENGE && isBoardExam(session.getSessionState()));
        if (!isMultiNoteMode) {
            return noteIds;
        }
        Object rawSourceRefs = session.getSessionState() == null
                ? null
                : session.getSessionState().get(SESSION_STATE_SOURCE_NOTE_REFS);
        if (!(rawSourceRefs instanceof List<?> sourceRefs)) {
            return noteIds;
        }
        for (Object sourceRef : sourceRefs) {
            UUID sourceNoteId = readSourceNoteId(sourceRef);
            if (sourceNoteId != null) {
                noteIds.add(sourceNoteId);
            }
        }
        return noteIds;
    }

    private UUID readSourceNoteId(Object sourceRef) {
        if (!(sourceRef instanceof Map<?, ?> sourceRefMap)) {
            return null;
        }
        Object rawNoteId = sourceRefMap.get(SOURCE_NOTE_ID_KEY);
        if (!(rawNoteId instanceof String noteId) || noteId.isBlank()) {
            return null;
        }
        return UUID.fromString(noteId);
    }

    private String resolveHistoryMode(QuickReviewSessionEntity session) {
        if (session.getSessionMode() == QuickReviewSessionMode.CHALLENGE && isBoardExam(session.getSessionState())) {
            return HISTORY_MODE_BOARD_EXAM;
        }
        if (session.getSessionMode() == QuickReviewSessionMode.ADAPTIVE
                && SUB_MODE_INTERVIEW.equals(QuizSessionStateUtils.extractSubMode(session.getSessionState()))) {
            return HISTORY_MODE_INTERVIEW_PRACTICE;
        }
        return session.getSessionMode().name();
    }

    private boolean isBoardExam(Map<String, Object> sessionState) {
        return sessionState != null && MODE_BOARD_EXAM.equals(sessionState.get(SESSION_STATE_MODE));
    }

    private String resolvePerformanceLevel(String historyMode, BigDecimal scorePercentage) {
        if (scorePercentage == null
                || (!QuickReviewSessionMode.CHALLENGE.name().equals(historyMode)
                && !HISTORY_MODE_BOARD_EXAM.equals(historyMode))) {
            return null;
        }
        return PERFORMANCE_LEVEL_GOOD;
    }

    private List<String> extractWeakConcepts(Map<String, Object> sessionMetadata) {
        if (sessionMetadata == null) {
            return List.of();
        }
        Object rawWeakConcepts = sessionMetadata.get(SESSION_METADATA_WEAK_CONCEPTS);
        if (!(rawWeakConcepts instanceof List<?> weakConcepts)) {
            return List.of();
        }
        return weakConcepts.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(concept -> !concept.isBlank())
                .toList();
    }

    private OffsetDateTime latest(OffsetDateTime left, OffsetDateTime right) {
        return left.isAfter(right) ? left : right;
    }
}
