package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
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

    private NoteTextExtractionService noteTextExtractionService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getOcr().setConfidenceThreshold(0.8);
        properties.getOcr().setFreeMaxImageBytes(5_000_000);
        properties.getOcr().setPremiumMaxImageBytes(10_000_000);
        properties.getOcr().setMaxPagesPerUpload(1);

        noteTextExtractionService = new NoteTextExtractionService(
                authService,
                ocrService,
                ocrRateLimitService,
                subscriptionService,
                properties
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
        verify(ocrRateLimitService).assertAllowed(userId, PlanType.FREE);
    }

    @Test
    void rejectsScannedPdfWithoutExtractableText() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "scan.pdf",
                    "application/pdf",
                    outputStream.toByteArray()
            );

            AppException error = assertThrows(AppException.class, () -> noteTextExtractionService.extractText(file, userId));

            assertEquals("PDF_NO_TEXT", error.getCode());
            assertEquals("This PDF appears to be scanned or image-based. Please upload images for OCR instead.", error.getMessage());
        }
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
