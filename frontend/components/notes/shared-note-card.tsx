import type { ReactNode } from "react";
import { Copy, Eye } from "lucide-react";
import { SubjectBadge } from "@/components/notes/subject-badge";
import { cn } from "@/lib/utils";

type SharedNoteCardProps = {
  title: string | null;
  courseProgram?: string | null;
  applicablePrograms?: string[] | null;
  subject: string | null;
  tags: string[];
  contentPreview: string;
  summaryPreview?: string | null;
  copyCount?: number | null;
  viewCount?: number | null;
  stateBadge?: ReactNode;
  metadataBadges?: ReactNode;
  titleTrailing?: ReactNode;
  metricsTrailing?: ReactNode;
  footer?: ReactNode;
  tagDisplayLimit?: number;
  previewLines?: 2 | 3;
};

export type CardExcerpt =
  | { kind: "note"; text: string }
  | { kind: "summary"; text: string }
  | { kind: "none" };

// Below this, the note body is a stub, not a real preview — fall back to the summary instead.
const MIN_NOTE_PREVIEW_LENGTH = 40;

function normalizeTags(tags: string[] | null | undefined): string[] {
  if (!Array.isArray(tags)) {
    return [];
  }
  return tags
    .map((tag) => tag?.trim())
    .filter((tag): tag is string => Boolean(tag && tag.length > 0));
}

function resolveCardPrograms(
  applicablePrograms: string[] | null | undefined,
  courseProgram: string | null | undefined,
): string[] {
  const joinedPrograms = Array.isArray(applicablePrograms)
    ? [...new Set(applicablePrograms.map((program) => program?.trim()).filter(Boolean))]
    : [];
  if (joinedPrograms.length > 0) {
    return joinedPrograms;
  }
  const legacyProgram = courseProgram?.trim();
  return legacyProgram ? [legacyProgram] : [];
}

/**
 * One program is worth naming — it is unambiguous and it is the common case. Several are not: on a
 * card the useful signal is that the note is broadly applicable, and any truncated list has to pick
 * which names to drop on alphabetical accident. The full read-only list lives on Note Detail.
 */
function resolveProgramSummary(programs: string[]): string | null {
  if (programs.length === 0) {
    return null;
  }
  if (programs.length === 1) {
    return programs[0];
  }
  return `Applies to ${programs.length} programs`;
}

/**
 * The note is the source object; the summary is a fallback preview of a derivative.
 * One excerpt per card, never both — see docs/features/public-library.md.
 * Exported so any surface previewing a note (not just SharedNoteCard itself) resolves the same way.
 */
export function resolveCardExcerpt(contentPreview: string | null | undefined, summaryPreview?: string | null): CardExcerpt {
  const trimmedNote = (contentPreview ?? "").trim().replace(/\s+/g, " ");
  if (trimmedNote.length >= MIN_NOTE_PREVIEW_LENGTH) {
    return { kind: "note", text: trimmedNote };
  }
  const trimmedSummary = (summaryPreview ?? "").trim().replace(/\s+/g, " ");
  if (trimmedSummary.length > 0) {
    return { kind: "summary", text: trimmedSummary };
  }
  return { kind: "none" };
}

export function SharedNoteCard({
  title,
  courseProgram,
  applicablePrograms,
  subject,
  tags,
  contentPreview,
  summaryPreview,
  copyCount,
  viewCount,
  stateBadge,
  metadataBadges,
  titleTrailing,
  metricsTrailing,
  footer,
  tagDisplayLimit,
  previewLines = 3,
}: Readonly<SharedNoteCardProps>) {
  const normalizedTags = normalizeTags(tags);
  const programs = resolveCardPrograms(applicablePrograms, courseProgram);
  const programSummary = resolveProgramSummary(programs);
  const hasDiscoveryMetrics = typeof viewCount === "number" || typeof copyCount === "number";
  const hasMetricsRow = hasDiscoveryMetrics || Boolean(metricsTrailing);
  const visibleTags = tagDisplayLimit ? normalizedTags.slice(0, tagDisplayLimit) : normalizedTags;
  const hiddenTagCount = Math.max(0, normalizedTags.length - visibleTags.length);
  const excerpt = resolveCardExcerpt(contentPreview, summaryPreview);

  return (
    <div className="flex h-full min-w-0 flex-col justify-between gap-4">
      <div className="space-y-4">
        {/* TOP ROW: Subject badge alone — the note's identity, above title */}
        <div className="flex flex-wrap items-center gap-2">
          <SubjectBadge subject={subject} />
        </div>

        <div className="space-y-3">
          <div className="flex items-start justify-between gap-3">
            <h3 className="line-clamp-2 min-w-0 flex-1 text-base font-semibold sm:text-lg">
              {title?.trim() || "Untitled note"}
            </h3>
            {titleTrailing ? (
              <div className="shrink-0 text-foreground/55">
                {titleTrailing}
              </div>
            ) : null}
          </div>

          {/* Reach, not identity: a count rather than names. Names beside the Subject badge read as a
              second identity, have no delimiter between them, and are largely redundant in a
              program-filtered view. The specific list lives on Note Detail. */}
          {programSummary ? (
            <p className="text-xs font-medium text-foreground/60">{programSummary}</p>
          ) : null}

          {/* BELOW TITLE: Study Pack Ready badge (stateBadge) + quality badges */}
          {(stateBadge || metadataBadges) ? (
            <div className="flex flex-wrap items-center gap-2">
              {stateBadge}
              {metadataBadges}
            </div>
          ) : null}

          {excerpt.kind !== "none" ? (
            <div className="space-y-1">
              {excerpt.kind === "summary" ? (
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">
                  Summary
                </p>
              ) : null}
              <p className={cn(
                "text-sm leading-relaxed text-foreground/85",
                previewLines === 2 ? "line-clamp-2" : "line-clamp-3",
              )}>
                {excerpt.text}
              </p>
            </div>
          ) : null}
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          {normalizedTags.length > 0 ? (
            <>
              {visibleTags.map((tag) => (
              <span
                key={`${title ?? "note"}-${tag}`}
                className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
              >
                {tag}
              </span>
              ))}
              {hiddenTagCount > 0 ? (
                <span className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/60">
                  +{hiddenTagCount}
                </span>
              ) : null}
            </>
          ) : (
            <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">
              No tags
            </span>
          )}
        </div>

        {hasMetricsRow ? (
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-foreground/60">
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
              {typeof viewCount === "number" ? (
                <span className="inline-flex items-center gap-1.5">
                  <Eye className="h-3.5 w-3.5" aria-hidden="true" />
                  <span>{viewCount.toLocaleString()} {viewCount === 1 ? "view" : "views"}</span>
                </span>
              ) : null}
              {typeof copyCount === "number" ? (
                <span className="inline-flex items-center gap-1.5">
                  <Copy className="h-3.5 w-3.5" aria-hidden="true" />
                  <span>{copyCount.toLocaleString()} {copyCount === 1 ? "copy" : "copies"}</span>
                </span>
              ) : null}
            </div>
            {metricsTrailing ? (
              <div className="shrink-0" onClick={(event) => event.stopPropagation()}>
                {metricsTrailing}
              </div>
            ) : null}
          </div>
        ) : null}

        {footer}
      </div>
    </div>
  );
}
