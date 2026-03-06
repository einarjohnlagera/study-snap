package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.CreateReviewRequest;
import com.studysnap.backend.dto.NeedsTextConfirmationResponse;
import com.studysnap.backend.dto.ReviewMeta;
import com.studysnap.backend.dto.ReviewResponse;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.ReviewDraftEntity;
import com.studysnap.backend.entity.ReviewEntity;
import com.studysnap.backend.entity.ReviewStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.ReviewDraftRepository;
import com.studysnap.backend.repository.ReviewRepository;
import com.studysnap.backend.service.model.GeneratedReviewContent;
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
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {
	private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
	private static final List<String> ALLOWED_IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");

	private final ReviewRepository reviewRepository;
	private final ReviewDraftRepository reviewDraftRepository;
	private final OcrService ocrService;
	private final LlmReviewService llmReviewService;
	private final StudySnapProperties properties;

	public ReviewResponse createFromText(CreateReviewRequest request) {
		long startedAt = System.currentTimeMillis();
		String requestId = UUID.randomUUID().toString();
		String normalizedText = normalizeAndValidateText(request.notesText());

		GeneratedReviewContent generated = llmReviewService.generateReview(normalizedText);
		ReviewEntity saved = saveReview(InputType.TEXT, null, generated);
		long latency = System.currentTimeMillis() - startedAt;

		log.info("requestId={} action=create_review inputType=text latencyMs={}", requestId, latency);
		return mapToResponse(saved, null, latency);
	}

	public Object createFromImage(MultipartFile image, String subject) {
		long startedAt = System.currentTimeMillis();
		String requestId = UUID.randomUUID().toString();
		validateImage(image);

		OcrResult ocrResult = ocrService.extractText(image);
		String extractedText = mergeSubject(ocrResult.extractedText(), subject);

		if (ocrResult.confidence() < properties.getOcr().getConfidenceThreshold()) {
			ReviewDraftEntity draft = new ReviewDraftEntity();
			draft.setId(UUID.randomUUID());
			draft.setExtractedText(extractedText);
			draft.setOcrConfidence(ocrResult.confidence());
			draft.setCreatedAt(OffsetDateTime.now());
			draft.setExpiresAt(OffsetDateTime.now().plusHours(24));
			reviewDraftRepository.save(draft);

			long latency = System.currentTimeMillis() - startedAt;
			log.info(
					"requestId={} action=create_review inputType=image outcome=needs_text_confirmation latencyMs={}",
					requestId,
					latency
			);
			return toNeedsConfirmation(draft.getId().toString(), extractedText, ocrResult.confidence());
		}

		String normalizedText = normalizeAndValidateText(extractedText);
		GeneratedReviewContent generated = llmReviewService.generateReview(normalizedText);
		ReviewEntity saved = saveReview(InputType.IMAGE, ocrResult.confidence(), generated);
		long latency = System.currentTimeMillis() - startedAt;

		log.info("requestId={} action=create_review inputType=image latencyMs={}", requestId, latency);
		return mapToResponse(saved, extractedText, latency);
	}

	public ReviewResponse confirmExtractedText(ConfirmTextRequest request) {
		long startedAt = System.currentTimeMillis();
		String requestId = UUID.randomUUID().toString();

		UUID draftId = parseUuid(request.draftId(), "DRAFT_NOT_FOUND", "Draft not found.");
		ReviewDraftEntity draft = reviewDraftRepository.findById(draftId)
				.orElseThrow(() -> new AppException("DRAFT_NOT_FOUND", "Draft not found.", HttpStatus.NOT_FOUND));

		if (draft.getExpiresAt().isBefore(OffsetDateTime.now())) {
			reviewDraftRepository.delete(draft);
			throw new AppException(
					"DRAFT_EXPIRED",
					"This text confirmation has expired. Please upload the image again.",
					HttpStatus.BAD_REQUEST
			);
		}

		String normalizedText = normalizeAndValidateText(request.notesText());
		GeneratedReviewContent generated = llmReviewService.generateReview(normalizedText);
		ReviewEntity saved = saveReview(InputType.IMAGE, draft.getOcrConfidence(), generated);
		reviewDraftRepository.delete(draft);
		long latency = System.currentTimeMillis() - startedAt;

		log.info("requestId={} action=confirm_text latencyMs={}", requestId, latency);
		return mapToResponse(saved, normalizedText, latency);
	}

	@Transactional(readOnly = true)
	public ReviewResponse getById(String id) {
		UUID reviewId = parseUuid(id, "REVIEW_NOT_FOUND", "Review not found.");
		ReviewEntity review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new AppException("REVIEW_NOT_FOUND", "Review not found.", HttpStatus.NOT_FOUND));
		return mapToResponse(review, null, null);
	}

	public NeedsTextConfirmationResponse toNeedsConfirmation(String draftId, String extractedText, double confidence) {
		return new NeedsTextConfirmationResponse(
				"needs_text_confirmation",
				draftId,
				extractedText,
				new ReviewMeta(confidence, null)
		);
	}

	private void validateImage(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			throw new AppException("INVALID_IMAGE", "Please upload an image to continue.", HttpStatus.BAD_REQUEST);
		}

		if (image.getSize() > properties.getLimits().getMaxImageBytes()) {
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
					"Please provide notes text before generating a review.",
					HttpStatus.BAD_REQUEST
			);
		}
		if (normalized.length() > properties.getLimits().getMaxNotesChars()) {
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

	private ReviewEntity saveReview(InputType inputType, Double ocrConfidence, GeneratedReviewContent generated) {
		ReviewEntity entity = new ReviewEntity();
		entity.setId(UUID.randomUUID());
		entity.setInputType(inputType);
		entity.setTitle(generated.title());
		entity.setSummary(generated.summary());
		entity.setKeyConcepts(generated.keyConcepts());
		entity.setQuiz(generated.quiz());
		entity.setOcrConfidence(ocrConfidence);
		entity.setModelTier(ModelTier.FREE);
		entity.setModelUsed(properties.getLlm().getModelFree());
		entity.setStatus(ReviewStatus.DONE);
		entity.setCreatedAt(OffsetDateTime.now());
		return reviewRepository.save(entity);
	}

	private ReviewResponse mapToResponse(ReviewEntity entity, String extractedText, Long latencyMs) {
		return new ReviewResponse(
				entity.getId().toString(),
				entity.getInputType().name().toLowerCase(),
				extractedText,
				entity.getTitle(),
				entity.getSummary(),
				entity.getKeyConcepts(),
				entity.getQuiz(),
				new ReviewMeta(entity.getOcrConfidence(), latencyMs)
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
