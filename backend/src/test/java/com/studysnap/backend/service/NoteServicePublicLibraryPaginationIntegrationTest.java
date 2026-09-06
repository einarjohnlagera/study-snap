package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.PublicLibraryDiscoverySectionsResponse;
import com.studysnap.backend.dto.PublicNoteListResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.InvalidPublicLibraryQueryException;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import com.studysnap.backend.repository.PublicNoteLikeCountProjection;
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
import java.util.ArrayList;
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
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector",
        "spring.flyway.enabled=false"
})
@Transactional
class NoteServicePublicLibraryPaginationIntegrationTest {
    private static final String NURSING_PROGRAM = "Nursing";
    private static final String EXCLUDED_PROGRAM = "Software Engineering";
    private static final String ACCOUNTANCY_PROGRAM = "Accountancy";
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-07-01T09:00:00Z");
    private static final String STUDENT_AUDIENCE = "STUDENT";
    private static final String READY_SOURCE_TAG = "ready-source";

    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private final Map<UUID, Long> likeCounts = new HashMap<>();
    private final Map<UUID, Long> shareCounts = new HashMap<>();
    private final Map<UUID, Long> viewCounts = new HashMap<>();
    private final Map<UUID, StudyPackEntity> studyPacks = new HashMap<>();
    private final Map<UUID, UserEntity> users = new HashMap<>();
    private NoteService noteService;

    @BeforeEach
    void initSchema() {
        createSchema();
        jdbcTemplate.execute("delete from analytics_events");
        jdbcTemplate.execute("delete from public_note_likes");
        jdbcTemplate.execute("delete from study_packs");
        jdbcTemplate.execute("delete from note_course_program");
        jdbcTemplate.execute("delete from course_programs");
        jdbcTemplate.execute("delete from notes");
        jdbcTemplate.execute("delete from users");
        likeCounts.clear();
        shareCounts.clear();
        viewCounts.clear();
        studyPacks.clear();
        users.clear();
        noteService = createNoteService();
        SqlCaptureStatementInspector.clear();
    }

    @Test
    void slugFilterAndProgramSearchPreserveLegacyResultsWithOneOrZeroJoinRows() {
        UUID ownerId = insertUser("programreader", UserRole.USER);
        NoteEntity nursing = saveNote(
                ownerId, "Clinical foundations", "Patient Care", NURSING_PROGRAM,
                new String[]{"clinical"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 2
        );
        NoteEntity excluded = saveNote(
                ownerId, "Software foundations", "Architecture", EXCLUDED_PROGRAM,
                new String[]{"systems"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 1
        );
        flushAndClear();
        UUID nursingId = insertCourseProgram(NURSING_PROGRAM);
        insertApplicableProgram(nursing.getId(), nursingId);

        List<String> nursingFilterIds = ids(page(null, null, "recent", null, List.of(), "nursing", null, false, List.of(), 0, 20));
        List<String> excludedFilterIds = ids(page(null, null, "recent", null, List.of(), "software-engineering", null, false, List.of(), 0, 20));
        List<String> nursingSearchIds = ids(searchPage("nursing"));
        List<String> excludedSearchIds = ids(searchPage("software engineering"));

        assertThat(nursingFilterIds).containsExactly(nursing.getId().toString());
        assertThat(excludedFilterIds).containsExactly(excluded.getId().toString());
        assertThat(nursingSearchIds).containsExactly(nursing.getId().toString());
        assertThat(excludedSearchIds).containsExactly(excluded.getId().toString());
    }

    @Test
    void authoredDepthFilterMatchesOneColumnExcludesNullAndOffersOnlyPublicDepths() {
        UUID ownerId = insertUser("depthreader", UserRole.USER);
        NoteEntity juniorHigh = saveNote(
                ownerId, "Junior High Algebra", "Mathematics", null,
                new String[]{"algebra"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 1
        );
        juniorHigh.setLearnerLevel(LearnerLevel.JUNIOR_HIGH);
        NoteEntity seniorHigh = saveNote(
                ownerId, "Senior High Calculus", "Mathematics", null,
                new String[]{"calculus"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 2
        );
        seniorHigh.setLearnerLevel(LearnerLevel.SENIOR_HIGH);
        NoteEntity withoutDepth = saveNote(
                ownerId, "Unclassified Mathematics", "Mathematics", null,
                new String[]{"foundations"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 3
        );
        NoteEntity privateCollege = saveNote(
                ownerId, "Private College Algebra", "Mathematics", null,
                new String[]{"algebra"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE,
                NoteTargetProfileType.STUDENT, 4
        );
        privateCollege.setLearnerLevel(LearnerLevel.COLLEGE);
        flushAndClear();

        PublicNoteListResponse juniorHighPage = pageAtLevel(LearnerLevel.JUNIOR_HIGH);
        PublicNoteListResponse juniorHighLegacy = legacyAtLevel(LearnerLevel.JUNIOR_HIGH);
        PublicNoteListResponse unusedProfessionalPage = pageAtLevel(LearnerLevel.PROFESSIONAL);

        assertThat(ids(juniorHighPage)).containsExactly(juniorHigh.getId().toString());
        assertThat(ids(juniorHighLegacy)).containsExactly(juniorHigh.getId().toString());
        assertThat(ids(juniorHighPage)).doesNotContain(withoutDepth.getId().toString());
        assertThat(unusedProfessionalPage.items()).isEmpty();
        assertThat(unusedProfessionalPage.totalMatching()).isZero();
        assertThat(noteService.listPublicLearnerLevels())
                .containsExactly(LearnerLevel.JUNIOR_HIGH, LearnerLevel.SENIOR_HIGH)
                .doesNotContain(LearnerLevel.COLLEGE, LearnerLevel.PROFESSIONAL);
    }

    @Test
    void joinedProgramsDrivePublicSlugSearchAndBlockTheLegacyString() {
        UUID ownerId = insertUser("multireader", UserRole.USER);
        NoteEntity curated = saveNote(
                ownerId, "Cross-program foundations", "Care", "Pharmacy",
                new String[]{"clinical"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 1
        );
        flushAndClear();
        UUID accountancyId = insertCourseProgram(ACCOUNTANCY_PROGRAM);
        UUID nursingId = insertCourseProgram(NURSING_PROGRAM);
        insertApplicableProgram(curated.getId(), accountancyId);
        insertApplicableProgram(curated.getId(), nursingId);

        PublicNoteListResponse accountancy = page(null, null, "recent", null, List.of(), "accountancy", null, false, List.of(), 0, 20);

        assertThat(ids(accountancy)).containsExactly(curated.getId().toString());
        assertThat(accountancy.items().getFirst().applicablePrograms())
                .containsExactly(ACCOUNTANCY_PROGRAM, NURSING_PROGRAM);
        assertThat(ids(searchPage("nursing"))).containsExactly(curated.getId().toString());
        assertThat(ids(page(null, null, "recent", null, List.of(), "pharmacy", null, false, List.of(), 0, 20))).isEmpty();
        assertThat(ids(searchPage("pharmacy"))).isEmpty();
    }

    @Test
    void legacyModeUsesJoinedProgramsBeforeThePersonalNoteStringAndMatchesPaginatedResults() {
        UUID ownerId = insertUser("legacyjoinreader", UserRole.USER);
        NoteEntity curated = saveNote(
                ownerId, "Curated nursing foundations", "Patient Care", null,
                new String[]{"clinical"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 3
        );
        NoteEntity personal = saveNote(
                ownerId, "Personal nursing foundations", "Patient Care", NURSING_PROGRAM,
                new String[]{"personal"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 2
        );
        NoteEntity mixed = saveNote(
                ownerId, "Mixed program foundations", "Patient Care", ACCOUNTANCY_PROGRAM,
                new String[]{"mixed"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 1
        );
        flushAndClear();
        UUID nursingId = insertCourseProgram(NURSING_PROGRAM);
        insertApplicableProgram(curated.getId(), nursingId);
        insertApplicableProgram(mixed.getId(), nursingId);

        PublicNoteListResponse legacyNursing = noteService.listPublic(
                null, null, "recent", null, List.of(), NURSING_PROGRAM, null, null
        );
        PublicNoteListResponse paginatedNursing = page(
                null, null, "recent", null, List.of(), NURSING_PROGRAM, null, false, List.of(), 0, 20
        );
        PublicNoteListResponse staleLegacyProgram = noteService.listPublic(
                null, null, "recent", null, List.of(), ACCOUNTANCY_PROGRAM, null, null
        );
        PublicNoteListResponse programSearch = noteService.listPublic(
                null, NURSING_PROGRAM, "recent", null, List.of(), null, null, null
        );

        assertThat(ids(legacyNursing))
                .containsExactly(curated.getId().toString(), personal.getId().toString(), mixed.getId().toString());
        assertThat(ids(legacyNursing)).containsExactlyElementsOf(ids(paginatedNursing));
        assertThat(legacyNursing.items()).allSatisfy(item -> assertThat(item.applicablePrograms()).isNotNull());
        assertThat(legacyNursing.items().stream()
                .filter(item -> item.id().equals(curated.getId().toString()))
                .findFirst().orElseThrow().applicablePrograms()).containsExactly(NURSING_PROGRAM);
        assertThat(ids(staleLegacyProgram)).doesNotContain(mixed.getId().toString());
        assertThat(ids(programSearch)).contains(curated.getId().toString());
    }

    @Test
    void legacyModePreservesCombinedFiltersTotalAndNullablePaginationFields() {
        UUID creatorId = insertUser("nursecreator", UserRole.USER);
        UUID otherOwnerId = insertUser("othercreator", UserRole.USER);
        NoteEntity expected = saveNote(
                creatorId, "Renal assessment", "Renal Care", "Nursing",
                new String[]{"kidney-health"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 3
        );
        expected.setDomainContext(DomainContext.NURSING);
        expected.setLearnerLevel(LearnerLevel.BOARD_EXAM_REVIEW);
        saveNote(
                creatorId, "Cardiac assessment", "Cardiology", "Nursing",
                new String[]{"heart-health"}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 2
        );
        saveNote(
                otherOwnerId, "Renal private", "Renal Care", "Nursing",
                new String[]{"kidney-health"}, NoteStatus.DRAFT, NoteVisibility.PRIVATE,
                NoteTargetProfileType.STUDENT, 4
        );
        flushAndClear();

        PublicNoteListResponse legacy = noteService.listPublic(
                null,
                "renal",
                "recent",
                "renal-care",
                List.of("kidney-health"),
                "nursing",
                "nursecreator",
                10
        );
        PublicNoteListResponse extendedLegacy = noteService.listPublic(
                null,
                "renal",
                "recent",
                "renal-care",
                List.of("kidney-health"),
                "nursing",
                "nursecreator",
                null,
                10,
                null,
                null,
                false,
                List.of()
        );
        PublicNoteListResponse paginated = page(
                null,
                "renal",
                "recent",
                "renal-care",
                List.of("kidney-health"),
                "nursing",
                "nursecreator",
                false,
                List.of(),
                0,
                20
        );

        assertThat(extendedLegacy).isEqualTo(legacy);
        assertThat(legacy.items()).extracting(NoteListItemResponse::id)
                .containsExactly(expected.getId().toString());
        assertThat(ids(paginated)).containsExactly(expected.getId().toString());
        assertThat(paginated.items().getFirst().domainContext()).isEqualTo(DomainContext.NURSING.name());
        assertThat(paginated.items().getFirst().learnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW.name());
        assertThat(paginated.totalMatching()).isEqualTo(1);
        assertThat(legacy.total()).isEqualTo(2);
        assertThat(legacy.page()).isNull();
        assertThat(legacy.pageSize()).isNull();
        assertThat(legacy.totalMatching()).isNull();
        assertThat(legacy.hasMore()).isNull();
    }

    @Test
    void publicTagFacetFallbackExcludesNonPublicNotes() {
        UUID ownerId = insertUser("tagfacetowner", UserRole.USER);
        saveNote(ownerId, "Public tags", "Biology", null, new String[]{"Visible", "Chemistry"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 1);
        saveNote(ownerId, "Private tags", "Biology", null, new String[]{"Hidden", "Owner only"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, NoteTargetProfileType.STUDENT, 2);
        flushAndClear();

        List<String> tags = noteService.listPublicTags();

        assertThat(tags).containsExactly("Chemistry", "Visible");
    }

    @Test
    void additiveReadyAndSourceFiltersWorkInLegacyAndPaginatedModes() {
        UUID viewerId = insertUser("viewer", UserRole.USER);
        UUID officialId = insertUser("official", UserRole.ADMIN);
        UUID communityId = insertUser("community", UserRole.USER);
        UUID deletedId = insertUserWithId(AccountPurgeService.DELETED_USER_ID, "deleted", UserRole.ADMIN);
        NoteEntity viewerReady = saveNote(viewerId, "Viewer ready", "Biology", "Science",
                new String[]{READY_SOURCE_TAG}, NoteStatus.GENERATED, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 4);
        NoteEntity officialReady = saveNote(officialId, "Official ready", "Biology", "Science",
                new String[]{READY_SOURCE_TAG}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 3);
        NoteEntity communityDraft = saveNote(communityId, "Community draft", "Biology", "Science",
                new String[]{READY_SOURCE_TAG}, NoteStatus.DRAFT, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 2);
        NoteEntity deletedAdmin = saveNote(deletedId, "Deleted admin", "Biology", "Science",
                new String[]{READY_SOURCE_TAG}, NoteStatus.GENERATED, NoteVisibility.PUBLIC,
                NoteTargetProfileType.STUDENT, 1);
        insertStudyPack(officialReady, true);
        flushAndClear();

        PublicNoteListResponse legacyOfficial = noteService.listPublic(
                viewerId, null, null, null, null, null, null, null,
                null, null, null, true, List.of("OFFICIAL")
        );
        PublicNoteListResponse paginatedViewer = page(
                viewerId, null, "recent", null, List.of(), null, null, true, List.of("BY_YOU"), 0, 20
        );
        PublicNoteListResponse paginatedCommunity = page(
                viewerId, null, "recent", null, List.of(), null, null, false, List.of("COMMUNITY"), 0, 20
        );
        PublicNoteListResponse combined = page(
                viewerId, null, "recent", null, List.of(READY_SOURCE_TAG), null, null, true, List.of("BY_YOU", "OFFICIAL"), 0, 20
        );

        assertThat(ids(legacyOfficial)).containsExactly(officialReady.getId().toString());
        assertThat(ids(paginatedViewer)).containsExactly(viewerReady.getId().toString());
        assertThat(ids(paginatedCommunity)).containsExactly(
                communityDraft.getId().toString(),
                deletedAdmin.getId().toString()
        );
        assertThat(ids(combined)).containsExactly(
                viewerReady.getId().toString(),
                officialReady.getId().toString()
        );

        assertThatThrownBy(() -> page(
                viewerId, null, "recent", null, List.of(), null, null, false, List.of("UNKNOWN"), 0, 20
        )).isInstanceOf(InvalidPublicLibraryQueryException.class);
        assertThatThrownBy(() -> page(
                viewerId, null, "unknown", null, List.of(), null, null, false, List.of(), 0, 20
        )).isInstanceOf(InvalidPublicLibraryQueryException.class);
    }

    @Test
    void paginatedSearchMatchesEachLegacyPreviewAndTagField() {
        UUID ownerId = insertUser("searchowner", UserRole.USER);
        NoteEntity title = saveNote(ownerId, "Unique title needle", "Biology", null, new String[]{"plain"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 1);
        NoteEntity content = saveNote(ownerId, "Content", "Biology", null, new String[]{"plain"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 2);
        content.setContent("Several realistic paragraphs contain a unique content needle for discovery.");
        NoteEntity tag = saveNote(ownerId, "Tag", "Biology", null, new String[]{"cardio-marker"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 3);
        NoteEntity summary = saveNote(ownerId, "Summary", "Biology", null, new String[]{"plain"},
                NoteStatus.GENERATED, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 4);
        insertStudyPack(summary, true);
        jdbcTemplate.update("update study_packs set summary = ? where note_id = ?", "Unique summary needle", summary.getId());
        NoteEntity outsidePreview = saveNote(ownerId, "Outside", "Biology", null, new String[]{"plain"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 5);
        outsidePreview.setContent("a".repeat(190) + " hidden-after-preview");
        flushAndClear();

        assertThat(ids(searchPage("title needle"))).containsExactly(title.getId().toString());
        assertThat(ids(searchPage("content needle"))).containsExactly(content.getId().toString());
        assertThat(ids(searchPage("marker"))).containsExactly(tag.getId().toString());
        assertThat(ids(searchPage("summary needle"))).containsExactly(summary.getId().toString());
        assertThat(ids(searchPage("hidden-after-preview"))).isEmpty();
    }

    @Test
    void fastPathSortsAndPaginatesWithoutOverlap() {
        UUID ownerId = insertUser("fastowner", UserRole.USER);
        NoteEntity charlie = saveNote(ownerId, "Charlie", "Science", null, new String[]{"fast"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 1);
        NoteEntity alpha = saveNote(ownerId, "Alpha", "Science", null, new String[]{"fast"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 3);
        NoteEntity bravo = saveNote(ownerId, "Bravo", "Science", null, new String[]{"fast"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 2);
        saveNote(ownerId, "Private", "Science", null, new String[]{"fast"},
                NoteStatus.DRAFT, NoteVisibility.PRIVATE, NoteTargetProfileType.STUDENT, 4);
        flushAndClear();

        SqlCaptureStatementInspector.clear();
        PublicNoteListResponse titleFirst = page(null, null, "title", null, List.of(), null, null, false, List.of(), 0, 2);
        List<String> firstPageQueries = publicPaginationQueries();
        PublicNoteListResponse titleSecond = page(null, null, "title", null, List.of(), null, null, false, List.of(), 1, 2);
        PublicNoteListResponse recent = page(null, null, "recent", null, List.of(), null, null, false, List.of(), 0, 20);

        assertThat(ids(titleFirst)).containsExactly(alpha.getId().toString(), bravo.getId().toString());
        assertThat(ids(titleSecond)).containsExactly(charlie.getId().toString());
        assertThat(new HashSet<>(ids(titleFirst))).doesNotContainAnyElementsOf(ids(titleSecond));
        assertThat(titleFirst.totalMatching()).isEqualTo(3);
        assertThat(titleFirst.hasMore()).isTrue();
        assertThat(titleSecond.hasMore()).isFalse();
        assertThat(ids(recent)).containsExactly(
                alpha.getId().toString(), bravo.getId().toString(), charlie.getId().toString()
        );
        assertThat(firstPageQueries).hasSize(2);
        assertThat(firstPageQueries).anyMatch(sql -> sql.startsWith("select count(*)"));
        assertThat(firstPageQueries).anyMatch(sql -> sql.contains("substring(n.content"));
    }

    @Test
    void rankedSortsPreserveEligibilityAndUngatedSemantics() {
        UUID ownerId = insertUser("rankowner", UserRole.USER);
        NoteEntity featured = saveNote(ownerId, "Featured", "Biology", null, new String[]{"rank"},
                NoteStatus.GENERATED, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 2);
        NoteEntity popular = saveNote(ownerId, "Popular", "Biology", null, new String[]{"rank"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 3);
        NoteEntity copied = saveNote(ownerId, "Copied", "Biology", null, new String[]{"rank"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 1);
        NoteEntity zero = saveNote(ownerId, "Zero", "Biology", null, new String[]{"rank"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, 4);
        insertStudyPack(featured, true);
        recordViews(featured.getId(), 12L);
        recordViews(popular.getId(), 30L);
        insertCopies(copied.getId(), ownerId, 4);
        flushAndClear();

        PublicNoteListResponse featuredPage = page(null, null, "featured", null, List.of(), null, null, false, List.of(), 0, 20);
        PublicNoteListResponse popularPage = page(null, null, "popular", null, List.of(), null, null, false, List.of(), 0, 20);
        PublicNoteListResponse copiedAliasPage = page(null, null, "copied", null, List.of(), null, null, false, List.of(), 0, 20);
        SqlCaptureStatementInspector.clear();
        PublicNoteListResponse mostCopiedFirstPage = page(null, null, "most_copied", null, List.of(), null, null, false, List.of(), 0, 2);
        List<String> materializedQueries = publicPaginationQueries();
        PublicNoteListResponse mostCopiedSecondPage = page(null, null, "most_copied", null, List.of(), null, null, false, List.of(), 1, 2);
        PublicNoteListResponse recommendedPage = page(null, null, "recommended", null, List.of(), null, null, false, List.of(), 0, 20);
        PublicNoteListResponse viewsPage = page(null, null, "views", null, List.of(), null, null, false, List.of(), 0, 20);

        assertThat(ids(featuredPage)).containsExactly(featured.getId().toString());
        assertThat(featuredPage.items().getFirst().quizCount()).isEqualTo(1);
        assertThat(ids(popularPage)).containsExactly(copied.getId().toString(), popular.getId().toString());
        assertThat(ids(copiedAliasPage)).isEqualTo(ids(popularPage));
        assertThat(ids(mostCopiedFirstPage)).containsExactly(copied.getId().toString(), zero.getId().toString());
        assertThat(ids(mostCopiedSecondPage)).containsExactly(popular.getId().toString(), featured.getId().toString());
        assertThat(mostCopiedFirstPage.hasMore()).isTrue();
        assertThat(mostCopiedSecondPage.hasMore()).isFalse();
        assertThat(new HashSet<>(ids(mostCopiedFirstPage))).doesNotContainAnyElementsOf(ids(mostCopiedSecondPage));
        assertThat(ids(recommendedPage)).containsExactly(
                popular.getId().toString(),
                featured.getId().toString(),
                copied.getId().toString(),
                zero.getId().toString()
        );
        assertThat(ids(viewsPage).getFirst()).isEqualTo(popular.getId().toString());
        assertThat(mostCopiedFirstPage.totalMatching()).isEqualTo(4);
        assertThat(recommendedPage.totalMatching()).isEqualTo(4);
        // ⚠️ totalMatching for the two eligibility-filtered sorts counts the ELIGIBLE rows, not every
        // match — that is what the Java path's `rankedCandidates.size()` reported, and `hasMore` rides
        // on it. Asserting only the ungated sorts above would miss a count that ignored eligibility.
        assertThat(featuredPage.totalMatching())
                .as("Featured drops ineligible notes before ordering, so its total is the eligible count")
                .isEqualTo(1);
        assertThat(popularPage.totalMatching()).isEqualTo(2);
        assertThat(featuredPage.hasMore()).isFalse();
        // v0.119.1: the ranked path issues a bounded count, a bounded id query and one projection
        // fetch. Before it, the id query was an UNBOUNDED candidate load and there was no count.
        assertThat(materializedQueries).hasSize(3);
        assertThat(materializedQueries).anyMatch(sql -> sql.startsWith("select count(*)"));
        assertThat(materializedQueries).anyMatch(sql -> sql.contains("rank_copies"));
        assertThat(materializedQueries).anyMatch(sql -> sql.contains("substring(n.content"));
    }

    /**
     * ⚠️ THE DEFAULT-SORT GUARD. The whole finding behind v0.119.1 is that {@code sort} defaults to
     * {@code recommended}, which is NOT SQL-orderable, so a request that simply omits {@code sort}
     * took the unbounded ranking path even when it was paginated. A fixture that passes
     * {@code sort=recent} exercises the one branch that was already fine and proves nothing.
     *
     * <p>Seven public notes against a page size of three: the request must come back with three, must
     * report all seven as matching, and the query that chose the page must carry a database limit.
     * Under the defect that query was an unlimited candidate load.
     */
    @Test
    void requestWithNoSortTakesTheRankedPathAndIsBoundedByTheDatabase() {
        UUID ownerId = insertUser("defaultsort", UserRole.USER);
        for (int index = 0; index < 7; index++) {
            saveNote(ownerId, "Default " + index, "Science", null, new String[]{"default"},
                    NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, index + 1);
        }
        flushAndClear();

        SqlCaptureStatementInspector.clear();
        PublicNoteListResponse defaultSorted = page(
                null, null, null, null, List.of(), null, null, false, List.of(), 0, 3);
        List<String> queries = publicPaginationQueries();

        assertThat(defaultSorted.items()).hasSize(3);
        assertThat(defaultSorted.totalMatching()).isEqualTo(7);
        assertThat(defaultSorted.hasMore()).isTrue();
        assertThat(queries)
                .as("no `sort` still means `recommended`, so this is the ranked path, not the fast path")
                .anyMatch(sql -> sql.contains("rank_views") || sql.contains("rank_copies"));
        assertThat(queries)
                .filteredOn(sql -> sql.startsWith("select n.id as id from"))
                .as("the query that chooses the ranked page must be limited by the database")
                .isNotEmpty()
                .allMatch(sql -> sql.contains("fetch first") || sql.contains("limit "));
    }

    /**
     * ⚠️ THE A1 CONTRACT GUARD. {@code /notes/public} is a public HTTP contract and a caller that
     * sends neither {@code page} nor {@code pageSize} still reaches the unpaginated shape. It must
     * degrade to a sane BOUNDED response — never a 400, and never the whole catalog — and an
     * unrecognised {@code sort} must still be ignored rather than rejected, which is what the Java
     * sorter's {@code default} branch did.
     */
    @Test
    void unpaginatedRequestIsBoundedKeepsUpdatedAtOrderAndNeverRejectsAnUnknownSort() {
        UUID ownerId = insertUser("legacybound", UserRole.USER);
        NoteEntity oldestButMostViewed = null;
        for (int index = 0; index < 55; index++) {
            NoteEntity note = saveNote(ownerId, "Bulk " + index, "Science", null, new String[]{"bulk"},
                    NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, index + 1);
            if (index == 0) {
                oldestButMostViewed = note;
            }
        }
        // ⚠️ Without this the fixture cannot tell `updated_at desc` from `recommended`: with every note
        // scoring zero the two orders coincide. Giving the OLDEST note the engagement makes them
        // disagree, so a change of the unpaginated default is caught here and not only in the
        // repository harness.
        recordViews(oldestButMostViewed.getId(), 50L);
        flushAndClear();

        PublicNoteListResponse unsized = noteService.listPublic(
                null, null, null, null, List.of(), null, null, null);
        PublicNoteListResponse unknownSort = noteService.listPublic(
                null, null, "not-a-sort", null, List.of(), null, null, null);
        PublicNoteListResponse sized = noteService.listPublic(
                null, null, null, null, List.of(), null, null, 4);

        assertThat(unsized.items())
                .as("an unpaginated request with no size is capped instead of returning all 55")
                .hasSize(50);
        assertThat(unsized.total())
                .as("total still reports every match, so nothing downstream loses a number")
                .isEqualTo(55);
        assertThat(unsized.page()).isNull();
        assertThat(unsized.pageSize()).isNull();
        assertThat(unknownSort.items()).extracting(NoteListItemResponse::id)
                .as("an unrecognised sort is ignored, not rejected, and keeps updated_at desc order")
                .isEqualTo(ids(unsized));
        assertThat(ids(sized)).isEqualTo(ids(unsized).subList(0, 4));
        assertThat(sized.items().getFirst().title())
                .as("updated_at desc: the newest note is first, NOT the most-viewed one recommended would lead with")
                .isEqualTo("Bulk 54");
        assertThat(unsized.items().getFirst().title()).isEqualTo("Bulk 54");
        assertThat(unknownSort.items().getFirst().title()).isEqualTo("Bulk 54");
        assertThat(noteService.listPublic(null, null, null, null, List.of(), null, "nobody", null).items())
                .as("an unknown creator still matches nothing rather than falling back to everything")
                .isEmpty();
    }

    @Test
    void unpaginatedRequestOnAnEmptyCatalogReturnsAnEmptyBoundedResponseForEverySort() {
        for (String sort : List.of("featured", "popular", "copied", "recent", "views", "title",
                "most_copied", "recommended")) {
            PublicNoteListResponse response = noteService.listPublic(
                    null, null, sort, null, List.of(), null, null, null);
            assertThat(response.items()).as("sort=%s", sort).isEmpty();
            assertThat(response.total()).isZero();
        }
    }

    @Test
    void discoverySectionsAreCappedUnfilteredAndMutuallyExclusive() {
        UUID ownerId = insertUser("discoveryowner", UserRole.USER);
        List<NoteEntity> featuredNotes = new ArrayList<>();
        List<NoteEntity> popularNotes = new ArrayList<>();
        List<NoteEntity> recentNotes = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            NoteEntity note = saveNote(ownerId, "Featured " + index, "Science", null, new String[]{"discover"},
                    NoteStatus.GENERATED, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, index + 1);
            insertStudyPack(note, true);
            recordViews(note.getId(), 100L + index);
            featuredNotes.add(note);
        }
        for (int index = 0; index < 7; index++) {
            NoteEntity note = saveNote(ownerId, "Popular " + index, "Science", null, new String[]{"discover"},
                    NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, index + 20);
            recordViews(note.getId(), 30L + index);
            popularNotes.add(note);
        }
        for (int index = 0; index < 7; index++) {
            recentNotes.add(saveNote(ownerId, "Recent " + index, "Science", null, new String[]{"discover"},
                    NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, index + 40));
        }
        NoteEntity boardOnly = saveNote(ownerId, "Board only", "Board", null, new String[]{"board"},
                NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.BOARD_TAKER, 60);
        saveNote(ownerId, "Private newest", "Science", null, new String[]{"discover"},
                NoteStatus.GENERATED, NoteVisibility.PRIVATE, NoteTargetProfileType.STUDENT, 100);
        flushAndClear();

        PublicLibraryDiscoverySectionsResponse sections = noteService.getPublicLibraryDiscoverySections(null);
        Set<String> featuredIds = ids(sections.featured());
        Set<String> popularIds = ids(sections.popular());
        Set<String> recentIds = ids(sections.recent());

        assertThat(sections.featured()).hasSize(6);
        assertThat(sections.popular()).hasSize(6);
        assertThat(sections.recent()).hasSize(6);
        assertThat(featuredIds).doesNotContainAnyElementsOf(popularIds).doesNotContainAnyElementsOf(recentIds);
        assertThat(popularIds).doesNotContainAnyElementsOf(recentIds);
        assertThat(featuredIds).allMatch(id -> featuredNotes.stream().anyMatch(note -> note.getId().toString().equals(id)));
        Set<String> allPublicNoteIds = java.util.stream.Stream.of(featuredNotes, popularNotes, recentNotes)
                .flatMap(List::stream)
                .map(note -> note.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        allPublicNoteIds.add(boardOnly.getId().toString());
        assertThat(popularIds).allMatch(allPublicNoteIds::contains);
        assertThat(recentIds).allMatch(allPublicNoteIds::contains)
                .contains(boardOnly.getId().toString());
    }

    private PublicNoteListResponse page(
            UUID viewerUserId,
            String search,
            String sort,
            String subject,
            List<String> tags,
            String courseProgram,
            String creator,
            boolean readyOnly,
            List<String> sources,
            int page,
            int pageSize
    ) {
        return noteService.listPublic(
                viewerUserId,
                search,
                sort,
                subject,
                tags,
                courseProgram,
                creator,
                null,
                null,
                page,
                pageSize,
                readyOnly,
                sources
        );
    }

    private PublicNoteListResponse searchPage(String search) {
        return page(null, search, "recent", null, List.of(), null, null, false, List.of(), 0, 20);
    }

    private PublicNoteListResponse pageAtLevel(LearnerLevel learnerLevel) {
        return noteService.listPublic(
                null,
                null,
                "recent",
                null,
                List.of(),
                null,
                null,
                learnerLevel,
                null,
                0,
                20,
                false,
                List.of()
        );
    }

    private PublicNoteListResponse legacyAtLevel(LearnerLevel learnerLevel) {
        return noteService.listPublic(
                null,
                null,
                "recent",
                null,
                List.of(),
                null,
                null,
                learnerLevel,
                null,
                null,
                null,
                false,
                List.of()
        );
    }

    private List<String> ids(PublicNoteListResponse response) {
        return response.items().stream().map(NoteListItemResponse::id).toList();
    }

    private Set<String> ids(List<NoteListItemResponse> items) {
        return items.stream().map(NoteListItemResponse::id).collect(java.util.stream.Collectors.toSet());
    }

    private NoteEntity saveNote(
            UUID ownerUserId,
            String title,
            String subject,
            String courseProgram,
            String[] tags,
            NoteStatus status,
            NoteVisibility visibility,
            NoteTargetProfileType targetProfileType,
            int createdDay
    ) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(ownerUserId);
        note.setTitle(title);
        note.setSubject(subject);
        note.setCourseProgram(courseProgram);
        note.setTags(tags);
        note.setContent("Content preview for " + title);
        note.setStatus(status);
        note.setVisibility(visibility);
        note.setTargetProfileType(targetProfileType);
        note.setCopiedFromPublic(false);
        note.setCreatedAt(BASE_TIME.plusDays(createdDay));
        note.setUpdatedAt(BASE_TIME.plusDays(createdDay));
        return noteRepository.save(note);
    }

    private void insertCopies(UUID sourceNoteId, UUID ownerUserId, int count) {
        for (int index = 0; index < count; index++) {
            NoteEntity copy = saveNote(
                    ownerUserId,
                    "Private copy " + sourceNoteId + " " + index,
                    null,
                    null,
                    new String[0],
                    NoteStatus.DRAFT,
                    NoteVisibility.PRIVATE,
                    NoteTargetProfileType.STUDENT,
                    -index - 1
            );
            copy.setCopiedFromNoteId(sourceNoteId);
            copy.setCopiedFromPublic(true);
            noteRepository.save(copy);
        }
    }

    /**
     * Records real {@code PUBLIC_NOTE_VIEWED} rows AND the mocked DTO count together. The ranking now
     * reads the rows; {@code toListItems} still reads the mock for the response body, so a fixture
     * that set only one of the two would assert an ordering the product cannot produce.
     */
    private void recordViews(UUID noteId, long count) {
        for (long index = 0; index < count; index++) {
            jdbcTemplate.update(
                    "insert into analytics_events (id, user_id, event_type, entity_id, metadata_json, created_at)"
                            + " values (?, null, 'PUBLIC_NOTE_VIEWED', ?, '{}', ?)",
                    UUID.randomUUID(), noteId, BASE_TIME
            );
        }
        viewCounts.put(noteId, count);
    }

    private void recordLikes(UUID noteId, long count) {
        for (long index = 0; index < count; index++) {
            jdbcTemplate.update(
                    "insert into public_note_likes (id, note_id, user_id, created_at) values (?, ?, ?, ?)",
                    UUID.randomUUID(), noteId, UUID.randomUUID(), BASE_TIME
            );
        }
        likeCounts.put(noteId, count);
    }

    private void insertStudyPack(NoteEntity note, boolean withQuiz) {
        UUID studyPackId = UUID.randomUUID();
        String quizJson = withQuiz
                ? "[{\"question\":\"Question?\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0}]"
                : "[]";
        jdbcTemplate.update(
                """
                        insert into study_packs (
                            id, owner_user_id, note_id, input_type, title, summary, key_concepts, quiz,
                            model_tier, model_used, status, tags, created_at, updated_at
                        ) values (?, ?, ?, 'TEXT', 'Pack', 'Meaningful summary', '[]', ? format json,
                                  'STANDARD', 'test-model', 'DONE', array['test'], ?, ?)
                        """,
                studyPackId,
                note.getOwnerUserId(),
                note.getId(),
                quizJson,
                BASE_TIME,
                BASE_TIME
        );
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(note.getOwnerUserId());
        studyPack.setNoteId(note.getId());
        studyPack.setSummary("Meaningful summary");
        studyPack.setQuiz(withQuiz
                ? List.of(new QuizItem("Question?", List.of("A", "B"), 0, "Concept", "Explanation"))
                : List.of());
        studyPacks.put(note.getId(), studyPack);
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

    private UUID insertUser(String username, UserRole role) {
        return insertUserWithId(UUID.randomUUID(), username, role);
    }

    private UUID insertUserWithId(UUID userId, String username, UserRole role) {
        jdbcTemplate.update(
                """
                        insert into users (
                            id, email, first_name, username, focus_subjects, public_profile_visible,
                            engagement_mode, inactivity_reminders_enabled, weak_concept_reminders_enabled,
                            weekly_summary_reminders_enabled, due_concepts_digest_reminders_enabled,
                            marketing_emails_enabled, theme_preference, status, role, token_version,
                            failed_login_attempts, current_streak, longest_streak, created_at, updated_at
                        ) values (?, ?, 'Test', ?, array[]::varchar array, true,
                                  'STANDARD', false, false, false, false, false, 'SYSTEM', 'ACTIVE', ?, 0,
                                  0, 0, 0, ?, ?)
                        """,
                userId,
                username + "@example.com",
                username,
                role.name(),
                BASE_TIME,
                BASE_TIME
        );
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(username + "@example.com");
        user.setFirstName("Test");
        user.setUsername(username);
        user.setRole(role);
        users.put(userId, user);
        return userId;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
        SqlCaptureStatementInspector.clear();
    }

    private List<String> publicPaginationQueries() {
        return SqlCaptureStatementInspector.statements().stream()
                .map(String::toLowerCase)
                .filter(sql -> sql.startsWith("select"))
                .filter(sql -> sql.contains("from notes n"))
                .filter(sql -> sql.contains("n.visibility = 'public'"))
                .toList();
    }

    private NoteService createNoteService() {
        AnalyticsEventRepository analyticsEventRepository = mock(AnalyticsEventRepository.class);
        PublicNoteLikeRepository publicNoteLikeRepository = mock(PublicNoteLikeRepository.class);
        StudyPackRepository studyPackRepository = mock(StudyPackRepository.class);
        GeneratedQuizRepository generatedQuizRepository = mock(GeneratedQuizRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        QuizSessionHistoryService quizSessionHistoryService = mock(QuizSessionHistoryService.class);

        lenient().when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(any(), any()))
                .thenAnswer(invocation -> {
                    AnalyticsEventType type = invocation.getArgument(0);
                    List<UUID> requestedIds = invocation.getArgument(1);
                    Map<UUID, Long> source = type == AnalyticsEventType.PUBLIC_NOTE_VIEWED
                            ? viewCounts
                            : shareCounts;
                    return requestedIds.stream()
                            .filter(source::containsKey)
                            .map(noteId -> eventCount(noteId, source.get(noteId)))
                            .toList();
                });
        lenient().when(publicNoteLikeRepository.countLikesByNoteIds(any())).thenAnswer(invocation -> {
            List<UUID> requestedIds = invocation.getArgument(0);
            return requestedIds.stream()
                    .filter(likeCounts::containsKey)
                    .map(noteId -> likeCount(noteId, likeCounts.get(noteId)))
                    .toList();
        });
        lenient().when(publicNoteLikeRepository.findLikedNoteIdsByUserIdAndNoteIdIn(any(), any()))
                .thenReturn(List.of());
        lenient().when(studyPackRepository.findByNoteIdIn(any())).thenAnswer(invocation -> {
            List<UUID> requestedIds = invocation.getArgument(0);
            return requestedIds.stream().map(studyPacks::get).filter(java.util.Objects::nonNull).toList();
        });
        lenient().when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(any(), any())).thenReturn(List.of());
        lenient().when(userRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> requestedIds = invocation.getArgument(0);
            List<UserEntity> result = new ArrayList<>();
            requestedIds.forEach(noteId -> {
                UserEntity user = users.get(noteId);
                if (user != null) {
                    result.add(user);
                }
            });
            return result;
        });
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

    private PublicNoteEventCountProjection eventCount(UUID noteId, long count) {
        PublicNoteEventCountProjection projection = mock(PublicNoteEventCountProjection.class);
        when(projection.getNoteId()).thenReturn(noteId);
        when(projection.getTotalCount()).thenReturn(count);
        return projection;
    }

    private PublicNoteLikeCountProjection likeCount(UUID noteId, long count) {
        PublicNoteLikeCountProjection projection = mock(PublicNoteLikeCountProjection.class);
        when(projection.getNoteId()).thenReturn(noteId);
        when(projection.getLikeCount()).thenReturn(count);
        return projection;
    }

    private void createSchema() {
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
        createRankingMetricSchema();
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
                    updated_at timestamp with time zone not null
                )
                """);
    }

    /**
     * ⚠️ Added with v0.119.1. The engagement metrics behind Featured/Popular/Recommended used to be
     * read through mocked repositories and merged in Java; the ranking now happens in SQL, so these
     * tables have to exist and carry real rows or the ordering under test is not the shipped one.
     */
    private void createRankingMetricSchema() {
        jdbcTemplate.execute("""
                create table if not exists analytics_events (
                    id uuid primary key,
                    user_id uuid,
                    event_type varchar(64) not null,
                    entity_id uuid,
                    metadata_json varchar(2000) not null default '{}',
                    created_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists public_note_likes (
                    id uuid primary key,
                    note_id uuid not null,
                    user_id uuid not null,
                    created_at timestamp with time zone not null
                )
                """);
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
}
