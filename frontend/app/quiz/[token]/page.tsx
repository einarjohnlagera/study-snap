"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { LoadingSpinner } from "@/components/ui/loading-spinner";
import {
  ApiRequestError,
  getPublicSharedQuiz,
  getSharedQuizResults,
  trackAnalyticsEvent,
  type PublicSharedQuizResponse,
  type SharedQuizResultsResponse,
} from "@/lib/api";

export default function SharedQuizPage() {
  const params = useParams<{ token: string }>();
  const token = params.token;
  const [quiz, setQuiz] = useState<PublicSharedQuizResponse | null>(null);
  const [results, setResults] = useState<SharedQuizResultsResponse | null>(null);
  const [answers, setAnswers] = useState<number[]>([]);
  const [selectedAnswer, setSelectedAnswer] = useState<number | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [inactive, setInactive] = useState(false);
  const [fetchError, setFetchError] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const loadQuiz = useCallback(async () => {
    if (!token) {
      setInactive(true);
      setLoading(false);
      return;
    }
    setLoading(true);
    setInactive(false);
    setFetchError(false);
    try {
      const loadedQuiz = await getPublicSharedQuiz(token);
      setQuiz(loadedQuiz);
      void trackAnalyticsEvent({
        eventType: "QUIZ_SHARE_LINK_OPENED",
        entityId: loadedQuiz.quizId,
        metadata: { token },
      });
    } catch (err) {
      setQuiz(null);
      if (err instanceof ApiRequestError && (err.status === 404 || err.status === 410)) {
        setInactive(true);
      } else {
        setFetchError(true);
      }
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void loadQuiz();
  }, [loadQuiz]);

  const currentQuestion = quiz?.questions[currentIndex] ?? null;
  const questionCount = quiz?.questions.length ?? 0;
  const progressLabel = questionCount > 0 ? `Question ${currentIndex + 1} of ${questionCount}` : "Shared quiz";
  const answeredQuestions = useMemo(() => answers.length + (selectedAnswer === null ? 0 : 1), [answers.length, selectedAnswer]);

  const handleContinue = useCallback(async () => {
    if (!quiz || selectedAnswer === null || submitting) {
      return;
    }
    const nextAnswers = [...answers, selectedAnswer];
    const isLastQuestion = currentIndex >= quiz.questions.length - 1;
    if (!isLastQuestion) {
      setAnswers(nextAnswers);
      setSelectedAnswer(null);
      setCurrentIndex((index) => index + 1);
      return;
    }

    setSubmitting(true);
    setSubmitError(null);
    try {
      const checkedResults = await getSharedQuizResults(token, nextAnswers);
      setAnswers(nextAnswers);
      setResults(checkedResults);
    } catch {
      setSubmitError("Could not submit answers. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }, [answers, currentIndex, quiz, selectedAnswer, submitting, token]);

  if (loading) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">
        <Card className="flex w-full max-w-md items-center justify-center gap-3 p-6">
          <LoadingSpinner />
          <span className="text-sm text-foreground/75">Loading quiz...</span>
        </Card>
      </main>
    );
  }

  if (fetchError) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">
        <Card className="w-full max-w-lg space-y-4 p-6 text-center">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Shared Quiz</p>
          <h1 className="text-2xl font-semibold">Something went wrong</h1>
          <p className="text-sm leading-6 text-foreground/75">
            We couldn&apos;t load the quiz. Please check your connection and try again.
          </p>
          <Button type="button" className="w-full sm:w-auto" onClick={() => void loadQuiz()}>
            Try Again
          </Button>
        </Card>
      </main>
    );
  }

  if (inactive || !quiz) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">
        <Card className="w-full max-w-lg space-y-4 p-6 text-center">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Shared Quiz</p>
          <h1 className="text-2xl font-semibold">This quiz link is no longer active</h1>
          <p className="text-sm leading-6 text-foreground/75">
            The teacher may have turned sharing off, or the link may have been removed.
          </p>
          <Link href="/" className={buttonVariants({ className: "w-full sm:w-auto" })}>
            Learn about NoteLib
          </Link>
        </Card>
      </main>
    );
  }

  if (results) {
    return (
      <main className="min-h-screen bg-background px-4 py-8 text-foreground sm:px-6 lg:px-8">
        <div className="mx-auto flex w-full max-w-4xl flex-col gap-6">
          <Card className="space-y-3 p-5 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Quiz Complete
            </p>
            <h1 className="text-2xl font-semibold sm:text-3xl">{quiz.noteTitle}</h1>
            <p className="text-lg font-semibold">
              Score: {results.score} / {results.total} correct
            </p>
            <TrackedLink
              href="/signup"
              eventType="QUIZ_SHARE_LINK_OPENED"
              entityId={quiz.quizId}
              eventMetadata={{ token, source: "shared_quiz_results_signup_cta" }}
              className={buttonVariants({ className: "w-full sm:w-auto" })}
            >
              Save your score and start studying with your own notes
            </TrackedLink>
          </Card>

          <div className="space-y-4">
            {quiz.questions.map((question, index) => {
              const result = results.items[index];
              const userAnswerIndex = answers[index];
              const correctIndex = result?.correctIndex ?? -1;
              return (
                <Card key={`${question.question}-${index}`} className="space-y-4 p-4 sm:p-6">
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">
                      Question {index + 1}
                    </p>
                    <h2 className="text-lg font-semibold">{question.question}</h2>
                  </div>
                  <div className="grid gap-2">
                    {question.choices.map((choice, choiceIndex) => {
                      const isCorrect = choiceIndex === correctIndex;
                      const isUserAnswer = choiceIndex === userAnswerIndex;
                      return (
                        <div
                          key={`${choice}-${choiceIndex}`}
                          className={[
                            "rounded-lg border px-3 py-2 text-sm",
                            isCorrect ? "border-emerald-500/40 bg-emerald-500/10" : "border-border",
                            isUserAnswer && !isCorrect ? "border-red-500/40 bg-red-500/10" : "",
                          ].join(" ")}
                        >
                          <span className="font-semibold">{String.fromCharCode(65 + choiceIndex)}.</span> {choice}
                          {isUserAnswer ? <span className="ml-2 text-xs text-foreground/60">Your answer</span> : null}
                          {isCorrect ? <span className="ml-2 text-xs text-emerald-700 dark:text-emerald-300">Correct</span> : null}
                        </div>
                      );
                    })}
                  </div>
                  {result?.explanation ? (
                    <p className="text-sm leading-6 text-foreground/75">{result.explanation}</p>
                  ) : null}
                </Card>
              );
            })}
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-background px-4 py-8 text-foreground sm:px-6 lg:px-8">
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
        <Card className="space-y-3 p-5 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Shared Quiz
          </p>
          <h1 className="text-2xl font-semibold sm:text-3xl">{quiz.noteTitle}</h1>
          <p className="text-sm text-foreground/70">
            {questionCount} question{questionCount === 1 ? "" : "s"} · {answeredQuestions} answered
          </p>
        </Card>

        {currentQuestion ? (
          <Card className="space-y-5 p-5 sm:p-6">
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">{progressLabel}</p>
              <h2 className="text-xl font-semibold leading-8">{currentQuestion.question}</h2>
              {currentQuestion.concept ? (
                <p className="text-sm text-foreground/60">{currentQuestion.concept}</p>
              ) : null}
            </div>
            <div className="grid gap-3">
              {currentQuestion.choices.map((choice, index) => (
                <button
                  key={`${choice}-${index}`}
                  type="button"
                  className={[
                    "motion-pressable rounded-xl border px-4 py-3 text-left text-sm font-medium transition-colors",
                    selectedAnswer === index
                      ? "border-primary bg-primary/10 text-foreground"
                      : "border-border bg-background hover:bg-highlight disabled:hover:bg-background",
                  ].join(" ")}
                  onClick={() => setSelectedAnswer(index)}
                  disabled={selectedAnswer !== null}
                >
                  <span className="mr-2 font-semibold">{String.fromCharCode(65 + index)}.</span>
                  {choice}
                </button>
              ))}
            </div>
            {submitError ? (
              <p className="text-sm text-red-600 dark:text-red-400">{submitError}</p>
            ) : null}
            <div className="flex justify-end">
              <Button
                type="button"
                onClick={() => void handleContinue()}
                disabled={selectedAnswer === null}
                loading={submitting}
                loadingText="Checking..."
              >
                {currentIndex >= questionCount - 1 ? "Submit Answers" : "Next Question"}
              </Button>
            </div>
          </Card>
        ) : null}
      </div>
    </main>
  );
}
