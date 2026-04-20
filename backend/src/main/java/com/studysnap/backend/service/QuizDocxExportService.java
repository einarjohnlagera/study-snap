package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizDocxExportMode;
import com.studysnap.backend.dto.QuizItem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class QuizDocxExportService {
    private static final String ANSWER_KEY_HEADING = "ANSWER KEY";
    private static final String EXPLANATIONS_HEADING = "EXPLANATIONS";
    private static final String DIVIDER = "-".repeat(44);
    private static final String UNTITLED_NOTE = "untitled-note";
    private static final String QUIZ_FILENAME_SUFFIX = "-quiz.docx";
    private static final String QUIZ_WITH_ANSWERS_FILENAME_SUFFIX = "-quiz-with-answers.docx";
    private static final String FONT_FAMILY = "Calibri";
    private static final int MARGIN_TWIPS = 1440;

    public byte[] exportQuizToDocx(ExportableQuiz quiz, QuizDocxExportMode mode) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            setMargins(document);
            addHeader(document, quiz);
            addStudentInfoSection(document);
            addQuestions(document, quiz.questions());
            if (mode == QuizDocxExportMode.WITH_ANSWERS) {
                addPageBreak(document);
                addDividerSectionHeading(document, ANSWER_KEY_HEADING);
                addAnswerKeyEntries(document, quiz.questions());
                addDividerSectionHeading(document, EXPLANATIONS_HEADING);
                addExplanationEntries(document, quiz.questions());
            }
            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not export quiz DOCX.", exception);
        }
    }

    public String buildFilename(String noteTitle, QuizDocxExportMode mode) {
        String baseName = sanitizeFilenameSegment(noteTitle);
        return baseName + (mode == QuizDocxExportMode.WITH_ANSWERS
                ? QUIZ_WITH_ANSWERS_FILENAME_SUFFIX
                : QUIZ_FILENAME_SUFFIX);
    }

    private void setMargins(XWPFDocument document) {
        CTSectPr sectPr = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(MARGIN_TWIPS));
        pageMar.setBottom(BigInteger.valueOf(MARGIN_TWIPS));
        pageMar.setLeft(BigInteger.valueOf(MARGIN_TWIPS));
        pageMar.setRight(BigInteger.valueOf(MARGIN_TWIPS));
    }

    private void addHeader(XWPFDocument document, ExportableQuiz quiz) {
        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setAlignment(ParagraphAlignment.CENTER);
        titleParagraph.setSpacingAfter(80);
        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(16);
        titleRun.setFontFamily(FONT_FAMILY);
        titleRun.setText(normalizeText(quiz.title()));

        XWPFParagraph subtitleParagraph = document.createParagraph();
        subtitleParagraph.setAlignment(ParagraphAlignment.CENTER);
        subtitleParagraph.setSpacingAfter(160);
        XWPFRun subtitleRun = subtitleParagraph.createRun();
        subtitleRun.setFontSize(12);
        subtitleRun.setFontFamily(FONT_FAMILY);
        subtitleRun.setText("Quiz");

        addDividerLine(document, 160);
    }

    private void addStudentInfoSection(XWPFDocument document) {
        addInfoLine(document, "Name: __________________________", 80);
        addInfoLine(document, "Date: __________________________", 360);
    }

    private void addDividerLine(XWPFDocument document, int spacingAfter) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(spacingAfter);
        XWPFRun run = paragraph.createRun();
        run.setFontSize(10);
        run.setFontFamily(FONT_FAMILY);
        run.setText(DIVIDER);
    }

    private void addDividerSectionHeading(XWPFDocument document, String text) {
        addDividerLine(document, 60);

        XWPFParagraph headingParagraph = document.createParagraph();
        headingParagraph.setSpacingAfter(60);
        XWPFRun headingRun = headingParagraph.createRun();
        headingRun.setBold(true);
        headingRun.setFontSize(13);
        headingRun.setFontFamily(FONT_FAMILY);
        headingRun.setText(text);

        addDividerLine(document, 160);
    }

    private void addInfoLine(XWPFDocument document, String text, int spacingAfter) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(spacingAfter);
        XWPFRun run = paragraph.createRun();
        run.setFontSize(11);
        run.setFontFamily(FONT_FAMILY);
        run.setText(text);
    }

    private void addQuestions(XWPFDocument document, List<QuizItem> questions) {
        for (int index = 0; index < questions.size(); index++) {
            QuizItem question = questions.get(index);

            XWPFParagraph questionParagraph = document.createParagraph();
            questionParagraph.setSpacingAfter(100);

            XWPFRun numberRun = questionParagraph.createRun();
            numberRun.setBold(true);
            numberRun.setFontSize(12);
            numberRun.setFontFamily(FONT_FAMILY);
            numberRun.setText((index + 1) + ". ");

            XWPFRun questionRun = questionParagraph.createRun();
            questionRun.setBold(false);
            questionRun.setFontSize(12);
            questionRun.setFontFamily(FONT_FAMILY);
            questionRun.setText(normalizeText(question.question()));

            List<String> choices = question.choices() == null ? List.of() : question.choices();
            for (int choiceIndex = 0; choiceIndex < choices.size(); choiceIndex++) {
                XWPFParagraph choiceParagraph = document.createParagraph();
                choiceParagraph.setIndentationLeft(360);
                choiceParagraph.setSpacingAfter(choiceIndex == choices.size() - 1 ? 0 : 60);
                XWPFRun choiceRun = choiceParagraph.createRun();
                choiceRun.setFontSize(11);
                choiceRun.setFontFamily(FONT_FAMILY);
                choiceRun.setText(answerLabel(choiceIndex) + ". " + normalizeText(choices.get(choiceIndex)));
            }

            XWPFParagraph spacer = document.createParagraph();
            spacer.setSpacingAfter(220);
        }
    }

    private void addPageBreak(XWPFDocument document) {
        XWPFParagraph pageBreakParagraph = document.createParagraph();
        XWPFRun pageBreakRun = pageBreakParagraph.createRun();
        pageBreakRun.addBreak(BreakType.PAGE);
    }

    private void addAnswerKeyEntries(XWPFDocument document, List<QuizItem> questions) {
        for (int index = 0; index < questions.size(); index++) {
            QuizItem question = questions.get(index);
            Integer correctIndex = question.correctIndex();
            String label = correctIndex == null ? "-" : answerLabel(correctIndex);
            addBodyParagraph(document, (index + 1) + ". " + label, false, 60);
        }
    }

    private void addExplanationEntries(XWPFDocument document, List<QuizItem> questions) {
        for (int index = 0; index < questions.size(); index++) {
            QuizItem question = questions.get(index);
            addBodyParagraph(document, (index + 1) + ". " + normalizeText(question.explanation()), false, 160);
        }
    }

    private void addBodyParagraph(XWPFDocument document, String text, boolean bold, int spacingAfter) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(spacingAfter);
        XWPFRun run = paragraph.createRun();
        run.setBold(bold);
        run.setFontSize(11);
        run.setFontFamily(FONT_FAMILY);
        run.setText(normalizeText(text));
    }

    private String sanitizeFilenameSegment(String value) {
        if (value == null || value.isBlank()) {
            return UNTITLED_NOTE;
        }
        String normalized = value.trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? UNTITLED_NOTE : normalized;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String answerLabel(int index) {
        return String.valueOf((char) ('A' + index));
    }

    public record ExportableQuiz(
            String title,
            String subject,
            OffsetDateTime generatedAt,
            List<QuizItem> questions
    ) {
        public ExportableQuiz {
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class QuizDocxFile {
        private final String filename;
        private final byte[] content;
    }
}
