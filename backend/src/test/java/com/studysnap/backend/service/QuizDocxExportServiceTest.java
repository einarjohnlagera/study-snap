package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizDocxExportMode;
import com.studysnap.backend.dto.QuizItem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class QuizDocxExportServiceTest {
    private final QuizDocxExportService quizDocxExportService = new QuizDocxExportService();

    @Test
    void exportQuizOnly_omitsAnswerKeyAndExplanations() throws IOException {
        byte[] content = quizDocxExportService.exportQuizToDocx(buildQuiz(List.of(
                new QuizItem("What is the nucleus?", List.of("Control center", "Energy source", "Cell wall", "Waste"), 0, "Cells", "The nucleus controls the cell.")
        )), QuizDocxExportMode.QUIZ_ONLY);

        String text = extractText(content);

        assertThat(text).contains("Biology Quiz")
            .contains("Name: __________________________")
            .contains("1. What is the nucleus?")
            .contains("A. Control center")
            .doesNotContain("Answer Key")
            .doesNotContain("Explanations");
    }

    @Test
    void exportWithAnswers_includesAnswerKeyAndExplanations() throws IOException {
        byte[] content = quizDocxExportService.exportQuizToDocx(buildQuiz(List.of(
                new QuizItem("Which organelle produces ATP?", List.of("Mitochondria", "Nucleus", "Ribosome", "Golgi body"), 0, "Cells", "Mitochondria produce most cellular ATP.")
        )), QuizDocxExportMode.WITH_ANSWERS);

        String text = extractText(content);

        assertThat(text).contains("Answer Key")
            .contains("1. A")
            .contains("Explanations")
            .contains("1. Mitochondria produce most cellular ATP.");
    }

    @Test
    void exportLargeQuizAndSpecialCharacters_remainsReadable() throws IOException {
        List<QuizItem> questions = IntStream.rangeClosed(1, 15)
                .mapToObj(index -> new QuizItem(
                        "Question " + index + " – café ßeta?",
                        List.of("Álpha", "Béta", "Gammá", "Deltá"),
                        index % 4,
                        "Topic " + index,
                        "Explanation " + index + " covers café, naïve, and résumé."
                ))
                .toList();

        byte[] content = quizDocxExportService.exportQuizToDocx(buildQuiz(questions), QuizDocxExportMode.WITH_ANSWERS);
        String text = extractText(content);

        assertThat(text).contains("15. Question 15 – café ßeta?")
            .contains("Deltá")
            .contains("Explanation 15 covers café, naïve, and résumé.");
    }

    @Test
    void buildFilename_usesExpectedSuffixByMode() {
        assertThat(quizDocxExportService.buildFilename("Teacher Note", QuizDocxExportMode.QUIZ_ONLY))
                .isEqualTo("teacher-note-quiz.docx");
        assertThat(quizDocxExportService.buildFilename("Teacher Note", QuizDocxExportMode.WITH_ANSWERS))
                .isEqualTo("teacher-note-quiz-with-answers.docx");
    }

    private QuizDocxExportService.ExportableQuiz buildQuiz(List<QuizItem> questions) {
        return new QuizDocxExportService.ExportableQuiz(
                "Biology Quiz",
                "Biology",
                OffsetDateTime.parse("2026-04-20T10:00:00Z"),
                questions
        );
    }

    private String extractText(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }
}
