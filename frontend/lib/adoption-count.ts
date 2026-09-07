/**
 * Display rules for an Official Review Set's adoption count (`v0.129.0`, Stage 2 of
 * `docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md`).
 *
 * <p>⚠️ THE THRESHOLD IS DISPLAY POLICY ONLY. The API returns the EXACT count and this module never
 * changes it — it decides only whether to render one. `RELEASES.md`, `CLAUDE.md` and `ROADMAP.md` all
 * state the queried count stays exact, so nothing here may clamp, bucket or null a value.
 *
 * <p>⚠️ Below the threshold the count is OMITTED ENTIRELY — never "fewer than 5", never a range.
 * Either of those still discloses the smallness the threshold exists to hide, which would defeat it.
 *
 * <p>⚠️ Do NOT re-derive the threshold from a distribution read. It is an owner decision made
 * 2026-09-07, not a computed value, and a later read showing different counts does not change it.
 */

/** Owner decision, 2026-09-07. Show a count at this many adopters or more; below it, show nothing. */
export const ADOPTION_COUNT_DISPLAY_THRESHOLD = 5;

const COMPACT_THOUSAND = 1000;

export function shouldShowAdoptionCount(adoptionCount: number | null | undefined): boolean {
  return typeof adoptionCount === "number"
    && Number.isFinite(adoptionCount)
    && adoptionCount >= ADOPTION_COUNT_DISPLAY_THRESHOLD;
}

/**
 * Compact form for cards: `40`, `1.2K`, `12K`.
 *
 * <p>Truncates rather than rounds, so the label never claims more adopters than exist — `1999`
 * reads `1.9K`, not `2K`.
 */
export function formatCompactAdoptionCount(adoptionCount: number): string {
  if (adoptionCount < COMPACT_THOUSAND) {
    return String(adoptionCount);
  }
  const thousands = adoptionCount / COMPACT_THOUSAND;
  const truncatedToOneDecimal = Math.floor(thousands * 10) / 10;
  return Number.isInteger(truncatedToOneDecimal)
    ? `${truncatedToOneDecimal}K`
    : `${truncatedToOneDecimal.toFixed(1)}K`;
}

/**
 * Card label. Returns null when the count is below the threshold or absent.
 *
 * <p>⚠️ The wording is "adopted", never "N learners adopted this" — the repo cannot prove every
 * counted owner is semantically a learner, and `ProfileType` must not be used to filter adoptions to
 * justify the word.
 */
export function getCompactAdoptionLabel(adoptionCount: number | null | undefined): string | null {
  if (!shouldShowAdoptionCount(adoptionCount)) {
    return null;
  }
  return `${formatCompactAdoptionCount(adoptionCount as number)} adopted`;
}

/** Fuller wording for the preview/detail surface, where there is room for a sentence. */
export function getDetailedAdoptionLabel(adoptionCount: number | null | undefined): string | null {
  if (!shouldShowAdoptionCount(adoptionCount)) {
    return null;
  }
  return `Adopted by ${(adoptionCount as number).toLocaleString("en-US")} study libraries`;
}
