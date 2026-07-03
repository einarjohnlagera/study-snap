package com.studysnap.backend.service;

import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.AdoptGoalResponse;
import com.studysnap.backend.dto.AdoptStudyPlanResponse;
import com.studysnap.backend.dto.CreateNoteCollectionRequest;
import com.studysnap.backend.dto.GoalCollectionChildResponse;
import com.studysnap.backend.dto.GoalCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionDetailResponse;
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
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.CollectionItemNotFoundException;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.exception.CollectionNotPublishableException;
import com.studysnap.backend.exception.InvalidCollectionRequestException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteCollectionChildCountProjection;
import com.studysnap.backend.repository.NoteCollectionItemCountProjection;
import com.studysnap.backend.repository.NoteCollectionItemNoteProjection;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
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

    private NoteCollectionService service;

    @BeforeEach
    void setUp() {
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
        when(noteRepository.findAllById(List.of(firstNoteId, secondNoteId))).thenReturn(List.of(firstNote, secondNote));
        when(studyPackRepository.findByNoteIdIn(List.of(firstNoteId, secondNoteId))).thenReturn(List.of(studyPack));
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, List.of(firstNoteId, secondNoteId)))
                .thenReturn(List.of(generatedQuiz));
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
        when(noteRepository.findAllById(noteIds)).thenReturn(notes);
        when(studyPackRepository.findByNoteIdIn(noteIds))
                .thenReturn(List.of(buildStudyPack(firstNoteId), buildStudyPack(thirdNoteId)));
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, noteIds)).thenReturn(List.of());
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
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.get(collectionId, userId);

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
        when(studyPackRepository.findByNoteIdIn(List.of(draftNoteId, biologyNoteId, otherNoteId)))
                .thenReturn(List.of(biologyPack, otherPack, foreignPack));
        when(progressReportService.buildSubjectProgressEntries(eq(List.of(biologyPack, otherPack)), eq(userId), any(OffsetDateTime.class)))
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
        verify(studyPackRepository, never()).findByNoteIdIn(anyList());
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
        when(studyPackRepository.findByNoteIdIn(List.of(noteId))).thenReturn(List.of());

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
        when(collectionRepository.findByIdAndOwnerUserId(firstChildId, userId)).thenReturn(Optional.of(firstChild));
        when(collectionRepository.findByIdAndOwnerUserId(secondChildId, userId)).thenReturn(Optional.of(secondChild));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(firstChildId))
                .thenReturn(List.of(buildItem(firstChildId, firstNoteId, 0, null)));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(secondChildId))
                .thenReturn(List.of(buildItem(secondChildId, secondNoteId, 0, null)));
        when(studyPackRepository.findByNoteIdIn(List.of(firstNoteId))).thenReturn(List.of(firstPack));
        when(studyPackRepository.findByNoteIdIn(List.of(secondNoteId))).thenReturn(List.of(secondPack));
        when(progressReportService.buildSubjectProgressEntries(eq(List.of(firstPack)), eq(userId), any(OffsetDateTime.class)))
                .thenReturn(List.of(new SubjectProgressEntry("Professional Education", 2, 1, 1, 0, 50)));
        when(progressReportService.buildSubjectProgressEntries(eq(List.of(secondPack)), eq(userId), any(OffsetDateTime.class)))
                .thenReturn(List.of(new SubjectProgressEntry("General Education", 2, 1, 0, 1, 50)));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of());

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.childCount()).isEqualTo(2);
        assertThat(result.totalConcepts()).isEqualTo(4);
        assertThat(result.masteredConcepts()).isEqualTo(2);
        assertThat(result.overallReadinessPercentage()).isEqualTo(50);
        assertThat(result.children()).extracting(GoalCollectionChildResponse::totalConcepts)
                .containsExactly(2, 2);
    }

    @Test
    void getGoal_returnsZeroShapeForEmptyGoal() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        NoteCollectionEntity goal = buildCollection(goalId, userId, "LET Mastery", Instant.now());
        when(collectionRepository.findByIdAndOwnerUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(goalId, userId))
                .thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of());

        GoalCollectionDetailResponse result = service.getGoal(goalId, userId);

        assertThat(result.childCount()).isZero();
        assertThat(result.totalConcepts()).isZero();
        assertThat(result.overallReadinessPercentage()).isZero();
        assertThat(result.children()).isEmpty();
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
        when(collectionRepository.findByIdAndOwnerUserId(firstChildId, userId)).thenReturn(Optional.of(firstChild));
        when(collectionRepository.findByIdAndOwnerUserId(secondChildId, userId)).thenReturn(Optional.of(secondChild));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(firstChildId)).thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(secondChildId)).thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of());

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
        when(collectionRepository.findByIdAndOwnerUserId(secondChildId, userId)).thenReturn(Optional.of(secondChild));
        when(collectionRepository.findByIdAndOwnerUserId(firstChildId, userId)).thenReturn(Optional.of(firstChild));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(secondChildId)).thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(firstChildId)).thenReturn(List.of());
        when(itemRepository.findByCollectionIdOrderByPositionAsc(goalId)).thenReturn(List.of());

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
        when(noteRepository.findAllById(List.of(noteId))).thenReturn(List.of(note));
        when(studyPackRepository.findByNoteIdIn(List.of(noteId))).thenReturn(List.of());
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, List.of(noteId))).thenReturn(List.of());
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
        when(noteRepository.findAllById(noteIds)).thenReturn(notes);
        when(studyPackRepository.findByNoteIdIn(noteIds)).thenReturn(List.of(dueStudyPack, currentStudyPack));
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, noteIds)).thenReturn(List.of());
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
        when(noteRepository.findAllById(List.of(noteId))).thenReturn(List.of(note));
        when(studyPackRepository.findByNoteIdIn(List.of(noteId))).thenReturn(List.of(studyPack));
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, List.of(noteId))).thenReturn(List.of());
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
        when(noteRepository.findAllById(List.of(noteId))).thenReturn(List.of(note));
        when(studyPackRepository.findByNoteIdIn(List.of(noteId))).thenReturn(List.of(studyPack));
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, List.of(noteId))).thenReturn(List.of());
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
                3
        ));

        assertThat(result.title()).isEqualTo(UPDATED_COLLECTION_TITLE);
        assertThat(result.description()).isEqualTo(UPDATED_COLLECTION_DESCRIPTION);
        assertThat(result.courseProgram()).isEqualTo(UPDATED_COURSE_PROGRAM);
        assertThat(result.estimatedStudyHours()).isEqualTo(3);
        assertThat(result.updatedAt()).isAfter(previousUpdatedAt);
        verify(analyticsService, never()).trackEvent(any(), eq(AnalyticsEventType.COLLECTION_CREATED), any(), any());
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
                null
        ));

        assertThat(result.description()).isNull();
        assertThat(collection.getDescription()).isNull();
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
        when(studyPackRepository.findByNoteIdIn(List.of(firstNoteId, noPackNoteId, secondNoteId)))
                .thenReturn(List.of(firstPack, secondPack));
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
        when(studyPackRepository.findByNoteIdIn(List.of(noteId))).thenReturn(List.of());

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
        assertThat(result.getFirst().childCount()).isEqualTo(1);
        assertThat(result.getFirst().notesPracticed()).isZero();
        verify(collectionRepository).findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
                CollectionVisibility.PUBLIC,
                UPDATED_COURSE_PROGRAM
        );
        verify(quizSessionHistoryService, never()).findLatestSessionCompletedAtByNoteIds(any(), any());
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
        NoteCollectionEntity sourceGoal = buildCollection(sourceGoalId, sourceOwnerId, "LET Mastery", Instant.now());
        sourceGoal.setVisibility(CollectionVisibility.PUBLIC);
        sourceGoal.setCourseProgram(UPDATED_COURSE_PROGRAM);
        sourceGoal.setEstimatedStudyHours(3);
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

        when(collectionRepository.findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC)).thenReturn(Optional.of(sourceGoal));
        when(collectionRepository.countByParentCollectionId(sourceGoalId)).thenReturn(2L);
        when(collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(sourceGoalId, sourceOwnerId))
                .thenReturn(List.of(firstChild, secondChild));
        when(collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourceGoalId)).thenReturn(Optional.empty());
        when(collectionRepository.saveAndFlush(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        assertThat(personalFirstChild.getParentCollectionId()).isEqualTo(result.goalCollectionId());
        assertThat(personalSecondChild.getParentCollectionId()).isEqualTo(result.goalCollectionId());
        assertThat(personalFirstChild.getSiblingPosition()).isZero();
        assertThat(personalSecondChild.getSiblingPosition()).isEqualTo(1);
        ArgumentCaptor<NoteCollectionEntity> goalCaptor = ArgumentCaptor.forClass(NoteCollectionEntity.class);
        verify(collectionRepository, times(3)).saveAndFlush(goalCaptor.capture());
        assertThat(goalCaptor.getAllValues().getFirst().getEstimatedStudyHours()).isEqualTo(3);
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
        assertThat(addMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(removeMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(orderMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(childrenOrderMethod.getAnnotation(Transactional.class)).isNotNull();
    }

    private void stubDetailItemLoad(UUID userId, List<UUID> noteIds, List<NoteEntity> notes) {
        when(noteRepository.findAllById(noteIds)).thenReturn(notes);
        when(studyPackRepository.findByNoteIdIn(noteIds)).thenReturn(List.of());
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, noteIds)).thenReturn(List.of());
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, noteIds)).thenReturn(Map.of());
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

    private NoteResponse noteResponse(UUID noteId) {
        return new NoteResponse(
                noteId.toString(),
                NOTE_TITLE_ONE,
                BIOLOGY_SUBJECT,
                COURSE_PROGRAM,
                NoteTargetProfileType.STUDENT.name(),
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
