package com.studysnap.backend.service.impl;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Block;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.Page;
import com.google.cloud.vision.v1.TextAnnotation;
import com.google.protobuf.ByteString;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.OcrService;
import com.studysnap.backend.service.model.OcrResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OCR implementation backed by Google Cloud Vision.
 *
 * <p>Flow:
 * <ol>
 *   <li>Run quick text detection ({@code TEXT_DETECTION}) as a low-cost guard.</li>
 *   <li>If sufficient text is found, run full OCR ({@code DOCUMENT_TEXT_DETECTION}).</li>
 *   <li>Normalize extracted text for downstream LLM efficiency.</li>
 *   <li>Reject images that are unreadable, empty, or likely non-study content.</li>
 *   <li>Return normalized text plus an OCR confidence score.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "studysnap.ocr", name = "provider", havingValue = "google-vision")
public class GoogleVisionOcrService implements OcrService {
    private static final Logger log = LoggerFactory.getLogger(GoogleVisionOcrService.class);

    private final StudySnapProperties properties;

    /**
     * Extracts text from an uploaded image using hybrid OCR.
     *
     * <p>This method first performs a quick text-presence gate, then full OCR.
     * It returns normalized text and confidence, or throws a user-friendly
     * {@link AppException} when input quality/provider state is not acceptable.
     */
    @Override
    public OcrResult extractText(MultipartFile image) {
        byte[] imageBytes = readImageBytes(image);

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            String quickDetectedText = runQuickDetection(client, imageBytes);
            validateQuickDetection(quickDetectedText);

            AnnotateImageResponse fullResponse = runFullOcr(client, imageBytes);
            TextAnnotation fullText = fullResponse.getFullTextAnnotation();
            String extractedText = normalizeExtractedText(fullText.getText());
            validateFullExtraction(extractedText);

            double confidence = computeConfidence(fullText);
            if (confidence < properties.getOcr().getHardRejectConfidence()) {
                throw new AppException(
                        "IMAGE_TEXT_UNREADABLE",
                        "The photo is too blurry to read. Please upload a clearer image of your notes.",
                        HttpStatus.BAD_REQUEST
                );
            }

            return new OcrResult(extractedText, confidence);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn(
                    "ocr_provider_failed provider=google-vision errorCode={} message={}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            throw new AppException(
                    "OCR_UNAVAILABLE",
                    "We could not read text from this image right now. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    /**
     * Reads uploaded image bytes from the multipart payload.
     */
    private byte[] readImageBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException ex) {
            throw new AppException(
                    "IMAGE_READ_FAILED",
                    "We could not process this image. Please try another upload.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Runs low-cost text detection to decide whether full OCR is worth doing.
     */
    private String runQuickDetection(ImageAnnotatorClient client, byte[] imageBytes) {
        AnnotateImageResponse response = annotate(
                client,
                imageBytes,
                Feature.Type.TEXT_DETECTION
        );

        String detected = response.getFullTextAnnotation().getText();
        return normalizeExtractedText(detected);
    }

    /**
     * Runs full document OCR once quick detection has passed.
     */
    private AnnotateImageResponse runFullOcr(ImageAnnotatorClient client, byte[] imageBytes) {
        return annotate(client, imageBytes, Feature.Type.DOCUMENT_TEXT_DETECTION);
    }

    /**
     * Calls Google Vision for the specified feature type and validates provider response shape.
     */
    private AnnotateImageResponse annotate(ImageAnnotatorClient client, byte[] imageBytes, Feature.Type type) {
        Image visionImage = Image.newBuilder().setContent(ByteString.copyFrom(imageBytes)).build();
        Feature feature = Feature.newBuilder().setType(type).build();
        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .setImage(visionImage)
                .addFeatures(feature)
                .build();

        BatchAnnotateImagesResponse batch = client.batchAnnotateImages(List.of(request));
        if (batch.getResponsesCount() == 0) {
            throw new AppException(
                    "OCR_EMPTY_RESPONSE",
                    "We could not detect text from this image. Please upload clearer notes.",
                    HttpStatus.BAD_REQUEST
            );
        }

        AnnotateImageResponse response = batch.getResponses(0);
        if (response.hasError()) {
            throw new AppException(
                    "OCR_PROVIDER_ERROR",
                    "We could not read this image right now. Please try again.",
                    response.getError().getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
        return response;
    }

    /**
     * Rejects images that fail the minimum quick-detection text threshold.
     */
    private void validateQuickDetection(String detectedText) {
        if (hasInsufficientText(detectedText)) {
            throw new AppException(
                    "IMAGE_TEXT_NOT_DETECTED",
                    "Please upload a clearer photo of your notes with visible text.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Rejects extracted content that is too sparse or obviously non-study content.
     */
    private void validateFullExtraction(String extractedText) {
        if (hasInsufficientText(extractedText)) {
            throw new AppException(
                    "IMAGE_TEXT_INSUFFICIENT",
                    "The image has too little readable text. Please upload a page with clearer notes.",
                    HttpStatus.BAD_REQUEST
            );
        }

        String lower = extractedText.toLowerCase(Locale.ROOT);
        if (looksLikeMemeOrNonStudyImage(lower)) {
            throw new AppException(
                    "IMAGE_NOT_STUDY_NOTES",
                    "This image does not look like study notes. Please upload lecture notes or reviewer text.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Checks minimum text volume using configurable character/word thresholds.
     */
    private boolean hasInsufficientText(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        int charCount = text.replaceAll("\\s+", "").length();
        int wordCount = text.trim().split("\\s+").length;
        return charCount < properties.getOcr().getMinDetectedChars()
                || wordCount < properties.getOcr().getMinDetectedWords();
    }

    /**
     * Lightweight heuristic to block obvious meme/social-caption style uploads.
     */
    private boolean looksLikeMemeOrNonStudyImage(String text) {
        return text.contains("follow for more")
                || text.contains("like and subscribe")
                || text.contains("when you")
                || text.contains("me irl");
    }

    /**
     * Computes average confidence from Vision block-level confidence values.
     *
     * <p>If confidence metadata is unavailable, falls back to configured
     * low-confidence threshold so the upstream flow can still proceed safely.
     */
    private double computeConfidence(TextAnnotation textAnnotation) {
        if (textAnnotation == null || textAnnotation.getPagesCount() == 0) {
            return 0.0;
        }

        List<Float> confidences = new ArrayList<>();
        for (Page page : textAnnotation.getPagesList()) {
            for (Block block : page.getBlocksList()) {
                if (block.getConfidence() > 0f) {
                    confidences.add(block.getConfidence());
                }
            }
        }

        if (confidences.isEmpty()) {
            return properties.getOcr().getConfidenceThreshold();
        }

        double total = 0.0;
        for (Float confidence : confidences) {
            total += confidence;
        }
        return total / confidences.size();
    }

    /**
     * Normalizes OCR text to reduce LLM token noise while preserving paragraph intent.
     *
     * <p>Normalization performed:
     * <ul>
     *   <li>Standardize line breaks</li>
     *   <li>Collapse repeated whitespace within lines</li>
     *   <li>Treat blank lines as paragraph boundaries</li>
     *   <li>Join wrapped lines inside the same paragraph</li>
     * </ul>
     */
    private String normalizeExtractedText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String normalizedBreaks = raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u000C', '\n');

        String[] lines = normalizedBreaks.split("\n");
        List<String> paragraphs = new ArrayList<>();
        StringBuilder currentParagraph = new StringBuilder();

        for (String line : lines) {
            String cleaned = line
                    .replace('\t', ' ')
                    .replaceAll("\\s+", " ")
                    .trim();

            if (cleaned.isBlank()) {
                if (!currentParagraph.isEmpty()) {
                    paragraphs.add(currentParagraph.toString().trim());
                    currentParagraph = new StringBuilder();
                }
                continue;
            }

            if (!currentParagraph.isEmpty()) {
                currentParagraph.append(' ');
            }
            currentParagraph.append(cleaned);
        }

        if (!currentParagraph.isEmpty()) {
            paragraphs.add(currentParagraph.toString().trim());
        }

        return String.join("\n\n", paragraphs).trim();
    }
}
