package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizDocxExportMode;
import com.studysnap.backend.dto.QuizItem;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class QuizDocxExportService {
    private static final String COMBINED_TOPIC = "Combined Exam";
    private static final String MIXED_SUBJECTS = "Mixed Subjects";
    private static final String SUBJECT_PREFIX = "Subject: ";
    private static final String TOPIC_PREFIX = "Topic: ";
    private static final String TIME_LINE = "Time: ____________";
    private static final String ANSWER_KEY_HEADING = "Answer Key";
    private static final String EXPLANATIONS_HEADING = "Explanations";
    private static final String EXAM_TITLE = "QUIZ";
    private static final String MULTIPLE_CHOICE_SECTION = "PART I - MULTIPLE CHOICE";
    private static final String MULTIPLE_CHOICE_DIRECTIONS = "Instructions: Read each question carefully. Choose the best answer.";
    private static final String NAME_LINE = "Name: __________________________";
    private static final String DATE_LINE = "Date: __________________________";
    private static final String SCORE_LINE = "Score: __________________________";
    private static final String UNTITLED_NOTE = "untitled-note";
    private static final String QUIZ_FILENAME_SUFFIX = "-quiz.docx";
    private static final String QUIZ_WITH_ANSWERS_FILENAME_SUFFIX = "-quiz-with-answers.docx";
    private static final String COMBINED_QUIZ_FILENAME = "combined-exam.docx";
    private static final String COMBINED_QUIZ_WITH_ANSWERS_FILENAME = "combined-exam-with-answers.docx";
    private static final String FONT_FAMILY = "Calibri";
    private static final int MARGIN_TWIPS = 1440;
    private static final int TITLE_FONT_SIZE = 16;
    private static final int SUBTITLE_FONT_SIZE = 12;
    private static final int BODY_FONT_SIZE = 11;
    private static final int SECTION_HEADING_FONT_SIZE = 14;
    private static final int NOTE_SECTION_HEADING_FONT_SIZE = 12;
    private static final int HEADER_SPACING = 120;
    private static final int TITLE_SPACING = 180;
    private static final int INSTRUCTION_SPACING = 160;
    private static final int SECTION_SPACING = 180;
    private static final int EXPLANATION_SPACING = 120;
    private static final int NOTE_SECTION_SPACING = 120;
    private static final int CHOICE_INDENT = 400;
    private static final int ANSWERS_PER_LINE = 5;

    public byte[] exportQuizToDocx(ExportableQuiz quiz, QuizDocxExportMode mode) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            log.info("Applying DOCX v4 exam formatting...");
            setMargins(document);
            addHeader(document, quiz);
            addExamTitle(document);
            addSectionIntro(document);
            addQuestions(document, quiz.questions(), 1);
            if (mode == QuizDocxExportMode.WITH_ANSWERS) {
                addPageBreak(document);
                addSectionHeading(document, ANSWER_KEY_HEADING);
                addAnswerKeyEntries(document, quiz.questions());
                addPageBreak(document);
                addSectionHeading(document, EXPLANATIONS_HEADING);
                addExplanationEntries(document, quiz.questions());
            }
            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not export quiz DOCX.", exception);
        }
    }

    public byte[] exportCombinedQuizToDocx(List<ExportableQuiz> quizzes, CombinedQuizDocxOptions options) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            log.info("Applying DOCX exam builder formatting...");
            setMargins(document);
            addCombinedHeader(document, quizzes);
            addExamTitle(document);
            addSectionIntro(document);

            List<QuizItem> orderedQuestions = new ArrayList<>();
            int nextQuestionNumber = 1;
            boolean multipleSections = quizzes.size() > 1;
            for (int quizIndex = 0; quizIndex < quizzes.size(); quizIndex++) {
                ExportableQuiz quiz = quizzes.get(quizIndex);
                if (multipleSections) {
                    addNoteSectionHeading(document, quizIndex + 1, quiz.title());
                }
                nextQuestionNumber = addQuestions(document, quiz.questions(), nextQuestionNumber);
                orderedQuestions.addAll(quiz.questions());
            }

            if (options.includeAnswerKey()) {
                addPageBreak(document);
                addSectionHeading(document, ANSWER_KEY_HEADING);
                addAnswerKeyEntries(document, orderedQuestions);
            }
            if (options.includeExplanations()) {
                addPageBreak(document);
                addSectionHeading(document, EXPLANATIONS_HEADING);
                addExplanationEntries(document, orderedQuestions);
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

    public String buildCombinedFilename(boolean includeAnswerKey, boolean includeExplanations) {
        return includeAnswerKey || includeExplanations
                ? COMBINED_QUIZ_WITH_ANSWERS_FILENAME
                : COMBINED_QUIZ_FILENAME;
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
        addHeaderLine(document, SUBJECT_PREFIX + normalizeText(quiz.subject()), HEADER_SPACING);
        addHeaderLine(document, TOPIC_PREFIX + normalizeText(quiz.title()), HEADER_SPACING);
        addHeaderLine(document, TIME_LINE, SECTION_SPACING);
        addHeaderLine(document, NAME_LINE, HEADER_SPACING);
        addHeaderLine(document, DATE_LINE, HEADER_SPACING);
        addHeaderLine(document, SCORE_LINE, SECTION_SPACING);
    }

    private void addCombinedHeader(XWPFDocument document, List<ExportableQuiz> quizzes) {
        addHeaderLine(document, SUBJECT_PREFIX + resolveCombinedSubject(quizzes), HEADER_SPACING);
        addHeaderLine(document, TOPIC_PREFIX + COMBINED_TOPIC, HEADER_SPACING);
        addHeaderLine(document, TIME_LINE, SECTION_SPACING);
        addHeaderLine(document, NAME_LINE, HEADER_SPACING);
        addHeaderLine(document, DATE_LINE, HEADER_SPACING);
        addHeaderLine(document, SCORE_LINE, SECTION_SPACING);
    }

    private void addExamTitle(XWPFDocument document) {
        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setAlignment(ParagraphAlignment.CENTER);
        titleParagraph.setSpacingAfter(TITLE_SPACING);
        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(TITLE_FONT_SIZE);
        titleRun.setFontFamily(FONT_FAMILY);
        titleRun.setText(EXAM_TITLE);
    }

    private void addSectionIntro(XWPFDocument document) {
        XWPFParagraph sectionParagraph = document.createParagraph();
        sectionParagraph.setSpacingAfter(HEADER_SPACING);
        XWPFRun sectionRun = sectionParagraph.createRun();
        sectionRun.setBold(true);
        sectionRun.setFontSize(SUBTITLE_FONT_SIZE);
        sectionRun.setFontFamily(FONT_FAMILY);
        sectionRun.setText(MULTIPLE_CHOICE_SECTION);

        XWPFParagraph directionParagraph = document.createParagraph();
        directionParagraph.setSpacingAfter(INSTRUCTION_SPACING);
        XWPFRun directionRun = directionParagraph.createRun();
        directionRun.setFontSize(BODY_FONT_SIZE);
        directionRun.setFontFamily(FONT_FAMILY);
        directionRun.setText(MULTIPLE_CHOICE_DIRECTIONS);
    }

    private void addNoteSectionHeading(XWPFDocument document, int sectionNumber, String title) {
        XWPFParagraph headingParagraph = document.createParagraph();
        headingParagraph.setSpacingBefore(NOTE_SECTION_SPACING);
        headingParagraph.setSpacingAfter(HEADER_SPACING);
        XWPFRun headingRun = headingParagraph.createRun();
        headingRun.setBold(true);
        headingRun.setFontSize(NOTE_SECTION_HEADING_FONT_SIZE);
        headingRun.setFontFamily(FONT_FAMILY);
        headingRun.setText("Section " + sectionNumber + ": " + normalizeText(title));
    }

    private void addSectionHeading(XWPFDocument document, String text) {
        XWPFParagraph headingParagraph = document.createParagraph();
        headingParagraph.setSpacingBefore(0);
        headingParagraph.setSpacingAfter(HEADER_SPACING);
        XWPFRun headingRun = headingParagraph.createRun();
        headingRun.setBold(true);
        headingRun.setFontSize(SECTION_HEADING_FONT_SIZE);
        headingRun.setFontFamily(FONT_FAMILY);
        headingRun.setText(text);
    }

    private void addHeaderLine(XWPFDocument document, String text, int spacingAfter) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(spacingAfter);
        XWPFRun run = paragraph.createRun();
        run.setFontSize(BODY_FONT_SIZE);
        run.setFontFamily(FONT_FAMILY);
        run.setText(text);
    }

    private int addQuestions(XWPFDocument document, List<QuizItem> questions, int startingNumber) {
        int nextQuestionNumber = startingNumber;
        for (QuizItem question : questions) {
            XWPFParagraph questionParagraph = document.createParagraph();
            questionParagraph.setSpacingAfter(0);

            XWPFRun numberRun = questionParagraph.createRun();
            numberRun.setBold(true);
            numberRun.setFontSize(BODY_FONT_SIZE);
            numberRun.setFontFamily(FONT_FAMILY);
            numberRun.setText(nextQuestionNumber + ". ");

            XWPFRun questionRun = questionParagraph.createRun();
            questionRun.setBold(false);
            questionRun.setFontSize(BODY_FONT_SIZE);
            questionRun.setFontFamily(FONT_FAMILY);
            questionRun.setText(normalizeText(question.question()));

            List<String> choices = question.choices() == null ? List.of() : question.choices();
            for (int choiceIndex = 0; choiceIndex < choices.size(); choiceIndex++) {
                XWPFParagraph choiceParagraph = document.createParagraph();
                choiceParagraph.setIndentationLeft(CHOICE_INDENT);
                choiceParagraph.setSpacingAfter(0);
                XWPFRun choiceRun = choiceParagraph.createRun();
                choiceRun.setFontSize(BODY_FONT_SIZE);
                choiceRun.setFontFamily(FONT_FAMILY);
                choiceRun.setText(answerLabel(choiceIndex) + ". " + normalizeText(choices.get(choiceIndex)));
            }

            XWPFParagraph spacer = document.createParagraph();
            spacer.setSpacingAfter(0);
            XWPFRun spacerRun = spacer.createRun();
            spacerRun.setFontFamily(FONT_FAMILY);
            spacerRun.setFontSize(BODY_FONT_SIZE);
            spacerRun.addBreak();
            nextQuestionNumber++;
        }
        return nextQuestionNumber;
    }

    private void addPageBreak(XWPFDocument document) {
        XWPFParagraph pageBreakParagraph = document.createParagraph();
        pageBreakParagraph.setSpacingAfter(0);
        XWPFRun pageBreakRun = pageBreakParagraph.createRun();
        pageBreakRun.setFontFamily(FONT_FAMILY);
        pageBreakRun.addBreak(BreakType.PAGE);
    }

    private void addAnswerKeyEntries(XWPFDocument document, List<QuizItem> questions) {
        for (int startIndex = 0; startIndex < questions.size(); startIndex += ANSWERS_PER_LINE) {
            StringBuilder line = new StringBuilder();
            int endIndex = Math.min(startIndex + ANSWERS_PER_LINE, questions.size());
            for (int questionIndex = startIndex; questionIndex < endIndex; questionIndex++) {
                QuizItem question = questions.get(questionIndex);
                Integer correctIndex = question.correctIndex();
                String label = correctIndex == null ? "-" : answerLabel(correctIndex);
                if (!line.isEmpty()) {
                    line.append("    ");
                }
                line.append(questionIndex + 1).append(". ").append(label);
            }
            addBodyParagraph(document, line.toString(), false, 0);
        }
    }

    private void addExplanationEntries(XWPFDocument document, List<QuizItem> questions) {
        for (int index = 0; index < questions.size(); index++) {
            QuizItem question = questions.get(index);
            addBodyParagraph(document, (index + 1) + ". " + normalizeText(question.explanation()), false, EXPLANATION_SPACING);
        }
    }

    private void addBodyParagraph(XWPFDocument document, String text, boolean bold, int spacingAfter) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(spacingAfter);
        XWPFRun run = paragraph.createRun();
        run.setBold(bold);
        run.setFontSize(BODY_FONT_SIZE);
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

    private String resolveCombinedSubject(List<ExportableQuiz> quizzes) {
        Set<String> subjects = new LinkedHashSet<>();
        for (ExportableQuiz quiz : quizzes) {
            String normalizedSubject = normalizeOptionalText(quiz.subject());
            if (normalizedSubject != null) {
                subjects.add(normalizedSubject);
            }
        }
        if (subjects.size() == 1) {
            return subjects.iterator().next();
        }
        return MIXED_SUBJECTS;
    }

    private String normalizeOptionalText(String value) {
        String normalized = normalizeText(value);
        return Objects.equals(normalized, "-") ? null : normalized;
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

    public record CombinedQuizDocxOptions(
            boolean includeAnswerKey,
            boolean includeExplanations
    ) {
    }

    @Getter
    @RequiredArgsConstructor
    public static class QuizDocxFile {
        private final String filename;
        private final byte[] content;
    }
}
