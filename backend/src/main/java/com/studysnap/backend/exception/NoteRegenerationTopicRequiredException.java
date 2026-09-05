package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * A combined Note + Study Pack regeneration was requested for a note with no title.
 *
 * <p>The note title IS the regeneration topic. {@code notes.title} is nullable ({@code V11__notes.sql}),
 * and the service builds {@code GenerateNoteFromTopicRequest} internally, so the DTO's {@code @NotBlank}
 * never fires — this is the explicit service-level guard that replaces it. Subject and content are
 * deliberately NOT used as a fallback topic.
 */
public class NoteRegenerationTopicRequiredException extends AppException {
    public NoteRegenerationTopicRequiredException() {
        super(
                "NOTE_REGENERATION_TOPIC_REQUIRED",
                "Add a title to this note before regenerating it. The title is the topic we write from.",
                HttpStatus.BAD_REQUEST
        );
    }
}
