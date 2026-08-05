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

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
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

        List<String> nursingFilterIds = ids(page(null, null, "recent", null, List.of(), "nursing", null,
                null, false, List.of(), 0, 20));
        List<String> excludedFilterIds = ids(page(null, null, "recent", null, List.of(), "software-engineering", null,
                null, false, List.of(), 0, 20));
        List<String> nursingSearchIds = ids(searchPage("nursing"));
        List<String> excludedSearchIds = ids(searchPage("software engineering"));

        assertThat(nursingFilterIds).containsExactly(nursing.getId().toString());
        assertThat(excludedFilterIds).containsExactly(excluded.getId().toString());
        assertThat(nursingSearchIds).containsExactly(nursing.getId().toString());
        assertThat(excludedSearchIds).containsExactly(excluded.getId().toString());
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

        PublicNoteListResponse accountancy = page(null, null, "recent", null, List.of(), "accountancy", null,
                null, false, List.of(), 0, 20);

        assertThat(ids(accountancy)).containsExactly(curated.getId().toString());
        assertThat(accountancy.items().getFirst().applicablePrograms())
                .containsExactly(ACCOUNTANCY_PROGRAM, NURSING_PROGRAM);
        assertThat(ids(searchPage("nursing"))).containsExactly(curated.getId().toString());
        assertThat(ids(page(null, null, "recent", null, List.of(), "pharmacy", null,
                null, false, List.of(), 0, 20))).isEmpty();
        assertThat(ids(searchPage("pharmacy"))).isEmpty();
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
                NoteTargetProfileType.STUDENT,
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
                NoteTargetProfileType.STUDENT,
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
                NoteTargetProfileType.STUDENT,
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
                viewerId, null, null, null, null, null, null, null, null,
                null, null, true, List.of("OFFICIAL")
        );
        PublicNoteListResponse paginatedViewer = page(
                viewerId, null, "recent", null, List.of(), null, null,
                null, true, List.of("BY_YOU"), 0, 20
        );
        PublicNoteListResponse paginatedCommunity = page(
                viewerId, null, "recent", null, List.of(), null, null,
                null, false, List.of("COMMUNITY"), 0, 20
        );
        PublicNoteListResponse combined = page(
                viewerId, null, "recent", null, List.of(READY_SOURCE_TAG), null, null,
                null, true, List.of("BY_YOU", "OFFICIAL"), 0, 20
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
                viewerId, null, "recent", null, List.of(), null, null,
                null, false, List.of("UNKNOWN"), 0, 20
        )).isInstanceOf(InvalidPublicLibraryQueryException.class);
        assertThatThrownBy(() -> page(
                viewerId, null, "unknown", null, List.of(), null, null,
                null, false, List.of(), 0, 20
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
        PublicNoteListResponse titleFirst = page(null, null, "title", null, List.of(), null, null,
                null, false, List.of(), 0, 2);
        List<String> firstPageQueries = publicPaginationQueries();
        PublicNoteListResponse titleSecond = page(null, null, "title", null, List.of(), null, null,
                null, false, List.of(), 1, 2);
        PublicNoteListResponse recent = page(null, null, "recent", null, List.of(), null, null,
                null, false, List.of(), 0, 20);

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
        viewCounts.put(featured.getId(), 12L);
        viewCounts.put(popular.getId(), 30L);
        insertCopies(copied.getId(), ownerId, 4);
        flushAndClear();

        PublicNoteListResponse featuredPage = page(null, null, "featured", null, List.of(), null, null,
                null, false, List.of(), 0, 20);
        PublicNoteListResponse popularPage = page(null, null, "popular", null, List.of(), null, null,
                null, false, List.of(), 0, 20);
        PublicNoteListResponse copiedAliasPage = page(null, null, "copied", null, List.of(), null, null,
                null, false, List.of(), 0, 20);
        SqlCaptureStatementInspector.clear();
        PublicNoteListResponse mostCopiedFirstPage = page(null, null, "most_copied", null, List.of(), null, null,
                null, false, List.of(), 0, 2);
        List<String> materializedQueries = publicPaginationQueries();
        PublicNoteListResponse mostCopiedSecondPage = page(null, null, "most_copied", null, List.of(), null, null,
                null, false, List.of(), 1, 2);
        PublicNoteListResponse recommendedPage = page(null, null, "recommended", null, List.of(), null, null,
                null, false, List.of(), 0, 20);
        PublicNoteListResponse viewsPage = page(null, null, "views", null, List.of(), null, null,
                null, false, List.of(), 0, 20);

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
        assertThat(materializedQueries).hasSize(2);
        assertThat(materializedQueries).anyMatch(sql -> sql.contains("left join study_packs sp"));
        assertThat(materializedQueries).anyMatch(sql -> sql.contains("substring(n.content"));
    }

    @Test
    void discoverySectionsAreCappedAudienceScopedAndMutuallyExclusive() {
        UUID ownerId = insertUser("discoveryowner", UserRole.USER);
        List<NoteEntity> featuredNotes = new ArrayList<>();
        List<NoteEntity> popularNotes = new ArrayList<>();
        List<NoteEntity> recentNotes = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            NoteEntity note = saveNote(ownerId, "Featured " + index, "Science", null, new String[]{"discover"},
                    NoteStatus.GENERATED, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, index + 1);
            insertStudyPack(note, true);
            viewCounts.put(note.getId(), 100L + index);
            featuredNotes.add(note);
        }
        for (int index = 0; index < 7; index++) {
            NoteEntity note = saveNote(ownerId, "Popular " + index, "Science", null, new String[]{"discover"},
                    NoteStatus.DRAFT, NoteVisibility.PUBLIC, NoteTargetProfileType.STUDENT, index + 20);
            viewCounts.put(note.getId(), 30L + index);
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

        PublicLibraryDiscoverySectionsResponse sections = noteService.getPublicLibraryDiscoverySections(
                null, NoteTargetProfileType.STUDENT
        );
        Set<String> featuredIds = ids(sections.featured());
        Set<String> popularIds = ids(sections.popular());
        Set<String> recentIds = ids(sections.recent());

        assertThat(sections.featured()).hasSize(6);
        assertThat(sections.popular()).hasSize(6);
        assertThat(sections.recent()).hasSize(6);
        assertThat(featuredIds).doesNotContainAnyElementsOf(popularIds).doesNotContainAnyElementsOf(recentIds);
        assertThat(popularIds).doesNotContainAnyElementsOf(recentIds);
        assertThat(featuredIds).allMatch(id -> featuredNotes.stream().anyMatch(note -> note.getId().toString().equals(id)));
        Set<String> allPublicStudentIds = java.util.stream.Stream.of(featuredNotes, popularNotes, recentNotes)
                .flatMap(List::stream)
                .map(note -> note.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(popularIds).allMatch(allPublicStudentIds::contains);
        assertThat(recentIds).allMatch(allPublicStudentIds::contains);

        PublicLibraryDiscoverySectionsResponse boardSections = noteService.getPublicLibraryDiscoverySections(
                null, NoteTargetProfileType.BOARD_TAKER
        );
        assertThat(boardSections.featured()).isEmpty();
        assertThat(boardSections.popular()).isEmpty();
        assertThat(boardSections.recent()).extracting(NoteListItemResponse::id)
                .containsExactly(boardOnly.getId().toString());
    }

    private PublicNoteListResponse page(
            UUID viewerUserId,
            String search,
            String sort,
            String subject,
            List<String> tags,
            String courseProgram,
            String creator,
            NoteTargetProfileType targetProfileType,
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
                targetProfileType,
                null,
                page,
                pageSize,
                readyOnly,
                sources
        );
    }

    private PublicNoteListResponse searchPage(String search) {
        return page(null, search, "recent", null, List.of(), null, null,
                null, false, List.of(), 0, 20);
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
                mock(NoteApplicableProgramsMaintenanceService.class),
                mock(com.studysnap.backend.repository.NoteCourseProgramRepository.class)
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
