/**
 * Quality badge logic for public note cards.
 *
 * Badges help users quickly identify strong notes in the Public Library,
 * Public Profile, and public subject pages. At most 2 badges are shown per
 * note so the card layout stays clean on mobile.
 *
 * Priority order: High Quality > Popular > New
 * Max badges: 2
 */

export const QUALITY_THRESHOLDS = {
  /** Minimum copies for a note to qualify as High Quality (alongside views). */
  HIGH_QUALITY_MIN_COPIES: 5,
  /** Minimum views for a note to qualify as High Quality (alongside copies). */
  HIGH_QUALITY_MIN_VIEWS: 10,
  /** Minimum copies OR views (either alone) for a note to qualify as Popular. */
  POPULAR_MIN_COPIES: 10,
  POPULAR_MIN_VIEWS: 20,
  /** How many days after creation a note is considered New. */
  NEW_WITHIN_DAYS: 7,
} as const;

export type QualityBadgeKind = "high-quality" | "popular" | "new";

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
 * - 🆕 New: note created within the last NEW_WITHIN_DAYS days
 *
 * Returns at most 2 badges. Null/undefined counts are treated as 0.
 */
export function computeQualityBadges(note: {
  copyCount?: number | null;
  viewCount?: number | null;
  createdAt?: string | null;
}): QualityBadgeDef[] {
  const copies = note.copyCount ?? 0;
  const views = note.viewCount ?? 0;

  const isHighQuality =
    copies >= QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES &&
    views >= QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS;

  const isPopular =
    copies >= QUALITY_THRESHOLDS.POPULAR_MIN_COPIES ||
    views >= QUALITY_THRESHOLDS.POPULAR_MIN_VIEWS;

  const isNew = (() => {
    if (!note.createdAt) return false;
    const created = new Date(note.createdAt).getTime();
    if (Number.isNaN(created)) return false;
    const diffMs = Date.now() - created;
    return diffMs >= 0 && diffMs <= QUALITY_THRESHOLDS.NEW_WITHIN_DAYS * 24 * 60 * 60 * 1000;
  })();

  const badges: QualityBadgeDef[] = [];

  if (isHighQuality) {
    badges.push({ kind: "high-quality", label: "High Quality" });
  } else if (isPopular) {
    // Popular is shown only when High Quality hasn't already claimed the engagement signal slot
    badges.push({ kind: "popular", label: "Popular" });
  }

  if (isNew) {
    badges.push({ kind: "new", label: "New" });
  }

  return badges.slice(0, 2);
}
