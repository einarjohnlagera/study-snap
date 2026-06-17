package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.InvalidBulkGenerationRequestException;
import com.studysnap.backend.exception.MonthlyNoteGenerationLimitReachedException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
    private static final String TARGET_AUDIENCE_REQUIRED_MESSAGE =
            "Target audience is required for teachers and admins.";
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
    private final OnboardingGuardService onboardingGuardService;
    private final BulkGenerationResultService bulkGenerationResultService;
    private final int maxTopics;
    private final int throttleDelayMs;

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
        this.onboardingGuardService = onboardingGuardService;
        this.bulkGenerationResultService = bulkGenerationResultService;
        this.maxTopics = Math.clamp(maxTopics, MIN_MAX_TOPICS, Integer.MAX_VALUE);
        this.throttleDelayMs = Math.clamp(throttleDelayMs, MIN_THROTTLE_DELAY_MS, MAX_THROTTLE_DELAY_MS);
    }

    public BulkGenerateNotesResponse queueBatch(
            BulkGenerateNotesRequest request,
            UUID ownerUserId,
            boolean enforceLimits
    ) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        UserEntity owner = userRepository.findById(ownerUserId).orElseThrow(UserNotFoundException::new);
        NormalizedBatch batch = normalizeAndValidate(request, owner);
        UUID resultId = UUID.randomUUID();
        taskDispatcher.execute(() -> processBatch(resultId, batch, ownerUserId, enforceLimits));
        return new BulkGenerateNotesResponse(
                resultId,
                batch.items().size(),
                batch.items().size(),
                batch.rejectedTopics()
        );
    }

    private void processBatch(UUID resultId, NormalizedBatch batch, UUID ownerUserId, boolean enforceLimits) {
        AtomicInteger createdCount = new AtomicInteger();
        List<String> failedTopics = new ArrayList<>();
        List<String> quotaBlockedTopics = new ArrayList<>();

        try {
            StudyPackGenerationContext context = generationContextResolver.resolveForBulkGeneration(
                    ownerUserId,
                    batch.courseProgram(),
                    batch.subject()
            );
            for (int index = 0; index < batch.items().size(); index++) {
                BulkGenerationItem item = batch.items().get(index);
                try {
                    processItem(batch, item, ownerUserId, enforceLimits, context);
                    createdCount.incrementAndGet();
                } catch (RuntimeException exception) {
                    if (exception instanceof MonthlyNoteGenerationLimitReachedException) {
                        quotaBlockedTopics.add(item.topic());
                    } else {
                        failedTopics.add(item.topic());
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
            quotaBlockedTopics.clear();
            log.warn(
                    "action=bulk_generate_batch outcome=failed_before_loop accepted={} subject={} ownerUserId={}",
                    batch.items().size(),
                    batch.subject(),
                    ownerUserId,
                    exception
            );
        } finally {
            try {
                bulkGenerationResultService.recordResult(
                        resultId,
                        ownerUserId,
                        batch.subject(),
                        batch.courseProgram(),
                        batch.targetProfileType().name(),
                        batch.makePublic(),
                        batch.items().size(),
                        createdCount.get(),
                        failedTopics,
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

    private void processItem(
            NormalizedBatch batch,
            BulkGenerationItem item,
            UUID ownerUserId,
            boolean enforceLimits,
            StudyPackGenerationContext context
    ) {
        String content = enforceLimits
                ? noteGenerationService.generateFromTopic(
                        new GenerateNoteFromTopicRequest(item.topic(), batch.courseProgram()),
                        ownerUserId
                ).content()
                : generateAdminContent(item.topic(), context);

        NoteResponse note = noteService.create(
                new UpsertNoteRequest(
                        item.topic(),
                        batch.subject(),
                        batch.courseProgram(),
                        List.of(),
                        batch.targetProfileType().name(),
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
    }

    private String generateAdminContent(String topic, StudyPackGenerationContext context) {
        contentModerationService.validateOrThrow(topic);
        return llmStudyPackService.generateNoteFromTopic(topic, context);
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

        boolean isTeacherOrAdmin = owner.getRole() == UserRole.ADMIN
                || owner.getProfileType() == ProfileType.TEACHER;
        String courseProgram = isTeacherOrAdmin
                ? requireText(request.courseProgram(), COURSE_PROGRAM_REQUIRED_MESSAGE)
                : normalizeOptionalText(owner.getCourseProgram());
        if (courseProgram != null) {
            assertMaxLength(courseProgram, MAX_COURSE_PROGRAM_LENGTH, COURSE_PROGRAM_FIELD);
        }
        NoteTargetProfileType targetProfileType = isTeacherOrAdmin
                ? requireTargetProfileType(request.targetProfileType())
                : mapProfileTypeToNoteTargetProfile(owner.getProfileType());

        return new NormalizedBatch(
                subject,
                courseProgram,
                targetProfileType,
                request.makePublic(),
                List.copyOf(items),
                rejectedTopics
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

    private NoteTargetProfileType requireTargetProfileType(NoteTargetProfileType targetProfileType) {
        if (targetProfileType == null) {
            throw new InvalidBulkGenerationRequestException(TARGET_AUDIENCE_REQUIRED_MESSAGE);
        }
        return targetProfileType;
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
            String courseProgram,
            NoteTargetProfileType targetProfileType,
            boolean makePublic,
            List<BulkGenerationItem> items,
            int rejectedTopics
    ) {
    }
}
