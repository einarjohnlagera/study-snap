"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { type NotePerformanceSummaryResponse, getUserNotePerformanceSummary } from "@/lib/api";
import { PROFILE_TOP_PERFORMANCE_SECTION_ID } from "@/lib/profile-sections";
import { buildNoteSessionReviewPath } from "@/lib/note-session-review";

const STRONGEST_NOTES_LIMIT = 3;

export function DashboardStrongestNotes() {
  const router = useRouter();
  const startRouteProgress = useRouteProgress();
  const [notes, setNotes] = useState<NotePerformanceSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    getUserNotePerformanceSummary(STRONGEST_NOTES_LIMIT)
      .then((result) => {
        if (active) setNotes(result);
      })
      .catch(() => {
        // Silent — dashboard section is non-critical
      })
      .finally(() => {
        if (active) setLoading(false);
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
      "dashboard",
    );
    startRouteProgress();
    router.push(path);
  };

  if (loading) {
    return (
      <section className="space-y-3">
        <h2 className="text-lg font-semibold sm:text-xl">Strongest Notes</h2>
        <div className="space-y-2">
          {[...Array(2)].map((_, i) => (
            <Skeleton key={i} className="h-14 rounded-2xl bg-foreground/5" />
          ))}
        </div>
      </section>
    );
  }

  if (notes.length === 0) {
    return (
      <Card className="space-y-3 p-4 sm:p-6">
        <h2 className="text-lg font-semibold sm:text-xl">Strongest Notes</h2>
        <p className="text-sm text-foreground/75">
          You haven&apos;t taken any quizzes yet. Complete a Quick Review or Challenge Quiz to see your strongest notes here.
        </p>
      </Card>
    );
  }

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold sm:text-xl">Strongest Notes</h2>
        <Link
          href={`/profile#${PROFILE_TOP_PERFORMANCE_SECTION_ID}`}
          className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
        >
          View all
        </Link>
      </div>
      <div className="space-y-2">
        {notes.map((note) => (
          <button
            key={note.noteId}
            type="button"
            onClick={() => handleSelectNote(note)}
            className="w-full rounded-2xl border border-border bg-background px-4 py-3 text-left transition-colors hover:bg-highlight"
          >
            <div className="flex items-center justify-between gap-3">
              <p className="min-w-0 truncate text-sm font-medium text-foreground">
                {note.noteTitle?.trim() || "Untitled note"}
              </p>
              <span className="shrink-0 text-sm font-semibold text-foreground">
                {Math.round(note.bestScore)}%
              </span>
            </div>
            <p className="mt-0.5 text-xs text-foreground/60">
              {note.attemptCount} {note.attemptCount === 1 ? "attempt" : "attempts"}
            </p>
          </button>
        ))}
      </div>
    </section>
  );
}
