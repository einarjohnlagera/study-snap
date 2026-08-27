package com.studysnap.backend.service;

import com.studysnap.backend.dto.FacetCount;
import com.studysnap.backend.dto.NotesLibraryFilterOptionsResponse;
import com.studysnap.backend.dto.NotesLibraryIdsResponse;
import com.studysnap.backend.dto.NotesLibraryPageResponse;
import com.studysnap.backend.dto.SubjectStatsResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.exception.InvalidLibraryQueryException;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
@Transactional
class NoteServiceLibraryPaginationIntegrationTest {
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-07-01T09:00:00Z");
    private static final String FILTER_ALL = "ALL";
    private static final String SORT_RECENTLY_UPDATED = "RECENTLY_UPDATED";
    private static final String SORT_RECENTLY_REVIEWED = "RECENTLY_REVIEWED";
    private static final String SORT_TITLE_ASC = "TITLE_ASC";
    private static final String INVALID_FILTER_VALUE = "UNKNOWN";
    private static final String REVIEW_ORDER_SUBJECT = "Review order";
    private static final String HEART_REVIEW_TAG = "heart-review";
    private static final String NURSING_PROGRAM = "Nursing";
    private static final String EXCLUDED_PROGRAM = "Software Engineering";
    private static final String ACCOUNTANCY_PROGRAM = "Accountancy";
    private static final String PHARMACY_PROGRAM = "Pharmacy";
    private static final String EDUCATION_PROGRAM = "Education";
    private static final String BSED_PROGRAM = "Bsed";

    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private NoteService noteService;
    private QuizSessionHistoryService quizSessionHistoryService;
    private final Map<UUID, OffsetDateTime> reviewedAtByNoteId = new HashMap<>();

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
                    status varchar(16),
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
        jdbcTemplate.execute("""
                create table if not exists study_packs (
                    id uuid primary key,
                    owner_user_id uuid,
                    note_id uuid,
                    anon_id varchar(128),
                    input_type varchar(32),
                    title varchar(255),
                    summary varchar(2000),
                    subject varchar(64),
                    source_text varchar(20000),
                    key_concepts json,
                    quiz json,
                    ocr_confidence double precision,
                    model_tier varchar(32),
                    model_used varchar(64),
                    input_tokens integer,
                    output_tokens integer,
                    cached_input_tokens integer,
                    estimated_cost numeric(12,6),
                    status varchar(32),
                    error_code varchar(64),
                    created_at timestamp with time zone,
                    updated_at timestamp with time zone,
                    share_token varchar(128),
                    tags varchar array
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists generated_quizzes (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    note_id uuid not null,
                    target_learner_level varchar(32),
                    questions json,
                    generated_at timestamp with time zone,
                    updated_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("delete from generated_quizzes");
        jdbcTemplate.execute("delete from study_packs");
        jdbcTemplate.execute("delete from note_course_program");
        jdbcTemplate.execute("delete from course_programs");
        jdbcTemplate.execute("delete from notes");
        reviewedAtByNoteId.clear();
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
    void courseProgramReadsPreserveLegacyResultsWithOneOrZeroJoinRows() {
        UUID ownerUserId = UUID.randomUUID();
        NoteEntity newerNursing = saveNote(ownerUserId, "Newer nursing", "Care", NURSING_PROGRAM,
                new String[]{"care"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 2, 3);
        NoteEntity olderNursing = saveNote(ownerUserId, "Older nursing", "Care", NURSING_PROGRAM,
                new String[]{"care"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 1, 2);
        NoteEntity excluded = saveNote(ownerUserId, "Excluded program", "Architecture", EXCLUDED_PROGRAM,
                new String[]{"software"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 3, 4);
        flushAndClear();
        UUID nursingId = insertCourseProgram(NURSING_PROGRAM);
        insertApplicableProgram(newerNursing.getId(), nursingId);
        insertApplicableProgram(olderNursing.getId(), nursingId);

        NotesLibraryFilterOptionsResponse options = noteService.getLibraryFilterOptions(ownerUserId);
        List<String> nursingIds = ids(page(ownerUserId, null, FILTER_ALL, NURSING_PROGRAM, null, List.of(),
                FILTER_ALL, SORT_RECENTLY_UPDATED, 0, 20));
        List<String> excludedIds = ids(page(ownerUserId, null, FILTER_ALL, EXCLUDED_PROGRAM, null, List.of(),
                FILTER_ALL, SORT_RECENTLY_UPDATED, 0, 20));

        assertThat(options.coursePrograms()).containsExactly(
                new FacetCount(NURSING_PROGRAM, 2),
                new FacetCount(EXCLUDED_PROGRAM, 1)
        );
        assertThat(nursingIds).containsExactly(newerNursing.getId().toString(), olderNursing.getId().toString());
        assertThat(excludedIds).containsExactly(excluded.getId().toString());
    }

    @Test
    void multiProgramReadsMatchEveryJoinedProgramAndBlockTheLegacyString() {
        UUID ownerUserId = UUID.randomUUID();
        NoteEntity curated = saveNote(ownerUserId, "Cross-program note", "Care", NURSING_PROGRAM,
                new String[]{"care"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 1, 1);
        flushAndClear();
        UUID accountancyId = insertCourseProgram(ACCOUNTANCY_PROGRAM);
        UUID pharmacyId = insertCourseProgram(PHARMACY_PROGRAM);
        insertApplicableProgram(curated.getId(), accountancyId);
        insertApplicableProgram(curated.getId(), pharmacyId);

        NotesLibraryFilterOptionsResponse options = noteService.getLibraryFilterOptions(ownerUserId);
        NotesLibraryPageResponse allNotes = page(ownerUserId, null, FILTER_ALL, null, null, List.of(),
                FILTER_ALL, SORT_RECENTLY_UPDATED, 0, 20);

        assertThat(options.coursePrograms()).containsExactly(
                new FacetCount(ACCOUNTANCY_PROGRAM, 1),
                new FacetCount(PHARMACY_PROGRAM, 1)
        );
        assertThat(options.coursePrograms().stream().mapToLong(FacetCount::count).sum())
                .isGreaterThan(allNotes.totalMatching());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, ACCOUNTANCY_PROGRAM, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(curated.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, PHARMACY_PROGRAM, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(curated.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, NURSING_PROGRAM, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).isEmpty();
        assertThat(allNotes.items().getFirst().applicablePrograms())
                .containsExactly(ACCOUNTANCY_PROGRAM, PHARMACY_PROGRAM);
    }

    @Test
    void bsedAliasIsDiscoveredAsEducationInsteadOfItsLegacyString() {
        UUID ownerUserId = UUID.randomUUID();
        NoteEntity aliased = saveNote(ownerUserId, "Education note", "Teaching", BSED_PROGRAM,
                new String[]{"teaching"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 1, 1);
        flushAndClear();
        UUID educationId = insertCourseProgram(EDUCATION_PROGRAM);
        insertApplicableProgram(aliased.getId(), educationId);

        assertThat(noteService.getLibraryFilterOptions(ownerUserId).coursePrograms())
                .containsExactly(new FacetCount(EDUCATION_PROGRAM, 1));
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, EDUCATION_PROGRAM, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(aliased.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, BSED_PROGRAM, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).isEmpty();
    }

    @Test
    void fastPathAppliesEverySqlFilterAndReadinessBranch() {
        UUID ownerUserId = UUID.randomUUID();
        NoteEntity cardiac = saveNote(ownerUserId, "Cardiac assessment", "Cardiology", "Nursing",
                new String[]{HEART_REVIEW_TAG}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 1, 8);
        NoteEntity tagSearch = saveNote(ownerUserId, "Medication safety", "Pharmacology", "Nursing",
                new String[]{"clinical-pharmacology"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC, 2, 7);
        NoteEntity generated = saveNote(ownerUserId, "Generated pack", "Biology", "BS Biology",
                new String[]{"cells"}, NoteStatus.GENERATED, NoteVisibility.PRIVATE, 3, 6);
        NoteEntity legacyPack = saveNote(ownerUserId, "Legacy pack", "Anatomy", "Nursing",
                new String[]{"legacy"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 4, 5);
        NoteEntity quizReady = saveNote(ownerUserId, "Teacher quiz", "Education", "BSEd",
                new String[]{"exam"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 5, 4);
        saveNote(ownerUserId, "Generating", "Chemistry", "STEM", new String[]{"lab"},
                NoteStatus.GENERATING, NoteVisibility.PRIVATE, 6, 3);
        saveNote(ownerUserId, "Failed", "Physics", "STEM", new String[]{"motion"},
                NoteStatus.FAILED, NoteVisibility.PRIVATE, 7, 2);
        saveNote(UUID.randomUUID(), "Foreign Cardiac", "Cardiology", "Nursing", new String[]{HEART_REVIEW_TAG},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 8, 9);
        insertStudyPack(legacyPack);
        insertGeneratedQuiz(quizReady);
        flushAndClear();

        assertThat(ids(page(ownerUserId, "cardiac", FILTER_ALL, null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(cardiac.getId().toString());
        assertThat(ids(page(ownerUserId, "cology", FILTER_ALL, null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(tagSearch.getId().toString());
        assertThat(ids(page(ownerUserId, null, "DRAFT", null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20)))
                .containsExactlyInAnyOrder(cardiac.getId().toString(), tagSearch.getId().toString(), quizReady.getId().toString());
        assertThat(ids(page(ownerUserId, null, "STUDY_PACK_READY", null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20)))
                .containsExactlyInAnyOrder(generated.getId().toString(), legacyPack.getId().toString());
        assertThat(ids(page(ownerUserId, null, "QUIZ_READY", null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(quizReady.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, "BS Biology", null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(generated.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, null, List.of("missing", HEART_REVIEW_TAG), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(cardiac.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, null, List.of(), "PUBLIC",
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(tagSearch.getId().toString());
        assertThat(ids(page(ownerUserId, "medication", "DRAFT", "Nursing", null,
                List.of("clinical-pharmacology"), "PUBLIC", SORT_RECENTLY_UPDATED, 0, 20)))
                .containsExactly(tagSearch.getId().toString());

        SqlCaptureStatementInspector.clear();
        page(ownerUserId, null, FILTER_ALL, null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 2);
        assertThat(paginationSelects()).hasSize(2);
    }

    @Test
    void fastPathPreservesAllFiveSqlSortsAndOffsetPagination() {
        UUID ownerUserId = UUID.randomUUID();
        NoteEntity delta = saveNote(ownerUserId, "Delta", "One", null, new String[]{"one"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 1, 4);
        NoteEntity alpha = saveNote(ownerUserId, "Alpha", "Two", null, new String[]{"two"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 4, 1);
        NoteEntity charlie = saveNote(ownerUserId, "Charlie", "Three", null, new String[]{"three"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 2, 3);
        NoteEntity untitled = saveNote(ownerUserId, null, "Four", null, new String[]{"four"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 3, 2);
        flushAndClear();

        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, null, List.of(), FILTER_ALL,
                SORT_TITLE_ASC, 0, 20))).containsExactly(alpha.getId().toString(), charlie.getId().toString(),
                delta.getId().toString(), untitled.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, null, List.of(), FILTER_ALL,
                "TITLE_DESC", 0, 20))).containsExactly(untitled.getId().toString(), delta.getId().toString(),
                charlie.getId().toString(), alpha.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, null, List.of(), FILTER_ALL,
                "OLDEST", 0, 20))).containsExactly(delta.getId().toString(), charlie.getId().toString(),
                untitled.getId().toString(), alpha.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, null, List.of(), FILTER_ALL,
                "NEWEST", 0, 20))).containsExactly(alpha.getId().toString(), untitled.getId().toString(),
                charlie.getId().toString(), delta.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(delta.getId().toString(), charlie.getId().toString(),
                untitled.getId().toString(), alpha.getId().toString());

        NotesLibraryPageResponse firstPage = page(ownerUserId, null, FILTER_ALL, null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 2);
        NotesLibraryPageResponse secondPage = page(ownerUserId, null, FILTER_ALL, null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 1, 2);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(firstPage.totalMatching()).isEqualTo(4);
        assertThat(new HashSet<>(ids(firstPage))).doesNotContainAnyElementsOf(ids(secondPage));
        assertThat(ids(firstPage)).hasSize(2);
        assertThat(ids(secondPage)).hasSize(2);
    }

    @Test
    void materializePathUsesDerivedSubjectBucketsAndReviewedTiebreak() {
        UUID ownerUserId = UUID.randomUUID();
        NoteEntity rawNursing = saveNote(ownerUserId, "Zulu", "Nursing", null, new String[]{"care"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 1, 3);
        NoteEntity courseFallback = saveNote(ownerUserId, "Alpha", null, "Nursing", new String[]{"care"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 2, 4);
        NoteEntity general = saveNote(ownerUserId, "General note", null, null, new String[]{"general"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 3, 5);
        NoteEntity hyphen = saveNote(ownerUserId, "Hyphen", "Nursing - Fundamentals", null, new String[]{"dash"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 4, 2);
        NoteEntity enDash = saveNote(ownerUserId, "En dash", "Nursing – Fundamentals", null, new String[]{"dash"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 5, 1);
        NoteEntity reviewedOlderUpdate = saveNote(ownerUserId, "Review older update", REVIEW_ORDER_SUBJECT, null,
                new String[]{"review"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 6, 6);
        NoteEntity reviewedNewerUpdate = saveNote(ownerUserId, "Review newer update", REVIEW_ORDER_SUBJECT, null,
                new String[]{"review"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 7, 7);
        NoteEntity neverReviewed = saveNote(ownerUserId, "Never reviewed", REVIEW_ORDER_SUBJECT, null,
                new String[]{"review"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE, 8, 8);
        OffsetDateTime reviewedAt = BASE_TIME.plusDays(10);
        reviewedAtByNoteId.put(rawNursing.getId(), reviewedAt);
        reviewedAtByNoteId.put(courseFallback.getId(), reviewedAt);
        reviewedAtByNoteId.put(general.getId(), reviewedAt.plusDays(1));
        reviewedAtByNoteId.put(reviewedOlderUpdate.getId(), reviewedAt);
        reviewedAtByNoteId.put(reviewedNewerUpdate.getId(), reviewedAt);
        flushAndClear();

        NotesLibraryPageResponse nursingTitlePage = page(ownerUserId, null, FILTER_ALL, null, "Nursing", List.of(),
                FILTER_ALL, SORT_TITLE_ASC, 0, 1);
        NotesLibraryPageResponse nursingSecondPage = page(ownerUserId, null, FILTER_ALL, null, "Nursing", List.of(),
                FILTER_ALL, SORT_TITLE_ASC, 1, 1);
        assertThat(ids(nursingTitlePage)).containsExactly(courseFallback.getId().toString());
        assertThat(ids(nursingSecondPage)).containsExactly(rawNursing.getId().toString());
        assertThat(nursingTitlePage.totalMatching()).isEqualTo(2);
        assertThat(nursingTitlePage.hasMore()).isTrue();
        assertThat(nursingSecondPage.hasMore()).isFalse();
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, "General", List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))).containsExactly(general.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, "Nursing - Fundamentals", List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20)))
                .containsExactly(hyphen.getId().toString(), enDash.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, "Nursing", List.of(), FILTER_ALL,
                SORT_RECENTLY_REVIEWED, 0, 20)))
                .containsExactly(courseFallback.getId().toString(), rawNursing.getId().toString());
        assertThat(ids(page(ownerUserId, null, FILTER_ALL, null, REVIEW_ORDER_SUBJECT, List.of(), FILTER_ALL,
                SORT_RECENTLY_REVIEWED, 0, 20)))
                .containsExactly(
                        reviewedNewerUpdate.getId().toString(),
                        reviewedOlderUpdate.getId().toString(),
                        neverReviewed.getId().toString()
                );

        SqlCaptureStatementInspector.clear();
        page(ownerUserId, null, FILTER_ALL, null, "Nursing", List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20);
        assertThat(paginationSelects()).hasSize(2);
    }

    @Test
    void idsStatsAndFilterOptionsShareBucketsAndRemainOwnerScoped() {
        UUID ownerUserId = UUID.randomUUID();
        saveNote(ownerUserId, "Nursing subject", "Nursing", null, new String[]{"core", "care"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 1, 1);
        saveNote(ownerUserId, "Nursing fallback", null, "Nursing", new String[]{"core"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 2, 2);
        saveNote(ownerUserId, "General", null, null, new String[]{"general"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 3, 3);
        for (int index = 0; index < 7; index++) {
            saveNote(ownerUserId, "Subject " + index, "Subject " + index, "Program " + index,
                    new String[]{"tag-" + index}, NoteStatus.DRAFT,
                    index == 6 ? NoteVisibility.PUBLIC : NoteVisibility.PRIVATE, 4 + index, 4 + index);
        }
        saveNote(UUID.randomUUID(), "Foreign Nursing", "Nursing", "Nursing", new String[]{"core"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, 20, 20);
        flushAndClear();

        NotesLibraryPageResponse nursingPage = page(ownerUserId, null, FILTER_ALL, null, "Nursing", List.of(),
                FILTER_ALL, SORT_RECENTLY_UPDATED, 0, 20);
        NotesLibraryIdsResponse nursingIds = noteService.listLibraryMatchingIds(
                ownerUserId, null, FILTER_ALL, null, "Nursing", List.of(), FILTER_ALL
        );
        assertThat(nursingIds.totalMatching()).isEqualTo(nursingPage.totalMatching()).isEqualTo(2);
        assertThat(nursingIds.noteIds()).containsExactlyInAnyOrderElementsOf(ids(nursingPage));
        assertThat(nursingIds.truncated()).isFalse();

        SubjectStatsResponse privateStats = noteService.getLibrarySubjectStats(
                ownerUserId, null, FILTER_ALL, null, List.of(), "PRIVATE"
        );
        assertThat(privateStats.total()).isEqualTo(9);
        assertThat(privateStats.topSubjects()).hasSize(6);
        assertThat(privateStats.topSubjects().getFirst().subject()).isEqualTo("Nursing");
        assertThat(privateStats.topSubjects().getFirst().count()).isEqualTo(2);
        assertThat(privateStats.otherSubjectsCount()).isEqualTo(2);

        NotesLibraryFilterOptionsResponse options = noteService.getLibraryFilterOptions(ownerUserId);
        assertThat(options.subjects()).contains(new FacetCount("Nursing", 2), new FacetCount("General", 1));
        assertThat(options.subjects()).extracting(FacetCount::count).containsExactly(2L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L);
        assertThat(options.coursePrograms()).contains(new FacetCount("Nursing", 1));
        assertThat(options.tags()).contains(new FacetCount("core", 2), new FacetCount("care", 1));
    }

    @Test
    void idsCapAlsoAppliesAfterSubjectMaterialization() {
        UUID ownerUserId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into notes (
                    id, owner_user_id, title, subject, course_program, tags, content, status, visibility,
                    target_profile_type, copied_from_public, created_at, updated_at
                )
                select random_uuid(), ?, 'Cap note ' || x, null, 'Cap Program', array['cap'], 'content',
                       'DRAFT', 'PRIVATE', 'STUDENT', false,
                       timestamp with time zone '2026-07-01 09:00:00+00',
                       timestamp with time zone '2026-07-01 09:00:00+00'
                from system_range(1, 1001)
                """, ownerUserId);
        entityManager.clear();

        NotesLibraryIdsResponse response = noteService.listLibraryMatchingIds(
                ownerUserId, null, FILTER_ALL, null, "Cap Program", List.of(), FILTER_ALL
        );

        assertThat(response.totalMatching()).isEqualTo(1001);
        assertThat(response.noteIds()).hasSize(1000);
        assertThat(response.truncated()).isTrue();
    }

    @Test
    void invalidEnumLikeFiltersAreRejectedAsBadLibraryQueries() {
        UUID ownerUserId = UUID.randomUUID();
        List<String> noTags = List.of();

        assertThatThrownBy(() -> page(ownerUserId, null, INVALID_FILTER_VALUE, null, null, noTags, FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20))
                .isInstanceOf(InvalidLibraryQueryException.class);
        assertThatThrownBy(() -> page(ownerUserId, null, FILTER_ALL, null, null, noTags, INVALID_FILTER_VALUE,
                SORT_RECENTLY_UPDATED, 0, 20))
                .isInstanceOf(InvalidLibraryQueryException.class);
        assertThatThrownBy(() -> page(ownerUserId, null, FILTER_ALL, null, null, noTags, FILTER_ALL,
                INVALID_FILTER_VALUE, 0, 20))
                .isInstanceOf(InvalidLibraryQueryException.class);
    }

    @Test
    void emptyMatchesReturnEmptyNonErrorContracts() {
        UUID ownerUserId = UUID.randomUUID();
        String unmatchedSearch = "no match";

        NotesLibraryPageResponse page = page(
                ownerUserId, unmatchedSearch, FILTER_ALL, null, null, List.of(), FILTER_ALL,
                SORT_RECENTLY_UPDATED, 0, 20
        );
        SubjectStatsResponse stats = noteService.getLibrarySubjectStats(
                ownerUserId, unmatchedSearch, FILTER_ALL, null, List.of(), FILTER_ALL
        );

        assertThat(page.items()).isEmpty();
        assertThat(page.totalMatching()).isZero();
        assertThat(page.hasMore()).isFalse();
        assertThat(stats.topSubjects()).isEmpty();
        assertThat(stats.total()).isZero();
    }

    private NotesLibraryPageResponse page(
            UUID ownerUserId,
            String search,
            String readiness,
            String courseProgram,
            String subject,
            List<String> tags,
            String visibility,
            String sort,
            int page,
            int pageSize
    ) {
        return noteService.listLibraryPage(
                ownerUserId, search, readiness, courseProgram, subject, tags, visibility, sort, page, pageSize
        );
    }

    private List<String> ids(NotesLibraryPageResponse response) {
        return response.items().stream().map(item -> item.id()).toList();
    }

    private NoteEntity saveNote(
            UUID ownerUserId,
            String title,
            String subject,
            String courseProgram,
            String[] tags,
            NoteStatus status,
            NoteVisibility visibility,
            int createdDay,
            int updatedDay
    ) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(ownerUserId);
        note.setTitle(title);
        note.setSubject(subject);
        note.setCourseProgram(courseProgram);
        note.setTags(tags);
        note.setContent("Content for " + (title == null ? "untitled note" : title));
        note.setStatus(status);
        note.setVisibility(visibility);
        note.setTargetProfileType(NoteTargetProfileType.STUDENT);
        note.setCopiedFromPublic(false);
        note.setCreatedAt(BASE_TIME.plusDays(createdDay));
        note.setUpdatedAt(BASE_TIME.plusDays(updatedDay));
        return noteRepository.save(note);
    }

    private void insertStudyPack(NoteEntity note) {
        jdbcTemplate.update(
                """
                        insert into study_packs (
                            id, owner_user_id, note_id, input_type, title, summary, key_concepts, quiz,
                            model_tier, model_used, status, tags, created_at, updated_at
                        ) values (?, ?, ?, 'TEXT', 'Pack', 'Summary', '[]', '{}',
                                  'STANDARD', 'test-model', 'READY', array['test'], ?, ?)
                        """,
                UUID.randomUUID(), note.getOwnerUserId(), note.getId(), BASE_TIME, BASE_TIME
        );
    }

    private void insertGeneratedQuiz(NoteEntity note) {
        jdbcTemplate.update(
                """
                        insert into generated_quizzes (
                            id, owner_user_id, note_id, questions, generated_at, updated_at
                        ) values (?, ?, ?, '[]', ?, ?)
                        """,
                UUID.randomUUID(), note.getOwnerUserId(), note.getId(), BASE_TIME, BASE_TIME
        );
    }

    private UUID insertCourseProgram(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into course_programs (id, name) values (?, ?)", id, name);
        return id;
    }

    private void insertApplicableProgram(UUID noteId, UUID courseProgramId) {
        jdbcTemplate.update(
                "insert into note_course_program (id, note_id, course_program_id) values (?, ?, ?)",
                UUID.randomUUID(),
                noteId,
                courseProgramId
        );
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
        SqlCaptureStatementInspector.clear();
    }

    private List<String> paginationSelects() {
        return SqlCaptureStatementInspector.statements().stream()
                .map(String::toLowerCase)
                .filter(sql -> sql.startsWith("select"))
                .filter(sql -> sql.contains("from notes n"))
                .filter(sql -> !sql.contains("copied_from_public=true"))
                .toList();
    }

    private NoteService createNoteService() {
        AnalyticsEventRepository analyticsEventRepository = mock(AnalyticsEventRepository.class);
        PublicNoteLikeRepository publicNoteLikeRepository = mock(PublicNoteLikeRepository.class);
        StudyPackRepository studyPackRepository = mock(StudyPackRepository.class);
        GeneratedQuizRepository generatedQuizRepository = mock(GeneratedQuizRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        quizSessionHistoryService = mock(QuizSessionHistoryService.class);
        lenient().when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(any(AnalyticsEventType.class), any()))
                .thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.countLikesByNoteIds(any())).thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.findLikedNoteIdsByUserIdAndNoteIdIn(any(), any())).thenReturn(List.of());
        lenient().when(studyPackRepository.findByNoteIdIn(any())).thenReturn(List.of());
        lenient().when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(any(), any())).thenReturn(List.of());
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(any(), any()))
                .thenAnswer(invocation -> {
                    Set<UUID> requestedIds = new HashSet<>(invocation.getArgument(1));
                    Map<UUID, OffsetDateTime> result = new HashMap<>();
                    reviewedAtByNoteId.forEach((noteId, completedAt) -> {
                        if (requestedIds.contains(noteId)) {
                            result.put(noteId, completedAt);
                        }
                    });
                    return result;
                });
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
                mock(StudyPackQuizMasteryService.class)
        );
    }
}
