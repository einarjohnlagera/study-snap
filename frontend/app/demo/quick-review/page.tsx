"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { DEMO_STUDY_PACK_RESULT } from "@/app/study/demo-content";

type QuickReviewPhase = "initial" | "retry-transition" | "retry" | "complete";

function DemoScoreBlock({
  score,
  total,
  percentage,
}: {
  score: number;
  total: number;
  percentage: number;
}) {
  return (
    <div className="space-y-1">
      <p className="text-base font-medium text-foreground">Score: {score} / {total} correct</p>
      <p className="font-medium text-foreground">{percentage}%</p>
      <div className="h-2 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-blue-600 transition-all dark:bg-blue-400"
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}

export default function DemoQuickReviewPage() {
  const quiz = DEMO_STUDY_PACK_RESULT.quiz;
  const totalQuestions = quiz.length;
  const [phase, setPhase] = useState<QuickReviewPhase>("initial");
  const [activeQuestionIndexes, setActiveQuestionIndexes] = useState<number[]>(
    quiz.map((_, index) => index),
  );
  const [currentRoundIndex, setCurrentRoundIndex] = useState(0);
  const [retryQuestionIndexes, setRetryQuestionIndexes] = useState<number[]>([]);
  const [roundSelections, setRoundSelections] = useState<Record<number, string>>({});
  const [selectedChoices, setSelectedChoices] = useState<Record<number, string>>({});

  const currentQuestionIndex = currentRoundIndex < activeQuestionIndexes.length
    ? activeQuestionIndexes[currentRoundIndex]
    : null;
  const currentQuestion = currentQuestionIndex !== null ? quiz[currentQuestionIndex] : null;
  const selectedChoice = currentQuestionIndex !== null ? roundSelections[currentQuestionIndex] ?? null : null;
  const hasAnsweredCurrent = Boolean(selectedChoice);

  const score = useMemo(
    () =>
      quiz.reduce((count, item, index) => {
        return selectedChoices[index] === item.answer ? count + 1 : count;
      }, 0),
    [quiz, selectedChoices],
  );
  const scorePercentage = useMemo(() => {
    if (totalQuestions === 0) {
      return 0;
    }
    return Number(((score / totalQuestions) * 100).toFixed(0));
  }, [score, totalQuestions]);

  const handleSelectChoice = (choice: string) => {
    if (!currentQuestion || currentQuestionIndex === null || hasAnsweredCurrent) {
      return;
    }
    setRoundSelections((previous) => ({
      ...previous,
      [currentQuestionIndex]: choice,
    }));
    setSelectedChoices((previous) => ({
      ...previous,
      [currentQuestionIndex]: choice,
    }));
  };

  const handleNext = () => {
    if (!hasAnsweredCurrent) {
      return;
    }
    const isLastInRound = currentRoundIndex + 1 >= activeQuestionIndexes.length;
    if (!isLastInRound) {
      setCurrentRoundIndex((previous) => previous + 1);
      return;
    }

    if (phase === "initial") {
      const incorrectIndexes = activeQuestionIndexes.filter(
        (index) => selectedChoices[index] !== quiz[index]?.answer,
      );
      if (incorrectIndexes.length === 0) {
        setPhase("complete");
        return;
      }
      setRetryQuestionIndexes(incorrectIndexes);
      setPhase("retry-transition");
      return;
    }

    if (phase === "retry") {
      setPhase("complete");
    }
  };

  const handleStartRetry = () => {
    if (retryQuestionIndexes.length === 0) {
      setPhase("complete");
      return;
    }
    setPhase("retry");
    setActiveQuestionIndexes(retryQuestionIndexes);
    setRoundSelections({});
    setCurrentRoundIndex(0);
  };

  const handleFinishReview = () => {
    setPhase("complete");
  };

  const handleRestart = () => {
    setPhase("initial");
    setActiveQuestionIndexes(quiz.map((_, index) => index));
    setCurrentRoundIndex(0);
    setRetryQuestionIndexes([]);
    setRoundSelections({});
    setSelectedChoices({});
  };

  return (
    <main className="mx-auto w-full max-w-3xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        <Link href="/demo" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
          Back to Demo Study Pack
        </Link>
      </div>

      {phase === "complete" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Demo Quick Review Complete
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Your demo results</h1>
          <DemoScoreBlock score={score} total={totalQuestions} percentage={scorePercentage} />
          <p className="text-sm text-foreground/75">
            This demo uses a prebuilt Study Pack and does not create a real session.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" onClick={handleRestart} className="w-full sm:w-auto">
              Practice Demo Again
            </Button>
            <Link href="/auth" className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Start Free
              </Button>
            </Link>
          </div>
        </Card>
      ) : phase === "retry-transition" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Demo Quick Review Progress
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">You&apos;re making progress.</h1>
          <DemoScoreBlock score={score} total={totalQuestions} percentage={scorePercentage} />
          <p className="text-sm text-foreground/75">
            You missed {retryQuestionIndexes.length} {retryQuestionIndexes.length === 1 ? "question" : "questions"}.
            Retry now or finish with your current score.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" onClick={handleStartRetry} className="w-full sm:w-auto">
              Retry Incorrect Questions
            </Button>
            <Button type="button" variant="outline" onClick={handleFinishReview} className="w-full sm:w-auto">
              Finish Review
            </Button>
          </div>
        </Card>
      ) : currentQuestion && currentQuestionIndex !== null ? (
        <div className="space-y-4">
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Demo Quick Review
            </p>
            <h1 className="text-xl font-semibold sm:text-2xl">{DEMO_STUDY_PACK_RESULT.title}</h1>
            <p className="text-sm text-foreground/75">
              {phase === "retry"
                ? `Retry question ${currentRoundIndex + 1} of ${activeQuestionIndexes.length}`
                : `Question ${currentRoundIndex + 1} of ${totalQuestions}`}
            </p>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold">
              {currentQuestionIndex + 1}. {currentQuestion.question}
            </h2>
            <QuizChoiceList
              choices={currentQuestion.choices}
              correctAnswer={currentQuestion.answer}
              selectedChoice={selectedChoice}
              revealAnswer={hasAnsweredCurrent}
              onSelectChoice={handleSelectChoice}
            />

            {hasAnsweredCurrent ? (
              <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
                <p>
                  <span className="font-medium text-foreground">Explanation:</span>{" "}
                  {currentQuestion.explanation}
                </p>
              </div>
            ) : null}

            <div className="flex justify-stretch sm:justify-end">
              <Button type="button" onClick={handleNext} className="w-full sm:w-auto" disabled={!hasAnsweredCurrent}>
                {currentRoundIndex + 1 === activeQuestionIndexes.length
                  ? phase === "retry"
                    ? "Finish Retry"
                    : "Finish Quick Review"
                  : "Next Question"}
              </Button>
            </div>
          </Card>
        </div>
      ) : null}
    </main>
  );
}
