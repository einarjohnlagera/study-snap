package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.OcrRateLimitService;
import com.studysnap.backend.service.model.OcrResult;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NoteTextExtractionService {
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_MIME = "application/pdf";
    private static final String TEXT_MIME = "text/plain";
    private static final long MAX_TXT_BYTES = (long) 1024 * 1024;
    private static final long MAX_DOCX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PDF_BYTES = 10L * 1024 * 1024;
    private static final int MAX_PDF_PAGES = 30;
    private static final int MAX_EXTRACTED_CHARACTERS = 200_000;

    private final AuthService authService;
    private final OcrService ocrService;
    private final OcrRateLimitService ocrRateLimitService;
    private final SubscriptionService subscriptionService;
    private final StudySnapProperties properties;

    public ExtractedNoteTextResponse extractText(MultipartFile file, UUID ownerUserId) {
        ImportedFileType type = resolveImportedFileType(file);
        return switch (type) {
            case IMAGE -> extractFromImage(file, ownerUserId);
            case TXT -> extractFromTxt(file);
            case PDF -> extractFromPdf(file);
            case DOCX -> extractFromDocx(file);
        };
    }

    private ExtractedNoteTextResponse extractFromImage(MultipartFile file, UUID ownerUserId) {
        authService.requireEmailVerified(ownerUserId);
        PlanType planType = subscriptionService.resolvePlan(ownerUserId);
        validateImage(file, planType);
        ocrRateLimitService.assertAllowed(ownerUserId, planType);

        OcrResult result = ocrService.extractText(file);
        String extractedText = normalizeText(result.extractedText());
        if (extractedText.isBlank()) {
            throw new AppException(
                    "OCR_NO_TEXT",
                    "No readable text was detected. Retake the photo with better lighting and focus, then try again.",
                    HttpStatus.BAD_REQUEST
            );
        }

        return new ExtractedNoteTextResponse(
                "image",
                extractedText,
                new ExtractedNoteTextResponse.ExtractionMeta(
                        result.confidence(),
                        result.confidence() < properties.getOcr().getConfidenceThreshold()
                )
        );
    }

    private ExtractedNoteTextResponse extractFromTxt(MultipartFile file) {
        assertFilePresent(file, "Please upload a TXT file to continue.");
        assertFileSize(file, MAX_TXT_BYTES, "This file is too large. Upload a TXT file under 1 MB.");
        try {
            String extractedText = normalizeText(new String(file.getBytes(), StandardCharsets.UTF_8));
            if (extractedText.isBlank()) {
                throw new AppException(
                        "EMPTY_IMPORTED_TEXT",
                        "This file is empty or has no readable text.",
                        HttpStatus.BAD_REQUEST
                );
            }
            return new ExtractedNoteTextResponse(
                    "txt",
                    extractedText,
                    new ExtractedNoteTextResponse.ExtractionMeta(null, false)
            );
        } catch (IOException exception) {
            throw new AppException("NOTE_IMPORT_FAILED", "Could not read this file.", HttpStatus.BAD_REQUEST);
        }
    }

    private ExtractedNoteTextResponse extractFromPdf(MultipartFile file) {
        assertFilePresent(file, "Please upload a PDF file to continue.");
        assertFileSize(file, MAX_PDF_BYTES, "This file is too large. Upload a PDF file under 10 MB.");
        try (PDDocument document = PDDocument.load(file.getBytes())) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new AppException(
                        "PDF_TOO_LONG",
                        "This PDF is too long to import at once. Try a PDF with 30 pages or fewer.",
                        HttpStatus.BAD_REQUEST
                );
            }

            PDFTextStripper stripper = new PDFTextStripper();
            String extractedText = normalizeText(stripper.getText(document));
            if (extractedText.isBlank()) {
                throw new AppException(
                        "PDF_NO_TEXT",
                        "This PDF appears to be scanned or image-based. Please upload images for OCR instead.",
                        HttpStatus.BAD_REQUEST
                );
            }
            assertExtractedTextLength(extractedText, "This PDF contains too much text to import at once. Try a shorter PDF.");

            return new ExtractedNoteTextResponse(
                    "pdf",
                    extractedText,
                    new ExtractedNoteTextResponse.ExtractionMeta(null, false)
            );
        } catch (IOException exception) {
            throw new AppException("NOTE_IMPORT_FAILED", "Could not read this PDF file.", HttpStatus.BAD_REQUEST);
        }
    }

    private ExtractedNoteTextResponse extractFromDocx(MultipartFile file) {
        assertFilePresent(file, "Please upload a DOCX file to continue.");
        assertFileSize(file, MAX_DOCX_BYTES, "This file is too large. Upload a DOCX file under 10 MB.");
        try (
                ByteArrayInputStream inputStream = new ByteArrayInputStream(file.getBytes());
                XWPFDocument document = new XWPFDocument(inputStream)
        ) {
            String extractedText = normalizeText(
                    document.getParagraphs().stream()
                            .map(XWPFParagraph::getText)
                            .filter(text -> text != null && !text.isBlank())
                            .reduce((left, right) -> left + "\n\n" + right)
                            .orElse("")
            );
            if (extractedText.isBlank()) {
                throw new AppException(
                        "DOCX_NO_TEXT",
                        "This DOCX file is empty or has no readable text.",
                        HttpStatus.BAD_REQUEST
                );
            }
            assertExtractedTextLength(extractedText, "This document is too large to import at once. Try a shorter DOCX file.");

            return new ExtractedNoteTextResponse(
                    "docx",
                    extractedText,
                    new ExtractedNoteTextResponse.ExtractionMeta(null, false)
            );
        } catch (IOException exception) {
            throw new AppException("NOTE_IMPORT_FAILED", "Could not read this DOCX file.", HttpStatus.BAD_REQUEST);
        }
    }

    private ImportedFileType resolveImportedFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("MISSING_IMPORT_FILE", "Please upload an image or file to continue.", HttpStatus.BAD_REQUEST);
        }

        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);

        if (ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return ImportedFileType.IMAGE;
        }
        if (fileName.endsWith(".txt") || TEXT_MIME.equals(contentType)) {
            return ImportedFileType.TXT;
        }
        if (fileName.endsWith(".pdf") || PDF_MIME.equals(contentType)) {
            return ImportedFileType.PDF;
        }
        if (fileName.endsWith(".docx") || DOCX_MIME.equals(contentType)) {
            return ImportedFileType.DOCX;
        }

        throw new AppException(
                "UNSUPPORTED_IMPORT_FILE_TYPE",
                "Unsupported file type. Upload PNG, JPG, JPEG, WEBP, TXT, PDF, or DOCX.",
                HttpStatus.BAD_REQUEST
        );
    }

    private void validateImage(MultipartFile image, PlanType planType) {
        assertFilePresent(image, "Please upload an image to continue.");
        if (properties.getOcr().getMaxPagesPerUpload() < 1) {
            throw new AppException(
                    "OCR_CONFIGURATION_ERROR",
                    "OCR upload is temporarily unavailable. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        long maxImageBytes = Math.max(
                1,
                planType == PlanType.PREMIUM
                        ? properties.getOcr().getPremiumMaxImageBytes()
                        : properties.getOcr().getFreeMaxImageBytes()
        );
        if (image.getSize() > maxImageBytes) {
            long maxSizeMb = Math.max(1, maxImageBytes / (1024 * 1024));
            throw new AppException(
                    "IMAGE_TOO_LARGE",
                    "Image is too large. Please upload an image under " + maxSizeMb + " MB.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void assertFilePresent(MultipartFile file, String message) {
        if (file == null || file.isEmpty()) {
            throw new AppException("MISSING_IMPORT_FILE", message, HttpStatus.BAD_REQUEST);
        }
    }

    private void assertFileSize(MultipartFile file, long maxBytes, String message) {
        if (file.getSize() > maxBytes) {
            throw new AppException("IMPORT_FILE_TOO_LARGE", message, HttpStatus.BAD_REQUEST);
        }
    }

    private void assertExtractedTextLength(String extractedText, String message) {
        if (extractedText.length() > MAX_EXTRACTED_CHARACTERS) {
            throw new AppException("IMPORTED_TEXT_TOO_LARGE", message, HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeText(String value) {
        return value == null
                ? ""
                : value
                .replace("\r\n", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private enum ImportedFileType {
        IMAGE,
        TXT,
        PDF,
        DOCX
    }
}
