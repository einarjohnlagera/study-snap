"use client";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizAnswerReview } from "@/components/study-pack/quiz-answer-review";
import type { QuizSessionReviewResponse } from "@/lib/api";
import {
  type RecentQuizSessionHistoryItem,
  getQuizSessionModeLabel,
} from "@/lib/quiz-session-history";
import { toSelectedChoiceIndexRecord } from "@/lib/quiz";

type QuizSessionHistoryProps = {
  sessions: RecentQuizSessionHistoryItem[];
  activeSessionId: string | null;
  activeReview: QuizSessionReviewResponse | null;
  loadingReview: boolean;
  reviewError: string | null;
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
  activeSessionId,
  activeReview,
  loadingReview,
  reviewError,
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
          {sessions.map((session) => {
            const isActive = session.sessionId === activeSessionId;
            return (
              <button
                key={`${session.sessionMode}-${session.sessionId}`}
                type="button"
                onClick={() => onSelectSession(session)}
                className={`w-full rounded-2xl border px-4 py-3 text-left transition-colors ${
                  isActive
                    ? "border-blue-500/40 bg-blue-500/10"
                    : "border-border bg-background hover:bg-muted/30"
                }`}
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
                  <span className="text-sm font-medium text-blue-700 dark:text-blue-300">
                    {isActive ? "Reviewing" : "Review session"}
                  </span>
                </div>
              </button>
            );
          })}
        </div>
      )}

      {loadingReview ? (
        <div className="space-y-2 rounded-2xl border border-border bg-background px-4 py-4">
          <p className="text-sm font-medium text-foreground">Loading session review...</p>
          <div className="h-4 w-2/3 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        </div>
      ) : null}

      {reviewError ? (
        <div className="rounded-2xl border border-red-500/30 bg-red-500/10 px-4 py-4 text-sm text-red-700 dark:text-red-300">
          {reviewError}
        </div>
      ) : null}

      {!loadingReview && !reviewError && sessions.length > 0 && !activeReview ? (
        <div className="rounded-2xl border border-dashed border-border bg-muted/20 px-4 py-5 text-sm text-foreground/70">
          Select a session to review answers and concept performance.
        </div>
      ) : null}

      {!loadingReview && activeReview ? (
        <div className="space-y-3">
          <div className="rounded-2xl border border-border bg-background px-4 py-4">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div className="space-y-1">
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
                  {getQuizSessionModeLabel(activeReview.sessionMode as RecentQuizSessionHistoryItem["sessionMode"])} Session
                </p>
                <p className="text-sm text-foreground/80">
                  {Math.round(activeReview.scorePercentage)}% • {activeReview.correctAnswers}/{activeReview.totalQuestions} • {formatCompletedAt(activeReview.completedAt)}
                </p>
              </div>
              {activeReview.retryCount > 0 ? (
                <div className="rounded-full border border-border bg-muted/40 px-3 py-1 text-xs font-medium text-foreground/70">
                  Retry attempts: {activeReview.retryCount}
                </div>
              ) : null}
            </div>
          </div>

          {activeReview.quiz.length > 0 ? (
            <QuizAnswerReview
              title="Session Review"
              quiz={activeReview.quiz}
              selectedChoices={toSelectedChoiceIndexRecord(activeReview.selectedChoices, activeReview.quiz)}
            />
          ) : (
            <Card className="space-y-4 p-4 sm:p-6">
              <div className="space-y-1">
                <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                  Session Review
                </p>
                <h3 className="text-lg font-semibold">Detailed answer review is unavailable for this session.</h3>
                <p className="text-sm text-foreground/75">
                  This older session does not have enough stored question detail to render answer-by-answer review, but the stored concept summary is still available below.
                </p>
              </div>

              <div className="space-y-2">
                <h4 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Concept Breakdown</h4>
                {activeReview.conceptBreakdown.length > 0 ? (
                  activeReview.conceptBreakdown.map((stat) => (
                    <div key={stat.concept} className="rounded-xl border border-border bg-background px-3 py-3 text-sm">
                      <p className="font-medium text-foreground">{stat.concept}</p>
                      <p className="text-foreground/70">
                        {stat.correctAnswers}/{stat.totalQuestions} correct ({stat.accuracyPercentage}%)
                      </p>
                    </div>
                  ))
                ) : (
                  <p className="text-sm text-foreground/70">No concept breakdown is available for this session.</p>
                )}
              </div>

              <div className="space-y-2">
                <h4 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Weak Concepts</h4>
                {activeReview.weakConcepts.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {activeReview.weakConcepts.map((concept) => (
                      <span
                        key={concept}
                        className="rounded-full border border-amber-500/35 bg-amber-500/10 px-3 py-1 text-xs font-medium text-amber-700 dark:text-amber-300"
                      >
                        {concept}
                      </span>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-foreground/70">No weak concepts identified in this session.</p>
                )}
              </div>
            </Card>
          )}
        </div>
      ) : null}
    </Card>
  );
}
