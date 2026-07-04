package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionItemResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.repository.GeneratedQuizNoteProjection;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionNoteProjection;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.testutil.SqlCaptureStatementInspector;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
@Transactional
class NoteCollectionServiceProjectionIntegrationTest {
    private static final String COLLECTION_TITLE = "Biology Unit";
    private static final String BIOLOGY_SUBJECT = "Biology";
    private static final String COURSE_PROGRAM = "STEM";
    private static final String WEEK_ONE_LABEL = "Week 1";
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-05-01T10:00:00Z");

    @Autowired
    private NoteCollectionService noteCollectionService;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private StudyPackRepository studyPackRepository;
    @Autowired
    private GeneratedQuizRepository generatedQuizRepository;
    @Autowired
    private NoteCollectionRepository collectionRepository;
    @Autowired
    private NoteCollectionItemRepository itemRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StubConceptHealthService conceptHealthService;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists notes (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    title text,
                    subject varchar(64),
                    course_program varchar(120),
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
                create table if not exists generated_quizzes (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    note_id uuid not null,
                    target_learner_level varchar(32),
                    questions json not null,
                    generated_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists note_collections (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    title varchar(150) not null,
                    description text,
                    visibility varchar(16) not null,
                    course_program varchar(120),
                    estimated_study_hours integer,
                    source_plan_id uuid,
                    parent_collection_id uuid,
                    sibling_position integer,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists note_collection_items (
                    id uuid primary key,
                    collection_id uuid not null,
                    note_id uuid not null,
                    label varchar(120),
                    position integer not null,
                    created_at timestamp with time zone not null
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
        jdbcTemplate.execute("delete from generated_quizzes");
        jdbcTemplate.execute("delete from study_packs");
        jdbcTemplate.execute("delete from note_collection_items");
        jdbcTemplate.execute("delete from note_collections");
        jdbcTemplate.execute("delete from quick_review_sessions");
        jdbcTemplate.execute("delete from notes");
        conceptHealthService.reset();
        SqlCaptureStatementInspector.clear();
    }

    @Test
    void getUsesLeanProjectionsAndMatchesLegacyItemResponses() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity collection = saveCollection(userId, CollectionVisibility.PRIVATE);
        NoteEntity draft = saveNote(userId, "Draft", NoteStatus.DRAFT, NoteVisibility.PRIVATE, 0);
        NoteEntity generating = saveNote(userId, "Generating", NoteStatus.GENERATING, NoteVisibility.PRIVATE, 1);
        NoteEntity failed = saveNote(userId, "Failed", NoteStatus.FAILED, NoteVisibility.PRIVATE, 2);
        NoteEntity ready = saveNote(userId, "Ready", NoteStatus.GENERATED, NoteVisibility.PRIVATE, 3);
        saveItem(collection.getId(), draft.getId(), 0);
        saveItem(collection.getId(), generating.getId(), 1);
        saveItem(collection.getId(), failed.getId(), 2);
        saveItem(collection.getId(), ready.getId(), 3);
        StudyPackEntity readyPack = saveStudyPack(userId, ready.getId(), List.of("Cells", "DNA", "Mitosis", "Genetics"));
        UUID generatedQuizId = saveGeneratedQuiz(userId, ready.getId());
        conceptHealthService.setCanView(true);
        conceptHealthService.setDueConcepts(Map.of(
                readyPack.getId(),
                List.of("Cells", "DNA", "Mitosis", "Genetics")
        ));

        List<NoteCollectionItemResponse> expectedItems = List.of(
                legacyItem(draft, null, null, 0, List.of()),
                legacyItem(generating, null, null, 1, List.of()),
                legacyItem(failed, null, null, 2, List.of()),
                legacyItem(ready, readyPack, generatedQuizId, 3, List.of("Cells", "DNA", "Mitosis", "Genetics"))
        );

        SqlCaptureStatementInspector.clear();

        NoteCollectionDetailResponse detail = noteCollectionService.get(collection.getId(), userId);

        assertThat(detail.items()).isEqualTo(expectedItems);
        assertThat(detail.progress().totalNotes()).isEqualTo(4);
        assertThat(detail.progress().notesWithStudyPack()).isEqualTo(1);
        assertThat(detail.progress().notesPracticed()).isZero();
        assertProjectionQueriesAvoidLargeColumns();
    }

    @Test
    void getReturnsEmptyDueConceptsWhenConceptHealthIsNotViewable() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity collection = saveCollection(userId, CollectionVisibility.PRIVATE);
        NoteEntity ready = saveNote(userId, "Ready", NoteStatus.GENERATED, NoteVisibility.PRIVATE, 0);
        saveItem(collection.getId(), ready.getId(), 0);
        saveStudyPack(userId, ready.getId(), List.of("Cells"));
        conceptHealthService.setCanView(false);

        NoteCollectionDetailResponse detail = noteCollectionService.get(collection.getId(), userId);

        assertThat(detail.items().getFirst().dueConceptCount()).isZero();
        assertThat(detail.items().getFirst().dueConcepts()).isEmpty();
    }

    @Test
    void getPublicUsesLeanProjectionsFiltersPrivateNotesAndMatchesLegacyOutput() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity collection = saveCollection(userId, CollectionVisibility.PUBLIC);
        NoteEntity publicNote = saveNote(userId, "Public", NoteStatus.GENERATED, NoteVisibility.PUBLIC, 0);
        NoteEntity privateNote = saveNote(userId, "Private", NoteStatus.GENERATED, NoteVisibility.PRIVATE, 1);
        saveItem(collection.getId(), publicNote.getId(), 0);
        saveItem(collection.getId(), privateNote.getId(), 1);
        StudyPackEntity publicPack = saveStudyPack(userId, publicNote.getId(), List.of("Cells"));
        saveStudyPack(userId, privateNote.getId(), List.of("Hidden"));

        SqlCaptureStatementInspector.clear();

        NoteCollectionDetailResponse detail = noteCollectionService.getPublic(collection.getId());

        assertThat(detail.items()).containsExactly(legacyItem(publicNote, publicPack, null, 0, List.of()));
        assertThat(detail.progress().totalNotes()).isEqualTo(1);
        assertThat(detail.progress().notesWithStudyPack()).isEqualTo(1);
        assertProjectionQueriesAvoidLargeColumns();
    }

    @Test
    void getThrowsWhenCollectionItemReferencesMissingNote() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity collection = saveCollection(userId, CollectionVisibility.PRIVATE);
        saveItem(collection.getId(), UUID.randomUUID(), 0);

        assertThatThrownBy(() -> noteCollectionService.get(collection.getId(), userId))
                .isInstanceOf(NoteNotFoundException.class);
    }

    @Test
    void noteAndGeneratedQuizProjectionsAreRecordConstructorResults() {
        UUID userId = UUID.randomUUID();
        NoteEntity note = saveNote(userId, "Projected", NoteStatus.DRAFT, NoteVisibility.PRIVATE, 0);
        UUID generatedQuizId = saveGeneratedQuiz(userId, note.getId());

        List<NoteCollectionNoteProjection> notes = noteRepository.findCollectionNoteProjectionsByIdIn(List.of(note.getId()));
        List<GeneratedQuizNoteProjection> generatedQuizzes = generatedQuizRepository.findNoteIdsByOwnerUserIdAndNoteIdIn(
                userId,
                List.of(note.getId())
        );

        assertThat(notes).containsExactly(new NoteCollectionNoteProjection(
                note.getId(),
                note.getTitle(),
                note.getSubject(),
                note.getCourseProgram(),
                note.getStatus(),
                note.getVisibility(),
                note.getUpdatedAt()
        ));
        assertThat(notes.getFirst().getClass().isRecord()).isTrue();
        assertThat(generatedQuizzes).containsExactly(new GeneratedQuizNoteProjection(note.getId(), generatedQuizId));
        assertThat(generatedQuizzes.getFirst().getClass().isRecord()).isTrue();
    }

    private void assertProjectionQueriesAvoidLargeColumns() {
        List<String> selects = SqlCaptureStatementInspector.statements().stream()
                .filter(sql -> sql.toLowerCase().startsWith("select"))
                .toList();
        assertThat(selects).isNotEmpty();
        assertThat(selects).allSatisfy(sql -> assertThat(sql.toLowerCase())
                .doesNotContain(".content")
                .doesNotContain(" source_text")
                .doesNotContain(".source_text")
                .doesNotContain(".quiz")
                .doesNotContain(" questions")
                .doesNotContain(".questions"));
    }

    private NoteCollectionItemResponse legacyItem(
            NoteEntity note,
            StudyPackEntity studyPack,
            UUID generatedQuizId,
            int position,
            List<String> dueConcepts
    ) {
        return new NoteCollectionItemResponse(
                note.getId(),
                WEEK_ONE_LABEL,
                position,
                note.getTitle(),
                note.getSubject(),
                note.getCourseProgram(),
                NoteStudyPackStatusResolver.resolve(note, studyPack),
                generatedQuizId == null ? null : generatedQuizId.toString(),
                null,
                dueConcepts.size(),
                dueConcepts.stream().limit(3).toList(),
                note.getUpdatedAt()
        );
    }

    private NoteCollectionEntity saveCollection(UUID userId, CollectionVisibility visibility) {
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(UUID.randomUUID());
        collection.setOwnerUserId(userId);
        collection.setTitle(COLLECTION_TITLE);
        collection.setDescription("Collection description");
        collection.setVisibility(visibility);
        collection.setCourseProgram(COURSE_PROGRAM);
        collection.setEstimatedStudyHours(4);
        collection.setCreatedAt(Instant.parse("2026-05-01T09:00:00Z"));
        collection.setUpdatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        return collectionRepository.save(collection);
    }

    private NoteEntity saveNote(UUID userId, String title, NoteStatus status, NoteVisibility visibility, int offsetHours) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(userId);
        note.setTitle(title);
        note.setSubject(BIOLOGY_SUBJECT);
        note.setCourseProgram(COURSE_PROGRAM);
        note.setTags(new String[0]);
        note.setContent("Large note content that must not be selected by collection detail projections.");
        note.setStatus(status);
        note.setVisibility(visibility);
        note.setTargetProfileType(NoteTargetProfileType.STUDENT);
        note.setCreatedAt(BASE_TIME.plusHours(offsetHours));
        note.setUpdatedAt(BASE_TIME.plusHours(offsetHours).plusMinutes(30));
        return noteRepository.save(note);
    }

    private NoteCollectionItemEntity saveItem(UUID collectionId, UUID noteId, int position) {
        NoteCollectionItemEntity item = new NoteCollectionItemEntity();
        item.setId(UUID.randomUUID());
        item.setCollectionId(collectionId);
        item.setNoteId(noteId);
        item.setLabel(WEEK_ONE_LABEL);
        item.setPosition(position);
        item.setCreatedAt(Instant.parse("2026-05-01T11:00:00Z").plusSeconds(position));
        return itemRepository.save(item);
    }

    private StudyPackEntity saveStudyPack(UUID userId, UUID noteId, List<String> keyConcepts) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setOwnerUserId(userId);
        studyPack.setNoteId(noteId);
        studyPack.setInputType(InputType.TEXT);
        studyPack.setTitle("Pack");
        studyPack.setSummary("Summary");
        studyPack.setSubject(BIOLOGY_SUBJECT);
        studyPack.setSourceText("Large source text that must not be selected by collection detail projections.");
        studyPack.setKeyConcepts(keyConcepts);
        studyPack.setQuiz(List.of(new QuizItem(
                "Question?",
                List.of("A", "B", "C", "D"),
                0,
                "Cells",
                "Explanation"
        )));
        studyPack.setModelTier(ModelTier.FREE);
        studyPack.setModelUsed("gpt-4.1-mini");
        studyPack.setEstimatedCost(BigDecimal.ZERO);
        studyPack.setStatus(StudyPackStatus.DONE);
        studyPack.setCreatedAt(BASE_TIME);
        studyPack.setUpdatedAt(BASE_TIME);
        studyPack.setTags(new String[0]);
        return studyPackRepository.save(studyPack);
    }

    private UUID saveGeneratedQuiz(UUID userId, UUID noteId) {
        UUID generatedQuizId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into generated_quizzes (
                            id,
                            owner_user_id,
                            note_id,
                            questions,
                            generated_at,
                            updated_at
                        ) values (?, ?, ?, ?, ?, ?)
                        """,
                generatedQuizId,
                userId,
                noteId,
                "[{\"question\":\"Question?\",\"choices\":[\"A\",\"B\",\"C\",\"D\"],\"correctAnswerIndex\":0,\"concept\":\"Cells\",\"explanation\":\"Explanation\"}]",
                BASE_TIME,
                BASE_TIME
        );
        return generatedQuizId;
    }

    @TestConfiguration
    static class ProjectionIntegrationTestConfiguration {
        @Bean
        @Primary
        StubConceptHealthService stubConceptHealthService() {
            return new StubConceptHealthService();
        }
    }

    static class StubConceptHealthService extends ConceptHealthService {
        private boolean canView;
        private Map<UUID, List<String>> dueConceptsByStudyPackId = Map.of();

        StubConceptHealthService() {
            super(null, null, null, null);
        }

        void reset() {
            canView = false;
            dueConceptsByStudyPackId = Map.of();
        }

        void setCanView(boolean canView) {
            this.canView = canView;
        }

        void setDueConcepts(Map<UUID, List<String>> dueConceptsByStudyPackId) {
            this.dueConceptsByStudyPackId = new HashMap<>(dueConceptsByStudyPackId);
        }

        @Override
        public boolean canViewConceptHealth(UUID userId) {
            return canView;
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
