"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import {
  generateAdaptiveQuickReviewQuiz,
  type QuickReviewAdaptiveQuizResponse,
} from "@/lib/api";

function AdaptivePracticeLoading() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="h-4 w-40 animate-pulse rounded bg-foreground/10" />
      <div className="h-7 w-3/4 animate-pulse rounded bg-foreground/10" />
      <div className="space-y-2">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={`adaptive-choice-${index}`} className="h-10 w-full animate-pulse rounded bg-foreground/10" />
        ))}
      </div>
    </Card>
  );
}

export default function AdaptivePracticePage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [adaptiveQuiz, setAdaptiveQuiz] = useState<QuickReviewAdaptiveQuizResponse | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedChoices, setSelectedChoices] = useState<Record<number, string>>({});

  const studyPackId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);

  const loadAdaptiveQuiz = useCallback(async () => {
    if (!studyPackId) {
      setError("Study Pack not found.");
      setLoading(false);
      return;
    }

    if (!requireVerifiedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await generateAdaptiveQuickReviewQuiz(studyPackId);
      setAdaptiveQuiz(response);
      setCurrentIndex(0);
      setSelectedChoices({});
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not generate adaptive practice.";
      setError(message);
      setAdaptiveQuiz(null);
    } finally {
      setLoading(false);
    }
  }, [router, studyPackId]);

  useEffect(() => {
    void loadAdaptiveQuiz();
  }, [loadAdaptiveQuiz]);

  const quiz = adaptiveQuiz?.quiz ?? [];
  const hasQuestions = quiz.length > 0;
  const currentQuestion = hasQuestions ? quiz[currentIndex] : null;
  const selectedChoice = selectedChoices[currentIndex] ?? null;
  const hasAnsweredCurrent = Boolean(selectedChoice);
  const isComplete = hasQuestions && currentIndex >= quiz.length;
  const score = useMemo(() => {
    return quiz.reduce((count, question, index) => {
      return selectedChoices[index] === question.answer ? count + 1 : count;
    }, 0);
  }, [quiz, selectedChoices]);
  const scorePercentage = useMemo(() => {
    if (quiz.length === 0) {
      return 0;
    }
    return Number(((score / quiz.length) * 100).toFixed(0));
  }, [quiz.length, score]);

  const handleSelectChoice = (choice: string) => {
    if (!currentQuestion || hasAnsweredCurrent) {
      return;
    }
    setSelectedChoices((prev) => ({
      ...prev,
      [currentIndex]: choice,
    }));
  };

  const handleNext = () => {
    if (!hasAnsweredCurrent) {
      return;
    }
    const nextIndex = currentIndex + 1;
    setCurrentIndex(nextIndex);
  };

  return (
    <main className="mx-auto w-full max-w-3xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        <Link
          href={studyPackId ? `/study-packs/${studyPackId}` : "/dashboard"}
          className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
        >
          Back to Study Pack
        </Link>
      </div>

      {loading ? (
        <AdaptivePracticeLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not generate adaptive practice</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={() => void loadAdaptiveQuiz()}>
              Try Again
            </Button>
            <Link href={studyPackId ? `/study-packs/${studyPackId}` : "/dashboard"} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Study Pack
              </Button>
            </Link>
          </div>
        </Card>
      ) : !adaptiveQuiz || !hasQuestions ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Practice Weak Areas</h1>
          <p className="text-sm text-foreground/75">
            {adaptiveQuiz?.message ?? "Adaptive practice is unavailable right now."}
          </p>
          <Link href={studyPackId ? `/study-packs/${studyPackId}/quick-review` : "/dashboard"} className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              Start Quick Review
            </Button>
          </Link>
        </Card>
      ) : isComplete ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice Complete
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">{adaptiveQuiz.title}</h1>
          <p className="text-sm text-foreground/75">
            Score: {score} / {quiz.length} ({scorePercentage}%)
          </p>
          {adaptiveQuiz.weakConcepts.length > 0 ? (
            <div className="rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-foreground/80">
              <p className="text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
                Targeted Weak Areas
              </p>
              <ul className="mt-2 list-disc space-y-1 pl-5">
                {adaptiveQuiz.weakConcepts.map((concept) => (
                  <li key={concept}>{concept}</li>
                ))}
              </ul>
            </div>
          ) : null}
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link href={`/study-packs/${studyPackId}`} className="w-full sm:w-auto">
              <Button type="button" className="w-full sm:w-auto">
                Back to Study Pack
              </Button>
            </Link>
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => void loadAdaptiveQuiz()}>
              Generate New Set
            </Button>
          </div>
        </Card>
      ) : (
        <div className="space-y-4">
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Adaptive Practice
            </p>
            <h1 className="text-xl font-semibold sm:text-2xl">{adaptiveQuiz.title}</h1>
            <p className="text-sm text-foreground/75">
              New follow-up practice based on your weak areas.
            </p>
            <p className="text-sm text-foreground/75">
              Question {currentIndex + 1} of {quiz.length}
            </p>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold">
              {currentIndex + 1}. {currentQuestion?.question}
            </h2>
            {currentQuestion ? (
              <QuizChoiceList
                choices={currentQuestion.choices}
                correctAnswer={currentQuestion.answer}
                selectedChoice={selectedChoice}
                revealAnswer={hasAnsweredCurrent}
                onSelectChoice={handleSelectChoice}
              />
            ) : null}

            {hasAnsweredCurrent && currentQuestion ? (
              <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
                <p>
                  <span className="font-medium text-foreground">Explanation:</span>{" "}
                  {currentQuestion.explanation}
                </p>
              </div>
            ) : null}

            <div className="flex justify-stretch sm:justify-end">
              <Button type="button" className="w-full sm:w-auto" onClick={handleNext} disabled={!hasAnsweredCurrent}>
                {currentIndex + 1 >= quiz.length ? "Finish Adaptive Practice" : "Next Question"}
              </Button>
            </div>
          </Card>
        </div>
      )}
    </main>
  );
}
