package com.studysnap.backend.entity;

import com.studysnap.backend.exception.QuickReviewSessionAnchorException;
import org.junit.jupiter.api.Test;

import java.util.List;
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

    /**
     * ⚠️ THE DISCRIMINATING CASE for `v0.113.1` item 1, and the reason the collapse was not a
     * no-op. The service copy this replaced tested only `hasPackAndNote == hasCollection`, so a
     * partial pack/note shape ALONGSIDE a collection anchor evaluated `false == true` and PASSED it.
     * The entity rule rejects it. A fixture using only whole shapes passes under both.
     */
    @Test
    void partialPackAndNoteShapeIsRejectedEvenWhenACollectionAnchorIsPresent() {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setStudyPackId(UUID.randomUUID());
        session.setNoteId(null);
        session.setSourceCollectionId(UUID.randomUUID());

        assertThatThrownBy(session::validateAnchor)
            .isInstanceOf(QuickReviewSessionAnchorException.class);
    }

    @Test
    void partialPackAndNoteShapeIsRejectedOnItsOwn() {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setNoteId(UUID.randomUUID());

        assertThatThrownBy(session::validateAnchor)
            .isInstanceOf(QuickReviewSessionAnchorException.class);
    }

    /**
     * The other half of the guard: an anchorless TERMINAL row is history, orphaned by the
     * collection FK's ON DELETE SET NULL, and must stay permitted. Tightening the rule to reject
     * every anchorless row would make deleting a Study Plan fail.
     */
    @Test
    void anchorlessTerminalSessionStaysPermitted() {
        for (QuickReviewSessionStatus terminal : List.of(
                QuickReviewSessionStatus.COMPLETED, QuickReviewSessionStatus.FORFEITED)) {
            QuickReviewSessionEntity session = new QuickReviewSessionEntity();
            session.setStatus(terminal);

            assertThatCode(session::validateAnchor).doesNotThrowAnyException();
        }
    }

}
