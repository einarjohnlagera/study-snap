package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerationFailureReason;
import com.studysnap.backend.exception.AppException;
import org.springframework.stereotype.Component;

@Component
public class BulkGenerationFailureReasonNormalizer {
    static final String UNEXPECTED_ERROR_CODE = "UNEXPECTED_ERROR";
    private static final String UNEXPECTED_ERROR_REASON_TEMPLATE =
            "An unexpected error occurred while generating this topic (%s).";

    public BulkGenerationFailureReason normalize(String topic, RuntimeException exception) {
        if (exception instanceof AppException appException) {
            return new BulkGenerationFailureReason(topic, appException.getCode(), appException.getMessage());
        }
        return unexpected(topic, exception);
    }

    static BulkGenerationFailureReason unexpected(String topic, RuntimeException exception) {
        String exceptionClassName = exception.getClass().getSimpleName();
        return new BulkGenerationFailureReason(
                topic,
                UNEXPECTED_ERROR_CODE,
                UNEXPECTED_ERROR_REASON_TEMPLATE.formatted(exceptionClassName)
        );
    }
}
