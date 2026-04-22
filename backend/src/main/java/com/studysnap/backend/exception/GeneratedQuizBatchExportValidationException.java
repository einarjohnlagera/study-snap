package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class GeneratedQuizBatchExportValidationException extends AppException {
    private static final String CODE = "GENERATED_QUIZ_BATCH_EXPORT_INVALID";

    private GeneratedQuizBatchExportValidationException(String message) {
        super(CODE, message, HttpStatus.BAD_REQUEST);
    }

    public static GeneratedQuizBatchExportValidationException emptySelection() {
        return new GeneratedQuizBatchExportValidationException("Select at least one quiz-ready note to export an exam.");
    }

    public static GeneratedQuizBatchExportValidationException noteWithoutGeneratedQuiz() {
        return new GeneratedQuizBatchExportValidationException("Only notes with generated quizzes can be included in an exam export.");
    }

    public static GeneratedQuizBatchExportValidationException unknownNote() {
        return new GeneratedQuizBatchExportValidationException("One or more selected notes could not be exported.");
    }

    public static GeneratedQuizBatchExportValidationException invalidQuestionSelection() {
        return new GeneratedQuizBatchExportValidationException("One or more selected quiz questions could not be exported.");
    }
}
