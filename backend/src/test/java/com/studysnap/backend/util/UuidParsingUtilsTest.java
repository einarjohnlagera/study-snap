package com.studysnap.backend.util;

import com.studysnap.backend.exception.NoteNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UuidParsingUtilsTest {

    @Test
    void parseUuidOrThrow_returnsParsedUuidForValidInput() {
        UUID uuid = UUID.randomUUID();

        UUID parsed = UuidParsingUtils.parseUuidOrThrow(uuid.toString(), NoteNotFoundException::new);

        assertThat(parsed).isEqualTo(uuid);
    }

    @Test
    void parseUuidOrThrow_throwsSuppliedExceptionForInvalidInput() {
        assertThatThrownBy(() -> UuidParsingUtils.parseUuidOrThrow("not-a-uuid", NoteNotFoundException::new))
                .isInstanceOf(NoteNotFoundException.class)
                .hasMessage("Note not found.");
    }
}
