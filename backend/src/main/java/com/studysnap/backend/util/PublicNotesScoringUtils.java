package com.studysnap.backend.util;

import com.studysnap.backend.dto.NoteListItemResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Utility methods for ranking public notes in discovery mode.
 * All sorting is computed in-memory from existing engagement signals.
 */
public final class PublicNotesScoringUtils {
    /**
     * ⚠️ These constants are the SINGLE definition of the public-library ranking rule, and
     * {@code PublicLibraryRepositoryImpl} builds its ranking SQL from them rather than repeating the
     * numbers. v0.119.1 moved the ordering itself into SQL so an anonymous {@code /notes/public}
     * request stops loading the entire public catalog to rank it in Java; this class stays the
     * readable statement of the rule the SQL encodes, and {@code PublicNotesScoringUtilsTest} stays
     * its executable specification. Change a weight here and the SQL follows automatically — do NOT
     * hardcode a second copy in the repository.
     */
    public static final int FEATURED_COPY_WEIGHT = 3;
    public static final int FEATURED_LIKE_WEIGHT = 2;
    public static final int FEATURED_VIEW_WEIGHT = 1;
    public static final long POPULAR_MIN_COPIES = 3L;
    public static final long POPULAR_MIN_VIEWS = 20L;
    public static final double FEATURED_DECAY_HALF_LIFE_DAYS = 30.0;
    public static final double FEATURED_DECAY_MIN_FACTOR = 0.1;
    private static final String FEATURED_READY_STATUS = "STUDY_PACK_READY";

    private PublicNotesScoringUtils() {}

    private static double computeDecayFactor(NoteListItemResponse note, Instant now) {
        if (note.createdAt() == null) {
            return 1.0;
        }
        long daysSince = ChronoUnit.DAYS.between(note.createdAt().toInstant(), now);
        double factor = 1.0 / (1.0 + Math.max(0, daysSince) / FEATURED_DECAY_HALF_LIFE_DAYS);
        return Math.max(factor, FEATURED_DECAY_MIN_FACTOR);
    }

    /**
     * v2 Featured score: engagement × age-decay factor.
     * score = (views + copies×3 + likes×2) × (1 / (1 + daysSince/30))
     * Floor: 10% of raw score to prevent very old notes from vanishing entirely.
     */
    public static double computeScore(NoteListItemResponse note) {
        return computeScore(note, Instant.now());
    }

    public static double computeScore(NoteListItemResponse note, Instant now) {
        long copies = metricValue(note.copyCount());
        long likes = metricValue(note.likeCount());
        long views = metricValue(note.viewCount());
        double baseScore = ((double) copies * FEATURED_COPY_WEIGHT) + (likes * FEATURED_LIKE_WEIGHT) + (views * FEATURED_VIEW_WEIGHT);
        return baseScore * computeDecayFactor(note, now);
    }

    public static boolean isFeaturedEligible(NoteListItemResponse note) {
        return "PUBLIC".equals(note.visibility())
                && FEATURED_READY_STATUS.equals(note.studyPackStatus())
                && hasMeaningfulText(note.summaryPreview())
                && note.quizCount() != null
                && note.quizCount() > 0
                && hasMeaningfulText(note.contentPreview());
    }

    public static boolean isPopular(NoteListItemResponse note) {
        return metricValue(note.copyCount()) >= POPULAR_MIN_COPIES
                || metricValue(note.viewCount()) >= POPULAR_MIN_VIEWS;
    }

    /**
     * Filter to Featured-eligible notes, then sort by decay-adjusted score desc,
     * copies desc, views desc, and newest createdAt desc.
     */
    public static List<NoteListItemResponse> sortByFeatured(List<NoteListItemResponse> notes) {
        return sortByFeatured(notes, Instant.now());
    }

    public static List<NoteListItemResponse> sortByFeatured(List<NoteListItemResponse> notes, Instant now) {
        Comparator<NoteListItemResponse> byCopies =
                Comparator.comparing(n -> metricValue(n.copyCount()),
                        Comparator.reverseOrder());
        Comparator<NoteListItemResponse> byViews =
                Comparator.comparing(n -> metricValue(n.viewCount()),
                        Comparator.reverseOrder());
        Comparator<NoteListItemResponse> byCreatedAt =
                Comparator.comparing(NoteListItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        return notes.stream()
                .filter(PublicNotesScoringUtils::isFeaturedEligible)
                .sorted(
                        Comparator.comparingDouble((NoteListItemResponse n) -> computeScore(n, now)).reversed()
                                .thenComparing(byCopies)
                                .thenComparing(byViews)
                                .thenComparing(byCreatedAt)
                )
                .toList();
    }

    /**
     * Filter to Popular-eligible notes, then sort by copy count desc,
     * view count desc, and newest createdAt desc.
     */
    public static List<NoteListItemResponse> sortByPopular(List<NoteListItemResponse> notes) {
        Comparator<NoteListItemResponse> byCopies =
                Comparator.comparing(n -> metricValue(n.copyCount()),
                        Comparator.reverseOrder());
        Comparator<NoteListItemResponse> byViews =
                Comparator.comparing(n -> metricValue(n.viewCount()),
                        Comparator.reverseOrder());
        Comparator<NoteListItemResponse> byLikes =
                Comparator.comparing(n -> metricValue(n.likeCount()),
                        Comparator.reverseOrder());
        Comparator<NoteListItemResponse> byCreatedAt =
                Comparator.comparing(NoteListItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        return notes.stream()
                .filter(PublicNotesScoringUtils::isPopular)
                .sorted(byCopies.thenComparing(byViews).thenComparing(byLikes).thenComparing(byCreatedAt))
                .toList();
    }

    /**
     * Sort by createdAt desc (most recently added first).
     */
    public static List<NoteListItemResponse> sortByRecent(List<NoteListItemResponse> notes) {
        return notes.stream()
                .sorted(Comparator.comparing(NoteListItemResponse::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static long metricValue(Long value) {
        return value == null ? 0L : value;
    }

    private static boolean hasMeaningfulText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
