package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class GeneratedQuizExportNotAllowedException extends AppException {
    public GeneratedQuizExportNotAllowedException() {
        super(
                "GENERATED_QUIZ_EXPORT_NOT_ALLOWED",
                "Quiz export is available for Teacher and Admin accounts only.",
                HttpStatus.FORBIDDEN
        );
    }
}
