package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.NeedsTextConfirmationResponse;
import com.studysnap.backend.dto.StudyPackMeta;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.StudyPackDraftEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.StudyPackDraftRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.OcrResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class StudyPackService {
    private static final Logger log = LoggerFactory.getLogger(StudyPackService.class);
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final StudyPackRepository studyPackRepository;
    private final StudyPackDraftRepository studyPackDraftRepository;
    private final OcrService ocrService;
    private final LlmStudyPackService llmStudyPackService;
    private final StudySnapProperties properties;

    public StudyPackResponse createFromText(CreateStudyPackRequest request) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        String normalizedText = normalizeAndValidateText(request.notesText());

        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(normalizedText);
        StudyPackEntity saved = saveStudyPack(InputType.TEXT, null, generated);
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=create_studyPack inputType=text latencyMs={}", requestId, latency);
        return mapToResponse(saved, null, latency);
    }

    public Object createFromImage(MultipartFile image, String subject) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        validateImage(image);

        OcrResult ocrResult = ocrService.extractText(image);
        String extractedText = mergeSubject(ocrResult.extractedText(), subject);

        if (ocrResult.confidence() < properties.getOcr().getConfidenceThreshold()) {
            StudyPackDraftEntity draft = new StudyPackDraftEntity();
            draft.setId(UUID.randomUUID());
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
        StudyPackEntity saved = saveStudyPack(InputType.IMAGE, ocrResult.confidence(), generated);
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=create_studyPack inputType=image latencyMs={}", requestId, latency);
        return mapToResponse(saved, extractedText, latency);
    }

    public StudyPackResponse confirmExtractedText(ConfirmTextRequest request) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        UUID draftId = parseUuid(request.draftId(), "DRAFT_NOT_FOUND", "Draft not found.");
        StudyPackDraftEntity draft = studyPackDraftRepository.findById(draftId)
                .orElseThrow(() -> new AppException("DRAFT_NOT_FOUND", "Draft not found.", HttpStatus.NOT_FOUND));

        if (draft.getExpiresAt().isBefore(OffsetDateTime.now())) {
            studyPackDraftRepository.delete(draft);
            throw new AppException(
                    "DRAFT_EXPIRED",
                    "This text confirmation has expired. Please upload the image again.",
                    HttpStatus.BAD_REQUEST
            );
        }

        String normalizedText = normalizeAndValidateText(request.notesText());
        GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(normalizedText);
        StudyPackEntity saved = saveStudyPack(InputType.IMAGE, draft.getOcrConfidence(), generated);
        studyPackDraftRepository.delete(draft);
        long latency = System.currentTimeMillis() - startedAt;

        log.info("requestId={} action=confirm_text latencyMs={}", requestId, latency);
        return mapToResponse(saved, normalizedText, latency);
    }

    @Transactional(readOnly = true)
    public StudyPackResponse getById(String id) {
        UUID studyPackId = parseUuid(id, "STUDY_PACK_NOT_FOUND", "Study pack not found.");
        StudyPackEntity studyPack = studyPackRepository.findById(studyPackId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));
        return mapToResponse(studyPack, null, null);
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

    private StudyPackEntity saveStudyPack(InputType inputType, Double ocrConfidence, GeneratedStudyPackContent generated) {
        StudyPackEntity entity = new StudyPackEntity();
        entity.setId(UUID.randomUUID());
        entity.setInputType(inputType);
        entity.setTitle(generated.title());
        entity.setSummary(generated.summary());
        entity.setKeyConcepts(generated.keyConcepts());
        entity.setQuiz(generated.quiz());
        entity.setOcrConfidence(ocrConfidence);
        entity.setModelTier(ModelTier.FREE);
        entity.setModelUsed(Optional.ofNullable(generated.modelUsed())
                .orElse(properties.getSettings().getModelFree()));
        entity.setInputTokens(generated.inputTokens());
        entity.setOutputTokens(generated.outputTokens());
        entity.setCachedInputTokens(generated.cachedInputTokens());
        entity.setEstimatedCost(generated.estimatedCost());
        entity.setStatus(StudyPackStatus.DONE);
        entity.setCreatedAt(OffsetDateTime.now());
        return studyPackRepository.save(entity);
    }

    private StudyPackResponse mapToResponse(StudyPackEntity entity, String extractedText, Long latencyMs) {
        return new StudyPackResponse(
                entity.getId().toString(),
                entity.getInputType().name().toLowerCase(),
                extractedText,
                entity.getTitle(),
                entity.getSummary(),
                entity.getKeyConcepts(),
                entity.getQuiz(),
                new StudyPackMeta(entity.getOcrConfidence(), latencyMs)
        );
    }

    private UUID parseUuid(String raw, String code, String message) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new AppException(code, message, HttpStatus.NOT_FOUND);
        }
    }
}

