package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.BulkGenerationFailureReason;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.BulkNoteGenerationQuotaExceededException;
import com.studysnap.backend.exception.CourseProgramSelectionRequiredException;
import com.studysnap.backend.exception.DuplicateCourseProgramException;
import com.studysnap.backend.exception.InvalidBulkGenerationRequestException;
import com.studysnap.backend.exception.MultiProgramDomainContextRequiredException;
import com.studysnap.backend.exception.MonthlyNoteGenerationLimitReachedException;
import com.studysnap.backend.exception.UnknownCourseProgramException;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NoteBulkGenerationService {
    private static final Logger log = LoggerFactory.getLogger(NoteBulkGenerationService.class);
    private static final int MIN_MAX_TOPICS = 1;
    private static final int MAX_TOPIC_LENGTH = 160;
    private static final int MAX_SUBJECT_LENGTH = 160;
    private static final int MAX_COURSE_PROGRAM_LENGTH = 160;
    private static final int MIN_THROTTLE_DELAY_MS = 0;
    private static final int MAX_THROTTLE_DELAY_MS = 5_000;
    private static final String EMPTY_BATCH_MESSAGE = "Add at least one topic.";
    private static final String COURSE_PROGRAM_REQUIRED_MESSAGE = "Course/program is required.";
    private static final String SUBJECT_REQUIRED_MESSAGE = "Subject is required.";
    private static final String FIELD_TOO_LONG_MESSAGE_TEMPLATE = "%s must be %d characters or less.";
    private static final String MAX_TOPICS_MESSAGE_TEMPLATE = "You can bulk generate up to %d topics at once.";
    private static final String COURSE_PROGRAM_FIELD = "Course/program";
    private static final String SUBJECT_FIELD = "Subject";
    private final NoteGenerationService noteGenerationService;
    private final NoteService noteService;
    private final StudyPackService studyPackService;
    private final LlmStudyPackService llmStudyPackService;
    private final ContentModerationService contentModerationService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final StudyPackGenerationTaskDispatcher taskDispatcher;
    private final UserRepository userRepository;
    private final CourseProgramCatalogRepository courseProgramCatalogRepository;
    private final OnboardingGuardService onboardingGuardService;
    private final BulkGenerationResultService bulkGenerationResultService;
    private final BulkGenerationFailureReasonNormalizer failureReasonNormalizer;
    private final MePlanService mePlanService;
    private final NoteCollectionService noteCollectionService;
    private final int maxTopics;
    private final int throttleDelayMs;

    @Autowired
    public NoteBulkGenerationService(
            NoteGenerationService noteGenerationService,
            NoteService noteService,
            StudyPackService studyPackService,
            LlmStudyPackService llmStudyPackService,
            ContentModerationService contentModerationService,
            StudyPackGenerationContextResolver generationContextResolver,
            StudyPackGenerationTaskDispatcher taskDispatcher,
            UserRepository userRepository,
            CourseProgramCatalogRepository courseProgramCatalogRepository,
            OnboardingGuardService onboardingGuardService,
            BulkGenerationResultService bulkGenerationResultService,
            BulkGenerationFailureReasonNormalizer failureReasonNormalizer,
            MePlanService mePlanService,
            NoteCollectionService noteCollectionService,
            @Value("${note.bulk-generation.max-topics:50}") int maxTopics,
            @Value("${note.bulk-generation.throttle-delay-ms:500}") int throttleDelayMs
    ) {
        this.noteGenerationService = noteGenerationService;
        this.noteService = noteService;
        this.studyPackService = studyPackService;
        this.llmStudyPackService = llmStudyPackService;
        this.contentModerationService = contentModerationService;
        this.generationContextResolver = generationContextResolver;
        this.taskDispatcher = taskDispatcher;
        this.userRepository = userRepository;
        this.courseProgramCatalogRepository = courseProgramCatalogRepository;
        this.onboardingGuardService = onboardingGuardService;
        this.bulkGenerationResultService = bulkGenerationResultService;
        this.failureReasonNormalizer = failureReasonNormalizer;
        this.mePlanService = mePlanService;
        this.noteCollectionService = noteCollectionService;
        this.maxTopics = Math.clamp(maxTopics, MIN_MAX_TOPICS, Integer.MAX_VALUE);
        this.throttleDelayMs = Math.clamp(throttleDelayMs, MIN_THROTTLE_DELAY_MS, MAX_THROTTLE_DELAY_MS);
    }

    /** Retained for focused unit tests that exercise only personal-note batches. */
    public NoteBulkGenerationService(
            NoteGenerationService noteGenerationService,
            NoteService noteService,
            StudyPackService studyPackService,
            LlmStudyPackService llmStudyPackService,
            ContentModerationService contentModerationService,
            StudyPackGenerationContextResolver generationContextResolver,
            StudyPackGenerationTaskDispatcher taskDispatcher,
            UserRepository userRepository,
            OnboardingGuardService onboardingGuardService,
            BulkGenerationResultService bulkGenerationResultService,
            MePlanService mePlanService,
            int maxTopics,
            int throttleDelayMs
    ) {
        this(
                noteGenerationService,
                noteService,
                studyPackService,
                llmStudyPackService,
                contentModerationService,
                generationContextResolver,
                taskDispatcher,
                userRepository,
                new CourseProgramCatalogRepository(null),
                onboardingGuardService,
                bulkGenerationResultService,
                new BulkGenerationFailureReasonNormalizer(),
                mePlanService,
                null,
                maxTopics,
                throttleDelayMs
        );
    }

    public BulkGenerateNotesResponse queueBatch(
            BulkGenerateNotesRequest request,
            UUID ownerUserId,
            boolean enforceLimits
    ) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        UserEntity owner = userRepository.findById(ownerUserId).orElseThrow(UserNotFoundException::new);
        NormalizedBatch batch = normalizeAndValidate(request, owner);
        if (batch.collectionId() != null) {
            noteCollectionService.validateNoteAcceptingCollection(batch.collectionId(), ownerUserId);
        }
        rejectIfNoteGenerationQuotaExceeded(batch, ownerUserId, enforceLimits);
        UUID resultId = UUID.randomUUID();
        taskDispatcher.execute(() -> processBatch(resultId, batch, ownerUserId, enforceLimits));
        return new BulkGenerateNotesResponse(
                resultId,
                batch.items().size(),
                batch.items().size(),
                batch.rejectedTopics()
        );
    }

    private void rejectIfNoteGenerationQuotaExceeded(
            NormalizedBatch batch,
            UUID ownerUserId,
            boolean enforceLimits
    ) {
        if (!enforceLimits) {
            return;
        }
        int remaining = mePlanService.getNoteGenerationsRemaining(ownerUserId);
        int requestedCount = batch.items().size();
        if (requestedCount > remaining) {
            throw new BulkNoteGenerationQuotaExceededException(remaining, requestedCount);
        }
    }

    private void processBatch(UUID resultId, NormalizedBatch batch, UUID ownerUserId, boolean enforceLimits) {
        AtomicInteger createdCount = new AtomicInteger();
        List<String> failedTopics = new ArrayList<>();
        List<BulkGenerationFailureReason> failedTopicReasons = new ArrayList<>();
        List<String> quotaBlockedTopics = new ArrayList<>();
        List<String> createdNoteIds = new ArrayList<>();
        String resultCourseProgram = null;

        try {
            StudyPackGenerationContext context = batch.courseProgramIds().isEmpty()
                    ? generationContextResolver.resolveForBulkGeneration(
                            ownerUserId,
                            batch.courseProgramText(),
                            batch.subject(),
                            batch.domainContext(),
                            batch.learnerLevel()
                    )
                    : generationContextResolver.resolveForBulkGeneration(
                            ownerUserId,
                            batch.courseProgramIds(),
                            batch.courseProgramText(),
                            batch.subject(),
                            batch.domainContext(),
                            batch.learnerLevel()
                    );
            resultCourseProgram = context.courseProgram();
            for (int index = 0; index < batch.items().size(); index++) {
                BulkGenerationItem item = batch.items().get(index);
                try {
                    String createdNoteId = processItem(batch, item, ownerUserId, enforceLimits, context);
                    createdNoteIds.add(createdNoteId);
                    createdCount.incrementAndGet();
                } catch (RuntimeException exception) {
                    if (exception instanceof MonthlyNoteGenerationLimitReachedException) {
                        quotaBlockedTopics.add(item.topic());
                    } else {
                        failedTopics.add(item.topic());
                        failedTopicReasons.add(normalizeFailureReason(item.topic(), exception));
                    }
                    log.warn(
                            "action=bulk_generate_note outcome=failed topic={} subject={} ownerUserId={}",
                            item.topic(),
                            batch.subject(),
                            ownerUserId,
                            exception
                    );
                }
                throttleBeforeNext(index, batch.items().size());
            }
        } catch (RuntimeException exception) {
            failedTopics.clear();
            failedTopics.addAll(batch.items().stream().map(BulkGenerationItem::topic).toList());
            failedTopicReasons.clear();
            failedTopicReasons.addAll(batch.items().stream()
                    .map(item -> normalizeFailureReason(item.topic(), exception))
                    .toList());
            quotaBlockedTopics.clear();
            log.warn(
                    "action=bulk_generate_batch outcome=failed_before_loop accepted={} subject={} ownerUserId={}",
                    batch.items().size(),
                    batch.subject(),
                    ownerUserId,
                    exception
            );
        } finally {
            addCreatedNotesToCollection(batch.collectionId(), batch.sectionLabel(), createdNoteIds, ownerUserId);
            try {
                bulkGenerationResultService.recordResult(
                        resultId,
                        ownerUserId,
                        batch.subject(),
                        resultCourseProgram == null ? batch.courseProgramText() : resultCourseProgram,
                        batch.domainContext(),
                        batch.learnerLevel(),
                        batch.targetProfileType().name(),
                        batch.collectionId(),
                        batch.makePublic(),
                        batch.items().size(),
                        createdCount.get(),
                        failedTopics,
                        failedTopicReasons,
                        quotaBlockedTopics
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "action=bulk_generate_result outcome=failed_to_record resultId={} subject={} ownerUserId={}",
                        resultId,
                        batch.subject(),
                        ownerUserId,
                        exception
                );
            }
            log.info(
                    "action=bulk_generate_batch outcome=completed accepted={} created={} failed={} quotaBlocked={} ownerUserId={}",
                    batch.items().size(),
                    createdCount.get(),
                    failedTopics.size(),
                    quotaBlockedTopics.size(),
                    ownerUserId
            );
        }
    }

    private BulkGenerationFailureReason normalizeFailureReason(String topic, RuntimeException exception) {
        try {
            BulkGenerationFailureReason normalized = failureReasonNormalizer.normalize(topic, exception);
            return normalized == null
                    ? BulkGenerationFailureReasonNormalizer.unexpected(topic, exception)
                    : normalized;
        } catch (RuntimeException normalizationException) {
            log.warn(
                    "action=bulk_generate_failure_reason outcome=normalization_failed topic={} exceptionType={}",
                    topic,
                    normalizationException.getClass().getSimpleName()
            );
            return BulkGenerationFailureReasonNormalizer.unexpected(topic, exception);
        }
    }

    private String processItem(
            NormalizedBatch batch,
            BulkGenerationItem item,
            UUID ownerUserId,
            boolean enforceLimits,
            StudyPackGenerationContext context
    ) {
        String content = enforceLimits
                ? noteGenerationService.generateFromTopic(
                        new GenerateNoteFromTopicRequest(
                                item.topic(),
                                batch.courseProgramIds(),
                                batch.courseProgramText(),
                                batch.domainContext() == null ? null : batch.domainContext().name()
                        ),
                        ownerUserId,
                        context
                ).content()
                : generateAdminContent(item.topic(), context);

        NoteResponse note = noteService.create(
                new UpsertNoteRequest(
                        item.topic(),
                        batch.subject(),
                        batch.courseProgramIds(),
                        batch.courseProgramText(),
                        batch.domainContext() == null ? null : batch.domainContext().name(),
                        batch.learnerLevel() == null ? null : batch.learnerLevel().name(),
                        List.of(),
                        content
                ),
                ownerUserId
        );
        if (batch.makePublic()) {
            try {
                noteService.updateVisibility(note.id(), NoteVisibility.PUBLIC.name(), ownerUserId);
            } catch (RuntimeException exception) {
                log.warn(
                        "action=bulk_generate_note_visibility outcome=failed_after_note_created noteId={} topic={} subject={} ownerUserId={}",
                        note.id(),
                        item.topic(),
                        batch.subject(),
                        ownerUserId,
                        exception
                );
            }
        }
        try {
            studyPackService.startAsyncGenerationFromNote(
                    note.id(),
                    ownerUserId,
                    false,
                    enforceLimits,
                    context,
                    batch.subject()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "action=bulk_generate_study_pack outcome=failed_after_note_created noteId={} topic={} subject={} ownerUserId={}",
                    note.id(),
                    item.topic(),
                    batch.subject(),
                    ownerUserId,
                    exception
            );
        }
        return note.id();
    }

    private void addCreatedNotesToCollection(
            UUID collectionId,
            String sectionLabel,
            List<String> noteIds,
            UUID ownerUserId
    ) {
        if (collectionId == null || noteIds.isEmpty()) {
            return;
        }
        try {
            // Lenient variant: a note deleted from the Library mid-batch must not stop the
            // rest of the batch from joining the Review Set.
            noteCollectionService.addGeneratedItems(
                    collectionId,
                    ownerUserId,
                    noteIds.stream().map(UUID::fromString).toList(),
                    sectionLabel
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "action=bulk_generate_collection_membership outcome=failed collectionId={} createdNotes={} ownerUserId={}",
                    collectionId,
                    noteIds.size(),
                    ownerUserId,
                    exception
            );
        }
    }

    private String generateAdminContent(String topic, StudyPackGenerationContext context) {
        contentModerationService.validateOrThrow(topic);
        return llmStudyPackService.generateNoteFromTopic(topic, context);
    }

    // Nobody curates during onboarding. Matches NoteService.isTeacherSelectableOwner and
    // NoteGenerationService.isCurator, which were corrected in adfa797f; this third path kept the bare
    // role check and was recorded as a v0.71.0 Known Limitation rather than a pattern to copy. Not
    // UI-reachable -- requireAuthenticatedOnboardedUser redirects mid-onboarding users away from bulk
    // generate -- but a rule with a live exception in the codebase is a rule that decays.
    private boolean isCurator(UserEntity owner) {
        if (owner.getOnboardingCompletedAt() == null) {
            return false;
        }
        return owner.getRole() == UserRole.ADMIN || owner.getProfileType() == ProfileType.TEACHER;
    }

    private NormalizedBatch normalizeAndValidate(BulkGenerateNotesRequest request, UserEntity owner) {
        if (request == null) {
            throw new InvalidBulkGenerationRequestException(EMPTY_BATCH_MESSAGE);
        }
        String subject = requireText(request.subject(), SUBJECT_REQUIRED_MESSAGE);
        assertMaxLength(subject, MAX_SUBJECT_LENGTH, SUBJECT_FIELD);
        if (request.topics() == null || request.topics().isEmpty()) {
            throw new InvalidBulkGenerationRequestException(EMPTY_BATCH_MESSAGE);
        }
        if (request.topics().size() > maxTopics) {
            throw new InvalidBulkGenerationRequestException(MAX_TOPICS_MESSAGE_TEMPLATE.formatted(maxTopics));
        }

        List<BulkGenerationItem> items = new ArrayList<>();
        int rejectedTopics = 0;
        for (String rawTopic : request.topics()) {
            String topic = rawTopic == null ? "" : rawTopic.trim();
            if (topic.isBlank() || topic.length() > MAX_TOPIC_LENGTH) {
                rejectedTopics++;
                continue;
            }
            items.add(new BulkGenerationItem(topic));
        }
        if (items.isEmpty()) {
            throw new InvalidBulkGenerationRequestException(EMPTY_BATCH_MESSAGE);
        }

        boolean isTeacherOrAdmin = isCurator(owner);
        NoteTargetProfileType targetProfileType = mapProfileTypeToNoteTargetProfile(owner.getProfileType());
        DomainContext domainContext = NoteAuthoringMetadataParser.parseDomainContextOrThrow(request.domainContext());
        LearnerLevel learnerLevel = NoteAuthoringMetadataParser.parseLearnerLevelOrThrow(request.learnerLevel());
        Set<UUID> courseProgramIds = isTeacherOrAdmin
                ? validateCuratedProgramIds(request.courseProgramIds())
                : Set.of();
        String courseProgramText = isTeacherOrAdmin
                ? null
                : requireText(firstNonBlank(request.courseProgramText(), owner.getCourseProgram()), COURSE_PROGRAM_REQUIRED_MESSAGE);
        if (courseProgramText != null) {
            assertMaxLength(courseProgramText, MAX_COURSE_PROGRAM_LENGTH, COURSE_PROGRAM_FIELD);
        }
        if (courseProgramIds.size() > 1 && domainContext == null) {
            throw new MultiProgramDomainContextRequiredException();
        }

        return new NormalizedBatch(
                subject,
                List.copyOf(courseProgramIds),
                courseProgramText,
                domainContext,
                learnerLevel,
                targetProfileType,
                request.makePublic(),
                List.copyOf(items),
                rejectedTopics,
                request.collectionId(),
                request.collectionId() == null ? null : normalizeOptionalText(request.sectionLabel())
        );
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidBulkGenerationRequestException(message);
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Set<UUID> validateCuratedProgramIds(List<UUID> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new CourseProgramSelectionRequiredException();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new DuplicateCourseProgramException();
        }
        if (courseProgramCatalogRepository.findExistingIds(uniqueIds).size() != uniqueIds.size()) {
            throw new UnknownCourseProgramException();
        }
        return uniqueIds;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private void assertMaxLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new InvalidBulkGenerationRequestException(
                    FIELD_TOO_LONG_MESSAGE_TEMPLATE.formatted(fieldName, maxLength)
            );
        }
    }

    private NoteTargetProfileType mapProfileTypeToNoteTargetProfile(ProfileType profileType) {
        if (profileType == ProfileType.BOARD_EXAM) {
            return NoteTargetProfileType.BOARD_TAKER;
        }
        if (profileType == ProfileType.PROFESSIONAL) {
            return NoteTargetProfileType.PROFESSIONAL;
        }
        return NoteTargetProfileType.STUDENT;
    }

    private void throttleBeforeNext(int index, int totalItems) {
        if (throttleDelayMs == 0 || index >= totalItems - 1) {
            return;
        }
        try {
            Thread.sleep(throttleDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bulk generation was interrupted.", exception);
        }
    }

    private record BulkGenerationItem(String topic) {
    }

    private record NormalizedBatch(
            String subject,
            List<UUID> courseProgramIds,
            String courseProgramText,
            DomainContext domainContext,
            LearnerLevel learnerLevel,
            NoteTargetProfileType targetProfileType,
            boolean makePublic,
            List<BulkGenerationItem> items,
            int rejectedTopics,
            UUID collectionId,
            String sectionLabel
    ) {
    }
}
