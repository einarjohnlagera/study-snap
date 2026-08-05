package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteStatusResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicNoteLikeRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.testutil.SqlCaptureStatementInspector;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
@Transactional
class NoteServiceStatusProjectionIntegrationTest {
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-07-01T09:00:00Z");
    private static final String SUBJECT = "Biology";
    private static final String NOTE_TAG = "review";
    private static final String KEY_CONCEPT = "Cells";
    private static final String CORRECT_ANSWER = "Nucleus";

    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private StudyPackRepository studyPackRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private NoteService noteService;

    @BeforeEach
    void initSchema() {
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
        jdbcTemplate.execute("delete from study_packs");
        jdbcTemplate.execute("delete from note_course_program");
        jdbcTemplate.execute("delete from course_programs");
        jdbcTemplate.execute("delete from notes");
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
    void listMineStatusesUsesOnlyLeanNoteAndStudyPackQueries() {
        UUID ownerUserId = UUID.randomUUID();
        NoteEntity generatedNote = saveNote(ownerUserId, NoteStatus.GENERATED, BASE_TIME.plusMinutes(1));
        NoteEntity draftNote = saveNote(ownerUserId, NoteStatus.DRAFT, BASE_TIME);
        saveStudyPack(ownerUserId, generatedNote.getId());
        entityManager.flush();
        entityManager.clear();
        SqlCaptureStatementInspector.clear();

        List<NoteStatusResponse> statuses = noteService.listMineStatuses(ownerUserId);

        assertThat(statuses).containsExactly(
                new NoteStatusResponse(generatedNote.getId().toString(), NoteStudyPackStatusResolver.STUDY_PACK_READY),
                new NoteStatusResponse(draftNote.getId().toString(), NoteStudyPackStatusResolver.DRAFT)
        );
        List<String> selects = SqlCaptureStatementInspector.statements().stream()
                .filter(sql -> sql.toLowerCase().startsWith("select"))
                .toList();
        assertThat(selects).hasSize(2);
        assertThat(selects).anySatisfy(sql -> assertThat(sql.toLowerCase())
                .contains("from notes")
                .contains("status")
                .contains("updated_at")
                .doesNotContain("content")
                .doesNotContain("title")
                .doesNotContain("tags"));
        assertThat(selects).anySatisfy(sql -> assertThat(sql.toLowerCase())
                .contains("from study_packs")
                .contains("note_id")
                .doesNotContain("summary")
                .doesNotContain("source_text")
                .doesNotContain("quiz"));
        assertThat(selects).allSatisfy(sql -> assertThat(sql.toLowerCase())
                .doesNotContain("analytics_events")
                .doesNotContain("public_note_likes")
                .doesNotContain("generated_quizzes")
                .doesNotContain("quick_review_sessions")
                .doesNotContain("users"));
    }

    private NoteEntity saveNote(UUID ownerUserId, NoteStatus status, OffsetDateTime updatedAt) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(ownerUserId);
        note.setTitle("Status-only note");
        note.setSubject(SUBJECT);
        note.setCourseProgram("BS Biology");
        note.setTags(new String[]{NOTE_TAG});
        note.setContent("Full note content must not be selected by the status poll.");
        note.setStatus(status);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setTargetProfileType(NoteTargetProfileType.STUDENT);
        note.setCopiedFromPublic(false);
        note.setCreatedAt(BASE_TIME);
        note.setUpdatedAt(updatedAt);
        return noteRepository.save(note);
    }

    private void saveStudyPack(UUID ownerUserId, UUID noteId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setOwnerUserId(ownerUserId);
        studyPack.setNoteId(noteId);
        studyPack.setInputType(InputType.TEXT);
        studyPack.setTitle("Biology Study Pack");
        studyPack.setSummary("Generated summary that must stay out of the poll query.");
        studyPack.setSubject(SUBJECT);
        studyPack.setSourceText("Large source text that must stay out of the poll query.");
        studyPack.setKeyConcepts(List.of(KEY_CONCEPT));
        studyPack.setQuiz(List.of(new QuizItem(
                "Which structure contains DNA?",
                List.of(CORRECT_ANSWER, "Membrane"),
                CORRECT_ANSWER,
                KEY_CONCEPT,
                "DNA is stored in the nucleus."
        )));
        studyPack.setModelTier(ModelTier.FREE);
        studyPack.setModelUsed("test-model");
        studyPack.setStatus(StudyPackStatus.DONE);
        studyPack.setCreatedAt(BASE_TIME);
        studyPack.setUpdatedAt(BASE_TIME);
        studyPack.setTags(new String[]{NOTE_TAG});
        studyPackRepository.save(studyPack);
    }

    private NoteService createNoteService() {
        return new NoteService(
                noteRepository,
                mock(AnalyticsEventRepository.class),
                mock(PublicNoteLikeRepository.class),
                studyPackRepository,
                mock(GeneratedQuizRepository.class),
                mock(UserRepository.class),
                mock(QuizSessionHistoryService.class),
                mock(SubscriptionService.class),
                mock(FeatureGateService.class),
                mock(AnalyticsService.class),
                mock(ContentModerationService.class),
                mock(OnboardingGuardService.class),
                mock(OfficialChallengeQuizTemplateService.class),
                mock(NoteApplicableProgramsMaintenanceService.class),
                mock(com.studysnap.backend.repository.NoteCourseProgramRepository.class)
        );
    }
}
