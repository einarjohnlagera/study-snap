package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.NeedsTextConfirmationResponse;
import com.studysnap.backend.dto.StudyPackListPageResponse;
import com.studysnap.backend.dto.StudyPackMeta;
import com.studysnap.backend.dto.StudyPackListItemResponse;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.StudyPackDraftEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.DraftNotFoundException;
import com.studysnap.backend.exception.NoteGenerationInProgressException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.NoteRegenerationStudyPackRequiredException;
import com.studysnap.backend.exception.NoteRegenerationTopicRequiredException;
import com.studysnap.backend.exception.OcrDisabledException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.exception.SubjectTooLongException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackDraftRepository;
import com.studysnap.backend.repository.StudyPackListItemProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.security.OcrRateLimitService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.OcrResult;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.service.model.StudyPackQuizMastery;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import com.studysnap.backend.util.CreatedAtIdCursorUtils;
import com.studysnap.backend.util.SubjectNormalizationUtils;
import com.studysnap.backend.util.NoteMetadataBounds;
import com.studysnap.backend.util.SummaryPreviewUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class StudyPackService {
    private static final Logger log = LoggerFactory.getLogger(StudyPackService.class);
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;
    private static final int MAX_TITLE_LENGTH = 180;
    private static final int MAX_TAG_LENGTH = 30;
    private static final int MAX_TAGS_PER_STUDY_PACK = 30;
    private static final int GENERATED_SUBJECT_WORD_BOUNDARY_WINDOW = 8;
    private static final String STUDY_PACK = "study-pack";
    private static final String ERROR_NOTE_ALREADY_HAS_STUDY_PACK = "NOTE_ALREADY_HAS_STUDY_PACK";
    private static final String MESSAGE_NOTE_ALREADY_HAS_STUDY_PACK = "This note already has a Study Pack. Use Regenerate Study Pack to replace it.";
    private static final Comparator<String> SUBJECT_DISPLAY_COMPARATOR = (left, right) -> {
        int caseInsensitive = left.compareToIgnoreCase(right);
        return caseInsensitive != 0 ? caseInsensitive : left.compareTo(right);
    };

    private final StudyPackRepository studyPackRepository;
    private final StudyPackDraftRepository studyPackDraftRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final OcrService ocrService;
    private final LlmStudyPackService llmStudyPackService;
    private final StudySnapProperties properties;
    private final ActivityTrackingService activityTrackingService;
    private final AnalyticsService analyticsService;
    private final SubscriptionService subscriptionService;
    private final UserUsageService userUsageService;
    private final StudyPackUsageService studyPackUsageService;
    private final OcrRateLimitService ocrRateLimitService;
    private final OcrUsageProtectionService ocrUsageProtectionService;
    private final AiRateLimitService aiRateLimitService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final TransactionOperations studyPackGenerationTransactionOperations;
    private final StudyPackGenerationTaskDispatcher studyPackGenerationTaskDispatcher;
    private final ContentModerationService contentModerationService;
    private final ExamQuestionPoolService examQuestionPoolService;
    private final OfficialChallengeQuizTemplateService officialChallengeQuizTemplateService;
    private final OnboardingGuardService onboardingGuardService;
    private final StudyPackQuizMasteryService studyPackQuizMasteryService;
    private final NoteGenerationService noteGenerationService;
    private final NoteGenerationUsageProtectionService noteGenerationUsageProtectionService;
    private final GeneratedQuizService generatedQuizService;

    public StudyPackResponse createFromText(CreateStudyPackRequest request, UUID ownerUserId) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        NoteEntity requestedSourceNote = resolveSourceNoteForGeneration(request.noteId(), ownerUserId, false);
        generationContextResolver.assertGenerationReady(requestedSourceNote);
        String normalizedText = requestedSourceNote == null
                ? normalizeAndValidateText(request.notesText())
                : normalizeAndValidateText(requestedSourceNote.getContent());
        PlanType planType = assertMonthlyStudyPackQuotaAvailable(ownerUserId);
        aiRateLimitService.assertAllowed(ownerUserId, planType, STUDY_PACK);

        StudyPackGenerationContext generationContext = generationContextResolver.resolve(ownerUserId, requestedSourceNote);
        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(
                normalizedText,
                generationContext
        );
        NoteEntity sourceNote = requestedSourceNote == null
                ? createGeneratedNote(ownerUserId, normalizedText, generated, generationContext.courseProgram())
                : requestedSourceNote;
        StudyPackEntity saved = saveStudyPack(
                InputType.TEXT,
                null,
                generated,
                normalizedText,
                ownerUserId,
                planType,
                sourceNote.getId()
        );
        if (requestedSourceNote != null) {
            markNoteGenerated(sourceNote.getId(), sourceNote);
        }
        analyticsService.trackEvent(ownerUserId, AnalyticsEventType.STUDY_PACK_GENERATED, saved.getId(), buildGenerationMetadata(
                sourceNote.getId(),
                InputType.TEXT,
                requestedSourceNote != null
        ));
        examQuestionPoolService.initiatePool(saved, ownerUserId);
        queueOfficialChallengeQuizTemplateSeed(saved);
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=create_studyPack inputType=text latencyMs={}", requestId, latency);
        return mapToResponse(saved, ownerUserId, null, latency);
    }

    public void startAsyncGenerationFromNote(String noteIdRaw, UUID ownerUserId) {
        startAsyncGenerationFromNote(noteIdRaw, ownerUserId, false);
    }

    public void startAsyncGenerationFromNote(String noteIdRaw, UUID ownerUserId, boolean autoApplyGeneratedMetadata) {
        startAsyncGenerationFromNote(noteIdRaw, ownerUserId, autoApplyGeneratedMetadata, true);
    }

    public void startAsyncGenerationFromNote(
            String noteIdRaw,
            UUID ownerUserId,
            boolean autoApplyGeneratedMetadata,
            boolean enforceLimits
    ) {
        startAsyncGenerationFromNote(
                noteIdRaw,
                ownerUserId,
                autoApplyGeneratedMetadata,
                enforceLimits,
                null,
                null
        );
    }

    public void startAsyncGenerationFromNote(
            String noteIdRaw,
            UUID ownerUserId,
            boolean autoApplyGeneratedMetadata,
            boolean enforceLimits,
            StudyPackGenerationContext generationContextOverride,
            String preservedSubject
    ) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        NoteEntity sourceNote = resolveSourceNoteForGeneration(noteIdRaw, ownerUserId, true);
        if (sourceNote == null) {
            throw new NoteNotFoundException();
        }
        generationContextResolver.assertGenerationReady(sourceNote);
        String normalizedText = normalizeAndValidateText(sourceNote.getContent());
        PlanType planType = enforceLimits
                ? assertMonthlyStudyPackQuotaAvailable(ownerUserId)
                : subscriptionService.resolvePlan(ownerUserId);
        if (enforceLimits) {
            aiRateLimitService.assertAllowed(ownerUserId, planType, STUDY_PACK);
        }
        StudyPackGenerationContext generationContext = generationContextOverride == null
                ? generationContextResolver.resolve(ownerUserId, sourceNote)
                : generationContextOverride;
        UUID noteId = sourceNote.getId();

        OffsetDateTime generationEnqueuedAt = OffsetDateTime.now();
        sourceNote.setStatus(NoteStatus.GENERATING);
        sourceNote.setGenerationEnqueuedAt(generationEnqueuedAt);
        sourceNote.setUpdatedAt(generationEnqueuedAt);
        noteRepository.save(sourceNote);

        Runnable generationTask = () -> generateStudyPackFromExistingNoteAsync(
                noteId,
                ownerUserId,
                normalizedText,
                planType,
                generationContext,
                autoApplyGeneratedMetadata,
                enforceLimits,
                preservedSubject,
                null,
                startedAt,
                requestId
        );
        dispatchAfterCommit(generationTask);

        log.info("requestId={} action=start_async_studyPack_generation noteId={}", requestId, noteId);
    }

    /**
     * Regenerates a note's CONTENT and its Study Pack as one operation, preserving both identities.
     *
     * <p>⚠️ THE PAIRING INVARIANT IS THE POINT. A regenerated note must never sit beside a Study Pack
     * built from the content it replaced. That is guaranteed structurally rather than by compensation:
     * BOTH LLM calls run on the async worker before anything is written, and the note content, the pack
     * row and BOTH meters land in the single commit transaction inside
     * {@link #generateStudyPackFromExistingNoteAsync}. A failure at any point therefore persists nothing
     * and charges nothing — no refund path exists because none is needed.
     *
     * <p>⚠️ The quota assertions are ORDERED, and the order is observable: the note-generation meter is
     * checked FIRST, because the learner-facing copy is "Uses 1 topic note and 1 Study Pack" and the
     * first thing it names should be the first thing that blocks.
     *
     * <p>⚠️ A note with no existing Study Pack is REJECTED rather than treated as first generation.
     * Without a prior pack this would silently become "first generation that overwrites the learner's
     * typed content" — a different operation with different disclosure obligations.
     */
    public void startAsyncNoteAndStudyPackRegeneration(String noteIdRaw, UUID ownerUserId) {
        startAsyncNoteAndStudyPackRegeneration(noteIdRaw, ownerUserId, true);
    }

    /**
     * Overload carrying the caller-supplied {@code enforceLimits}, so bulk regeneration can apply the
     * SAME rule bulk generation already applies at {@code NoteController.bulkGenerate}:
     * {@code user.role() != UserRole.ADMIN}.
     *
     * <p>⚠️ THIS WIDENS NOTHING. The ADMIN-only bypass is exactly the one that already exists on every
     * other generation path ({@code startAsyncGenerationFromNote}, {@code NoteBulkGenerationService});
     * a TEACHER curator is metered normally, and the two-argument entry point above — the one
     * {@code POST /notes/&#123;id&#125;/regenerate} uses — still passes {@code true}, so the single-Note
     * contract is byte-identical to what v0.118.0 shipped.
     *
     * <p>⚠️ {@code enforceLimits} gates the ASSERTIONS and the CHARGES together, and it must stay that
     * way. Asserting without charging silently grants free generations; charging without asserting
     * bills past the limit.
     */
    public void startAsyncNoteAndStudyPackRegeneration(
            String noteIdRaw,
            UUID ownerUserId,
            boolean enforceLimits
    ) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        NoteEntity sourceNote = resolveSourceNoteForGeneration(noteIdRaw, ownerUserId, true);
        if (sourceNote == null) {
            throw new NoteNotFoundException();
        }
        UUID noteId = sourceNote.getId();
        if (studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId).isEmpty()) {
            throw new NoteRegenerationStudyPackRequiredException();
        }
        // The note TITLE is the regeneration topic, used verbatim. notes.title is nullable and the request
        // is built here rather than bound from a body, so GenerateNoteFromTopicRequest's @NotBlank can
        // never fire — this is the guard that replaces it. Subject and content are deliberately not a
        // fallback: writing a whole note from a topic we inferred is not what the learner asked for.
        String topic = sourceNote.getTitle() == null ? "" : sourceNote.getTitle().trim();
        if (topic.isEmpty()) {
            throw new NoteRegenerationTopicRequiredException();
        }

        PlanType planType = subscriptionService.resolvePlan(ownerUserId);
        if (enforceLimits) {
            noteGenerationUsageProtectionService.assertQuotaAvailable(ownerUserId, planType);
        }
        PlanType studyPackPlanType = enforceLimits
                ? assertMonthlyStudyPackQuotaAvailable(ownerUserId)
                : planType;
        if (enforceLimits) {
            aiRateLimitService.assertAllowed(ownerUserId, studyPackPlanType, STUDY_PACK);
        }

        generationContextResolver.assertGenerationReady(sourceNote);
        // Resolved FROM THE EXISTING NOTE, so title, subject, tags, Domain Context, Authored Depth and the
        // single resolved Course / Program are inputs to regeneration rather than things it rewrites.
        StudyPackGenerationContext generationContext = generationContextResolver.resolve(ownerUserId, sourceNote);

        OffsetDateTime generationEnqueuedAt = OffsetDateTime.now();
        sourceNote.setStatus(NoteStatus.GENERATING);
        sourceNote.setGenerationEnqueuedAt(generationEnqueuedAt);
        sourceNote.setUpdatedAt(generationEnqueuedAt);
        noteRepository.save(sourceNote);

        GenerateNoteFromTopicRequest noteContentRequest =
                new GenerateNoteFromTopicRequest(topic, null, null);
        Runnable generationTask = () -> generateStudyPackFromExistingNoteAsync(
                noteId,
                ownerUserId,
                // No stored text is carried: the worker generates the note body first and uses THAT as the
                // Study Pack's source, which is exactly why nothing has to be persisted in between.
                null,
                studyPackPlanType,
                generationContext,
                // ⚠️ Never auto-apply LLM-suggested metadata here. Metadata is an INPUT to this operation.
                false,
                enforceLimits,
                null,
                noteContentRequest,
                startedAt,
                requestId
        );
        dispatchAfterCommit(generationTask);

        log.info(
                "requestId={} action=start_async_note_and_studyPack_regeneration noteId={}",
                requestId,
                noteId
        );
    }

    public Object createFromImage(MultipartFile image, String subject, UUID ownerUserId) {
        if (!properties.getOcr().isEnabled()) {
            throw new OcrDisabledException();
        }
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        PlanType planType = assertMonthlyStudyPackQuotaAvailable(ownerUserId);
        ocrUsageProtectionService.assertQuotaAvailable(ownerUserId, planType);
        ocrRateLimitService.assertAllowed(ownerUserId, planType);
        validateImage(image, planType);

        ocrUsageProtectionService.recordUsage(ownerUserId, OffsetDateTime.now(ZoneOffset.UTC));
        OcrResult ocrResult = ocrService.extractText(image);
        String extractedText = mergeSubject(ocrResult.extractedText(), subject);

        if (ocrResult.confidence() < properties.getOcr().getConfidenceThreshold()) {
            StudyPackDraftEntity draft = new StudyPackDraftEntity();
            draft.setId(UUID.randomUUID());
            draft.setOwnerUserId(ownerUserId);
            draft.setExtractedText(extractedText);
            draft.setOcrConfidence(ocrResult.confidence());
            draft.setCreatedAt(OffsetDateTime.now());
            draft.setExpiresAt(OffsetDateTime.now().plusHours(24));
            studyPackDraftRepository.save(draft);

            long latency = System.currentTimeMillis() - startedAt;
            log.info(
                    "requestId={} action=create_studyPack inputType=image outcome=needs_text_confirmation latencyMs={}",
                    requestId,
                    latency
            );
            return toNeedsConfirmation(draft.getId().toString(), extractedText, ocrResult.confidence());
        }

        String normalizedText = normalizeAndValidateText(extractedText);
        aiRateLimitService.assertAllowed(ownerUserId, planType, STUDY_PACK);
        StudyPackGenerationContext generationContext = generationContextResolver.resolve(ownerUserId, null);
        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(
                normalizedText,
                generationContext
        );
        NoteEntity generatedNote = createGeneratedNote(ownerUserId, normalizedText, generated, generationContext.courseProgram());
        StudyPackEntity saved = saveStudyPack(
                InputType.IMAGE,
                ocrResult.confidence(),
                generated,
                normalizedText,
                ownerUserId,
                planType,
                generatedNote.getId()
        );
        markNoteGenerated(generatedNote.getId(), generatedNote);
        analyticsService.trackEvent(ownerUserId, AnalyticsEventType.STUDY_PACK_GENERATED, saved.getId(), buildGenerationMetadata(
                generatedNote.getId(),
                InputType.IMAGE,
                false
        ));
        examQuestionPoolService.initiatePool(saved, ownerUserId);
        queueOfficialChallengeQuizTemplateSeed(saved);
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=create_studyPack inputType=image latencyMs={}", requestId, latency);
        return mapToResponse(saved, ownerUserId, extractedText, latency);
    }

    public StudyPackResponse confirmExtractedText(ConfirmTextRequest request, UUID ownerUserId) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        UUID draftId = UuidParsingUtils.parseUuidOrThrow(request.draftId(), DraftNotFoundException::new);
        StudyPackDraftEntity draft = studyPackDraftRepository.findById(draftId)
                .orElseThrow(DraftNotFoundException::new);
        if (draft.getOwnerUserId() == null || !draft.getOwnerUserId().equals(ownerUserId)) {
            throw new DraftNotFoundException();
        }

        if (draft.getExpiresAt().isBefore(OffsetDateTime.now())) {
            studyPackDraftRepository.delete(draft);
            throw new AppException(
                    "DRAFT_EXPIRED",
                    "This text confirmation has expired. Please upload the image again.",
                    HttpStatus.BAD_REQUEST
            );
        }

        String normalizedText = normalizeAndValidateText(request.notesText());
        PlanType planType = assertMonthlyStudyPackQuotaAvailable(ownerUserId);
        aiRateLimitService.assertAllowed(ownerUserId, planType, STUDY_PACK);
        StudyPackGenerationContext generationContext = generationContextResolver.resolve(ownerUserId, null);
        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(
                normalizedText,
                generationContext
        );
        NoteEntity generatedNote = createGeneratedNote(ownerUserId, normalizedText, generated, generationContext.courseProgram());
        StudyPackEntity saved = saveStudyPack(
                InputType.IMAGE,
                draft.getOcrConfidence(),
                generated,
                normalizedText,
                ownerUserId,
                planType,
                generatedNote.getId()
        );
        markNoteGenerated(generatedNote.getId(), generatedNote);
        studyPackDraftRepository.delete(draft);
        analyticsService.trackEvent(ownerUserId, AnalyticsEventType.STUDY_PACK_GENERATED, saved.getId(), buildGenerationMetadata(
                generatedNote.getId(),
                InputType.IMAGE,
                false
        ));
        examQuestionPoolService.initiatePool(saved, ownerUserId);
        queueOfficialChallengeQuizTemplateSeed(saved);
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=confirm_text latencyMs={}", requestId, latency);
        return mapToResponse(saved, ownerUserId, normalizedText, latency);
    }

    @Transactional(readOnly = true)
    public StudyPackResponse getById(String id, UUID ownerUserId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(id, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(StudyPackNotFoundException::new);
        activityTrackingService.recordActivity(ownerUserId, ActivityType.OPENED_STUDY_PACK, studyPack.getId());
        return mapToResponse(studyPack, ownerUserId, null, null);
    }

    @Transactional(readOnly = true)
    public StudyPackListPageResponse listMine(UUID ownerUserId, Integer limit, String cursor) {
        int pageSize = normalizeLimit(limit);
        int fetchSize = pageSize + 1;
        CreatedAtIdCursorUtils.CursorToken cursorToken = parseCursorToken(cursor);

        List<StudyPackListItemProjection> fetched = cursorToken == null
                ? studyPackRepository.findListItemProjectionsByOwnerUserIdOrderByCreatedAtDescIdDesc(
                        ownerUserId,
                        PageRequest.of(0, fetchSize)
                )
                : studyPackRepository.findListItemProjectionsByOwnerUserIdAndCursor(
                        ownerUserId,
                        cursorToken.createdAt(),
                        cursorToken.id(),
                        PageRequest.of(0, fetchSize)
                );

        boolean hasMore = fetched.size() > pageSize;
        List<StudyPackListItemProjection> pageProjections = hasMore ? fetched.subList(0, pageSize) : fetched;
        Map<UUID, Integer> quizCountsById = loadQuizCountsByStudyPackId(pageProjections);
        List<StudyPackListItemResponse> items = pageProjections.stream()
                .map(projection -> mapToListItemResponse(
                        projection,
                        quizCountsById.getOrDefault(projection.id(), 0)
                ))
                .toList();

        String nextCursor = hasMore && !pageProjections.isEmpty()
                ? encodeCursorToken(pageProjections.getLast().createdAt(), pageProjections.getLast().id())
                : null;

        return new StudyPackListPageResponse(items, nextCursor, hasMore);
    }

    public void deleteMine(String id, UUID ownerUserId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(id, StudyPackNotFoundException::new);
        StudyPackEntity entity = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(StudyPackNotFoundException::new);
        UUID linkedNoteId = entity.getNoteId();
        studyPackRepository.delete(entity);
        if (linkedNoteId != null) {
            noteRepository.findByIdAndOwnerUserId(linkedNoteId, ownerUserId).ifPresent(note -> {
                note.setStatus(NoteStatus.DRAFT);
                note.setUpdatedAt(OffsetDateTime.now());
                noteRepository.save(note);
            });
        }
    }

    public StudyPackResponse updateTags(String id, UUID ownerUserId, List<String> tags) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(id, StudyPackNotFoundException::new);
        StudyPackEntity entity = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(StudyPackNotFoundException::new);
        assertNoteEditable(entity.getNoteId(), ownerUserId);

        List<String> normalizedTags = normalizeEditableTags(tags);
        String[] currentTags = entity.getTags() == null ? new String[0] : entity.getTags();
        String[] nextTags = normalizedTags.toArray(String[]::new);

        StudyPackEntity targetEntity = entity;
        if (!Arrays.equals(currentTags, nextTags)) {
            entity.setTags(nextTags);
            entity.setUpdatedAt(OffsetDateTime.now());
            targetEntity = studyPackRepository.save(entity);
            syncNoteTags(targetEntity.getNoteId(), ownerUserId, nextTags);
        }

        return mapToResponse(targetEntity, ownerUserId, null, null);
    }

    public StudyPackResponse updateMetadata(String id, UUID ownerUserId, String title, String subject) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(id, StudyPackNotFoundException::new);
        StudyPackEntity entity = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(StudyPackNotFoundException::new);
        assertNoteEditable(entity.getNoteId(), ownerUserId);

        String normalizedTitle = normalizeEditableTitle(title);
        String normalizedSubject = normalizeUserSuppliedSubject(subject);

        StudyPackEntity targetEntity = entity;
        if (!Objects.equals(entity.getTitle(), normalizedTitle) || !Objects.equals(entity.getSubject(), normalizedSubject)) {
            entity.setTitle(normalizedTitle);
            entity.setSubject(normalizedSubject);
            entity.setUpdatedAt(OffsetDateTime.now());
            targetEntity = studyPackRepository.save(entity);
            syncNoteMetadata(targetEntity.getNoteId(), ownerUserId, normalizedTitle, normalizedSubject);
        }

        return mapToResponse(targetEntity, ownerUserId, null, null);
    }

    public void recordQuickReviewActivity(String id, UUID ownerUserId, ActivityType activityType) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(id, StudyPackNotFoundException::new);
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(StudyPackNotFoundException::new);

        if (activityType != ActivityType.STARTED_QUICK_REVIEW
                && activityType != ActivityType.COMPLETED_QUICK_REVIEW
                && activityType != ActivityType.COMPLETED_ADAPTIVE_QUIZ) {
            throw new AppException(
                    "INVALID_ACTIVITY_TYPE",
                    "Unsupported study activity type.",
                    HttpStatus.BAD_REQUEST
            );
        }

        activityTrackingService.recordActivity(ownerUserId, activityType, studyPackId);
    }

    public NeedsTextConfirmationResponse toNeedsConfirmation(String draftId, String extractedText, double confidence) {
        return new NeedsTextConfirmationResponse(
                "needs_text_confirmation",
                draftId,
                extractedText,
                new StudyPackMeta(confidence, null)
        );
    }

    private void validateImage(MultipartFile image, PlanType planType) {
        if (image == null || image.isEmpty()) {
            throw new AppException("INVALID_IMAGE", "Please upload an image to continue.", HttpStatus.BAD_REQUEST);
        }
        if (properties.getOcr().getMaxPagesPerUpload() < 1) {
            throw new AppException(
                    "OCR_CONFIGURATION_ERROR",
                    "OCR upload is temporarily unavailable. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        long maxImageBytes = resolveMaxImageBytes(planType);
        if (image.getSize() > maxImageBytes) {
            long maxSizeMb = Math.max(1, maxImageBytes / (1024 * 1024));
            throw new AppException(
                    "IMAGE_TOO_LARGE",
                    "Image is too large. Please upload an image under " + maxSizeMb + "MB.",
                    HttpStatus.BAD_REQUEST
            );
        }

        String contentType = StringUtils.defaultString(image.getContentType()).toLowerCase();
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new AppException(
                    "UNSUPPORTED_IMAGE_TYPE",
                    "Unsupported image type. Please use JPG, PNG, or WEBP.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private long resolveMaxImageBytes(PlanType planType) {
        long configured = planType != null && planType.isPaid()
                ? properties.getOcr().getPremiumMaxImageBytes()
                : properties.getOcr().getFreeMaxImageBytes();
        if (configured > 0) {
            return configured;
        }
        return properties.getSettings().getMaxImageBytes();
    }

    private String normalizeAndValidateText(String raw) {
        String normalized = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new AppException(
                    "EMPTY_NOTES",
                    "Please provide notes text before generating a study pack.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (normalized.length() > properties.getSettings().getMaxNotesChars()) {
            throw new AppException(
                    "NOTES_TOO_LONG",
                    "Notes are too long. Please shorten and try again.",
                    HttpStatus.BAD_REQUEST
            );
        }
        contentModerationService.validateOrThrow(normalized);
        return normalized;
    }

    private String mergeSubject(String extractedText, String subject) {
        if (subject == null || subject.isBlank()) {
            return extractedText;
        }
        return "Subject: " + subject.trim() + ". " + extractedText;
    }

    private StudyPackEntity saveStudyPack(
            InputType inputType,
            Double ocrConfidence,
            GeneratedStudyPackContent generated,
            String sourceText,
            UUID ownerUserId,
            PlanType planType,
            UUID noteId
    ) {
        return saveStudyPack(inputType, ocrConfidence, generated, sourceText, ownerUserId, planType, noteId, true);
    }

    private StudyPackEntity saveStudyPack(
            InputType inputType,
            Double ocrConfidence,
            GeneratedStudyPackContent generated,
            String sourceText,
            UUID ownerUserId,
            PlanType planType,
            UUID noteId,
            boolean recordUsage
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity entity = noteId == null
                ? new StudyPackEntity()
                : studyPackRepository.findByNoteId(noteId).orElseGet(StudyPackEntity::new);
        boolean isNewStudyPack = entity.getId() == null;
        if (isNewStudyPack) {
            entity.setId(UUID.randomUUID());
            entity.setOwnerUserId(ownerUserId);
            entity.setCreatedAt(now);
        }

        entity.setNoteId(noteId);
        entity.setInputType(inputType);
        entity.setTitle(generated.title());
        entity.setSummary(generated.summary());
        String noteSubject = noteId != null
                ? noteRepository.findById(noteId).map(NoteEntity::getSubject).orElse(null)
                : null;
        entity.setSubject(noteSubject != null && !noteSubject.isBlank()
                ? noteSubject
                : normalizeGeneratedSubject(generated.subject()));
        entity.setSourceText(sourceText);
        entity.setKeyConcepts(generated.keyConcepts());
        entity.setQuiz(generated.quiz());
        entity.setOcrConfidence(ocrConfidence);
        entity.setModelTier(planType != null && planType.isPaid() ? ModelTier.PREMIUM : ModelTier.FREE);
        entity.setModelUsed(Optional.ofNullable(generated.modelUsed())
                .orElse(properties.getSettings().getModelFree()));
        entity.setInputTokens(generated.inputTokens());
        entity.setOutputTokens(generated.outputTokens());
        entity.setCachedInputTokens(generated.cachedInputTokens());
        entity.setEstimatedCost(generated.estimatedCost());
        entity.setStatus(StudyPackStatus.DONE);
        entity.setErrorCode(null);
        entity.setUpdatedAt(now);
        entity.setTags(resolveTags(generated.tags(), generated.title()));
        StudyPackEntity savedEntity = studyPackRepository.save(entity);
        if (recordUsage) {
            userUsageService.incrementStudyPackGeneration(ownerUserId, now);
        }
        activityTrackingService.recordActivity(ownerUserId, ActivityType.CREATED_STUDY_PACK, savedEntity.getId());
        return savedEntity;
    }

    private NoteEntity resolveSourceNoteForGeneration(String noteIdRaw, UUID ownerUserId, boolean allowExistingStudyPack) {
        if (noteIdRaw == null || noteIdRaw.isBlank()) {
            return null;
        }

        UUID noteId = UuidParsingUtils.parseUuidOrThrow(noteIdRaw, NoteNotFoundException::new);

        NoteEntity sourceNote = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(NoteNotFoundException::new);

        NoteStatus sourceStatus = sourceNote.getStatus() == null ? NoteStatus.DRAFT : sourceNote.getStatus();
        if (sourceStatus == NoteStatus.GENERATING) {
            throw new NoteGenerationInProgressException();
        }

        boolean hasExistingStudyPack = studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId).isPresent();
        if (hasExistingStudyPack && !allowExistingStudyPack) {
            throw new AppException(
                    ERROR_NOTE_ALREADY_HAS_STUDY_PACK,
                    MESSAGE_NOTE_ALREADY_HAS_STUDY_PACK,
                    HttpStatus.CONFLICT
            );
        }

        return sourceNote;
    }

    private void dispatchAfterCommit(Runnable generationTask) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchStudyPackGeneration(generationTask);
                }
            });
            return;
        }
        dispatchStudyPackGeneration(generationTask);
    }

    private void dispatchStudyPackGeneration(Runnable generationTask) {
        studyPackGenerationTaskDispatcher.execute(generationTask);
    }

    private void queueOfficialChallengeQuizTemplateSeed(StudyPackEntity studyPack) {
        if (studyPack == null || studyPack.getNoteId() == null) {
            return;
        }
        noteRepository.findById(studyPack.getNoteId())
                .ifPresent(note -> officialChallengeQuizTemplateService.queueSeedIfEligible(note, studyPack));
    }

    /**
     * @param noteContentRegenerationRequest when non-null, the note's own CONTENT is regenerated first and
     *                                       becomes the Study Pack's source text, so one operation replaces
     *                                       both artifacts. Null on every pre-existing path, which then
     *                                       behaves exactly as before.
     *                                       <p>⚠️ This method is deliberately PRIVATE and invoked through a
     *                                       lambda on the generation executor, so the class-level
     *                                       {@code @Transactional} proxy does NOT apply and both LLM calls
     *                                       run with no transaction and no JDBC connection held
     *                                       ({@code v0.112.0}). Making it public — or calling it through
     *                                       the proxy — would silently wrap both LLM calls in one
     *                                       transaction.
     */
    private void generateStudyPackFromExistingNoteAsync(
            UUID noteId,
            UUID ownerUserId,
            String normalizedText,
            PlanType planType,
            StudyPackGenerationContext generationContext,
            boolean autoApplyGeneratedMetadata,
            boolean recordUsage,
            String preservedSubject,
            GenerateNoteFromTopicRequest noteContentRegenerationRequest,
            long startedAt,
            String requestId
    ) {
        boolean regeneratingNoteContent = noteContentRegenerationRequest != null;
        try {
            // LLM call 1 (combined scope only). Charging is deferred: recordUsage=false here, and the
            // note-generation meter is incremented inside the commit transaction below, so a Study Pack
            // failure after this point leaves BOTH meters untouched.
            String rawGeneratedNoteBody = regeneratingNoteContent
                    ? noteGenerationService
                            // ⚠️ THE LAST ARGUMENT IS enforceLimits, ARRIVING HERE AS recordUsage.
                            // On the regeneration path the caller passes enforceLimits into that
                            // parameter, so the two are the same value; the fourth argument stays
                            // false because THIS class charges the note meter at commit, not
                            // generateFromTopic.
                            // ⚠️ Until v0.119.0 generateFromTopic asserted the note-generation quota
                            // UNCONDITIONALLY, so an ADMIN who had skipped the request-side check
                            // still hit it here — and because the exception surfaces on the generation
                            // thread it was swallowed, marking every combined item FAILED with no
                            // reason. Found by the v0.119.0 pressure test.
                            .generateFromTopic(
                                    noteContentRegenerationRequest, ownerUserId, generationContext,
                                    false, recordUsage)
                            .content()
                    : null;
            // ⚠️ TWO VALUES FROM ONE GENERATION, AND CONFLATING THEM SILENTLY RUINS THE NOTE.
            // normalizeAndValidateText collapses every run of whitespace to a single space — right for the
            // LLM input and the study_packs.source_text column, and ruinous for the note BODY, which is a
            // structured document (title, blank line, "📘 Overview", bullet lines; see
            // OpenAiLlmStudyPackService#buildGeneratedNoteContent). Full Notes renders notes.content under
            // `whitespace-pre-wrap`, and the ordinary topic → create path stores it trimmed only
            // (NoteService#normalizeRequiredContent), so writing the collapsed form here would flatten a
            // regenerated note into one unreadable paragraph while first generation kept its structure.
            // Validation — blank, length and content moderation — still runs on the collapsed form, and
            // runs FIRST, so a blank or oversized body throws before either value is used.
            String sourceText = regeneratingNoteContent
                    ? normalizeAndValidateText(rawGeneratedNoteBody)
                    : normalizedText;
            String regeneratedNoteBody = rawGeneratedNoteBody == null ? null : rawGeneratedNoteBody.trim();
            // LLM call 2. On the combined path this reads the freshly generated body from memory — nothing
            // has been persisted yet, which is what makes "new content beside an old pack" unreachable.
            GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(
                    sourceText,
                    generationContext
            );
            StudyPackEntity saved = studyPackGenerationTransactionOperations.execute(status -> {
                NoteEntity sourceNote = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                        .orElseThrow(NoteNotFoundException::new);
                if (sourceNote.getStatus() != NoteStatus.GENERATING) {
                    // Generation recovery safety interlock: a late worker must discard its result
                    // instead of resurrecting a note already resolved to FAILED for the learner.
                    // ⚠️ It runs FIRST, before the content write, saveStudyPack, either meter and the
                    // share-link deactivation, so a declined worker persists nothing, charges nothing and
                    // deactivates nothing.
                    log.info(
                            "requestId={} action=complete_async_studyPack_generation noteId={} outcome=skipped status={}",
                            requestId,
                            noteId,
                            sourceNote.getStatus()
                    );
                    return null;
                }
                if (regeneratingNoteContent) {
                    // The BODY, not the collapsed LLM input — see the comment at the generation call above.
                    sourceNote.setContent(regeneratedNoteBody);
                    noteRepository.save(sourceNote);
                }
                StudyPackEntity savedEntity = saveStudyPack(
                        InputType.TEXT,
                        null,
                        generated,
                        sourceText,
                        ownerUserId,
                        planType,
                        noteId,
                        recordUsage
                );
                markNoteGenerated(noteId, sourceNote);
                if (regeneratingNoteContent) {
                    if (recordUsage) {
                        // The second meter. saveStudyPack above charged the Study Pack one; both land in
                        // this transaction so the operation is all-or-nothing on money as well as on
                        // content.
                        // ⚠️ GATED ON THE SAME FLAG AS THE STUDY PACK METER, AND ON THE SAME FLAG THAT
                        // GATED THE ASSERTIONS AT THE REQUEST SIDE. An ungated charge here would bill an
                        // ADMIN bulk batch that was never quota-asserted; an over-eager gate would leave
                        // a metered TEACHER batch free. Both directions are pinned by tests.
                        noteGenerationUsageProtectionService.recordUsage(
                                ownerUserId, OffsetDateTime.now(ZoneOffset.UTC));
                    }
                    // The note's shared quiz was built from the content we just replaced. Deactivate its
                    // live links rather than letting a recipient be graded against questions drawn from
                    // material that no longer exists (v0.110.2's rule, reusing its implementation).
                    generatedQuizService.deactivateShareLinksForNote(noteId, ownerUserId);
                }
                if (preservedSubject != null) {
                    applyBulkGeneratedMetadataToNote(sourceNote, generated, preservedSubject);
                } else if (autoApplyGeneratedMetadata) {
                    applyGeneratedMetadataToNote(sourceNote, generated);
                }
                return savedEntity;
            });

            if (saved == null) {
                // The interlock above declined to persist: the note is no longer GENERATING, so a
                // recovery sweep (or a mid-generation delete) already resolved it and the learner has
                // been told so. Return quietly. Dereferencing `saved` here used to throw an NPE that
                // the catch below swallowed into a false `outcome=failed` with a stack trace — on the
                // one surface whose purpose is operational visibility — and re-wrote FAILED, bumping
                // `updated_at` and floating the note to the top of a library sorted by that column.
                return;
            }

            analyticsService.trackEvent(ownerUserId, AnalyticsEventType.STUDY_PACK_GENERATED, saved.getId(), buildGenerationMetadata(
                    noteId,
                    InputType.TEXT,
                    true
            ));
            examQuestionPoolService.initiatePool(saved, ownerUserId);
            queueOfficialChallengeQuizTemplateSeed(saved);
            long latency = System.currentTimeMillis() - startedAt;
            log.info("requestId={} action=complete_async_studyPack_generation noteId={} latencyMs={}", requestId, noteId, latency);
        } catch (Exception ex) {
            studyPackGenerationTransactionOperations.execute(status -> {
                markNoteGenerationFailed(noteId, ownerUserId);
                return null;
            });
            long latency = System.currentTimeMillis() - startedAt;
            log.warn(
                    "requestId={} action=complete_async_studyPack_generation noteId={} outcome=failed latencyMs={}",
                    requestId,
                    noteId,
                    latency,
                    ex
            );
        }
    }

    private NoteEntity createGeneratedNote(
            UUID ownerUserId,
            String normalizedContent,
            GeneratedStudyPackContent generated,
            String courseProgram
    ) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(ownerUserId);
        note.setTitle(generated.title());
        note.setSubject(normalizeGeneratedSubject(generated.subject()));
        note.setCourseProgram(normalizeCourseProgram(courseProgram));
        note.setTags(resolveTags(generated.tags(), generated.title()));
        note.setContent(normalizedContent);
        note.setStatus(NoteStatus.GENERATED);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setSourceNoteId(null);
        note.setTargetProfileType(resolveTargetProfileType(ownerUserId));
        note.setCreatedAt(OffsetDateTime.now());
        note.setUpdatedAt(OffsetDateTime.now());
        return noteRepository.save(note);
    }

    // notes.target_profile_type is NOT NULL with no database default, so this path has to supply a
    // value even though nothing reads the column any more (Target Audience was removed from every
    // product surface in v0.83.0 and the column survives only as migration evidence until phase 4).
    // Derived rather than hardcoded because SPEC.md documents the contract as "derives a constrained
    // non-null value from the owner's profile"; a constant would write an audience the owner never
    // chose. Missing owner falls through to the same STUDENT default the mapping already uses.
    private NoteTargetProfileType resolveTargetProfileType(UUID ownerUserId) {
        return NoteTargetProfileType.forOwnerProfile(
                userRepository.findById(ownerUserId).map(UserEntity::getProfileType).orElse(null)
        );
    }

    private StudyPackResponse mapToResponse(
            StudyPackEntity entity,
            UUID ownerUserId,
            String extractedText,
            Long latencyMs
    ) {
        StudyPackQuizMastery quizMastery = studyPackQuizMasteryService.resolve(ownerUserId, entity);
        return new StudyPackResponse(
                entity.getId().toString(),
                entity.getNoteId() == null ? null : entity.getNoteId().toString(),
                entity.getInputType().name().toLowerCase(),
                extractedText,
                entity.getTitle(),
                entity.getSummary(),
                entity.getSourceText(),
                entity.getSubject(),
                entity.getKeyConcepts(),
                entity.getTags() == null ? List.of() : Arrays.asList(entity.getTags()),
                entity.getQuiz(),
                quizMastery.mastered(),
                quizMastery.masteredAt(),
                entity.getCreatedAt(),
                new StudyPackMeta(entity.getOcrConfidence(), latencyMs)
        );
    }

    private Map<UUID, Integer> loadQuizCountsByStudyPackId(List<StudyPackListItemProjection> projections) {
        if (projections.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids = projections.stream()
                .map(StudyPackListItemProjection::id)
                .toList();
        Map<UUID, Integer> countsById = new LinkedHashMap<>();
        studyPackRepository.findQuizCountsByIdIn(ids).forEach(row -> {
            UUID studyPackId = toUuid(row[0]);
            int questionCount = row[1] instanceof Number number ? number.intValue() : 0;
            countsById.put(studyPackId, questionCount);
        });
        return countsById;
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private StudyPackListItemResponse mapToListItemResponse(
            StudyPackListItemProjection projection,
            int questionCount
    ) {
        return new StudyPackListItemResponse(
                projection.id().toString(),
                projection.title(),
                SummaryPreviewUtils.buildSummaryPreview(projection.summary(), 140),
                questionCount,
                projection.subject(),
                projection.tags() == null ? List.of() : Arrays.asList(projection.tags()),
                projection.createdAt()
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.clamp(limit, 1, MAX_LIST_LIMIT);
    }

    private CreatedAtIdCursorUtils.CursorToken parseCursorToken(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return CreatedAtIdCursorUtils.decode(cursor);
        } catch (Exception ex) {
            throw new AppException(
                    "INVALID_CURSOR",
                    "Invalid pagination cursor.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String encodeCursorToken(OffsetDateTime createdAt, UUID id) {
        return CreatedAtIdCursorUtils.encode(createdAt, id);
    }

    private String[] resolveTags(List<String> generatedTags, String fallbackTitle) {
        if (generatedTags != null) {
            List<String> normalizedTags = generatedTags.stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (!normalizedTags.isEmpty()) {
                return normalizedTags.toArray(String[]::new);
            }
        }

        if (fallbackTitle == null || fallbackTitle.isBlank()) {
            return new String[0];
        }
        return new String[]{fallbackTitle.trim()};
    }

    private List<String> normalizeEditableTags(List<String> rawTags) {
        if (rawTags == null) {
            throw new AppException("INVALID_TAGS", "Tags are required.", HttpStatus.BAD_REQUEST);
        }

        LinkedHashMap<String, String> normalizedByKey = new LinkedHashMap<>();
        for (String rawTag : rawTags) {
            String normalizedTag = StringUtils.defaultString(rawTag).trim();
            if (normalizedTag.isBlank()) {
                continue;
            }
            if (normalizedTag.length() > MAX_TAG_LENGTH) {
                throw new AppException(
                        "TAG_TOO_LONG",
                        "Tags must be 30 characters or fewer.",
                        HttpStatus.BAD_REQUEST
                );
            }

            String duplicateKey = normalizedTag.toLowerCase(Locale.ROOT);
            normalizedByKey.putIfAbsent(duplicateKey, normalizedTag);
            if (normalizedByKey.size() > MAX_TAGS_PER_STUDY_PACK) {
                throw new AppException(
                        "TOO_MANY_TAGS",
                        "You can add up to 30 tags per Study Pack.",
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        return List.copyOf(normalizedByKey.values());
    }

    private String normalizeUserSuppliedSubject(String subject) {
        String normalized = SubjectNormalizationUtils.normalizeForStorage(subject);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > NoteMetadataBounds.SUBJECT_MAX_LENGTH) {
            throw new SubjectTooLongException();
        }
        return resolveCanonicalSubject(normalized);
    }

    private String normalizeGeneratedSubject(String subject) {
        String normalized = SubjectNormalizationUtils.normalizeForStorage(subject);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > NoteMetadataBounds.SUBJECT_MAX_LENGTH) {
            normalized = clampGeneratedSubject(normalized);
        }
        return resolveCanonicalSubject(normalized);
    }

    private String clampGeneratedSubject(String subject) {
        int maxLength = NoteMetadataBounds.SUBJECT_MAX_LENGTH;
        int lastSpace = subject.lastIndexOf(' ', maxLength);
        if (lastSpace >= maxLength - GENERATED_SUBJECT_WORD_BOUNDARY_WINDOW) {
            return subject.substring(0, lastSpace).trim();
        }
        return subject.substring(0, maxLength).trim();
    }

    private String resolveCanonicalSubject(String normalized) {
        String lookup = SubjectNormalizationUtils.normalizeForLookup(normalized);
        return noteRepository.findAllSubjectValues().stream()
                .map(SubjectNormalizationUtils::normalizeForStorage)
                .filter(Objects::nonNull)
                .sorted(SUBJECT_DISPLAY_COMPARATOR)
                .filter(existing -> SubjectNormalizationUtils.normalizeForLookup(existing).equals(lookup))
                .findFirst()
                .orElse(normalized);
    }

    /**
     * Clamps rather than throws, for the same reason {@code normalizeGeneratedSubject} does: this runs
     * inside note creation that follows an already-billed LLM call, and normalization can GROW the value
     * (a bare hyphen expands to " - "), so a stored-or-derived program sitting near the column bound can
     * cross it here. Failing would discard a completed generation over a secondary metadata field.
     */
    private String normalizeCourseProgram(String courseProgram) {
        String normalized = CourseProgramNormalizationUtils.normalizeForStorage(courseProgram);
        if (normalized != null && normalized.length() > NoteMetadataBounds.COURSE_PROGRAM_MAX_LENGTH) {
            return normalized.substring(0, NoteMetadataBounds.COURSE_PROGRAM_MAX_LENGTH).trim();
        }
        return normalized;
    }

    private String normalizeEditableTitle(String title) {
        if (title == null) {
            throw new AppException("INVALID_TITLE", "Title is required.", HttpStatus.BAD_REQUEST);
        }
        String normalized = title.trim();
        if (normalized.isEmpty()) {
            throw new AppException("INVALID_TITLE", "Title is required.", HttpStatus.BAD_REQUEST);
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new AppException(
                    "TITLE_TOO_LONG",
                    "Title must be 180 characters or fewer.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private PlanType assertMonthlyStudyPackQuotaAvailable(UUID ownerUserId) {
        PlanType planType = subscriptionService.resolvePlan(ownerUserId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int usedThisMonth = studyPackUsageService.resolveUsage(ownerUserId, now).usedCount();

        int monthlyLimit = resolveMonthlyStudyPackLimit(planType);
        if (usedThisMonth < monthlyLimit) {
            return planType;
        }

        throw new AppException(
                "MONTHLY_STUDY_PACK_LIMIT_REACHED",
                resolveQuotaReachedMessage(planType, monthlyLimit),
                HttpStatus.FORBIDDEN
        );
    }

    private int resolveMonthlyStudyPackLimit(PlanType planType) {
        return properties.getPricing().resolveMonthlyStudyPackLimit(planType);
    }

    private String resolveQuotaReachedMessage(PlanType planType, int limit) {
        if (planType != null && planType.isPaid()) {
            return "You have reached your Study Pack limit for the current billing period (" + limit + ").";
        }
        return "You have reached your Free plan limit for the current billing period (" + limit + " Study Packs). Upgrade to unlock higher limits.";
    }

    private void markNoteGenerated(UUID noteId, NoteEntity cachedNote) {
        NoteEntity note = cachedNote == null
                ? noteRepository.findById(noteId).orElse(null)
                : cachedNote;
        if (note == null) {
            return;
        }
        if (note.getStatus() == NoteStatus.GENERATED) {
            return;
        }
        note.setStatus(NoteStatus.GENERATED);
        note.setUpdatedAt(OffsetDateTime.now());
        noteRepository.save(note);
    }

    private void markNoteGenerationFailed(UUID noteId, UUID ownerUserId) {
        noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId).ifPresent(this::markNoteGenerationFailed);
    }

    public void markNoteGenerationFailed(NoteEntity note) {
        if (note.getStatus() == NoteStatus.GENERATED) {
            return;
        }
        note.setStatus(NoteStatus.FAILED);
        note.setUpdatedAt(OffsetDateTime.now());
        noteRepository.save(note);
    }

    private void syncNoteTags(UUID noteId, UUID ownerUserId, String[] tags) {
        if (noteId == null) {
            return;
        }
        noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId).ifPresent(note -> {
            note.setTags(tags == null ? new String[0] : Arrays.copyOf(tags, tags.length));
            note.setUpdatedAt(OffsetDateTime.now());
            noteRepository.save(note);
        });
    }

    private void syncNoteMetadata(UUID noteId, UUID ownerUserId, String title, String subject) {
        if (noteId == null) {
            return;
        }
        noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId).ifPresent(note -> {
            note.setTitle(title);
            note.setSubject(subject);
            note.setUpdatedAt(OffsetDateTime.now());
            noteRepository.save(note);
        });
    }

    private void applyGeneratedMetadataToNote(NoteEntity note, GeneratedStudyPackContent generated) {
        boolean changed = false;
        if ((note.getSubject() == null || note.getSubject().isBlank()) && generated.subject() != null) {
            note.setSubject(normalizeGeneratedSubject(generated.subject()));
            changed = true;
        }
        String[] existingTags = note.getTags();
        if ((existingTags == null || existingTags.length == 0) && generated.tags() != null && !generated.tags().isEmpty()) {
            note.setTags(resolveTags(generated.tags(), generated.title()));
            changed = true;
        }
        if (changed) {
            note.setUpdatedAt(OffsetDateTime.now());
            noteRepository.save(note);
        }
    }

    private void applyBulkGeneratedMetadataToNote(
            NoteEntity note,
            GeneratedStudyPackContent generated,
            String preservedSubject
    ) {
        note.setTitle(normalizeEditableTitle(generated.title()));
        note.setTags(resolveTags(generated.tags(), generated.title()));
        note.setSubject(normalizeUserSuppliedSubject(preservedSubject));
        note.setUpdatedAt(OffsetDateTime.now());
        noteRepository.save(note);
    }

    private LinkedHashMap<String, Object> buildGenerationMetadata(UUID noteId, InputType inputType, boolean generatedFromExistingNote) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        if (noteId != null) {
            metadata.put("noteId", noteId.toString());
        }
        if (inputType != null) {
            metadata.put("inputType", inputType.name());
        }
        metadata.put("generatedFromExistingNote", generatedFromExistingNote);
        return metadata;
    }

    private void assertNoteEditable(UUID noteId, UUID ownerUserId) {
        if (noteId == null) {
            return;
        }
        NoteEntity linkedNote = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(NoteNotFoundException::new);
        if (linkedNote.getStatus() == NoteStatus.GENERATED) {
            throw new AppException(
                    "NOTE_LOCKED",
                    "This note is locked because it already has a Study Pack. Make a copy to edit.",
                    HttpStatus.CONFLICT
            );
        }
    }
}
