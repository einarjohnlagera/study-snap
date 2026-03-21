import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { NoteListItemResponse } from "@/lib/api";

type StudyPackGridProps = {
  notes: NoteListItemResponse[];
  totalNotes: number;
  recentNoteMetaById: Record<string, { lastReviewedAt: string | null; quizCount: number | null }>;
};

function toPreview(contentPreview: string, maxLength = 160) {
  const clean = contentPreview.trim();
  if (clean.length <= maxLength) {
    return clean;
  }
  return `${clean.slice(0, maxLength - 3)}...`;
}

function toFormattedDate(value: string | null | undefined) {
  if (!value) {
    return "Unavailable";
  }
  const timestamp = new Date(value);
  if (Number.isNaN(timestamp.getTime())) {
    return "Unavailable";
  }
  return timestamp.toLocaleString();
}

export function StudyPackGrid({ notes, totalNotes, recentNoteMetaById }: StudyPackGridProps) {
  return (
    <section className="space-y-3 sm:space-y-4">
      <div className="flex flex-col gap-0.5 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-lg font-semibold sm:text-xl">Recent Notes</h2>
        <p className="text-xs text-foreground/65">{totalNotes} saved</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {notes.map((item) => (
          <Card key={item.id} className="space-y-4 p-4 transition-colors hover:bg-muted/40 hover:shadow-md sm:p-6">
            <div className="space-y-2">
              <h3 className="text-base font-semibold sm:text-lg">
                {item.title?.trim() || "Untitled note"}
              </h3>
              <p className="text-xs text-foreground/65">
                {item.subject?.trim() || "No subject"}
              </p>
              <span
                className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${
                  item.studyPackStatus === "STUDY_PACK_READY"
                    ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
                    : "border-border bg-muted/50 text-foreground/70"
                }`}
              >
                {item.studyPackStatus === "STUDY_PACK_READY" ? "✨ Study Pack" : "📝 Draft"}
              </span>
              <p className="text-sm leading-relaxed text-foreground/75">
                {toPreview(item.contentPreview)}
              </p>
            </div>

            {item.studyPackStatus === "STUDY_PACK_READY" ? (
              <div className="space-y-1">
                <p className="break-words text-xs text-foreground/65">
                  {recentNoteMetaById[item.id]?.lastReviewedAt
                    ? `Last studied ${toFormattedDate(recentNoteMetaById[item.id]?.lastReviewedAt)}`
                    : `Last opened ${toFormattedDate(item.updatedAt)}`}
                </p>
                {recentNoteMetaById[item.id]?.quizCount !== null && recentNoteMetaById[item.id]?.quizCount !== undefined ? (
                  <p className="text-xs text-foreground/65">
                    {recentNoteMetaById[item.id]?.quizCount} quiz questions
                  </p>
                ) : null}
              </div>
            ) : (
              <p className="break-words text-xs text-foreground/65">
                Last edited {toFormattedDate(item.updatedAt)}
              </p>
            )}

            {item.studyPackStatus === "STUDY_PACK_READY" ? (
              <div className="flex flex-wrap gap-2">
                {item.tags.length > 0 ? (
                  item.tags.map((tag) => (
                    <span
                      key={`${item.id}-${tag}`}
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
            ) : null}

            <div className="pt-1">
              <Link href={`/notes/${item.id}?from=dashboard`} className="w-full sm:w-auto">
                <Button type="button" variant={item.studyPackStatus === "STUDY_PACK_READY" ? "default" : "outline"}>
                  {item.studyPackStatus === "STUDY_PACK_READY" ? "Open Study Pack" : "Open Note"}
                </Button>
              </Link>
            </div>
          </Card>
        ))}
      </div>

      <div className="pt-0.5">
        <Link href="/library" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
          View All in Library &rarr;
        </Link>
      </div>
    </section>
  );
}
