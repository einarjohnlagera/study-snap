package com.studysnap.backend.service;

import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.AdoptGoalResponse;
import com.studysnap.backend.dto.AdoptStudyPlanResponse;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionFaqItem;
import com.studysnap.backend.dto.CompanionMentorTip;
import com.studysnap.backend.dto.CompanionMentorTipAction;
import com.studysnap.backend.dto.CompanionMentorTipSurfacingCondition;
import com.studysnap.backend.dto.CompanionMentorTipSurfacingConditionType;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.CompanionStructureSnapshot;
import com.studysnap.backend.dto.CreateNoteCollectionRequest;
import com.studysnap.backend.dto.GenerateCompanionRequest;
import com.studysnap.backend.dto.GeneratedCompanionContentResponse;
import com.studysnap.backend.dto.GoalCollectionChildResponse;
import com.studysnap.backend.dto.GoalCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionItemResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.NoteConceptCountsResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.PlanReadinessResponse;
import com.studysnap.backend.dto.SetNoteCollectionChildrenOrderRequest;
import com.studysnap.backend.dto.SetNoteCollectionParentRequest;
import com.studysnap.backend.dto.SetNoteCollectionOrderRequest;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.dto.UpdateNoteCollectionRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.CollectionItemNotFoundException;
import com.studysnap.backend.model.StudyPackProgressProjection;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.exception.CollectionNotPublishableException;
import com.studysnap.backend.exception.InvalidCollectionRequestException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.GeneratedQuizNoteProjection;
import com.studysnap.backend.repository.NoteCollectionChildCountProjection;
import com.studysnap.backend.repository.NoteCollectionItemCountProjection;
import com.studysnap.backend.repository.NoteCollectionItemNoteProjection;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteCollectionNoteProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteCollectionServiceTest {

    private static final String COLLECTION_TITLE = "Biology Unit";
    private static final String COLLECTION_DESCRIPTION = "Cell biology review";
    private static final String NOTE_TITLE_ONE = "Cell Structure";
    private static final String NOTE_TITLE_TWO = "Cell Transport";
    private static final String NOTE_TITLE_THREE = "Photosynthesis";
    private static final String BIOLOGY_SUBJECT = "Biology";
    private static final String COURSE_PROGRAM = "STEM";
    private static final String WEEK_ONE_LABEL = "Week 1";
    private static final String WEEK_TWO_LABEL = "Week 2";
    private static final String WHITESPACE = "  ";
    private static final String REPEATED_CHARACTER = "a";
    private static final String NEWER_COLLECTION_TITLE = "Newer";
    private static final String EARLIER_COLLECTION_TITLE = "Earlier";
    private static final String UPDATED_COLLECTION_TITLE = "Updated";
    private static final String UPDATED_COLLECTION_DESCRIPTION = "Updated description";
    private static final String UPDATED_COURSE_PROGRAM = "Licensure Examination for Teachers";
    private static final String OLDEST_CONCEPT = "Oldest";
    private static final String OLDER_CONCEPT = "Older";
    private static final String NEVER_SEEN_CONCEPT = "Never Seen";
    private static final String NEWEST_DUE_CONCEPT = "Newest Due";
    private static final String CURRENT_CONCEPT = "Current";
    private static final String BASE_TIMESTAMP = "2026-04-01T00:00:00Z";
    private static final String QUIZ_TIMESTAMP = "2026-04-01T02:00:00Z";
    private static final OffsetDateTime FIRST_PRACTICED_AT = OffsetDateTime.parse("2026-04-02T01:00:00Z");
    private static final OffsetDateTime SECOND_PRACTICED_AT = OffsetDateTime.parse("2026-04-03T01:00:00Z");

    @Mock
    private NoteCollectionRepository collectionRepository;

    @Mock
    private NoteCollectionItemRepository itemRepository;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private StudyPackRepository studyPackRepository;

    @Mock
    private GeneratedQuizRepository generatedQuizRepository;

    @Mock
    private QuizSessionHistoryService quizSessionHistoryService;

    @Mock
    private ConceptHealthService conceptHealthService;

    @Mock
    private ProgressReportService progressReportService;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private NoteService noteService;

    @Mock
    private LlmStudyPackService llmStudyPackService;

    @Mock
    private UserRepository userRepository;

    private NoteCollectionService service;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return Optional.of(buildUser(userId));
        });
        service = new NoteCollectionService(
                collectionRepository,
                itemRepository,
                noteRepository,
                studyPackRepository,
                generatedQuizRepository,
                quizSessionHistoryService,
                conceptHealthService,
                progressReportService,
                analyticsService,
                noteService,
                llmStudyPackService,
                userRepository,
                TransactionOperations.withoutTransaction()
        );
    }

    @Test
    void create_withNoItemsSavesCollection() {
        UUID userId = UUID.randomUUID();
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        NoteCollectionDetailResponse result = service.create(userId, new CreateNoteCollectionRequest(
                WHITESPACE + COLLECTION_TITLE + WHITESPACE,
                WHITESPACE + COLLECTION_DESCRIPTION + WHITESPACE,
                null
        ));

        assertThat(result.title()).isEqualTo(COLLECTION_TITLE);
        assertThat(result.description()).isEqualTo(COLLECTION_DESCRIPTION);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void create_persistsExplicitLearnerLevel() {
        UUID userId = UUID.randomUUID();
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        NoteCollectionDetailResponse result = service.create(userId, new CreateNoteCollectionRequest(
                COLLECTION_TITLE,
                null,
                null,
                LearnerLevel.BOARD_EXAM_REVIEW.name()
        ));

        assertThat(result.learnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW.name());
    }

    @Test
    void create_withValidOwnedNoteIdsPreservesOrder() {
        UUID userId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        NoteEntity firstNote = buildNote(firstNoteId, userId, NOTE_TITLE_ONE);
        NoteEntity secondNote = buildNote(secondNoteId, userId, NOTE_TITLE_TWO);
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(firstNoteId, secondNoteId)))
                .thenReturn(List.of(secondNote, firstNote));
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(firstNoteId, secondNoteId), List.of(firstNote, secondNote));

        NoteCollectionDetailResponse result = service.create(userId, new CreateNoteCollectionRequest(
                COLLECTION_TITLE,
                null,
                List.of(firstNoteId, secondNoteId)
        ));

        assertThat(result.items()).extracting(item -> item.noteId()).containsExactly(firstNoteId, secondNoteId);
        assertThat(result.items()).extracting(item -> item.position()).containsExactly(0, 1);
        assertThat(result.items()).extracting(item -> item.label()).containsOnlyNulls();
    }

    @Test
    void create_dedupesRepeatedNoteIds() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note));
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(noteId), List.of(note));

        NoteCollectionDetailResponse result = service.create(userId, new CreateNoteCollectionRequest(
                COLLECTION_TITLE,
                null,
                List.of(noteId, noteId)
        ));

        assertThat(result.items()).extracting(item -> item.noteId()).containsExactly(noteId);
    }

    @Test
    void create_tracksCollectionCreatedOnceWithInitialItemCount() {
        UUID userId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        NoteEntity firstNote = buildNote(firstNoteId, userId, NOTE_TITLE_ONE);
        NoteEntity secondNote = buildNote(secondNoteId, userId, NOTE_TITLE_TWO);
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(firstNoteId, secondNoteId)))
                .thenReturn(List.of(firstNote, secondNote));
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(firstNoteId, secondNoteId), List.of(firstNote, secondNote));

        NoteCollectionDetailResponse result = service.create(userId, new CreateNoteCollectionRequest(
                COLLECTION_TITLE,
                null,
                List.of(firstNoteId, secondNoteId)
        ));

        verify(analyticsService, times(1)).trackEvent(
                userId,
                AnalyticsEventType.COLLECTION_CREATED,
                result.id(),
                Map.of("itemCount", 2)
        );
    }

    @Test
    void create_withForeignNoteThrowsAndDoesNotPersist() {
        UUID userId = UUID.randomUUID();
        UUID foreignNoteId = UUID.randomUUID();
        CreateNoteCollectionRequest request = new CreateNoteCollectionRequest(
                COLLECTION_TITLE,
                null,
                List.of(foreignNoteId)
        );
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(foreignNoteId))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(NoteNotFoundException.class);
        verify(collectionRepository, never()).save(any(NoteCollectionEntity.class));
        verify(itemRepository, never()).saveAll(anyList());
    }

    @Test
    void list_returnsCallerCollectionsOnlyNewestUpdatedFirst() {
        UUID userId = UUID.randomUUID();
        UUID newerNoteId = UUID.randomUUID();
        UUID olderNoteId = UUID.randomUUID();
        NoteCollectionEntity newer = buildCollection(UUID.randomUUID(), userId, NEWER_COLLECTION_TITLE, Instant.parse("2026-04-02T00:00:00Z"));
        NoteCollectionEntity older = buildCollection(UUID.randomUUID(), userId, EARLIER_COLLECTION_TITLE, Instant.parse(BASE_TIMESTAMP));
        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(newer, older));
        when(itemRepository.countItemsByCollectionIds(List.of(newer.getId(), older.getId())))
                .thenReturn(List.of(countProjection(newer.getId(), 2), countProjection(older.getId(), 1)));
        when(collectionRepository.countChildrenByCollectionIds(List.of(newer.getId(), older.getId())))
                .thenReturn(List.of(childCountProjection(newer.getId(), 3)));
        when(itemRepository.findNoteIdsByCollectionIds(List.of(newer.getId(), older.getId())))
                .thenReturn(List.of(
                        noteProjection(newer.getId(), newerNoteId),
                        noteProjection(older.getId(), olderNoteId)
                ));
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(eq(userId), anyList()))
                .thenReturn(Map.of(newerNoteId, FIRST_PRACTICED_AT));

        List<NoteCollectionSummaryResponse> result = service.list(userId);

        assertThat(result).extracting(NoteCollectionSummaryResponse::title)
                .containsExactly(NEWER_COLLECTION_TITLE, EARLIER_COLLECTION_TITLE);
        assertThat(result).extracting(NoteCollectionSummaryResponse::itemCount).containsExactly(2, 1);
        assertThat(result).extracting(NoteCollectionSummaryResponse::childCount).containsExactly(3, 0);
        assertThat(result).extracting(NoteCollectionSummaryResponse::notesPracticed).containsExactly(1, 0);
        verify(collectionRepository).findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId);
        ArgumentCaptor<List<UUID>> noteIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(quizSessionHistoryService, times(1))
                .findLatestSessionCompletedAtByNoteIds(eq(userId), noteIdsCaptor.capture());
        assertThat(noteIdsCaptor.getValue()).containsExactlyInAnyOrder(newerNoteId, olderNoteId);
    }

    @Test
    void listNoteAccepting_excludesGoalsAndResolvesParentLearnerLevel() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(UUID.randomUUID(), userId, "Goal", Instant.now());
        goal.setLearnerLevel(LearnerLevel.BOARD_EXAM_REVIEW);
        NoteCollectionEntity leaf = buildCollection(UUID.randomUUID(), userId, "Engineering Math", Instant.now());
        leaf.setParentCollectionId(goal.getId());
        when(collectionRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of(goal, leaf));
        when(collectionRepository.countChildrenByCollectionIds(List.of(goal.getId(), leaf.getId())))
                .thenReturn(List.of(childCountProjection(goal.getId(), 1)));
        when(itemRepository.countItemsByCollectionIds(List.of(goal.getId(), leaf.getId()))).thenReturn(List.of());
        when(itemRepository.findNoteIdsByCollectionIds(List.of(goal.getId(), leaf.getId()))).thenReturn(List.of());
        when(collectionRepository.findById(leaf.getId())).thenReturn(Optional.of(leaf));
        when(collectionRepository.findById(goal.getId())).thenReturn(Optional.of(goal));

        List<NoteCollectionSummaryResponse> result = service.listNoteAccepting(userId);

        assertThat(result).extracting(NoteCollectionSummaryResponse::id).containsExactly(leaf.getId());
        assertThat(result.getFirst().resolvedLearnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW.name());
    }

    @Test
    void list_rollsUpGoalItemAndReadyCountsFromChildrenWhileKeepingLeafCountsDirect() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(UUID.randomUUID(), userId, "Goal", Instant.parse("2026-04-03T00:00:00Z"));
        NoteCollectionEntity leaf = buildCollection(UUID.randomUUID(), userId, "Leaf", Instant.parse("2026-04-02T00:00:00Z"));
        NoteCollectionEntity firstChild = buildCollection(UUID.randomUUID(), userId, "First Subject", Instant.now());
        NoteCollectionEntity secondChild = buildCollection(UUID.randomUUID(), userId, "Second Subject", Instant.now());
        firstChild.setParentCollectionId(goal.getId());
        secondChild.setParentCollectionId(goal.getId());
        UUID firstReadyNoteId = UUID.randomUUID();
        UUID firstNotReadyNoteId = UUID.randomUUID();
        UUID secondReadyNoteId = UUID.randomUUID();
        UUID leafReadyNoteId = UUID.randomUUID();
        List<NoteCollectionEntity> collections = List.of(goal, leaf);
        List<NoteCollectionEntity> collectionsWithChildren = List.of(goal, leaf, firstChild, secondChild);
        NoteEntity firstReadyNote = buildNote(firstReadyNoteId, userId, NOTE_TITLE_ONE);
        NoteEntity firstNotReadyNote = buildNote(firstNotReadyNoteId, userId, NOTE_TITLE_TWO);
        NoteEntity secondReadyNote = buildNote(secondReadyNoteId, userId, NOTE_TITLE_THREE);
        NoteEntity leafReadyNote = buildNote(leafReadyNoteId, userId, "Leaf note");

        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenReturn(collections);
        when(collectionRepository.findByParentCollectionIdIn(List.of(goal.getId(), leaf.getId())))
                .thenReturn(List.of(firstChild, secondChild));
        when(itemRepository.countItemsByCollectionIds(collectionsWithChildren.stream().map(NoteCollectionEntity::getId).toList()))
                .thenReturn(List.of(
                        countProjection(firstChild.getId(), 2),
                        countProjection(secondChild.getId(), 1),
                        countProjection(leaf.getId(), 1)
                ));
        when(itemRepository.findNoteIdsByCollectionIds(collectionsWithChildren.stream().map(NoteCollectionEntity::getId).toList()))
                .thenReturn(List.of(
                        noteProjection(firstChild.getId(), firstReadyNoteId),
                        noteProjection(firstChild.getId(), firstNotReadyNoteId),
                        noteProjection(secondChild.getId(), secondReadyNoteId),
                        noteProjection(leaf.getId(), leafReadyNoteId)
                ));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(anyList()))
                .thenReturn(asNoteProjections(firstReadyNote, firstNotReadyNote, secondReadyNote, leafReadyNote));
        when(studyPackRepository.findProgressViewsByNoteIdIn(anyList())).thenReturn(asProjections(
                buildStudyPack(firstReadyNoteId),
                buildStudyPack(secondReadyNoteId),
                buildStudyPack(leafReadyNoteId)
        ));
        when(collectionRepository.countChildrenByCollectionIds(List.of(goal.getId(), leaf.getId())))
                .thenReturn(List.of(childCountProjection(goal.getId(), 2)));

        List<NoteCollectionSummaryResponse> result = service.list(userId);

        assertThat(result).extracting(NoteCollectionSummaryResponse::itemCount).containsExactly(3, 1);
        assertThat(result).extracting(NoteCollectionSummaryResponse::readyCount).containsExactly(2, 1);
        verify(collectionRepository).findByParentCollectionIdIn(List.of(goal.getId(), leaf.getId()));
    }

    @Test
    void list_returnsPracticedCountsForStatusBoundariesFromOnePracticeLookup() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity notStarted = buildCollection(UUID.randomUUID(), userId, "Not Started", Instant.parse("2026-04-04T00:00:00Z"));
        NoteCollectionEntity inProgress = buildCollection(UUID.randomUUID(), userId, "In Progress", Instant.parse("2026-04-03T00:00:00Z"));
        NoteCollectionEntity completed = buildCollection(UUID.randomUUID(), userId, "Completed", Instant.parse("2026-04-02T00:00:00Z"));
        NoteCollectionEntity empty = buildCollection(UUID.randomUUID(), userId, "Empty", Instant.parse(BASE_TIMESTAMP));
        UUID notStartedNoteId = UUID.randomUUID();
        UUID inProgressPracticedNoteId = UUID.randomUUID();
        UUID inProgressUnpracticedNoteId = UUID.randomUUID();
        UUID completedFirstNoteId = UUID.randomUUID();
        UUID completedSecondNoteId = UUID.randomUUID();
        List<NoteCollectionEntity> collections = List.of(notStarted, inProgress, completed, empty);
        List<UUID> collectionIds = collections.stream().map(NoteCollectionEntity::getId).toList();
        List<UUID> noteIds = List.of(
                notStartedNoteId,
                inProgressPracticedNoteId,
                inProgressUnpracticedNoteId,
                completedFirstNoteId,
                completedSecondNoteId
        );
        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenReturn(collections);
        when(itemRepository.countItemsByCollectionIds(collectionIds)).thenReturn(List.of(
                countProjection(notStarted.getId(), 1),
                countProjection(inProgress.getId(), 2),
                countProjection(completed.getId(), 2)
        ));
        when(itemRepository.findNoteIdsByCollectionIds(collectionIds)).thenReturn(List.of(
                noteProjection(notStarted.getId(), notStartedNoteId),
                noteProjection(inProgress.getId(), inProgressPracticedNoteId),
                noteProjection(inProgress.getId(), inProgressUnpracticedNoteId),
                noteProjection(completed.getId(), completedFirstNoteId),
                noteProjection(completed.getId(), completedSecondNoteId)
        ));
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(eq(userId), anyList())).thenReturn(Map.of(
                inProgressPracticedNoteId, FIRST_PRACTICED_AT,
                completedFirstNoteId, FIRST_PRACTICED_AT,
                completedSecondNoteId, SECOND_PRACTICED_AT
        ));

        List<NoteCollectionSummaryResponse> result = service.list(userId);

        assertThat(result).extracting(NoteCollectionSummaryResponse::itemCount).containsExactly(1, 2, 2, 0);
        assertThat(result).extracting(NoteCollectionSummaryResponse::notesPracticed).containsExactly(0, 1, 2, 0);
        ArgumentCaptor<List<UUID>> noteIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(quizSessionHistoryService, times(1))
                .findLatestSessionCompletedAtByNoteIds(eq(userId), noteIdsCaptor.capture());
        assertThat(noteIdsCaptor.getValue()).containsExactlyInAnyOrderElementsOf(noteIds);
    }

    @Test
    void list_skipsPracticeLookupWhenEveryCollectionIsEmpty() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity empty = buildCollection(UUID.randomUUID(), userId, "Empty", Instant.parse(BASE_TIMESTAMP));
        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(empty));
        when(itemRepository.countItemsByCollectionIds(List.of(empty.getId()))).thenReturn(List.of());
        when(itemRepository.findNoteIdsByCollectionIds(List.of(empty.getId()))).thenReturn(List.of());

        List<NoteCollectionSummaryResponse> result = service.list(userId);

        assertThat(result.getFirst().itemCount()).isZero();
        assertThat(result.getFirst().notesPracticed()).isZero();
        verify(quizSessionHistoryService, never()).findLatestSessionCompletedAtByNoteIds(any(), any());
    }

    @Test
    void list_degradesPracticeLookupFailureToZeroPracticedCounts() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(UUID.randomUUID(), userId, COLLECTION_TITLE, Instant.parse(BASE_TIMESTAMP));
        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(collection));
        when(itemRepository.countItemsByCollectionIds(List.of(collection.getId())))
                .thenReturn(List.of(countProjection(collection.getId(), 1)));
        when(itemRepository.findNoteIdsByCollectionIds(List.of(collection.getId())))
                .thenReturn(List.of(noteProjection(collection.getId(), noteId)));
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, List.of(noteId)))
                .thenThrow(new IllegalStateException("session history unavailable"));

        List<NoteCollectionSummaryResponse> result = service.list(userId);

        assertThat(result.getFirst().itemCount()).isEqualTo(1);
        assertThat(result.getFirst().notesPracticed()).isZero();
    }

    @Test
    void get_returnsItemsInPositionOrderWithLeanNotePayload() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity firstItem = buildItem(collectionId, firstNoteId, 0, null);
        NoteCollectionItemEntity secondItem = buildItem(collectionId, secondNoteId, 1, null);
        NoteEntity firstNote = buildNote(firstNoteId, userId, NOTE_TITLE_ONE);
        NoteEntity secondNote = buildNote(secondNoteId, userId, NOTE_TITLE_TWO);
        StudyPackEntity studyPack = buildStudyPack(secondNoteId);
        GeneratedQuizEntity generatedQuiz = buildGeneratedQuiz(secondNoteId, userId);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(firstItem, secondItem));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(List.of(firstNoteId, secondNoteId)))
                .thenReturn(asNoteProjections(firstNote, secondNote));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(firstNoteId, secondNoteId))).thenReturn(asProjections(studyPack));
        when(generatedQuizRepository.findNoteIdsByOwnerUserIdAndNoteIdIn(userId, List.of(firstNoteId, secondNoteId)))
                .thenReturn(asGeneratedQuizProjections(generatedQuiz));
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(
                userId,
                List.of(firstNoteId, secondNoteId)
        )).thenReturn(Map.of(secondNoteId, FIRST_PRACTICED_AT));

        NoteCollectionDetailResponse result = service.get(collectionId, userId);

        assertThat(result.items()).extracting(item -> item.noteId()).containsExactly(firstNoteId, secondNoteId);
        assertThat(result.items().get(0).studyPackStatus()).isEqualTo(NoteStudyPackStatusResolver.DRAFT);
        assertThat(result.items().get(0).lastSessionCompletedAt()).isNull();
        assertThat(result.items().get(1).studyPackStatus()).isEqualTo(NoteStudyPackStatusResolver.STUDY_PACK_READY);
        assertThat(result.items().get(1).generatedQuizId()).isEqualTo(generatedQuiz.getId().toString());
        assertThat(result.items().get(1).lastSessionCompletedAt()).isEqualTo(FIRST_PRACTICED_AT);
        assertThat(result.progress().totalNotes()).isEqualTo(2);
        assertThat(result.progress().notesWithStudyPack()).isEqualTo(1);
        assertThat(result.progress().notesPracticed()).isEqualTo(1);
        verify(quizSessionHistoryService, times(1))
                .findLatestSessionCompletedAtByNoteIds(userId, List.of(firstNoteId, secondNoteId));
    }

    @Test
    void get_returnsMixedCollectionProgressFromAssembledItems() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        UUID thirdNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        List<NoteCollectionItemEntity> items = List.of(
                buildItem(collectionId, firstNoteId, 0, null),
                buildItem(collectionId, secondNoteId, 1, null),
                buildItem(collectionId, thirdNoteId, 2, null)
        );
        List<NoteEntity> notes = List.of(
                buildNote(firstNoteId, userId, NOTE_TITLE_ONE),
                buildNote(secondNoteId, userId, NOTE_TITLE_TWO),
                buildNote(thirdNoteId, userId, NOTE_TITLE_THREE)
        );
        List<UUID> noteIds = List.of(firstNoteId, secondNoteId, thirdNoteId);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(items);
        when(noteRepository.findCollectionNoteProjectionsByIdIn(noteIds)).thenReturn(asNoteProjections(notes));
        when(studyPackRepository.findProgressViewsByNoteIdIn(noteIds))
                .thenReturn(asProjections(buildStudyPack(firstNoteId), buildStudyPack(thirdNoteId)));
        when(generatedQuizRepository.findNoteIdsByOwnerUserIdAndNoteIdIn(userId, noteIds)).thenReturn(List.of());
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, noteIds))
                .thenReturn(Map.of(
                        firstNoteId, FIRST_PRACTICED_AT,
                        secondNoteId, SECOND_PRACTICED_AT
                ));

        NoteCollectionDetailResponse result = service.get(collectionId, userId);

        assertThat(result.progress().totalNotes()).isEqualTo(3);
        assertThat(result.progress().notesWithStudyPack()).isEqualTo(2);
        assertThat(result.progress().notesPracticed()).isEqualTo(2);
        assertThat(result.items()).extracting(item -> item.lastSessionCompletedAt())
                .containsExactly(FIRST_PRACTICED_AT, SECOND_PRACTICED_AT, null);
        verify(quizSessionHistoryService, times(1)).findLatestSessionCompletedAtByNoteIds(userId, noteIds);
    }

    @Test
    void get_returnsZeroProgressForEmptyCollectionWithoutLoadingPracticeSignals() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        collection.setCompanion(companionContent());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.get(collectionId, userId);

        assertThat(result.companion()).isEqualTo(companionContent());
        assertThat(result.progress().totalNotes()).isZero();
        assertThat(result.progress().notesWithStudyPack()).isZero();
        assertThat(result.progress().notesPracticed()).isZero();
        assertThat(result.items()).isEmpty();
        verify(quizSessionHistoryService, never()).findLatestSessionCompletedAtByNoteIds(any(), any());
    }

    @Test
    void getReadiness_returnsOwnerScopedPlanReadinessFromSharedProgressAggregation() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID draftNoteId = UUID.randomUUID();
        UUID biologyNoteId = UUID.randomUUID();
        UUID otherNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        List<NoteCollectionItemEntity> items = List.of(
                buildItem(collectionId, draftNoteId, 0, null),
                buildItem(collectionId, biologyNoteId, 1, null),
                buildItem(collectionId, otherNoteId, 2, null)
        );
        StudyPackEntity biologyPack = buildStudyPack(biologyNoteId, List.of("Cells", "DNA", "Mitosis"));
        biologyPack.setOwnerUserId(userId);
        StudyPackEntity otherPack = buildStudyPack(otherNoteId, List.of("Practice"));
        otherPack.setOwnerUserId(userId);
        StudyPackEntity foreignPack = buildStudyPack(UUID.randomUUID(), List.of("Hidden"));
        foreignPack.setOwnerUserId(UUID.randomUUID());
        List<SubjectProgressEntry> subjects = List.of(
                new SubjectProgressEntry(BIOLOGY_SUBJECT, 3, 2, 1, 0, 67),
                new SubjectProgressEntry("Other", 1, 0, 0, 1, 0)
        );
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(items);
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(draftNoteId, biologyNoteId, otherNoteId)))
                .thenReturn(asProjections(biologyPack, otherPack, foreignPack));
        when(progressReportService.buildSubjectProgressEntries(eq(asProjections(biologyPack, otherPack)), eq(userId), any(OffsetDateTime.class)))
                .thenReturn(subjects);

        PlanReadinessResponse result = service.getReadiness(collectionId, userId);

        assertThat(result.collectionId()).isEqualTo(collectionId);
        assertThat(result.totalNotes()).isEqualTo(3);
        assertThat(result.notesWithStudyPack()).isEqualTo(2);
        assertThat(result.totalConcepts()).isEqualTo(4);
        assertThat(result.masteredConcepts()).isEqualTo(2);
        assertThat(result.dueConcepts()).isEqualTo(1);
        assertThat(result.notPracticedConcepts()).isEqualTo(1);
        assertThat(result.overallReadinessPercentage()).isEqualTo(50);
        assertThat(result.subjects()).containsExactlyElementsOf(subjects);
    }

    @Test
    void getReadiness_returnsZeroShapeForEmptyPlanWithoutProgressAggregation() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        PlanReadinessResponse result = service.getReadiness(collectionId, userId);

        assertThat(result.totalNotes()).isZero();
        assertThat(result.notesWithStudyPack()).isZero();
        assertThat(result.overallReadinessPercentage()).isZero();
        assertThat(result.totalConcepts()).isZero();
        assertThat(result.subjects()).isEmpty();
        verify(studyPackRepository, never()).findProgressViewsByNoteIdIn(anyList());
        verify(progressReportService, never()).buildSubjectProgressEntries(anyList(), any(), any());
    }

    @Test
    void getReadiness_returnsZeroShapeForPlanWithNotesButNoStudyPacks() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(List.of(buildItem(collectionId, noteId, 0, null)));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(List.of());

        PlanReadinessResponse result = service.getReadiness(collectionId, userId);

        assertThat(result.totalNotes()).isEqualTo(1);
        assertThat(result.notesWithStudyPack()).isZero();
        assertThat(result.overallReadinessPercentage()).isZero();
        assertThat(result.subjects()).isEmpty();
        verify(progressReportService, never()).buildSubjectProgressEntries(anyList(), any(), any());
    }

    @Test
    void updateParent_setsParentWhenParentIsTopLevelAndChildHasNoChildren() {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        NoteCollectionEntity parent = buildCollection(parentId, userId, "LET Mastery", Instant.now());
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(childId, userId)).thenReturn(Optional.of(child));
        when(collectionRepository.findByIdAndOwnerUserId(parentId, userId)).thenReturn(Optional.of(parent));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(parentId)).thenReturn(List.of());
        when(collectionRepository.countByParentCollectionId(childId)).thenReturn(0L);
        when(collectionRepository.findMaxSiblingPosition(parentId, userId)).thenReturn(2);
        when(collectionRepository.save(child)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(childId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateParent(
                childId,
                userId,
                new SetNoteCollectionParentRequest(parentId)
        );

        assertThat(child.getParentCollectionId()).isEqualTo(parentId);
        assertThat(child.getSiblingPosition()).isEqualTo(3);
        assertThat(result.parentCollectionId()).isEqualTo(parentId);
    }

    @Test
    void updateParent_clearsTopLevelOnlyFieldsWhenTopLevelGoalBecomesChild() {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        NoteCollectionEntity parent = buildCollection(parentId, userId, "LET Mastery", Instant.now());
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setTargetCompletionDate(LocalDate.parse("2026-12-01"));
        child.setCompanion(companionContent());
        child.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));
        when(collectionRepository.findByIdAndOwnerUserId(childId, userId)).thenReturn(Optional.of(child));
        when(collectionRepository.findByIdAndOwnerUserId(parentId, userId)).thenReturn(Optional.of(parent));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(parentId)).thenReturn(List.of());
        when(collectionRepository.countByParentCollectionId(childId)).thenReturn(0L);
        when(collectionRepository.findMaxSiblingPosition(parentId, userId)).thenReturn(0);
        when(collectionRepository.save(child)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(childId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateParent(
                childId,
                userId,
                new SetNoteCollectionParentRequest(parentId)
        );

        assertThat(child.getTargetCompletionDate()).isNull();
        assertThat(child.getCompanion()).isNull();
        assertThat(child.getCompanionStructureSnapshot()).isNull();
        assertThat(result.targetCompletionDate()).isNull();
        assertThat(result.companion()).isNull();
    }

    @Test
    void updateParent_clearsParentIdempotently() {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setParentCollectionId(parentId);
        when(collectionRepository.findByIdAndOwnerUserId(childId, userId)).thenReturn(Optional.of(child));
        when(collectionRepository.save(child)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(childId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateParent(
                childId,
                userId,
                new SetNoteCollectionParentRequest(null)
        );

        assertThat(child.getParentCollectionId()).isNull();
        assertThat(child.getSiblingPosition()).isNull();
        assertThat(result.parentCollectionId()).isNull();
    }

    @Test
    void updateParent_rejectsSelfParent() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> service.updateParent(
                collectionId,
                userId,
                new SetNoteCollectionParentRequest(collectionId)
        )).isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("A collection cannot be nested under itself.");
    }

    @Test
    void updateParent_rejectsParentThatIsNotTopLevel() {
        UUID userId = UUID.randomUUID();
        UUID grandParentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        NoteCollectionEntity parent = buildCollection(parentId, userId, "Child Goal", Instant.now());
        parent.setParentCollectionId(grandParentId);
        NoteCollectionEntity child = buildCollection(childId, userId, "Subject", Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(childId, userId)).thenReturn(Optional.of(child));
        when(collectionRepository.findByIdAndOwnerUserId(parentId, userId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.updateParent(
                childId,
                userId,
                new SetNoteCollectionParentRequest(parentId)
        )).isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("A collection can only be nested under a top-level goal.");
    }

    @Test
    void updateParent_rejectsChildThatAlreadyHasChildren() {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        NoteCollectionEntity parent = buildCollection(parentId, userId, "LET Mastery", Instant.now());
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(childId, userId)).thenReturn(Optional.of(child));
        when(collectionRepository.findByIdAndOwnerUserId(parentId, userId)).thenReturn(Optional.of(parent));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(parentId)).thenReturn(List.of());
        when(collectionRepository.countByParentCollectionId(childId)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateParent(
                childId,
                userId,
                new SetNoteCollectionParentRequest(parentId)
        )).isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("A collection with child plans cannot be nested under another goal.");
    }

    @Test
    void updateParent_rejectsParentOwnedByAnotherUserAsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(childId, userId)).thenReturn(Optional.of(child));
        when(collectionRepository.findByIdAndOwnerUserId(parentId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateParent(
                childId,
                userId,
                new SetNoteCollectionParentRequest(parentId)
        )).isInstanceOf(CollectionNotFoundException.class);
    }

    @Test
    void setPrimary_setsOwnedTopLevelCollection() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "LET Mastery", Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.setPrimary(collectionId, userId);

        assertThat(user.getPrimaryCollectionId()).isEqualTo(collectionId);
        verify(userRepository).save(user);
    }

    @Test
    void setPrimary_rejectsCollectionOwnedByAnotherUserAsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPrimary(collectionId, userId))
                .isInstanceOf(CollectionNotFoundException.class);
    }

    @Test
    void setPrimary_rejectsChildCollection() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "Professional Education", Instant.now());
        collection.setParentCollectionId(UUID.randomUUID());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> service.setPrimary(collectionId, userId))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Only a top-level Goal can be primary.");
    }

    @Test
    void setPrimary_isNoOpWhenCollectionIsAlreadyPrimary() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setPrimaryCollectionId(collectionId);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "LET Mastery", Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.setPrimary(collectionId, userId);

        assertThat(user.getPrimaryCollectionId()).isEqualTo(collectionId);
        verify(userRepository, never()).save(user);
    }

    @Test
    void clearPrimaryClearsExistingPrimaryAndIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setPrimaryCollectionId(collectionId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.clearPrimary(userId);
        service.clearPrimary(userId);

        assertThat(user.getPrimaryCollectionId()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void setCompanion_setsOwnedTopLevelCollectionForAdmin() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        CompanionContent content = companionContent();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "LET Mastery", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of());
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.setCompanion(collectionId, userId, content);

        assertThat(collection.getCompanion()).isEqualTo(content);
        assertThat(collection.getCompanionStructureSnapshot()).isEqualTo(new CompanionStructureSnapshot(0, List.of()));
        assertThat(result.companion()).isEqualTo(content);
        assertThat(result.companion().resources()).isEqualTo("- [Official guide](https://example.com/guide)");
    }

    @Test
    void setCompanion_capturesSortedChildSnapshotForGoalWithChildren() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstChildId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID secondChildId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        CompanionContent content = companionContent();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "LET Mastery", Instant.now());
        NoteCollectionEntity firstChild = buildCollection(firstChildId, userId, "Professional Education", Instant.now());
        NoteCollectionEntity secondChild = buildCollection(secondChildId, userId, "General Education", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of(firstChild, secondChild));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        service.setCompanion(collectionId, userId, content);

        assertThat(collection.getCompanionStructureSnapshot()).isEqualTo(new CompanionStructureSnapshot(
                2,
                List.of(secondChildId, firstChildId)
        ));
    }

    @Test
    void setCompanion_capturesSortedNoteSnapshotForChildlessTopLevelCollection() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstNoteId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID secondNoteId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "LET Mastery", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of());
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(List.of(
                        buildItem(collectionId, firstNoteId, 0, null),
                        buildItem(collectionId, secondNoteId, 1, null)
                ))
                .thenReturn(List.of());

        service.setCompanion(collectionId, userId, companionContent());

        assertThat(collection.getCompanionStructureSnapshot()).isEqualTo(new CompanionStructureSnapshot(
                2,
                List.of(secondNoteId, firstNoteId)
        ));
    }

    @Test
    void setCompanion_acceptsIncrementalEmptyContent() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        CompanionContent content = new CompanionContent(null, null, null, null, List.of(), List.of());
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "LET Mastery", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of());
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.setCompanion(collectionId, userId, content);

        assertThat(result.companion()).isEqualTo(content);
    }

    @Test
    void setCompanion_rejectsNonAdmin() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.setCompanion(collectionId, userId, companionContent()))
                .isInstanceOf(AccessDeniedException.class);

        verify(collectionRepository, never()).findByIdAndOwnerUserId(any(), any());
    }

    @Test
    void setCompanion_rejectsNullContent() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.setCompanion(collectionId, userId, null))
                .isInstanceOf(InvalidCollectionRequestException.class);

        verify(collectionRepository, never()).findByIdAndOwnerUserId(any(), any());
    }

    @Test
    void setCompanion_rejectsMentorTipNegativeThresholdBeforeLoadingCollection() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        CompanionContent content = new CompanionContent(
                null,
                null,
                null,
                null,
                List.of(),
                List.of(new CompanionMentorTip(
                        UUID.randomUUID(),
                        "Review your weak spots",
                        "Spend one block on concepts that still feel uncertain.",
                        CompanionMentorTipAction.REVIEW_DUE_CONCEPTS,
                        new CompanionMentorTipSurfacingCondition(
                                CompanionMentorTipSurfacingConditionType.DAYS_BEFORE_TARGET_DATE,
                                -1
                        )
                ))
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.setCompanion(collectionId, userId, content))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Mentor tip surfacing threshold must be zero or greater.");

        verify(collectionRepository, never()).findByIdAndOwnerUserId(any(), any());
    }

    @Test
    void setCompanion_rejectsCollectionOwnedByAnotherUserAsNotFoundForAdmin() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCompanion(collectionId, userId, companionContent()))
                .isInstanceOf(CollectionNotFoundException.class);
    }

    @Test
    void setCompanion_rejectsChildCollection() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "Professional Education", Instant.now());
        collection.setParentCollectionId(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> service.setCompanion(collectionId, userId, companionContent()))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Only a top-level Goal can have a Companion.");
    }

    @Test
    void clearCompanion_clearsExistingCompanionForAdmin() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "LET Mastery", Instant.now());
        collection.setCompanion(companionContent());
        collection.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.clearCompanion(collectionId, userId);

        assertThat(collection.getCompanion()).isNull();
        assertThat(collection.getCompanionStructureSnapshot()).isNull();
        assertThat(result.companion()).isNull();
    }

    @Test
    void clearCompanion_rejectsNonAdmin() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.clearCompanion(collectionId, userId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void generateCompanion_returnsOnlyRequestedSectionDraftForAdminGoalWithChildren() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "LET Mastery", Instant.now());
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setDescription("Teaching principles and assessment.");
        CompanionContent draft = new CompanionContent("Draft overview", null, null, null, List.of(), List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of(child));
        when(llmStudyPackService.generateCompanion(any(), eq(Set.of(CompanionSection.OVERVIEW))))
                .thenReturn(draft);

        GeneratedCompanionContentResponse result = service.generateCompanion(
                collectionId,
                userId,
                new GenerateCompanionRequest(List.of(CompanionSection.OVERVIEW))
        );

        assertThat(result.overview()).isEqualTo("Draft overview");
        assertThat(result.studyStrategy()).isNull();
        assertThat(result.commonMistakes()).isNull();
        assertThat(result.faq()).isEmpty();
        ArgumentCaptor<com.studysnap.backend.service.model.CompanionGenerationContext> contextCaptor =
                ArgumentCaptor.forClass(com.studysnap.backend.service.model.CompanionGenerationContext.class);
        verify(llmStudyPackService).generateCompanion(contextCaptor.capture(), eq(Set.of(CompanionSection.OVERVIEW)));
        assertThat(contextCaptor.getValue().subjectPlans())
                .extracting(com.studysnap.backend.service.model.CompanionGenerationContext.CompanionContextItem::title)
                .containsExactly("Professional Education");
        verify(collectionRepository, never()).save(any());
    }

    @Test
    void generateCompanion_returnsMentorTipDraftsWithCuratorControlledFieldsUnset() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "LET Mastery", Instant.now());
        CompanionContent draft = new CompanionContent(
                null,
                null,
                null,
                null,
                List.of(),
                List.of(new CompanionMentorTip(
                        null,
                        "Do one focused check-in",
                        "Open the next ready item and name the concept you most need to revisit.",
                        CompanionMentorTipAction.NONE,
                        null
                ))
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());
        when(llmStudyPackService.generateCompanion(any(), eq(Set.of(CompanionSection.MENTOR_TIPS))))
                .thenReturn(draft);

        GeneratedCompanionContentResponse result = service.generateCompanion(
                collectionId,
                userId,
                new GenerateCompanionRequest(List.of(CompanionSection.MENTOR_TIPS))
        );

        assertThat(result.mentorTips()).hasSize(1);
        assertThat(result.mentorTips().getFirst().linkedAction()).isEqualTo(CompanionMentorTipAction.NONE);
        assertThat(result.mentorTips().getFirst().surfacingCondition()).isNull();
        verify(collectionRepository, never()).save(any());
    }

    @Test
    void generateCompanion_usesOwnNoteTitlesAndSubjectsForChildlessTopLevelCollection() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteEntity firstNote = buildNote(firstNoteId, userId, NOTE_TITLE_ONE);
        NoteEntity secondNote = buildNote(secondNoteId, userId, NOTE_TITLE_TWO);
        CompanionContent draft = companionContent();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(
                buildItem(collectionId, firstNoteId, 0, null),
                buildItem(collectionId, secondNoteId, 1, null)
        ));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(List.of(firstNoteId, secondNoteId)))
                .thenReturn(asNoteProjections(List.of(secondNote, firstNote)));
        when(llmStudyPackService.generateCompanion(any(), eq(Set.of(
                CompanionSection.OVERVIEW,
                CompanionSection.STUDY_STRATEGY,
                CompanionSection.COMMON_MISTAKES,
                CompanionSection.FAQ
        )))).thenReturn(draft);

        GeneratedCompanionContentResponse result = service.generateCompanion(
                collectionId,
                userId,
                new GenerateCompanionRequest(List.of(
                        CompanionSection.OVERVIEW,
                        CompanionSection.STUDY_STRATEGY,
                        CompanionSection.COMMON_MISTAKES,
                        CompanionSection.FAQ
                ))
        );

        assertThat(result.overview()).isEqualTo("Overview");
        assertThat(result.faq()).hasSize(1);
        ArgumentCaptor<com.studysnap.backend.service.model.CompanionGenerationContext> contextCaptor =
                ArgumentCaptor.forClass(com.studysnap.backend.service.model.CompanionGenerationContext.class);
        verify(llmStudyPackService).generateCompanion(contextCaptor.capture(), any());
        assertThat(contextCaptor.getValue().notes())
                .extracting(com.studysnap.backend.service.model.CompanionGenerationContext.CompanionContextItem::title)
                .containsExactly(NOTE_TITLE_ONE, NOTE_TITLE_TWO);
        assertThat(contextCaptor.getValue().notes())
                .extracting(com.studysnap.backend.service.model.CompanionGenerationContext.CompanionContextItem::description)
                .containsExactly(BIOLOGY_SUBJECT, BIOLOGY_SUBJECT);
        verify(collectionRepository, never()).save(any());
    }

    @Test
    void generateCompanion_rejectsNonAdmin() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.generateCompanion(
                collectionId,
                userId,
                new GenerateCompanionRequest(List.of(CompanionSection.OVERVIEW))
        )).isInstanceOf(AccessDeniedException.class);

        verify(collectionRepository, never()).findByIdAndOwnerUserId(any(), any());
    }

    @Test
    void generateCompanion_rejectsChildCollection() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, "Professional Education", Instant.now());
        collection.setParentCollectionId(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> service.generateCompanion(
                collectionId,
                userId,
                new GenerateCompanionRequest(List.of(CompanionSection.OVERVIEW))
        ))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Only a top-level Goal can have a Companion.");

        verify(llmStudyPackService, never()).generateCompanion(any(), any());
    }

    @Test
    void create_autoSetsFirstTopLevelCollectionAsPrimary() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        NoteCollectionEntity[] savedCollection = new NoteCollectionEntity[1];
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> {
            savedCollection[0] = invocation.getArgument(0);
            return savedCollection[0];
        });
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.countByOwnerUserIdAndParentCollectionIdIsNull(userId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenAnswer(invocation -> List.of(savedCollection[0]));

        NoteCollectionDetailResponse result = service.create(userId, new CreateNoteCollectionRequest(COLLECTION_TITLE, null, null));

        assertThat(user.getPrimaryCollectionId()).isEqualTo(result.id());
    }

    @Test
    void create_doesNotOverwriteExistingPrimaryWhenSecondGoalIsCreated() {
        UUID userId = UUID.randomUUID();
        UUID existingPrimaryId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setPrimaryCollectionId(existingPrimaryId);
        NoteCollectionEntity existingPrimary = buildCollection(existingPrimaryId, userId, "Existing Goal", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(existingPrimaryId, userId)).thenReturn(Optional.of(existingPrimary));
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(userId, new CreateNoteCollectionRequest(COLLECTION_TITLE, null, null));

        assertThat(user.getPrimaryCollectionId()).isEqualTo(existingPrimaryId);
    }

    @Test
    void deletePrimaryAutoSetsOnlyRemainingTopLevelCollection() {
        UUID userId = UUID.randomUUID();
        UUID primaryId = UUID.randomUUID();
        UUID remainingId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setPrimaryCollectionId(primaryId);
        NoteCollectionEntity primary = buildCollection(primaryId, userId, "Primary Goal", Instant.now());
        NoteCollectionEntity remaining = buildCollection(remainingId, userId, "Remaining Goal", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(primaryId, userId))
                .thenReturn(Optional.of(primary))
                .thenReturn(Optional.empty());
        when(collectionRepository.countByOwnerUserIdAndParentCollectionIdIsNull(userId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(remaining));

        service.delete(primaryId, userId);

        assertThat(user.getPrimaryCollectionId()).isEqualTo(remainingId);
    }

    @Test
    void deleteNonPrimaryKeepsPrimaryUnchanged() {
        UUID userId = UUID.randomUUID();
        UUID primaryId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setPrimaryCollectionId(primaryId);
        NoteCollectionEntity primary = buildCollection(primaryId, userId, "Primary Goal", Instant.now());
        NoteCollectionEntity deleted = buildCollection(deletedId, userId, "Other Goal", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(deletedId, userId)).thenReturn(Optional.of(deleted));
        when(collectionRepository.findByIdAndOwnerUserId(primaryId, userId)).thenReturn(Optional.of(primary));

        service.delete(deletedId, userId);

        assertThat(user.getPrimaryCollectionId()).isEqualTo(primaryId);
    }

    @Test
    void updateParentClearsPrimaryWhenPrimaryBecomesChildWithMultipleTopLevelGoalsRemaining() {
        UUID userId = UUID.randomUUID();
        UUID primaryId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setPrimaryCollectionId(primaryId);
        NoteCollectionEntity primary = buildCollection(primaryId, userId, "Primary Goal", Instant.now());
        NoteCollectionEntity parent = buildCollection(parentId, userId, "Parent Goal", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(primaryId, userId))
                .thenReturn(Optional.of(primary))
                .thenReturn(Optional.of(primary));
        when(collectionRepository.findByIdAndOwnerUserId(parentId, userId)).thenReturn(Optional.of(parent));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(parentId)).thenReturn(List.of());
        when(collectionRepository.countByParentCollectionId(primaryId)).thenReturn(0L);
        when(collectionRepository.findMaxSiblingPosition(parentId, userId)).thenReturn(-1);
        when(collectionRepository.save(primary)).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.countByOwnerUserIdAndParentCollectionIdIsNull(userId)).thenReturn(2L);
        when(itemRepository.findByCollectionIdOrderByPositionAsc(primaryId)).thenReturn(List.of());

        service.updateParent(primaryId, userId, new SetNoteCollectionParentRequest(parentId));

        assertThat(user.getPrimaryCollectionId()).isNull();
    }

    @Test
    void updateParentAutoSetsDetachedChildWhenItIsOnlyTopLevelCollection() {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setParentCollectionId(parentId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(childId, userId)).thenReturn(Optional.of(child));
        when(collectionRepository.save(child)).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.countByOwnerUserIdAndParentCollectionIdIsNull(userId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(child));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(childId)).thenReturn(List.of());

        service.updateParent(childId, userId, new SetNoteCollectionParentRequest(null));

        assertThat(user.getPrimaryCollectionId()).isEqualTo(childId);
    }

    @Test
    void getGoal_rollsUpChildReadinessBySummedCountsWithoutMergedConceptDeduplication() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID firstChildId = UUID.randomUUID();
        UUID secondChildId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        NoteCollectionEntity firstChild = buildCollection(firstChildId, userId, "Professional Education", Instant.now());
        NoteCollectionEntity secondChild = buildCollection(secondChildId, userId, "General Education", Instant.now());
        firstChild.setParentCollectionId(goalId);
        secondChild.setParentCollectionId(goalId);
        StudyPackEntity firstPack = buildStudyPack(firstNoteId, List.of("Assessment", "Rubrics"));
        firstPack.setOwnerUserId(userId);
        StudyPackEntity secondPack = buildStudyPack(secondNoteId, List.of("Assessment", "Foundations"));
        secondPack.setOwnerUserId(userId);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(firstChild, secondChild));
        when(itemRepository.countItemsByCollectionIds(List.of(firstChildId, secondChildId))).thenReturn(List.of(
                countProjection(firstChildId, 1),
                countProjection(secondChildId, 1)
        ));
        when(itemRepository.findNoteIdsByCollectionIds(List.of(firstChildId, secondChildId))).thenReturn(List.of(
                noteProjection(firstChildId, firstNoteId),
                noteProjection(secondChildId, secondNoteId)
        ));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(firstNoteId, secondNoteId)))
                .thenReturn(asProjections(firstPack, secondPack));
        when(progressReportService.buildSubjectProgressEntriesByGroup(
                anyMap(),
                eq(userId),
                any(OffsetDateTime.class)
        )).thenReturn(Map.of(
                firstChildId, new ProgressReportService.SubjectProgressBatchResult(
                        List.of(new SubjectProgressEntry("Professional Education", 2, 1, 1, 0, 50)),
                        null
                ),
                secondChildId, new ProgressReportService.SubjectProgressBatchResult(
                        List.of(new SubjectProgressEntry("General Education", 2, 1, 0, 1, 50)),
                        null
                )
        ));
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.childCount()).isEqualTo(2);
        assertThat(result.totalConcepts()).isEqualTo(4);
        assertThat(result.masteredConcepts()).isEqualTo(2);
        assertThat(result.overallReadinessPercentage()).isEqualTo(50);
        assertThat(result.children()).extracting(GoalCollectionChildResponse::totalConcepts)
                .containsExactly(2, 2);
        verify(itemRepository, never()).findByCollectionIdOrderByPositionAsc(goalId);
    }

    @Test
    void getGoal_usesDirectItemReadinessForChildlessCollection() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        StudyPackEntity studyPack = buildStudyPack(noteId, List.of("Cell", "Mitosis"));
        studyPack.setOwnerUserId(userId);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(List.of(buildItem(collectionId, noteId, 0, null)));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(asProjections(studyPack));
        when(progressReportService.buildSubjectProgressEntries(
                anyList(),
                eq(userId),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(new SubjectProgressEntry("Biology", 10, 4, 2, 4, 40)));
        when(itemRepository.countByCollectionId(collectionId)).thenReturn(1L);

        GoalCollectionDetailResponse result = service.getGoal(collectionId, userId);
        PlanReadinessResponse directItemReadiness = service.getReadiness(collectionId, userId);

        assertThat(result.childCount()).isZero();
        assertThat(result.totalConcepts()).isEqualTo(10);
        assertThat(result.masteredConcepts()).isEqualTo(4);
        assertThat(result.dueConcepts()).isEqualTo(2);
        assertThat(result.notPracticedConcepts()).isEqualTo(4);
        assertThat(result.overallReadinessPercentage()).isEqualTo(40);
        assertThat(result.totalConcepts()).isEqualTo(directItemReadiness.totalConcepts());
        assertThat(result.masteredConcepts()).isEqualTo(directItemReadiness.masteredConcepts());
        assertThat(result.dueConcepts()).isEqualTo(directItemReadiness.dueConcepts());
        assertThat(result.notPracticedConcepts()).isEqualTo(directItemReadiness.notPracticedConcepts());
        assertThat(result.overallReadinessPercentage()).isEqualTo(directItemReadiness.overallReadinessPercentage());
    }

    @Test
    void getGoal_returnsZeroReadinessForChildlessItemsWithoutStudyPacks() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(List.of(buildItem(collectionId, noteId, 0, null)));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(List.of());
        when(itemRepository.countByCollectionId(collectionId)).thenReturn(1L);

        GoalCollectionDetailResponse result = service.getGoal(collectionId, userId);

        assertThat(result.totalConcepts()).isZero();
        assertThat(result.masteredConcepts()).isZero();
        assertThat(result.dueConcepts()).isZero();
        assertThat(result.notPracticedConcepts()).isZero();
        assertThat(result.overallReadinessPercentage()).isZero();
        verify(progressReportService, never()).buildSubjectProgressEntries(anyList(), any(), any());
    }

    @Test
    void getGoal_returnsZeroShapeForEmptyGoal() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(companionContent());
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of());
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.companion()).isEqualTo(companionContent());
        assertThat(result.childCount()).isZero();
        assertThat(result.totalConcepts()).isZero();
        assertThat(result.overallReadinessPercentage()).isZero();
        assertThat(result.children()).isEmpty();
    }

    @Test
    void getGoal_returnsCompanionOutdatedFalseImmediatelyAfterMatchingSnapshotForAdmin() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(companionContent());
        goal.setCompanionStructureSnapshot(new CompanionStructureSnapshot(1, List.of(noteId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());
        when(itemRepository.countByCollectionId(goalId)).thenReturn(1L);
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId))
                .thenReturn(List.of(buildItem(goalId, noteId, 0, null)));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.companionMayBeOutdated()).isFalse();
    }

    @Test
    void getGoal_keepsCompanionOutdatedFalseWhenOnlyMentorTipsChange() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(new CompanionContent(
                "Overview",
                "Study strategy",
                "Common mistakes",
                null,
                List.of(),
                List.of(new CompanionMentorTip(
                        UUID.randomUUID(),
                        "Updated tip",
                        "Use this updated tip text without changing the structure snapshot.",
                        CompanionMentorTipAction.TERMINAL_ACTION,
                        null
                ))
        ));
        goal.setCompanionStructureSnapshot(new CompanionStructureSnapshot(1, List.of(noteId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());
        when(itemRepository.countByCollectionId(goalId)).thenReturn(1L);
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId))
                .thenReturn(List.of(buildItem(goalId, noteId, 0, null)));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.companionMayBeOutdated()).isFalse();
    }

    @Test
    void getGoal_returnsCompanionOutdatedTrueForAdminWhenNoteMembershipChanges() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(companionContent());
        goal.setCompanionStructureSnapshot(new CompanionStructureSnapshot(1, List.of(firstNoteId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());
        when(itemRepository.countByCollectionId(goalId)).thenReturn(2L);
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of(
                buildItem(goalId, firstNoteId, 0, null),
                buildItem(goalId, secondNoteId, 1, null)
        ));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.companionMayBeOutdated()).isTrue();
    }

    @Test
    void getGoal_returnsCompanionOutdatedTrueForAdminWhenChildMembershipChanges() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID firstChildId = UUID.randomUUID();
        UUID secondChildId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setRole(UserRole.ADMIN);
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(companionContent());
        goal.setCompanionStructureSnapshot(new CompanionStructureSnapshot(1, List.of(firstChildId)));
        NoteCollectionEntity firstChild = buildCollection(firstChildId, userId, "Professional Education", Instant.now());
        NoteCollectionEntity secondChild = buildCollection(secondChildId, userId, "General Education", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(firstChild, secondChild));
        when(itemRepository.countItemsByCollectionIds(List.of(firstChildId, secondChildId))).thenReturn(List.of());
        when(itemRepository.findNoteIdsByCollectionIds(List.of(firstChildId, secondChildId))).thenReturn(List.of());
        when(progressReportService.buildSubjectProgressEntriesByGroup(
                anyMap(),
                eq(userId),
                any(OffsetDateTime.class)
        )).thenReturn(Map.of());
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.companionMayBeOutdated()).isTrue();
        verify(itemRepository, never()).findByCollectionIdOrderByPositionAsc(goalId);
    }

    @Test
    void getGoal_returnsCompanionOutdatedFalseForNonAdminEvenWhenStructureChanged() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(companionContent());
        goal.setCompanionStructureSnapshot(new CompanionStructureSnapshot(1, List.of(firstNoteId)));
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());
        when(itemRepository.countByCollectionId(goalId)).thenReturn(2L);

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.companionMayBeOutdated()).isFalse();
    }

    @Test
    void getGoal_returnsCompanionOutdatedFalseWhenSnapshotIsUnknown() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(companionContent());
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.companionMayBeOutdated()).isFalse();
    }

    @Test
    void getGoal_reflectsTargetCompletionDateSetViaUpdateMetadata() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setTargetCompletionDate(LocalDate.parse("2026-12-01"));
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.targetCompletionDate()).isEqualTo(LocalDate.parse("2026-12-01"));
        assertThat(result.weeklyFocusByDay()).isEmpty();
    }

    @Test
    void getGoal_returnsNullCountdownFieldsWhenNoTargetDateIsSet() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.weeksRemaining()).isNull();
        assertThat(result.conceptsRemaining()).isNull();
        assertThat(result.todaysConceptBudget()).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getGoal_computesWeeklyCountdownWithDueConceptsAsFloor() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setTargetCompletionDate(LocalDate.now().plusDays(14));
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setParentCollectionId(goalId);
        StudyPackEntity pack = buildStudyPack(noteId, List.of("Assessment"));
        pack.setOwnerUserId(userId);
        UserEntity user = buildUser(userId);
        user.setStudyDaysPerWeek(7);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(child));
        when(itemRepository.countItemsByCollectionIds(List.of(childId))).thenReturn(List.of(countProjection(childId, 1)));
        when(itemRepository.findNoteIdsByCollectionIds(List.of(childId))).thenReturn(List.of(noteProjection(childId, noteId)));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(asProjections(pack));
        when(progressReportService.buildSubjectProgressEntriesByGroup(anyMap(), eq(userId), any(OffsetDateTime.class)))
                .thenReturn(Map.of(
                        childId, new ProgressReportService.SubjectProgressBatchResult(
                                List.of(new SubjectProgressEntry("Professional Education", 100, 20, 5, 30, 20)),
                                null
                        )
                ));
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.weeksRemaining()).isEqualTo(2);
        assertThat(result.conceptsRemaining()).isEqualTo(80);
        assertThat(result.todaysConceptBudget()).isEqualTo(8);
    }

    @Test
    void getGoal_defaultsToSevenDaysPerWeekWhenStudyIntensityNotSet() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setTargetCompletionDate(LocalDate.now().plusDays(7));
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setParentCollectionId(goalId);
        StudyPackEntity pack = buildStudyPack(noteId, List.of("Assessment"));
        pack.setOwnerUserId(userId);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(child));
        when(itemRepository.countItemsByCollectionIds(List.of(childId))).thenReturn(List.of(countProjection(childId, 1)));
        when(itemRepository.findNoteIdsByCollectionIds(List.of(childId))).thenReturn(List.of(noteProjection(childId, noteId)));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(asProjections(pack));
        when(progressReportService.buildSubjectProgressEntriesByGroup(anyMap(), eq(userId), any(OffsetDateTime.class)))
                .thenReturn(Map.of(
                        childId, new ProgressReportService.SubjectProgressBatchResult(
                                List.of(new SubjectProgressEntry("Professional Education", 7, 0, 0, 7, 0)),
                                null
                        )
                ));
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);
        // userRepository.findById returns a fresh buildUser(userId) via the lenient stub in setUp(),
        // which leaves studyDaysPerWeek null — asserting the default-to-7 fallback applies.

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.weeksRemaining()).isEqualTo(1);
        assertThat(result.todaysConceptBudget()).isEqualTo(1);
    }

    @Test
    void getGoal_countdownFloorsRemainingScheduledDaysAtOneWhenTargetDateIsOverdue() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setTargetCompletionDate(LocalDate.now().minusDays(3));
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setParentCollectionId(goalId);
        StudyPackEntity pack = buildStudyPack(noteId, List.of("Assessment"));
        pack.setOwnerUserId(userId);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(child));
        when(itemRepository.countItemsByCollectionIds(List.of(childId))).thenReturn(List.of(countProjection(childId, 1)));
        when(itemRepository.findNoteIdsByCollectionIds(List.of(childId))).thenReturn(List.of(noteProjection(childId, noteId)));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(asProjections(pack));
        when(progressReportService.buildSubjectProgressEntriesByGroup(anyMap(), eq(userId), any(OffsetDateTime.class)))
                .thenReturn(Map.of(
                        childId, new ProgressReportService.SubjectProgressBatchResult(
                                List.of(new SubjectProgressEntry("Professional Education", 12, 0, 2, 10, 0)),
                                null
                        )
                ));
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.weeksRemaining()).isZero();
        assertThat(result.todaysConceptBudget()).isEqualTo(12);
    }

    @Test
    void getGoal_allocatesTodaysConceptBudgetAcrossChildrenWithLargestRemainder() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        ChildReadiness first = new ChildReadiness(UUID.randomUUID(), "Professional Education", 10, 1, 4, 15);
        ChildReadiness second = new ChildReadiness(UUID.randomUUID(), "General Education", 20, 2, 8, 30);
        ChildReadiness third = new ChildReadiness(UUID.randomUUID(), "Specialization", 5, 0, 2, 7);
        UserEntity user = buildUser(userId);
        user.setStudyDaysPerWeek(7);
        stubGoalReadiness(userId, goalId, LocalDate.now().plusDays(7), List.of(first, second, third));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.todaysConceptBudget()).isEqualTo(5);
        assertThat(result.children()).extracting(GoalCollectionChildResponse::todaysConceptBudget)
                .containsExactly(2, 3, 0);
        assertThat(result.children().stream()
                .map(GoalCollectionChildResponse::todaysConceptBudget)
                .mapToInt(Integer::intValue)
                .sum()).isEqualTo(result.todaysConceptBudget());
    }

    @Test
    void getGoal_breaksLargestRemainderTiesByExistingChildOrder() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        ChildReadiness first = new ChildReadiness(UUID.randomUUID(), "First", 0, 0, 1, 1);
        ChildReadiness second = new ChildReadiness(UUID.randomUUID(), "Second", 0, 0, 1, 1);
        UserEntity user = buildUser(userId);
        user.setStudyDaysPerWeek(7);
        stubGoalReadiness(userId, goalId, LocalDate.now().plusDays(14), List.of(first, second));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.todaysConceptBudget()).isEqualTo(1);
        assertThat(result.children()).extracting(GoalCollectionChildResponse::todaysConceptBudget)
                .containsExactly(1, 0);
    }

    @Test
    void getGoal_setsChildBudgetToDueOnlyWhenNoNewConceptPoolExists() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        ChildReadiness first = new ChildReadiness(UUID.randomUUID(), "First", 2, 3, 0, 5);
        ChildReadiness second = new ChildReadiness(UUID.randomUUID(), "Second", 4, 1, 0, 5);
        stubGoalReadiness(userId, goalId, LocalDate.now().plusDays(7), List.of(first, second));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.todaysConceptBudget()).isEqualTo(4);
        assertThat(result.children()).extracting(GoalCollectionChildResponse::todaysConceptBudget)
                .containsExactly(3, 1);
    }

    @Test
    void getGoal_nullGatesChildBudgetsAndWeeklyFocusWhenNoTargetDateIsSet() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        ChildReadiness child = new ChildReadiness(UUID.randomUUID(), "Professional Education", 0, 1, 4, 5);
        stubGoalReadiness(userId, goalId, null, List.of(child));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.todaysConceptBudget()).isNull();
        assertThat(result.children()).extracting(GoalCollectionChildResponse::todaysConceptBudget)
                .containsExactly((Integer) null);
        assertThat(result.weeklyFocusByDay()).isEmpty();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getGoal_buildsWeeklyFocusDaysForFiveStudyDaysPerWeek() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        ChildReadiness first = new ChildReadiness(UUID.randomUUID(), "First", 0, 0, 4, 4);
        ChildReadiness second = new ChildReadiness(UUID.randomUUID(), "Second", 0, 0, 4, 4);
        UserEntity user = buildUser(userId);
        user.setStudyDaysPerWeek(5);
        stubGoalReadiness(userId, goalId, LocalDate.now().plusDays(14), List.of(first, second));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.weeklyFocusByDay()).extracting("dayOfWeek")
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        assertThat(result.weeklyFocusByDay()).extracting("collectionIds")
                .containsExactly(List.of(first.collectionId()), List.of(second.collectionId()));
    }

    @Test
    void getGoal_groupsMoreChildrenThanStudyDaysOnTheSameWeekday() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        ChildReadiness first = new ChildReadiness(UUID.randomUUID(), "First", 0, 0, 1, 1);
        ChildReadiness second = new ChildReadiness(UUID.randomUUID(), "Second", 0, 0, 1, 1);
        ChildReadiness third = new ChildReadiness(UUID.randomUUID(), "Third", 0, 0, 1, 1);
        UserEntity user = buildUser(userId);
        user.setStudyDaysPerWeek(2);
        stubGoalReadiness(userId, goalId, LocalDate.now().plusDays(7), List.of(first, second, third));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.weeklyFocusByDay()).extracting("dayOfWeek")
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
        assertThat(result.weeklyFocusByDay()).extracting("collectionIds")
                .containsExactly(
                        List.of(first.collectionId(), third.collectionId()),
                        List.of(second.collectionId())
                );
    }

    @Test
    void getGoal_returnsOnlyAssignedWeeklyFocusDaysWhenChildrenAreFewerThanStudyDays() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        ChildReadiness child = new ChildReadiness(UUID.randomUUID(), "Only Child", 0, 0, 3, 3);
        UserEntity user = buildUser(userId);
        user.setStudyDaysPerWeek(7);
        stubGoalReadiness(userId, goalId, LocalDate.now().plusDays(7), List.of(child));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.weeklyFocusByDay()).hasSize(1);
        assertThat(result.weeklyFocusByDay().getFirst().dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(result.weeklyFocusByDay().getFirst().collectionIds()).containsExactly(child.collectionId());
    }

    @Test
    void getGoal_usesDefaultSevenDaysForWeeklyFocusWhenStudyIntensityIsUnset() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        ChildReadiness child = new ChildReadiness(UUID.randomUUID(), "Only Child", 0, 0, 7, 7);
        stubGoalReadiness(userId, goalId, LocalDate.now().plusDays(7), List.of(child));

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.todaysConceptBudget()).isEqualTo(1);
        assertThat(result.weeklyFocusByDay()).hasSize(1);
        assertThat(result.weeklyFocusByDay().getFirst().dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(result.weeklyFocusByDay().getFirst().collectionIds()).containsExactly(child.collectionId());
    }

    @Test
    void getGoal_usesExpectedStudyWeekdayFormulaForOneThreeFiveAndSevenDays() {
        UUID userId = UUID.randomUUID();

        assertThat(studyDaysForGoal(userId, 1)).containsExactly(DayOfWeek.MONDAY);
        assertThat(studyDaysForGoal(userId, 3)).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        assertThat(studyDaysForGoal(userId, 5)).containsExactly(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
        );
        assertThat(studyDaysForGoal(userId, 7)).containsExactly(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
        );
    }

    @Test
    void getGoal_returnsChildrenInSiblingPositionOrderFromRepository() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID firstChildId = UUID.randomUUID();
        UUID secondChildId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        NoteCollectionEntity firstChild = buildCollection(firstChildId, userId, "First", Instant.now());
        NoteCollectionEntity secondChild = buildCollection(secondChildId, userId, "Second", Instant.now());
        firstChild.setParentCollectionId(goalId);
        firstChild.setSiblingPosition(0);
        secondChild.setParentCollectionId(goalId);
        secondChild.setSiblingPosition(1);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(firstChild, secondChild));
        when(itemRepository.countItemsByCollectionIds(List.of(firstChildId, secondChildId))).thenReturn(List.of());
        when(itemRepository.findNoteIdsByCollectionIds(List.of(firstChildId, secondChildId))).thenReturn(List.of());
        when(progressReportService.buildSubjectProgressEntriesByGroup(anyMap(), eq(userId), any(OffsetDateTime.class)))
                .thenReturn(Map.of(
                        firstChildId, new ProgressReportService.SubjectProgressBatchResult(List.of(), null),
                        secondChildId, new ProgressReportService.SubjectProgressBatchResult(List.of(), null)
                ));
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.children()).extracting(GoalCollectionChildResponse::collectionId)
                .containsExactly(firstChildId, secondChildId);
    }

    @Test
    void setChildrenOrder_reassignsSiblingPositionsAndReturnsGoal() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID firstChildId = UUID.randomUUID();
        UUID secondChildId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        NoteCollectionEntity firstChild = buildCollection(firstChildId, userId, "First", Instant.now());
        NoteCollectionEntity secondChild = buildCollection(secondChildId, userId, "Second", Instant.now());
        firstChild.setParentCollectionId(goalId);
        firstChild.setSiblingPosition(0);
        secondChild.setParentCollectionId(goalId);
        secondChild.setSiblingPosition(1);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(firstChild, secondChild))
                .thenReturn(List.of(secondChild, firstChild));
        when(collectionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.save(goal)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.countItemsByCollectionIds(List.of(secondChildId, firstChildId))).thenReturn(List.of());
        when(itemRepository.findNoteIdsByCollectionIds(List.of(secondChildId, firstChildId))).thenReturn(List.of());
        when(progressReportService.buildSubjectProgressEntriesByGroup(anyMap(), eq(userId), any(OffsetDateTime.class)))
                .thenReturn(Map.of(
                        secondChildId, new ProgressReportService.SubjectProgressBatchResult(List.of(), null),
                        firstChildId, new ProgressReportService.SubjectProgressBatchResult(List.of(), null)
                ));
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);

        GoalCollectionDetailResponse result = service.setChildrenOrder(
                goalId,
                userId,
                new SetNoteCollectionChildrenOrderRequest(List.of(secondChildId, firstChildId))
        );

        assertThat(secondChild.getSiblingPosition()).isZero();
        assertThat(firstChild.getSiblingPosition()).isEqualTo(1);
        assertThat(result.children()).extracting(GoalCollectionChildResponse::collectionId)
                .containsExactly(secondChildId, firstChildId);
    }

    @Test
    void setChildrenOrder_rejectsForeignOrMissingChildIds() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID foreignChildId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        NoteCollectionEntity child = buildCollection(childId, userId, "First", Instant.now());
        child.setParentCollectionId(goalId);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(child));

        assertThatThrownBy(() -> service.setChildrenOrder(
                goalId,
                userId,
                new SetNoteCollectionChildrenOrderRequest(List.of(childId, foreignChildId))
        )).isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Child order must include exactly the current child plans.");
        verify(collectionRepository, never()).saveAll(anyList());
    }

    @Test
    void get_degradesPracticeSignalFailureToNotPracticed() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity item = buildItem(collectionId, noteId, 0, null);
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(item));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(List.of(noteId))).thenReturn(asNoteProjections(note));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(List.of());
        when(generatedQuizRepository.findNoteIdsByOwnerUserIdAndNoteIdIn(userId, List.of(noteId))).thenReturn(List.of());
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, List.of(noteId)))
                .thenThrow(new IllegalStateException("session history unavailable"));

        NoteCollectionDetailResponse result = service.get(collectionId, userId);

        assertThat(result.progress().notesPracticed()).isZero();
        assertThat(result.items().getFirst().lastSessionCompletedAt()).isNull();
    }

    @Test
    void get_entitledUserReturnsOrderedCappedDueConceptsFromOneBatchCall() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID dueNoteId = UUID.randomUUID();
        UUID currentNoteId = UUID.randomUUID();
        UUID draftNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        List<UUID> noteIds = List.of(dueNoteId, currentNoteId, draftNoteId);
        List<NoteCollectionItemEntity> items = List.of(
                buildItem(collectionId, dueNoteId, 0, null),
                buildItem(collectionId, currentNoteId, 1, null),
                buildItem(collectionId, draftNoteId, 2, null)
        );
        List<NoteEntity> notes = List.of(
                buildNote(dueNoteId, userId, NOTE_TITLE_ONE),
                buildNote(currentNoteId, userId, NOTE_TITLE_TWO),
                buildNote(draftNoteId, userId, NOTE_TITLE_THREE)
        );
        StudyPackEntity dueStudyPack = buildStudyPack(
                dueNoteId,
                List.of(OLDEST_CONCEPT, OLDER_CONCEPT, NEVER_SEEN_CONCEPT, NEWEST_DUE_CONCEPT, CURRENT_CONCEPT)
        );
        StudyPackEntity currentStudyPack = buildStudyPack(currentNoteId, List.of(CURRENT_CONCEPT));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(items);
        when(noteRepository.findCollectionNoteProjectionsByIdIn(noteIds)).thenReturn(asNoteProjections(notes));
        when(studyPackRepository.findProgressViewsByNoteIdIn(noteIds)).thenReturn(asProjections(dueStudyPack, currentStudyPack));
        when(generatedQuizRepository.findNoteIdsByOwnerUserIdAndNoteIdIn(userId, noteIds)).thenReturn(List.of());
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, noteIds)).thenReturn(Map.of());
        when(conceptHealthService.canViewConceptHealth(userId)).thenReturn(true);
        when(conceptHealthService.getDueConceptsByStudyPackIds(eq(userId), anyMap(), any(OffsetDateTime.class)))
                .thenReturn(Map.of(
                        dueStudyPack.getId(), List.of(
                                OLDEST_CONCEPT,
                                OLDER_CONCEPT,
                                NEVER_SEEN_CONCEPT,
                                NEWEST_DUE_CONCEPT
                        ),
                        currentStudyPack.getId(), List.of()
                ));

        NoteCollectionDetailResponse result = service.get(collectionId, userId);

        assertThat(result.items().get(0).dueConceptCount()).isEqualTo(4);
        assertThat(result.items().get(0).dueConcepts())
                .containsExactly(OLDEST_CONCEPT, OLDER_CONCEPT, NEVER_SEEN_CONCEPT);
        assertThat(result.items().get(1).dueConceptCount()).isZero();
        assertThat(result.items().get(1).dueConcepts()).isEmpty();
        assertThat(result.items().get(2).dueConceptCount()).isZero();
        assertThat(result.items().get(2).dueConcepts()).isEmpty();
        verify(conceptHealthService, times(1))
                .getDueConceptsByStudyPackIds(eq(userId), anyMap(), any(OffsetDateTime.class));
    }

    @Test
    void get_nonEntitledUserDoesNotLoadOrReturnDueConcepts() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity item = buildItem(collectionId, noteId, 0, null);
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        StudyPackEntity studyPack = buildStudyPack(noteId, List.of("Underlying Due Concept"));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(item));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(List.of(noteId))).thenReturn(asNoteProjections(note));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(asProjections(studyPack));
        when(generatedQuizRepository.findNoteIdsByOwnerUserIdAndNoteIdIn(userId, List.of(noteId))).thenReturn(List.of());
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, List.of(noteId))).thenReturn(Map.of());
        when(conceptHealthService.canViewConceptHealth(userId)).thenReturn(false);

        NoteCollectionDetailResponse result = service.get(collectionId, userId);

        assertThat(result.items().getFirst().dueConceptCount()).isZero();
        assertThat(result.items().getFirst().dueConcepts()).isEmpty();
        verify(conceptHealthService, never()).getDueConceptsByStudyPackIds(any(), anyMap(), any());
    }

    @Test
    void get_degradesConceptHealthFailureToEmptyDueConcepts() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity item = buildItem(collectionId, noteId, 0, null);
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        StudyPackEntity studyPack = buildStudyPack(noteId, List.of("Due Concept"));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(item));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(List.of(noteId))).thenReturn(asNoteProjections(note));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(asProjections(studyPack));
        when(generatedQuizRepository.findNoteIdsByOwnerUserIdAndNoteIdIn(userId, List.of(noteId))).thenReturn(List.of());
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, List.of(noteId))).thenReturn(Map.of());
        when(conceptHealthService.canViewConceptHealth(userId)).thenReturn(true);
        when(conceptHealthService.getDueConceptsByStudyPackIds(eq(userId), anyMap(), any(OffsetDateTime.class)))
                .thenThrow(new IllegalStateException("concept health unavailable"));

        NoteCollectionDetailResponse result = service.get(collectionId, userId);

        assertThat(result.items().getFirst().dueConceptCount()).isZero();
        assertThat(result.items().getFirst().dueConcepts()).isEmpty();
    }

    @Test
    void updateMetadata_changesTitleDescriptionAndBumpsUpdatedAt() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        Instant previousUpdatedAt = Instant.parse(BASE_TIMESTAMP);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, previousUpdatedAt);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateMetadata(collectionId, userId, new UpdateNoteCollectionRequest(
                UPDATED_COLLECTION_TITLE,
                UPDATED_COLLECTION_DESCRIPTION,
                UPDATED_COURSE_PROGRAM,
                3,
                null
        ));

        assertThat(result.title()).isEqualTo(UPDATED_COLLECTION_TITLE);
        assertThat(result.description()).isEqualTo(UPDATED_COLLECTION_DESCRIPTION);
        assertThat(result.courseProgram()).isEqualTo(UPDATED_COURSE_PROGRAM);
        assertThat(result.estimatedStudyHours()).isEqualTo(3);
        assertThat(result.updatedAt()).isAfter(previousUpdatedAt);
        verify(analyticsService, never()).trackEvent(any(), eq(AnalyticsEventType.COLLECTION_CREATED), any(), any());
    }

    @Test
    void updateMetadata_setsAndClearsLearnerLevel() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse setResult = service.updateMetadata(collectionId, userId,
                new UpdateNoteCollectionRequest(null, null, null, null, null, LearnerLevel.COLLEGE.name()));
        NoteCollectionDetailResponse clearResult = service.updateMetadata(collectionId, userId,
                new UpdateNoteCollectionRequest(null, null, null, null, null, ""));

        assertThat(setResult.learnerLevel()).isEqualTo(LearnerLevel.COLLEGE.name());
        assertThat(clearResult.learnerLevel()).isNull();
        assertThat(collection.getLearnerLevel()).isNull();
    }

    @Test
    void resolveInheritedLearnerLevel_usesNearestValueAndHasNoDefault() {
        UUID goalId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID leafId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, UUID.randomUUID(), "Goal", Instant.now());
        goal.setLearnerLevel(LearnerLevel.BOARD_EXAM_REVIEW);
        NoteCollectionEntity parent = buildCollection(parentId, goal.getOwnerUserId(), "Parent", Instant.now());
        parent.setParentCollectionId(goalId);
        parent.setLearnerLevel(LearnerLevel.COLLEGE);
        NoteCollectionEntity leaf = buildCollection(leafId, goal.getOwnerUserId(), "Leaf", Instant.now());
        leaf.setParentCollectionId(parentId);
        when(collectionRepository.findById(leafId)).thenReturn(Optional.of(leaf));
        when(collectionRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(collectionRepository.findById(goalId)).thenReturn(Optional.of(goal));

        assertThat(service.resolveInheritedLearnerLevel(leafId)).contains(LearnerLevel.COLLEGE);

        parent.setLearnerLevel(null);
        assertThat(service.resolveInheritedLearnerLevel(leafId)).contains(LearnerLevel.BOARD_EXAM_REVIEW);

        goal.setLearnerLevel(null);
        assertThat(service.resolveInheritedLearnerLevel(leafId)).isEmpty();
    }

    @Test
    void resolveInheritedLearnerLevel_terminatesForCycle() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        NoteCollectionEntity first = buildCollection(firstId, UUID.randomUUID(), "First", Instant.now());
        NoteCollectionEntity second = buildCollection(secondId, first.getOwnerUserId(), "Second", Instant.now());
        first.setParentCollectionId(secondId);
        second.setParentCollectionId(firstId);
        when(collectionRepository.findById(firstId)).thenReturn(Optional.of(first));
        when(collectionRepository.findById(secondId)).thenReturn(Optional.of(second));

        assertThat(service.resolveInheritedLearnerLevel(firstId)).isEmpty();
        verify(collectionRepository, times(1)).findById(firstId);
        verify(collectionRepository, times(1)).findById(secondId);
    }

    @Test
    void resolveInheritedLearnerLevel_returnsOwnLevelEvenWhenAncestorChainIsCyclic() {
        // A broken chain further up must not discard a level this collection sets itself.
        // The walk answers as soon as it finds one, so the cycle is never reached.
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        NoteCollectionEntity first = buildCollection(firstId, UUID.randomUUID(), "First", Instant.now());
        NoteCollectionEntity second = buildCollection(secondId, first.getOwnerUserId(), "Second", Instant.now());
        first.setLearnerLevel(LearnerLevel.BOARD_EXAM_REVIEW);
        first.setParentCollectionId(secondId);
        second.setParentCollectionId(firstId);
        when(collectionRepository.findById(firstId)).thenReturn(Optional.of(first));

        assertThat(service.resolveInheritedLearnerLevel(firstId)).contains(LearnerLevel.BOARD_EXAM_REVIEW);
    }

    @Test
    void updateMetadata_titleOnlyRequestPreservesDescriptionCourseProgramAndEstimatedHours() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        collection.setCourseProgram(COURSE_PROGRAM);
        collection.setEstimatedStudyHours(4);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        // Regression: the Goal Builder rename PATCHes only a title. The other fields arrive null,
        // and null must mean "not provided" — not "clear it" — so they must survive untouched.
        NoteCollectionDetailResponse result = service.updateMetadata(collectionId, userId, new UpdateNoteCollectionRequest(
                UPDATED_COLLECTION_TITLE,
                null,
                null,
                null,
                null
        ));

        assertThat(result.title()).isEqualTo(UPDATED_COLLECTION_TITLE);
        assertThat(result.description()).isEqualTo(COLLECTION_DESCRIPTION);
        assertThat(result.courseProgram()).isEqualTo(COURSE_PROGRAM);
        assertThat(result.estimatedStudyHours()).isEqualTo(4);
    }

    @Test
    void updateMetadata_preservesEstimatedStudyHoursWhenRequestValueIsNull() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        collection.setEstimatedStudyHours(4);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateMetadata(collectionId, userId, new UpdateNoteCollectionRequest(
                null,
                COLLECTION_DESCRIPTION,
                COURSE_PROGRAM,
                null,
                null
        ));

        assertThat(result.estimatedStudyHours()).isEqualTo(4);
        assertThat(collection.getEstimatedStudyHours()).isEqualTo(4);
    }

    @Test
    void updateMetadata_clearsDescriptionOnExplicitEmptyString() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        // An explicit empty string is the caller's signal to clear a text field.
        NoteCollectionDetailResponse result = service.updateMetadata(collectionId, userId, new UpdateNoteCollectionRequest(
                null,
                "",
                null,
                null,
                null
        ));

        assertThat(result.description()).isNull();
        assertThat(collection.getDescription()).isNull();
    }

    @Test
    void updateMetadata_cascadesCourseProgramToBlankChildrenOnlyNotDifferentValues() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID blankChildId = UUID.randomUUID();
        UUID differentChildId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionEntity blankChild = buildCollection(blankChildId, userId, "Blank Child", Instant.now());
        blankChild.setParentCollectionId(goalId);
        NoteCollectionEntity differentChild = buildCollection(differentChildId, userId, "Different Child", Instant.now());
        differentChild.setParentCollectionId(goalId);
        differentChild.setCourseProgram("Existing Program");
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.save(goal)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of());
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(blankChild, differentChild));
        when(collectionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateMetadata(goalId, userId, new UpdateNoteCollectionRequest(
                null,
                null,
                UPDATED_COURSE_PROGRAM,
                null,
                null
        ));

        assertThat(blankChild.getCourseProgram()).isEqualTo(UPDATED_COURSE_PROGRAM);
        assertThat(differentChild.getCourseProgram()).isEqualTo("Existing Program");
        verify(collectionRepository).saveAll(List.of(blankChild));
    }

    @Test
    void updateMetadata_doesNotCascadeCourseProgramWhenClearedToBlank() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, COLLECTION_TITLE, Instant.now());
        goal.setCourseProgram(COURSE_PROGRAM);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.save(goal)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of());

        // Explicit empty string clears courseProgram; a clear must not cascade anything to children.
        service.updateMetadata(goalId, userId, new UpdateNoteCollectionRequest(
                null,
                null,
                "",
                null,
                null
        ));

        assertThat(goal.getCourseProgram()).isNull();
        verify(collectionRepository, never()).findOrderedChildrenByParentCollectionIdAndOwnerUserId(any(), any());
        verify(collectionRepository, never()).saveAll(anyList());
    }

    @Test
    void updateMetadata_setsTargetCompletionDateOnTopLevelGoal() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateMetadata(collectionId, userId, new UpdateNoteCollectionRequest(
                null,
                null,
                null,
                null,
                LocalDate.parse("2026-12-01")
        ));

        assertThat(result.targetCompletionDate()).isEqualTo(LocalDate.parse("2026-12-01"));
        assertThat(collection.getTargetCompletionDate()).isEqualTo(LocalDate.parse("2026-12-01"));
    }

    @Test
    void updateMetadata_rejectsTargetCompletionDateOnChildSubjectPlan() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        collection.setParentCollectionId(UUID.randomUUID());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> service.updateMetadata(collectionId, userId, new UpdateNoteCollectionRequest(
                null,
                null,
                null,
                null,
                LocalDate.parse("2026-12-01")
        ))).isInstanceOf(InvalidCollectionRequestException.class);
    }

    @Test
    void updateMetadata_preservesTargetCompletionDateWhenRequestValueIsNull() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        collection.setTargetCompletionDate(LocalDate.parse("2026-12-01"));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateMetadata(collectionId, userId, new UpdateNoteCollectionRequest(
                UPDATED_COLLECTION_TITLE,
                null,
                null,
                null,
                null
        ));

        assertThat(result.targetCompletionDate()).isEqualTo(LocalDate.parse("2026-12-01"));
    }

    @Test
    void clearTargetDate_clearsExistingDate() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        collection.setTargetCompletionDate(LocalDate.parse("2026-12-01"));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.clearTargetDate(collectionId, userId);

        assertThat(result.targetCompletionDate()).isNull();
        assertThat(collection.getTargetCompletionDate()).isNull();
    }

    @Test
    void clearTargetDate_isNoOpWhenNothingSet() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        service.clearTargetDate(collectionId, userId);

        verify(collectionRepository, never()).save(any());
    }

    @Test
    void clearTargetDate_rejectsCollectionOwnedByAnotherUserAsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.clearTargetDate(collectionId, userId))
                .isInstanceOf(CollectionNotFoundException.class);
    }

    @Test
    void getNoteConceptCounts_returnsCountsForNotesWithStudyPacksOnly() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        UUID noPackNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        List<NoteCollectionItemEntity> items = List.of(
                buildItem(collectionId, firstNoteId, 0, WEEK_ONE_LABEL),
                buildItem(collectionId, noPackNoteId, 1, null),
                buildItem(collectionId, secondNoteId, 2, WEEK_TWO_LABEL)
        );
        StudyPackEntity firstPack = buildStudyPack(firstNoteId, List.of("Cells", "DNA", "Mitosis"));
        StudyPackEntity secondPack = buildStudyPack(secondNoteId, List.of("Bonds", "Acids"));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(items);
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(firstNoteId, noPackNoteId, secondNoteId)))
                .thenReturn(asProjections(firstPack, secondPack));
        when(progressReportService.getConceptCountsPerStudyPack(
                eq(List.of(firstPack.getId(), secondPack.getId())),
                anyCollection(),
                eq(userId),
                any(OffsetDateTime.class)
        )).thenReturn(Map.of(
                firstPack.getId(), new ProgressReportService.ConceptCounts(3, 1, 1, 1),
                secondPack.getId(), new ProgressReportService.ConceptCounts(2, 0, 1, 1)
        ));

        Map<String, NoteConceptCountsResponse> result = service.getNoteConceptCounts(collectionId, userId);

        assertThat(result).containsOnly(
                Map.entry(firstNoteId.toString(), new NoteConceptCountsResponse(3, 1, 1, 1)),
                Map.entry(secondNoteId.toString(), new NoteConceptCountsResponse(2, 0, 1, 1))
        );
        assertThat(result).doesNotContainKey(noPackNoteId.toString());
    }

    @Test
    void getNoteConceptCounts_returnsEmptyMapWhenNoNotesHaveStudyPacks() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(List.of(buildItem(collectionId, noteId, 0, null)));
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId))).thenReturn(List.of());

        Map<String, NoteConceptCountsResponse> result = service.getNoteConceptCounts(collectionId, userId);

        assertThat(result).isEmpty();
        verify(progressReportService, never()).getConceptCountsPerStudyPack(anyList(), any(), any(), any());
    }

    @Test
    void getNoteConceptCounts_returnsNotFoundForNonOwnedCollection() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNoteConceptCounts(collectionId, userId))
                .isInstanceOf(CollectionNotFoundException.class);
        verify(itemRepository, never()).findByCollectionIdOrderByPositionAsc(collectionId);
    }

    @Test
    void listPublic_filtersByNormalizedCourseProgramAndReturnsPublicPlansOnly() {
        NoteCollectionEntity collection = buildCollection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                COLLECTION_TITLE,
                Instant.now()
        );
        collection.setVisibility(CollectionVisibility.PUBLIC);
        collection.setCourseProgram(UPDATED_COURSE_PROGRAM);
        when(collectionRepository.findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
                CollectionVisibility.PUBLIC,
                UPDATED_COURSE_PROGRAM
        )).thenReturn(List.of(collection));
        when(itemRepository.countItemsByCollectionIds(List.of(collection.getId())))
                .thenReturn(List.of(countProjection(collection.getId(), 2)));
        when(collectionRepository.countChildrenByCollectionIds(List.of(collection.getId())))
                .thenReturn(List.of(childCountProjection(collection.getId(), 1)));

        List<NoteCollectionSummaryResponse> result = service.listPublic("  " + UPDATED_COURSE_PROGRAM + "  ");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().visibility()).isEqualTo(CollectionVisibility.PUBLIC.name());
        assertThat(result.getFirst().courseProgram()).isEqualTo(UPDATED_COURSE_PROGRAM);
        assertThat(result.getFirst().itemCount()).isEqualTo(2);
        assertThat(result.getFirst().readyCount()).isZero();
        assertThat(result.getFirst().childCount()).isEqualTo(1);
        assertThat(result.getFirst().notesPracticed()).isZero();
        verify(collectionRepository).findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
                CollectionVisibility.PUBLIC,
                UPDATED_COURSE_PROGRAM
        );
        verify(quizSessionHistoryService, never()).findLatestSessionCompletedAtByNoteIds(any(), any());
    }

    @Test
    void listPublic_countsOnlyStudyPackReadyNotes() {
        UUID collectionId = UUID.randomUUID();
        UUID readyNoteId = UUID.randomUUID();
        UUID generatingNoteId = UUID.randomUUID();
        UUID failedNoteId = UUID.randomUUID();
        UUID draftNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, UUID.randomUUID(), COLLECTION_TITLE, Instant.now());
        collection.setVisibility(CollectionVisibility.PUBLIC);
        List<UUID> noteIds = List.of(readyNoteId, generatingNoteId, failedNoteId, draftNoteId);
        NoteEntity readyNote = buildNote(readyNoteId, collection.getOwnerUserId(), NOTE_TITLE_ONE);
        readyNote.setVisibility(NoteVisibility.PUBLIC);
        NoteEntity generatingNote = buildNote(generatingNoteId, collection.getOwnerUserId(), NOTE_TITLE_TWO);
        generatingNote.setVisibility(NoteVisibility.PUBLIC);
        generatingNote.setStatus(NoteStatus.GENERATING);
        NoteEntity failedNote = buildNote(failedNoteId, collection.getOwnerUserId(), NOTE_TITLE_THREE);
        failedNote.setVisibility(NoteVisibility.PUBLIC);
        failedNote.setStatus(NoteStatus.FAILED);
        NoteEntity draftNote = buildNote(draftNoteId, collection.getOwnerUserId(), "Draft note");
        draftNote.setVisibility(NoteVisibility.PUBLIC);
        when(collectionRepository.findByVisibilityAndParentCollectionIdIsNullOrderByUpdatedAtDesc(CollectionVisibility.PUBLIC))
                .thenReturn(List.of(collection));
        when(itemRepository.countItemsByCollectionIds(List.of(collectionId)))
                .thenReturn(List.of(countProjection(collectionId, noteIds.size())));
        when(itemRepository.findNoteIdsByCollectionIds(List.of(collectionId))).thenReturn(List.of(
                noteProjection(collectionId, readyNoteId),
                noteProjection(collectionId, generatingNoteId),
                noteProjection(collectionId, failedNoteId),
                noteProjection(collectionId, draftNoteId)
        ));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(noteIds))
                .thenReturn(asNoteProjections(readyNote, generatingNote, failedNote, draftNote));
        when(studyPackRepository.findProgressViewsByNoteIdIn(noteIds)).thenReturn(asProjections(
                buildStudyPack(readyNoteId),
                buildStudyPack(generatingNoteId),
                buildStudyPack(failedNoteId)
        ));
        when(collectionRepository.countChildrenByCollectionIds(List.of(collectionId))).thenReturn(List.of());

        List<NoteCollectionSummaryResponse> result = service.listPublic(null);

        assertThat(result).singleElement().extracting(NoteCollectionSummaryResponse::readyCount).isEqualTo(1);
    }

    @Test
    void listPublic_rollsUpGoalItemAndReadyCountsFromCascadePublishedChildren() {
        UUID goalId = UUID.randomUUID();
        UUID firstChildId = UUID.randomUUID();
        UUID secondChildId = UUID.randomUUID();
        UUID firstReadyNoteId = UUID.randomUUID();
        UUID secondNotReadyNoteId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, UUID.randomUUID(), "Public Goal", Instant.now());
        goal.setVisibility(CollectionVisibility.PUBLIC);
        NoteCollectionEntity firstChild = buildCollection(firstChildId, goal.getOwnerUserId(), "First Subject", Instant.now());
        NoteCollectionEntity secondChild = buildCollection(secondChildId, goal.getOwnerUserId(), "Second Subject", Instant.now());
        firstChild.setParentCollectionId(goalId);
        secondChild.setParentCollectionId(goalId);
        NoteEntity firstReadyNote = buildNote(firstReadyNoteId, goal.getOwnerUserId(), NOTE_TITLE_ONE);
        NoteEntity secondNotReadyNote = buildNote(secondNotReadyNoteId, goal.getOwnerUserId(), NOTE_TITLE_TWO);
        List<NoteCollectionEntity> collectionsWithChildren = List.of(goal, firstChild, secondChild);

        when(collectionRepository.findByVisibilityAndParentCollectionIdIsNullOrderByUpdatedAtDesc(CollectionVisibility.PUBLIC))
                .thenReturn(List.of(goal));
        when(collectionRepository.findByParentCollectionIdIn(List.of(goalId))).thenReturn(List.of(firstChild, secondChild));
        when(itemRepository.countItemsByCollectionIds(collectionsWithChildren.stream().map(NoteCollectionEntity::getId).toList()))
                .thenReturn(List.of(countProjection(firstChildId, 1), countProjection(secondChildId, 1)));
        when(itemRepository.findNoteIdsByCollectionIds(collectionsWithChildren.stream().map(NoteCollectionEntity::getId).toList()))
                .thenReturn(List.of(
                        noteProjection(firstChildId, firstReadyNoteId),
                        noteProjection(secondChildId, secondNotReadyNoteId)
                ));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(anyList()))
                .thenReturn(asNoteProjections(firstReadyNote, secondNotReadyNote));
        when(studyPackRepository.findProgressViewsByNoteIdIn(anyList())).thenReturn(asProjections(buildStudyPack(firstReadyNoteId)));
        when(collectionRepository.countChildrenByCollectionIds(List.of(goalId)))
                .thenReturn(List.of(childCountProjection(goalId, 2)));

        List<NoteCollectionSummaryResponse> result = service.listPublic(null);

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.itemCount()).isEqualTo(2);
            assertThat(summary.readyCount()).isEqualTo(1);
        });
        verify(collectionRepository).findByParentCollectionIdIn(List.of(goalId));
    }

    @Test
    void getPublic_exposesReadyCountForItsPublicItems() {
        UUID collectionId = UUID.randomUUID();
        UUID readyNoteId = UUID.randomUUID();
        UUID draftNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, UUID.randomUUID(), COLLECTION_TITLE, Instant.now());
        collection.setVisibility(CollectionVisibility.PUBLIC);
        NoteEntity readyNote = buildNote(readyNoteId, collection.getOwnerUserId(), NOTE_TITLE_ONE);
        readyNote.setVisibility(NoteVisibility.PUBLIC);
        NoteEntity draftNote = buildNote(draftNoteId, collection.getOwnerUserId(), NOTE_TITLE_TWO);
        draftNote.setVisibility(NoteVisibility.PUBLIC);
        List<UUID> noteIds = List.of(readyNoteId, draftNoteId);
        when(collectionRepository.findByIdAndVisibility(collectionId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(collection));
        when(collectionRepository.findByParentCollectionIdIn(List.of(collectionId))).thenReturn(List.of());
        when(itemRepository.findByCollectionIdInOrderByCollectionIdAscPositionAsc(List.of(collectionId))).thenReturn(List.of(
                buildItem(collectionId, readyNoteId, 0, null),
                buildItem(collectionId, draftNoteId, 1, null)
        ));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(noteIds)).thenReturn(asNoteProjections(readyNote, draftNote));
        when(studyPackRepository.findProgressViewsByNoteIdIn(noteIds)).thenReturn(asProjections(buildStudyPack(readyNoteId)));
        when(collectionRepository.countByParentCollectionId(collectionId)).thenReturn(0L);

        NoteCollectionDetailResponse result = service.getPublic(collectionId);

        assertThat(result.readyCount()).isEqualTo(1);
        assertThat(result.progress().notesWithStudyPack()).isEqualTo(1);
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void getPublic_rollsUpGoalPreviewItemsAndReadyCountFromChildren() {
        UUID goalId = UUID.randomUUID();
        UUID firstChildId = UUID.randomUUID();
        UUID secondChildId = UUID.randomUUID();
        UUID readyNoteId = UUID.randomUUID();
        UUID draftNoteId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, UUID.randomUUID(), "Public Goal", Instant.now());
        goal.setVisibility(CollectionVisibility.PUBLIC);
        NoteCollectionEntity firstChild = buildCollection(firstChildId, goal.getOwnerUserId(), "First Subject", Instant.now());
        NoteCollectionEntity secondChild = buildCollection(secondChildId, goal.getOwnerUserId(), "Second Subject", Instant.now());
        firstChild.setParentCollectionId(goalId);
        secondChild.setParentCollectionId(goalId);
        NoteEntity readyNote = buildNote(readyNoteId, goal.getOwnerUserId(), NOTE_TITLE_ONE);
        NoteEntity draftNote = buildNote(draftNoteId, goal.getOwnerUserId(), NOTE_TITLE_TWO);
        readyNote.setVisibility(NoteVisibility.PUBLIC);
        draftNote.setVisibility(NoteVisibility.PUBLIC);
        List<UUID> collectionIds = List.of(goalId, firstChildId, secondChildId);
        List<UUID> noteIds = List.of(readyNoteId, draftNoteId);

        when(collectionRepository.findByIdAndVisibility(goalId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(goal));
        when(collectionRepository.findByParentCollectionIdIn(List.of(goalId))).thenReturn(List.of(firstChild, secondChild));
        when(itemRepository.findByCollectionIdInOrderByCollectionIdAscPositionAsc(collectionIds)).thenReturn(List.of(
                buildItem(firstChildId, readyNoteId, 0, null),
                buildItem(secondChildId, draftNoteId, 0, null)
        ));
        when(noteRepository.findCollectionNoteProjectionsByIdIn(noteIds)).thenReturn(asNoteProjections(readyNote, draftNote));
        when(studyPackRepository.findProgressViewsByNoteIdIn(noteIds)).thenReturn(asProjections(buildStudyPack(readyNoteId)));
        when(collectionRepository.countByParentCollectionId(goalId)).thenReturn(2L);

        NoteCollectionDetailResponse result = service.getPublic(goalId);

        assertThat(result.items()).extracting(NoteCollectionItemResponse::noteId)
                .containsExactly(readyNoteId, draftNoteId);
        assertThat(result.readyCount()).isEqualTo(1);
        assertThat(result.progress().totalNotes()).isEqualTo(2);
    }

    @Test
    void updateVisibility_rejectsEmptyCollectionWhenPublishing() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateVisibility(collectionId, userId, CollectionVisibility.PUBLIC.name()))
                .isInstanceOf(CollectionNotPublishableException.class);
    }

    @Test
    void updateVisibility_rejectsPrivateItemWhenPublishing() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity item = buildItem(collectionId, noteId, 0, null);
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(item));
        when(noteRepository.findAllById(List.of(noteId))).thenReturn(List.of(note));

        assertThatThrownBy(() -> service.updateVisibility(collectionId, userId, CollectionVisibility.PUBLIC.name()))
                .isInstanceOf(CollectionNotPublishableException.class);
    }

    @Test
    void updateVisibility_publishesWhenEveryItemIsPublic() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity item = buildItem(collectionId, noteId, 0, null);
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        note.setVisibility(NoteVisibility.PUBLIC);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(item));
        when(noteRepository.findAllById(List.of(noteId))).thenReturn(List.of(note));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(noteId), List.of(note));

        NoteCollectionDetailResponse result = service.updateVisibility(collectionId, userId, CollectionVisibility.PUBLIC.name());

        assertThat(result.visibility()).isEqualTo(CollectionVisibility.PUBLIC.name());
        assertThat(collection.getVisibility()).isEqualTo(CollectionVisibility.PUBLIC);
    }

    @Test
    void updateVisibility_rejectsGoalWithNoChildrenWhenPublishing() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.countByParentCollectionId(goalId)).thenReturn(1L);
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.updateVisibility(goalId, userId, CollectionVisibility.PUBLIC.name()))
                .isInstanceOf(CollectionNotPublishableException.class)
                .hasMessage("A Goal must have at least one Subject plan before it can be published.");
    }

    @Test
    void updateVisibility_rejectsGoalWithEmptyChildWhenPublishing() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(companionContent());
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setParentCollectionId(goalId);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.countByParentCollectionId(goalId)).thenReturn(1L);
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(child));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(childId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateVisibility(goalId, userId, CollectionVisibility.PUBLIC.name()))
                .isInstanceOf(CollectionNotPublishableException.class)
                .hasMessage("All Subject plans must contain at least one public note before publishing a Goal.");
    }

    @Test
    void updateVisibility_rejectsGoalWithPrivateChildNoteWhenPublishing() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(companionContent());
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setParentCollectionId(goalId);
        NoteCollectionItemEntity item = buildItem(childId, noteId, 0, null);
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.countByParentCollectionId(goalId)).thenReturn(1L);
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(child));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(childId)).thenReturn(List.of(item));
        when(noteRepository.findAllById(List.of(noteId))).thenReturn(List.of(note));

        assertThatThrownBy(() -> service.updateVisibility(goalId, userId, CollectionVisibility.PUBLIC.name()))
                .isInstanceOf(CollectionNotPublishableException.class)
                .hasMessage("All notes in all Subject plans must be public before publishing a Goal.");
    }

    @Test
    void updateVisibility_publishingGoalCascadesPublicVisibilityToChildren() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setCompanion(companionContent());
        NoteCollectionEntity child = buildCollection(childId, userId, "Professional Education", Instant.now());
        child.setParentCollectionId(goalId);
        NoteCollectionItemEntity item = buildItem(childId, noteId, 0, null);
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        note.setVisibility(NoteVisibility.PUBLIC);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.countByParentCollectionId(goalId)).thenReturn(1L);
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of(child));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(childId)).thenReturn(List.of(item));
        when(noteRepository.findAllById(List.of(noteId))).thenReturn(List.of(note));
        when(collectionRepository.save(goal)).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateVisibility(goalId, userId, CollectionVisibility.PUBLIC.name());

        assertThat(result.visibility()).isEqualTo(CollectionVisibility.PUBLIC.name());
        assertThat(result.companion()).isEqualTo(companionContent());
        assertThat(child.getCompanion()).isNull();
        assertThat(child.getVisibility()).isEqualTo(CollectionVisibility.PUBLIC);
        verify(collectionRepository).saveAll(List.of(child));
    }

    @Test
    void updateVisibility_unpublishingGoalDoesNotCascadeToChildren() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setVisibility(CollectionVisibility.PUBLIC);
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.save(goal)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateVisibility(goalId, userId, CollectionVisibility.PRIVATE.name());

        assertThat(result.visibility()).isEqualTo(CollectionVisibility.PRIVATE.name());
        verify(collectionRepository, never()).saveAll(anyList());
        verify(collectionRepository, never()).findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId);
    }

    @Test
    void adopt_returnsExistingPersonalPlanWhenAlreadyAdopted() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID personalPlanId = UUID.randomUUID();
        NoteCollectionEntity source = buildCollection(sourcePlanId, UUID.randomUUID(), COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        NoteCollectionEntity existing = buildCollection(personalPlanId, userId, COLLECTION_TITLE, Instant.now());
        existing.setSourcePlanId(sourcePlanId);
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourcePlanId))
                .thenReturn(Optional.of(existing));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(personalPlanId))
                .thenReturn(List.of(buildItem(personalPlanId, UUID.randomUUID(), 0, null)));

        AdoptStudyPlanResponse result = service.adopt(sourcePlanId, userId);

        assertThat(result.collectionId()).isEqualTo(personalPlanId);
        assertThat(result.alreadyAdopted()).isTrue();
        assertThat(result.copiedCount()).isEqualTo(1);
        verify(noteService, never()).copyNote(any(), any(), eq(true));
    }

    @Test
    void adopt_copiesPublicItemsAndSkipsUnavailableItems() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID privateNoteId = UUID.randomUUID();
        UUID copiedNoteId = UUID.randomUUID();
        NoteCollectionEntity source = buildCollection(sourcePlanId, sourceOwnerId, COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        source.setCourseProgram(UPDATED_COURSE_PROGRAM);
        source.setEstimatedStudyHours(2);
        source.setCompanion(companionContent());
        source.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));
        NoteCollectionItemEntity firstItem = buildItem(sourcePlanId, firstNoteId, 0, WEEK_ONE_LABEL);
        NoteCollectionItemEntity privateItem = buildItem(sourcePlanId, privateNoteId, 1, WEEK_TWO_LABEL);
        NoteEntity publicNote = buildNote(firstNoteId, sourceOwnerId, NOTE_TITLE_ONE);
        publicNote.setVisibility(NoteVisibility.PUBLIC);
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId)).thenReturn(List.of(firstItem, privateItem));
        when(noteRepository.findByIdAndVisibility(firstNoteId, NoteVisibility.PUBLIC)).thenReturn(Optional.of(publicNote));
        when(noteRepository.findByIdAndVisibility(privateNoteId, NoteVisibility.PUBLIC)).thenReturn(Optional.empty());
        when(noteService.copyNote(firstNoteId.toString(), userId, true)).thenReturn(noteResponse(copiedNoteId));
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        AdoptStudyPlanResponse result = service.adopt(sourcePlanId, userId);

        assertThat(result.copiedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.alreadyAdopted()).isFalse();
        ArgumentCaptor<List<NoteCollectionItemEntity>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).extracting(NoteCollectionItemEntity::getNoteId).containsExactly(copiedNoteId);
        assertThat(itemsCaptor.getValue()).extracting(NoteCollectionItemEntity::getLabel).containsExactly(WEEK_ONE_LABEL);
        ArgumentCaptor<NoteCollectionEntity> collectionCaptor = ArgumentCaptor.forClass(NoteCollectionEntity.class);
        verify(collectionRepository).saveAndFlush(collectionCaptor.capture());
        assertThat(collectionCaptor.getValue().getVisibility()).isEqualTo(CollectionVisibility.PRIVATE);
        assertThat(collectionCaptor.getValue().getSourcePlanId()).isEqualTo(sourcePlanId);
        assertThat(collectionCaptor.getValue().getEstimatedStudyHours()).isEqualTo(2);
        assertThat(collectionCaptor.getValue().getCompanion()).isEqualTo(companionContent());
        assertThat(collectionCaptor.getValue().getCompanionStructureSnapshot()).isNull();
    }

    @Test
    void adopt_carriesNullEstimatedStudyHoursToPersonalPlan() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        NoteCollectionEntity source = buildCollection(sourcePlanId, sourceOwnerId, COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        source.setEstimatedStudyHours(null);
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId)).thenReturn(List.of());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.adopt(sourcePlanId, userId);

        ArgumentCaptor<NoteCollectionEntity> collectionCaptor = ArgumentCaptor.forClass(NoteCollectionEntity.class);
        verify(collectionRepository).saveAndFlush(collectionCaptor.capture());
        assertThat(collectionCaptor.getValue().getEstimatedStudyHours()).isNull();
    }

    @Test
    void adopt_neverCopiesTargetCompletionDateFromSource() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        NoteCollectionEntity source = buildCollection(sourcePlanId, sourceOwnerId, COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        source.setTargetCompletionDate(LocalDate.parse("2026-12-01"));
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId)).thenReturn(List.of());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.adopt(sourcePlanId, userId);

        ArgumentCaptor<NoteCollectionEntity> collectionCaptor = ArgumentCaptor.forClass(NoteCollectionEntity.class);
        verify(collectionRepository).saveAndFlush(collectionCaptor.capture());
        assertThat(collectionCaptor.getValue().getTargetCompletionDate()).isNull();
    }

    @Test
    void adopt_copiesCompanionOnCrossOwnerAdopt() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        NoteCollectionEntity source = buildCollection(sourcePlanId, sourceOwnerId, COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        source.setCompanion(companionContent());
        source.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId)).thenReturn(List.of());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.adopt(sourcePlanId, userId);

        ArgumentCaptor<NoteCollectionEntity> collectionCaptor = ArgumentCaptor.forClass(NoteCollectionEntity.class);
        verify(collectionRepository).saveAndFlush(collectionCaptor.capture());
        assertThat(collectionCaptor.getValue().getCompanion()).isEqualTo(companionContent());
        assertThat(collectionCaptor.getValue().getCompanionStructureSnapshot()).isNull();
    }

    @Test
    void adopt_neverCopiesTargetCompletionDateOnSelfCopy() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        NoteCollectionEntity source = buildCollection(sourcePlanId, userId, COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        source.setTargetCompletionDate(LocalDate.parse("2026-12-01"));
        source.setCompanion(companionContent());
        source.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId)).thenReturn(List.of());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.adopt(sourcePlanId, userId);

        ArgumentCaptor<NoteCollectionEntity> collectionCaptor = ArgumentCaptor.forClass(NoteCollectionEntity.class);
        verify(collectionRepository).saveAndFlush(collectionCaptor.capture());
        assertThat(collectionCaptor.getValue().getTargetCompletionDate()).isNull();
        assertThat(collectionCaptor.getValue().getCompanion()).isNull();
        assertThat(collectionCaptor.getValue().getCompanionStructureSnapshot()).isNull();
    }

    @Test
    void adopt_isolatesUnexpectedCopyFailureAndKeepsTheRest() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        UUID failingNoteId = UUID.randomUUID();
        UUID okNoteId = UUID.randomUUID();
        UUID copiedNoteId = UUID.randomUUID();
        NoteCollectionEntity source = buildCollection(sourcePlanId, sourceOwnerId, COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        NoteEntity failingNote = buildNote(failingNoteId, sourceOwnerId, NOTE_TITLE_ONE);
        failingNote.setVisibility(NoteVisibility.PUBLIC);
        NoteEntity okNote = buildNote(okNoteId, sourceOwnerId, NOTE_TITLE_TWO);
        okNote.setVisibility(NoteVisibility.PUBLIC);
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId)).thenReturn(List.of(
                buildItem(sourcePlanId, failingNoteId, 0, WEEK_ONE_LABEL),
                buildItem(sourcePlanId, okNoteId, 1, WEEK_TWO_LABEL)
        ));
        when(noteRepository.findByIdAndVisibility(failingNoteId, NoteVisibility.PUBLIC)).thenReturn(Optional.of(failingNote));
        when(noteRepository.findByIdAndVisibility(okNoteId, NoteVisibility.PUBLIC)).thenReturn(Optional.of(okNote));
        when(noteService.copyNote(failingNoteId.toString(), userId, true)).thenThrow(new RuntimeException("copy boom"));
        when(noteService.copyNote(okNoteId.toString(), userId, true)).thenReturn(noteResponse(copiedNoteId));
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        AdoptStudyPlanResponse result = service.adopt(sourcePlanId, userId);

        assertThat(result.copiedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.alreadyAdopted()).isFalse();
        ArgumentCaptor<List<NoteCollectionItemEntity>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).extracting(NoteCollectionItemEntity::getNoteId).containsExactly(copiedNoteId);
    }

    @Test
    void adopt_recoversFromConcurrentFirstAdoptRace() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID copiedNoteId = UUID.randomUUID();
        UUID winnerPlanId = UUID.randomUUID();
        NoteCollectionEntity source = buildCollection(sourcePlanId, sourceOwnerId, COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        NoteEntity publicNote = buildNote(noteId, sourceOwnerId, NOTE_TITLE_ONE);
        publicNote.setVisibility(NoteVisibility.PUBLIC);
        NoteCollectionEntity winner = buildCollection(winnerPlanId, userId, COLLECTION_TITLE, Instant.now());
        winner.setSourcePlanId(sourcePlanId);
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        // fast path empty on first lookup; the winner's row is found after the unique-index race.
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourcePlanId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId))
                .thenReturn(List.of(buildItem(sourcePlanId, noteId, 0, WEEK_ONE_LABEL)));
        when(noteRepository.findByIdAndVisibility(noteId, NoteVisibility.PUBLIC)).thenReturn(Optional.of(publicNote));
        when(noteService.copyNote(noteId.toString(), userId, true)).thenReturn(noteResponse(copiedNoteId));
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate adopt"));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(winnerPlanId))
                .thenReturn(List.of(buildItem(winnerPlanId, copiedNoteId, 0, WEEK_ONE_LABEL)));

        AdoptStudyPlanResponse result = service.adopt(sourcePlanId, userId);

        assertThat(result.collectionId()).isEqualTo(winnerPlanId);
        assertThat(result.alreadyAdopted()).isTrue();
    }

    @Test
    void adopt_autoSetsStandalonePlanWhenItIsFirstTopLevelCollection() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        NoteCollectionEntity[] savedCollection = new NoteCollectionEntity[1];
        NoteCollectionEntity source = buildCollection(sourcePlanId, UUID.randomUUID(), COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId)).thenReturn(List.of());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> {
            savedCollection[0] = invocation.getArgument(0);
            return savedCollection[0];
        });
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.countByOwnerUserIdAndParentCollectionIdIsNull(userId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenAnswer(invocation -> List.of(savedCollection[0]));

        AdoptStudyPlanResponse result = service.adopt(sourcePlanId, userId);

        assertThat(result.alreadyAdopted()).isFalse();
        assertThat(user.getPrimaryCollectionId()).isEqualTo(result.collectionId());
    }

    @Test
    void adopt_setsPrimaryForNewlyAdoptedPlanWhenOtherTopLevelCollectionsExist() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        NoteCollectionEntity[] savedCollection = new NoteCollectionEntity[1];
        NoteCollectionEntity source = buildCollection(sourcePlanId, UUID.randomUUID(), COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId)).thenReturn(List.of());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> {
            savedCollection[0] = invocation.getArgument(0);
            return savedCollection[0];
        });
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.countByOwnerUserIdAndParentCollectionIdIsNull(userId)).thenReturn(3L);

        AdoptStudyPlanResponse result = service.adopt(sourcePlanId, userId);

        assertThat(result.alreadyAdopted()).isFalse();
        assertThat(user.getPrimaryCollectionId()).isEqualTo(result.collectionId());
    }

    @Test
    void adopt_doesNotOverwriteExistingPrimary() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID existingPrimaryId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setPrimaryCollectionId(existingPrimaryId);
        NoteCollectionEntity existingPrimary = buildCollection(existingPrimaryId, userId, "Existing Goal", Instant.now());
        NoteCollectionEntity source = buildCollection(sourcePlanId, UUID.randomUUID(), COLLECTION_TITLE, Instant.now());
        source.setVisibility(CollectionVisibility.PUBLIC);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(source));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlanId)).thenReturn(Optional.empty());
        when(collectionRepository.findByIdAndOwnerUserId(existingPrimaryId, userId)).thenReturn(Optional.of(existingPrimary));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlanId)).thenReturn(List.of());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.adopt(sourcePlanId, userId);

        assertThat(user.getPrimaryCollectionId()).isEqualTo(existingPrimaryId);
    }

    @Test
    void adoptGoal_copiesPublicChildrenAndNestsSubjectsUnderPersonalGoal() {
        UUID userId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        UUID sourceGoalId = UUID.randomUUID();
        UUID firstChildId = UUID.randomUUID();
        UUID secondChildId = UUID.randomUUID();
        UUID firstSourceNoteId = UUID.randomUUID();
        UUID secondSourceNoteId = UUID.randomUUID();
        UUID firstCopiedNoteId = UUID.randomUUID();
        UUID secondCopiedNoteId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        NoteCollectionEntity[] savedGoal = new NoteCollectionEntity[1];
        NoteCollectionEntity sourceGoal = buildCollection(sourceGoalId, sourceOwnerId, "LET Mastery", Instant.now());
        sourceGoal.setVisibility(CollectionVisibility.PUBLIC);
        sourceGoal.setCourseProgram(UPDATED_COURSE_PROGRAM);
        sourceGoal.setEstimatedStudyHours(3);
        sourceGoal.setCompanion(companionContent());
        sourceGoal.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));
        NoteCollectionEntity firstChild = buildCollection(firstChildId, sourceOwnerId, "General Education", Instant.now());
        NoteCollectionEntity secondChild = buildCollection(secondChildId, sourceOwnerId, "Professional Education", Instant.now());
        firstChild.setVisibility(CollectionVisibility.PUBLIC);
        secondChild.setVisibility(CollectionVisibility.PUBLIC);
        firstChild.setParentCollectionId(sourceGoalId);
        secondChild.setParentCollectionId(sourceGoalId);
        NoteEntity firstPublicNote = buildNote(firstSourceNoteId, sourceOwnerId, NOTE_TITLE_ONE);
        NoteEntity secondPublicNote = buildNote(secondSourceNoteId, sourceOwnerId, NOTE_TITLE_TWO);
        firstPublicNote.setVisibility(NoteVisibility.PUBLIC);
        secondPublicNote.setVisibility(NoteVisibility.PUBLIC);
        NoteCollectionEntity personalFirstChild = buildCollection(UUID.randomUUID(), userId, "General Education", Instant.now());
        NoteCollectionEntity personalSecondChild = buildCollection(UUID.randomUUID(), userId, "Professional Education", Instant.now());
        personalFirstChild.setCompanion(companionContent());
        personalFirstChild.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));
        personalSecondChild.setCompanion(companionContent());
        personalSecondChild.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(sourceGoal));
        when(collectionRepository.countByParentCollectionId(sourceGoalId)).thenReturn(2L);
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(sourceGoalId, sourceOwnerId))
                .thenReturn(List.of(firstChild, secondChild));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> {
            NoteCollectionEntity saved = invocation.getArgument(0);
            if (savedGoal[0] == null) {
                savedGoal[0] = saved;
            }
            return saved;
        });
        when(collectionRepository.findByIdAndOwnerUserId(any(UUID.class), eq(userId)))
                .thenAnswer(invocation -> Optional.ofNullable(savedGoal[0]));
        when(collectionRepository.findByIdAndVisibility(firstChildId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(firstChild));
        when(collectionRepository.findByIdAndVisibility(secondChildId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(secondChild));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, firstChildId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(personalFirstChild));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, secondChildId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(personalSecondChild));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, firstChildId)).thenReturn(Optional.empty());
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, secondChildId)).thenReturn(Optional.empty());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(firstChildId))
                .thenReturn(List.of(buildItem(firstChildId, firstSourceNoteId, 0, WEEK_ONE_LABEL)));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(secondChildId))
                .thenReturn(List.of(buildItem(secondChildId, secondSourceNoteId, 0, WEEK_TWO_LABEL)));
        when(noteRepository.findByIdAndVisibility(firstSourceNoteId, NoteVisibility.PUBLIC)).thenReturn(Optional.of(firstPublicNote));
        when(noteRepository.findByIdAndVisibility(secondSourceNoteId, NoteVisibility.PUBLIC)).thenReturn(Optional.of(secondPublicNote));
        when(noteService.copyNote(firstSourceNoteId.toString(), userId, true)).thenReturn(noteResponse(firstCopiedNoteId));
        when(noteService.copyNote(secondSourceNoteId.toString(), userId, true)).thenReturn(noteResponse(secondCopiedNoteId));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdoptGoalResponse result = service.adoptGoal(sourceGoalId, userId);

        assertThat(result.alreadyAdopted()).isFalse();
        assertThat(result.adoptedSubjectCount()).isEqualTo(2);
        assertThat(result.skippedSubjectCount()).isZero();
        assertThat(result.totalNotesCopied()).isEqualTo(2);
        assertThat(result.totalNotesSkipped()).isZero();
        assertThat(user.getPrimaryCollectionId()).isEqualTo(result.goalCollectionId());
        assertThat(user.getPrimaryCollectionId())
                .isNotEqualTo(personalFirstChild.getId())
                .isNotEqualTo(personalSecondChild.getId());
        assertThat(personalFirstChild.getParentCollectionId()).isEqualTo(result.goalCollectionId());
        assertThat(personalSecondChild.getParentCollectionId()).isEqualTo(result.goalCollectionId());
        assertThat(personalFirstChild.getSiblingPosition()).isZero();
        assertThat(personalSecondChild.getSiblingPosition()).isEqualTo(1);
        assertThat(personalFirstChild.getCompanion()).isNull();
        assertThat(personalFirstChild.getCompanionStructureSnapshot()).isNull();
        assertThat(personalSecondChild.getCompanion()).isNull();
        assertThat(personalSecondChild.getCompanionStructureSnapshot()).isNull();
        ArgumentCaptor<NoteCollectionEntity> goalCaptor = ArgumentCaptor.forClass(NoteCollectionEntity.class);
        verify(collectionRepository, times(3)).saveAndFlush(goalCaptor.capture());
        assertThat(goalCaptor.getAllValues().getFirst().getEstimatedStudyHours()).isEqualTo(3);
        assertThat(goalCaptor.getAllValues().getFirst().getCompanion()).isEqualTo(companionContent());
        assertThat(goalCaptor.getAllValues().getFirst().getCompanionStructureSnapshot()).isNull();
        verify(analyticsService).trackEvent(
                eq(userId),
                eq(AnalyticsEventType.STUDY_GOAL_ADOPTED),
                eq(result.goalCollectionId()),
                anyMap()
        );
    }

    @Test
    void adoptGoal_returnsExistingGoalOnSecondCall() {
        UUID userId = UUID.randomUUID();
        UUID sourceGoalId = UUID.randomUUID();
        UUID personalGoalId = UUID.randomUUID();
        UUID personalChildId = UUID.randomUUID();
        NoteCollectionEntity sourceGoal = buildCollection(sourceGoalId, UUID.randomUUID(), "LET Mastery", Instant.now());
        sourceGoal.setVisibility(CollectionVisibility.PUBLIC);
        NoteCollectionEntity existingGoal = buildCollection(personalGoalId, userId, "LET Mastery", Instant.now());
        existingGoal.setSourcePlanId(sourceGoalId);
        NoteCollectionEntity existingChild = buildCollection(personalChildId, userId, "Professional Education", Instant.now());
        existingChild.setParentCollectionId(personalGoalId);
        when(collectionRepository.findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(sourceGoal));
        when(collectionRepository.countByParentCollectionId(sourceGoalId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId)).thenReturn(Optional.of(existingGoal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(personalGoalId, userId))
                .thenReturn(List.of(existingChild));
        when(itemRepository.countItemsByCollectionIds(List.of(personalChildId)))
                .thenReturn(List.of(countProjection(personalChildId, 4)));

        AdoptGoalResponse result = service.adoptGoal(sourceGoalId, userId);

        assertThat(result.goalCollectionId()).isEqualTo(personalGoalId);
        assertThat(result.alreadyAdopted()).isTrue();
        assertThat(result.adoptedSubjectCount()).isEqualTo(1);
        assertThat(result.totalNotesCopied()).isEqualTo(4);
        verify(noteService, never()).copyNote(any(), any(), eq(true));
    }

    @Test
    void adoptGoal_reparentsStandaloneAdoptedChildAndSkipsAlreadyNestedChild() {
        UUID userId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        UUID sourceGoalId = UUID.randomUUID();
        UUID standaloneSourceChildId = UUID.randomUUID();
        UUID nestedSourceChildId = UUID.randomUUID();
        UUID otherGoalId = UUID.randomUUID();
        NoteCollectionEntity sourceGoal = buildCollection(sourceGoalId, sourceOwnerId, "LET Mastery", Instant.now());
        sourceGoal.setVisibility(CollectionVisibility.PUBLIC);
        NoteCollectionEntity standaloneSourceChild = buildCollection(standaloneSourceChildId, sourceOwnerId, "Standalone", Instant.now());
        NoteCollectionEntity nestedSourceChild = buildCollection(nestedSourceChildId, sourceOwnerId, "Nested", Instant.now());
        standaloneSourceChild.setVisibility(CollectionVisibility.PUBLIC);
        nestedSourceChild.setVisibility(CollectionVisibility.PUBLIC);
        NoteCollectionEntity standalonePersonalChild = buildCollection(UUID.randomUUID(), userId, "Standalone", Instant.now());
        standalonePersonalChild.setSourcePlanId(standaloneSourceChildId);
        standalonePersonalChild.setCompanion(companionContent());
        standalonePersonalChild.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));
        NoteCollectionEntity nestedPersonalChild = buildCollection(UUID.randomUUID(), userId, "Nested", Instant.now());
        nestedPersonalChild.setSourcePlanId(nestedSourceChildId);
        nestedPersonalChild.setParentCollectionId(otherGoalId);
        when(collectionRepository.findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(sourceGoal));
        when(collectionRepository.countByParentCollectionId(sourceGoalId)).thenReturn(2L);
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(sourceGoalId, sourceOwnerId))
                .thenReturn(List.of(standaloneSourceChild, nestedSourceChild));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.findByIdAndVisibility(standaloneSourceChildId, CollectionVisibility.PUBLIC))
                .thenReturn(Optional.of(standaloneSourceChild));
        when(collectionRepository.findByIdAndVisibility(nestedSourceChildId, CollectionVisibility.PUBLIC))
                .thenReturn(Optional.of(nestedSourceChild));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, standaloneSourceChildId))
                .thenReturn(Optional.of(standalonePersonalChild))
                .thenReturn(Optional.of(standalonePersonalChild));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, nestedSourceChildId))
                .thenReturn(Optional.of(nestedPersonalChild))
                .thenReturn(Optional.of(nestedPersonalChild));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(standalonePersonalChild.getId()))
                .thenReturn(List.of(buildItem(standalonePersonalChild.getId(), UUID.randomUUID(), 0, null)));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(nestedPersonalChild.getId()))
                .thenReturn(List.of(buildItem(nestedPersonalChild.getId(), UUID.randomUUID(), 0, null)));
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdoptGoalResponse result = service.adoptGoal(sourceGoalId, userId);

        assertThat(result.adoptedSubjectCount()).isEqualTo(1);
        assertThat(result.skippedSubjectCount()).isEqualTo(1);
        assertThat(standalonePersonalChild.getParentCollectionId()).isEqualTo(result.goalCollectionId());
        assertThat(standalonePersonalChild.getSiblingPosition()).isZero();
        assertThat(standalonePersonalChild.getCompanion()).isNull();
        assertThat(standalonePersonalChild.getCompanionStructureSnapshot()).isNull();
        assertThat(nestedPersonalChild.getParentCollectionId()).isEqualTo(otherGoalId);
    }

    @Test
    void adoptGoal_recoversFromConcurrentFirstAdoptRace() {
        UUID userId = UUID.randomUUID();
        UUID sourceGoalId = UUID.randomUUID();
        UUID winnerGoalId = UUID.randomUUID();
        NoteCollectionEntity sourceGoal = buildCollection(sourceGoalId, UUID.randomUUID(), "LET Mastery", Instant.now());
        sourceGoal.setVisibility(CollectionVisibility.PUBLIC);
        NoteCollectionEntity winner = buildCollection(winnerGoalId, userId, "LET Mastery", Instant.now());
        winner.setSourcePlanId(sourceGoalId);
        when(collectionRepository.findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(sourceGoal));
        when(collectionRepository.countByParentCollectionId(sourceGoalId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(sourceGoalId, sourceGoal.getOwnerUserId()))
                .thenReturn(List.of(buildCollection(UUID.randomUUID(), sourceGoal.getOwnerUserId(), "Child", Instant.now())));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate goal adopt"));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(winnerGoalId, userId))
                .thenReturn(List.of());

        AdoptGoalResponse result = service.adoptGoal(sourceGoalId, userId);

        assertThat(result.goalCollectionId()).isEqualTo(winnerGoalId);
        assertThat(result.alreadyAdopted()).isTrue();
    }

    @Test
    void adoptGoal_autoSetsFirstTimeGoalWhenItIsFirstTopLevelCollection() {
        UUID userId = UUID.randomUUID();
        UUID sourceGoalId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        NoteCollectionEntity[] savedGoal = new NoteCollectionEntity[1];
        NoteCollectionEntity sourceGoal = buildCollection(sourceGoalId, UUID.randomUUID(), "LET Mastery", Instant.now());
        sourceGoal.setVisibility(CollectionVisibility.PUBLIC);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(collectionRepository.findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(sourceGoal));
        when(collectionRepository.countByParentCollectionId(sourceGoalId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(sourceGoalId, sourceGoal.getOwnerUserId()))
                .thenReturn(List.of());
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> {
            savedGoal[0] = invocation.getArgument(0);
            return savedGoal[0];
        });
        when(collectionRepository.countByOwnerUserIdAndParentCollectionIdIsNull(userId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId))
                .thenAnswer(invocation -> List.of(savedGoal[0]));

        AdoptGoalResponse result = service.adoptGoal(sourceGoalId, userId);

        assertThat(result.alreadyAdopted()).isFalse();
        assertThat(user.getPrimaryCollectionId()).isEqualTo(result.goalCollectionId());
    }

    @Test
    void adoptGoal_neverCopiesTargetCompletionDateFromSourceGoal() {
        UUID userId = UUID.randomUUID();
        UUID sourceGoalId = UUID.randomUUID();
        NoteCollectionEntity sourceGoal = buildCollection(sourceGoalId, UUID.randomUUID(), "LET Mastery", Instant.now());
        sourceGoal.setVisibility(CollectionVisibility.PUBLIC);
        sourceGoal.setTargetCompletionDate(LocalDate.parse("2026-12-01"));
        sourceGoal.setCompanion(companionContent());
        when(collectionRepository.findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(sourceGoal));
        when(collectionRepository.countByParentCollectionId(sourceGoalId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(sourceGoalId, sourceGoal.getOwnerUserId()))
                .thenReturn(List.of());
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptGoal(sourceGoalId, userId);

        ArgumentCaptor<NoteCollectionEntity> collectionCaptor = ArgumentCaptor.forClass(NoteCollectionEntity.class);
        verify(collectionRepository).saveAndFlush(collectionCaptor.capture());
        assertThat(collectionCaptor.getValue().getTargetCompletionDate()).isNull();
        assertThat(collectionCaptor.getValue().getCompanion()).isEqualTo(companionContent());
    }

    @Test
    void adoptGoal_excludesCompanionOnSelfCopy() {
        UUID userId = UUID.randomUUID();
        UUID sourceGoalId = UUID.randomUUID();
        NoteCollectionEntity sourceGoal = buildCollection(sourceGoalId, userId, "LET Mastery", Instant.now());
        sourceGoal.setVisibility(CollectionVisibility.PUBLIC);
        sourceGoal.setCompanion(companionContent());
        sourceGoal.setCompanionStructureSnapshot(new CompanionStructureSnapshot(0, List.of()));
        when(collectionRepository.findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(sourceGoal));
        when(collectionRepository.countByParentCollectionId(sourceGoalId)).thenReturn(1L);
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(sourceGoalId, userId))
                .thenReturn(List.of());
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptGoal(sourceGoalId, userId);

        ArgumentCaptor<NoteCollectionEntity> collectionCaptor = ArgumentCaptor.forClass(NoteCollectionEntity.class);
        verify(collectionRepository).saveAndFlush(collectionCaptor.capture());
        assertThat(collectionCaptor.getValue().getCompanion()).isNull();
        assertThat(collectionCaptor.getValue().getCompanionStructureSnapshot()).isNull();
    }

    @Test
    void adoptGoal_rejectsPrivateSource() {
        UUID userId = UUID.randomUUID();
        UUID sourceGoalId = UUID.randomUUID();
        when(collectionRepository.findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adoptGoal(sourceGoalId, userId))
                .isInstanceOf(CollectionNotFoundException.class);
    }

    @Test
    void adoptGoal_rejectsLeafSource() {
        UUID userId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        NoteCollectionEntity sourcePlan = buildCollection(sourcePlanId, UUID.randomUUID(), COLLECTION_TITLE, Instant.now());
        sourcePlan.setVisibility(CollectionVisibility.PUBLIC);
        when(collectionRepository.findByIdAndVisibility(sourcePlanId, CollectionVisibility.PUBLIC))
                .thenReturn(Optional.of(sourcePlan));
        when(collectionRepository.countByParentCollectionId(sourcePlanId)).thenReturn(0L);

        assertThatThrownBy(() -> service.adoptGoal(sourcePlanId, userId))
                .isInstanceOf(CollectionNotFoundException.class);
    }

    @Test
    void delete_removesCollectionButDoesNotDeleteNotesDirectly() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        service.delete(collectionId, userId);

        verify(collectionRepository).delete(collection);
        verify(noteRepository, never()).delete(any(NoteEntity.class));
    }

    @Test
    void addItems_appendsAndSkipsAlreadyPresentNote() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID existingNoteId = UUID.randomUUID();
        UUID newNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity existingItem = buildItem(collectionId, existingNoteId, 0, null);
        NoteEntity existingNote = buildNote(existingNoteId, userId, NOTE_TITLE_ONE);
        NoteEntity newNote = buildNote(newNoteId, userId, NOTE_TITLE_TWO);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(existingNoteId, newNoteId)))
                .thenReturn(List.of(existingNote, newNote));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(existingItem));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(existingNoteId, newNoteId), List.of(existingNote, newNote));

        NoteCollectionDetailResponse result = service.addItems(collectionId, userId, new AddNoteCollectionItemsRequest(
                List.of(existingNoteId, newNoteId)
        ));

        assertThat(result.items()).extracting(item -> item.noteId()).containsExactly(existingNoteId, newNoteId);
        assertThat(result.items()).extracting(item -> item.position()).containsExactly(0, 1);
        verify(analyticsService, never()).trackEvent(any(), eq(AnalyticsEventType.COLLECTION_CREATED), any(), any());
    }

    @Test
    void addGeneratedItems_skipsNotesDeletedMidBatchInsteadOfDroppingAllOfThem() {
        // A bulk batch runs for minutes while its notes are already visible in the Library.
        // Deleting one made addItems throw on the first unresolvable id, so NONE of the
        // others were added and the only signal was a server log.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID survivingNoteId = UUID.randomUUID();
        UUID deletedNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteEntity surviving = buildNote(survivingNoteId, userId, NOTE_TITLE_ONE);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.countByParentCollectionId(collectionId)).thenReturn(0L);
        // The filter pass sees both ids and resolves only the surviving one.
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(survivingNoteId, deletedNoteId)))
                .thenReturn(List.of(surviving));
        // addItems then re-resolves the already-filtered list.
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(survivingNoteId)))
                .thenReturn(List.of(surviving));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(survivingNoteId), List.of(surviving));

        int added = service.addGeneratedItems(
                collectionId,
                userId,
                List.of(survivingNoteId, deletedNoteId),
                WEEK_ONE_LABEL
        );

        assertThat(added).isEqualTo(1);
        ArgumentCaptor<List<NoteCollectionItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(NoteCollectionItemEntity::getNoteId)
                .containsExactly(survivingNoteId);
        assertThat(captor.getValue()).extracting(NoteCollectionItemEntity::getLabel)
                .containsExactly(WEEK_ONE_LABEL);
    }

    @Test
    void addGeneratedItems_withNullLabelKeepsItemsUnsectioned() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.countByParentCollectionId(collectionId)).thenReturn(0L);
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(noteId), List.of(note));

        service.addGeneratedItems(collectionId, userId, List.of(noteId), null);

        ArgumentCaptor<List<NoteCollectionItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(NoteCollectionItemEntity::getLabel).containsOnlyNulls();
    }

    @Test
    void addGeneratedItems_withOverLongLabelThrowsExistingValidationMessage() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        String overLongLabel = REPEATED_CHARACTER.repeat(121);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.countByParentCollectionId(collectionId)).thenReturn(0L);
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.addGeneratedItems(collectionId, userId, List.of(noteId), overLongLabel))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Collection item label must be 120 characters or fewer.");
        verify(itemRepository, never()).saveAll(anyList());
    }

    @Test
    void addItems_repeatingTheSameNoteDoesNotCreateDuplicateMembership() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        NoteCollectionItemEntity existingItem = buildItem(collectionId, noteId, 0, null);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(List.of(), List.of(existingItem));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(noteId), List.of(note));

        AddNoteCollectionItemsRequest request = new AddNoteCollectionItemsRequest(List.of(noteId));
        service.addItems(collectionId, userId, request);
        service.addItems(collectionId, userId, request);

        ArgumentCaptor<List<NoteCollectionItemEntity>> savedItems = ArgumentCaptor.forClass(List.class);
        verify(itemRepository, times(2)).saveAll(savedItems.capture());
        assertThat(savedItems.getAllValues()).extracting(List::size).containsExactly(1, 0);
    }

    @Test
    void addItems_withForeignNoteThrowsAndDoesNotPersist() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID foreignNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        AddNoteCollectionItemsRequest request = new AddNoteCollectionItemsRequest(List.of(foreignNoteId));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(foreignNoteId))).thenReturn(List.of());

        assertThatThrownBy(() -> service.addItems(collectionId, userId, request))
                .isInstanceOf(NoteNotFoundException.class);
        verify(itemRepository, never()).saveAll(anyList());
        verify(collectionRepository, never()).save(collection);
    }

    @Test
    void removeItem_removesOneAndCompactsRemainingPositions() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        UUID thirdNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity firstItem = buildItem(collectionId, firstNoteId, 0, null);
        NoteCollectionItemEntity secondItem = buildItem(collectionId, secondNoteId, 1, null);
        NoteCollectionItemEntity thirdItem = buildItem(collectionId, thirdNoteId, 2, null);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdAndNoteId(collectionId, secondNoteId)).thenReturn(Optional.of(secondItem));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(firstItem, thirdItem));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));

        service.removeItem(collectionId, userId, secondNoteId);

        verify(itemRepository).delete(secondItem);
        assertThat(firstItem.getPosition()).isZero();
        assertThat(thirdItem.getPosition()).isEqualTo(1);
        verify(itemRepository).saveAll(List.of(firstItem, thirdItem));
    }

    @Test
    void removeItem_missingItemThrows() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdAndNoteId(collectionId, noteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem(collectionId, userId, noteId))
                .isInstanceOf(CollectionItemNotFoundException.class);
    }

    @Test
    void setOrder_reordersAndRelabels() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity firstItem = buildItem(collectionId, firstNoteId, 0, null);
        NoteCollectionItemEntity secondItem = buildItem(collectionId, secondNoteId, 1, null);
        NoteEntity firstNote = buildNote(firstNoteId, userId, NOTE_TITLE_ONE);
        NoteEntity secondNote = buildNote(secondNoteId, userId, NOTE_TITLE_TWO);
        SetNoteCollectionOrderRequest request = new SetNoteCollectionOrderRequest(List.of(
                new SetNoteCollectionOrderRequest.OrderedItem(secondNoteId, WEEK_TWO_LABEL),
                new SetNoteCollectionOrderRequest.OrderedItem(firstNoteId, WEEK_ONE_LABEL)
        ));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(secondNoteId, firstNoteId)))
                .thenReturn(List.of(firstNote, secondNote));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(firstItem, secondItem));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(secondNoteId, firstNoteId), List.of(firstNote, secondNote));

        NoteCollectionDetailResponse result = service.setOrder(collectionId, userId, request);

        assertThat(result.items()).extracting(item -> item.noteId()).containsExactly(secondNoteId, firstNoteId);
        assertThat(result.items()).extracting(item -> item.label()).containsExactly(WEEK_TWO_LABEL, WEEK_ONE_LABEL);
        assertThat(result.items()).extracting(item -> item.position()).containsExactly(0, 1);
        verify(analyticsService, never()).trackEvent(any(), eq(AnalyticsEventType.COLLECTION_CREATED), any(), any());
    }

    @Test
    void setOrder_withMismatchedNoteSetThrows() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID existingNoteId = UUID.randomUUID();
        UUID addedNoteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        NoteCollectionItemEntity existingItem = buildItem(collectionId, existingNoteId, 0, null);
        NoteEntity existingNote = buildNote(existingNoteId, userId, NOTE_TITLE_ONE);
        NoteEntity addedNote = buildNote(addedNoteId, userId, NOTE_TITLE_TWO);
        SetNoteCollectionOrderRequest request = new SetNoteCollectionOrderRequest(List.of(
                new SetNoteCollectionOrderRequest.OrderedItem(existingNoteId, null),
                new SetNoteCollectionOrderRequest.OrderedItem(addedNoteId, null)
        ));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(existingNoteId, addedNoteId)))
                .thenReturn(List.of(existingNote, addedNote));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(existingItem));

        assertThatThrownBy(() -> service.setOrder(collectionId, userId, request))
                .isInstanceOf(InvalidCollectionRequestException.class);
    }

    @Test
    void blankTitleThrows() {
        CreateNoteCollectionRequest request = new CreateNoteCollectionRequest(WHITESPACE, null, null);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Collection title is required.");
    }

    @Test
    void overLongTitleThrows() {
        CreateNoteCollectionRequest request = new CreateNoteCollectionRequest(REPEATED_CHARACTER.repeat(151), null, null);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Collection title must be 150 characters or fewer.");
    }

    @Test
    void overLongLabelThrows() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, Instant.now());
        SetNoteCollectionOrderRequest request = new SetNoteCollectionOrderRequest(List.of(
                new SetNoteCollectionOrderRequest.OrderedItem(noteId, REPEATED_CHARACTER.repeat(121))
        ));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> service.setOrder(collectionId, userId, request))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Collection item label must be 120 characters or fewer.");
    }

    @Test
    void accessingAnotherUsersCollectionThrowsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(collectionId, userId))
                .isInstanceOf(CollectionNotFoundException.class);
    }

    @Test
    void oneNoteCanAppearInMultipleCollections() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID firstCollectionId = UUID.randomUUID();
        UUID secondCollectionId = UUID.randomUUID();
        NoteCollectionEntity firstCollection = buildCollection(firstCollectionId, userId, "First", Instant.now());
        NoteCollectionEntity secondCollection = buildCollection(secondCollectionId, userId, "Second", Instant.now());
        NoteEntity note = buildNote(noteId, userId, NOTE_TITLE_ONE);
        when(collectionRepository.findByIdAndOwnerUserId(firstCollectionId, userId)).thenReturn(Optional.of(firstCollection));
        when(collectionRepository.findByIdAndOwnerUserId(secondCollectionId, userId)).thenReturn(Optional.of(secondCollection));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(firstCollectionId)).thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(secondCollectionId)).thenReturn(List.of());
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailItemLoad(userId, List.of(noteId), List.of(note));

        service.addItems(firstCollectionId, userId, new AddNoteCollectionItemsRequest(List.of(noteId)));
        service.addItems(secondCollectionId, userId, new AddNoteCollectionItemsRequest(List.of(noteId)));

        ArgumentCaptor<List<NoteCollectionItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(items -> assertThat(items).hasSize(1));
    }

    @Test
    void mutationsCarryTransactionalAnnotation() throws NoSuchMethodException {
        Method createMethod = NoteCollectionService.class.getMethod("create", UUID.class, CreateNoteCollectionRequest.class);
        Method updateMethod = NoteCollectionService.class.getMethod("updateMetadata", UUID.class, UUID.class, UpdateNoteCollectionRequest.class);
        Method deleteMethod = NoteCollectionService.class.getMethod("delete", UUID.class, UUID.class);
        Method setPrimaryMethod = NoteCollectionService.class.getMethod("setPrimary", UUID.class, UUID.class);
        Method clearPrimaryMethod = NoteCollectionService.class.getMethod("clearPrimary", UUID.class);
        Method setCompanionMethod = NoteCollectionService.class.getMethod(
                "setCompanion",
                UUID.class,
                UUID.class,
                CompanionContent.class
        );
        Method clearCompanionMethod = NoteCollectionService.class.getMethod("clearCompanion", UUID.class, UUID.class);
        Method generateCompanionMethod = NoteCollectionService.class.getMethod(
                "generateCompanion",
                UUID.class,
                UUID.class,
                GenerateCompanionRequest.class
        );
        Method clearTargetDateMethod = NoteCollectionService.class.getMethod("clearTargetDate", UUID.class, UUID.class);
        Method addMethod = NoteCollectionService.class.getMethod("addItems", UUID.class, UUID.class, AddNoteCollectionItemsRequest.class);
        Method removeMethod = NoteCollectionService.class.getMethod("removeItem", UUID.class, UUID.class, UUID.class);
        Method orderMethod = NoteCollectionService.class.getMethod("setOrder", UUID.class, UUID.class, SetNoteCollectionOrderRequest.class);
        Method childrenOrderMethod = NoteCollectionService.class.getMethod(
                "setChildrenOrder",
                UUID.class,
                UUID.class,
                SetNoteCollectionChildrenOrderRequest.class
        );

        assertThat(createMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(updateMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(deleteMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(setPrimaryMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(clearPrimaryMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(setCompanionMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(clearCompanionMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(generateCompanionMethod.getAnnotation(Transactional.class)).isNull();
        assertThat(clearTargetDateMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(addMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(removeMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(orderMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(childrenOrderMethod.getAnnotation(Transactional.class)).isNotNull();
    }

    private void stubDetailItemLoad(UUID userId, List<UUID> noteIds, List<NoteEntity> notes) {
        when(noteRepository.findCollectionNoteProjectionsByIdIn(noteIds)).thenReturn(asNoteProjections(notes));
        when(studyPackRepository.findProgressViewsByNoteIdIn(noteIds)).thenReturn(List.of());
        when(generatedQuizRepository.findNoteIdsByOwnerUserIdAndNoteIdIn(userId, noteIds)).thenReturn(List.of());
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, noteIds)).thenReturn(Map.of());
    }

    private record ChildReadiness(
            UUID collectionId,
            String title,
            int masteredConcepts,
            int dueConcepts,
            int notPracticedConcepts,
            int totalConcepts
    ) {
    }

    private void stubGoalReadiness(
            UUID userId,
            UUID goalId,
            LocalDate targetCompletionDate,
            List<ChildReadiness> childReadiness
    ) {
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        goal.setTargetCompletionDate(targetCompletionDate);
        List<NoteCollectionEntity> children = new java.util.ArrayList<>();
        Map<UUID, ProgressReportService.SubjectProgressBatchResult> progressByChildId = new java.util.LinkedHashMap<>();
        for (int index = 0; index < childReadiness.size(); index++) {
            ChildReadiness readiness = childReadiness.get(index);
            NoteCollectionEntity child = buildCollection(readiness.collectionId(), userId, readiness.title(), Instant.now());
            child.setParentCollectionId(goalId);
            child.setSiblingPosition(index);
            children.add(child);
            progressByChildId.put(
                    readiness.collectionId(),
                    new ProgressReportService.SubjectProgressBatchResult(
                            List.of(new SubjectProgressEntry(
                                    readiness.title(),
                                    readiness.totalConcepts(),
                                    readiness.masteredConcepts(),
                                    readiness.dueConcepts(),
                                    readiness.notPracticedConcepts(),
                                    masteryPercentage(readiness.masteredConcepts(), readiness.totalConcepts())
                            )),
                            null
                    )
            );
        }
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(children);
        when(itemRepository.countItemsByCollectionIds(children.stream().map(NoteCollectionEntity::getId).toList()))
                .thenReturn(List.of());
        when(itemRepository.findNoteIdsByCollectionIds(children.stream().map(NoteCollectionEntity::getId).toList()))
                .thenReturn(List.of());
        when(progressReportService.buildSubjectProgressEntriesByGroup(anyMap(), eq(userId), any(OffsetDateTime.class)))
                .thenReturn(progressByChildId);
        when(itemRepository.countByCollectionId(goalId)).thenReturn(0L);
    }

    private List<DayOfWeek> studyDaysForGoal(UUID userId, int studyDaysPerWeek) {
        UUID goalId = UUID.randomUUID();
        List<ChildReadiness> childReadiness = new java.util.ArrayList<>();
        for (int index = 0; index < studyDaysPerWeek; index++) {
            childReadiness.add(new ChildReadiness(UUID.randomUUID(), "Child " + index, 0, 0, 1, 1));
        }
        UserEntity user = buildUser(userId);
        user.setStudyDaysPerWeek(studyDaysPerWeek);
        stubGoalReadiness(userId, goalId, LocalDate.now().plusDays(7), childReadiness);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return service.getGoal(goalId, userId).weeklyFocusByDay().stream()
                .map(com.studysnap.backend.dto.WeeklyFocusDayEntry::dayOfWeek)
                .toList();
    }

    private int masteryPercentage(int masteredConcepts, int totalConcepts) {
        if (totalConcepts == 0) {
            return 0;
        }
        return (int) Math.round(masteredConcepts * 100.0 / totalConcepts);
    }

    private NoteCollectionEntity buildCollection(UUID id, UUID userId, String title, Instant updatedAt) {
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(id);
        collection.setOwnerUserId(userId);
        collection.setTitle(title);
        collection.setDescription(COLLECTION_DESCRIPTION);
        collection.setCreatedAt(updatedAt.minusSeconds(60));
        collection.setUpdatedAt(updatedAt);
        return collection;
    }

    private UserEntity buildUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUpdatedAt(OffsetDateTime.parse("2026-04-01T01:00:00Z"));
        return user;
    }

    private CompanionContent companionContent() {
        return new CompanionContent(
                "Overview",
                "Study strategy",
                "Common mistakes",
                "- [Official guide](https://example.com/guide)",
                List.of(new CompanionFaqItem("Question?", "Answer.")),
                List.of(new CompanionMentorTip(
                        UUID.fromString("00000000-0000-0000-0000-000000000043"),
                        "Start with one subject",
                        "Open the next subject plan and make one concrete pass today.",
                        CompanionMentorTipAction.CONTINUE_STUDYING,
                        new CompanionMentorTipSurfacingCondition(
                                CompanionMentorTipSurfacingConditionType.AFTER_SUBJECTS_COMPLETED,
                                1
                        )
                ))
        );
    }

    private NoteCollectionItemEntity buildItem(UUID collectionId, UUID noteId, int position, String label) {
        NoteCollectionItemEntity item = new NoteCollectionItemEntity();
        item.setId(UUID.randomUUID());
        item.setCollectionId(collectionId);
        item.setNoteId(noteId);
        item.setPosition(position);
        item.setLabel(label);
        item.setCreatedAt(Instant.now());
        return item;
    }

    private NoteEntity buildNote(UUID noteId, UUID userId, String title) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(userId);
        note.setTitle(title);
        note.setSubject(BIOLOGY_SUBJECT);
        note.setCourseProgram(COURSE_PROGRAM);
        note.setTags(new String[0]);
        note.setContent("content");
        note.setStatus(NoteStatus.DRAFT);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setTargetProfileType(NoteTargetProfileType.STUDENT);
        note.setCreatedAt(OffsetDateTime.parse(BASE_TIMESTAMP));
        note.setUpdatedAt(OffsetDateTime.parse("2026-04-01T01:00:00Z"));
        return note;
    }

    private StudyPackEntity buildStudyPack(UUID noteId) {
        return buildStudyPack(noteId, List.of());
    }

    private StudyPackEntity buildStudyPack(UUID noteId, List<String> keyConcepts) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setNoteId(noteId);
        studyPack.setKeyConcepts(keyConcepts);
        studyPack.setStatus(StudyPackStatus.DONE);
        return studyPack;
    }

    private static List<NoteCollectionNoteProjection> asNoteProjections(NoteEntity... notes) {
        return java.util.Arrays.stream(notes)
                .map(NoteCollectionServiceTest::asNoteProjection)
                .toList();
    }

    private static List<NoteCollectionNoteProjection> asNoteProjections(List<NoteEntity> notes) {
        return notes.stream()
                .map(NoteCollectionServiceTest::asNoteProjection)
                .toList();
    }

    private static NoteCollectionNoteProjection asNoteProjection(NoteEntity note) {
        return new NoteCollectionNoteProjection(
                note.getId(),
                note.getTitle(),
                note.getSubject(),
                note.getCourseProgram(),
                note.getDomainContext(),
                note.getLearnerLevel(),
                note.getStatus(),
                note.getVisibility(),
                note.getUpdatedAt()
        );
    }

    // Test double for the JPA-proxy Spring Data generates for StudyPackRepository's projection
    // queries. Deliberately does NOT reuse StudyPackEntity: only Spring Data's own proxy is
    // allowed to implement StudyPackProgressProjection (see its Javadoc). Records compare by
    // value, so a second call with the same underlying entity's fields still matches an
    // eq(asProjections(...)) expectation elsewhere in a test.
    private static List<StudyPackProgressProjection> asProjections(StudyPackEntity... packs) {
        return java.util.Arrays.stream(packs)
                .map(pack -> (StudyPackProgressProjection) new TestStudyPackProgressView(
                        pack.getId(),
                        pack.getNoteId(),
                        pack.getOwnerUserId(),
                        pack.getSubject(),
                        pack.getKeyConcepts(),
                        pack.getStatus()
                ))
                .toList();
    }

    private record TestStudyPackProgressView(
            UUID id,
            UUID noteId,
            UUID ownerUserId,
            String subject,
            List<String> keyConcepts,
            StudyPackStatus status
    ) implements StudyPackProgressProjection {
        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public UUID getNoteId() {
            return noteId;
        }

        @Override
        public UUID getOwnerUserId() {
            return ownerUserId;
        }

        @Override
        public String getSubject() {
            return subject;
        }

        @Override
        public List<String> getKeyConcepts() {
            return keyConcepts;
        }

        @Override
        public StudyPackStatus getStatus() {
            return status;
        }
    }

    private GeneratedQuizEntity buildGeneratedQuiz(UUID noteId, UUID userId) {
        GeneratedQuizEntity generatedQuiz = new GeneratedQuizEntity();
        generatedQuiz.setId(UUID.randomUUID());
        generatedQuiz.setNoteId(noteId);
        generatedQuiz.setOwnerUserId(userId);
        generatedQuiz.setGeneratedAt(OffsetDateTime.parse(QUIZ_TIMESTAMP));
        generatedQuiz.setUpdatedAt(OffsetDateTime.parse(QUIZ_TIMESTAMP));
        generatedQuiz.setQuestions(List.of());
        return generatedQuiz;
    }

    private static List<GeneratedQuizNoteProjection> asGeneratedQuizProjections(GeneratedQuizEntity... generatedQuizzes) {
        return java.util.Arrays.stream(generatedQuizzes)
                .map(generatedQuiz -> new GeneratedQuizNoteProjection(generatedQuiz.getNoteId(), generatedQuiz.getId()))
                .toList();
    }

    private NoteResponse noteResponse(UUID noteId) {
        return new NoteResponse(
                noteId.toString(),
                NOTE_TITLE_ONE,
                BIOLOGY_SUBJECT,
                COURSE_PROGRAM,
                null,
                null,
                List.of(),
                "content",
                NoteVisibility.PRIVATE.name(),
                OffsetDateTime.parse(BASE_TIMESTAMP),
                OffsetDateTime.parse(BASE_TIMESTAMP),
                null,
                null,
                null,
                null,
                null,
                null,
                NoteStudyPackStatusResolver.DRAFT,
                null,
                List.of(),
                List.of(),
                null,
                null,
                false,
                false,
                false
        );
    }

    private NoteCollectionItemCountProjection countProjection(UUID collectionId, long itemCount) {
        return new NoteCollectionItemCountProjection() {
            @Override
            public UUID getCollectionId() {
                return collectionId;
            }

            @Override
            public long getItemCount() {
                return itemCount;
            }
        };
    }

    private NoteCollectionChildCountProjection childCountProjection(UUID collectionId, long childCount) {
        return new NoteCollectionChildCountProjection() {
            @Override
            public UUID getCollectionId() {
                return collectionId;
            }

            @Override
            public long getChildCount() {
                return childCount;
            }
        };
    }

    private NoteCollectionItemNoteProjection noteProjection(UUID collectionId, UUID noteId) {
        return new NoteCollectionItemNoteProjection() {
            @Override
            public UUID getCollectionId() {
                return collectionId;
            }

            @Override
            public UUID getNoteId() {
                return noteId;
            }
        };
    }
}
