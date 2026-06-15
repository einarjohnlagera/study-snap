package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerateNoteGroupRequest;
import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.exception.InvalidBulkGenerationRequestException;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NoteBulkGenerationService {
    private static final Logger log = LoggerFactory.getLogger(NoteBulkGenerationService.class);
    private static final int MIN_MAX_TITLES = 1;
    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_SUBJECT_LENGTH = 160;
    private static final int MAX_COURSE_PROGRAM_LENGTH = 160;
    private static final int MIN_THROTTLE_DELAY_MS = 0;
    private static final int MAX_THROTTLE_DELAY_MS = 5_000;
    private static final String EMPTY_BATCH_MESSAGE = "Add at least one title under a Subject: heading.";
    private static final String COURSE_PROGRAM_REQUIRED_MESSAGE = "Course/program is required.";
    private static final String TARGET_AUDIENCE_REQUIRED_MESSAGE = "Target audience is required.";
    private static final String SUBJECT_REQUIRED_MESSAGE = "Every group must include a subject.";
    private static final String FIELD_TOO_LONG_MESSAGE_TEMPLATE = "%s must be %d characters or less.";
    private static final String MAX_TITLES_MESSAGE_TEMPLATE = "You can bulk generate up to %d titles at once.";
    private static final String COURSE_PROGRAM_FIELD = "Course/program";
    private static final String SUBJECT_FIELD = "Subject";
    private static final Set<LearnerLevel> SUPPORTED_TARGET_AUDIENCES = EnumSet.of(
            LearnerLevel.GRADE_SCHOOL,
            LearnerLevel.JUNIOR_HIGH,
            LearnerLevel.SENIOR_HIGH,
            LearnerLevel.COLLEGE,
            LearnerLevel.BOARD_EXAM_REVIEW,
            LearnerLevel.PROFESSIONAL
    );

    private final NoteGenerationService noteGenerationService;
    private final NoteService noteService;
    private final StudyPackService studyPackService;
    private final LlmStudyPackService llmStudyPackService;
    private final ContentModerationService contentModerationService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final StudyPackGenerationTaskDispatcher taskDispatcher;
    private final int maxTitles;
    private final int throttleDelayMs;

    public NoteBulkGenerationService(
            NoteGenerationService noteGenerationService,
            NoteService noteService,
            StudyPackService studyPackService,
            LlmStudyPackService llmStudyPackService,
            ContentModerationService contentModerationService,
            StudyPackGenerationContextResolver generationContextResolver,
            StudyPackGenerationTaskDispatcher taskDispatcher,
            @Value("${note.bulk-generation.max-titles:50}") int maxTitles,
            @Value("${note.bulk-generation.throttle-delay-ms:500}") int throttleDelayMs
    ) {
        this.noteGenerationService = noteGenerationService;
        this.noteService = noteService;
        this.studyPackService = studyPackService;
        this.llmStudyPackService = llmStudyPackService;
        this.contentModerationService = contentModerationService;
        this.generationContextResolver = generationContextResolver;
        this.taskDispatcher = taskDispatcher;
        this.maxTitles = Math.clamp(maxTitles, MIN_MAX_TITLES, Integer.MAX_VALUE);
        this.throttleDelayMs = Math.clamp(throttleDelayMs, MIN_THROTTLE_DELAY_MS, MAX_THROTTLE_DELAY_MS);
    }

    public BulkGenerateNotesResponse queueBatch(
            BulkGenerateNotesRequest request,
            UUID ownerUserId,
            boolean enforceLimits
    ) {
        NormalizedBatch batch = normalizeAndValidate(request);
        taskDispatcher.execute(() -> processBatch(batch, ownerUserId, enforceLimits));
        return new BulkGenerateNotesResponse(
                batch.items().size(),
                batch.items().size(),
                batch.subjectCount(),
                batch.rejectedTitles()
        );
    }

    private void processBatch(NormalizedBatch batch, UUID ownerUserId, boolean enforceLimits) {
        AtomicInteger createdCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        for (int index = 0; index < batch.items().size(); index++) {
            BulkGenerationItem item = batch.items().get(index);
            try {
                processItem(batch, item, ownerUserId, enforceLimits);
                createdCount.incrementAndGet();
            } catch (RuntimeException exception) {
                failedCount.incrementAndGet();
                log.warn(
                        "action=bulk_generate_note outcome=failed title={} subject={} ownerUserId={}",
                        item.title(),
                        item.subject(),
                        ownerUserId,
                        exception
                );
            }
            throttleBeforeNext(index, batch.items().size());
        }

        log.info(
                "action=bulk_generate_batch outcome=completed accepted={} created={} failed={} ownerUserId={}",
                batch.items().size(),
                createdCount.get(),
                failedCount.get(),
                ownerUserId
        );
    }

    private void processItem(
            NormalizedBatch batch,
            BulkGenerationItem item,
            UUID ownerUserId,
            boolean enforceLimits
    ) {
        StudyPackGenerationContext context = generationContextResolver.resolveForBulkGeneration(
                ownerUserId,
                batch.targetAudience(),
                batch.courseProgram(),
                item.subject()
        );
        String content = enforceLimits
                ? noteGenerationService.generateFromTopic(
                        new GenerateNoteFromTopicRequest(item.title(), batch.courseProgram()),
                        ownerUserId
                ).content()
                : generateAdminContent(item.title(), context);

        NoteResponse note = noteService.create(
                new UpsertNoteRequest(
                        item.title(),
                        item.subject(),
                        batch.courseProgram(),
                        List.of(),
                        mapTargetProfileType(batch.targetAudience()).name(),
                        content
                ),
                ownerUserId
        );
        if (batch.makePublic()) {
            noteService.updateVisibility(note.id(), NoteVisibility.PUBLIC.name(), ownerUserId);
        }
        studyPackService.startAsyncGenerationFromNote(
                note.id(),
                ownerUserId,
                false,
                enforceLimits,
                context,
                item.subject()
        );
    }

    private String generateAdminContent(String title, StudyPackGenerationContext context) {
        contentModerationService.validateOrThrow(title);
        return llmStudyPackService.generateNoteFromTopic(title, context);
    }

    private NormalizedBatch normalizeAndValidate(BulkGenerateNotesRequest request) {
        if (request == null) {
            throw new InvalidBulkGenerationRequestException(EMPTY_BATCH_MESSAGE);
        }
        String courseProgram = requireText(request.courseProgram(), COURSE_PROGRAM_REQUIRED_MESSAGE);
        assertMaxLength(courseProgram, MAX_COURSE_PROGRAM_LENGTH, COURSE_PROGRAM_FIELD);
        if (request.targetAudience() == null || !SUPPORTED_TARGET_AUDIENCES.contains(request.targetAudience())) {
            throw new InvalidBulkGenerationRequestException(TARGET_AUDIENCE_REQUIRED_MESSAGE);
        }
        if (request.groups() == null || request.groups().isEmpty()) {
            throw new InvalidBulkGenerationRequestException(EMPTY_BATCH_MESSAGE);
        }

        int submittedTitleCount = request.groups().stream()
                .filter(group -> group != null && group.titles() != null)
                .mapToInt(group -> group.titles().size())
                .sum();
        if (submittedTitleCount > maxTitles) {
            throw new InvalidBulkGenerationRequestException(MAX_TITLES_MESSAGE_TEMPLATE.formatted(maxTitles));
        }

        List<BulkGenerationItem> items = new ArrayList<>();
        int rejectedTitles = 0;
        int subjectCount = 0;
        for (BulkGenerateNoteGroupRequest group : request.groups()) {
            if (group == null) {
                throw new InvalidBulkGenerationRequestException(SUBJECT_REQUIRED_MESSAGE);
            }
            String subject = requireText(group.subject(), SUBJECT_REQUIRED_MESSAGE);
            assertMaxLength(subject, MAX_SUBJECT_LENGTH, SUBJECT_FIELD);
            int groupAccepted = 0;
            for (String rawTitle : group.titles() == null ? List.<String>of() : group.titles()) {
                String title = rawTitle == null ? "" : rawTitle.trim();
                if (title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
                    rejectedTitles++;
                    continue;
                }
                items.add(new BulkGenerationItem(subject, title));
                groupAccepted++;
            }
            if (groupAccepted > 0) {
                subjectCount++;
            }
        }
        if (items.isEmpty()) {
            throw new InvalidBulkGenerationRequestException(EMPTY_BATCH_MESSAGE);
        }
        return new NormalizedBatch(
                courseProgram,
                request.targetAudience(),
                request.makePublic(),
                List.copyOf(items),
                subjectCount,
                rejectedTitles
        );
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidBulkGenerationRequestException(message);
        }
        return value.trim();
    }

    private void assertMaxLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new InvalidBulkGenerationRequestException(
                    FIELD_TOO_LONG_MESSAGE_TEMPLATE.formatted(fieldName, maxLength)
            );
        }
    }

    private NoteTargetProfileType mapTargetProfileType(LearnerLevel targetAudience) {
        if (targetAudience == LearnerLevel.BOARD_EXAM_REVIEW) {
            return NoteTargetProfileType.BOARD_TAKER;
        }
        if (targetAudience == LearnerLevel.PROFESSIONAL) {
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

    private record BulkGenerationItem(String subject, String title) {
    }

    private record NormalizedBatch(
            String courseProgram,
            LearnerLevel targetAudience,
            boolean makePublic,
            List<BulkGenerationItem> items,
            int subjectCount,
            int rejectedTitles
    ) {
    }
}
