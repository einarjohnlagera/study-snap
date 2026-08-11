package com.studysnap.backend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UpsertNoteRequestDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsTheLegacyCourseProgramFieldName() throws Exception {
        // M1: v0.71.0 renamed this field on the wire. Without the alias a learner on a stale bundle sends
        // `courseProgram`, courseProgramText reads as null, and NoteService.resolveRequestedCourseProgram
        // falls back to the owner's PROFILE program -- never the note's existing value -- silently
        // reassigning the note for the length of the deploy window.
        UpsertNoteRequest request = objectMapper.readValue(
                """
                {
                  "title": "Title",
                  "subject": "Algebra",
                  "courseProgram": "BS Civil Engineering",
                  "content": "Body"
                }
                """,
                UpsertNoteRequest.class
        );

        assertThat(request.courseProgramText()).isEqualTo("BS Civil Engineering");
    }

    @Test
    void prefersTheCurrentFieldNameAndStillReadsCatalogIds() throws Exception {
        UUID programId = UUID.randomUUID();

        UpsertNoteRequest request = objectMapper.readValue(
                """
                {
                  "title": "Title",
                  "subject": "Algebra",
                  "courseProgramText": "Personal Program",
                  "courseProgramIds": ["%s"],
                  "content": "Body"
                }
                """.formatted(programId),
                UpsertNoteRequest.class
        );

        assertThat(request.courseProgramText()).isEqualTo("Personal Program");
        assertThat(request.courseProgramIds()).containsExactly(programId);
    }

    @Test
    void leavesTheFieldNullWhenNeitherNameIsSent() throws Exception {
        // The curator branch sends no personal program at all; it must stay null rather than becoming "".
        UpsertNoteRequest request = objectMapper.readValue(
                """
                {
                  "title": "Title",
                  "subject": "Algebra",
                  "content": "Body"
                }
                """,
                UpsertNoteRequest.class
        );

        assertThat(request.courseProgramText()).isNull();
        assertThat(request.courseProgramIds()).isNull();
    }
}
