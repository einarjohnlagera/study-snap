package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidBoardExamSourceException extends AppException {
    private static final String CODE = "INVALID_BOARD_EXAM_SOURCE";
    private static final String PRIMARY_SUBJECT_REQUIRED_MESSAGE = "Add a subject to this note before adding more sources";
    private static final String SUBJECT_MISMATCH_MESSAGE = "All notes must share the same subject";
    private static final String SOURCE_UNAVAILABLE_MESSAGE = "One of the selected notes is no longer available";
    private static final String TOO_MANY_SOURCES_MESSAGE = "Too many notes selected for the available question count — remove one";

    private InvalidBoardExamSourceException(String message) {
        super(CODE, message, HttpStatus.BAD_REQUEST);
    }

    public static InvalidBoardExamSourceException primarySubjectRequired() {
        return new InvalidBoardExamSourceException(PRIMARY_SUBJECT_REQUIRED_MESSAGE);
    }

    public static InvalidBoardExamSourceException subjectMismatch() {
        return new InvalidBoardExamSourceException(SUBJECT_MISMATCH_MESSAGE);
    }

    public static InvalidBoardExamSourceException sourceUnavailable() {
        return new InvalidBoardExamSourceException(SOURCE_UNAVAILABLE_MESSAGE);
    }

    public static InvalidBoardExamSourceException tooManySources() {
        return new InvalidBoardExamSourceException(TOO_MANY_SOURCES_MESSAGE);
    }
}
