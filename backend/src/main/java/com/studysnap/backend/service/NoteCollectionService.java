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
import com.studysnap.backend.dto.ReviewSetUpdateChange;
import com.studysnap.backend.dto.ReviewSetUpdateResponse;
import com.studysnap.backend.dto.SetNoteCollectionParentRequest;
import com.studysnap.backend.dto.SetNoteCollectionChildrenOrderRequest;
import com.studysnap.backend.dto.SetNoteCollectionOrderRequest;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.dto.UpdateNoteCollectionRequest;
import com.studysnap.backend.dto.WeeklyFocusDayEntry;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.NoteCollectionItemRemovalEntity;
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
import com.studysnap.backend.repository.NoteCollectionItemRemovalRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
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
    private static final int MAX_LEARNER_LEVEL_ANCESTOR_DEPTH = 10;
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
    private static final String SOURCE_CONNECTED = "CONNECTED";
    private static final String SOURCE_DETACHED = "DETACHED";
    private static final String UPDATE_AVAILABLE = "UPDATES_AVAILABLE";
    private static final String UPDATE_CURRENT = "ALREADY_UP_TO_DATE";
    private static final String UPDATE_DETACHED = "DETACHED_FROM_SOURCE";
    private static final String SOURCE_UPDATE_REQUIRES_ADOPTED_MESSAGE =
            "Only an adopted Review Set can check for source updates.";
    private static final String ALREADY_ADOPTED_METADATA_KEY = "alreadyAdopted";
    private static final String ADOPTED_SUBJECT_COUNT_METADATA_KEY = "adoptedSubjectCount";
    private static final String SKIPPED_SUBJECT_COUNT_METADATA_KEY = "skippedSubjectCount";
    private static final String TOTAL_NOTES_COPIED_METADATA_KEY = "totalNotesCopied";
    private static final String TOTAL_NOTES_SKIPPED_METADATA_KEY = "totalNotesSkipped";
    private static final String ADDED_COUNT_METADATA_KEY = "addedCount";
    private static final String SOURCE_METADATA_KEY = "source";
    private static final String ADD_SOURCE_INTERACTIVE = "interactive";
    private static final String ADD_SOURCE_BULK_GENERATION = "bulk_generation";

    // Sessions that are HISTORY. Everything else is in flight and is cleared when its plan is
    // deleted; see delete(...) and chk_quick_review_sessions_anchor.
    private static final List<QuickReviewSessionStatus> TERMINAL_SESSION_STATUSES = List.of(
            QuickReviewSessionStatus.COMPLETED,
            QuickReviewSessionStatus.FORFEITED
    );

    private final NoteCollectionRepository collectionRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final NoteCollectionItemRepository itemRepository;
    private final NoteCollectionItemRemovalRepository itemRemovalRepository;
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
    public List<NoteCollectionSummaryResponse> listNoteAccepting(UUID userId) {
        List<NoteCollectionEntity> collections = collectionRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId);
        if (collections.isEmpty()) {
            return List.of();
        }
        Map<UUID, Integer> itemCountsByCollectionId = loadItemCounts(collections);
        Map<UUID, Integer> readyCountsByCollectionId = loadReadyCounts(collections);
        Map<UUID, Integer> childCountsByCollectionId = loadChildCounts(collections);
        return collections.stream()
                .filter(collection -> childCountsByCollectionId.getOrDefault(collection.getId(), 0) == 0)
                // childCount is genuinely 0 here — goals are filtered out above. practicedCount is
                // NOT computed: this endpoint exists only to populate the bulk-authoring selector,
                // which reads id, title and resolvedLearnerLevel. Do not consume practicedCount
                // from this response without loading it first; it is a placeholder, not a count.
                .map(collection -> toSummaryResponse(
                        collection,
                        itemCountsByCollectionId.getOrDefault(collection.getId(), 0),
                        readyCountsByCollectionId.getOrDefault(collection.getId(), 0),
                        0,
                        0
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
        collection.setLearnerLevel(NoteAuthoringMetadataParser.parseLearnerLevelOrThrow(
                request == null ? null : request.learnerLevel()
        ));
        collection.setVisibility(CollectionVisibility.PRIVATE);
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);
        NoteCollectionEntity saved = collectionRepository.save(collection);

        List<NoteCollectionItemEntity> items = buildItems(saved.getId(), orderedNoteIds, 0, now, null);
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
        ReadinessConceptTotals readinessTotals = children.isEmpty()
                ? getDirectItemReadinessConceptTotals(collectionId, userId)
                : new ReadinessConceptTotals(
                        childResponses.stream().mapToInt(GoalCollectionChildResponse::totalConcepts).sum(),
                        childResponses.stream().mapToInt(GoalCollectionChildResponse::masteredConcepts).sum(),
                        childResponses.stream().mapToInt(GoalCollectionChildResponse::dueConcepts).sum(),
                        childResponses.stream().mapToInt(GoalCollectionChildResponse::notPracticedConcepts).sum()
                );
        int totalConcepts = readinessTotals.totalConcepts();
        int masteredConcepts = readinessTotals.masteredConcepts();
        int dueConcepts = readinessTotals.dueConcepts();
        int notPracticedConcepts = readinessTotals.notPracticedConcepts();
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

    private ReadinessConceptTotals getDirectItemReadinessConceptTotals(UUID collectionId, UUID userId) {
        List<UUID> noteIds = itemRepository.findByCollectionIdOrderByPositionAsc(collectionId).stream()
                .map(NoteCollectionItemEntity::getNoteId)
                .toList();
        List<StudyPackProgressView> studyPacks = loadOwnedStudyPackProgressViews(noteIds, userId);
        List<SubjectProgressEntry> subjects = studyPacks.isEmpty()
                ? List.of()
                : progressReportService.buildSubjectProgressEntries(studyPacks, userId, OffsetDateTime.now());
        return summarizeReadinessConcepts(subjects);
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
            if (request.learnerLevel() != null) {
                collection.setLearnerLevel(
                        NoteAuthoringMetadataParser.parseLearnerLevelOrThrow(request.learnerLevel())
                );
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

    @Transactional(readOnly = true)
    public ReviewSetUpdateResponse getSourceUpdate(UUID collectionId, UUID userId) {
        return toUpdateResponse(inspectSourceUpdate(collectionId, userId), 0, 0, 0, Set.of());
    }

    /**
     * Applies only source additions. Each note copy and placement insert is isolated so a later retry
     * can resume after a transient failure without rolling back successful earlier additions.
     */
    public ReviewSetUpdateResponse applySourceUpdate(UUID collectionId, UUID userId) {
        SourceUpdateInspection inspection = inspectSourceUpdate(collectionId, userId);
        if (inspection.detached()) {
            return toUpdateResponse(inspection, 0, 0, 0, Set.of());
        }

        int notesAdded = 0;
        int subjectPlansAdded = 0;
        int additionsResolvedByConcurrentPass = 0;
        int skipped = (int) inspection.changes().stream()
                .filter(change -> "SKIPPED_NOT_PUBLIC".equals(change.type()))
                .count();
        Set<String> appliedKeys = new HashSet<>();

        for (PendingSubjectAddition pending : inspection.subjectAdditions()) {
            try {
                CreatedSubjectAddition created = createSubjectAddition(
                        inspection.adoptedRoot(),
                        pending.sourcePlan(),
                        userId
                );
                NoteCollectionEntity child = created.collection();
                if (!Objects.equals(child.getParentCollectionId(), inspection.adoptedRoot().getId())) {
                    continue;
                }
                if (created.created()) {
                    subjectPlansAdded++;
                    appliedKeys.add(changeKey("ADDED_SUBJECT_PLAN", pending.sourcePlan().getId(), null));
                } else {
                    additionsResolvedByConcurrentPass++;
                }
                for (NoteCollectionItemEntity sourceItem : pending.placements()) {
                    try {
                        if (applyPlacementAddition(
                                new PendingPlacementAddition(child, pending.sourcePlan(), sourceItem),
                                userId
                        )) {
                            notesAdded++;
                            appliedKeys.add(changeKey(
                                    "ADDED_NOTE",
                                    pending.sourcePlan().getId(),
                                    sourceItem.getNoteId()
                            ));
                        } else {
                            additionsResolvedByConcurrentPass++;
                        }
                    } catch (RuntimeException exception) {
                        skipped++;
                        log.warn(
                                "review_set_update_item_skipped adoptedCollectionId={} sourcePlanId={} noteId={} userId={}",
                                child.getId(),
                                pending.sourcePlan().getId(),
                                sourceItem.getNoteId(),
                                userId,
                                exception
                        );
                    }
                }
            } catch (RuntimeException exception) {
                skipped++;
                log.warn(
                        "review_set_update_subject_skipped adoptedCollectionId={} sourcePlanId={} userId={}",
                        collectionId,
                        pending.sourcePlan().getId(),
                        userId,
                        exception
                );
            }
        }

        for (PendingPlacementAddition pending : inspection.placementAdditions()) {
            try {
                if (applyPlacementAddition(pending, userId)) {
                    notesAdded++;
                    appliedKeys.add(changeKey(
                            "ADDED_NOTE",
                            pending.sourcePlan().getId(),
                            pending.sourceItem().getNoteId()
                    ));
                } else {
                    additionsResolvedByConcurrentPass++;
                }
            } catch (RuntimeException exception) {
                skipped++;
                log.warn(
                        "review_set_update_item_skipped adoptedCollectionId={} sourcePlanId={} noteId={} userId={}",
                        pending.adoptedPlan().getId(),
                        pending.sourcePlan().getId(),
                        pending.sourceItem().getNoteId(),
                        userId,
                        exception
                );
            }
        }

        if (collectionRepository.findByIdAndVisibility(
                inspection.sourceRoot().getId(),
                CollectionVisibility.PUBLIC
        ).isEmpty()) {
            return new ReviewSetUpdateResponse(
                    collectionId,
                    SOURCE_DETACHED,
                    UPDATE_DETACHED,
                    0,
                    notesAdded,
                    subjectPlansAdded,
                    skipped,
                    markApplied(inspection.changes(), appliedKeys)
            );
        }

        acknowledgeSourceSnapshots(inspection);
        int additionsRemaining = Math.max(
                0,
                inspection.additionsAvailable()
                        - notesAdded
                        - subjectPlansAdded
                        - additionsResolvedByConcurrentPass
        );
        String status = skipped > 0 ? "PARTIALLY_UPDATED" : "UPDATED";
        if (notesAdded == 0 && subjectPlansAdded == 0 && additionsRemaining == 0) {
            status = UPDATE_CURRENT;
        }
        return new ReviewSetUpdateResponse(
                collectionId,
                SOURCE_CONNECTED,
                status,
                additionsRemaining,
                notesAdded,
                subjectPlansAdded,
                skipped,
                markApplied(inspection.changes(), appliedKeys)
        );
    }

    @Transactional
    public void delete(UUID collectionId, UUID userId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        boolean wasTopLevel = collection.getParentCollectionId() == null;
        // ⚠️ v0.113.0. quick_review_sessions.source_collection_id is ON DELETE SET NULL, and the anchor
        // CHECK permits an anchorless row only when the session is COMPLETED or FORFEITED. A
        // GENERATING, FAILED, IN_PROGRESS or PAUSED plan-scoped session would therefore make this
        // delete fail on a constraint violation, so those are cleared first. COMPLETED and FORFEITED
        // sessions are LEFT for the FK to orphan on purpose -- they are the learner's history, and
        // session_state.sourceNoteRefs keeps them reachable from every note they sampled.
        quickReviewSessionRepository.deleteBySourceCollectionIdAndStatusNotIn(
                collectionId,
                TERMINAL_SESSION_STATUSES
        );
        collectionRepository.delete(collection);
        if (wasTopLevel) {
            reassertPrimaryInvariant(userId);
        }
    }

    @Transactional
    public NoteCollectionDetailResponse addItems(UUID collectionId, UUID userId, AddNoteCollectionItemsRequest request) {
        return addItems(collectionId, userId, request, ADD_SOURCE_INTERACTIVE);
    }

    /**
     * Membership write shared by the interactive API and bulk authoring.
     *
     * <p>{@code source} exists only so {@code NOTE_ADDED_TO_COLLECTION} can be read as a learner
     * signal. {@link #addGeneratedItems} routes through here, so a curator generating a batch into a
     * Review Set would otherwise land in the same event stream as a learner deciding a note belongs
     * in their plan, and the two cannot be told apart afterwards.
     */
    private NoteCollectionDetailResponse addItems(
            UUID collectionId,
            UUID userId,
            AddNoteCollectionItemsRequest request,
            String source
    ) {
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
        String label = validateOptionalLabel(request == null ? null : request.label());
        List<NoteCollectionItemEntity> newItems = buildItems(collectionId, newNoteIds, nextPosition, now, label);
        itemRepository.saveAll(newItems);
        touch(collection, now);
        NoteCollectionEntity saved = collectionRepository.save(collection);

        // Guarded on newItems rather than on the request: addItems filters out notes the set already
        // holds, so re-adding an existing note is a no-op that must not emit a zero-count event.
        // trackEvent swallows its own failures (AnalyticsService:49-59), so no wrapper is needed here
        // and none is used by COLLECTION_CREATED above.
        if (!newItems.isEmpty()) {
            analyticsService.trackEvent(
                    userId,
                    AnalyticsEventType.NOTE_ADDED_TO_COLLECTION,
                    collectionId,
                    Map.of(
                            ADDED_COUNT_METADATA_KEY, newItems.size(),
                            SOURCE_METADATA_KEY, source
                    )
            );
        }

        List<NoteCollectionItemEntity> allItems = new ArrayList<>(currentItems);
        allItems.addAll(newItems);
        allItems.sort((left, right) -> Integer.compare(left.getPosition(), right.getPosition()));
        return toDetailResponse(saved, allItems);
    }

    /**
     * Bulk-authoring variant of {@link #addItems}: skips ids that no longer resolve to a note
     * this user owns, rather than failing the entire write.
     *
     * <p>{@code addItems} throws {@link com.studysnap.backend.exception.NoteNotFoundException} on
     * the first unresolvable id, which is correct for the interactive API — asking to add a note
     * you do not own should 404. It is wrong for a bulk batch, which runs for minutes while its
     * notes are already visible in the Library: deleting a single unwanted note made the whole
     * membership write throw, so none of the other notes were added and the only signal was a
     * server log. Input order is preserved so batch order still determines position.
     */
    @Transactional
    public int addGeneratedItems(UUID collectionId, UUID userId, List<UUID> noteIds, String label) {
        List<UUID> requested = dedupeNoteIds(noteIds);
        if (requested.isEmpty()) {
            return 0;
        }
        Set<UUID> stillOwned = noteRepository.findByOwnerUserIdAndIdIn(userId, requested).stream()
                .map(NoteEntity::getId)
                .collect(Collectors.toSet());
        List<UUID> resolvable = requested.stream().filter(stillOwned::contains).toList();
        if (resolvable.isEmpty()) {
            return 0;
        }
        addItems(collectionId, userId, new AddNoteCollectionItemsRequest(resolvable, label), ADD_SOURCE_BULK_GENERATION);
        return resolvable.size();
    }

    /**
     * Queue-time guard for a bulk batch's target Review Set: the caller must own it and it must be
     * able to hold notes. Returns nothing — the depth is resolved client-side for the form pre-fill,
     * and resolving it here as well was a wasted ancestor walk whose result every caller discarded.
     */
    @Transactional(readOnly = true)
    public void validateNoteAcceptingCollection(UUID collectionId, UUID userId) {
        getOwnedCollectionOrThrow(collectionId, userId);
        if (collectionRepository.countByParentCollectionId(collectionId) > 0) {
            throw new InvalidCollectionRequestException(GOAL_CANNOT_ACCEPT_NOTES_MESSAGE);
        }
    }

    @Transactional(readOnly = true)
    public Optional<LearnerLevel> resolveInheritedLearnerLevel(UUID collectionId) {
        UUID currentId = collectionId;
        UUID ownerUserId = null;
        Set<UUID> visited = new HashSet<>();
        for (int depth = 0; currentId != null && depth < MAX_LEARNER_LEVEL_ANCESTOR_DEPTH; depth++) {
            if (!visited.add(currentId)) {
                return Optional.empty();
            }
            Optional<NoteCollectionEntity> current = collectionRepository.findById(currentId);
            if (current.isEmpty()) {
                return Optional.empty();
            }
            NoteCollectionEntity currentCollection = current.get();
            if (ownerUserId == null) {
                ownerUserId = currentCollection.getOwnerUserId();
            } else if (!ownerUserId.equals(currentCollection.getOwnerUserId())) {
                return Optional.empty();
            }
            // Return the moment a level is found. Continuing the walk after this point can only
            // discard an answer that is already correct: a cycle, a depth-cap exit, or an
            // owner mismatch further up would fall through to Optional.empty() and drop the
            // nearest level — including a collection's OWN explicitly-set level.
            if (currentCollection.getLearnerLevel() != null) {
                return Optional.of(currentCollection.getLearnerLevel());
            }
            currentId = currentCollection.getParentCollectionId();
        }
        return Optional.empty();
    }

    @Transactional
    public void removeItem(UUID collectionId, UUID userId, UUID noteId) {
        NoteCollectionEntity collection = getOwnedCollectionOrThrow(collectionId, userId);
        NoteCollectionItemEntity item = itemRepository.findByCollectionIdAndNoteId(collectionId, noteId)
                .orElseThrow(CollectionItemNotFoundException::new);
        if (collection.getSourcePlanId() != null && item.getSourceSyncedAt() != null) {
            noteRepository.findById(noteId)
                    .map(this::sourceNoteIdForAdoptedCopy)
                    .flatMap(Function.identity())
                    .ifPresent(sourceNoteId -> {
                        NoteCollectionItemRemovalEntity removal = new NoteCollectionItemRemovalEntity();
                        removal.setAdoptedCollectionId(collectionId);
                        removal.setSourcePlanId(collection.getSourcePlanId());
                        removal.setSourceNoteId(sourceNoteId);
                        removal.setRemovedAt(Instant.now());
                        itemRemovalRepository.save(removal);
                    });
        }
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
        if (!CuratorAuthoringPredicate.isCurator(user) && collection.getSourcePlanId() == null) {
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
                copiedItems.add(new CopiedPlanItem(
                        UUID.fromString(copiedNote.id()),
                        sourceItem.getLabel(),
                        sourceItem.getPosition()
                ));
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
            // Authored depth travels with the adopted plan. Without it the adopted copy loses the
            // curator's depth, so the bulk-authoring pre-fill resolves to nothing for exactly the
            // population that adopts Official Review Sets.
            collection.setLearnerLevel(source.getLearnerLevel());
            collection.setEstimatedStudyHours(source.getEstimatedStudyHours());
            if (shouldCopyCompanion(source, userId)) {
                collection.setCompanion(source.getCompanion());
            }
            // targetCompletionDate is deliberately never copied from source (including on a self-copy where
            // ownerUserId == source's owner) — a curator's or previous owner's target date means nothing to
            // the new owner, so it stays null on the fresh entity until the new owner sets their own.
            collection.setSourcePlanId(source.getId());
            collection.setSourceTitleAtSync(source.getTitle());
            collection.setSourceParentIdAtSync(source.getParentCollectionId());
            collection.setSourcePositionAtSync(source.getSiblingPosition());
            collection.setSourceSyncedAt(now);
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
            // Authored depth travels with the adopted plan. Without it the adopted copy loses the
            // curator's depth, so the bulk-authoring pre-fill resolves to nothing for exactly the
            // population that adopts Official Review Sets.
            collection.setLearnerLevel(source.getLearnerLevel());
            collection.setEstimatedStudyHours(source.getEstimatedStudyHours());
            if (shouldCopyCompanion(source, userId)) {
                collection.setCompanion(source.getCompanion());
            }
            // targetCompletionDate is deliberately never copied from source (including on a self-copy where
            // ownerUserId == source's owner) — a curator's or previous owner's target date means nothing to
            // the new owner, so it stays null on the fresh entity until the new owner sets their own.
            collection.setSourcePlanId(source.getId());
            collection.setSourceTitleAtSync(source.getTitle());
            collection.setSourceParentIdAtSync(source.getParentCollectionId());
            collection.setSourcePositionAtSync(source.getSiblingPosition());
            collection.setSourceSyncedAt(now);
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

    private SourceUpdateInspection inspectSourceUpdate(UUID collectionId, UUID userId) {
        NoteCollectionEntity adoptedRoot = getOwnedCollectionOrThrow(collectionId, userId);
        if (adoptedRoot.getSourcePlanId() == null) {
            throw new InvalidCollectionRequestException(SOURCE_UPDATE_REQUIRES_ADOPTED_MESSAGE);
        }
        Optional<NoteCollectionEntity> sourceRoot = collectionRepository.findByIdAndVisibility(
                adoptedRoot.getSourcePlanId(),
                CollectionVisibility.PUBLIC
        );
        if (sourceRoot.isEmpty()) {
            return SourceUpdateInspection.detached(adoptedRoot);
        }

        List<NoteCollectionEntity> sourceChildren = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(
                        sourceRoot.get().getId(),
                        sourceRoot.get().getOwnerUserId()
                );
        List<NoteCollectionEntity> sourcePlans = sourceChildren.isEmpty()
                ? List.of(sourceRoot.get())
                : sourceChildren;
        List<NoteCollectionEntity> adoptedChildren = collectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(adoptedRoot.getId(), userId);
        List<NoteCollectionEntity> adoptedPlans = sourceChildren.isEmpty()
                ? List.of(adoptedRoot)
                : adoptedChildren;
        Map<UUID, NoteCollectionEntity> adoptedBySourcePlan = adoptedPlans.stream()
                .filter(plan -> plan.getSourcePlanId() != null)
                .collect(Collectors.toMap(
                        NoteCollectionEntity::getSourcePlanId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        Map<UUID, List<NoteCollectionItemEntity>> sourceItemsByPlan = new LinkedHashMap<>();
        Set<UUID> sourceNoteIds = new LinkedHashSet<>();
        Map<UUID, Set<UUID>> currentLocationsBySourceNote = new HashMap<>();
        for (NoteCollectionEntity sourcePlan : sourcePlans) {
            List<NoteCollectionItemEntity> sourceItems =
                    itemRepository.findByCollectionIdOrderByPositionAsc(sourcePlan.getId());
            sourceItemsByPlan.put(sourcePlan.getId(), sourceItems);
            for (NoteCollectionItemEntity sourceItem : sourceItems) {
                sourceNoteIds.add(sourceItem.getNoteId());
                currentLocationsBySourceNote
                        .computeIfAbsent(sourceItem.getNoteId(), ignored -> new LinkedHashSet<>())
                        .add(sourcePlan.getId());
            }
        }
        Map<UUID, NoteEntity> sourceNotesById = noteRepository.findAllById(sourceNoteIds).stream()
                .collect(Collectors.toMap(NoteEntity::getId, Function.identity()));

        List<UUID> adoptedPlanIds = adoptedPlans.stream().map(NoteCollectionEntity::getId).toList();
        Map<UUID, Set<UUID>> tombstonesByAdoptedPlan = new HashMap<>();
        Map<UUID, Set<UUID>> removedLocationsBySourceNote = new HashMap<>();
        if (!adoptedPlanIds.isEmpty()) {
            for (NoteCollectionItemRemovalEntity removal :
                    itemRemovalRepository.findByAdoptedCollectionIdIn(adoptedPlanIds)) {
                tombstonesByAdoptedPlan
                        .computeIfAbsent(removal.getAdoptedCollectionId(), ignored -> new HashSet<>())
                        .add(removal.getSourceNoteId());
                removedLocationsBySourceNote
                        .computeIfAbsent(removal.getSourceNoteId(), ignored -> new HashSet<>())
                        .add(removal.getSourcePlanId());
            }
        }

        Map<UUID, Map<UUID, AdoptedPlacement>> adoptedPlacementsByPlan = new HashMap<>();
        Map<UUID, Set<UUID>> adoptedLocationsBySourceNote = new HashMap<>();
        for (NoteCollectionEntity adoptedPlan : adoptedPlans) {
            List<NoteCollectionItemEntity> adoptedItems =
                    itemRepository.findByCollectionIdOrderByPositionAsc(adoptedPlan.getId());
            Map<UUID, NoteEntity> adoptedNotes = noteRepository.findAllById(
                    adoptedItems.stream().map(NoteCollectionItemEntity::getNoteId).toList()
            ).stream().collect(Collectors.toMap(NoteEntity::getId, Function.identity()));
            Map<UUID, AdoptedPlacement> bySourceNote = new LinkedHashMap<>();
            for (NoteCollectionItemEntity adoptedItem : adoptedItems) {
                NoteEntity adoptedNote = adoptedNotes.get(adoptedItem.getNoteId());
                if (adoptedNote == null) {
                    continue;
                }
                sourceNoteIdForAdoptedCopy(adoptedNote).ifPresent(sourceNoteId -> {
                    bySourceNote.put(sourceNoteId, new AdoptedPlacement(adoptedItem, adoptedNote));
                    if (adoptedPlan.getSourcePlanId() != null) {
                        adoptedLocationsBySourceNote
                                .computeIfAbsent(sourceNoteId, ignored -> new LinkedHashSet<>())
                                .add(adoptedPlan.getSourcePlanId());
                    }
                });
            }
            adoptedPlacementsByPlan.put(adoptedPlan.getId(), bySourceNote);
        }

        List<ReviewSetUpdateChange> changes = new ArrayList<>();
        List<PendingSubjectAddition> subjectAdditions = new ArrayList<>();
        List<PendingPlacementAddition> placementAdditions = new ArrayList<>();
        Set<String> emittedChanges = new HashSet<>();
        addCollectionDrift(changes, emittedChanges, adoptedRoot, sourceRoot.get());

        for (NoteCollectionEntity sourcePlan : sourcePlans) {
            NoteCollectionEntity adoptedPlan = adoptedBySourcePlan.get(sourcePlan.getId());
            if (adoptedPlan == null) {
                Optional<NoteCollectionEntity> adoptedElsewhere =
                        collectionRepository.findByOwnerUserIdAndSourcePlanId(userId, sourcePlan.getId());
                if (adoptedElsewhere.isPresent()) {
                    addChange(changes, emittedChanges, new ReviewSetUpdateChange(
                            "MOVED",
                            sourcePlan.getId(),
                            null,
                            sourcePlan.getTitle(),
                            null,
                            adoptedElsewhere.get().getParentCollectionId() == null ? "Standalone" : "Another Goal",
                            sourceRoot.get().getTitle(),
                            false
                    ));
                    continue;
                }
                List<NoteCollectionItemEntity> additionsForSubject = new ArrayList<>();
                for (NoteCollectionItemEntity sourceItem :
                        sourceItemsByPlan.getOrDefault(sourcePlan.getId(), List.of())) {
                    NoteEntity sourceNote = sourceNotesById.get(sourceItem.getNoteId());
                    Set<UUID> previousLocations = new HashSet<>(adoptedLocationsBySourceNote
                            .getOrDefault(sourceItem.getNoteId(), Set.of()));
                    previousLocations.addAll(removedLocationsBySourceNote
                            .getOrDefault(sourceItem.getNoteId(), Set.of()));
                    boolean moved = previousLocations
                            .stream()
                            .anyMatch(previousPlan -> !currentLocationsBySourceNote
                                    .getOrDefault(sourceItem.getNoteId(), Set.of())
                                    .contains(previousPlan));
                    if (moved) {
                        addChange(changes, emittedChanges, placementChange(
                                "MOVED", sourcePlan, sourceItem.getNoteId(),
                                sourceNote == null ? null : sourceNote.getTitle(), null, sourcePlan.getTitle()
                        ));
                    } else if (sourceNote == null || sourceNote.getVisibility() != NoteVisibility.PUBLIC) {
                        addChange(changes, emittedChanges, placementChange(
                                "SKIPPED_NOT_PUBLIC", sourcePlan, sourceItem.getNoteId(),
                                sourceNote == null ? null : sourceNote.getTitle(), null, null
                        ));
                    } else {
                        additionsForSubject.add(sourceItem);
                        addChange(changes, emittedChanges, placementChange(
                                "ADDED_NOTE", sourcePlan, sourceItem.getNoteId(), sourceNote.getTitle(), null,
                                sourceNote.getTitle()
                        ));
                    }
                }
                subjectAdditions.add(new PendingSubjectAddition(sourcePlan, additionsForSubject));
                addChange(changes, emittedChanges, new ReviewSetUpdateChange(
                        "ADDED_SUBJECT_PLAN",
                        sourcePlan.getId(),
                        null,
                        sourcePlan.getTitle(),
                        null,
                        null,
                        sourcePlan.getTitle(),
                        false
                ));
                continue;
            }
            addCollectionDrift(changes, emittedChanges, adoptedPlan, sourcePlan);
            Map<UUID, AdoptedPlacement> adoptedPlacements =
                    adoptedPlacementsByPlan.getOrDefault(adoptedPlan.getId(), Map.of());
            Set<UUID> currentSourceIds = sourceItemsByPlan.getOrDefault(sourcePlan.getId(), List.of()).stream()
                    .map(NoteCollectionItemEntity::getNoteId)
                    .collect(Collectors.toSet());

            for (NoteCollectionItemEntity sourceItem :
                    sourceItemsByPlan.getOrDefault(sourcePlan.getId(), List.of())) {
                AdoptedPlacement adoptedPlacement = adoptedPlacements.get(sourceItem.getNoteId());
                NoteEntity sourceNote = sourceNotesById.get(sourceItem.getNoteId());
                String noteTitle = sourceNote == null ? null : sourceNote.getTitle();
                if (adoptedPlacement == null) {
                    if (tombstonesByAdoptedPlan.getOrDefault(adoptedPlan.getId(), Set.of())
                            .contains(sourceItem.getNoteId())) {
                        continue;
                    }
                    Set<UUID> previousLocations = new HashSet<>(adoptedLocationsBySourceNote
                            .getOrDefault(sourceItem.getNoteId(), Set.of()));
                    previousLocations.addAll(removedLocationsBySourceNote
                            .getOrDefault(sourceItem.getNoteId(), Set.of()));
                    boolean moved = previousLocations
                            .stream()
                            .anyMatch(previousPlan -> !currentLocationsBySourceNote
                                    .getOrDefault(sourceItem.getNoteId(), Set.of())
                                    .contains(previousPlan));
                    if (moved) {
                        addChange(changes, emittedChanges, placementChange(
                                "MOVED",
                                sourcePlan,
                                sourceItem.getNoteId(),
                                noteTitle,
                                null,
                                sourcePlan.getTitle()
                        ));
                    } else if (sourceNote == null || sourceNote.getVisibility() != NoteVisibility.PUBLIC) {
                        addChange(changes, emittedChanges, placementChange(
                                "SKIPPED_NOT_PUBLIC",
                                sourcePlan,
                                sourceItem.getNoteId(),
                                noteTitle,
                                null,
                                null
                        ));
                    } else {
                        placementAdditions.add(new PendingPlacementAddition(adoptedPlan, sourcePlan, sourceItem));
                        addChange(changes, emittedChanges, placementChange(
                                "ADDED_NOTE",
                                sourcePlan,
                                sourceItem.getNoteId(),
                                noteTitle,
                                null,
                                noteTitle
                        ));
                    }
                    continue;
                }

                NoteCollectionItemEntity synced = adoptedPlacement.item();
                if (synced.getSourceSyncedAt() != null
                        && !Objects.equals(synced.getSourceLabelAtSync(), sourceItem.getLabel())) {
                    addChange(changes, emittedChanges, placementChange(
                            "RENAMED",
                            sourcePlan,
                            sourceItem.getNoteId(),
                            noteTitle,
                            synced.getSourceLabelAtSync(),
                            sourceItem.getLabel()
                    ));
                }
                if (synced.getSourcePositionAtSync() != null
                        && synced.getSourcePositionAtSync() != sourceItem.getPosition()) {
                    addChange(changes, emittedChanges, placementChange(
                            "REORDERED",
                            sourcePlan,
                            sourceItem.getNoteId(),
                            noteTitle,
                            synced.getSourcePositionAtSync().toString(),
                            Integer.toString(sourceItem.getPosition())
                    ));
                }
            }

            for (Map.Entry<UUID, AdoptedPlacement> entry : adoptedPlacements.entrySet()) {
                if (currentSourceIds.contains(entry.getKey())) {
                    continue;
                }
                boolean moved = currentLocationsBySourceNote.containsKey(entry.getKey());
                addChange(changes, emittedChanges, placementChange(
                        moved ? "MOVED" : "RETIRED",
                        sourcePlan,
                        entry.getKey(),
                        entry.getValue().note().getCopiedFromTitle(),
                        sourcePlan.getTitle(),
                        moved ? "Another Subject Plan" : null
                ));
            }
        }

        Set<UUID> currentSourcePlanIds = sourcePlans.stream()
                .map(NoteCollectionEntity::getId)
                .collect(Collectors.toSet());
        for (NoteCollectionEntity adoptedPlan : adoptedPlans) {
            if (adoptedPlan == adoptedRoot || adoptedPlan.getSourcePlanId() == null
                    || currentSourcePlanIds.contains(adoptedPlan.getSourcePlanId())) {
                continue;
            }
            Optional<NoteCollectionEntity> relocated = collectionRepository.findByIdAndVisibility(
                    adoptedPlan.getSourcePlanId(),
                    CollectionVisibility.PUBLIC
            );
            addChange(changes, emittedChanges, new ReviewSetUpdateChange(
                    relocated.isPresent() ? "MOVED" : "RETIRED",
                    adoptedPlan.getSourcePlanId(),
                    null,
                    adoptedPlan.getSourceTitleAtSync(),
                    null,
                    adoptedPlan.getSourceTitleAtSync(),
                    relocated.map(NoteCollectionEntity::getTitle).orElse(null),
                    false
            ));
        }

        return new SourceUpdateInspection(
                adoptedRoot,
                sourceRoot.get(),
                sourcePlans,
                adoptedBySourcePlan,
                sourceItemsByPlan,
                adoptedPlacementsByPlan,
                changes,
                subjectAdditions,
                placementAdditions,
                false
        );
    }

    private boolean applyPlacementAddition(PendingPlacementAddition pending, UUID userId) {
        if (!isPublicSourceNote(pending.sourceItem().getNoteId())) {
            throw new NoteNotFoundException();
        }
        NoteResponse copied = noteService.copyNote(pending.sourceItem().getNoteId().toString(), userId, true);
        UUID copiedNoteId = UUID.fromString(copied.id());
        try {
            Boolean inserted = collectionTransactionOperations.execute(status -> {
                Optional<NoteCollectionItemEntity> existing = itemRepository.findByCollectionIdAndNoteId(
                        pending.adoptedPlan().getId(),
                        copiedNoteId
                );
                if (existing.isPresent()) {
                    return false;
                }
                List<NoteCollectionItemEntity> current = itemRepository.findByCollectionIdOrderByPositionAsc(
                        pending.adoptedPlan().getId()
                );
                NoteCollectionItemEntity item = new NoteCollectionItemEntity();
                item.setId(UUID.randomUUID());
                item.setCollectionId(pending.adoptedPlan().getId());
                item.setNoteId(copiedNoteId);
                item.setLabel(pending.sourceItem().getLabel());
                item.setPosition(current.stream().mapToInt(NoteCollectionItemEntity::getPosition).max().orElse(-1) + 1);
                Instant now = Instant.now();
                item.setSourceLabelAtSync(pending.sourceItem().getLabel());
                item.setSourcePositionAtSync(pending.sourceItem().getPosition());
                item.setSourceSyncedAt(now);
                item.setCreatedAt(now);
                itemRepository.saveAndFlush(item);
                touch(pending.adoptedPlan(), now);
                collectionRepository.save(pending.adoptedPlan());
                return true;
            });
            return Boolean.TRUE.equals(inserted);
        } catch (DataIntegrityViolationException raceLost) {
            if (itemRepository.findByCollectionIdAndNoteId(pending.adoptedPlan().getId(), copiedNoteId).isPresent()) {
                return false;
            }
            throw raceLost;
        }
    }

    private CreatedSubjectAddition createSubjectAddition(
            NoteCollectionEntity adoptedRoot,
            NoteCollectionEntity sourcePlan,
            UUID userId
    ) {
        try {
            return collectionTransactionOperations.execute(status -> {
                Optional<NoteCollectionEntity> existing =
                        collectionRepository.findByOwnerUserIdAndSourcePlanIdForUpdate(userId, sourcePlan.getId());
                if (existing.isPresent()) {
                    return new CreatedSubjectAddition(existing.get(), false);
                }
                Instant now = Instant.now();
                NoteCollectionEntity child = new NoteCollectionEntity();
                child.setId(UUID.randomUUID());
                child.setOwnerUserId(userId);
                child.setTitle(sourcePlan.getTitle());
                child.setDescription(sourcePlan.getDescription());
                child.setVisibility(CollectionVisibility.PRIVATE);
                child.setCourseProgram(sourcePlan.getCourseProgram());
                child.setLearnerLevel(sourcePlan.getLearnerLevel());
                child.setEstimatedStudyHours(sourcePlan.getEstimatedStudyHours());
                child.setSourcePlanId(sourcePlan.getId());
                child.setSourceTitleAtSync(sourcePlan.getTitle());
                child.setSourceParentIdAtSync(sourcePlan.getParentCollectionId());
                child.setSourcePositionAtSync(sourcePlan.getSiblingPosition());
                child.setSourceSyncedAt(now);
                child.setParentCollectionId(adoptedRoot.getId());
                child.setSiblingPosition(nextChildPosition(adoptedRoot, userId));
                child.setCreatedAt(now);
                child.setUpdatedAt(now);
                return new CreatedSubjectAddition(collectionRepository.saveAndFlush(child), true);
            });
        } catch (DataIntegrityViolationException raceLost) {
            NoteCollectionEntity winner = collectionRepository
                    .findByOwnerUserIdAndSourcePlanId(userId, sourcePlan.getId())
                    .orElseThrow(() -> raceLost);
            return new CreatedSubjectAddition(winner, false);
        }
    }

    private void acknowledgeSourceSnapshots(SourceUpdateInspection inspection) {
        Instant now = Instant.now();
        inspection.adoptedRoot().setSourceTitleAtSync(inspection.sourceRoot().getTitle());
        inspection.adoptedRoot().setSourceParentIdAtSync(inspection.sourceRoot().getParentCollectionId());
        inspection.adoptedRoot().setSourcePositionAtSync(inspection.sourceRoot().getSiblingPosition());
        inspection.adoptedRoot().setSourceSyncedAt(now);
        collectionRepository.save(inspection.adoptedRoot());

        for (NoteCollectionEntity sourcePlan : inspection.sourcePlans()) {
            NoteCollectionEntity adoptedPlan = inspection.adoptedBySourcePlan().get(sourcePlan.getId());
            if (adoptedPlan == null) {
                continue;
            }
            adoptedPlan.setSourceTitleAtSync(sourcePlan.getTitle());
            adoptedPlan.setSourceParentIdAtSync(sourcePlan.getParentCollectionId());
            adoptedPlan.setSourcePositionAtSync(sourcePlan.getSiblingPosition());
            adoptedPlan.setSourceSyncedAt(now);
            collectionRepository.save(adoptedPlan);
            Map<UUID, AdoptedPlacement> placements = inspection.adoptedPlacementsByPlan()
                    .getOrDefault(adoptedPlan.getId(), Map.of());
            for (NoteCollectionItemEntity sourceItem :
                    inspection.sourceItemsByPlan().getOrDefault(sourcePlan.getId(), List.of())) {
                AdoptedPlacement placement = placements.get(sourceItem.getNoteId());
                if (placement == null) {
                    continue;
                }
                placement.item().setSourceLabelAtSync(sourceItem.getLabel());
                placement.item().setSourcePositionAtSync(sourceItem.getPosition());
                placement.item().setSourceSyncedAt(now);
                itemRepository.save(placement.item());
            }
        }
    }

    private void addCollectionDrift(
            List<ReviewSetUpdateChange> changes,
            Set<String> emitted,
            NoteCollectionEntity adopted,
            NoteCollectionEntity source
    ) {
        if (adopted.getSourceTitleAtSync() != null
                && !Objects.equals(adopted.getSourceTitleAtSync(), source.getTitle())) {
            addChange(changes, emitted, new ReviewSetUpdateChange(
                    "RENAMED",
                    source.getId(),
                    null,
                    source.getTitle(),
                    null,
                    adopted.getSourceTitleAtSync(),
                    source.getTitle(),
                    false
            ));
        }
        if (adopted.getSourceSyncedAt() != null
                && !Objects.equals(adopted.getSourcePositionAtSync(), source.getSiblingPosition())) {
            addChange(changes, emitted, new ReviewSetUpdateChange(
                    "REORDERED",
                    source.getId(),
                    null,
                    source.getTitle(),
                    null,
                    adopted.getSourcePositionAtSync().toString(),
                    source.getSiblingPosition() == null ? null : source.getSiblingPosition().toString(),
                    false
            ));
        }
        if (adopted.getSourceSyncedAt() != null
                && !Objects.equals(adopted.getSourceParentIdAtSync(), source.getParentCollectionId())) {
            addChange(changes, emitted, new ReviewSetUpdateChange(
                    "MOVED",
                    source.getId(),
                    null,
                    source.getTitle(),
                    null,
                    adopted.getSourceParentIdAtSync().toString(),
                    source.getParentCollectionId() == null ? null : source.getParentCollectionId().toString(),
                    false
            ));
        }
    }

    private ReviewSetUpdateChange placementChange(
            String type,
            NoteCollectionEntity sourcePlan,
            UUID sourceNoteId,
            String noteTitle,
            String previousValue,
            String currentValue
    ) {
        return new ReviewSetUpdateChange(
                type,
                sourcePlan.getId(),
                sourceNoteId,
                sourcePlan.getTitle(),
                noteTitle,
                previousValue,
                currentValue,
                false
        );
    }

    private void addChange(
            List<ReviewSetUpdateChange> changes,
            Set<String> emitted,
            ReviewSetUpdateChange change
    ) {
        if (emitted.add(changeKey(change.type(), change.sourcePlanId(), change.sourceNoteId()))) {
            changes.add(change);
        }
    }

    private String changeKey(String type, UUID sourcePlanId, UUID sourceNoteId) {
        return type + ":" + sourcePlanId + ":" + sourceNoteId;
    }

    private List<ReviewSetUpdateChange> markApplied(
            List<ReviewSetUpdateChange> changes,
            Set<String> appliedKeys
    ) {
        return changes.stream().map(change -> new ReviewSetUpdateChange(
                change.type(),
                change.sourcePlanId(),
                change.sourceNoteId(),
                change.subjectTitle(),
                change.noteTitle(),
                change.previousValue(),
                change.currentValue(),
                appliedKeys.contains(changeKey(change.type(), change.sourcePlanId(), change.sourceNoteId()))
        )).toList();
    }

    private ReviewSetUpdateResponse toUpdateResponse(
            SourceUpdateInspection inspection,
            int notesAdded,
            int subjectPlansAdded,
            int skipped,
            Set<String> appliedKeys
    ) {
        if (inspection.detached()) {
            return new ReviewSetUpdateResponse(
                    inspection.adoptedRoot().getId(),
                    SOURCE_DETACHED,
                    UPDATE_DETACHED,
                    0,
                    notesAdded,
                    subjectPlansAdded,
                    skipped,
                    List.of()
            );
        }
        return new ReviewSetUpdateResponse(
                inspection.adoptedRoot().getId(),
                SOURCE_CONNECTED,
                inspection.additionsAvailable() > 0 ? UPDATE_AVAILABLE : UPDATE_CURRENT,
                inspection.additionsAvailable(),
                notesAdded,
                subjectPlansAdded,
                skipped,
                markApplied(inspection.changes(), appliedKeys)
        );
    }

    private int nextChildPosition(NoteCollectionEntity adoptedRoot, UUID userId) {
        return collectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(
                adoptedRoot.getId(),
                userId
        ).stream().map(NoteCollectionEntity::getSiblingPosition)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1) + 1;
    }

    private Optional<UUID> sourceNoteIdForAdoptedCopy(NoteEntity note) {
        if (note.getCopiedFromNoteId() != null) {
            return Optional.of(note.getCopiedFromNoteId());
        }
        return Optional.ofNullable(note.getSourceNoteId());
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
            Instant now,
            String label
    ) {
        List<NoteCollectionItemEntity> items = new ArrayList<>();
        for (int index = 0; index < noteIds.size(); index++) {
            NoteCollectionItemEntity item = new NoteCollectionItemEntity();
            item.setId(UUID.randomUUID());
            item.setCollectionId(collectionId);
            item.setNoteId(noteIds.get(index));
            item.setLabel(label);
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
            item.setSourceLabelAtSync(copiedItem.label());
            item.setSourcePositionAtSync(copiedItem.sourcePosition());
            item.setSourceSyncedAt(now);
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

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
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
                enumName(collection.getLearnerLevel()),
                resolveInheritedLearnerLevel(collection.getId()).map(Enum::name).orElse(null),
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
                enumName(collection.getLearnerLevel()),
                resolveInheritedLearnerLevel(collection.getId()).map(Enum::name).orElse(null),
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
                enumName(collection.getLearnerLevel()),
                // resolvedLearnerLevel is deliberately NULL on the public mapper. This endpoint is
                // unauthenticated, and a PUBLIC collection may have a non-null parentCollectionId
                // (validatePublishable does not require top-level), so resolving here would read a
                // PRIVATE parent's level and emit it to anonymous callers — the only field on this
                // response derived from a row the caller cannot see. It is consumed solely by the
                // owner-scoped bulk-authoring selector, so omitting it costs nothing.
                null,
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
                note.domainContext() == null ? null : note.domainContext().name(),
                note.learnerLevel() == null ? null : note.learnerLevel().name(),
                NoteStudyPackStatusResolver.resolve(note.status(), studyPack != null),
                generatedQuizId == null ? null : generatedQuizId.toString(),
                lastSessionCompletedAt,
                dueConcepts.size(),
                dueConcepts.stream().limit(DUE_CONCEPT_DISPLAY_LIMIT).toList(),
                note.updatedAt()
        );
    }

    private record CopiedPlanItem(UUID noteId, String label, int sourcePosition) {
    }

    private record AdoptedPlacement(NoteCollectionItemEntity item, NoteEntity note) {
    }

    private record PendingSubjectAddition(
            NoteCollectionEntity sourcePlan,
            List<NoteCollectionItemEntity> placements
    ) {
    }

    private record CreatedSubjectAddition(NoteCollectionEntity collection, boolean created) {
    }

    private record PendingPlacementAddition(
            NoteCollectionEntity adoptedPlan,
            NoteCollectionEntity sourcePlan,
            NoteCollectionItemEntity sourceItem
    ) {
    }

    private record SourceUpdateInspection(
            NoteCollectionEntity adoptedRoot,
            NoteCollectionEntity sourceRoot,
            List<NoteCollectionEntity> sourcePlans,
            Map<UUID, NoteCollectionEntity> adoptedBySourcePlan,
            Map<UUID, List<NoteCollectionItemEntity>> sourceItemsByPlan,
            Map<UUID, Map<UUID, AdoptedPlacement>> adoptedPlacementsByPlan,
            List<ReviewSetUpdateChange> changes,
            List<PendingSubjectAddition> subjectAdditions,
            List<PendingPlacementAddition> placementAdditions,
            boolean detached
    ) {
        private static SourceUpdateInspection detached(NoteCollectionEntity adoptedRoot) {
            return new SourceUpdateInspection(
                    adoptedRoot,
                    null,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    true
            );
        }

        private int additionsAvailable() {
            return (int) changes.stream()
                    .filter(change -> "ADDED_NOTE".equals(change.type())
                            || "ADDED_SUBJECT_PLAN".equals(change.type()))
                    .count();
        }
    }

    private record ReadinessConceptTotals(
            int totalConcepts,
            int masteredConcepts,
            int dueConcepts,
            int notPracticedConcepts
    ) {
    }
}
