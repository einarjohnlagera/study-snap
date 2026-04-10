"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { QuizItem } from "@/lib/api";
import { getDisplayedQuizChoices, isQuizSelectionCorrect, resolveQuizCorrectIndex } from "@/lib/quiz";
import { cn } from "@/lib/utils";

type ReviewMode = "all" | "incorrect";

type QuizAnswerReviewProps = {
  quiz: QuizItem[];
  selectedChoices: Record<number, number>;
  initialMode?: ReviewMode;
  title?: string;
  className?: string;
};

export function QuizAnswerReview({
  quiz,
  selectedChoices,
  initialMode = "all",
  title = "Review Answers",
  className,
}: QuizAnswerReviewProps) {
  const [mode, setMode] = useState<ReviewMode>(initialMode);
  const [currentReviewIndex, setCurrentReviewIndex] = useState(0);

  const reviewItems = useMemo(() => {
    return quiz.map((item, originalIndex) => {
      const selectedChoiceIndex = selectedChoices[originalIndex] ?? null;
      const correctIndex = resolveQuizCorrectIndex(item);
      return {
        item,
        originalIndex,
        selectedChoiceIndex,
        correctIndex,
        isCorrect: isQuizSelectionCorrect(item, selectedChoiceIndex),
      };
    });
  }, [quiz, selectedChoices]);

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
                All
              </Button>
              <Button
                type="button"
                size="sm"
                variant={mode === "incorrect" ? "default" : "outline"}
                className="h-8 px-3"
                aria-pressed={mode === "incorrect"}
                onClick={() => handleSetMode("incorrect")}
              >
                Incorrect only
              </Button>
            </div>
          ) : null}
        </div>
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
                  {currentItem.item.concept?.trim() || "Unknown concept"}
                </span>
                <span
                  className={cn(
                    "rounded-full border px-2.5 py-1 text-xs font-medium",
                    currentItem.isCorrect
                      ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
                      : "border-red-500/40 bg-red-500/10 text-red-700 dark:text-red-300",
                  )}
                >
                  {currentItem.isCorrect ? "Answered correctly" : "Needs review"}
                </span>
              </div>
              <h3 className="text-base font-semibold leading-relaxed sm:text-lg">
                {currentItem.item.question}
              </h3>
            </div>
          </div>

          <ul className="space-y-2 text-sm">
            {getDisplayedQuizChoices(currentItem.item).map((choice) => {
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

          <div className="space-y-1 rounded-md border border-border bg-muted/30 p-3 text-sm text-foreground/80">
            <p className="font-medium text-foreground">Why this is correct</p>
            <p className="leading-relaxed">
              {currentItem.item.explanation?.trim() || "No explanation available for this question."}
            </p>
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
    </Card>
  );
}
