"use client";

import { Card } from "@/components/ui/card";
import { QuizAnswerReview } from "@/components/study-pack/quiz-answer-review";
import type { QuizSessionReviewResponse } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { getQuizSessionModeLabel, type RecentQuizSessionHistoryItem } from "@/lib/quiz-session-history";
import {
  toSelectedChoiceIndexRecord,
  toSelectedEnumerationAnswersRecord,
  toSelectedIdentificationAnswerRecord,
  toSelectedMultiChoiceIndicesRecord,
} from "@/lib/quiz";
import { cn } from "@/lib/utils";

type QuizSessionReviewContentProps = {
  review: QuizSessionReviewResponse;
  title?: string;
  className?: string;
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

export function QuizSessionReviewContent({
  review,
  title = "Session Review",
  className,
}: QuizSessionReviewContentProps) {
  return (
    <div className={cn("space-y-3", className)}>
      <div className="rounded-2xl border border-border bg-background px-4 py-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
              {getQuizSessionModeLabel(review.sessionMode as RecentQuizSessionHistoryItem["sessionMode"])} Session
            </p>
            <h2 className="text-lg font-semibold text-foreground sm:text-xl">{title}</h2>
            <p className="text-sm text-foreground/80">
              {Math.round(review.scorePercentage)}% • {review.correctAnswers}/{review.totalQuestions} • {formatCompletedAt(review.completedAt)}
            </p>
          </div>
          {review.retryCount > 0 ? (
            <div className="rounded-full border border-border bg-muted/40 px-3 py-1 text-xs font-medium text-foreground/70">
              Retries: {review.retryCount}
            </div>
          ) : null}
        </div>
      </div>

      {review.quiz.length > 0 ? (
        <QuizAnswerReview
          title={title}
          quiz={review.quiz}
          selectedChoices={toSelectedChoiceIndexRecord(review.selectedChoices, review.quiz)}
          selectedMultiChoices={toSelectedMultiChoiceIndicesRecord(review.selectedMultiChoices, review.quiz)}
          selectedIdentificationAnswers={toSelectedIdentificationAnswerRecord(review.selectedIdentificationAnswers, review.quiz)}
          selectedEnumerationAnswers={toSelectedEnumerationAnswersRecord(review.selectedEnumerationAnswers, review.quiz)}
          planType={getAuthUser()?.planType ?? null}
          stickyNav
        />
      ) : (
        <Card className="space-y-4 p-4 sm:p-6">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              {title}
            </p>
            <h3 className="text-lg font-semibold">Detailed answer review is unavailable for this session.</h3>
            <p className="text-sm text-foreground/75">
              This older session does not have enough stored question detail to render answer-by-answer review, but the stored concept summary is still available below.
            </p>
          </div>

          <div className="space-y-2">
            <h4 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Concept Breakdown</h4>
            {review.conceptBreakdown.length > 0 ? (
              review.conceptBreakdown.map((stat) => (
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
            {review.weakConcepts.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {review.weakConcepts.map((concept) => (
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
  );
}
