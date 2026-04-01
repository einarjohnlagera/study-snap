package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.CreateStudyPackRequest;
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
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.StudyPackDraftEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.DraftNotFoundException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackDraftRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.security.OcrRateLimitService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.OcrResult;
import com.studysnap.backend.util.CreatedAtIdCursorUtils;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
    private static final String STUDY_PACK = "study-pack";

    private final StudyPackRepository studyPackRepository;
    private final StudyPackDraftRepository studyPackDraftRepository;
    private final NoteRepository noteRepository;
    private final OcrService ocrService;
    private final LlmStudyPackService llmStudyPackService;
    private final StudySnapProperties properties;
    private final ActivityTrackingService activityTrackingService;
    private final AnalyticsService analyticsService;
    private final SubscriptionService subscriptionService;
    private final UserUsageService userUsageService;
    private final BillingUsagePeriodService billingUsagePeriodService;
    private final OcrRateLimitService ocrRateLimitService;
    private final OcrUsageProtectionService ocrUsageProtectionService;
    private final AiRateLimitService aiRateLimitService;

    public StudyPackResponse createFromText(CreateStudyPackRequest request, UUID ownerUserId) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        NoteEntity requestedSourceNote = resolveSourceNoteForGeneration(request.noteId(), ownerUserId);
        String normalizedText = requestedSourceNote == null
                ? normalizeAndValidateText(request.notesText())
                : normalizeAndValidateText(requestedSourceNote.getContent());
        PlanType planType = assertMonthlyStudyPackQuotaAvailable(ownerUserId);
        aiRateLimitService.assertAllowed(ownerUserId, planType, STUDY_PACK);

        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(normalizedText);
        NoteEntity sourceNote = requestedSourceNote == null
                ? createGeneratedNote(ownerUserId, normalizedText, generated)
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
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=create_studyPack inputType=text latencyMs={}", requestId, latency);
        return mapToResponse(saved, null, latency);
    }

    public Object createFromImage(MultipartFile image, String subject, UUID ownerUserId) {
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
        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(normalizedText);
        NoteEntity generatedNote = createGeneratedNote(ownerUserId, normalizedText, generated);
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
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=create_studyPack inputType=image latencyMs={}", requestId, latency);
        return mapToResponse(saved, extractedText, latency);
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
        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(normalizedText);
        NoteEntity generatedNote = createGeneratedNote(ownerUserId, normalizedText, generated);
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
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=confirm_text latencyMs={}", requestId, latency);
        return mapToResponse(saved, normalizedText, latency);
    }

    @Transactional(readOnly = true)
    public StudyPackResponse getById(String id, UUID ownerUserId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(id, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(StudyPackNotFoundException::new);
        activityTrackingService.recordActivity(ownerUserId, ActivityType.OPENED_STUDY_PACK, studyPack.getId());
        return mapToResponse(studyPack, null, null);
    }

    @Transactional(readOnly = true)
    public StudyPackListPageResponse listMine(UUID ownerUserId, Integer limit, String cursor) {
        int pageSize = normalizeLimit(limit);
        int fetchSize = pageSize + 1;
        CreatedAtIdCursorUtils.CursorToken cursorToken = parseCursorToken(cursor);

        List<StudyPackEntity> fetched = cursorToken == null
                ? studyPackRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(ownerUserId, PageRequest.of(0, fetchSize))
                : studyPackRepository.findByOwnerUserIdAndCursor(
                        ownerUserId,
                        cursorToken.createdAt(),
                        cursorToken.id(),
                        PageRequest.of(0, fetchSize)
                );

        boolean hasMore = fetched.size() > pageSize;
        List<StudyPackEntity> pageEntities = hasMore ? fetched.subList(0, pageSize) : fetched;
        List<StudyPackListItemResponse> items = pageEntities.stream()
                .map(this::mapToListItemResponse)
                .toList();

        String nextCursor = hasMore && !pageEntities.isEmpty()
                ? encodeCursorToken(pageEntities.getLast().getCreatedAt(), pageEntities.getLast().getId())
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

        return mapToResponse(targetEntity, null, null);
    }

    public StudyPackResponse updateMetadata(String id, UUID ownerUserId, String title, String subject) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(id, StudyPackNotFoundException::new);
        StudyPackEntity entity = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(StudyPackNotFoundException::new);
        assertNoteEditable(entity.getNoteId(), ownerUserId);

        String normalizedTitle = normalizeEditableTitle(title);
        String normalizedSubject = normalizeSubject(subject);

        StudyPackEntity targetEntity = entity;
        if (!Objects.equals(entity.getTitle(), normalizedTitle) || !Objects.equals(entity.getSubject(), normalizedSubject)) {
            entity.setTitle(normalizedTitle);
            entity.setSubject(normalizedSubject);
            entity.setUpdatedAt(OffsetDateTime.now());
            targetEntity = studyPackRepository.save(entity);
            syncNoteMetadata(targetEntity.getNoteId(), ownerUserId, normalizedTitle, normalizedSubject);
        }

        return mapToResponse(targetEntity, null, null);
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
        long configured = planType == PlanType.PREMIUM
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
        StudyPackEntity entity = new StudyPackEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setCreatedAt(OffsetDateTime.now());

        entity.setNoteId(noteId);
        entity.setInputType(inputType);
        entity.setTitle(generated.title());
        entity.setSummary(generated.summary());
        entity.setSubject(normalizeSubject(generated.subject()));
        entity.setSourceText(sourceText);
        entity.setKeyConcepts(generated.keyConcepts());
        entity.setQuiz(generated.quiz());
        entity.setOcrConfidence(ocrConfidence);
        entity.setModelTier(planType == PlanType.PREMIUM ? ModelTier.PREMIUM : ModelTier.FREE);
        entity.setModelUsed(Optional.ofNullable(generated.modelUsed())
                .orElse(properties.getSettings().getModelFree()));
        entity.setInputTokens(generated.inputTokens());
        entity.setOutputTokens(generated.outputTokens());
        entity.setCachedInputTokens(generated.cachedInputTokens());
        entity.setEstimatedCost(generated.estimatedCost());
        entity.setStatus(StudyPackStatus.DONE);
        entity.setErrorCode(null);
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setTags(resolveTags(generated.tags(), generated.title()));
        StudyPackEntity savedEntity = studyPackRepository.save(entity);
        userUsageService.incrementStudyPackGeneration(ownerUserId, savedEntity.getCreatedAt());
        activityTrackingService.recordActivity(ownerUserId, ActivityType.CREATED_STUDY_PACK, savedEntity.getId());
        return savedEntity;
    }

    private NoteEntity resolveSourceNoteForGeneration(String noteIdRaw, UUID ownerUserId) {
        if (noteIdRaw == null || noteIdRaw.isBlank()) {
            return null;
        }

        UUID noteId = UuidParsingUtils.parseUuidOrThrow(noteIdRaw, NoteNotFoundException::new);

        NoteEntity sourceNote = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(NoteNotFoundException::new);

        if (sourceNote.getStatus() == NoteStatus.GENERATED) {
            throw new AppException(
                    "NOTE_ALREADY_GENERATED",
                    "This note is already generated. Make a copy to create a new Study Pack.",
                    HttpStatus.CONFLICT
            );
        }

        boolean hasExistingStudyPack = studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId).isPresent();
        if (hasExistingStudyPack) {
            throw new AppException(
                    "NOTE_ALREADY_HAS_STUDY_PACK",
                    "This note already has a Study Pack. Make a copy to generate a new version.",
                    HttpStatus.CONFLICT
            );
        }

        return sourceNote;
    }

    private NoteEntity createGeneratedNote(
            UUID ownerUserId,
            String normalizedContent,
            GeneratedStudyPackContent generated
    ) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(ownerUserId);
        note.setTitle(generated.title());
        note.setSubject(normalizeSubject(generated.subject()));
        note.setTags(resolveTags(generated.tags(), generated.title()));
        note.setContent(normalizedContent);
        note.setStatus(NoteStatus.GENERATED);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setSourceNoteId(null);
        note.setCreatedAt(OffsetDateTime.now());
        note.setUpdatedAt(OffsetDateTime.now());
        return noteRepository.save(note);
    }

    private StudyPackResponse mapToResponse(StudyPackEntity entity, String extractedText, Long latencyMs) {
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
                entity.getCreatedAt(),
                new StudyPackMeta(entity.getOcrConfidence(), latencyMs)
        );
    }

    private StudyPackListItemResponse mapToListItemResponse(StudyPackEntity entity) {
        return new StudyPackListItemResponse(
                entity.getId().toString(),
                entity.getTitle(),
                SummaryPreviewUtils.buildSummaryPreview(entity.getSummary(), 140),
                entity.getQuiz() == null ? 0 : entity.getQuiz().size(),
                entity.getSubject(),
                entity.getTags() == null ? List.of() : Arrays.asList(entity.getTags()),
                entity.getCreatedAt()
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
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

    private String normalizeSubject(String subject) {
        if (subject == null) {
            return null;
        }
        String normalized = subject.trim();
        return normalized.isBlank() ? null : normalized;
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
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(ownerUserId, now);

        long usedFromStudyPacks = studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                ownerUserId,
                usagePeriod.periodStart(),
                usagePeriod.periodEnd()
        );
        long usedFromUsage = userUsageService.getMonthlyUsage(ownerUserId, now).studyPackGenerations();
        long usedThisMonth = Math.max(usedFromStudyPacks, usedFromUsage);

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
        if (planType == PlanType.PREMIUM) {
            return "You have reached your Study Pack limit for the current billing period (" + limit + ").";
        }
        return "You have reached your Free plan limit for the current billing period (" + limit + " Study Packs). Upgrade to Premium for higher limits.";
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
