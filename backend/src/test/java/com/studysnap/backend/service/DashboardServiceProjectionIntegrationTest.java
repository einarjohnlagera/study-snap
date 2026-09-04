package com.studysnap.backend.service;

import com.studysnap.backend.dto.DashboardOverviewResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicNoteLikeRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.testutil.SqlCaptureStatementInspector;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
@Transactional
class DashboardServiceProjectionIntegrationTest {
    private static final int DASHBOARD_NOTE_FETCH_LIMIT = 20;
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-06-01T10:00:00Z");
    private static final String SESSION_METADATA_CONCEPT_BREAKDOWN = "conceptBreakdown";
    private static final String CARDIOLOGY_CONCEPT = "Cardiology";
    private static final String ANATOMY_CONCEPT = "Anatomy";

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudyPackRepository studyPackRepository;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private StubConceptHealthService conceptHealthService;

    private NoteService noteService;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists users (
                    id uuid primary key,
                    email varchar(255) not null,
                    pending_email varchar(255),
                    password_hash varchar(255),
                    first_name varchar(100) not null,
                    last_name varchar(100),
                    display_name varchar(100),
                    username varchar(30) not null,
                    bio varchar(200),
                    birth_year smallint,
                    birth_year_updated_at timestamp with time zone,
                    learner_level varchar(32),
                    course_program varchar(120),
                    study_goal text,
                    focus_subjects text array not null,
                    school_name varchar(120),
                    public_profile_visible boolean not null,
                    country_code varchar(8),
                    utm_source varchar(255),
                    utm_medium varchar(255),
                    utm_campaign varchar(255),
                    utm_content varchar(255),
                    utm_term varchar(255),
                    referrer varchar(2048),
                    profile_type varchar(32),
                    exam_date date,
                    review_days text array,
                    review_commitment_prompted_at timestamp with time zone,
                    engagement_mode varchar(32) not null,
                    inactivity_reminders_enabled boolean not null,
                    weak_concept_reminders_enabled boolean not null,
                    weekly_summary_reminders_enabled boolean not null,
                    due_concepts_digest_reminders_enabled boolean not null,
                    knowledge_impact_digest_reminders_enabled boolean not null default false,
                    marketing_emails_enabled boolean not null,
                    mobile_tab_bar_enabled boolean,
                    theme_preference varchar(16) not null,
                    status varchar(32) not null,
                    role varchar(32) not null,
                    token_version integer not null,
                    failed_login_attempts integer not null,
                    current_streak integer not null,
                    longest_streak integer not null,
                    last_study_date date,
                    locked_until timestamp with time zone,
                    last_password_change_at timestamp with time zone,
                    last_login_at timestamp with time zone,
                    email_verified_at timestamp with time zone,
                    deleted_at timestamp with time zone,
                    onboarding_completed_at timestamp with time zone,
                    product_onboarding_completed_at timestamp with time zone,
                    primary_collection_id uuid,
                    study_days_per_week integer,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null,
                    generation_enqueued_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists notes (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    title text,
                    subject varchar(64),
                    course_program varchar(120),
                    domain_context varchar(64),
                    learner_level varchar(32),
                    tags varchar array not null,
                    content text not null,
                    status varchar(16) not null,
                    visibility varchar(16) not null,
                    target_profile_type varchar(16) not null,
                    source_note_id uuid,
                    copied_from_note_id uuid,
                    copied_from_user_id uuid,
                    copied_from_title varchar(255),
                    copied_from_public boolean,
                    copied_at timestamp with time zone,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """);
        createApplicableProgramsSchema();
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
                    study_pack_id uuid,
                    note_id uuid,
                    source_collection_id uuid,
                    session_mode varchar(32) not null,
                    status varchar(32) not null,
                    current_question_index integer not null,
                    current_round varchar(16) not null,
                    total_questions integer not null,
                    correct_answers integer,
                    verified_correct_answers integer,
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
        jdbcTemplate.execute("alter table quick_review_sessions add column if not exists quota_exempt boolean not null default false");
        jdbcTemplate.execute("""
                create table if not exists user_activity_events (
                    id uuid primary key,
                    user_id uuid not null,
                    study_pack_id uuid,
                    note_id uuid,
                    activity_type varchar(64) not null,
                    created_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("delete from quick_review_sessions");
        // ⚠️ v0.113.1. Mirrors V133's chk_quick_review_sessions_anchor so a test cannot persist an
        // anchor shape the real database rejects. H2 in PostgreSQL mode DOES support CHECK.
        // ⚠️ It does NOT support PARTIAL unique indexes (a WHERE clause on CREATE INDEX) -- verified
        // empirically, not assumed -- so V133's three active-session indexes are NOT expressible
        // here. NativeQueryPostgresIntegrationTest is their only authority; do not assume this
        // fixture proves anything about them.
        jdbcTemplate.execute("alter table quick_review_sessions add constraint if not exists"
                + " chk_quick_review_sessions_anchor check ((study_pack_id is not null and note_id is not null)"
                + " or source_collection_id is not null or status in ('COMPLETED', 'FORFEITED'))");
        jdbcTemplate.execute("delete from study_packs");
        jdbcTemplate.execute("delete from user_activity_events");
        jdbcTemplate.execute("delete from note_course_program");
        jdbcTemplate.execute("delete from course_programs");
        jdbcTemplate.execute("delete from notes");
        jdbcTemplate.execute("delete from users");
        conceptHealthService.reset();
        noteService = createNoteService();
        SqlCaptureStatementInspector.clear();
    }

    private void createApplicableProgramsSchema() {
        jdbcTemplate.execute("""
                create table if not exists course_programs (
                    id uuid primary key,
                    name varchar(120) not null unique
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists note_course_program (
                    id uuid primary key,
                    note_id uuid not null,
                    course_program_id uuid not null,
                    created_at timestamp with time zone not null default current_timestamp,
                    unique (note_id, course_program_id),
                    foreign key (note_id) references notes(id) on delete cascade,
                    foreign key (course_program_id) references course_programs(id)
                )
                """);
    }

    @Test
    void getOverviewPreservesUnboundedNoteSignalsBeyondTheStageOneLimit() {
        UUID userId = UUID.randomUUID();
        saveUser(userId, ProfileType.STUDENT, null);
        NoteEntity oldestReadyNote = null;
        for (int index = 0; index <= DASHBOARD_NOTE_FETCH_LIMIT; index++) {
            boolean oldest = index == DASHBOARD_NOTE_FETCH_LIMIT;
            NoteEntity note = saveNote(
                    userId,
                    "Note " + index,
                    BASE_TIME.minusHours(index),
                    oldest ? NoteStatus.GENERATED : NoteStatus.DRAFT
            );
            if (oldest) {
                oldestReadyNote = note;
                saveStudyPack(userId, note.getId(), List.of("Older ready concept"), List.of(
                        new QuizItem("Question", List.of("A", "B"), "A", "Older ready concept", "Explanation")
                ));
            }
        }
        entityManager.flush();
        entityManager.clear();

        var unboundedItems = noteService.listMine(userId);
        var boundedItems = noteService.listMine(userId, DASHBOARD_NOTE_FETCH_LIMIT);
        DashboardOverviewResponse response = dashboardService.getOverview(userId);

        assertThat(unboundedItems).hasSize(DASHBOARD_NOTE_FETCH_LIMIT + 1);
        assertThat(boundedItems).hasSize(DASHBOARD_NOTE_FETCH_LIMIT);
        assertThat(boundedItems).noneMatch(item -> (item.quizCount() == null ? 0 : item.quizCount()) > 0);
        assertThat(response.totalNoteCount()).isEqualTo(unboundedItems.size());
        assertThat(response.hasQuizQuestions()).isEqualTo(
                unboundedItems.stream().anyMatch(item -> (item.quizCount() == null ? 0 : item.quizCount()) > 0)
        );
        assertThat(response.hasQuizQuestions()).isTrue();
        assertThat(response.mostRecentReadyNoteId()).isEqualTo(oldestReadyNote.getId().toString());
    }

    @Test
    void getOverviewReturnsEmptyNoteSignalsWhenTheUserOwnsNoNotes() {
        UUID userId = UUID.randomUUID();
        saveUser(userId, ProfileType.STUDENT, null);
        entityManager.flush();
        entityManager.clear();

        DashboardOverviewResponse response = dashboardService.getOverview(userId);

        assertThat(noteService.listMine(userId)).isEmpty();
        assertThat(response.totalNoteCount()).isZero();
        assertThat(response.hasQuizQuestions()).isFalse();
        assertThat(response.mostRecentReadyNoteId()).isNull();
    }

    @Test
    void getOverviewPreservesHistoryAggregatesAndUsesLeanQueries() {
        UUID userId = UUID.randomUUID();
        saveUser(userId, ProfileType.BOARD_EXAM, LocalDate.now(ZoneOffset.UTC).plusDays(5));
        StudyPackEntity firstPack = saveStudyPack(userId, List.of(CARDIOLOGY_CONCEPT, ANATOMY_CONCEPT));
        StudyPackEntity secondPack = saveStudyPack(userId, List.of("Pharmacology"));
        saveCompletedSession(userId, firstPack.getId(), QuickReviewSessionMode.QUICK_REVIEW, new BigDecimal("80"), Map.of());
        saveCompletedSession(
                userId,
                firstPack.getId(),
                QuickReviewSessionMode.CHALLENGE,
                new BigDecimal("100"),
                Map.of(SESSION_METADATA_CONCEPT_BREAKDOWN, List.of(
                        conceptBreakdownEntry(CARDIOLOGY_CONCEPT, 4, 4),
                        conceptBreakdownEntry(ANATOMY_CONCEPT, 1, 4)
                ))
        );
        saveCompletedSession(
                userId,
                secondPack.getId(),
                QuickReviewSessionMode.CHALLENGE,
                null,
                Map.of(SESSION_METADATA_CONCEPT_BREAKDOWN, List.of(
                        conceptBreakdownEntry(CARDIOLOGY_CONCEPT, 1, 2),
                        conceptBreakdownEntry(ANATOMY_CONCEPT, 0, 2)
                ))
        );
        conceptHealthService.setDueConcepts(Map.of(
                firstPack.getId(), List.of(CARDIOLOGY_CONCEPT, ANATOMY_CONCEPT),
                secondPack.getId(), List.of("Pharmacology")
        ));
        entityManager.flush();
        entityManager.clear();
        SqlCaptureStatementInspector.clear();

        DashboardOverviewResponse response = dashboardService.getOverview(userId);

        assertThat(response.performanceSummary().averageQuizScore()).isEqualByComparingTo("60.00");
        assertThat(response.performanceSummary().totalQuizzesTaken()).isEqualTo(3);
        assertThat(response.performanceSummary().strongestConcept().conceptName()).isEqualTo(CARDIOLOGY_CONCEPT);
        assertThat(response.performanceSummary().weakestConcept().conceptName()).isEqualTo(ANATOMY_CONCEPT);
        assertThat(response.examPacingPlan().dueConceptCount()).isEqualTo(3);
        assertThat(response.examPacingPlan().dailyConceptTarget()).isEqualTo(1);
        assertThat(response.examPacingPlan().daysRemaining()).isEqualTo(5);

        assertOverviewQueryShapes();
    }

    @Test
    void getOverviewReturnsNullAverageWhenNoCompletedQuizSessionsExist() {
        UUID userId = UUID.randomUUID();
        saveUser(userId, ProfileType.STUDENT, null);
        entityManager.flush();
        entityManager.clear();

        DashboardOverviewResponse response = dashboardService.getOverview(userId);

        assertThat(response.performanceSummary().averageQuizScore()).isNull();
        assertThat(response.performanceSummary().totalQuizzesTaken()).isZero();
    }

    private void assertOverviewQueryShapes() {
        List<String> selects = SqlCaptureStatementInspector.statements().stream()
                .filter(sql -> sql.toLowerCase().startsWith("select"))
                .toList();
        List<String> sessionSelects = selects.stream()
                .filter(sql -> sql.toLowerCase().contains("quick_review_sessions"))
                .toList();
        List<String> studyPackProjectionSelects = selects.stream()
                .filter(sql -> sql.toLowerCase().contains("study_packs"))
                .filter(sql -> !sql.toLowerCase().contains("count("))
                .filter(sql -> sql.toLowerCase().contains("key_concepts"))
                .toList();

        assertThat(sessionSelects).hasSize(2);
        assertThat(sessionSelects).anySatisfy(sql -> assertThat(sql.toLowerCase())
                .contains("count(")
                .contains("sum(coalesce(")
                .contains("score_percentage"));
        assertThat(sessionSelects).anySatisfy(sql -> assertThat(sql.toLowerCase())
                .contains("session_metadata")
                .doesNotContain("count("));
        assertThat(studyPackProjectionSelects).hasSize(1);
        assertThat(studyPackProjectionSelects).allSatisfy(sql -> assertThat(sql.toLowerCase())
                .contains("key_concepts")
                .doesNotContain("summary")
                .doesNotContain("quiz")
                .doesNotContain("source_text"));
    }

    private void saveUser(UUID userId, ProfileType profileType, LocalDate examDate) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(userId + "@example.com");
        user.setFirstName("Study");
        user.setUsername("user" + userId.toString().replace("-", "").substring(0, 12));
        user.setFocusSubjects(new String[0]);
        user.setPublicProfileVisible(false);
        user.setProfileType(profileType);
        user.setExamDate(examDate);
        user.setEngagementMode(EngagementMode.FOCUSED);
        user.setInactivityRemindersEnabled(false);
        user.setWeakConceptRemindersEnabled(false);
        user.setWeeklySummaryRemindersEnabled(false);
        user.setDueConceptsDigestRemindersEnabled(false);
        user.setMarketingEmailsEnabled(false);
        user.setThemePreference(ThemePreference.SYSTEM);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setCurrentStreak(0);
        user.setLongestStreak(0);
        user.setCreatedAt(BASE_TIME);
        user.setUpdatedAt(BASE_TIME);
        userRepository.save(user);
    }

    private StudyPackEntity saveStudyPack(UUID userId, List<String> keyConcepts) {
        return saveStudyPack(
                userId,
                UUID.randomUUID(),
                keyConcepts,
                List.of(new QuizItem("Question", List.of("A", "B"), "A", CARDIOLOGY_CONCEPT, "Explanation"))
        );
    }

    private StudyPackEntity saveStudyPack(
            UUID userId,
            UUID noteId,
            List<String> keyConcepts,
            List<QuizItem> quiz
    ) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setOwnerUserId(userId);
        studyPack.setNoteId(noteId);
        studyPack.setInputType(InputType.TEXT);
        studyPack.setTitle("Study Pack");
        studyPack.setSummary("Large generated summary must not be selected.");
        studyPack.setKeyConcepts(keyConcepts);
        studyPack.setQuiz(quiz);
        studyPack.setModelTier(ModelTier.FREE);
        studyPack.setModelUsed("test-model");
        studyPack.setStatus(StudyPackStatus.DONE);
        studyPack.setCreatedAt(BASE_TIME);
        studyPack.setUpdatedAt(BASE_TIME);
        studyPack.setTags(new String[]{"test"});
        return studyPackRepository.save(studyPack);
    }

    private NoteEntity saveNote(UUID userId, String title, OffsetDateTime updatedAt, NoteStatus status) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(userId);
        note.setTitle(title);
        note.setSubject("Test subject");
        note.setCourseProgram("Test program");
        note.setTags(new String[]{"test"});
        note.setContent("Test note content");
        note.setStatus(status);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setTargetProfileType(NoteTargetProfileType.STUDENT);
        note.setCopiedFromPublic(false);
        note.setCreatedAt(updatedAt);
        note.setUpdatedAt(updatedAt);
        return noteRepository.save(note);
    }

    private NoteService createNoteService() {
        AnalyticsEventRepository analyticsEventRepository = mock(AnalyticsEventRepository.class);
        PublicNoteLikeRepository publicNoteLikeRepository = mock(PublicNoteLikeRepository.class);
        GeneratedQuizRepository generatedQuizRepository = mock(GeneratedQuizRepository.class);
        QuizSessionHistoryService quizSessionHistoryService = mock(QuizSessionHistoryService.class);

        lenient().when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(any(), any()))
                .thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.countLikesByNoteIds(any())).thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.findLikedNoteIdsByUserIdAndNoteIdIn(any(), any()))
                .thenReturn(List.of());
        lenient().when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(any(), any())).thenReturn(List.of());
        lenient().when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(any(), any()))
                .thenReturn(Map.of());
        return new NoteService(
                noteRepository,
                org.mockito.Mockito.mock(com.studysnap.backend.repository.NoteShareRepository.class),
                analyticsEventRepository,
                publicNoteLikeRepository,
                studyPackRepository,
                generatedQuizRepository,
                userRepository,
                quizSessionHistoryService,
                mock(SubscriptionService.class),
                mock(FeatureGateService.class),
                mock(AnalyticsService.class),
                mock(ContentModerationService.class),
                mock(OnboardingGuardService.class),
                mock(OfficialChallengeQuizTemplateService.class),
                mock(com.studysnap.backend.repository.NoteCourseProgramRepository.class),
                mock(com.studysnap.backend.repository.CourseProgramCatalogRepository.class),
                mock(StudyPackQuizMasteryService.class),
                mock(StudyPackGenerationContextResolver.class)
        );
    }

    private void saveCompletedSession(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode,
            BigDecimal scorePercentage,
            Map<String, Object> sessionMetadata
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(UUID.randomUUID());
        session.setSessionMode(sessionMode);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(5);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(5);
        session.setCorrectAnswers(3);
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(0);
        session.setSessionMetadata(sessionMetadata);
        session.setCreatedAt(BASE_TIME);
        session.setCompletedAt(BASE_TIME.plusHours(1));
        quickReviewSessionRepository.save(session);
    }

    private Map<String, Object> conceptBreakdownEntry(String conceptName, int correctAnswers, int totalQuestions) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("concept", conceptName);
        entry.put("correctAnswers", correctAnswers);
        entry.put("totalQuestions", totalQuestions);
        return entry;
    }

    @TestConfiguration
    static class DashboardProjectionIntegrationTestConfiguration {
        @Bean
        @Primary
        StubSubscriptionService stubSubscriptionService() {
            return new StubSubscriptionService();
        }

        @Bean
        @Primary
        StubConceptHealthService stubConceptHealthService() {
            return new StubConceptHealthService();
        }
    }

    static class StubSubscriptionService extends SubscriptionService {
        StubSubscriptionService() {
            super(null, null, null, Clock.systemUTC());
        }

        @Override
        public PlanType resolvePlan(UUID userId) {
            return PlanType.PRO;
        }
    }

    static class StubConceptHealthService extends ConceptHealthService {
        private Map<UUID, List<String>> dueConceptsByStudyPackId = Map.of();

        StubConceptHealthService() {
            super(null, null, null, null, null);
        }

        void reset() {
            dueConceptsByStudyPackId = Map.of();
        }

        void setDueConcepts(Map<UUID, List<String>> dueConceptsByStudyPackId) {
            this.dueConceptsByStudyPackId = new HashMap<>(dueConceptsByStudyPackId);
        }

        @Override
        public Map<UUID, List<String>> getDueConceptsByStudyPackIds(
                UUID userId,
                Map<UUID, List<String>> conceptsByStudyPackId,
                OffsetDateTime now
        ) {
            return dueConceptsByStudyPackId;
        }
    }
}
