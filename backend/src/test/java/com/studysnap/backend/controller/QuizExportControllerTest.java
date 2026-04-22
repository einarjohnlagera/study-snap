package com.studysnap.backend.controller;

import com.studysnap.backend.dto.MultiNoteQuizDocxExportRequest;
import com.studysnap.backend.dto.QuizDocxExportMode;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.GeneratedQuizService;
import com.studysnap.backend.service.QuizDocxExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizExportControllerTest {

    @Mock
    private GeneratedQuizService generatedQuizService;

    private QuizExportController quizExportController;

    @BeforeEach
    void setUp() {
        quizExportController = new QuizExportController(generatedQuizService);
    }

    @Test
    void exportGeneratedQuizDocx_returnsDocxAttachmentResponse() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        byte[] content = "docx".getBytes();
        when(generatedQuizService.exportDocx("quiz-1", userId, QuizDocxExportMode.WITH_ANSWERS))
                .thenReturn(new QuizDocxExportService.QuizDocxFile("teacher-note-quiz-with-answers.docx", content));

        var response = quizExportController.exportGeneratedQuizDocx("quiz-1", QuizDocxExportMode.WITH_ANSWERS, user);

        verify(generatedQuizService).exportDocx("quiz-1", userId, QuizDocxExportMode.WITH_ANSWERS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment;");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("teacher-note-quiz-with-answers.docx");
        assertThat(response.getBody()).isEqualTo(content);
    }

    @Test
    void exportCombinedGeneratedQuizDocx_returnsDocxAttachmentResponse() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        byte[] content = "combined-docx".getBytes();
        MultiNoteQuizDocxExportRequest request = new MultiNoteQuizDocxExportRequest(
                java.util.List.of(
                        new MultiNoteQuizDocxExportRequest.Section(
                                "Section A",
                                java.util.List.of(
                                        new MultiNoteQuizDocxExportRequest.QuestionRef("note-1", 0),
                                        new MultiNoteQuizDocxExportRequest.QuestionRef("note-2", 1)
                                )
                        )
                ),
                true,
                true
        );
        when(generatedQuizService.exportCombinedDocx(request.sections(), userId, true, true))
                .thenReturn(new QuizDocxExportService.QuizDocxFile("combined-exam-with-answers.docx", content));

        var response = quizExportController.exportCombinedGeneratedQuizDocx(request, user);

        verify(generatedQuizService).exportCombinedDocx(request.sections(), userId, true, true);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("combined-exam-with-answers.docx");
        assertThat(response.getBody()).isEqualTo(content);
    }
}
