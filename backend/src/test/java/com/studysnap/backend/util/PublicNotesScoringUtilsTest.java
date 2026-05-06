package com.studysnap.backend.util;

import com.studysnap.backend.dto.NoteListItemResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PublicNotesScoringUtilsTest {

    @Test
    void computeScore_usesViewsCopiesAndLikesWeights() {
        NoteListItemResponse note = makeNote("id", 4L, 3L, 7L, OffsetDateTime.now());

        assertThat(PublicNotesScoringUtils.computeScore(note)).isCloseTo(25.0, within(0.001));
    }

    @Test
    void computeScore_treatsNullCountsAsZero() {
        NoteListItemResponse note = makeNote("id", null, null, null, OffsetDateTime.now());

        assertThat(PublicNotesScoringUtils.computeScore(note)).isZero();
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
    void sortByFeatured_filtersIneligibleNotesAndAppliesTieBreakers() {
        OffsetDateTime base = OffsetDateTime.now();
        NoteListItemResponse moreCopies = makeNote("moreCopies", 3L, 0L, 5L, base.minusDays(3));
        NoteListItemResponse moreViews = makeNote("moreViews", 1L, 0L, 11L, base.minusDays(2));
        NoteListItemResponse newer = makeNote("newer", 2L, 0L, 8L, base.minusDays(1));
        NoteListItemResponse ineligible = makeNote(
                "ineligible",
                20L,
                0L,
                100L,
                base,
                "PUBLIC",
                "DRAFT",
                "preview",
                "summary",
                0
        );

        List<NoteListItemResponse> sorted = PublicNotesScoringUtils.sortByFeatured(
                List.of(newer, ineligible, moreViews, moreCopies)
        );

        assertThat(sorted).extracting(NoteListItemResponse::id)
                .containsExactly("moreCopies", "newer", "moreViews");
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
