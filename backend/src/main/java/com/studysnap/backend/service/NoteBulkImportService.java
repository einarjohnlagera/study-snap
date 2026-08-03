package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkImportResultResponse;
import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.InvalidBulkImportRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NoteBulkImportService {
    private static final int TITLE_MAX_LENGTH = 150;
    private static final int MIN_MAX_BATCH_FILES = 1;
    private static final String EMPTY_FILES_MESSAGE = "Please upload at least one file.";
    private static final String MAX_FILES_MESSAGE_TEMPLATE = "You can import up to %d files at once.";
    private static final String DEFAULT_FILE_NAME = "untitled";
    private static final String DEFAULT_TITLE = "Imported note";
    private static final String NO_READABLE_TEXT_CODE = "NO_READABLE_TEXT";
    private static final String NO_READABLE_TEXT_MESSAGE = "No readable text was found in this file.";
    private static final String DEFAULT_FILE_FAILURE_CODE = "BULK_IMPORT_FILE_FAILED";
    private static final String DEFAULT_FILE_FAILURE_MESSAGE = "Could not import this file.";
    private static final String CREATED_COUNT_KEY = "createdCount";
    private static final String FAILED_COUNT_KEY = "failedCount";
    private static final String EXTENSION_SEPARATOR = ".";
    private static final String PATH_SEPARATOR = "/";
    private static final String WINDOWS_PATH_SEPARATOR = "\\";
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final AuthService authService;
    private final NoteTextExtractionService noteTextExtractionService;
    private final NoteService noteService;
    private final AnalyticsService analyticsService;
    private final OnboardingGuardService onboardingGuardService;
    private final int maxBatchFiles;

    public NoteBulkImportService(
            AuthService authService,
            NoteTextExtractionService noteTextExtractionService,
            NoteService noteService,
            AnalyticsService analyticsService,
            OnboardingGuardService onboardingGuardService,
            @Value("${note.import.max-batch-files:20}") int maxBatchFiles
    ) {
        this.authService = authService;
        this.noteTextExtractionService = noteTextExtractionService;
        this.noteService = noteService;
        this.analyticsService = analyticsService;
        this.onboardingGuardService = onboardingGuardService;
        this.maxBatchFiles = Math.clamp(maxBatchFiles, MIN_MAX_BATCH_FILES, Integer.MAX_VALUE);
    }

    public BulkImportResultResponse importBatch(UUID userId, List<MultipartFile> files) {
        authService.requireEmailVerified(userId);
        onboardingGuardService.assertProfileComplete(userId);
        validateFiles(files);

        List<BulkImportResultResponse.ImportedNoteResult> created = new ArrayList<>();
        List<BulkImportResultResponse.FailedImportResult> failed = new ArrayList<>();

        for (MultipartFile file : files) {
            String fileName = resolveFileName(file);
            try {
                importOneFile(userId, file, fileName, created, failed);
            } catch (AppException ex) {
                failed.add(new BulkImportResultResponse.FailedImportResult(fileName, ex.getCode(), ex.getMessage()));
            } catch (RuntimeException ex) {
                failed.add(new BulkImportResultResponse.FailedImportResult(
                        fileName,
                        DEFAULT_FILE_FAILURE_CODE,
                        DEFAULT_FILE_FAILURE_MESSAGE
                ));
            }
        }

        trackBulkImportIfAnyCreated(userId, created.size(), failed.size());
        return new BulkImportResultResponse(List.copyOf(created), List.copyOf(failed));
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.stream().allMatch(this::isMissingOrEmpty)) {
            throw new InvalidBulkImportRequestException(EMPTY_FILES_MESSAGE);
        }
        if (files.size() > maxBatchFiles) {
            throw new InvalidBulkImportRequestException(MAX_FILES_MESSAGE_TEMPLATE.formatted(maxBatchFiles));
        }
    }

    private void importOneFile(
            UUID userId,
            MultipartFile file,
            String fileName,
            List<BulkImportResultResponse.ImportedNoteResult> created,
            List<BulkImportResultResponse.FailedImportResult> failed
    ) {
        ExtractedNoteTextResponse extracted = noteTextExtractionService.extractText(file, userId);
        String extractedText = extracted.extractedText();
        if (extractedText == null || extractedText.isBlank()) {
            failed.add(new BulkImportResultResponse.FailedImportResult(
                    fileName,
                    NO_READABLE_TEXT_CODE,
                    NO_READABLE_TEXT_MESSAGE
            ));
            return;
        }

        String title = deriveTitle(fileName);
        NoteResponse note = noteService.create(
                new UpsertNoteRequest(title, null, null, null, null, List.of(), null, extractedText),
                userId
        );
        created.add(new BulkImportResultResponse.ImportedNoteResult(
                UUID.fromString(note.id()),
                note.title(),
                fileName,
                extracted.meta() != null && extracted.meta().lowConfidence()
        ));
    }

    private void trackBulkImportIfAnyCreated(UUID userId, int createdCount, int failedCount) {
        if (createdCount == 0) {
            return;
        }
        analyticsService.trackEvent(
                userId,
                AnalyticsEventType.NOTES_BULK_IMPORTED,
                null,
                Map.of(CREATED_COUNT_KEY, createdCount, FAILED_COUNT_KEY, failedCount)
        );
    }

    private boolean isMissingOrEmpty(MultipartFile file) {
        return file == null || file.isEmpty();
    }

    private String resolveFileName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            return DEFAULT_FILE_NAME;
        }
        return file.getOriginalFilename().trim();
    }

    private String deriveTitle(String fileName) {
        String baseName = stripPath(fileName);
        int extensionIndex = baseName.lastIndexOf(EXTENSION_SEPARATOR);
        if (extensionIndex > 0) {
            baseName = baseName.substring(0, extensionIndex);
        }

        String normalized = WHITESPACE_PATTERN.matcher(baseName).replaceAll(" ").trim();
        if (normalized.isBlank()) {
            normalized = DEFAULT_TITLE;
        }
        if (normalized.length() > TITLE_MAX_LENGTH) {
            return normalized.substring(0, TITLE_MAX_LENGTH).trim();
        }
        return normalized;
    }

    private String stripPath(String fileName) {
        int unixSeparator = fileName.lastIndexOf(PATH_SEPARATOR);
        int windowsSeparator = fileName.lastIndexOf(WINDOWS_PATH_SEPARATOR);
        int separator = Math.max(unixSeparator, windowsSeparator);
        if (separator >= 0 && separator + 1 < fileName.length()) {
            return fileName.substring(separator + 1);
        }
        return fileName;
    }
}
