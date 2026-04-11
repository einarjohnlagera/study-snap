package com.studysnap.backend.util;

import com.studysnap.backend.dto.NoteListItemResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PublicNotesScoringUtilsTest {

    // --- computeScore ---

    @Test
    void computeScore_weightsFormula_copiesPointSixViewsPointFour() {
        NoteListItemResponse note = makeNote("id", 10L, 10L, 0L, OffsetDateTime.now());
        // (10 * 0.6) + (10 * 0.4) = 6.0 + 4.0 = 10.0
        assertThat(PublicNotesScoringUtils.computeScore(note)).isCloseTo(10.0, within(0.001));
    }

    @Test
    void computeScore_copiesWeightedHigherThanViews() {
        NoteListItemResponse highCopies = makeNote("a", 10L, 0L, 0L, OffsetDateTime.now());
        NoteListItemResponse highViews = makeNote("b", 0L, 10L, 0L, OffsetDateTime.now());
        // highCopies: 6.0, highViews: 4.0
        assertThat(PublicNotesScoringUtils.computeScore(highCopies))
                .isGreaterThan(PublicNotesScoringUtils.computeScore(highViews));
    }

    @Test
    void computeScore_treatsNullCountsAsZero() {
        NoteListItemResponse note = makeNote("id", null, null, null, OffsetDateTime.now());
        assertThat(PublicNotesScoringUtils.computeScore(note)).isZero();
    }

    @Test
    void computeScore_zeroCountsProduceZeroScore() {
        NoteListItemResponse note = makeNote("id", 0L, 0L, 0L, OffsetDateTime.now());
        assertThat(PublicNotesScoringUtils.computeScore(note)).isZero();
    }

    // --- sortByFeatured ---

    @Test
    void sortByFeatured_sortsHighestScoreFirst() {
        OffsetDateTime now = OffsetDateTime.now();
        NoteListItemResponse low = makeNote("low", 1L, 1L, 0L, now.minusDays(1));
        NoteListItemResponse high = makeNote("high", 10L, 10L, 0L, now);

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByFeatured(List.of(low, high));

        assertThat(sorted).extracting(NoteListItemResponse::id).containsExactly("high", "low");
    }

    @Test
    void sortByFeatured_tiebreaksByNewestCreatedAt() {
        OffsetDateTime base = OffsetDateTime.now();
        NoteListItemResponse older = makeNote("older", 5L, 5L, 0L, base.minusDays(2));
        NoteListItemResponse newer = makeNote("newer", 5L, 5L, 0L, base.minusDays(1));

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByFeatured(List.of(older, newer));

        assertThat(sorted).extracting(NoteListItemResponse::id).containsExactly("newer", "older");
    }

    @Test
    void sortByFeatured_emptyListReturnsEmpty() {
        assertThat(PublicNotesScoringUtils.sortByFeatured(List.of())).isEmpty();
    }

    @Test
    void sortByFeatured_singleItemReturnsSingleItem() {
        NoteListItemResponse note = makeNote("only", 3L, 2L, 0L, OffsetDateTime.now());
        assertThat(PublicNotesScoringUtils.sortByFeatured(List.of(note)))
                .extracting(NoteListItemResponse::id).containsExactly("only");
    }

    // --- sortByPopular ---

    @Test
    void sortByPopular_sortsByCopyCountDescFirst() {
        OffsetDateTime now = OffsetDateTime.now();
        NoteListItemResponse fewerCopies = makeNote("fewer", 2L, 100L, 0L, now);
        NoteListItemResponse moreCopies = makeNote("more", 20L, 1L, 0L, now);

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByPopular(List.of(fewerCopies, moreCopies));

        assertThat(sorted).extracting(NoteListItemResponse::id).containsExactly("more", "fewer");
    }

    @Test
    void sortByPopular_tiebreaksByViewCountDesc() {
        OffsetDateTime now = OffsetDateTime.now();
        NoteListItemResponse fewerViews = makeNote("fewerViews", 5L, 3L, 0L, now);
        NoteListItemResponse moreViews = makeNote("moreViews", 5L, 10L, 0L, now);

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByPopular(List.of(fewerViews, moreViews));

        assertThat(sorted).extracting(NoteListItemResponse::id).containsExactly("moreViews", "fewerViews");
    }

    @Test
    void sortByPopular_tiebreaksByNewestCreatedAtWhenCopiesAndViewsEqual() {
        OffsetDateTime base = OffsetDateTime.now();
        NoteListItemResponse older = makeNote("older", 5L, 5L, 0L, base.minusDays(2));
        NoteListItemResponse newer = makeNote("newer", 5L, 5L, 0L, base.minusDays(1));

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByPopular(List.of(older, newer));

        assertThat(sorted).extracting(NoteListItemResponse::id).containsExactly("newer", "older");
    }

    @Test
    void sortByPopular_treatsNullCountsAsZero() {
        OffsetDateTime now = OffsetDateTime.now();
        NoteListItemResponse nullCounts = makeNote("null", null, null, null, now.minusDays(1));
        NoteListItemResponse withCopies = makeNote("withCopies", 1L, 0L, 0L, now);

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByPopular(List.of(nullCounts, withCopies));

        assertThat(sorted).extracting(NoteListItemResponse::id).containsExactly("withCopies", "null");
    }

    @Test
    void sortByPopular_emptyListReturnsEmpty() {
        assertThat(PublicNotesScoringUtils.sortByPopular(List.of())).isEmpty();
    }

    // --- sortByRecent ---

    @Test
    void sortByRecent_sortsNewestFirst() {
        OffsetDateTime base = OffsetDateTime.now();
        NoteListItemResponse oldest = makeNote("oldest", 0L, 0L, 0L, base.minusDays(3));
        NoteListItemResponse middle = makeNote("middle", 0L, 0L, 0L, base.minusDays(2));
        NoteListItemResponse newest = makeNote("newest", 0L, 0L, 0L, base.minusDays(1));

        List<NoteListItemResponse> sorted =
                PublicNotesScoringUtils.sortByRecent(List.of(oldest, newest, middle));

        assertThat(sorted).extracting(NoteListItemResponse::id).containsExactly("newest", "middle", "oldest");
    }

    @Test
    void sortByRecent_emptyListReturnsEmpty() {
        assertThat(PublicNotesScoringUtils.sortByRecent(List.of())).isEmpty();
    }

    @Test
    void sortByRecent_singleItemReturnsSingleItem() {
        NoteListItemResponse note = makeNote("only", 0L, 0L, 0L, OffsetDateTime.now());
        assertThat(PublicNotesScoringUtils.sortByRecent(List.of(note)))
                .extracting(NoteListItemResponse::id).containsExactly("only");
    }

    // --- helpers ---

    private NoteListItemResponse makeNote(
            String id,
            Long copyCount,
            Long viewCount,
            Long shareCount,
            OffsetDateTime createdAt
    ) {
        return new NoteListItemResponse(
                id,
                "owner-id",
                "Title",
                null,
                null,
                "Subject",
                List.of(),
                "preview",
                null,
                "PUBLIC",
                null,
                "GENERATED",
                0,
                copyCount,
                shareCount,
                viewCount,
                "Author",
                false,
                false,
                createdAt,
                createdAt,
                null,
                false
        );
    }
}
