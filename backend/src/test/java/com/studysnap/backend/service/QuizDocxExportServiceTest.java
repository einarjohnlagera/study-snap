package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizDocxExportMode;
import com.studysnap.backend.dto.QuizItem;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
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

        try (XWPFDocument document = openDocument(content)) {
            String text = extractText(document);

            assertThat(document.getTables()).isEmpty();
            assertThat(text).contains("Subject: Biology")
                .contains("Topic: Biology Quiz")
                .contains("Time: ____________")
                .contains("Name: __________________________")
                .contains("Date: __________________________")
                .contains("Score: __________________________")
                .contains("QUIZ")
                .contains("PART I - MULTIPLE CHOICE")
                .contains("Instructions: Read each question carefully. Choose the best answer.")
                .contains("1. What is the nucleus?")
                .contains("A. Control center")
                .doesNotContain("Answer Key")
                .doesNotContain("Explanations")
                .doesNotContain("Correct");

            XWPFParagraph headerParagraph = document.getParagraphs().getFirst();
            assertThat(headerParagraph.getAlignment()).isEqualTo(ParagraphAlignment.LEFT);
            assertThat(headerParagraph.getText()).isEqualTo("Subject: Biology");

            XWPFParagraph titleParagraph = document.getParagraphs().stream()
                .filter(paragraph -> "QUIZ".equals(paragraph.getText()))
                .findFirst()
                .orElseThrow();
            assertThat(titleParagraph.getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(titleParagraph.getSpacingAfter()).isEqualTo(180);

            XWPFParagraph firstChoiceParagraph = document.getParagraphs().stream()
                .filter(paragraph -> "A. Control center".equals(paragraph.getText()))
                .findFirst()
                .orElseThrow();
            assertThat(firstChoiceParagraph.getIndentationLeft()).isEqualTo(400);
            assertThat(firstChoiceParagraph.getSpacingAfter()).isZero();
        }
    }

    @Test
    void exportWithAnswers_includesAnswerKeyAndExplanations() throws IOException {
        byte[] content = quizDocxExportService.exportQuizToDocx(buildQuiz(List.of(
                new QuizItem("Which organelle produces ATP?", List.of("Mitochondria", "Nucleus", "Ribosome", "Golgi body"), 0, "Cells", "Mitochondria produce most cellular ATP.")
        )), QuizDocxExportMode.WITH_ANSWERS);

        try (XWPFDocument document = openDocument(content)) {
            String text = extractText(document);

            assertThat(text).contains("Subject: Biology")
                .contains("Topic: Biology Quiz")
                .contains("PART I - MULTIPLE CHOICE")
                .contains("Answer Key")
                .contains("1. A")
                .contains("Explanations")
                .contains("1. Mitochondria produce most cellular ATP.")
                .doesNotContain("Correct");

            XWPFParagraph answerKeyHeading = document.getParagraphs().stream()
                .filter(paragraph -> "Answer Key".equals(paragraph.getText()))
                .findFirst()
                .orElseThrow();
            assertThat(answerKeyHeading.getSpacingBefore()).isZero();
            assertThat(answerKeyHeading.getSpacingAfter()).isEqualTo(120);

            XWPFParagraph pageBreakParagraph = document.getParagraphs().stream()
                .filter(paragraph -> paragraph.getRuns().stream().anyMatch(this::hasPageBreak))
                .findFirst()
                .orElseThrow();
            assertThat(pageBreakParagraph.getRuns().stream().anyMatch(this::hasPageBreak)).isTrue();
        }
    }

    @Test
    void exportMultipleVersions_withAnswersKeepsEachAnswerSetAfterItsVersion() throws IOException {
        List<QuizItem> questions = IntStream.rangeClosed(1, 6)
                .mapToObj(index -> new QuizItem(
                        "Question " + index,
                        List.of("Correct " + index, "Distractor A " + index, "Distractor B " + index, "Distractor C " + index),
                        0,
                        "Topic " + index,
                        "Explanation " + index
                ))
                .toList();

        byte[] content = quizDocxExportService.exportQuizToDocx(
                buildQuiz(questions),
                QuizDocxExportMode.WITH_ANSWERS,
                QuizDocxExportService.DocxHeaderOptions.empty(),
                2
        );

        try (XWPFDocument document = openDocument(content)) {
            List<String> paragraphs = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .toList();

            assertThat(paragraphs).containsSubsequence(
                    "Version A",
                    "Answer Key",
                    "Explanations",
                    "Version B",
                    "Answer Key",
                    "Explanations"
            );
            assertThat(document.getParagraphs().stream()
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .filter(this::hasPageBreak))
                    .hasSizeGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void exportQuiz_customHeaderIncludesTeacherExportDetails() throws IOException {
        byte[] content = quizDocxExportService.exportQuizToDocx(
                buildQuiz(List.of(
                        new QuizItem("What is the nucleus?", List.of("Control center", "Energy source", "Cell wall", "Waste"), 0, "Cells", "The nucleus controls the cell.")
                )),
                QuizDocxExportMode.QUIZ_ONLY,
                new QuizDocxExportService.DocxHeaderOptions(
                        "NoteLib Academy",
                        "Grade 7 - Rizal",
                        true,
                        Locale.forLanguageTag("en-PH"),
                        LocalDate.of(2026, 5, 21)
                )
        );

        try (XWPFDocument document = openDocument(content)) {
            assertThat(document.getParagraphs().subList(0, 3))
                    .extracting(XWPFParagraph::getText)
                    .containsExactly(
                            "NoteLib Academy",
                            "Biology Quiz — Grade 7 - Rizal",
                            "May 21, 2026"
                    );
            assertThat(document.getParagraphs().get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(document.getParagraphs().get(0).getRuns().getFirst().isBold()).isTrue();
            assertThat(document.getParagraphs().get(1).getRuns().getFirst().isBold()).isTrue();
            assertThat(document.getParagraphs().get(2).getRuns().getFirst().isBold()).isFalse();
        }
    }

    @Test
    void exportQuiz_customHeaderSkipsBlankSchoolAndDisabledDate() throws IOException {
        byte[] content = quizDocxExportService.exportQuizToDocx(
                buildQuiz(List.of(
                        new QuizItem("What is the nucleus?", List.of("Control center", "Energy source", "Cell wall", "Waste"), 0, "Cells", "The nucleus controls the cell.")
                )),
                QuizDocxExportMode.QUIZ_ONLY,
                new QuizDocxExportService.DocxHeaderOptions(" ", null, false, null, null)
        );

        try (XWPFDocument document = openDocument(content)) {
            assertThat(document.getParagraphs().getFirst().getText()).isEqualTo("Subject: Biology");
            assertThat(extractText(document)).doesNotContain("May 21, 2026");
        }
    }

    @Test
    void exportWithAnswers_compactsAnswerKeyIntoRows() throws IOException {
        List<QuizItem> questions = IntStream.rangeClosed(1, 10)
            .mapToObj(index -> new QuizItem(
                "Question " + index,
                List.of("A", "B", "C", "D"),
                index % 4,
                "Topic " + index,
                "Explanation " + index
            ))
            .toList();

        byte[] content = quizDocxExportService.exportQuizToDocx(buildQuiz(questions), QuizDocxExportMode.WITH_ANSWERS);

        try (XWPFDocument document = openDocument(content)) {
            String text = extractText(document);

            assertThat(text).contains("1. B    2. C    3. D    4. A    5. B");
            assertThat(text).contains("6. C    7. D    8. A    9. B    10. C");
        }
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
        try (XWPFDocument document = openDocument(content)) {
            String text = extractText(document);

            assertThat(text).contains("Subject: Biology")
                .contains("Topic: Biology Quiz")
                .contains("15. Question 15 – café ßeta?")
                .contains("Deltá")
                .contains("Explanation 15 covers café, naïve, and résumé.");
            assertThat(document.getParagraphs().stream().filter(paragraph -> paragraph.getText().startsWith("Question")).count()).isZero();
        }
    }

    @Test
    void exportCombinedQuiz_keepsSelectedSectionsAndOptionalTeacherMaterials() throws IOException {
        List<QuizDocxExportService.ExportableSection> sections = List.of(
                new QuizDocxExportService.ExportableSection(
                        "SECTION A - Multiple Choice",
                        List.of("Biology"),
                        List.of(new QuizItem("What is a cell?", List.of("Unit", "Atom", "Bond", "Gas"), 0, "Cells", "Cells are the basic unit of life."))
                ),
                new QuizDocxExportService.ExportableSection(
                        "SECTION B - Application",
                        List.of("Chemistry"),
                        List.of(new QuizItem("What is a mole?", List.of("Mass", "Amount", "Energy", "Charge"), 1, "Stoichiometry", "A mole is a measure of amount of substance."))
                )
        );

        byte[] content = quizDocxExportService.exportCombinedQuizToDocx(
                sections,
                new QuizDocxExportService.CombinedQuizDocxOptions(true, true)
        );

        try (XWPFDocument document = openDocument(content)) {
            String text = extractText(document);

            assertThat(text).contains("Subject: Mixed Subjects")
                    .contains("Topic: Combined Exam")
                    .contains("SECTION A - Multiple Choice")
                    .contains("SECTION B - Application")
                    .contains("1. What is a cell?")
                    .contains("2. What is a mole?")
                    .contains("Answer Key")
                    .contains("1. A")
                    .contains("2. B")
                    .contains("Explanations")
                    .contains("1. Cells are the basic unit of life.")
                    .contains("2. A mole is a measure of amount of substance.");
        }
    }

    @Test
    void exportCombinedQuiz_rendersThreeDeterministicVersionSections() throws IOException {
        List<QuizDocxExportService.ExportableSection> sections = List.of(
                new QuizDocxExportService.ExportableSection(
                        "SECTION A - Multiple Choice",
                        List.of("Biology"),
                        IntStream.rangeClosed(1, 6)
                                .mapToObj(index -> new QuizItem(
                                        "Combined question " + index,
                                        List.of("Correct " + index, "Choice B " + index, "Choice C " + index, "Choice D " + index),
                                        0,
                                        "Cells",
                                        "Explanation " + index
                                ))
                                .toList(),
                        "combined-section-seed"
                )
        );

        byte[] firstExport = quizDocxExportService.exportCombinedQuizToDocx(
                sections,
                new QuizDocxExportService.CombinedQuizDocxOptions(false, false, 3)
        );
        byte[] secondExport = quizDocxExportService.exportCombinedQuizToDocx(
                sections,
                new QuizDocxExportService.CombinedQuizDocxOptions(false, false, 3)
        );

        try (XWPFDocument firstDocument = openDocument(firstExport);
             XWPFDocument secondDocument = openDocument(secondExport)) {
            assertThat(extractText(firstDocument)).contains("Version A")
                    .contains("Version B")
                    .contains("Version C");
            assertThat(extractText(secondDocument)).isEqualTo(extractText(firstDocument));
        }
    }

    @Test
    void buildFilename_usesExpectedSuffixByMode() {
        assertThat(quizDocxExportService.buildFilename("Teacher Note", QuizDocxExportMode.QUIZ_ONLY))
                .isEqualTo("teacher-note-quiz.docx");
        assertThat(quizDocxExportService.buildFilename("Teacher Note", QuizDocxExportMode.WITH_ANSWERS))
                .isEqualTo("teacher-note-quiz-with-answers.docx");
        assertThat(quizDocxExportService.buildCombinedFilename(false, false))
                .isEqualTo("combined-exam.docx");
        assertThat(quizDocxExportService.buildCombinedFilename(true, false))
                .isEqualTo("combined-exam-with-answers.docx");
    }

    private QuizDocxExportService.ExportableQuiz buildQuiz(List<QuizItem> questions) {
        return new QuizDocxExportService.ExportableQuiz(
                "Biology Quiz",
                "Biology",
                OffsetDateTime.parse("2026-04-20T10:00:00Z"),
                questions
        );
    }

    private XWPFDocument openDocument(byte[] content) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(content));
    }

    private String extractText(XWPFDocument document) {
        return document.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .collect(Collectors.joining("\n"));
    }

    private boolean hasPageBreak(XWPFRun run) {
        return run.getCTR().getBrList().stream().anyMatch(breakElement -> breakElement.getType() == STBrType.PAGE);
    }
}
