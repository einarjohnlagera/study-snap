import type { ReactNode } from "react";
import { Copy, Eye } from "lucide-react";
import { SubjectBadge } from "@/components/notes/subject-badge";
import { cn } from "@/lib/utils";

type SharedNoteCardProps = {
  title: string | null;
  courseProgram?: string | null;
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
  notePreviewLines?: 2 | 3;
  summaryPreviewLines?: 2 | 3;
  showPreviewLabels?: boolean;
};

function normalizeTags(tags: string[] | null | undefined): string[] {
  if (!Array.isArray(tags)) {
    return [];
  }
  return tags
    .map((tag) => tag?.trim())
    .filter((tag): tag is string => Boolean(tag && tag.length > 0));
}

function resolveNotePreview(contentPreview: string) {
  const trimmed = contentPreview.trim();
  return trimmed.length > 0 ? trimmed : "No note preview available yet.";
}

function resolveSummaryPreview(summaryPreview?: string | null) {
  const trimmed = summaryPreview?.trim() ?? "";
  return trimmed.length > 0 ? trimmed : "No summary available yet.";
}

export function SharedNoteCard({
  title,
  courseProgram,
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
  notePreviewLines = 3,
  summaryPreviewLines = 2,
  showPreviewLabels = true,
}: Readonly<SharedNoteCardProps>) {
  const normalizedTags = normalizeTags(tags);
  const normalizedCourseProgram = courseProgram?.trim() || null;
  const hasDiscoveryMetrics = typeof viewCount === "number" || typeof copyCount === "number";
  const hasMetricsRow = hasDiscoveryMetrics || Boolean(metricsTrailing);
  const visibleTags = tagDisplayLimit ? normalizedTags.slice(0, tagDisplayLimit) : normalizedTags;
  const hiddenTagCount = Math.max(0, normalizedTags.length - visibleTags.length);

  return (
    <div className="flex h-full min-w-0 flex-col justify-between gap-4">
      <div className="space-y-4">
        {/* TOP ROW: Subject badge + Course/Program metadata — above title */}
        <div className="flex flex-wrap items-center gap-2">
          <SubjectBadge subject={subject} />
          {normalizedCourseProgram ? (
            <span className="text-xs font-medium text-foreground/65">
              {normalizedCourseProgram}
            </span>
          ) : null}
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

          {/* BELOW TITLE: Study Pack Ready badge (stateBadge) + quality badges */}
          {(stateBadge || metadataBadges) ? (
            <div className="flex flex-wrap items-center gap-2">
              {stateBadge}
              {metadataBadges}
            </div>
          ) : null}

          <div className="space-y-3">
            <div className={showPreviewLabels ? "space-y-1" : "space-y-0"}>
              {showPreviewLabels ? (
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">
                  Note Preview
                </p>
              ) : null}
              <p className={cn(
                "text-sm leading-relaxed text-foreground/85",
                notePreviewLines === 2 ? "line-clamp-2" : "line-clamp-3",
              )}>
                {resolveNotePreview(contentPreview)}
              </p>
            </div>

            <div className={showPreviewLabels ? "space-y-1" : "space-y-0"}>
              {showPreviewLabels ? (
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">
                  Summary Preview
                </p>
              ) : null}
              <p className={cn(
                "text-sm leading-relaxed text-foreground/65",
                summaryPreviewLines === 3 ? "line-clamp-3" : "line-clamp-2",
              )}>
                {resolveSummaryPreview(summaryPreview)}
              </p>
            </div>
          </div>
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
