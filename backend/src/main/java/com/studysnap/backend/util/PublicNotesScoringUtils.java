package com.studysnap.backend.util;

import com.studysnap.backend.dto.NoteListItemResponse;

import java.util.Comparator;
import java.util.List;

/**
 * Utility methods for ranking public notes in discovery mode.
 * All sorting is computed in-memory from existing engagement signals.
 */
public final class PublicNotesScoringUtils {
    private static final int FEATURED_COPY_WEIGHT = 3;
    private static final int FEATURED_VIEW_WEIGHT = 1;
    private static final long POPULAR_MIN_COPIES = 3L;
    private static final long POPULAR_MIN_VIEWS = 20L;
    private static final String FEATURED_READY_STATUS = "STUDY_PACK_READY";

    private PublicNotesScoringUtils() {}

    /**
     * v1 Featured score for a note.
     * score = views + (copies × 3)
     */
    public static double computeScore(NoteListItemResponse note) {
        long copies = metricValue(note.copyCount());
        long views = metricValue(note.viewCount());
        return (copies * FEATURED_COPY_WEIGHT) + (views * FEATURED_VIEW_WEIGHT);
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
     * Filter to Featured-eligible notes, then sort by score desc,
     * copies desc, views desc, and newest createdAt desc.
     */
    public static List<NoteListItemResponse> sortByFeatured(List<NoteListItemResponse> notes) {
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
                        Comparator.comparingDouble(PublicNotesScoringUtils::computeScore).reversed()
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
        Comparator<NoteListItemResponse> byCreatedAt =
                Comparator.comparing(NoteListItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        return notes.stream()
                .filter(PublicNotesScoringUtils::isPopular)
                .sorted(byCopies.thenComparing(byViews).thenComparing(byCreatedAt))
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
