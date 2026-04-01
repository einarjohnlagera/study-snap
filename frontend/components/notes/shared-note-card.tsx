import type { ReactNode } from "react";
import { SubjectBadge } from "@/components/notes/subject-badge";

type SharedNoteCardProps = {
  title: string | null;
  subject: string | null;
  tags: string[];
  contentPreview: string;
  summaryPreview?: string | null;
  copyCount?: number | null;
  metadataBadges?: ReactNode;
  footer?: ReactNode;
  actionSlot?: ReactNode;
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
  subject,
  tags,
  contentPreview,
  summaryPreview,
  copyCount,
  metadataBadges,
  footer,
  actionSlot,
}: Readonly<SharedNoteCardProps>) {
  const normalizedTags = normalizeTags(tags);

  return (
    <div className="flex h-full gap-3">
      <div className="flex min-w-0 flex-1 flex-col justify-between gap-4">
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <SubjectBadge subject={subject} />
            {typeof copyCount === "number" ? (
              <span className="rounded-full border border-border bg-muted/40 px-2 py-1 text-xs text-foreground/70">
                {copyCount} {copyCount === 1 ? "copy" : "copies"}
              </span>
            ) : null}
            {metadataBadges}
          </div>

          <div className="space-y-3">
            <h3 className="line-clamp-2 text-base font-semibold sm:text-lg">
              {title?.trim() || "Untitled note"}
            </h3>

            <div className="space-y-3">
              <div className="space-y-1">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">
                  Note Preview
                </p>
                <p className="line-clamp-3 text-sm leading-relaxed text-foreground/75">
                  {resolveNotePreview(contentPreview)}
                </p>
              </div>

              <div className="space-y-1">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">
                  Summary Preview
                </p>
                <p className="line-clamp-3 text-sm leading-relaxed text-foreground/75">
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

          {footer}
        </div>
      </div>

      {actionSlot ? (
        <div className="relative shrink-0 self-start" data-card-menu="true">
          {actionSlot}
        </div>
      ) : null}
    </div>
  );
}
