package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TriTest {
    private static final Rational FIVE_PERCENT = Rational.of(5, 100);

    @Test void completeEvidenceCanPassOrFail() {
        assertThat(Tri.evaluate(List.of(0), List.of(), FIVE_PERCENT)).isEqualTo(Tri.PASS_COMPLETE);
        assertThat(Tri.evaluate(Collections.nCopies(5, 1), List.of(), FIVE_PERCENT)).isEqualTo(Tri.FAIL);
    }

    @Test void unresolvedNeverProducesCompletePassEvenWhenRangeCannotChangeResult() {
        assertThat(Tri.evaluate(List.of(0), List.of(new Tri.Unresolved(0, 0)), FIVE_PERCENT))
                .isEqualTo(Tri.INCONCLUSIVE);
        assertThat(Tri.evaluate(List.of(), List.of(new Tri.Unresolved(-1, 1)), FIVE_PERCENT))
                .isEqualTo(Tri.INCONCLUSIVE);
    }

    @Test void failureMustSurviveLowestMostCandidateFavourableCompletion() {
        assertThat(Tri.evaluate(Collections.nCopies(8, 1), List.of(new Tri.Unresolved(-1, 1)), FIVE_PERCENT))
                .isEqualTo(Tri.FAIL);
        assertThat(Tri.evaluate(Collections.nCopies(4, 1), List.of(new Tri.Unresolved(-1, 1)), FIVE_PERCENT))
                .isEqualTo(Tri.INCONCLUSIVE);
        assertThat(Tri.evaluate(Collections.nCopies(4, 1), List.of(new Tri.Unresolved(1, 1)), FIVE_PERCENT))
                .isEqualTo(Tri.FAIL);
    }
}
