"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Card } from "@/components/ui/card";
import { type BestQuizSessionResponse, getUserBestQuizSessions } from "@/lib/api";
import { buildNoteSessionReviewPath } from "@/lib/note-session-review";

const BEST_SESSIONS_LIMIT = 5;

function formatCompletedAt(value: string): string {
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

function getSessionModeLabel(sessionMode: BestQuizSessionResponse["sessionMode"]): string {
  return sessionMode === "CHALLENGE" ? "Challenge Quiz" : "Quick Review";
}

function ScoreBadge({ scorePercentage }: Readonly<{ scorePercentage: number }>) {
  const rounded = Math.round(scorePercentage);
  const isPerfect = rounded === 100;
  const isStrong = rounded >= 80;

  if (isPerfect) {
    return (
      <span className="rounded-full border border-amber-500/40 bg-amber-500/10 px-2.5 py-1 text-xs font-semibold text-amber-700 dark:text-amber-300">
        ⭐ Perfect
      </span>
    );
  }
  if (isStrong) {
    return (
      <span className="rounded-full border border-green-500/40 bg-green-500/10 px-2.5 py-1 text-xs font-semibold text-green-700 dark:text-green-300">
        Top Score
      </span>
    );
  }
  return null;
}

export function ProfileBestSessions() {
  const router = useRouter();
  const [sessions, setSessions] = useState<BestQuizSessionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    getUserBestQuizSessions(BEST_SESSIONS_LIMIT)
      .then((result) => {
        if (active) {
          setSessions(result);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Could not load best sessions.");
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

  const handleSelectSession = (session: BestQuizSessionResponse) => {
    const path = buildNoteSessionReviewPath(session.noteId, session.sessionId, session.sessionMode, "quiz");
    router.push(path);
  };

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold sm:text-xl">Best Sessions</h2>
        <p className="text-sm text-foreground/75">
          Your top-performing quiz attempts across all notes.
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
          <p className="font-medium text-foreground">Could not load best sessions.</p>
          <p className="mt-1">{error}</p>
        </div>
      ) : sessions.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-muted/20 px-4 py-5 text-sm text-foreground/70">
          <p className="font-medium text-foreground">No quiz sessions yet.</p>
          <p className="mt-1">Start a quiz to see your best sessions here.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {sessions.map((session) => (
            <button
              key={session.sessionId}
              type="button"
              onClick={() => handleSelectSession(session)}
              className="w-full rounded-2xl border border-border bg-background px-4 py-3 text-left transition-colors hover:bg-highlight"
            >
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs font-medium text-foreground/70">
                      {getSessionModeLabel(session.sessionMode)}
                    </span>
                    <ScoreBadge scorePercentage={session.scorePercentage} />
                  </div>
                  <p className="truncate text-sm font-medium text-foreground">
                    {session.noteTitle?.trim() || "Untitled note"}
                  </p>
                  <p className="text-xs text-foreground/65">
                    {Math.round(session.scorePercentage)}% &bull; {session.correctAnswers}/{session.totalQuestions} &bull; {formatCompletedAt(session.completedAt)}
                  </p>
                </div>
                <span className="shrink-0 self-start rounded-full px-2.5 py-1 text-xs font-semibold text-blue-700 dark:text-blue-300">
                  Review session
                </span>
              </div>
            </button>
          ))}
        </div>
      )}
    </Card>
  );
}
