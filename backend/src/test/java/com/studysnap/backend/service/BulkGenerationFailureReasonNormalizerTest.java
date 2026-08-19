package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerationFailureReason;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.SubjectTooLongException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BulkGenerationFailureReasonNormalizerTest {
    private static final String TOPIC = "Cell Respiration";
    private static final String APP_ERROR_CODE = "LLM_INVALID_OUTPUT";
    private static final String APP_ERROR_MESSAGE = "Generated note has an invalid key idea.";

    private final BulkGenerationFailureReasonNormalizer normalizer =
            new BulkGenerationFailureReasonNormalizer();

    @Test
    void normalize_preservesAppExceptionCodeAndMessage() {
        AppException exception = new TestAppException();

        BulkGenerationFailureReason result = normalizer.normalize(TOPIC, exception);

        assertThat(result).isEqualTo(new BulkGenerationFailureReason(
                TOPIC,
                APP_ERROR_CODE,
                APP_ERROR_MESSAGE
        ));
    }

    @Test
    void normalize_recordsNamedSubjectBoundReasonInsteadOfUnexpectedError() {
        BulkGenerationFailureReason result = normalizer.normalize("Topic", new SubjectTooLongException());

        assertThat(result.code()).isEqualTo("SUBJECT_TOO_LONG");
        assertThat(result.reason()).isEqualTo("Subject must be 64 characters or less.");
    }

    @Test
    void normalize_usesOnlyGenericCopyAndClassNameForUnexpectedExceptions() {
        String sensitiveMessage = "raw provider response must stay private";

        BulkGenerationFailureReason result = normalizer.normalize(
                TOPIC,
                new IllegalStateException(sensitiveMessage)
        );

        assertThat(result.code()).isEqualTo(BulkGenerationFailureReasonNormalizer.UNEXPECTED_ERROR_CODE);
        assertThat(result.reason())
                .contains(IllegalStateException.class.getSimpleName())
                .doesNotContain(sensitiveMessage);
    }

    private static final class TestAppException extends AppException {
        private TestAppException() {
            super(APP_ERROR_CODE, APP_ERROR_MESSAGE, HttpStatus.BAD_GATEWAY);
        }
    }
}
