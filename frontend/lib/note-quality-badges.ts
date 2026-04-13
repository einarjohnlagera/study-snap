import { PUBLIC_LIBRARY_RANKING } from "@/lib/public-library-discovery";

/**
 * Quality badge logic for public note cards.
 *
 * Badges help users quickly identify strong notes in the Public Library,
 * Public Profile, and public subject pages. At most 2 badges are shown per
 * note so the card layout stays clean on mobile.
 *
 * Priority order: High Quality > Popular
 * Max badges: 2
 */

export const QUALITY_THRESHOLDS = {
  /** Minimum copies for a note to qualify as High Quality (alongside views). */
  HIGH_QUALITY_MIN_COPIES: 5,
  /** Minimum views for a note to qualify as High Quality (alongside copies). */
  HIGH_QUALITY_MIN_VIEWS: 10,
  /** Minimum copies OR views (either alone) for a note to qualify as Popular. */
  POPULAR_MIN_COPIES: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_COPIES,
  POPULAR_MIN_VIEWS: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_VIEWS,
} as const;

export type QualityBadgeKind = "high-quality" | "popular";

export interface QualityBadgeDef {
  kind: QualityBadgeKind;
  label: string;
}

/**
 * Compute the quality badges applicable to a note.
 *
 * Rules:
 * - ⭐ High Quality: copies >= HIGH_QUALITY_MIN_COPIES AND views >= HIGH_QUALITY_MIN_VIEWS
 * - 🔥 Popular: copies >= POPULAR_MIN_COPIES OR views >= POPULAR_MIN_VIEWS
 *   (skipped if High Quality is already shown — High Quality is the stronger signal)
 *
 * Returns at most 2 badges. Null/undefined counts are treated as 0.
 */
export function computeQualityBadges(note: {
  copyCount?: number | null;
  viewCount?: number | null;
}): QualityBadgeDef[] {
  const copies = note.copyCount ?? 0;
  const views = note.viewCount ?? 0;

  const isHighQuality =
    copies >= QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES &&
    views >= QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS;

  const isPopular =
    copies >= QUALITY_THRESHOLDS.POPULAR_MIN_COPIES ||
    views >= QUALITY_THRESHOLDS.POPULAR_MIN_VIEWS;

  const badges: QualityBadgeDef[] = [];

  if (isHighQuality) {
    badges.push({ kind: "high-quality", label: "High Quality" });
  } else if (isPopular) {
    // Popular is shown only when High Quality hasn't already claimed the engagement signal slot
    badges.push({ kind: "popular", label: "Popular" });
  }

  return badges.slice(0, 2);
}
