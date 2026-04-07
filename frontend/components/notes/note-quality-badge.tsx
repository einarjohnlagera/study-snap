import { computeQualityBadges, type QualityBadgeDef } from "@/lib/note-quality-badges";

const BADGE_STYLES: Record<QualityBadgeDef["kind"], string> = {
  "high-quality":
    "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300",
  popular:
    "border-orange-500/40 bg-orange-500/10 text-orange-700 dark:text-orange-300",
};

const BADGE_ICONS: Record<QualityBadgeDef["kind"], string> = {
  "high-quality": "⭐",
  popular: "🔥",
};

/**
 * Renders quality signal badges (High Quality, Popular) for a public note
 * card. Renders nothing when the note doesn't qualify for any badge.
 *
 * Use on public-facing surfaces only (Public Library, Public Profile, subject
 * pages). Do NOT use on private Library cards — they have no public metrics.
 */
export function NoteQualityBadges({
  copyCount,
  viewCount,
}: Readonly<{
  copyCount?: number | null;
  viewCount?: number | null;
}>) {
  const badges = computeQualityBadges({ copyCount, viewCount });

  if (badges.length === 0) {
    return null;
  }

  return (
    <>
      {badges.map((badge) => (
        <span
          key={badge.kind}
          className={`inline-flex items-center gap-1 rounded-full border px-2 py-1 text-xs font-medium ${BADGE_STYLES[badge.kind]}`}
        >
          <span aria-hidden="true">{BADGE_ICONS[badge.kind]}</span>
          {badge.label}
        </span>
      ))}
    </>
  );
}
