"use client";

import { type ReactNode, useMemo, useState } from "react";
import { CheckCircle2, ChevronDown, ChevronUp, CircleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { QuizItem } from "@/lib/api";
import {
  computeConceptBreakdown,
  computeScore,
  computeWeakConcepts,
  mapPerformanceLevel,
} from "@/lib/challenge-quiz-results";
import { getDisplayedQuizChoices, isQuizSelectionCorrect, resolveQuizCorrectIndex } from "@/lib/quiz";
import { cn } from "@/lib/utils";

type ReviewMode = "all" | "incorrect";

type QuizAnswerReviewProps = {
  quiz: QuizItem[];
  selectedChoices: Record<number, number>;
  initialMode?: ReviewMode;
  title?: string;
  className?: string;
  footer?: ReactNode;
};

export function QuizAnswerReview({
  quiz,
  selectedChoices,
  initialMode = "all",
  title = "Review Answers",
  className,
  footer,
}: QuizAnswerReviewProps) {
  const [mode, setMode] = useState<ReviewMode>(initialMode);
  const [currentReviewIndex, setCurrentReviewIndex] = useState(0);
  const [expandedExplanations, setExpandedExplanations] = useState<Record<number, boolean>>(() =>
    Object.fromEntries(quiz.map((_, index) => [index, true])),
  );

  const reviewItems = useMemo(() => {
    return quiz.map((item, originalIndex) => {
      const displayedChoices = getDisplayedQuizChoices(item);
      const selectedChoiceIndex = selectedChoices[originalIndex] ?? null;
      const correctIndex = resolveQuizCorrectIndex(item);
      const selectedChoice = displayedChoices.find((choice) => choice.canonicalIndex === selectedChoiceIndex) ?? null;
      const correctChoice = displayedChoices.find((choice) => choice.canonicalIndex === correctIndex) ?? null;
      return {
        item,
        originalIndex,
        conceptLabel: item.concept?.trim() || "Unknown concept",
        displayedChoices,
        selectedChoiceIndex,
        correctIndex,
        selectedChoice,
        correctChoice,
        isCorrect: isQuizSelectionCorrect(item, selectedChoiceIndex),
      };
    });
  }, [quiz, selectedChoices]);

  const score = useMemo(() => computeScore(quiz, selectedChoices), [quiz, selectedChoices]);
  const performanceLevel = useMemo(
    () => mapPerformanceLevel(score.scorePercentage),
    [score.scorePercentage],
  );
  const conceptBreakdown = useMemo(
    () => computeConceptBreakdown(quiz, selectedChoices),
    [quiz, selectedChoices],
  );
  const weakConcepts = useMemo(
    () => computeWeakConcepts(conceptBreakdown),
    [conceptBreakdown],
  );
  const incorrectCount = useMemo(() => reviewItems.filter((item) => !item.isCorrect).length, [reviewItems]);
  const visibleItems = useMemo(() => {
    return mode === "incorrect" ? reviewItems.filter((item) => !item.isCorrect) : reviewItems;
  }, [mode, reviewItems]);

  const effectiveReviewIndex = Math.min(currentReviewIndex, Math.max(visibleItems.length - 1, 0));
  const currentItem = visibleItems[effectiveReviewIndex] ?? null;

  if (reviewItems.length === 0) {
    return null;
  }

  const handleSetMode = (nextMode: ReviewMode) => {
    setMode(nextMode);
    setCurrentReviewIndex(0);
  };

  const isExplanationExpanded = (questionIndex: number) => expandedExplanations[questionIndex] !== false;
  const anyExplanationCollapsed = reviewItems.some((item) => !isExplanationExpanded(item.originalIndex));
  const anyExplanationExpanded = reviewItems.some((item) => isExplanationExpanded(item.originalIndex));

  const setAllExplanationsExpanded = (expanded: boolean) => {
    setExpandedExplanations(() => {
      const next: Record<number, boolean> = {};
      reviewItems.forEach((item) => {
        next[item.originalIndex] = expanded;
      });
      return next;
    });
  };

  const currentExplanationExpanded = currentItem ? isExplanationExpanded(currentItem.originalIndex) : false;
  const selectedAnswerSummary = currentItem?.selectedChoice
    ? `${currentItem.selectedChoice.label}. ${currentItem.selectedChoice.text}`
    : "No answer selected";
  const correctAnswerSummary = currentItem?.correctChoice
    ? `${currentItem.correctChoice.label}. ${currentItem.correctChoice.text}`
    : "Correct answer unavailable";

  return (
    <Card className={cn("motion-fade-enter space-y-4 p-4 sm:p-6", className)} aria-label="Answer review">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          {title}
        </p>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1">
            <h2 className="text-lg font-semibold">Learn from each question</h2>
            <p className="text-sm text-foreground/70">
              Compare your answer with the correct answer, then review the explanation and concept.
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:items-end">
            {incorrectCount > 0 ? (
              <div className="flex rounded-md border border-border bg-background p-1 text-sm">
                <Button
                  type="button"
                  size="sm"
                  variant={mode === "all" ? "default" : "outline"}
                  className="h-8 px-3"
                  aria-pressed={mode === "all"}
                  onClick={() => handleSetMode("all")}
                >
                  All Questions
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant={mode === "incorrect" ? "default" : "outline"}
                  className="h-8 px-3"
                  aria-pressed={mode === "incorrect"}
                  onClick={() => handleSetMode("incorrect")}
                >
                  Incorrect Only
                </Button>
              </div>
            ) : null}
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="h-8 px-3"
                onClick={() => setAllExplanationsExpanded(true)}
                disabled={!anyExplanationCollapsed}
              >
                Expand All
              </Button>
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="h-8 px-3"
                onClick={() => setAllExplanationsExpanded(false)}
                disabled={!anyExplanationExpanded}
              >
                Collapse All
              </Button>
            </div>
          </div>
        </div>
      </div>

      <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-lg border border-border bg-background px-3 py-3">
          <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Correct</p>
          <p className="mt-1 text-lg font-semibold text-foreground">{score.correctAnswers}</p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-3">
          <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Total Questions</p>
          <p className="mt-1 text-lg font-semibold text-foreground">{score.totalQuestions}</p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-3">
          <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Percentage</p>
          <p className="mt-1 text-lg font-semibold text-foreground">{score.scorePercentage}%</p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-3">
          <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Performance</p>
          <p className="mt-1 text-lg font-semibold text-foreground">{performanceLevel}</p>
        </div>
      </div>

      <div className="rounded-lg border border-border bg-background px-3 py-3">
        <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Weak Concepts</p>
        <p className="mt-1 text-sm leading-relaxed text-foreground/75">
          {weakConcepts.length > 0
            ? weakConcepts.join(", ")
            : "No weak concepts identified in this review."}
        </p>
      </div>

      {currentItem ? (
        <div
          key={`${mode}-${currentItem.originalIndex}`}
          data-testid="quiz-answer-review-current-item"
          className="motion-fade-enter space-y-4 rounded-lg border border-border bg-background p-4"
        >
          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs font-medium text-foreground/70">
                  Question {currentItem.originalIndex + 1} of {quiz.length}
                </span>
                <span className="rounded-full border border-blue-500/30 bg-blue-500/10 px-2.5 py-1 text-xs font-medium text-blue-700 dark:text-blue-300">
                  Concept: {currentItem.conceptLabel}
                </span>
                <span
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium",
                    currentItem.isCorrect
                      ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
                      : "border-red-500/40 bg-red-500/10 text-red-700 dark:text-red-300",
                  )}
                >
                  {currentItem.isCorrect ? (
                    <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                  ) : (
                    <CircleAlert className="h-3.5 w-3.5" aria-hidden="true" />
                  )}
                  {currentItem.isCorrect ? "Correct" : "Incorrect"}
                </span>
              </div>
              <h3 className="text-base font-semibold leading-relaxed sm:text-lg">
                {currentItem.item.question}
              </h3>
            </div>
          </div>

          <div className="grid gap-2 rounded-lg border border-border bg-muted/20 p-3 sm:grid-cols-2">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Result</p>
              <p className="text-sm font-medium text-foreground">
                {currentItem.isCorrect ? "Correct" : "Incorrect"}
              </p>
            </div>
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Concept</p>
              <p className="text-sm text-foreground">{currentItem.conceptLabel}</p>
            </div>
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Your Answer</p>
              <p className="text-sm text-foreground">{selectedAnswerSummary}</p>
            </div>
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Correct Answer</p>
              <p className="text-sm text-foreground">{correctAnswerSummary}</p>
            </div>
          </div>

          <ul className="space-y-2 text-sm">
            {currentItem.displayedChoices.map((choice) => {
              const isCorrectChoice = choice.canonicalIndex === currentItem.correctIndex;
              const isSelectedChoice = choice.canonicalIndex === currentItem.selectedChoiceIndex;
              const isIncorrectSelection = isSelectedChoice && !isCorrectChoice;

              return (
                <li
                  key={`${currentItem.originalIndex}-${choice.label}-${choice.canonicalIndex}`}
                  className={cn(
                    "rounded-md border px-3 py-2 text-sm leading-relaxed transition-colors",
                    isCorrectChoice
                      ? "border-emerald-500/60 bg-emerald-500/10 text-foreground"
                      : isIncorrectSelection
                        ? "border-red-500/60 bg-red-500/10 text-foreground"
                        : "border-border bg-muted/20 text-foreground/75",
                  )}
                >
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <p className="whitespace-normal break-words">
                      <span className="mr-2 font-semibold text-foreground">{choice.label}.</span>
                      <span>{choice.text}</span>
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {isSelectedChoice ? (
                        <span className="rounded-full border border-blue-500/40 bg-blue-500/10 px-2 py-0.5 text-xs font-medium text-blue-700 dark:text-blue-300">
                          Your answer
                        </span>
                      ) : null}
                      {isCorrectChoice ? (
                        <span className="rounded-full border border-emerald-500/40 bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                          Correct answer
                        </span>
                      ) : null}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>

          {currentItem.selectedChoiceIndex === null ? (
            <p className="rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-foreground/80">
              No answer selected for this question.
            </p>
          ) : null}

          <div className="space-y-2">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-medium text-foreground">Why this is correct</p>
                <p className="text-xs text-foreground/65">Review the explanation when you need more context.</p>
              </div>
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={() => setExpandedExplanations((previous) => ({
                  ...previous,
                  [currentItem.originalIndex]: !isExplanationExpanded(currentItem.originalIndex),
                }))}
              >
                {currentExplanationExpanded ? (
                  <ChevronUp className="h-4 w-4" aria-hidden="true" />
                ) : (
                  <ChevronDown className="h-4 w-4" aria-hidden="true" />
                )}
                {currentExplanationExpanded ? "Collapse Explanation" : "Expand Explanation"}
              </Button>
            </div>
            <div
              data-testid="quiz-answer-review-explanation"
              className="motion-collapse"
              data-state={currentExplanationExpanded ? "expanded" : "collapsed"}
              aria-hidden={!currentExplanationExpanded}
            >
              <div className="motion-collapse-inner">
                <div className="space-y-1 rounded-md border border-border bg-muted/30 p-3 text-sm text-foreground/80">
                  <p className="font-medium text-foreground">Explanation</p>
                  <p className="leading-relaxed">
                    {currentItem.item.explanation?.trim() || "No explanation available for this question."}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      ) : (
        <p className="rounded-md border border-border bg-background p-3 text-sm text-foreground/70">
          No questions match this review filter.
        </p>
      )}

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-foreground/70">
          Reviewing {visibleItems.length === 0 ? 0 : effectiveReviewIndex + 1} of {visibleItems.length}
        </p>
        <div className="flex flex-col gap-2 sm:flex-row">
          <Button
            type="button"
            variant="outline"
            className="w-full sm:w-auto"
            onClick={() => setCurrentReviewIndex((current) => Math.max(0, current - 1))}
            disabled={effectiveReviewIndex <= 0}
          >
            Previous Question
          </Button>
          <Button
            type="button"
            variant="outline"
            className="w-full sm:w-auto"
            onClick={() => setCurrentReviewIndex((current) => Math.min(visibleItems.length - 1, current + 1))}
            disabled={effectiveReviewIndex >= visibleItems.length - 1}
          >
            Next Question
          </Button>
        </div>
      </div>

      {footer ? (
        <div className="space-y-3 border-t border-border pt-4">
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">Continue learning</p>
            <p className="text-sm text-foreground/70">
              Take the next step while this review is still fresh.
            </p>
          </div>
          {footer}
        </div>
      ) : null}
    </Card>
  );
}
