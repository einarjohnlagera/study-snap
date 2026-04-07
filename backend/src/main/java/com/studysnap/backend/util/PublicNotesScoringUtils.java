package com.studysnap.backend.util;

import com.studysnap.backend.dto.NoteListItemResponse;

import java.util.Comparator;
import java.util.List;

/**
 * Utility methods for ranking public notes in discovery mode.
 * All sorting is computed in-memory from existing engagement signals — no DB persistence.
 */
public final class PublicNotesScoringUtils {

    private PublicNotesScoringUtils() {}

    /**
     * Weighted engagement score for a note.
     * score = (copies × 0.6) + (views × 0.4)
     */
    public static double computeScore(NoteListItemResponse note) {
        long copies = note.copyCount() == null ? 0 : note.copyCount();
        long views = note.viewCount() == null ? 0 : note.viewCount();
        return (copies * 0.6) + (views * 0.4);
    }

    /**
     * Sort by score desc, tiebreak by newest createdAt desc.
     */
    public static List<NoteListItemResponse> sortByFeatured(List<NoteListItemResponse> notes) {
        return notes.stream()
                .sorted(
                        Comparator.comparingDouble(PublicNotesScoringUtils::computeScore).reversed()
                                .thenComparing(Comparator.comparing(NoteListItemResponse::createdAt,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                )
                )
                .toList();
    }

    /**
     * Sort by copy count desc, then view count desc, then newest createdAt desc.
     */
    public static List<NoteListItemResponse> sortByPopular(List<NoteListItemResponse> notes) {
        Comparator<NoteListItemResponse> byCopies =
                Comparator.<NoteListItemResponse, Long>comparing(n -> n.copyCount() == null ? 0L : n.copyCount(),
                        Comparator.reverseOrder());
        Comparator<NoteListItemResponse> byViews =
                Comparator.<NoteListItemResponse, Long>comparing(n -> n.viewCount() == null ? 0L : n.viewCount(),
                        Comparator.reverseOrder());
        Comparator<NoteListItemResponse> byCreatedAt =
                Comparator.comparing(NoteListItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        return notes.stream()
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
}
