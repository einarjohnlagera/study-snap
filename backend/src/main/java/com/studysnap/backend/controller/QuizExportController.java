package com.studysnap.backend.controller;

import com.studysnap.backend.dto.QuizDocxExportMode;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.GeneratedQuizService;
import com.studysnap.backend.service.QuizDocxExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/quizzes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class QuizExportController {
    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final GeneratedQuizService generatedQuizService;

    @PostMapping("/{quizId}/export-docx")
    public ResponseEntity<byte[]> exportGeneratedQuizDocx(
            @PathVariable String quizId,
            @RequestParam QuizDocxExportMode mode,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        QuizDocxExportService.QuizDocxFile exportedFile = generatedQuizService.exportDocx(quizId, userId, mode);
        return ResponseEntity.ok()
                .contentType(DOCX_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(exportedFile.getFilename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(exportedFile.getContent());
    }
}
