package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.OcrDisabledException;
import com.studysnap.backend.security.OcrRateLimitService;
import com.studysnap.backend.service.model.OcrResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteTextExtractionServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private OcrService ocrService;

    @Mock
    private OcrRateLimitService ocrRateLimitService;

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private OcrUsageProtectionService ocrUsageProtectionService;

    private NoteTextExtractionService noteTextExtractionService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getOcr().setConfidenceThreshold(0.8);
        properties.getOcr().setFreeMaxImageBytes(5_000_000);
        properties.getOcr().setPremiumMaxImageBytes(10_000_000);
        properties.getOcr().setMaxPagesPerUpload(1);
        properties.getLimits().setTxtUploadMaxSize(1);
        properties.getLimits().setPdfUploadMaxSize(10);
        properties.getLimits().setDocxUploadMaxSize(10);
        properties.getLimits().setFileUploadMaxSize(10);
        properties.getLimits().setPdfMaxPages(30);
        properties.getLimits().setExtractedTextMaxLength(200_000);

        noteTextExtractionService = new NoteTextExtractionService(
                authService,
                ocrService,
                ocrRateLimitService,
                subscriptionService,
                properties,
                ocrUsageProtectionService
        );
        userId = UUID.randomUUID();
    }

    @Test
    void extractsTxtIntoNormalizedContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "  Line one\r\n\r\nLine two  ".getBytes()
        );

        ExtractedNoteTextResponse response = noteTextExtractionService.extractText(file, userId);

        assertEquals("txt", response.inputType());
        assertEquals("Line one\n\nLine two", response.extractedText());
        assertFalse(response.meta().lowConfidence());
        assertNull(response.meta().ocrConfidence());
    }

    @Test
    void extractsDocxIntoReadableText() throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XWPFParagraph first = document.createParagraph();
            first.createRun().setText("Cell membrane");
            XWPFParagraph second = document.createParagraph();
            second.createRun().setText("Nucleus");
            document.write(outputStream);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "notes.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    outputStream.toByteArray()
            );

            ExtractedNoteTextResponse response = noteTextExtractionService.extractText(file, userId);

            assertEquals("docx", response.inputType());
            assertEquals("Cell membrane\n\nNucleus", response.extractedText());
        }
    }

    @Test
    void extractsTextBasedPdfIntoReadableText() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Photosynthesis converts light into chemical energy.");
                contentStream.endText();
            }
            document.save(outputStream);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "notes.pdf",
                    "application/pdf",
                    outputStream.toByteArray()
            );

            ExtractedNoteTextResponse response = noteTextExtractionService.extractText(file, userId);

            assertEquals("pdf", response.inputType());
            assertTrue(response.extractedText().contains("Photosynthesis converts light into chemical energy."));
        }
    }

    @Test
    void fallsBackToOcrForScannedPdfWithoutEmbeddedText() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "scan.pdf",
                    "application/pdf",
                    outputStream.toByteArray()
            );
            when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
            when(ocrService.extractText(org.mockito.ArgumentMatchers.any(MultipartFile.class)))
                    .thenReturn(new OcrResult("Scanned PDF OCR text", 0.88));

            ExtractedNoteTextResponse response = noteTextExtractionService.extractText(file, userId);

            assertEquals("pdf", response.inputType());
            assertEquals("Scanned PDF OCR text", response.extractedText());
        assertEquals(0.88, response.meta().ocrConfidence());
        assertFalse(response.meta().lowConfidence());
        verify(authService).requireEmailVerified(userId);
        verify(ocrUsageProtectionService).assertQuotaAvailable(userId, PlanType.FREE);
        verify(ocrUsageProtectionService).recordUsage(eq(userId), org.mockito.ArgumentMatchers.any());
        verify(ocrRateLimitService).assertAllowed(userId, PlanType.FREE);
    }
    }

    @Test
    void returnsLowConfidenceMetadataForImageOcr() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.png",
                "image/png",
                "fake-image".getBytes()
        );
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(ocrService.extractText(file)).thenReturn(new OcrResult("Low confidence image text", 0.41));

        ExtractedNoteTextResponse response = noteTextExtractionService.extractText(file, userId);

        assertEquals("image", response.inputType());
        assertEquals("Low confidence image text", response.extractedText());
        assertTrue(response.meta().lowConfidence());
        assertEquals(0.41, response.meta().ocrConfidence());
        verify(authService).requireEmailVerified(userId);
        verify(ocrUsageProtectionService).assertQuotaAvailable(userId, PlanType.FREE);
        verify(ocrUsageProtectionService).recordUsage(eq(userId), org.mockito.ArgumentMatchers.any());
        verify(ocrRateLimitService).assertAllowed(userId, PlanType.FREE);
    }

    @Test
    void rejectsScannedPdfWithoutExtractableTextWhenOcrFallbackAlsoFindsNothing() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "scan.pdf",
                    "application/pdf",
                    outputStream.toByteArray()
            );
            when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
            when(ocrService.extractText(org.mockito.ArgumentMatchers.any(MultipartFile.class)))
                    .thenReturn(new OcrResult("", 0.32));

            AppException error = assertThrows(AppException.class, () -> noteTextExtractionService.extractText(file, userId));

            assertEquals("PDF_NO_TEXT", error.getCode());
            assertEquals("This PDF appears to be scanned or image-based. Please upload images for OCR instead.", error.getMessage());
            verify(ocrUsageProtectionService).recordUsage(eq(userId), org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void rejectsImageImportWithTypedErrorWhenOcrIsDisabled() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getOcr().setEnabled(false);
        properties.getOcr().setFreeMaxImageBytes(5_000_000);
        properties.getOcr().setPremiumMaxImageBytes(10_000_000);
        properties.getLimits().setTxtUploadMaxSize(1);
        properties.getLimits().setPdfUploadMaxSize(10);
        properties.getLimits().setDocxUploadMaxSize(10);
        properties.getLimits().setFileUploadMaxSize(10);
        properties.getLimits().setPdfMaxPages(30);
        properties.getLimits().setExtractedTextMaxLength(200_000);
        noteTextExtractionService = new NoteTextExtractionService(
                authService,
                ocrService,
                ocrRateLimitService,
                subscriptionService,
                properties,
                ocrUsageProtectionService
        );
        MockMultipartFile file = new MockMultipartFile("file", "note.png", "image/png", "fake-image".getBytes());

        assertThrows(OcrDisabledException.class, () -> noteTextExtractionService.extractText(file, userId));

        verify(authService, never()).requireEmailVerified(userId);
        verify(ocrUsageProtectionService, never()).assertQuotaAvailable(eq(userId), org.mockito.ArgumentMatchers.any());
        verify(ocrService, never()).extractText(org.mockito.ArgumentMatchers.any(MultipartFile.class));
    }

    @Test
    void rejectsScannedPdfImportWithTypedErrorWhenOcrIsDisabled() throws IOException {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getOcr().setEnabled(false);
        properties.getOcr().setFreeMaxImageBytes(5_000_000);
        properties.getOcr().setPremiumMaxImageBytes(10_000_000);
        properties.getLimits().setTxtUploadMaxSize(1);
        properties.getLimits().setPdfUploadMaxSize(10);
        properties.getLimits().setDocxUploadMaxSize(10);
        properties.getLimits().setFileUploadMaxSize(10);
        properties.getLimits().setPdfMaxPages(30);
        properties.getLimits().setExtractedTextMaxLength(200_000);
        noteTextExtractionService = new NoteTextExtractionService(
                authService,
                ocrService,
                ocrRateLimitService,
                subscriptionService,
                properties,
                ocrUsageProtectionService
        );

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "scan.pdf",
                    "application/pdf",
                    outputStream.toByteArray()
            );

            assertThrows(OcrDisabledException.class, () -> noteTextExtractionService.extractText(file, userId));

            verify(authService, never()).requireEmailVerified(userId);
            verify(ocrService, never()).extractText(org.mockito.ArgumentMatchers.any(MultipartFile.class));
        }
    }

    @Test
    void rejectsPdfImportWhenFileExceedsConfiguredLimit() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getOcr().setConfidenceThreshold(0.8);
        properties.getOcr().setFreeMaxImageBytes(5_000_000);
        properties.getOcr().setPremiumMaxImageBytes(10_000_000);
        properties.getOcr().setMaxPagesPerUpload(1);
        properties.getLimits().setFileUploadMaxSize(10);
        properties.getLimits().setPdfUploadMaxSize(1);
        properties.getLimits().setDocxUploadMaxSize(10);
        properties.getLimits().setTxtUploadMaxSize(1);
        properties.getLimits().setPdfMaxPages(30);
        properties.getLimits().setExtractedTextMaxLength(200_000);
        noteTextExtractionService = new NoteTextExtractionService(
                authService,
                ocrService,
                ocrRateLimitService,
                subscriptionService,
                properties,
                ocrUsageProtectionService
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.pdf",
                "application/pdf",
                new byte[2 * 1024 * 1024]
        );

        AppException error = assertThrows(AppException.class, () -> noteTextExtractionService.extractText(file, userId));

        assertEquals("IMPORT_FILE_TOO_LARGE", error.getCode());
        assertEquals("This file is too large. Upload a smaller PDF file.", error.getMessage());
    }

    @Test
    void rejectsTxtImportWhenExtractedContentExceedsConfiguredLength() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getOcr().setConfidenceThreshold(0.8);
        properties.getOcr().setFreeMaxImageBytes(5_000_000);
        properties.getOcr().setPremiumMaxImageBytes(10_000_000);
        properties.getOcr().setMaxPagesPerUpload(1);
        properties.getLimits().setFileUploadMaxSize(10);
        properties.getLimits().setTxtUploadMaxSize(10);
        properties.getLimits().setPdfUploadMaxSize(10);
        properties.getLimits().setDocxUploadMaxSize(10);
        properties.getLimits().setPdfMaxPages(30);
        properties.getLimits().setExtractedTextMaxLength(10);
        noteTextExtractionService = new NoteTextExtractionService(
                authService,
                ocrService,
                ocrRateLimitService,
                subscriptionService,
                properties,
                ocrUsageProtectionService
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "01234567890".getBytes(StandardCharsets.UTF_8)
        );

        AppException error = assertThrows(AppException.class, () -> noteTextExtractionService.extractText(file, userId));

        assertEquals("IMPORTED_TEXT_TOO_LARGE", error.getCode());
        assertEquals("This file is too large to process. Please upload a smaller file.", error.getMessage());
    }

    @Test
    void rejectsImageImportsWhenOcrQuotaIsExceeded() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.png",
                "image/png",
                "fake-image".getBytes()
        );
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        doThrow(new AppException(
                "OCR_LIMIT_REACHED",
                "You have reached your OCR limit for now. Please try again later or upgrade to Premium.",
                org.springframework.http.HttpStatus.FORBIDDEN
        )).when(ocrUsageProtectionService).assertQuotaAvailable(userId, PlanType.FREE);

        AppException error = assertThrows(AppException.class, () -> noteTextExtractionService.extractText(file, userId));

        assertEquals("OCR_LIMIT_REACHED", error.getCode());
        verify(ocrRateLimitService, never()).assertAllowed(userId, PlanType.FREE);
    }

    @Test
    void rejectsImageImportsForUnverifiedUsers() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.png",
                "image/png",
                "fake-image".getBytes()
        );
        doThrow(new AppException(
                "EMAIL_VERIFICATION_REQUIRED",
                "Verify your email before using OCR upload.",
                org.springframework.http.HttpStatus.FORBIDDEN
        )).when(authService).requireEmailVerified(userId);

        AppException error = assertThrows(AppException.class, () -> noteTextExtractionService.extractText(file, userId));

        assertEquals("EMAIL_VERIFICATION_REQUIRED", error.getCode());
    }
}
