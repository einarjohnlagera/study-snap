package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.testutil.SqlCaptureStatementInspector;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
@Transactional
class QuickReviewSessionLastReviewedIntegrationTest {
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-07-18T08:00:00Z");
    private static final String KEY_CONCEPT = "Cardiology";

    @Autowired
    private QuickReviewSessionService quickReviewSessionService;
    @Autowired
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Autowired
    private StudyPackRepository studyPackRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists study_packs (
                    id uuid primary key,
                    owner_user_id uuid,
                    note_id uuid,
                    anon_id varchar(128),
                    input_type varchar(32) not null,
                    title varchar(255) not null,
                    summary varchar(2000) not null,
                    subject varchar(64),
                    source_text varchar(20000),
                    key_concepts json not null,
                    quiz json not null,
                    ocr_confidence double precision,
                    model_tier varchar(32) not null,
                    model_used varchar(64) not null,
                    input_tokens integer,
                    output_tokens integer,
                    cached_input_tokens integer,
                    estimated_cost numeric(12,6),
                    status varchar(32) not null,
                    error_code varchar(64),
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null,
                    share_token varchar(128),
                    tags varchar array not null
                )
                """);
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
                    created_at timestamp with time zone not null,
                    completed_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("delete from quick_review_sessions");
        jdbcTemplate.execute("delete from study_packs");
        SqlCaptureStatementInspector.clear();
    }

    @Test
    void getLastReviewedAtByNoteIdsUsesOneOwnedQuickReviewAggregateForTheWholeBatch() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        StudyPackEntity firstQuickReviewPack = saveStudyPack(userId);
        StudyPackEntity secondQuickReviewPack = saveStudyPack(userId);
        StudyPackEntity challengeOnlyPack = saveStudyPack(userId);
        StudyPackEntity otherUserPack = saveStudyPack(otherUserId);
        UUID packlessNoteId = UUID.randomUUID();
        OffsetDateTime firstCompletedAt = BASE_TIME.plusHours(1);
        OffsetDateTime latestFirstCompletedAt = BASE_TIME.plusHours(3);
        OffsetDateTime secondCompletedAt = BASE_TIME.plusHours(2);
        saveCompletedSession(userId, firstQuickReviewPack, QuickReviewSessionMode.QUICK_REVIEW, firstCompletedAt);
        saveCompletedSession(userId, firstQuickReviewPack, QuickReviewSessionMode.QUICK_REVIEW, latestFirstCompletedAt);
        saveCompletedSession(userId, secondQuickReviewPack, QuickReviewSessionMode.QUICK_REVIEW, secondCompletedAt);
        saveCompletedSession(userId, challengeOnlyPack, QuickReviewSessionMode.CHALLENGE, BASE_TIME.plusHours(4));
        saveCompletedSession(userId, challengeOnlyPack, QuickReviewSessionMode.LONG_EXAM, BASE_TIME.plusHours(5));
        saveCompletedSession(otherUserId, otherUserPack, QuickReviewSessionMode.QUICK_REVIEW, BASE_TIME.plusHours(6));
        entityManager.flush();
        entityManager.clear();
        SqlCaptureStatementInspector.clear();

        Map<UUID, OffsetDateTime> result = quickReviewSessionService.getLastReviewedAtByNoteIds(List.of(
                firstQuickReviewPack.getNoteId(),
                secondQuickReviewPack.getNoteId(),
                challengeOnlyPack.getNoteId(),
                packlessNoteId,
                otherUserPack.getNoteId()
        ), userId);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                firstQuickReviewPack.getNoteId(), latestFirstCompletedAt,
                secondQuickReviewPack.getNoteId(), secondCompletedAt
        ));
        List<String> selects = SqlCaptureStatementInspector.statements().stream()
                .filter(sql -> sql.toLowerCase().startsWith("select"))
                .toList();
        assertThat(selects).hasSize(2);
        assertThat(selects.stream().filter(sql -> sql.toLowerCase().contains("quick_review_sessions")))
                .singleElement()
                .satisfies(sql -> assertThat(sql.toLowerCase())
                        .contains("max(")
                        .contains("study_pack_id in")
                        .contains("session_mode")
                        .contains("group by"));
    }

    private StudyPackEntity saveStudyPack(UUID ownerUserId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setOwnerUserId(ownerUserId);
        studyPack.setNoteId(UUID.randomUUID());
        studyPack.setInputType(InputType.TEXT);
        studyPack.setTitle("Study Pack");
        studyPack.setSummary("Summary");
        studyPack.setSubject("Biology");
        studyPack.setKeyConcepts(List.of(KEY_CONCEPT));
        studyPack.setQuiz(List.of(new QuizItem(
                "Question", List.of("A", "B"), "A", KEY_CONCEPT, "Explanation"
        )));
        studyPack.setModelTier(ModelTier.FREE);
        studyPack.setModelUsed("test-model");
        studyPack.setStatus(StudyPackStatus.DONE);
        studyPack.setCreatedAt(BASE_TIME);
        studyPack.setUpdatedAt(BASE_TIME);
        studyPack.setTags(new String[]{"review"});
        return studyPackRepository.save(studyPack);
    }

    private void saveCompletedSession(
            UUID userId,
            StudyPackEntity studyPack,
            QuickReviewSessionMode sessionMode,
            OffsetDateTime completedAt
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPack.getId());
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(sessionMode);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(1);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(1);
        session.setCorrectAnswers(1);
        session.setScorePercentage(new BigDecimal("100.00"));
        session.setRetryCount(0);
        session.setCreatedAt(completedAt.minusMinutes(5));
        session.setCompletedAt(completedAt);
        quickReviewSessionRepository.save(session);
    }
}
