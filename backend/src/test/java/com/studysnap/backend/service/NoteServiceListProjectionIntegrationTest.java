package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NotesLibraryPageResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicNoteLikeRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.testutil.SqlCaptureStatementInspector;
import com.studysnap.backend.util.ContentPreviewUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
@Transactional
class NoteServiceListProjectionIntegrationTest {
    private static final int CONTENT_PREVIEW_MAX_LENGTH = 180;
    private static final int CONTENT_PREFIX_LENGTH = 2000;
    private static final String LONG_NOTE_TITLE = "Comprehensive cardiac assessment";
    private static final String SHORT_NOTE_TITLE = "Medication safety";
    private static final String THIRD_NOTE_TITLE = "Patient handoff";
    private static final String COURSE_PROGRAM = "BS Nursing";
    private static final String SUBJECT = "Medical-Surgical Nursing";
    private static final String[] NOTE_TAGS = {"clinical", "review"};
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-06-15T09:00:00Z");

    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private NoteService noteService;
    private UserRepository userRepository;

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
                    updated_at timestamp with time zone not null,
                    generation_enqueued_at timestamp with time zone
                )
                """);
        createApplicableProgramsSchema();
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
    void listMineUsesBoundedProjectionWithoutChangingListItemValues() {
        UUID ownerUserId = UUID.randomUUID();
        String longContent = realisticLongContent();
        NoteEntity longNote = saveNote(
                ownerUserId,
                LONG_NOTE_TITLE,
                longContent,
                BASE_TIME.plusHours(3),
                NoteVisibility.PRIVATE
        );
        NoteEntity shortNote = saveNote(
                ownerUserId,
                SHORT_NOTE_TITLE,
                "Medication reconciliation prevents avoidable treatment errors during transitions of care.",
                BASE_TIME.plusHours(2),
                NoteVisibility.PRIVATE
        );
        NoteEntity thirdNote = saveNote(
                ownerUserId,
                THIRD_NOTE_TITLE,
                "Use SBAR to keep patient handoffs focused and complete.",
                BASE_TIME.plusHours(1),
                NoteVisibility.PRIVATE
        );
        entityManager.flush();
        entityManager.clear();
        SqlCaptureStatementInspector.clear();

        List<NoteListItemResponse> unbounded = noteService.listMine(ownerUserId);

        assertThat(unbounded).hasSize(3);
        assertThat(unbounded).extracting(NoteListItemResponse::id)
                .containsExactly(longNote.getId().toString(), shortNote.getId().toString(), thirdNote.getId().toString());
        assertListItemMatchesStoredNote(unbounded.getFirst(), longNote, longContent);
        assertListItemMatchesStoredNote(unbounded.get(1), shortNote, shortNote.getContent());
        assertThat(unbounded.getFirst().contentPreview())
                .isEqualTo(ContentPreviewUtils.buildContentPreview(longContent, CONTENT_PREVIEW_MAX_LENGTH));
        assertThat(longContent.length()).isGreaterThan(CONTENT_PREFIX_LENGTH);

        assertBoundedContentProjectionQuery();

        List<NoteListItemResponse> limited = noteService.listMine(ownerUserId, 2);
        assertThat(limited).containsExactlyElementsOf(unbounded.subList(0, 2));
        assertThat(noteService.listMine(ownerUserId, null)).containsExactlyElementsOf(unbounded);
    }

    @Test
    void createPersistsAuthoringAxesAndLoadsThemOnFreshFetch() {
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerUserId);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Engineering algebra",
                "Algebra",
                "Civil Engineering",
                "engineering_mathematics",
                "college",
                List.of("algebra"),
                "Canonical algebra content"
        );

        NoteResponse created = noteService.create(request, ownerUserId);
        entityManager.flush();
        entityManager.clear();

        NoteResponse refreshed = noteService.getById(created.id(), ownerUserId);

        assertThat(refreshed.domainContext()).isEqualTo(DomainContext.ENGINEERING_MATHEMATICS.name());
        assertThat(refreshed.learnerLevel()).isEqualTo(LearnerLevel.COLLEGE.name());
    }

    @Test
    void listPublicStillBuildsItsPreviewFromTheFullEntityContent() {
        UUID ownerUserId = UUID.randomUUID();
        String longContent = realisticLongContent();
        saveNote(ownerUserId, LONG_NOTE_TITLE, longContent, BASE_TIME, NoteVisibility.PUBLIC);
        entityManager.flush();
        entityManager.clear();
        SqlCaptureStatementInspector.clear();

        var response = noteService.listPublic(null, null, null, null, null, null, null, null);

        assertThat(response.items()).singleElement()
                .extracting(NoteListItemResponse::contentPreview)
                .isEqualTo(ContentPreviewUtils.buildContentPreview(longContent, CONTENT_PREVIEW_MAX_LENGTH));
        assertThat(selectStatements()).anySatisfy(sql -> assertThat(sql.toLowerCase())
                .contains("content")
                .doesNotContain("substring"));
    }

    @Test
    void listMineReturnsJoinedApplicableProgramsRatherThanAnEmptyArray() {
        // M2: GET /notes advertised applicablePrograms on its DTO and always returned []. The JPQL
        // projection behind listMine never selected the field -- JPQL cannot express the array_agg the
        // join needs -- while the Library page's native query always did. A mock-based unit test cannot
        // catch that, because the mapper was never the broken part; only a real query can.
        UUID ownerUserId = UUID.randomUUID();
        NoteEntity note = saveNote(
                ownerUserId,
                LONG_NOTE_TITLE,
                "Cardiac assessment content",
                BASE_TIME.plusHours(2),
                NoteVisibility.PRIVATE
        );
        entityManager.flush();
        UUID programId = UUID.randomUUID();
        jdbcTemplate.update("insert into course_programs (id, name) values (?, ?)", programId, "Nursing");
        UUID secondProgramId = UUID.randomUUID();
        jdbcTemplate.update("insert into course_programs (id, name) values (?, ?)", secondProgramId, "Pharmacy");
        jdbcTemplate.update(
                "insert into note_course_program (id, note_id, course_program_id) values (?, ?, ?)",
                UUID.randomUUID(), note.getId(), programId
        );
        jdbcTemplate.update(
                "insert into note_course_program (id, note_id, course_program_id) values (?, ?, ?)",
                UUID.randomUUID(), note.getId(), secondProgramId
        );
        entityManager.clear();

        List<NoteListItemResponse> response = noteService.listMine(ownerUserId);

        assertThat(response).singleElement()
                .satisfies(item -> assertThat(item.applicablePrograms()).containsExactly("Nursing", "Pharmacy"));
    }

    @Test
    void listMineReturnsAnEmptyProgramListForANoteWithNoJoinRows() {
        // The left join must not drop notes that carry no programs -- the shape every learner note has.
        UUID ownerUserId = UUID.randomUUID();
        saveNote(
                ownerUserId,
                SHORT_NOTE_TITLE,
                "Medication safety content",
                BASE_TIME.plusHours(1),
                NoteVisibility.PRIVATE
        );
        entityManager.flush();
        entityManager.clear();

        List<NoteListItemResponse> response = noteService.listMine(ownerUserId);

        assertThat(response).singleElement()
                .satisfies(item -> assertThat(item.applicablePrograms()).isEmpty());
    }

    @Test
    void listLibraryPageMapsTheNativeFastPathIntoTheExistingListItemProjection() {
        UUID ownerUserId = UUID.randomUUID();
        NoteEntity newest = saveNote(
                ownerUserId,
                LONG_NOTE_TITLE,
                "Cardiac assessment content",
                BASE_TIME.plusHours(2),
                NoteVisibility.PRIVATE
        );
        saveNote(
                ownerUserId,
                SHORT_NOTE_TITLE,
                "Medication safety content",
                BASE_TIME.plusHours(1),
                NoteVisibility.PRIVATE
        );
        entityManager.flush();
        entityManager.clear();
        SqlCaptureStatementInspector.clear();

        NotesLibraryPageResponse response = noteService.listLibraryPage(
                ownerUserId, null, "ALL", null, null, List.of(), "ALL", "RECENTLY_UPDATED", 0, 1
        );

        assertThat(response.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(newest.getId().toString());
                    assertThat(item.domainContext()).isEqualTo(DomainContext.NURSING.name());
                    assertThat(item.learnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW.name());
                    assertThat(item.tags()).containsExactly(NOTE_TAGS);
                });
        assertThat(response.totalMatching()).isEqualTo(2);
        assertThat(response.hasMore()).isTrue();
        assertThat(selectStatements().stream()
                .filter(sql -> !sql.toLowerCase().contains("copied_from_public=true")))
                .hasSize(2);
    }

    private void assertListItemMatchesStoredNote(
            NoteListItemResponse item,
            NoteEntity note,
            String fullContent
    ) {
        assertThat(item.id()).isEqualTo(note.getId().toString());
        assertThat(item.ownerUserId()).isEqualTo(note.getOwnerUserId().toString());
        assertThat(item.title()).isEqualTo(note.getTitle());
        assertThat(item.courseProgram()).isEqualTo(note.getCourseProgram());
        assertThat(item.domainContext()).isEqualTo(note.getDomainContext().name());
        assertThat(item.learnerLevel()).isEqualTo(note.getLearnerLevel().name());
        assertThat(item.subject()).isEqualTo(note.getSubject());
        assertThat(item.tags()).containsExactly(note.getTags());
        assertThat(item.contentPreview()).isEqualTo(ContentPreviewUtils.buildContentPreview(fullContent, CONTENT_PREVIEW_MAX_LENGTH));
        assertThat(item.studyPackStatus()).isEqualTo(NoteStudyPackStatusResolver.DRAFT);
        assertThat(item.visibility()).isEqualTo(note.getVisibility().name());
        assertThat(item.createdAt()).isEqualTo(note.getCreatedAt());
        assertThat(item.updatedAt()).isEqualTo(note.getUpdatedAt());
        assertThat(item.copiedFromNoteId()).isNull();
        assertThat(item.copiedFromPublic()).isFalse();
    }

    private void assertBoundedContentProjectionQuery() {
        assertThat(selectStatements())
                .filteredOn(sql -> sql.toLowerCase().contains("substring"))
                .singleElement()
                .satisfies(sql -> assertThat(sql.toLowerCase())
                        .contains("substring")
                        .contains("content")
                        .doesNotContain("source_note_id")
                        .doesNotContain("copied_from_user_id")
                        .doesNotContain("copied_from_title")
                        .doesNotContain("copied_at"));
    }

    private List<String> selectStatements() {
        return SqlCaptureStatementInspector.statements().stream()
                .filter(sql -> sql.toLowerCase().startsWith("select"))
                .toList();
    }

    private NoteEntity saveNote(
            UUID ownerUserId,
            String title,
            String content,
            OffsetDateTime updatedAt,
            NoteVisibility visibility
    ) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(ownerUserId);
        note.setTitle(title);
        note.setCourseProgram(COURSE_PROGRAM);
        note.setDomainContext(DomainContext.NURSING);
        note.setLearnerLevel(LearnerLevel.BOARD_EXAM_REVIEW);
        note.setTargetProfileType(NoteTargetProfileType.BOARD_TAKER);
        note.setSubject(SUBJECT);
        note.setTags(NOTE_TAGS.clone());
        note.setContent(content);
        note.setStatus(NoteStatus.DRAFT);
        note.setVisibility(visibility);
        note.setCreatedAt(BASE_TIME);
        note.setUpdatedAt(updatedAt);
        note.setCopiedFromPublic(false);
        return noteRepository.save(note);
    }

    private String realisticLongContent() {
        String paragraph = "During a cardiac assessment, begin by confirming the patient's symptoms, baseline activity tolerance, "
                + "vital signs, and medication history. Compare the current findings with prior observations, explain each step "
                + "in plain language, and document changes that need prompt escalation to the clinical team.";
        return String.join("\n\n", java.util.Collections.nCopies(12, paragraph));
    }

    private NoteService createNoteService() {
        AnalyticsEventRepository analyticsEventRepository = mock(AnalyticsEventRepository.class);
        PublicNoteLikeRepository publicNoteLikeRepository = mock(PublicNoteLikeRepository.class);
        StudyPackRepository studyPackRepository = mock(StudyPackRepository.class);
        GeneratedQuizRepository generatedQuizRepository = mock(GeneratedQuizRepository.class);
        userRepository = mock(UserRepository.class);
        QuizSessionHistoryService quizSessionHistoryService = mock(QuizSessionHistoryService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        FeatureGateService featureGateService = mock(FeatureGateService.class);
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        ContentModerationService contentModerationService = mock(ContentModerationService.class);
        OnboardingGuardService onboardingGuardService = mock(OnboardingGuardService.class);
        StudyPackQuizMasteryService studyPackQuizMasteryService = mock(StudyPackQuizMasteryService.class);

        lenient().when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(any(AnalyticsEventType.class), any()))
                .thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.countLikesByNoteIds(any())).thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.findLikedNoteIdsByUserIdAndNoteIdIn(any(), any())).thenReturn(List.of());
        lenient().when(studyPackRepository.findByNoteIdIn(any())).thenReturn(List.of());
        lenient().when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(any(), any())).thenReturn(List.of());
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(any(), any())).thenReturn(Map.of());
        lenient().when(studyPackQuizMasteryService.resolve(any(), any()))
                .thenReturn(com.studysnap.backend.service.model.StudyPackQuizMastery.notMastered());
        return new NoteService(
                noteRepository,
                org.mockito.Mockito.mock(com.studysnap.backend.repository.NoteShareRepository.class),
                analyticsEventRepository,
                publicNoteLikeRepository,
                studyPackRepository,
                generatedQuizRepository,
                userRepository,
                quizSessionHistoryService,
                subscriptionService,
                featureGateService,
                analyticsService,
                contentModerationService,
                onboardingGuardService,
                mock(OfficialChallengeQuizTemplateService.class),
                mock(com.studysnap.backend.repository.NoteCourseProgramRepository.class),
                mock(com.studysnap.backend.repository.CourseProgramCatalogRepository.class),
                studyPackQuizMasteryService,
                mock(StudyPackGenerationContextResolver.class)
        );
    }
}
