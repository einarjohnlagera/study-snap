package com.studysnap.backend.service;

import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.CreateNoteCollectionRequest;
import com.studysnap.backend.dto.NoteCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionItemResponse;
import com.studysnap.backend.dto.NoteCollectionProgressResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.SetNoteCollectionOrderRequest;
import com.studysnap.backend.dto.UpdateNoteCollectionRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteCollectionService {

    private static final int TITLE_MAX_LENGTH = 150;
    private static final int LABEL_MAX_LENGTH = 120;
    private static final int DUE_CONCEPT_DISPLAY_LIMIT = 3;
    private static final String TITLE_REQUIRED_MESSAGE = "Collection title is required.";
    private static final String TITLE_TOO_LONG_MESSAGE = "Collection title must be 150 characters or fewer.";
    private static final String LABEL_TOO_LONG_MESSAGE = "Collection item label must be 120 characters or fewer.";
    private static final String NOTE_ID_REQUIRED_MESSAGE = "Collection item note id is required.";
    private static final String ORDER_SET_MISMATCH_MESSAGE = "Collection order must include exactly the current collection notes.";
    private static final String ITEM_COUNT_METADATA_KEY = "itemCount";

    private final NoteCollectionRepository collectionRepository;
    private final NoteCollectionItemRepository itemRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final QuizSessionHistoryService quizSessionHistoryService;
    private final ConceptHealthService conceptHealthService;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public List<NoteCollectionSummaryResponse> list(UUID userId) {
        List<NoteCollectionEntity> collections = collectionRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId);
        if (collections.isEmpty()) {
            return List.of();
        }
        Map<UUID, Integer> itemCountsByCollectionId = loadItemCounts(collections);
        return collections.stream()
                .map(collection -> toSummaryResponse(
                        collection,
                        itemCountsByCollectionId.getOrDefault(collection.getId(), 0)
                ))
                .toList();
    }

    @Transactional
    public NoteCollectionDetailResponse create(UUID userId, CreateNoteCollectionRequest request) {
        String title = validateRequiredTitle(request == null ? null : request.title());
        String description = normalizeOptionalText(request == null ? null : request.description());
        List<UUID> orderedNoteIds = dedupeNoteIds(request == null ? null : request.noteIds());
        loadOwnedNotesByIdOrThrow(userId, orderedNoteIds);

        Instant now = Instant.now();
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(UUID.randomUUID());
        collection.setOwnerUserId(userId);
        collection.setTitle(title);
        collection.setDescription(description);
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);
        NoteCollectionEntity saved = collectionRepository.save(collection);

        List<NoteCollectionItemEntity> items = buildItems(saved.getId(), orderedNoteIds, 0, now);
        itemRepository.saveAll(items);
        analyticsService.trackEvent(
                userId,
                AnalyticsEventType.COLLECTION_CREATED,
                saved.getId(),
                Map.of(ITEM_COUNT_METADATA_KEY, items.size())
        );
        return toDetailResponse(saved, items);
    }

    @Transactional(readOnly = true)
    public NoteCollectionDetailResponse get(UUID collectionId, UUID userId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        return toDetailResponse(collection, items);
    }

    @Transactional
    public NoteCollectionDetailResponse updateMetadata(UUID collectionId, UUID userId, UpdateNoteCollectionRequest request) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        if (request != null && request.title() != null) {
            collection.setTitle(validateRequiredTitle(request.title()));
        }
        if (request != null) {
            collection.setDescription(normalizeOptionalText(request.description()));
        }
        touch(collection);
        NoteCollectionEntity saved = collectionRepository.save(collection);
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        return toDetailResponse(saved, items);
    }

    @Transactional
    public void delete(UUID collectionId, UUID userId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        collectionRepository.delete(collection);
    }

    @Transactional
    public NoteCollectionDetailResponse addItems(UUID collectionId, UUID userId, AddNoteCollectionItemsRequest request) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        List<UUID> orderedNoteIds = dedupeNoteIds(request == null ? null : request.noteIds());
        loadOwnedNotesByIdOrThrow(userId, orderedNoteIds);

        List<NoteCollectionItemEntity> currentItems = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        Set<UUID> existingNoteIds = currentItems.stream()
                .map(NoteCollectionItemEntity::getNoteId)
                .collect(Collectors.toSet());
        List<UUID> newNoteIds = orderedNoteIds.stream()
                .filter(noteId -> !existingNoteIds.contains(noteId))
                .toList();

        Instant now = Instant.now();
        int nextPosition = currentItems.stream()
                .mapToInt(NoteCollectionItemEntity::getPosition)
                .max()
                .orElse(-1) + 1;
        List<NoteCollectionItemEntity> newItems = buildItems(collectionId, newNoteIds, nextPosition, now);
        itemRepository.saveAll(newItems);
        touch(collection, now);
        NoteCollectionEntity saved = collectionRepository.save(collection);

        List<NoteCollectionItemEntity> allItems = new ArrayList<>(currentItems);
        allItems.addAll(newItems);
        allItems.sort((left, right) -> Integer.compare(left.getPosition(), right.getPosition()));
        return toDetailResponse(saved, allItems);
    }

    @Transactional
    public void removeItem(UUID collectionId, UUID userId, UUID noteId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        NoteCollectionItemEntity item = itemRepository.findByCollectionIdAndNoteId(collectionId, noteId)
                .orElseThrow(CollectionItemNotFoundException::new);
        itemRepository.delete(item);

        List<NoteCollectionItemEntity> remainingItems = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId).stream()
                .filter(remainingItem -> !remainingItem.getId().equals(item.getId()))
                .toList();
        rewritePositions(remainingItems);
        itemRepository.saveAll(remainingItems);
        touch(collection);
        collectionRepository.save(collection);
    }

    @Transactional
    public NoteCollectionDetailResponse setOrder(UUID collectionId, UUID userId, SetNoteCollectionOrderRequest request) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        List<SetNoteCollectionOrderRequest.OrderedItem> submittedItems = request == null || request.items() == null
                ? List.of()
                : request.items();
        List<UUID> submittedNoteIds = extractSubmittedNoteIds(submittedItems);
        loadOwnedNotesByIdOrThrow(userId, submittedNoteIds);

        List<NoteCollectionItemEntity> currentItems = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        validateSubmittedSetMatchesCurrent(currentItems, submittedNoteIds);

        Map<UUID, NoteCollectionItemEntity> itemByNoteId = currentItems.stream()
                .collect(Collectors.toMap(NoteCollectionItemEntity::getNoteId, Function.identity()));
        for (int index = 0; index < submittedItems.size(); index++) {
            SetNoteCollectionOrderRequest.OrderedItem submittedItem = submittedItems.get(index);
            NoteCollectionItemEntity item = itemByNoteId.get(submittedItem.noteId());
            item.setPosition(index);
            item.setLabel(validateOptionalLabel(submittedItem.label()));
        }
        itemRepository.saveAll(currentItems);
        touch(collection);
        NoteCollectionEntity saved = collectionRepository.save(collection);
        List<NoteCollectionItemEntity> orderedItems = new ArrayList<>(currentItems);
        orderedItems.sort((left, right) -> Integer.compare(left.getPosition(), right.getPosition()));
        return toDetailResponse(saved, orderedItems);
    }

    private NoteCollectionEntity getOwnedCollectionOrThrow(UUID collectionId, UUID userId) {
        return collectionRepository.findByIdAndOwnerUserId(collectionId, userId)
                .orElseThrow(CollectionNotFoundException::new);
    }

    private Map<UUID, Integer> loadItemCounts(List<NoteCollectionEntity> collections) {
        List<UUID> collectionIds = collections.stream().map(NoteCollectionEntity::getId).toList();
        Map<UUID, Integer> countsByCollectionId = new HashMap<>();
        for (NoteCollectionItemCountProjection projection : itemRepository.countItemsByCollectionIds(collectionIds)) {
            countsByCollectionId.put(projection.getCollectionId(), Math.toIntExact(projection.getItemCount()));
        }
        return countsByCollectionId;
    }

    private Map<UUID, NoteEntity> loadOwnedNotesByIdOrThrow(UUID userId, List<UUID> noteIds) {
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        List<NoteEntity> ownedNotes = noteRepository.findByOwnerUserIdAndIdIn(userId, noteIds);
        Map<UUID, NoteEntity> notesById = ownedNotes.stream()
                .collect(Collectors.toMap(NoteEntity::getId, Function.identity()));
        for (UUID noteId : noteIds) {
            if (!notesById.containsKey(noteId)) {
                throw new NoteNotFoundException();
            }
        }
        return notesById;
    }

    private List<UUID> dedupeNoteIds(List<UUID> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> deduped = new LinkedHashSet<>();
        for (UUID noteId : noteIds) {
            if (noteId == null) {
                throw new InvalidCollectionRequestException(NOTE_ID_REQUIRED_MESSAGE);
            }
            deduped.add(noteId);
        }
        return List.copyOf(deduped);
    }

    private List<UUID> extractSubmittedNoteIds(List<SetNoteCollectionOrderRequest.OrderedItem> submittedItems) {
        List<UUID> noteIds = new ArrayList<>();
        Set<UUID> seenNoteIds = new HashSet<>();
        for (SetNoteCollectionOrderRequest.OrderedItem item : submittedItems) {
            if (item == null || item.noteId() == null) {
                throw new InvalidCollectionRequestException(NOTE_ID_REQUIRED_MESSAGE);
            }
            validateOptionalLabel(item.label());
            if (!seenNoteIds.add(item.noteId())) {
                throw new InvalidCollectionRequestException(ORDER_SET_MISMATCH_MESSAGE);
            }
            noteIds.add(item.noteId());
        }
        return noteIds;
    }

    private void validateSubmittedSetMatchesCurrent(
            List<NoteCollectionItemEntity> currentItems,
            List<UUID> submittedNoteIds
    ) {
        Set<UUID> currentNoteIds = currentItems.stream()
                .map(NoteCollectionItemEntity::getNoteId)
                .collect(Collectors.toSet());
        Set<UUID> submittedNoteIdSet = new HashSet<>(submittedNoteIds);
        if (currentItems.size() != submittedNoteIds.size() || !currentNoteIds.equals(submittedNoteIdSet)) {
            throw new InvalidCollectionRequestException(ORDER_SET_MISMATCH_MESSAGE);
        }
    }

    private List<NoteCollectionItemEntity> buildItems(
            UUID collectionId,
            List<UUID> noteIds,
            int startingPosition,
            Instant now
    ) {
        List<NoteCollectionItemEntity> items = new ArrayList<>();
        for (int index = 0; index < noteIds.size(); index++) {
            NoteCollectionItemEntity item = new NoteCollectionItemEntity();
            item.setId(UUID.randomUUID());
            item.setCollectionId(collectionId);
            item.setNoteId(noteIds.get(index));
            item.setLabel(null);
            item.setPosition(startingPosition + index);
            item.setCreatedAt(now);
            items.add(item);
        }
        return items;
    }

    private void rewritePositions(List<NoteCollectionItemEntity> items) {
        for (int index = 0; index < items.size(); index++) {
            items.get(index).setPosition(index);
        }
    }

    private String validateRequiredTitle(String rawTitle) {
        String title = normalizeOptionalText(rawTitle);
        if (title == null) {
            throw new InvalidCollectionRequestException(TITLE_REQUIRED_MESSAGE);
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new InvalidCollectionRequestException(TITLE_TOO_LONG_MESSAGE);
        }
        return title;
    }

    private String validateOptionalLabel(String rawLabel) {
        String label = normalizeOptionalText(rawLabel);
        if (label != null && label.length() > LABEL_MAX_LENGTH) {
            throw new InvalidCollectionRequestException(LABEL_TOO_LONG_MESSAGE);
        }
        return label;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void touch(NoteCollectionEntity collection) {
        touch(collection, Instant.now());
    }

    private void touch(NoteCollectionEntity collection, Instant now) {
        collection.setUpdatedAt(now);
    }

    private NoteCollectionSummaryResponse toSummaryResponse(NoteCollectionEntity collection, int itemCount) {
        return new NoteCollectionSummaryResponse(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                itemCount,
                collection.getCreatedAt(),
                collection.getUpdatedAt()
        );
    }

    private NoteCollectionDetailResponse toDetailResponse(
            NoteCollectionEntity collection,
            List<NoteCollectionItemEntity> items
    ) {
        List<NoteCollectionItemResponse> itemResponses = toItemResponses(collection.getOwnerUserId(), items);
        return new NoteCollectionDetailResponse(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getCreatedAt(),
                collection.getUpdatedAt(),
                toProgressResponse(itemResponses),
                itemResponses
        );
    }

    private NoteCollectionProgressResponse toProgressResponse(List<NoteCollectionItemResponse> items) {
        int notesWithStudyPack = (int) items.stream()
                .filter(item -> NoteStudyPackStatusResolver.STUDY_PACK_READY.equals(item.studyPackStatus()))
                .count();
        int notesPracticed = (int) items.stream()
                .filter(item -> item.lastSessionCompletedAt() != null)
                .count();
        return new NoteCollectionProgressResponse(items.size(), notesWithStudyPack, notesPracticed);
    }

    private List<NoteCollectionItemResponse> toItemResponses(UUID userId, List<NoteCollectionItemEntity> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<UUID> noteIds = items.stream().map(NoteCollectionItemEntity::getNoteId).toList();
        Map<UUID, NoteEntity> notesById = noteRepository.findAllById(noteIds).stream()
                .collect(Collectors.toMap(NoteEntity::getId, Function.identity()));
        Map<UUID, StudyPackEntity> studyPacksByNoteId = studyPackRepository.findByNoteIdIn(noteIds).stream()
                .filter(studyPack -> studyPack.getNoteId() != null)
                .collect(Collectors.toMap(StudyPackEntity::getNoteId, Function.identity(), (left, right) -> left));
        Map<UUID, GeneratedQuizEntity> generatedQuizzesByNoteId = generatedQuizRepository
                .findByOwnerUserIdAndNoteIdIn(userId, noteIds).stream()
                .filter(generatedQuiz -> generatedQuiz.getNoteId() != null)
                .collect(Collectors.toMap(GeneratedQuizEntity::getNoteId, Function.identity(), (left, right) -> left));
        Map<UUID, OffsetDateTime> lastSessionCompletedAtByNoteId = loadLastSessionCompletedAt(userId, noteIds);
        Map<UUID, List<String>> dueConceptsByStudyPackId = loadDueConceptsByStudyPackId(
                userId,
                studyPacksByNoteId.values()
        );

        return items.stream()
                .map(item -> toItemResponse(
                        item,
                        notesById.get(item.getNoteId()),
                        studyPacksByNoteId.get(item.getNoteId()),
                        generatedQuizzesByNoteId.get(item.getNoteId()),
                        lastSessionCompletedAtByNoteId.get(item.getNoteId()),
                        dueConceptsByStudyPackId
                ))
                .toList();
    }

    private Map<UUID, List<String>> loadDueConceptsByStudyPackId(
            UUID userId,
            Collection<StudyPackEntity> studyPacks
    ) {
        if (studyPacks.isEmpty()) {
            return Map.of();
        }
        try {
            if (!conceptHealthService.canViewConceptHealth(userId)) {
                return Map.of();
            }
            Map<UUID, List<String>> conceptsByStudyPackId = studyPacks.stream()
                    .collect(Collectors.toMap(
                            StudyPackEntity::getId,
                            studyPack -> studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                            (left, right) -> left
                    ));
            return conceptHealthService.getDueConceptsByStudyPackIds(
                    userId,
                    conceptsByStudyPackId,
                    OffsetDateTime.now()
            );
        } catch (RuntimeException exception) {
            log.warn("Could not load collection due concepts for user {}", userId, exception);
            return Map.of();
        }
    }

    private Map<UUID, OffsetDateTime> loadLastSessionCompletedAt(UUID userId, List<UUID> noteIds) {
        try {
            Map<UUID, OffsetDateTime> resolved = quizSessionHistoryService
                    .findLatestSessionCompletedAtByNoteIds(userId, noteIds);
            return resolved == null ? Map.of() : resolved;
        } catch (RuntimeException exception) {
            log.warn("Could not load collection practice timestamps for user {}", userId, exception);
            return Map.of();
        }
    }

    private NoteCollectionItemResponse toItemResponse(
            NoteCollectionItemEntity item,
            NoteEntity note,
            StudyPackEntity studyPack,
            GeneratedQuizEntity generatedQuiz,
            OffsetDateTime lastSessionCompletedAt,
            Map<UUID, List<String>> dueConceptsByStudyPackId
    ) {
        if (note == null) {
            throw new NoteNotFoundException();
        }
        List<String> dueConcepts = studyPack == null
                ? List.of()
                : dueConceptsByStudyPackId.getOrDefault(studyPack.getId(), List.of());
        return new NoteCollectionItemResponse(
                item.getNoteId(),
                item.getLabel(),
                item.getPosition(),
                note.getTitle(),
                note.getSubject(),
                note.getCourseProgram(),
                NoteStudyPackStatusResolver.resolve(note, studyPack),
                generatedQuiz == null ? null : generatedQuiz.getId().toString(),
                lastSessionCompletedAt,
                dueConcepts.size(),
                dueConcepts.stream().limit(DUE_CONCEPT_DISPLAY_LIMIT).toList(),
                note.getUpdatedAt()
        );
    }
}
