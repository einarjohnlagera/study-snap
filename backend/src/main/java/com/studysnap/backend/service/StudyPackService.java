package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.NeedsTextConfirmationResponse;
import com.studysnap.backend.dto.StudyPackListPageResponse;
import com.studysnap.backend.dto.StudyPackMeta;
import com.studysnap.backend.dto.StudyPackListItemResponse;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.StudyPackDraftEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.StudyPackDraftRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.OcrResult;
import com.studysnap.backend.util.CreatedAtIdCursorUtils;
import com.studysnap.backend.util.SummaryPreviewUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
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

    private final StudyPackRepository studyPackRepository;
    private final StudyPackDraftRepository studyPackDraftRepository;
    private final OcrService ocrService;
    private final LlmStudyPackService llmStudyPackService;
    private final StudySnapProperties properties;
    private final ActivityTrackingService activityTrackingService;
    private final SubscriptionService subscriptionService;

    public StudyPackResponse createFromText(CreateStudyPackRequest request, UUID ownerUserId) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        String normalizedText = normalizeAndValidateText(request.notesText());
        PlanType planType = assertMonthlyStudyPackQuotaAvailable(ownerUserId);

        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(normalizedText);
        StudyPackEntity saved = saveStudyPack(InputType.TEXT, null, generated, normalizedText, ownerUserId, planType);
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=create_studyPack inputType=text latencyMs={}", requestId, latency);
        return mapToResponse(saved, null, latency);
    }

    public Object createFromImage(MultipartFile image, String subject, UUID ownerUserId) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        PlanType planType = assertMonthlyStudyPackQuotaAvailable(ownerUserId);
        validateImage(image);

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
        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(normalizedText);
        StudyPackEntity saved = saveStudyPack(InputType.IMAGE, ocrResult.confidence(), generated, normalizedText, ownerUserId, planType);
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=create_studyPack inputType=image latencyMs={}", requestId, latency);
        return mapToResponse(saved, extractedText, latency);
    }

    public StudyPackResponse confirmExtractedText(ConfirmTextRequest request, UUID ownerUserId) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        UUID draftId = UuidParsingUtils.parseUuidOrThrow(
                request.draftId(),
                "DRAFT_NOT_FOUND",
                "Draft not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackDraftEntity draft = studyPackDraftRepository.findById(draftId)
                .orElseThrow(() -> new AppException("DRAFT_NOT_FOUND", "Draft not found.", HttpStatus.NOT_FOUND));
        if (draft.getOwnerUserId() == null || !draft.getOwnerUserId().equals(ownerUserId)) {
            throw new AppException("DRAFT_NOT_FOUND", "Draft not found.", HttpStatus.NOT_FOUND);
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
        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(normalizedText);
        StudyPackEntity saved = saveStudyPack(InputType.IMAGE, draft.getOcrConfidence(), generated, normalizedText, ownerUserId, planType);
        studyPackDraftRepository.delete(draft);
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=confirm_text latencyMs={}", requestId, latency);
        return mapToResponse(saved, normalizedText, latency);
    }

    @Transactional(readOnly = true)
    public StudyPackResponse getById(String id, UUID ownerUserId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));
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
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity entity = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));
        studyPackRepository.delete(entity);
    }

    public StudyPackResponse updateTags(String id, UUID ownerUserId, List<String> tags) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity entity = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        List<String> normalizedTags = normalizeEditableTags(tags);
        String[] currentTags = entity.getTags() == null ? new String[0] : entity.getTags();
        String[] nextTags = normalizedTags.toArray(String[]::new);

        StudyPackEntity targetEntity = entity;
        if (!Arrays.equals(currentTags, nextTags)) {
            entity.setTags(nextTags);
            entity.setUpdatedAt(OffsetDateTime.now());
            targetEntity = studyPackRepository.save(entity);
        }

        return mapToResponse(targetEntity, null, null);
    }

    public StudyPackResponse updateMetadata(String id, UUID ownerUserId, String title, String subject) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity entity = studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        String normalizedTitle = normalizeEditableTitle(title);
        String normalizedSubject = normalizeSubject(subject);

        StudyPackEntity targetEntity = entity;
        if (!Objects.equals(entity.getTitle(), normalizedTitle) || !Objects.equals(entity.getSubject(), normalizedSubject)) {
            entity.setTitle(normalizedTitle);
            entity.setSubject(normalizedSubject);
            entity.setUpdatedAt(OffsetDateTime.now());
            targetEntity = studyPackRepository.save(entity);
        }

        return mapToResponse(targetEntity, null, null);
    }

    public void recordQuickReviewActivity(String id, UUID ownerUserId, ActivityType activityType) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, ownerUserId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

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

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new AppException("INVALID_IMAGE", "Please upload an image to continue.", HttpStatus.BAD_REQUEST);
        }

        if (image.getSize() > properties.getSettings().getMaxImageBytes()) {
            throw new AppException(
                    "IMAGE_TOO_LARGE",
                    "Image is too large. Please upload an image under 5MB.",
                    HttpStatus.BAD_REQUEST
            );
        }

        String contentType = image.getContentType() == null ? "" : image.getContentType().toLowerCase();
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new AppException(
                    "UNSUPPORTED_IMAGE_TYPE",
                    "Unsupported image type. Please use JPG, PNG, or WEBP.",
                    HttpStatus.BAD_REQUEST
            );
        }
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
            PlanType planType
    ) {
        StudyPackEntity entity = new StudyPackEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
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
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setTags(resolveTags(generated.tags(), generated.title()));
        StudyPackEntity savedEntity = studyPackRepository.save(entity);
        activityTrackingService.recordActivity(ownerUserId, ActivityType.CREATED_STUDY_PACK, savedEntity.getId());
        return savedEntity;
    }

    private StudyPackResponse mapToResponse(StudyPackEntity entity, String extractedText, Long latencyMs) {
        return new StudyPackResponse(
                entity.getId().toString(),
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
            if (rawTag == null) {
                continue;
            }

            String normalizedTag = rawTag.trim();
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
        OffsetDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime nextMonthStart = monthStart.plusMonths(1);

        long usedThisMonth = studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                ownerUserId,
                monthStart,
                nextMonthStart
        );

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
        return planType == PlanType.PREMIUM
                ? properties.getPricing().getPremiumMonthlyStudyPackLimit()
                : properties.getPricing().getFreeMonthlyStudyPackLimit();
    }

    private String resolveQuotaReachedMessage(PlanType planType, int limit) {
        if (planType == PlanType.PREMIUM) {
            return "You have reached your monthly Study Pack limit (" + limit + "). Please try again next month.";
        }
        return "You have reached your monthly Free plan limit (" + limit + " Study Packs). Upgrade to Premium for more monthly Study Packs.";
    }
}

