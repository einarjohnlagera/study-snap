import type { ReactNode } from "react";
import { Copy, Eye } from "lucide-react";
import { SubjectBadge } from "@/components/notes/subject-badge";

type SharedNoteCardProps = {
  title: string | null;
  metaLine?: ReactNode;
  subject: string | null;
  tags: string[];
  contentPreview: string;
  summaryPreview?: string | null;
  copyCount?: number | null;
  viewCount?: number | null;
  metadataBadges?: ReactNode;
  titleTrailing?: ReactNode;
  footer?: ReactNode;
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
  metaLine,
  subject,
  tags,
  contentPreview,
  summaryPreview,
  copyCount,
  viewCount,
  metadataBadges,
  titleTrailing,
  footer,
}: Readonly<SharedNoteCardProps>) {
  const normalizedTags = normalizeTags(tags);
  const hasDiscoveryMetrics = typeof viewCount === "number" || typeof copyCount === "number";

  return (
    <div className="flex h-full min-w-0 flex-col justify-between gap-4">
      <div className="space-y-4">
        {metaLine ? (
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-foreground/60">
            {metaLine}
          </div>
        ) : null}

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

          <div className="flex flex-wrap items-center gap-2">
            <SubjectBadge subject={subject} />
            {metadataBadges}
          </div>

          <div className="space-y-3">
            <div className="space-y-1">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">
                Note Preview
              </p>
              <p className="line-clamp-3 text-sm leading-relaxed text-foreground/85">
                {resolveNotePreview(contentPreview)}
              </p>
            </div>

            <div className="space-y-1">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">
                Summary Preview
              </p>
              <p className="line-clamp-2 text-sm leading-relaxed text-foreground/65">
                {resolveSummaryPreview(summaryPreview)}
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          {normalizedTags.length > 0 ? (
            normalizedTags.map((tag) => (
              <span
                key={`${title ?? "note"}-${tag}`}
                className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
              >
                {tag}
              </span>
            ))
          ) : (
            <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">
              No tags
            </span>
          )}
        </div>

        {hasDiscoveryMetrics ? (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-foreground/60">
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
        ) : null}

        {footer}
      </div>
    </div>
  );
}
