package com.studysnap.backend.service;

import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.CreateNoteCollectionRequest;
import com.studysnap.backend.dto.NoteCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.SetNoteCollectionOrderRequest;
import com.studysnap.backend.dto.UpdateNoteCollectionRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
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
import com.studysnap.backend.exception.InvalidCollectionRequestException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteCollectionItemCountProjection;
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
import org.springframework.transaction.annotation.Transactional;

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
import static org.mockito.ArgumentMatchers.anyList;
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
    private AnalyticsService analyticsService;

    private NoteCollectionService service;

    @BeforeEach
    void setUp() {
        service = new NoteCollectionService(
                collectionRepository,
                itemRepository,
                noteRepository,
                studyPackRepository,
                generatedQuizRepository,
                analyticsService
        );
    }

    @Test
    void create_withNoItemsSavesCollection() {
        UUID userId = UUID.randomUUID();
        when(collectionRepository.save(any(NoteCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        NoteCollectionDetailResponse result = service.create(userId, new CreateNoteCollectionRequest(
                "  " + COLLECTION_TITLE + "  ",
                "  " + COLLECTION_DESCRIPTION + "  ",
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
        NoteCollectionEntity newer = buildCollection(UUID.randomUUID(), userId, "Newer", Instant.parse("2026-04-02T00:00:00Z"));
        NoteCollectionEntity older = buildCollection(UUID.randomUUID(), userId, "Older", Instant.parse("2026-04-01T00:00:00Z"));
        when(collectionRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of(newer, older));
        when(itemRepository.countItemsByCollectionIds(List.of(newer.getId(), older.getId())))
                .thenReturn(List.of(countProjection(newer.getId(), 2), countProjection(older.getId(), 1)));

        List<NoteCollectionSummaryResponse> result = service.list(userId);

        assertThat(result).extracting(NoteCollectionSummaryResponse::title).containsExactly("Newer", "Older");
        assertThat(result).extracting(NoteCollectionSummaryResponse::itemCount).containsExactly(2, 1);
        verify(collectionRepository).findByOwnerUserIdOrderByUpdatedAtDesc(userId);
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

        NoteCollectionDetailResponse result = service.get(collectionId, userId);

        assertThat(result.items()).extracting(item -> item.noteId()).containsExactly(firstNoteId, secondNoteId);
        assertThat(result.items().get(0).studyPackStatus()).isEqualTo(NoteStudyPackStatusResolver.DRAFT);
        assertThat(result.items().get(1).studyPackStatus()).isEqualTo(NoteStudyPackStatusResolver.STUDY_PACK_READY);
        assertThat(result.items().get(1).generatedQuizId()).isEqualTo(generatedQuiz.getId().toString());
    }

    @Test
    void updateMetadata_changesTitleDescriptionAndBumpsUpdatedAt() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        Instant previousUpdatedAt = Instant.parse("2026-04-01T00:00:00Z");
        NoteCollectionEntity collection = buildCollection(collectionId, userId, COLLECTION_TITLE, previousUpdatedAt);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of());

        NoteCollectionDetailResponse result = service.updateMetadata(collectionId, userId, new UpdateNoteCollectionRequest(
                "Updated",
                "Updated description"
        ));

        assertThat(result.title()).isEqualTo("Updated");
        assertThat(result.description()).isEqualTo("Updated description");
        assertThat(result.updatedAt()).isAfter(previousUpdatedAt);
        verify(analyticsService, never()).trackEvent(any(), eq(AnalyticsEventType.COLLECTION_CREATED), any(), any());
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
        CreateNoteCollectionRequest request = new CreateNoteCollectionRequest("  ", null, null);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessage("Collection title is required.");
    }

    @Test
    void overLongTitleThrows() {
        CreateNoteCollectionRequest request = new CreateNoteCollectionRequest("a".repeat(151), null, null);

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
                new SetNoteCollectionOrderRequest.OrderedItem(noteId, "a".repeat(121))
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

        assertThat(createMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(updateMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(deleteMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(addMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(removeMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(orderMethod.getAnnotation(Transactional.class)).isNotNull();
    }

    private void stubDetailItemLoad(UUID userId, List<UUID> noteIds, List<NoteEntity> notes) {
        when(noteRepository.findAllById(noteIds)).thenReturn(notes);
        when(studyPackRepository.findByNoteIdIn(noteIds)).thenReturn(List.of());
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, noteIds)).thenReturn(List.of());
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
        note.setCreatedAt(OffsetDateTime.parse("2026-04-01T00:00:00Z"));
        note.setUpdatedAt(OffsetDateTime.parse("2026-04-01T01:00:00Z"));
        return note;
    }

    private StudyPackEntity buildStudyPack(UUID noteId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setNoteId(noteId);
        studyPack.setStatus(StudyPackStatus.DONE);
        return studyPack;
    }

    private GeneratedQuizEntity buildGeneratedQuiz(UUID noteId, UUID userId) {
        GeneratedQuizEntity generatedQuiz = new GeneratedQuizEntity();
        generatedQuiz.setId(UUID.randomUUID());
        generatedQuiz.setNoteId(noteId);
        generatedQuiz.setOwnerUserId(userId);
        generatedQuiz.setGeneratedAt(OffsetDateTime.parse("2026-04-01T02:00:00Z"));
        generatedQuiz.setUpdatedAt(OffsetDateTime.parse("2026-04-01T02:00:00Z"));
        generatedQuiz.setQuestions(List.of());
        return generatedQuiz;
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
}
