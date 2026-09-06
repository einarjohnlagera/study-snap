package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record PublicSharedQuizResponse(
        UUID quizId,
        String noteTitle,
        List<PublicQuizItem> questions,
        /**
         * Sources the recipient may continue learning from, or an EMPTY list.
         *
         * <p>⚠️ A LIST rather than a nullable single field on purpose. The rule is a capability, not a
         * single-note special case: a source appears only where NoteLib has DURABLE source-Note identity
         * AND the source is legitimately accessible to this recipient. Today
         * {@code generated_quizzes.note_id} qualifies and a combined quiz's copied section title does not,
         * so combined quizzes contribute nothing -- through the ordinary path, with no {@code isCombined}
         * branch anywhere. When combined quizzes gain provenance they light up under the same rule.
         *
         * <p>⚠️ NEVER infer source identity from a copied title.
         */
        List<PublicSourceNote> sourceNotes
) {
}
