"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Card } from "@/components/ui/card";
import { type NotePerformanceSummaryResponse, getUserNotePerformanceSummary } from "@/lib/api";
import { getBrowsingCardClassName } from "@/lib/clickable-card";
import { PROFILE_TOP_PERFORMANCE_SECTION_ID } from "@/lib/profile-sections";
import { buildNoteSessionReviewPath } from "@/lib/note-session-review";

const NOTE_PERFORMANCE_LIMIT = 5;

function formatLastAttempted(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "Date unavailable";
  }
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

function BestScoreBadge({ score }: Readonly<{ score: number }>) {
  const rounded = Math.round(score);
  if (rounded === 100) {
    return (
      <span className="rounded-full border border-amber-500/40 bg-amber-500/10 px-2.5 py-1 text-xs font-semibold text-amber-700 dark:text-amber-300">
        ⭐ Perfect
      </span>
    );
  }
  if (rounded >= 80) {
    return (
      <span className="rounded-full border border-green-500/40 bg-green-500/10 px-2.5 py-1 text-xs font-semibold text-green-700 dark:text-green-300">
        Top Score
      </span>
    );
  }
  return null;
}

export function ProfileNotePerformance() {
  const router = useRouter();
  const [notes, setNotes] = useState<NotePerformanceSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    getUserNotePerformanceSummary(NOTE_PERFORMANCE_LIMIT)
      .then((result) => {
        if (active) {
          setNotes(result);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Could not load note performance.");
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const handleSelectNote = (note: NotePerformanceSummaryResponse) => {
    const path = buildNoteSessionReviewPath(
      note.noteId,
      note.bestSessionId,
      note.bestSessionMode,
      "quiz",
      "profile",
    );
    router.push(path);
  };

  return (
    <Card id={PROFILE_TOP_PERFORMANCE_SECTION_ID} className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold sm:text-xl">Top Performance by Note</h2>
        <p className="text-sm text-foreground/75">
          Your strongest quiz results grouped by note.
        </p>
      </div>

      {loading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-16 animate-pulse rounded-2xl bg-foreground/5" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-2xl border border-dashed border-border bg-muted/20 px-4 py-5 text-sm text-foreground/70">
          <p className="font-medium text-foreground">Could not load note performance.</p>
          <p className="mt-1">{error}</p>
        </div>
      ) : notes.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-muted/20 px-4 py-5 text-sm text-foreground/70">
          <p className="font-medium text-foreground">No quiz sessions yet.</p>
          <p className="mt-1">Start a quiz to see your performance by note here.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {notes.map((note) => (
            <button
              key={note.noteId}
              type="button"
              onClick={() => handleSelectNote(note)}
              className={getBrowsingCardClassName("w-full px-4 py-3 text-left")}
            >
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <BestScoreBadge score={note.bestScore} />
                  </div>
                  <p className="truncate text-sm font-medium text-foreground">
                    {note.noteTitle?.trim() || "Untitled note"}
                  </p>
                  <p className="text-xs text-foreground/65">
                    Best: {Math.round(note.bestScore)}%
                    {note.averageScore != null ? ` \u00b7 Avg: ${Math.round(note.averageScore)}%` : ""}
                    {" \u00b7 "}
                    {note.attemptCount} {note.attemptCount === 1 ? "session" : "sessions"}
                    {" \u00b7 "}
                    {formatLastAttempted(note.lastAttemptedAt)}
                  </p>
                </div>
                <span className="shrink-0 self-start rounded-full px-2.5 py-1 text-xs font-semibold text-blue-700 dark:text-blue-300">
                  Review best
                </span>
              </div>
            </button>
          ))}
        </div>
      )}
    </Card>
  );
}
