package com.studysnap.backend.service;

import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.AdoptGoalResponse;
import com.studysnap.backend.dto.AdoptStudyPlanResponse;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionMentorTip;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.CompanionStructureSnapshot;
import com.studysnap.backend.dto.CreateNoteCollectionRequest;
import com.studysnap.backend.dto.GenerateCompanionRequest;
import com.studysnap.backend.dto.GeneratedCompanionContentResponse;
import com.studysnap.backend.dto.GoalCollectionChildResponse;
import com.studysnap.backend.dto.GoalCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionItemResponse;
import com.studysnap.backend.dto.NoteCollectionProgressResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.NoteConceptCountsResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.PlanReadinessResponse;
import com.studysnap.backend.dto.SetNoteCollectionParentRequest;
import com.studysnap.backend.dto.SetNoteCollectionChildrenOrderRequest;
import com.studysnap.backend.dto.SetNoteCollectionOrderRequest;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.dto.UpdateNoteCollectionRequest;
import com.studysnap.backend.dto.WeeklyFocusDayEntry;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.CollectionItemNotFoundException;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.exception.CollectionNotPublishableException;
import com.studysnap.backend.exception.InvalidCollectionRequestException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.model.StudyPackProgressView;
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
import com.studysnap.backend.service.model.CompanionGenerationContext;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private static final int DEFAULT_STUDY_DAYS_PER_WEEK = 7;
    private static final String TITLE_REQUIRED_MESSAGE = "Collection title is required.";
    private static final String TITLE_TOO_LONG_MESSAGE = "Collection title must be 150 characters or fewer.";
    private static final String LABEL_TOO_LONG_MESSAGE = "Collection item label must be 120 characters or fewer.";
    private static final String NOTE_ID_REQUIRED_MESSAGE = "Collection item note id is required.";
    private static final String ORDER_SET_MISMATCH_MESSAGE = "Collection order must include exactly the current collection notes.";
    private static final String INVALID_VISIBILITY_MESSAGE = "Collection visibility must be PRIVATE or PUBLIC.";
    private static final String EMPTY_PUBLISH_MESSAGE = "Add at least one public note before publishing this study plan.";
    private static final String PRIVATE_NOTE_PUBLISH_MESSAGE = "Every note in a published study plan must be public.";
    private static final String EMPTY_GOAL_PUBLISH_MESSAGE = "A Goal must have at least one Subject plan before it can be published.";
    private static final String EMPTY_GOAL_CHILD_PUBLISH_MESSAGE = "All Subject plans must contain at least one public note before publishing a Goal.";
    private static final String PRIVATE_GOAL_NOTE_PUBLISH_MESSAGE = "All notes in all Subject plans must be public before publishing a Goal.";
    private static final String SELF_PARENT_MESSAGE = "A collection cannot be nested under itself.";
    private static final String PARENT_NOT_TOP_LEVEL_MESSAGE = "A collection can only be nested under a top-level goal.";
    private static final String CHILD_HAS_CHILDREN_MESSAGE = "A collection with child plans cannot be nested under another goal.";
    private static final String GOAL_CANNOT_ACCEPT_NOTES_MESSAGE = "A goal collection cannot contain direct notes.";
    private static final String PARENT_WITH_NOTES_MESSAGE = "A collection must be empty before it can become a goal.";
    private static final String CHILD_ORDER_SET_MISMATCH_MESSAGE = "Child order must include exactly the current child plans.";
    private static final String CHILD_ID_REQUIRED_MESSAGE = "Child collection id is required.";
    private static final String PRIMARY_REQUIRES_TOP_LEVEL_GOAL_MESSAGE = "Only a top-level Goal can be primary.";
    private static final String TARGET_DATE_REQUIRES_TOP_LEVEL_GOAL_MESSAGE = "Only a top-level Goal can have a target completion date.";
    private static final String COMPANION_REQUIRES_TOP_LEVEL_GOAL_MESSAGE = "Only a top-level Goal can have a Companion.";
    private static final String COMPANION_SECTION_REQUIRED_MESSAGE = "Select at least one Companion section to generate.";
    private static final String COMPANION_CONTENT_REQUIRED_MESSAGE = "Companion content is required.";
    private static final String COMPANION_MENTOR_TIP_CONDITION_TYPE_REQUIRED_MESSAGE =
            "Mentor tip surfacing condition type is required.";
    private static final String COMPANION_MENTOR_TIP_THRESHOLD_INVALID_MESSAGE =
            "Mentor tip surfacing threshold must be zero or greater.";
    private static final String ADMIN_REQUIRED_MESSAGE = "You do not have permission to access this endpoint.";
    private static final String ITEM_COUNT_METADATA_KEY = "itemCount";
    private static final String SOURCE_PLAN_ID_METADATA_KEY = "sourcePlanId";
    private static final String COPIED_COUNT_METADATA_KEY = "copiedCount";
    private static final String SKIPPED_COUNT_METADATA_KEY = "skippedCount";
    private static final String ALREADY_ADOPTED_METADATA_KEY = "alreadyAdopted";
    private static final String ADOPTED_SUBJECT_COUNT_METADATA_KEY = "adoptedSubjectCount";
    private static final String SKIPPED_SUBJECT_COUNT_METADATA_KEY = "skippedSubjectCount";
    private static final String TOTAL_NOTES_COPIED_METADATA_KEY = "totalNotesCopied";
    private static final String TOTAL_NOTES_SKIPPED_METADATA_KEY = "totalNotesSkipped";

    private final NoteCollectionRepository collectionRepository;
    private final NoteCollectionItemRepository itemRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final QuizSessionHistoryService quizSessionHistoryService;
    private final ConceptHealthService conceptHealthService;
    private final ProgressReportService progressReportService;
    private final AnalyticsService analyticsService;
    private final NoteService noteService;
    private final LlmStudyPackService llmStudyPackService;
    private final UserRepository userRepository;
    private final TransactionOperations collectionTransactionOperations;

    @Transactional(readOnly = true)
    public List<NoteCollectionSummaryResponse> list(UUID userId) {
        List<NoteCollectionEntity> collections =
                collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId);
        if (collections.isEmpty()) {
            return List.of();
        }
        List<NoteCollectionEntity> children = collectionRepository.findByParentCollectionIdIn(collectionIds(collections));
        List<NoteCollectionEntity> collectionsWithChildren = new ArrayList<>(collections);
        collectionsWithChildren.addAll(children);
        Map<UUID, Integer> itemCountsByCollectionId = loadItemCounts(collectionsWithChildren);
        Map<UUID, Integer> readyCountsByCollectionId = loadReadyCounts(collectionsWithChildren);
        Map<UUID, Integer> rolledUpItemCountsByCollectionId = rollUpCounts(collections, children, itemCountsByCollectionId);
        Map<UUID, Integer> rolledUpReadyCountsByCollectionId = rollUpCounts(collections, children, readyCountsByCollectionId);
        Map<UUID, Integer> childCountsByCollectionId = loadChildCounts(collections);
        Map<UUID, Integer> practicedCountsByCollectionId = loadPracticedCounts(userId, collections);
        return collections.stream()
                .map(collection -> toSummaryResponse(
                        collection,
                        rolledUpItemCountsByCollectionId.getOrDefault(collection.getId(), 0),
                        rolledUpReadyCountsByCollectionId.getOrDefault(collection.getId(), 0),
                        childCountsByCollectionId.getOrDefault(collection.getId(), 0),
                        practicedCountsByCollectionId.getOrDefault(collection.getId(), 0)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoteCollectionSummaryResponse> listPublic(String courseProgram) {
        String normalizedCourseProgram = CourseProgramNormalizationUtils.normalizeForStorage(courseProgram);
        List<NoteCollectionEntity> collections = normalizedCourseProgram == null
                ? collectionRepository.findByVisibilityAndParentCollectionIdIsNullOrderByUpdatedAtDesc(CollectionVisibility.PUBLIC)
                : collectionRepository.findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
                        CollectionVisibility.PUBLIC,
                        normalizedCourseProgram
                );
        if (collections.isEmpty()) {
            return List.of();
        }
        List<NoteCollectionEntity> children = collectionRepository.findByParentCollectionIdIn(collectionIds(collections));
        List<NoteCollectionEntity> collectionsWithChildren = new ArrayList<>(collections);
        collectionsWithChildren.addAll(children);
        Map<UUID, Integer> itemCountsByCollectionId = loadItemCounts(collectionsWithChildren);
        Map<UUID, Integer> readyCountsByCollectionId = loadReadyCounts(collectionsWithChildren);
        Map<UUID, Integer> rolledUpItemCountsByCollectionId = rollUpCounts(collections, children, itemCountsByCollectionId);
        Map<UUID, Integer> rolledUpReadyCountsByCollectionId = rollUpCounts(collections, children, readyCountsByCollectionId);
        Map<UUID, Integer> childCountsByCollectionId = loadChildCounts(collections);
        return collections.stream()
                .map(collection -> toSummaryResponse(
                        collection,
                        rolledUpItemCountsByCollectionId.getOrDefault(collection.getId(), 0),
                        rolledUpReadyCountsByCollectionId.getOrDefault(collection.getId(), 0),
                        childCountsByCollectionId.getOrDefault(collection.getId(), 0),
                        0
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
        collection.setVisibility(CollectionVisibility.PRIVATE);
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);
        NoteCollectionEntity saved = collectionRepository.save(collection);

        List<NoteCollectionItemEntity> items = buildItems(saved.getId(), orderedNoteIds, 0, now);
        itemRepository.saveAll(items);
        reassertPrimaryInvariant(userId);
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

    @Transactional(readOnly = true)
    public PlanReadinessResponse getReadiness(UUID collectionId, UUID userId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        List<UUID> noteIds = items.stream().map(NoteCollectionItemEntity::getNoteId).toList();
        return toPlanReadinessResponse(
                collection.getId(),
                items.size(),
                loadOwnedStudyPackProgressViews(noteIds, userId),
                userId,
                OffsetDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, NoteConceptCountsResponse> getNoteConceptCounts(UUID collectionId, UUID userId) {
        getOwnedCollectionOrThrow(collectionId, userId);
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        List<UUID> noteIds = items.stream()
                .map(NoteCollectionItemEntity::getNoteId)
                .toList();
        if (noteIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, StudyPackProgressView> studyPacksByNoteId = studyPackRepository.findProgressViewsByNoteIdIn(noteIds).stream()
                .filter(studyPack -> studyPack.getNoteId() != null)
                .collect(Collectors.toMap(
                        StudyPackProgressView::getNoteId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (studyPacksByNoteId.isEmpty()) {
            return Map.of();
        }

        List<UUID> studyPackIds = studyPacksByNoteId.values().stream()
                .map(StudyPackProgressView::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, ProgressReportService.ConceptCounts> countsByStudyPackId =
                progressReportService.getConceptCountsPerStudyPack(
                        studyPackIds,
                        studyPacksByNoteId.values(),
                        userId,
                        OffsetDateTime.now()
                );
        Map<String, NoteConceptCountsResponse> countsByNoteId = new HashMap<>();
        for (NoteCollectionItemEntity item : items) {
            StudyPackProgressView studyPack = studyPacksByNoteId.get(item.getNoteId());
            if (studyPack == null || studyPack.getId() == null) {
                continue;
            }
            ProgressReportService.ConceptCounts counts = countsByStudyPackId.get(studyPack.getId());
            if (counts == null) {
                continue;
            }
            countsByNoteId.put(item.getNoteId().toString(), new NoteConceptCountsResponse(
                    counts.totalConcepts(),
                    counts.masteredConcepts(),
                    counts.dueConcepts(),
                    counts.notPracticedConcepts()
            ));
        }
        return countsByNoteId;
    }

    @Transactional(readOnly = true)
    public GoalCollectionDetailResponse getGoal(UUID collectionId, UUID userId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        List<NoteCollectionEntity> children = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId);
        Map<UUID, Integer> itemCountsByCollectionId = children.isEmpty() ? Map.of() : loadItemCounts(children);
        Map<UUID, List<StudyPackProgressView>> studyPacksByChildId = children.isEmpty()
                ? Map.of()
                : loadGoalStudyPacksByChildId(children, userId);
        Map<UUID, ProgressReportService.SubjectProgressBatchResult> progressByChildId = children.isEmpty()
                ? Map.of()
                : progressReportService.buildSubjectProgressEntriesByGroup(
                        studyPacksByChildId,
                        userId,
                        OffsetDateTime.now()
                );
        List<GoalCollectionChildResponse> childResponses = children.stream()
                .map(child -> toGoalChildResponse(
                        child,
                        userId,
                        itemCountsByCollectionId.getOrDefault(child.getId(), 0),
                        progressByChildId.get(child.getId())
                ))
                .toList();
        int totalConcepts = childResponses.stream().mapToInt(GoalCollectionChildResponse::totalConcepts).sum();
        int masteredConcepts = childResponses.stream().mapToInt(GoalCollectionChildResponse::masteredConcepts).sum();
        int dueConcepts = childResponses.stream().mapToInt(GoalCollectionChildResponse::dueConcepts).sum();
        int notPracticedConcepts = childResponses.stream().mapToInt(GoalCollectionChildResponse::notPracticedConcepts).sum();
        int itemCount = Math.toIntExact(itemRepository.countByCollectionId(collectionId));
        boolean companionMayBeOutdated = companionMayBeOutdated(collection, children, userId);
        WeeklyCountdown countdown = computeWeeklyCountdown(
                userId,
                collection.getTargetCompletionDate(),
                masteredConcepts,
                dueConcepts,
                notPracticedConcepts,
                totalConcepts
        );
        List<GoalCollectionChildResponse> scheduledChildResponses = applyTodaysConceptBudgets(
                childResponses,
                countdown.newConceptsToday()
        );
        List<WeeklyFocusDayEntry> weeklyFocusByDay = buildWeeklyFocusByDay(
                collection.getTargetCompletionDate(),
                countdown.studyDaysPerWeek(),
                scheduledChildResponses.stream().map(GoalCollectionChildResponse::collectionId).toList()
        );
        return new GoalCollectionDetailResponse(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getVisibility().name(),
                collection.getCourseProgram(),
                collection.getTargetCompletionDate(),
                collection.getCompanion(),
                companionMayBeOutdated,
                collection.getSourcePlanId(),
                collection.getParentCollectionId(),
                itemCount,
                childResponses.size(),
                masteryPercentage(masteredConcepts, totalConcepts),
                masteredConcepts,
                dueConcepts,
                notPracticedConcepts,
                totalConcepts,
                countdown.weeksRemaining(),
                countdown.conceptsRemaining(),
                countdown.todaysConceptBudget(),
                weeklyFocusByDay,
                collection.getCreatedAt(),
                collection.getUpdatedAt(),
                scheduledChildResponses
        );
    }

    private record WeeklyCountdown(
            Integer weeksRemaining,
            Integer conceptsRemaining,
            Integer todaysConceptBudget,
            Integer newConceptsToday,
            Integer studyDaysPerWeek
    ) {
        private static final WeeklyCountdown NONE = new WeeklyCountdown(null, null, null, null, null);
    }

    private WeeklyCountdown computeWeeklyCountdown(
            UUID userId,
            LocalDate targetCompletionDate,
            int masteredConcepts,
            int dueConcepts,
            int notPracticedConcepts,
            int totalConcepts
    ) {
        if (targetCompletionDate == null) {
            return WeeklyCountdown.NONE;
        }
        UserEntity user = getUserOrThrow(userId);
        int studyDaysPerWeek = user.getStudyDaysPerWeek() != null
                ? user.getStudyDaysPerWeek()
                : DEFAULT_STUDY_DAYS_PER_WEEK;
        long remainingDays = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), targetCompletionDate));
        long remainingScheduledDays = Math.max(1, Math.round(remainingDays * studyDaysPerWeek / 7.0));
        int weeksRemaining = (int) Math.ceil(remainingDays / 7.0);
        int conceptsRemaining = totalConcepts - masteredConcepts;
        int newConceptsToday = (int) Math.ceil(notPracticedConcepts / (double) remainingScheduledDays);
        int todaysConceptBudget = dueConcepts + newConceptsToday;
        return new WeeklyCountdown(weeksRemaining, conceptsRemaining, todaysConceptBudget, newConceptsToday, studyDaysPerWeek);
    }

    private record SubjectAllocation(UUID collectionId, int floorShare, double fractionalRemainder, int order) {
    }

    private List<GoalCollectionChildResponse> applyTodaysConceptBudgets(
            List<GoalCollectionChildResponse> childResponses,
            Integer newConceptsToday
    ) {
        if (newConceptsToday == null) {
            return childResponses.stream()
                    .map(child -> withTodaysConceptBudget(child, null))
                    .toList();
        }
        Map<UUID, Integer> allocatedNewConceptsByChildId = allocateNewConceptsByChild(childResponses, newConceptsToday);
        return childResponses.stream()
                .map(child -> withTodaysConceptBudget(
                        child,
                        child.dueConcepts() + allocatedNewConceptsByChildId.getOrDefault(child.collectionId(), 0)
                ))
                .toList();
    }

    private Map<UUID, Integer> allocateNewConceptsByChild(
            List<GoalCollectionChildResponse> childResponses,
            int newConceptsToday
    ) {
        int totalNotPracticed = childResponses.stream().mapToInt(GoalCollectionChildResponse::notPracticedConcepts).sum();
        if (totalNotPracticed == 0 || newConceptsToday == 0) {
            return childResponses.stream()
                    .collect(Collectors.toMap(
                            GoalCollectionChildResponse::collectionId,
                            ignored -> 0,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
        }

        List<SubjectAllocation> allocations = new ArrayList<>();
        int floorTotal = 0;
        for (int index = 0; index < childResponses.size(); index++) {
            GoalCollectionChildResponse child = childResponses.get(index);
            double exactShare = newConceptsToday * child.notPracticedConcepts() / (double) totalNotPracticed;
            int floorShare = (int) Math.floor(exactShare);
            floorTotal += floorShare;
            allocations.add(new SubjectAllocation(
                    child.collectionId(),
                    floorShare,
                    exactShare - floorShare,
                    index
            ));
        }

        Map<UUID, Integer> result = allocations.stream()
                .collect(Collectors.toMap(
                        SubjectAllocation::collectionId,
                        SubjectAllocation::floorShare,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        int leftover = newConceptsToday - floorTotal;
        allocations.stream()
                .sorted(Comparator
                        .comparingDouble(SubjectAllocation::fractionalRemainder)
                        .reversed()
                        .thenComparingInt(SubjectAllocation::order))
                .limit(leftover)
                .forEach(allocation -> result.computeIfPresent(
                        allocation.collectionId(),
                        (ignored, current) -> current + 1
                ));
        return result;
    }

    private GoalCollectionChildResponse withTodaysConceptBudget(
            GoalCollectionChildResponse child,
            Integer todaysConceptBudget
    ) {
        return new GoalCollectionChildResponse(
                child.collectionId(),
                child.title(),
                child.description(),
                child.itemCount(),
                child.overallReadinessPercentage(),
                child.masteredConcepts(),
                child.dueConcepts(),
                child.notPracticedConcepts(),
                child.totalConcepts(),
                todaysConceptBudget
        );
    }

    private List<WeeklyFocusDayEntry> buildWeeklyFocusByDay(
            LocalDate targetCompletionDate,
            Integer studyDaysPerWeek,
            List<UUID> childCollectionIds
    ) {
        if (targetCompletionDate == null || childCollectionIds.isEmpty()) {
            return List.of();
        }
        int effectiveStudyDaysPerWeek = studyDaysPerWeek != null ? studyDaysPerWeek : DEFAULT_STUDY_DAYS_PER_WEEK;
        List<DayOfWeek> studyDays = new ArrayList<>();
        for (int index = 0; index < effectiveStudyDaysPerWeek; index++) {
            studyDays.add(DayOfWeek.of(1 + (index * 7) / effectiveStudyDaysPerWeek));
        }

        Map<DayOfWeek, List<UUID>> collectionIdsByDay = new LinkedHashMap<>();
        for (int index = 0; index < childCollectionIds.size(); index++) {
            DayOfWeek studyDay = studyDays.get(index % studyDays.size());
            collectionIdsByDay.computeIfAbsent(studyDay, ignored -> new ArrayList<>())
                    .add(childCollectionIds.get(index));
        }
        return collectionIdsByDay.entrySet().stream()
                .map(entry -> new WeeklyFocusDayEntry(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteCollectionDetailResponse getPublic(UUID collectionId) {
        NoteCollectionEntity collection = collectionRepository.findByIdAndVisibility(collectionId, CollectionVisibility.PUBLIC)
                .orElseThrow(CollectionNotFoundException::new);
        List<NoteCollectionEntity> children = collectionRepository.findByParentCollectionIdIn(List.of(collectionId));
        List<NoteCollectionEntity> collectionsWithChildren = new ArrayList<>(List.of(collection));
        collectionsWithChildren.addAll(children);
        List<NoteCollectionItemEntity> items = itemRepository
                .findByCollectionIdInOrderByCollectionIdAscPositionAsc(collectionIds(collectionsWithChildren));
        return toPublicDetailResponse(collection, items);
    }

    @Transactional
    public NoteCollectionDetailResponse updateMetadata(UUID collectionId, UUID userId, UpdateNoteCollectionRequest request) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        if (request != null) {
            // PATCH semantics: only overwrite fields the caller actually provided. A null field means
            // "not included in this request" and must be left untouched — otherwise a partial update
            // (e.g. the Goal Builder's title-only rename) silently wipes description, courseProgram, and
            // estimatedStudyHours. To clear a text field, callers send an explicit empty string, which
            // normalizes to null below.
            if (request.title() != null) {
                collection.setTitle(validateRequiredTitle(request.title()));
            }
            if (request.description() != null) {
                collection.setDescription(normalizeOptionalText(request.description()));
            }
            if (request.courseProgram() != null) {
                String normalizedCourseProgram = CourseProgramNormalizationUtils.normalizeForStorage(request.courseProgram());
                collection.setCourseProgram(normalizedCourseProgram);
                if (normalizedCourseProgram != null) {
                    cascadeCourseProgramToBlankChildren(collectionId, userId, normalizedCourseProgram);
                }
            }
            if (request.estimatedStudyHours() != null) {
                collection.setEstimatedStudyHours(request.estimatedStudyHours());
            }
            if (request.targetCompletionDate() != null) {
                if (collection.getParentCollectionId() != null) {
                    throw new InvalidCollectionRequestException(TARGET_DATE_REQUIRES_TOP_LEVEL_GOAL_MESSAGE);
                }
                collection.setTargetCompletionDate(request.targetCompletionDate());
            }
        }
        touch(collection);
        NoteCollectionEntity saved = collectionRepository.save(collection);
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        return toDetailResponse(saved, items);
    }

    @Transactional
    public NoteCollectionDetailResponse clearTargetDate(UUID collectionId, UUID userId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        if (collection.getTargetCompletionDate() != null) {
            collection.setTargetCompletionDate(null);
            touch(collection);
            collection = collectionRepository.save(collection);
        }
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        return toDetailResponse(collection, items);
    }

    @Transactional
    public NoteCollectionDetailResponse updateParent(
            UUID collectionId,
            UUID userId,
            SetNoteCollectionParentRequest request
    ) {
        NoteCollectionEntity child = getOwnedCollectionOrThrow(collectionId, userId);
        UUID parentId = request == null ? null : request.parentId();
        if (parentId == null) {
            if (child.getParentCollectionId() != null) {
                child.setParentCollectionId(null);
                child.setSiblingPosition(null);
                touch(child);
                child = collectionRepository.save(child);
            }
            reassertPrimaryInvariant(userId);
            return toDetailResponse(child, itemRepository.findByCollectionIdOrderByPositionAsc(collectionId));
        }
        if (parentId.equals(collectionId)) {
            throw new InvalidCollectionRequestException(SELF_PARENT_MESSAGE);
        }

        NoteCollectionEntity parent = getOwnedCollectionOrThrow(parentId, userId);
        validateParentCanAcceptChild(parent);
        validateChildCanBeNested(child);
        if (!parentId.equals(child.getParentCollectionId())) {
            child.setParentCollectionId(parentId);
            child.setSiblingPosition(collectionRepository.findMaxSiblingPosition(parentId, userId) + 1);
            // targetCompletionDate and Companion are top-level-Goal-only fields; a collection
            // that becomes a child must not keep carrying either one, or stale top-level data
            // resurfaces if it is later detached back to top-level via updateParent(null).
            child.setTargetCompletionDate(null);
            child.setCompanion(null);
            child.setCompanionStructureSnapshot(null);
            touch(child);
            child = collectionRepository.save(child);
        }
        reassertPrimaryInvariant(userId);
        return toDetailResponse(child, itemRepository.findByCollectionIdOrderByPositionAsc(collectionId));
    }

    @Transactional
    public void setPrimary(UUID collectionId, UUID userId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        if (collection.getParentCollectionId() != null) {
            throw new InvalidCollectionRequestException(PRIMARY_REQUIRES_TOP_LEVEL_GOAL_MESSAGE);
        }
        UserEntity user = getUserOrThrow(userId);
        if (collectionId.equals(user.getPrimaryCollectionId())) {
            return;
        }
        user.setPrimaryCollectionId(collectionId);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void clearPrimary(UUID userId) {
        UserEntity user = getUserOrThrow(userId);
        if (user.getPrimaryCollectionId() == null) {
            return;
        }
        user.setPrimaryCollectionId(null);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public NoteCollectionDetailResponse setCompanion(UUID collectionId, UUID userId, CompanionContent content) {
        UserEntity user = getUserOrThrow(userId);
        assertAdmin(user);
        if (content == null) {
            throw new InvalidCollectionRequestException(COMPANION_CONTENT_REQUIRED_MESSAGE);
        }
        validateCompanionContent(content);
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        validateCompanionTarget(collection);
        List<NoteCollectionEntity> children = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId);
        collection.setCompanion(content);
        collection.setCompanionStructureSnapshot(computeCompanionStructureSnapshot(collection, children));
        touch(collection);
        NoteCollectionEntity saved = collectionRepository.save(collection);
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        return toDetailResponse(saved, items);
    }

    @Transactional
    public NoteCollectionDetailResponse clearCompanion(UUID collectionId, UUID userId) {
        UserEntity user = getUserOrThrow(userId);
        assertAdmin(user);
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        validateCompanionTarget(collection);
        if (collection.getCompanion() != null) {
            collection.setCompanion(null);
            collection.setCompanionStructureSnapshot(null);
            touch(collection);
            collection = collectionRepository.save(collection);
        }
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        return toDetailResponse(collection, items);
    }

    public GeneratedCompanionContentResponse generateCompanion(
            UUID collectionId,
            UUID userId,
            GenerateCompanionRequest request
    ) {
        Set<CompanionSection> sections = extractCompanionSections(request);
        UserEntity user = getUserOrThrow(userId);
        assertAdmin(user);
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        validateCompanionTarget(collection);
        CompanionContent draft = llmStudyPackService.generateCompanion(
                buildCompanionGenerationContext(collection),
                sections
        );
        return GeneratedCompanionContentResponse.from(draft);
    }

    @Transactional
    public GoalCollectionDetailResponse setChildrenOrder(
            UUID collectionId,
            UUID userId,
            SetNoteCollectionChildrenOrderRequest request
    ) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        List<UUID> submittedChildIds = extractSubmittedChildIds(request == null ? null : request.childIds());
        List<NoteCollectionEntity> currentChildren = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId);
        validateSubmittedChildSetMatchesCurrent(currentChildren, submittedChildIds);

        Map<UUID, NoteCollectionEntity> childById = currentChildren.stream()
                .collect(Collectors.toMap(NoteCollectionEntity::getId, Function.identity()));
        for (int index = 0; index < submittedChildIds.size(); index++) {
            childById.get(submittedChildIds.get(index)).setSiblingPosition(index);
        }
        collectionRepository.saveAll(currentChildren);
        touch(collection);
        collectionRepository.save(collection);
        return getGoal(collectionId, userId);
    }

    @Transactional
    public NoteCollectionDetailResponse updateVisibility(UUID collectionId, UUID userId, String visibilityRaw) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        CollectionVisibility visibility = parseVisibility(visibilityRaw);
        if (visibility == CollectionVisibility.PUBLIC) {
            validatePublishable(collection);
        }
        collection.setVisibility(visibility);
        touch(collection);
        NoteCollectionEntity saved = collectionRepository.save(collection);
        if (visibility == CollectionVisibility.PUBLIC) {
            publishChildCollections(collectionId, userId);
        }
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        return toDetailResponse(saved, items);
    }

    public AdoptStudyPlanResponse adopt(UUID sourceCollectionId, UUID userId) {
        return adopt(sourceCollectionId, userId, true);
    }

    private AdoptStudyPlanResponse adopt(UUID sourceCollectionId, UUID userId, boolean reassertPrimaryAfterPersist) {
        NoteCollectionEntity source = collectionRepository
                .findByIdAndVisibility(sourceCollectionId, CollectionVisibility.PUBLIC)
                .orElseThrow(CollectionNotFoundException::new);

        // Fast idempotency path: an existing personal plan for this source is returned as-is.
        Optional<NoteCollectionEntity> alreadyAdopted =
                collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceCollectionId);
        if (alreadyAdopted.isPresent()) {
            return alreadyAdoptedResponse(userId, sourceCollectionId, alreadyAdopted.get());
        }

        // adopt() is intentionally non-transactional so each note copy runs in its own transaction:
        // a single failed copy is isolated and never rolls back the whole adopt.
        List<CopiedPlanItem> copiedItems = new ArrayList<>();
        int skippedCount = copySourceItems(source, userId, copiedItems);

        try {
            return persistAdoptedPlan(source, userId, copiedItems, skippedCount, reassertPrimaryAfterPersist);
        } catch (DataIntegrityViolationException raceLost) {
            // A concurrent first-adopt won the (owner_user_id, source_plan_id) unique index — return theirs.
            NoteCollectionEntity winner = collectionRepository
                    .findByOwnerUserIdAndSourcePlanId(userId, sourceCollectionId)
                    .orElseThrow(() -> raceLost);
            return alreadyAdoptedResponse(userId, sourceCollectionId, winner);
        }
    }

    public AdoptGoalResponse adoptGoal(UUID sourceGoalId, UUID userId) {
        NoteCollectionEntity source = collectionRepository
                .findByIdAndVisibility(sourceGoalId, CollectionVisibility.PUBLIC)
                .orElseThrow(CollectionNotFoundException::new);
        if (collectionRepository.countByParentCollectionId(sourceGoalId) == 0) {
            throw new CollectionNotFoundException();
        }

        Optional<NoteCollectionEntity> alreadyAdopted =
                collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId);
        if (alreadyAdopted.isPresent()) {
            return alreadyAdoptedGoalResponse(userId, sourceGoalId, alreadyAdopted.get());
        }

        List<NoteCollectionEntity> sourceChildren = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(sourceGoalId, source.getOwnerUserId());
        AdoptedGoalPersistence persistedGoal;
        try {
            persistedGoal = persistAdoptedGoal(source, userId);
        } catch (DataIntegrityViolationException raceLost) {
            NoteCollectionEntity winner = collectionRepository
                    .findByOwnerUserIdAndSourcePlanId(userId, sourceGoalId)
                    .orElseThrow(() -> raceLost);
            return alreadyAdoptedGoalResponse(userId, sourceGoalId, winner);
        }
        if (persistedGoal.alreadyAdopted()) {
            return alreadyAdoptedGoalResponse(userId, sourceGoalId, persistedGoal.collection());
        }

        int adoptedSubjectCount = 0;
        int skippedSubjectCount = 0;
        int totalNotesCopied = 0;
        int totalNotesSkipped = 0;
        for (int index = 0; index < sourceChildren.size(); index++) {
            NoteCollectionEntity sourceChild = sourceChildren.get(index);
            AdoptStudyPlanResponse childAdoptResult = adopt(sourceChild.getId(), userId, false);
            totalNotesCopied += childAdoptResult.copiedCount();
            totalNotesSkipped += childAdoptResult.skippedCount();
            Optional<NoteCollectionEntity> personalChild =
                    collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourceChild.getId());
            if (personalChild.isEmpty()) {
                skippedSubjectCount++;
                continue;
            }

            NoteCollectionEntity child = personalChild.get();
            if (child.getParentCollectionId() == null) {
                child.setParentCollectionId(persistedGoal.collection().getId());
                child.setSiblingPosition(index);
                // Same invariant as updateParent(): a collection that becomes a child must not
                // keep carrying targetCompletionDate or Companion, both top-level-Goal-only fields.
                child.setTargetCompletionDate(null);
                child.setCompanion(null);
                child.setCompanionStructureSnapshot(null);
                touch(child);
                collectionRepository.save(child);
                adoptedSubjectCount++;
            } else {
                skippedSubjectCount++;
            }
        }

        trackStudyGoalAdopted(
                userId,
                sourceGoalId,
                persistedGoal.collection().getId(),
                adoptedSubjectCount,
                skippedSubjectCount,
                totalNotesCopied,
                totalNotesSkipped,
                false
        );
        reassertPrimaryInvariant(userId);
        return new AdoptGoalResponse(
                persistedGoal.collection().getId(),
                adoptedSubjectCount,
                skippedSubjectCount,
                totalNotesCopied,
                totalNotesSkipped,
                false
        );
    }

    @Transactional
    public void delete(UUID collectionId, UUID userId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        boolean wasTopLevel = collection.getParentCollectionId() == null;
        collectionRepository.delete(collection);
        if (wasTopLevel) {
            reassertPrimaryInvariant(userId);
        }
    }

    @Transactional
    public NoteCollectionDetailResponse addItems(UUID collectionId, UUID userId, AddNoteCollectionItemsRequest request) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        if (collectionRepository.countByParentCollectionId(collectionId) > 0) {
            throw new InvalidCollectionRequestException(GOAL_CANNOT_ACCEPT_NOTES_MESSAGE);
        }
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

    private UserEntity getUserOrThrow(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private void assertAdmin(UserEntity user) {
        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException(ADMIN_REQUIRED_MESSAGE);
        }
    }

    private void validateCompanionTarget(NoteCollectionEntity collection) {
        if (collection.getParentCollectionId() != null) {
            throw new InvalidCollectionRequestException(COMPANION_REQUIRES_TOP_LEVEL_GOAL_MESSAGE);
        }
    }

    private void validateCompanionContent(CompanionContent content) {
        if (content.mentorTips() == null) {
            return;
        }
        content.mentorTips().stream()
                .filter(Objects::nonNull)
                .forEach(this::validateCompanionMentorTip);
    }

    private void validateCompanionMentorTip(CompanionMentorTip tip) {
        if (tip.surfacingCondition() == null) {
            return;
        }
        if (tip.surfacingCondition().type() == null) {
            throw new InvalidCollectionRequestException(COMPANION_MENTOR_TIP_CONDITION_TYPE_REQUIRED_MESSAGE);
        }
        if (tip.surfacingCondition().threshold() < 0) {
            throw new InvalidCollectionRequestException(COMPANION_MENTOR_TIP_THRESHOLD_INVALID_MESSAGE);
        }
    }

    private boolean companionMayBeOutdated(
            NoteCollectionEntity collection,
            List<NoteCollectionEntity> children,
            UUID userId
    ) {
        if (collection.getCompanion() == null || collection.getCompanionStructureSnapshot() == null) {
            return false;
        }
        UserEntity user = getUserOrThrow(userId);
        if (user.getRole() != UserRole.ADMIN) {
            return false;
        }
        CompanionStructureSnapshot currentSnapshot = computeCompanionStructureSnapshot(collection, children);
        return !currentSnapshot.equals(collection.getCompanionStructureSnapshot());
    }

    private CompanionStructureSnapshot computeCompanionStructureSnapshot(
            NoteCollectionEntity collection,
            List<NoteCollectionEntity> children
    ) {
        List<UUID> memberIds = children.isEmpty()
                ? itemRepository.findByCollectionIdOrderByPositionAsc(collection.getId()).stream()
                        .map(NoteCollectionItemEntity::getNoteId)
                        .sorted()
                        .toList()
                : children.stream()
                        .map(NoteCollectionEntity::getId)
                        .sorted()
                        .toList();
        return new CompanionStructureSnapshot(memberIds.size(), memberIds);
    }

    private Set<CompanionSection> extractCompanionSections(GenerateCompanionRequest request) {
        if (request == null || request.sections() == null || request.sections().isEmpty()) {
            throw new InvalidCollectionRequestException(COMPANION_SECTION_REQUIRED_MESSAGE);
        }
        Set<CompanionSection> sections = EnumSet.noneOf(CompanionSection.class);
        request.sections().stream()
                .filter(Objects::nonNull)
                .forEach(sections::add);
        if (sections.isEmpty()) {
            throw new InvalidCollectionRequestException(COMPANION_SECTION_REQUIRED_MESSAGE);
        }
        return sections;
    }

    private CompanionGenerationContext buildCompanionGenerationContext(NoteCollectionEntity collection) {
        List<NoteCollectionEntity> children = collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(
                collection.getId(),
                collection.getOwnerUserId()
        );
        if (!children.isEmpty()) {
            return new CompanionGenerationContext(
                    collection.getTitle(),
                    collection.getDescription(),
                    collection.getCourseProgram(),
                    children.stream()
                            .map(child -> new CompanionGenerationContext.CompanionContextItem(
                                    child.getTitle(),
                                    child.getDescription()
                            ))
                            .toList(),
                    List.of()
            );
        }

        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collection.getId());
        List<UUID> noteIds = items.stream().map(NoteCollectionItemEntity::getNoteId).toList();
        if (noteIds.isEmpty()) {
            return new CompanionGenerationContext(
                    collection.getTitle(),
                    collection.getDescription(),
                    collection.getCourseProgram(),
                    List.of(),
                    List.of()
            );
        }
        Map<UUID, NoteCollectionNoteProjection> notesById = noteRepository.findCollectionNoteProjectionsByIdIn(noteIds)
                .stream()
                .collect(Collectors.toMap(NoteCollectionNoteProjection::noteId, Function.identity()));
        return new CompanionGenerationContext(
                collection.getTitle(),
                collection.getDescription(),
                collection.getCourseProgram(),
                List.of(),
                items.stream()
                        .map(NoteCollectionItemEntity::getNoteId)
                        .map(notesById::get)
                        .filter(Objects::nonNull)
                        .map(note -> new CompanionGenerationContext.CompanionContextItem(
                                note.title(),
                                note.subject()
                        ))
                        .toList()
        );
    }

    private boolean shouldCopyCompanion(NoteCollectionEntity source, UUID userId) {
        return source.getParentCollectionId() == null && !source.getOwnerUserId().equals(userId);
    }

    private void reassertPrimaryInvariant(UUID userId) {
        UserEntity user = getUserOrThrow(userId);
        UUID primaryCollectionId = user.getPrimaryCollectionId();
        boolean changed = false;
        if (primaryCollectionId != null && !isValidPrimaryCollection(primaryCollectionId, userId)) {
            user.setPrimaryCollectionId(null);
            primaryCollectionId = null;
            changed = true;
        }

        if (primaryCollectionId == null && collectionRepository.countByOwnerUserIdAndParentCollectionIdIsNull(userId) == 1) {
            List<NoteCollectionEntity> topLevelCollections =
                    collectionRepository.findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(userId);
            if (topLevelCollections.size() == 1) {
                user.setPrimaryCollectionId(topLevelCollections.getFirst().getId());
                changed = true;
            }
        }
        if (changed) {
            user.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);
        }
    }

    private void assignAdoptedCollectionAsPrimaryWhenMissing(UUID userId, NoteCollectionEntity adoptedCollection) {
        if (adoptedCollection.getParentCollectionId() != null) {
            return;
        }
        UserEntity user = getUserOrThrow(userId);
        if (user.getPrimaryCollectionId() != null) {
            return;
        }
        user.setPrimaryCollectionId(adoptedCollection.getId());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    private boolean isValidPrimaryCollection(UUID collectionId, UUID userId) {
        return collectionRepository.findByIdAndOwnerUserId(collectionId, userId)
                .map(collection -> collection.getParentCollectionId() == null)
                .orElse(false);
    }

    private void validateParentCanAcceptChild(NoteCollectionEntity parent) {
        if (parent.getParentCollectionId() != null) {
            throw new InvalidCollectionRequestException(PARENT_NOT_TOP_LEVEL_MESSAGE);
        }
        if (!itemRepository.findByCollectionIdOrderByPositionAsc(parent.getId()).isEmpty()) {
            throw new InvalidCollectionRequestException(PARENT_WITH_NOTES_MESSAGE);
        }
    }

    private void validateChildCanBeNested(NoteCollectionEntity child) {
        if (collectionRepository.countByParentCollectionId(child.getId()) > 0) {
            throw new InvalidCollectionRequestException(CHILD_HAS_CHILDREN_MESSAGE);
        }
    }

    private void validatePublishable(NoteCollectionEntity collection) {
        if (collectionRepository.countByParentCollectionId(collection.getId()) > 0) {
            validateGoalPublishable(collection);
            return;
        }
        validateLeafPublishable(collection.getId(), EMPTY_PUBLISH_MESSAGE, PRIVATE_NOTE_PUBLISH_MESSAGE);
    }

    private void validateGoalPublishable(NoteCollectionEntity collection) {
        List<NoteCollectionEntity> children = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(collection.getId(), collection.getOwnerUserId());
        if (children.isEmpty()) {
            throw new CollectionNotPublishableException(EMPTY_GOAL_PUBLISH_MESSAGE);
        }
        for (NoteCollectionEntity child : children) {
            validateLeafPublishable(child.getId(), EMPTY_GOAL_CHILD_PUBLISH_MESSAGE, PRIVATE_GOAL_NOTE_PUBLISH_MESSAGE);
        }
    }

    private void validateLeafPublishable(
            UUID collectionId,
            String emptyMessage,
            String privateNoteMessage
    ) {
        List<NoteCollectionItemEntity> items = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId);
        if (items.isEmpty()) {
            throw new CollectionNotPublishableException(emptyMessage);
        }
        List<UUID> noteIds = items.stream().map(NoteCollectionItemEntity::getNoteId).toList();
        Map<UUID, NoteEntity> notesById = noteRepository.findAllById(noteIds).stream()
                .collect(Collectors.toMap(NoteEntity::getId, Function.identity()));
        for (UUID noteId : noteIds) {
            NoteEntity note = notesById.get(noteId);
            if (note == null || note.getVisibility() != NoteVisibility.PUBLIC) {
                throw new CollectionNotPublishableException(privateNoteMessage);
            }
        }
    }

    private void publishChildCollections(UUID collectionId, UUID userId) {
        List<NoteCollectionEntity> children = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId);
        if (children.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        children.forEach(child -> {
            child.setVisibility(CollectionVisibility.PUBLIC);
            touch(child, now);
        });
        collectionRepository.saveAll(children);
    }

    private void cascadeCourseProgramToBlankChildren(UUID collectionId, UUID userId, String courseProgram) {
        List<NoteCollectionEntity> blankChildren = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId)
                .stream()
                .filter(child -> child.getCourseProgram() == null || child.getCourseProgram().isBlank())
                .toList();
        if (blankChildren.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        blankChildren.forEach(child -> {
            child.setCourseProgram(courseProgram);
            touch(child, now);
        });
        collectionRepository.saveAll(blankChildren);
    }

    private CollectionVisibility parseVisibility(String visibilityRaw) {
        if (visibilityRaw == null || visibilityRaw.isBlank()) {
            throw new InvalidCollectionRequestException(INVALID_VISIBILITY_MESSAGE);
        }
        try {
            return CollectionVisibility.valueOf(visibilityRaw.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new InvalidCollectionRequestException(INVALID_VISIBILITY_MESSAGE);
        }
    }

    private int copySourceItems(NoteCollectionEntity source, UUID userId, List<CopiedPlanItem> copiedItems) {
        List<NoteCollectionItemEntity> sourceItems = itemRepository.findByCollectionIdOrderByPositionAsc(source.getId());
        int skippedCount = 0;
        for (NoteCollectionItemEntity sourceItem : sourceItems) {
            try {
                if (!isPublicSourceNote(sourceItem.getNoteId())) {
                    skippedCount++;
                    continue;
                }
                NoteResponse copiedNote = noteService.copyNote(sourceItem.getNoteId().toString(), userId, true);
                copiedItems.add(new CopiedPlanItem(UUID.fromString(copiedNote.id()), sourceItem.getLabel()));
            } catch (RuntimeException exception) {
                skippedCount++;
                log.warn(
                        "study_plan_adopt_item_skipped sourcePlanId={} noteId={} userId={}",
                        source.getId(),
                        sourceItem.getNoteId(),
                        userId,
                        exception
                );
            }
        }
        return skippedCount;
    }

    private AdoptStudyPlanResponse persistAdoptedPlan(
            NoteCollectionEntity source,
            UUID userId,
            List<CopiedPlanItem> copiedItems,
            int skippedCount,
            boolean reassertPrimaryAfterPersist
    ) {
        return collectionTransactionOperations.execute(status -> {
            Optional<NoteCollectionEntity> existing =
                    collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, source.getId());
            if (existing.isPresent()) {
                return alreadyAdoptedResponse(userId, source.getId(), existing.get());
            }

            Instant now = Instant.now();
            NoteCollectionEntity collection = new NoteCollectionEntity();
            collection.setId(UUID.randomUUID());
            collection.setOwnerUserId(userId);
            collection.setTitle(source.getTitle());
            collection.setDescription(source.getDescription());
            collection.setVisibility(CollectionVisibility.PRIVATE);
            collection.setCourseProgram(source.getCourseProgram());
            collection.setEstimatedStudyHours(source.getEstimatedStudyHours());
            if (shouldCopyCompanion(source, userId)) {
                collection.setCompanion(source.getCompanion());
            }
            // targetCompletionDate is deliberately never copied from source (including on a self-copy where
            // ownerUserId == source's owner) — a curator's or previous owner's target date means nothing to
            // the new owner, so it stays null on the fresh entity until the new owner sets their own.
            collection.setSourcePlanId(source.getId());
            collection.setCreatedAt(now);
            collection.setUpdatedAt(now);
            // saveAndFlush so a concurrent first-adopt's unique-index violation surfaces here as a
            // translated DataIntegrityViolationException (recovered in adopt()), not at commit.
            NoteCollectionEntity saved = collectionRepository.saveAndFlush(collection);

            List<NoteCollectionItemEntity> items = buildAdoptedItems(saved.getId(), copiedItems, now);
            itemRepository.saveAll(items);
            trackStudyPlanAdopted(userId, source.getId(), saved.getId(), items.size(), skippedCount, false);
            if (reassertPrimaryAfterPersist) {
                reassertPrimaryInvariant(userId);
                assignAdoptedCollectionAsPrimaryWhenMissing(userId, saved);
            }
            return new AdoptStudyPlanResponse(saved.getId(), items.size(), skippedCount, false);
        });
    }

    private AdoptedGoalPersistence persistAdoptedGoal(NoteCollectionEntity source, UUID userId) {
        return collectionTransactionOperations.execute(status -> {
            Optional<NoteCollectionEntity> existing =
                    collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, source.getId());
            if (existing.isPresent()) {
                return new AdoptedGoalPersistence(existing.get(), true);
            }

            Instant now = Instant.now();
            NoteCollectionEntity collection = new NoteCollectionEntity();
            collection.setId(UUID.randomUUID());
            collection.setOwnerUserId(userId);
            collection.setTitle(source.getTitle());
            collection.setDescription(source.getDescription());
            collection.setVisibility(CollectionVisibility.PRIVATE);
            collection.setCourseProgram(source.getCourseProgram());
            collection.setEstimatedStudyHours(source.getEstimatedStudyHours());
            if (shouldCopyCompanion(source, userId)) {
                collection.setCompanion(source.getCompanion());
            }
            // targetCompletionDate is deliberately never copied from source (including on a self-copy where
            // ownerUserId == source's owner) — a curator's or previous owner's target date means nothing to
            // the new owner, so it stays null on the fresh entity until the new owner sets their own.
            collection.setSourcePlanId(source.getId());
            collection.setCreatedAt(now);
            collection.setUpdatedAt(now);
            NoteCollectionEntity saved = collectionRepository.saveAndFlush(collection);
            assignAdoptedCollectionAsPrimaryWhenMissing(userId, saved);
            return new AdoptedGoalPersistence(saved, false);
        });
    }

    private AdoptStudyPlanResponse alreadyAdoptedResponse(
            UUID userId,
            UUID sourcePlanId,
            NoteCollectionEntity existing
    ) {
        int itemCount = itemRepository.findByCollectionIdOrderByPositionAsc(existing.getId()).size();
        trackStudyPlanAdopted(userId, sourcePlanId, existing.getId(), itemCount, 0, true);
        return new AdoptStudyPlanResponse(existing.getId(), itemCount, 0, true);
    }

    private AdoptGoalResponse alreadyAdoptedGoalResponse(
            UUID userId,
            UUID sourceGoalId,
            NoteCollectionEntity existing
    ) {
        List<NoteCollectionEntity> children = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(existing.getId(), userId);
        int totalNotesCopied = children.isEmpty()
                ? 0
                : loadItemCounts(children).values().stream().mapToInt(Integer::intValue).sum();
        trackStudyGoalAdopted(
                userId,
                sourceGoalId,
                existing.getId(),
                children.size(),
                0,
                totalNotesCopied,
                0,
                true
        );
        return new AdoptGoalResponse(existing.getId(), children.size(), 0, totalNotesCopied, 0, true);
    }

    private boolean isPublicSourceNote(UUID noteId) {
        return noteRepository.findByIdAndVisibility(noteId, NoteVisibility.PUBLIC).isPresent();
    }

    private void trackStudyPlanAdopted(
            UUID userId,
            UUID sourcePlanId,
            UUID personalPlanId,
            int copiedCount,
            int skippedCount,
            boolean alreadyAdopted
    ) {
        analyticsService.trackEvent(
                userId,
                AnalyticsEventType.STUDY_PLAN_ADOPTED,
                personalPlanId,
                Map.of(
                        SOURCE_PLAN_ID_METADATA_KEY, sourcePlanId.toString(),
                        COPIED_COUNT_METADATA_KEY, copiedCount,
                        SKIPPED_COUNT_METADATA_KEY, skippedCount,
                        ALREADY_ADOPTED_METADATA_KEY, alreadyAdopted
                )
        );
    }

    private void trackStudyGoalAdopted(
            UUID userId,
            UUID sourceGoalId,
            UUID personalGoalId,
            int adoptedSubjectCount,
            int skippedSubjectCount,
            int totalNotesCopied,
            int totalNotesSkipped,
            boolean alreadyAdopted
    ) {
        analyticsService.trackEvent(
                userId,
                AnalyticsEventType.STUDY_GOAL_ADOPTED,
                personalGoalId,
                Map.of(
                        SOURCE_PLAN_ID_METADATA_KEY, sourceGoalId.toString(),
                        ADOPTED_SUBJECT_COUNT_METADATA_KEY, adoptedSubjectCount,
                        SKIPPED_SUBJECT_COUNT_METADATA_KEY, skippedSubjectCount,
                        TOTAL_NOTES_COPIED_METADATA_KEY, totalNotesCopied,
                        TOTAL_NOTES_SKIPPED_METADATA_KEY, totalNotesSkipped,
                        ALREADY_ADOPTED_METADATA_KEY, alreadyAdopted
                )
        );
    }

    private record AdoptedGoalPersistence(NoteCollectionEntity collection, boolean alreadyAdopted) {
    }

    private Map<UUID, Integer> loadItemCounts(List<NoteCollectionEntity> collections) {
        List<UUID> collectionIds = collections.stream().map(NoteCollectionEntity::getId).toList();
        Map<UUID, Integer> countsByCollectionId = new HashMap<>();
        for (NoteCollectionItemCountProjection projection : itemRepository.countItemsByCollectionIds(collectionIds)) {
            countsByCollectionId.put(projection.getCollectionId(), Math.toIntExact(projection.getItemCount()));
        }
        return countsByCollectionId;
    }

    private Map<UUID, Integer> rollUpCounts(
            List<NoteCollectionEntity> collections,
            List<NoteCollectionEntity> children,
            Map<UUID, Integer> countsByCollectionId
    ) {
        Map<UUID, Integer> rolledUpCountsByCollectionId = new HashMap<>();
        for (NoteCollectionEntity collection : collections) {
            rolledUpCountsByCollectionId.put(collection.getId(), countsByCollectionId.getOrDefault(collection.getId(), 0));
        }
        for (NoteCollectionEntity child : children) {
            UUID parentCollectionId = child.getParentCollectionId();
            if (parentCollectionId != null) {
                rolledUpCountsByCollectionId.merge(
                        parentCollectionId,
                        countsByCollectionId.getOrDefault(child.getId(), 0),
                        Integer::sum
                );
            }
        }
        return rolledUpCountsByCollectionId;
    }

    private List<UUID> collectionIds(List<NoteCollectionEntity> collections) {
        return collections.stream().map(NoteCollectionEntity::getId).toList();
    }

    private Map<UUID, Integer> loadReadyCounts(List<NoteCollectionEntity> collections) {
        Map<UUID, List<UUID>> noteIdsByCollectionId = loadNoteIdsByCollectionId(collections);
        LinkedHashSet<UUID> allNoteIds = noteIdsByCollectionId.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allNoteIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, NoteCollectionNoteProjection> notesById = noteRepository
                .findCollectionNoteProjectionsByIdIn(List.copyOf(allNoteIds)).stream()
                .collect(Collectors.toMap(NoteCollectionNoteProjection::noteId, Function.identity()));
        Set<UUID> noteIdsWithStudyPacks = studyPackRepository.findProgressViewsByNoteIdIn(List.copyOf(allNoteIds)).stream()
                .map(StudyPackProgressView::getNoteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Integer> readyCountsByCollectionId = new HashMap<>();
        for (Map.Entry<UUID, List<UUID>> entry : noteIdsByCollectionId.entrySet()) {
            int readyCount = (int) entry.getValue().stream()
                    .map(notesById::get)
                    .filter(Objects::nonNull)
                    .filter(note -> NoteStudyPackStatusResolver.STUDY_PACK_READY.equals(
                            NoteStudyPackStatusResolver.resolve(note.status(), noteIdsWithStudyPacks.contains(note.noteId()))
                    ))
                    .count();
            readyCountsByCollectionId.put(entry.getKey(), readyCount);
        }
        return readyCountsByCollectionId;
    }

    private Map<UUID, Integer> loadChildCounts(List<NoteCollectionEntity> collections) {
        List<UUID> collectionIds = collections.stream().map(NoteCollectionEntity::getId).toList();
        Map<UUID, Integer> countsByCollectionId = new HashMap<>();
        for (NoteCollectionChildCountProjection projection : collectionRepository.countChildrenByCollectionIds(collectionIds)) {
            countsByCollectionId.put(projection.getCollectionId(), Math.toIntExact(projection.getChildCount()));
        }
        return countsByCollectionId;
    }

    private Map<UUID, Integer> loadPracticedCounts(UUID userId, List<NoteCollectionEntity> collections) {
        Map<UUID, List<UUID>> noteIdsByCollectionId = loadNoteIdsByCollectionId(collections);
        LinkedHashSet<UUID> allNoteIds = noteIdsByCollectionId.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allNoteIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, OffsetDateTime> completedAtByNoteId = loadLastSessionCompletedAt(userId, List.copyOf(allNoteIds));
        Map<UUID, Integer> practicedCountsByCollectionId = new HashMap<>();
        for (Map.Entry<UUID, List<UUID>> entry : noteIdsByCollectionId.entrySet()) {
            int practicedCount = (int) entry.getValue().stream()
                    .filter(noteId -> completedAtByNoteId.get(noteId) != null)
                    .count();
            practicedCountsByCollectionId.put(entry.getKey(), practicedCount);
        }
        return practicedCountsByCollectionId;
    }

    private Map<UUID, List<UUID>> loadNoteIdsByCollectionId(List<NoteCollectionEntity> collections) {
        List<UUID> collectionIds = collections.stream().map(NoteCollectionEntity::getId).toList();
        Map<UUID, List<UUID>> noteIdsByCollectionId = new HashMap<>();
        for (NoteCollectionItemNoteProjection projection : itemRepository.findNoteIdsByCollectionIds(collectionIds)) {
            noteIdsByCollectionId
                    .computeIfAbsent(projection.getCollectionId(), ignored -> new ArrayList<>())
                    .add(projection.getNoteId());
        }
        return noteIdsByCollectionId;
    }

    private List<StudyPackProgressView> loadOwnedStudyPackProgressViews(List<UUID> noteIds, UUID userId) {
        if (noteIds.isEmpty()) {
            return List.of();
        }
        return studyPackRepository.findProgressViewsByNoteIdIn(noteIds).stream()
                .filter(studyPack -> Objects.equals(userId, studyPack.getOwnerUserId()))
                .filter(studyPack -> studyPack.getNoteId() != null)
                .map(StudyPackProgressView.class::cast)
                .toList();
    }

    private Map<UUID, List<StudyPackProgressView>> loadGoalStudyPacksByChildId(
            List<NoteCollectionEntity> children,
            UUID userId
    ) {
        Map<UUID, List<UUID>> noteIdsByChildId = loadNoteIdsByCollectionId(children);
        Map<UUID, List<StudyPackProgressView>> studyPacksByChildId = new LinkedHashMap<>();
        for (NoteCollectionEntity child : children) {
            studyPacksByChildId.put(child.getId(), new ArrayList<>());
        }

        Map<UUID, List<UUID>> childIdsByNoteId = new HashMap<>();
        LinkedHashSet<UUID> allNoteIds = new LinkedHashSet<>();
        for (NoteCollectionEntity child : children) {
            UUID childId = child.getId();
            for (UUID noteId : noteIdsByChildId.getOrDefault(childId, List.of())) {
                allNoteIds.add(noteId);
                childIdsByNoteId
                        .computeIfAbsent(noteId, ignored -> new ArrayList<>())
                        .add(childId);
            }
        }
        if (allNoteIds.isEmpty()) {
            return studyPacksByChildId;
        }

        for (StudyPackProgressView studyPack : loadOwnedStudyPackProgressViews(List.copyOf(allNoteIds), userId)) {
            for (UUID childId : childIdsByNoteId.getOrDefault(studyPack.getNoteId(), List.of())) {
                List<StudyPackProgressView> childStudyPacks = studyPacksByChildId.get(childId);
                if (childStudyPacks != null) {
                    childStudyPacks.add(studyPack);
                }
            }
        }
        return studyPacksByChildId;
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

    private List<UUID> extractSubmittedChildIds(List<UUID> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return List.of();
        }
        List<UUID> submittedChildIds = new ArrayList<>();
        Set<UUID> seenChildIds = new HashSet<>();
        for (UUID childId : childIds) {
            if (childId == null) {
                throw new InvalidCollectionRequestException(CHILD_ID_REQUIRED_MESSAGE);
            }
            if (!seenChildIds.add(childId)) {
                throw new InvalidCollectionRequestException(CHILD_ORDER_SET_MISMATCH_MESSAGE);
            }
            submittedChildIds.add(childId);
        }
        return submittedChildIds;
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

    private void validateSubmittedChildSetMatchesCurrent(
            List<NoteCollectionEntity> currentChildren,
            List<UUID> submittedChildIds
    ) {
        Set<UUID> currentChildIds = currentChildren.stream()
                .map(NoteCollectionEntity::getId)
                .collect(Collectors.toSet());
        Set<UUID> submittedChildIdSet = new HashSet<>(submittedChildIds);
        if (currentChildren.size() != submittedChildIds.size() || !currentChildIds.equals(submittedChildIdSet)) {
            throw new InvalidCollectionRequestException(CHILD_ORDER_SET_MISMATCH_MESSAGE);
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

    private List<NoteCollectionItemEntity> buildAdoptedItems(
            UUID collectionId,
            List<CopiedPlanItem> copiedItems,
            Instant now
    ) {
        List<NoteCollectionItemEntity> items = new ArrayList<>();
        for (int index = 0; index < copiedItems.size(); index++) {
            CopiedPlanItem copiedItem = copiedItems.get(index);
            NoteCollectionItemEntity item = new NoteCollectionItemEntity();
            item.setId(UUID.randomUUID());
            item.setCollectionId(collectionId);
            item.setNoteId(copiedItem.noteId());
            item.setLabel(copiedItem.label());
            item.setPosition(index);
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

    private NoteCollectionSummaryResponse toSummaryResponse(
            NoteCollectionEntity collection,
            int itemCount,
            int readyCount,
            int childCount,
            int notesPracticed
    ) {
        return new NoteCollectionSummaryResponse(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getVisibility().name(),
                collection.getCourseProgram(),
                collection.getSourcePlanId(),
                collection.getParentCollectionId(),
                itemCount,
                readyCount,
                childCount,
                notesPracticed,
                collection.getCreatedAt(),
                collection.getUpdatedAt()
        );
    }

    private NoteCollectionDetailResponse toDetailResponse(
            NoteCollectionEntity collection,
            List<NoteCollectionItemEntity> items
    ) {
        List<NoteCollectionItemResponse> itemResponses = toItemResponses(collection.getOwnerUserId(), items);
        NoteCollectionProgressResponse progress = toProgressResponse(itemResponses);
        return new NoteCollectionDetailResponse(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getVisibility().name(),
                collection.getCourseProgram(),
                collection.getEstimatedStudyHours(),
                collection.getTargetCompletionDate(),
                collection.getCompanion(),
                collection.getSourcePlanId(),
                collection.getParentCollectionId(),
                Math.toIntExact(collectionRepository.countByParentCollectionId(collection.getId())),
                progress.notesWithStudyPack(),
                collection.getCreatedAt(),
                collection.getUpdatedAt(),
                progress,
                itemResponses
        );
    }

    private NoteCollectionDetailResponse toPublicDetailResponse(
            NoteCollectionEntity collection,
            List<NoteCollectionItemEntity> items
    ) {
        List<NoteCollectionItemResponse> itemResponses = toPublicItemResponses(items);
        NoteCollectionProgressResponse progress = toProgressResponse(itemResponses);
        return new NoteCollectionDetailResponse(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getVisibility().name(),
                collection.getCourseProgram(),
                collection.getEstimatedStudyHours(),
                collection.getTargetCompletionDate(),
                collection.getCompanion(),
                collection.getSourcePlanId(),
                collection.getParentCollectionId(),
                Math.toIntExact(collectionRepository.countByParentCollectionId(collection.getId())),
                progress.notesWithStudyPack(),
                collection.getCreatedAt(),
                collection.getUpdatedAt(),
                progress,
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

    private int masteryPercentage(int masteredConcepts, int totalConcepts) {
        if (totalConcepts == 0) {
            return 0;
        }
        return (int) Math.round(masteredConcepts * 100.0 / totalConcepts);
    }

    private PlanReadinessResponse toPlanReadinessResponse(
            UUID collectionId,
            int totalNotes,
            List<? extends StudyPackProgressView> studyPacks,
            UUID userId,
            OffsetDateTime now
    ) {
        int notesWithStudyPack = (int) studyPacks.stream()
                .map(StudyPackProgressView::getNoteId)
                .distinct()
                .count();
        List<SubjectProgressEntry> subjects = studyPacks.isEmpty()
                ? List.of()
                : progressReportService.buildSubjectProgressEntries(
                        studyPacks,
                        userId,
                        now
                );
        ReadinessConceptTotals totals = summarizeReadinessConcepts(subjects);
        return new PlanReadinessResponse(
                collectionId,
                totalNotes,
                notesWithStudyPack,
                masteryPercentage(totals.masteredConcepts(), totals.totalConcepts()),
                totals.totalConcepts(),
                totals.masteredConcepts(),
                totals.dueConcepts(),
                totals.notPracticedConcepts(),
                subjects
        );
    }

    private ReadinessConceptTotals summarizeReadinessConcepts(List<SubjectProgressEntry> subjects) {
        int totalConcepts = subjects.stream().mapToInt(SubjectProgressEntry::totalConcepts).sum();
        int masteredConcepts = subjects.stream().mapToInt(SubjectProgressEntry::masteredConcepts).sum();
        int dueConcepts = subjects.stream().mapToInt(SubjectProgressEntry::dueConcepts).sum();
        int notPracticedConcepts = subjects.stream().mapToInt(SubjectProgressEntry::notPracticedConcepts).sum();
        return new ReadinessConceptTotals(totalConcepts, masteredConcepts, dueConcepts, notPracticedConcepts);
    }

    private GoalCollectionChildResponse toGoalChildResponse(
            NoteCollectionEntity child,
            UUID userId,
            int itemCount,
            ProgressReportService.SubjectProgressBatchResult progress
    ) {
        if (progress != null && progress.failure() == null) {
            ReadinessConceptTotals totals = summarizeReadinessConcepts(progress.subjects());
            return new GoalCollectionChildResponse(
                    child.getId(),
                    child.getTitle(),
                    child.getDescription(),
                    itemCount,
                    masteryPercentage(totals.masteredConcepts(), totals.totalConcepts()),
                    totals.masteredConcepts(),
                    totals.dueConcepts(),
                    totals.notPracticedConcepts(),
                    totals.totalConcepts(),
                    null
            );
        }

        if (progress != null && progress.failure() != null) {
            log.warn(
                    "Could not load child collection readiness collectionId={} userId={}",
                    child.getId(),
                    userId,
                    progress.failure()
            );
        }
        return new GoalCollectionChildResponse(
                child.getId(),
                child.getTitle(),
                child.getDescription(),
                itemCount,
                0,
                0,
                0,
                0,
                0,
                null
        );
    }

    private List<NoteCollectionItemResponse> toItemResponses(UUID userId, List<NoteCollectionItemEntity> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<UUID> noteIds = items.stream().map(NoteCollectionItemEntity::getNoteId).toList();
        Map<UUID, NoteCollectionNoteProjection> notesById = noteRepository.findCollectionNoteProjectionsByIdIn(noteIds).stream()
                .collect(Collectors.toMap(NoteCollectionNoteProjection::noteId, Function.identity()));
        Map<UUID, StudyPackProgressView> studyPacksByNoteId = studyPackRepository.findProgressViewsByNoteIdIn(noteIds).stream()
                .filter(studyPack -> studyPack.getNoteId() != null)
                .collect(Collectors.toMap(StudyPackProgressView::getNoteId, Function.identity(), (left, right) -> left));
        Map<UUID, UUID> generatedQuizIdByNoteId = generatedQuizRepository
                .findNoteIdsByOwnerUserIdAndNoteIdIn(userId, noteIds).stream()
                .filter(generatedQuiz -> generatedQuiz.noteId() != null)
                .collect(Collectors.toMap(
                        GeneratedQuizNoteProjection::noteId,
                        GeneratedQuizNoteProjection::generatedQuizId,
                        (left, right) -> left
                ));
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
                        generatedQuizIdByNoteId.get(item.getNoteId()),
                        lastSessionCompletedAtByNoteId.get(item.getNoteId()),
                        dueConceptsByStudyPackId
                ))
                .toList();
    }

    private List<NoteCollectionItemResponse> toPublicItemResponses(List<NoteCollectionItemEntity> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<UUID> noteIds = items.stream().map(NoteCollectionItemEntity::getNoteId).toList();
        Map<UUID, NoteCollectionNoteProjection> notesById = noteRepository.findCollectionNoteProjectionsByIdIn(noteIds).stream()
                .filter(note -> note.visibility() == NoteVisibility.PUBLIC)
                .collect(Collectors.toMap(NoteCollectionNoteProjection::noteId, Function.identity()));
        Map<UUID, StudyPackProgressView> studyPacksByNoteId = studyPackRepository.findProgressViewsByNoteIdIn(noteIds).stream()
                .filter(studyPack -> studyPack.getNoteId() != null)
                .collect(Collectors.toMap(StudyPackProgressView::getNoteId, Function.identity(), (left, right) -> left));

        return items.stream()
                .filter(item -> notesById.containsKey(item.getNoteId()))
                .map(item -> toItemResponse(
                        item,
                        notesById.get(item.getNoteId()),
                        studyPacksByNoteId.get(item.getNoteId()),
                        null,
                        null,
                        Map.of()
                ))
                .toList();
    }

    private Map<UUID, List<String>> loadDueConceptsByStudyPackId(
            UUID userId,
            Collection<? extends StudyPackProgressView> studyPacks
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
                            StudyPackProgressView::getId,
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
        if (noteIds.isEmpty()) {
            return Map.of();
        }
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
            NoteCollectionNoteProjection note,
            StudyPackProgressView studyPack,
            UUID generatedQuizId,
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
                note.title(),
                note.subject(),
                note.courseProgram(),
                NoteStudyPackStatusResolver.resolve(note.status(), studyPack != null),
                generatedQuizId == null ? null : generatedQuizId.toString(),
                lastSessionCompletedAt,
                dueConcepts.size(),
                dueConcepts.stream().limit(DUE_CONCEPT_DISPLAY_LIMIT).toList(),
                note.updatedAt()
        );
    }

    private record CopiedPlanItem(UUID noteId, String label) {
    }

    private record ReadinessConceptTotals(
            int totalConcepts,
            int masteredConcepts,
            int dueConcepts,
            int notPracticedConcepts
    ) {
    }
}
