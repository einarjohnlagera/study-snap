"use client";

import { Card } from "@/components/ui/card";
import {
  type RecentQuizSessionHistoryItem,
  getQuizSessionModeLabel,
} from "@/lib/quiz-session-history";

type QuizSessionHistoryProps = {
  sessions: RecentQuizSessionHistoryItem[];
  onSelectSession: (session: RecentQuizSessionHistoryItem) => void;
};

function formatCompletedAt(value: string | null): string {
  if (!value) {
    return "Completed date unavailable";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "Completed date unavailable";
  }
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

export function QuizSessionHistory({
  sessions,
  onSelectSession,
}: QuizSessionHistoryProps) {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold sm:text-xl">Recent Sessions</h2>
        <p className="text-sm text-foreground/75">
          Revisit past quiz attempts to review answers, spot weak concepts, and track whether you&apos;re improving.
        </p>
      </div>

      {sessions.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-muted/20 px-4 py-5 text-sm text-foreground/70">
          <p className="font-medium text-foreground">No completed quiz sessions yet.</p>
          <p className="mt-1">Start a quiz to begin tracking your progress.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {sessions.map((session) => (
            <button
              key={`${session.sessionMode}-${session.sessionId}`}
              type="button"
              onClick={() => onSelectSession(session)}
              className="w-full rounded-2xl border border-border bg-background px-4 py-3 text-left transition-colors hover:bg-muted/30"
            >
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs font-medium text-foreground/70">
                      {getQuizSessionModeLabel(session.sessionMode)}
                    </span>
                    {session.performanceLevel ? (
                      <span className="rounded-full border border-border bg-background px-2.5 py-1 text-xs font-medium text-foreground/70">
                        {session.performanceLevel}
                      </span>
                    ) : null}
                  </div>
                  <p className="text-sm font-medium text-foreground">
                    {Math.round(session.scorePercentage)}% • {session.correctAnswers}/{session.totalQuestions} • {formatCompletedAt(session.completedAt)}
                  </p>
                  <p className="text-xs text-foreground/65">
                    {session.sessionMode === "QUICK_REVIEW" && session.retryCount > 0
                      ? `Retry attempts: ${session.retryCount}`
                      : session.weakConcepts.length > 0
                        ? `${session.weakConcepts.length} weak concept${session.weakConcepts.length === 1 ? "" : "s"} flagged`
                        : "No weak concepts flagged"}
                  </p>
                </div>
                <span className="self-start rounded-full px-2.5 py-1 text-xs font-semibold text-blue-700 dark:text-blue-300">
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
