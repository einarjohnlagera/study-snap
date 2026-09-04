package com.studysnap.backend.entity;

import com.studysnap.backend.exception.QuickReviewSessionAnchorException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuickReviewSessionEntityTest {

    @Test
    void packAndNoteAnchorIsValid() {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setStudyPackId(UUID.randomUUID());
        session.setNoteId(UUID.randomUUID());

        assertThatCode(session::validateAnchor).doesNotThrowAnyException();
    }

    @Test
    void collectionAnchorIsValid() {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setSourceCollectionId(UUID.randomUUID());

        assertThatCode(session::validateAnchor).doesNotThrowAnyException();
    }

    @Test
    void anchorlessSessionFailsWithNamedExceptionBeforePersistence() {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();

        assertThatThrownBy(session::validateAnchor)
            .isInstanceOf(QuickReviewSessionAnchorException.class);
    }

    @Test
    void mixedAnchorShapesFailWithNamedExceptionBeforePersistence() {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setStudyPackId(UUID.randomUUID());
        session.setNoteId(UUID.randomUUID());
        session.setSourceCollectionId(UUID.randomUUID());

        assertThatThrownBy(session::validateAnchor)
            .isInstanceOf(QuickReviewSessionAnchorException.class);
    }
}
