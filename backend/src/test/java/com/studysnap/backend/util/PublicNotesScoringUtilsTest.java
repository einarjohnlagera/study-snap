package com.studysnap.backend.util;

import com.studysnap.backend.dto.NoteListItemResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PublicNotesScoringUtilsTest {

    private static final Instant NOW = Instant.parse("2026-05-12T00:00:00Z");

    @Test
    void computeScore_usesViewsCopiesAndLikesWeights() {
        // Note created at NOW → decay factor = 1.0 → score = (4×3) + (3×2) + (7×1) = 25
        NoteListItemResponse note = makeNote("id", 4L, 3L, 7L, OffsetDateTime.parse("2026-05-12T00:00:00Z"));

        assertThat(PublicNotesScoringUtils.computeScore(note, NOW)).isCloseTo(25.0, within(0.001));
    }

    @Test
    void computeScore_treatsNullCountsAsZero() {
        NoteListItemResponse note = makeNote("id", null, null, null, OffsetDateTime.parse("2026-05-12T00:00:00Z"));

        assertThat(PublicNotesScoringUtils.computeScore(note, NOW)).isZero();
    }

    @Test
    void computeScore_reducesScoreForOlderNotes() {
        // 30-day-old note → decay factor = 1/(1+30/30) = 0.5 → score = 10×0.5 = 5
        NoteListItemResponse freshNote = makeNote("fresh", 0L, 0L, 10L, OffsetDateTime.parse("2026-05-12T00:00:00Z"));
        NoteListItemResponse oldNote = makeNote("old", 0L, 0L, 10L, OffsetDateTime.parse("2026-04-12T00:00:00Z"));

        double freshScore = PublicNotesScoringUtils.computeScore(freshNote, NOW);
        double oldScore = PublicNotesScoringUtils.computeScore(oldNote, NOW);

        assertThat(freshScore).isCloseTo(10.0, within(0.001));
        assertThat(oldScore).isCloseTo(5.0, within(0.01));
    }

    @Test
    void isFeaturedEligible_requiresStudyReadySummaryQuizAndPreview() {
        assertThat(PublicNotesScoringUtils.isFeaturedEligible(
                makeNote("eligible", 2L, 0L, 5L, OffsetDateTime.now())
        )).isTrue();

        assertThat(PublicNotesScoringUtils.isFeaturedEligible(
                makeNote("missing-summary", 2L, 0L, 5L, OffsetDateTime.now(), "PUBLIC", "STUDY_PACK_READY", "preview", " ", 2)
        )).isFalse();
        assertThat(PublicNotesScoringUtils.isFeaturedEligible(
                makeNote("missing-quiz", 2L, 0L, 5L, OffsetDateTime.now(), "PUBLIC", "STUDY_PACK_READY", "preview", "summary", 0)
        )).isFalse();
        assertThat(PublicNotesScoringUtils.isFeaturedEligible(
                makeNote("draft", 2L, 0L, 5L, OffsetDateTime.now(), "PUBLIC", "DRAFT", "preview", "summary", 2)
        )).isFalse();
    }

    @Test
    void sortByFeatured_filtersIneligibleAndRanksByDecayedScore() {
        // highScore note: copyCount=10, viewCount=20, fresh → large decayed score
        NoteListItemResponse high = makeNote("high", 10L, 0L, 20L, OffsetDateTime.parse("2026-05-11T00:00:00Z"));
        // midScore note: copyCount=3, viewCount=5, fresh → smaller decayed score
        NoteListItemResponse mid = makeNote("mid", 3L, 0L, 5L, OffsetDateTime.parse("2026-05-11T00:00:00Z"));
        // ineligible draft with huge raw engagement — must be excluded
        NoteListItemResponse ineligible = makeNote(
                "ineligible", 100L, 0L, 1000L,
                OffsetDateTime.parse("2026-05-12T00:00:00Z"),
                "PUBLIC", "DRAFT", "preview", "summary", 0
        );

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByFeatured(
                List.of(mid, ineligible, high), NOW
        );

        assertThat(sorted).extracting(NoteListItemResponse::id)
                .containsExactly("high", "mid");
    }

    @Test
    void sortByFeatured_ranksFresherNotesHigherWhenRawScoresAreEqual() {
        // Same raw engagement (viewCount=10 each) but different ages → fresher wins
        NoteListItemResponse fresh = makeNote("fresh", 0L, 0L, 10L, OffsetDateTime.parse("2026-05-11T00:00:00Z"));
        NoteListItemResponse stale = makeNote("stale", 0L, 0L, 10L, OffsetDateTime.parse("2026-02-10T00:00:00Z"));

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByFeatured(List.of(stale, fresh), NOW);

        assertThat(sorted).extracting(NoteListItemResponse::id).containsExactly("fresh", "stale");
    }

    @Test
    void sortByFeatured_tiebreaksByCopiesWhenDecayFactorsAreEqual() {
        // Same createdAt → same decay factor → tiebreak by copies desc
        OffsetDateTime sameTime = OffsetDateTime.parse("2026-05-12T00:00:00Z");
        // Both have raw score 14 (copies×3 + views)
        NoteListItemResponse moreCopies = makeNote("moreCopies", 3L, 0L, 5L, sameTime);
        NoteListItemResponse moreViews = makeNote("moreViews", 1L, 0L, 11L, sameTime);

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByFeatured(
                List.of(moreViews, moreCopies), NOW
        );

        assertThat(sorted).extracting(NoteListItemResponse::id).containsExactly("moreCopies", "moreViews");
    }

    @Test
    void isPopular_acceptsCopiesOrViewsThreshold() {
        assertThat(PublicNotesScoringUtils.isPopular(makeNote("copies", 3L, 0L, 0L, OffsetDateTime.now()))).isTrue();
        assertThat(PublicNotesScoringUtils.isPopular(makeNote("views", 0L, 0L, 20L, OffsetDateTime.now()))).isTrue();
        assertThat(PublicNotesScoringUtils.isPopular(makeNote("below", 2L, 0L, 19L, OffsetDateTime.now()))).isFalse();
    }

    @Test
    void sortByPopular_filtersBelowThresholdAndSortsByCopiesViewsLikesThenCreatedAt() {
        OffsetDateTime base = OffsetDateTime.now();
        NoteListItemResponse moreCopies = makeNote("moreCopies", 7L, 0L, 1L, base.minusDays(3));
        NoteListItemResponse moreLikes = makeNote("moreLikes", 3L, 9L, 30L, base.minusDays(2));
        NoteListItemResponse newer = makeNote("newer", 3L, 2L, 30L, base.minusDays(1));
        NoteListItemResponse belowThreshold = makeNote("below", 2L, 0L, 19L, base);

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByPopular(
                List.of(newer, belowThreshold, moreLikes, moreCopies)
        );

        assertThat(sorted).extracting(NoteListItemResponse::id)
                .containsExactly("moreCopies", "moreLikes", "newer");
    }

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

    private NoteListItemResponse makeNote(String id, Long copyCount, Long likeCount, Long viewCount, OffsetDateTime createdAt) {
        return makeNote(id, copyCount, likeCount, viewCount, createdAt, "PUBLIC", "STUDY_PACK_READY", "preview", "summary", 2);
    }

    private NoteListItemResponse makeNote(
            String id,
            Long copyCount,
            Long likeCount,
            Long viewCount,
            OffsetDateTime createdAt,
            String visibility,
            String studyPackStatus,
            String contentPreview,
            String summaryPreview,
            Integer quizCount
    ) {
        return new NoteListItemResponse(
                id,
                "owner-id",
                "Title",
                null,
                null,
                "STUDENT",
                "Subject",
                List.of(),
                contentPreview,
                summaryPreview,
                visibility,
                "study-pack-id",
                studyPackStatus,
                quizCount,
                copyCount,
                likeCount,
                0L,
                viewCount,
                "Author",
                "author",
                false,
                false,
                createdAt,
                createdAt,
                null,
                null,
                null,
                null,
                false,
                false
        );
    }
}
