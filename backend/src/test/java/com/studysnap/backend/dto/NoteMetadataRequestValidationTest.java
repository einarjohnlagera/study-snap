package com.studysnap.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoteMetadataRequestValidationTest {

    @Test
    void requestDtosMatchSubjectAndCourseProgramStorageBounds() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            UpsertNoteRequest noteRequest = new UpsertNoteRequest(
                    "Title", "s".repeat(65), "c".repeat(121), null, null, List.of(), "content"
            );
            BulkGenerateNotesRequest bulkRequest = new BulkGenerateNotesRequest(
                    "s".repeat(65), List.of("Topic"), false, "c".repeat(121), null, null
            );
            UpdateStudyPackMetadataRequest studyPackRequest = new UpdateStudyPackMetadataRequest(
                    "Title", "s".repeat(65)
            );

            assertThat(validator.validate(noteRequest))
                    .extracting(violation -> violation.getMessage())
                    .containsExactlyInAnyOrder(
                            "Subject must be 64 characters or less.",
                            "Course/program must be 120 characters or less."
                    );
            assertThat(validator.validate(bulkRequest))
                    .extracting(violation -> violation.getMessage())
                    .containsExactlyInAnyOrder(
                            "Subject must be 64 characters or less.",
                            "Course/program must be 120 characters or less."
                    );
            assertThat(validator.validate(studyPackRequest))
                    .extracting(violation -> violation.getMessage())
                    .containsExactly("Subject must be 64 characters or less.");
        }
    }
}
