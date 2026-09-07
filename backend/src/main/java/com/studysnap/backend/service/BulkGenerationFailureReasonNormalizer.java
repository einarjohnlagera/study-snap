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
        return normalizeFailure(topic, exception);
    }

    /**
     * The same policy, reachable without injecting this component.
     *
     * <p>⚠️ IT EXISTS SO THE POLICY HAS EXACTLY ONE HOME. {@code v0.127.0} gave single-note generation
     * failures a persisted reason on {@code notes}, and {@code StudyPackService} normalizes them here
     * rather than minting a second implementation of {@code v0.87.0}'s rule -- two implementations of
     * "never surface raw exception text" is the shape that drifts. It is {@code static} because
     * {@code StudyPackService} is constructed positionally in eight places; a constructor argument
     * would have cost eight edits for no behavioural difference.
     *
     * <p>⚠️ Takes {@link Exception}, not {@link RuntimeException}, because the async generation worker
     * catches the wider type. The bulk callers still pass a {@code RuntimeException} and are unchanged.
     */
    static BulkGenerationFailureReason normalizeFailure(String topic, Exception exception) {
        if (exception instanceof AppException appException) {
            return new BulkGenerationFailureReason(topic, appException.getCode(), appException.getMessage());
        }
        return unexpected(topic, exception);
    }

    static BulkGenerationFailureReason unexpected(String topic, Exception exception) {
        String exceptionClassName = exception.getClass().getSimpleName();
        return new BulkGenerationFailureReason(
                topic,
                UNEXPECTED_ERROR_CODE,
                UNEXPECTED_ERROR_REASON_TEMPLATE.formatted(exceptionClassName)
        );
    }
}
