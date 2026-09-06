"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizQuestionText } from "@/components/study-pack/quiz-question-text";
import { LoadingSpinner } from "@/components/ui/loading-spinner";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";
import {
  ApiRequestError,
  getPublicSharedQuiz,
  getSharedQuizResults,
  trackAnalyticsEvent,
  type PublicSharedQuizResponse,
  type SharedQuizResultsResponse,
} from "@/lib/api";

const MULTI_SELECT_FORMAT = "MULTI_SELECT";

export default function SharedQuizPage() {
  const params = useParams<{ token: string }>();
  const token = params.token;
  const [quiz, setQuiz] = useState<PublicSharedQuizResponse | null>(null);
  const [results, setResults] = useState<SharedQuizResultsResponse | null>(null);
  // Index-aligned with the questions: `answers` is null wherever the question is MULTI_SELECT, and
  // `multiAnswers` is null everywhere else. The server reads them positionally.
  const [answers, setAnswers] = useState<(number | null)[]>([]);
  const [multiAnswers, setMultiAnswers] = useState<(number[] | null)[]>([]);
  const [selectedAnswer, setSelectedAnswer] = useState<number | null>(null);
  const [selectedMultiAnswers, setSelectedMultiAnswers] = useState<number[]>([]);
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
      setAnswers([]);
      setMultiAnswers([]);
      setSelectedAnswer(null);
      setSelectedMultiAnswers([]);
      setCurrentIndex(0);
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
  const isMultiSelect = currentQuestion?.questionFormat === MULTI_SELECT_FORMAT;
  const hasSelection = isMultiSelect ? selectedMultiAnswers.length > 0 : selectedAnswer !== null;
  // hasSelection is recomputed every render, so memoizing this saved nothing.
  const answeredQuestions = answers.length + (hasSelection ? 1 : 0);

  const toggleMultiAnswer = useCallback((choiceIndex: number) => {
    setSelectedMultiAnswers((current) => (
      current.includes(choiceIndex)
        ? current.filter((index) => index !== choiceIndex)
        : [...current, choiceIndex].sort((a, b) => a - b)
    ));
  }, []);

  const handleContinue = useCallback(async () => {
    if (!quiz || !hasSelection || submitting) {
      return;
    }
    const nextAnswers = [...answers, isMultiSelect ? null : selectedAnswer];
    const nextMultiAnswers = [...multiAnswers, isMultiSelect ? [...selectedMultiAnswers] : null];
    const isLastQuestion = currentIndex >= quiz.questions.length - 1;
    if (!isLastQuestion) {
      setAnswers(nextAnswers);
      setMultiAnswers(nextMultiAnswers);
      setSelectedAnswer(null);
      setSelectedMultiAnswers([]);
      setCurrentIndex((index) => index + 1);
      return;
    }

    setSubmitting(true);
    setSubmitError(null);
    try {
      const checkedResults = await getSharedQuizResults(token, nextAnswers, nextMultiAnswers);
      setAnswers(nextAnswers);
      setMultiAnswers(nextMultiAnswers);
      setResults(checkedResults);
      // Fires only after grading SUCCEEDS, so a failed submit the recipient retries is not counted as a
      // completion. Paired with QUIZ_SHARE_LINK_OPENED (fired on load) this gives the funnel a readable
      // opened -> completed rate, which is the proximal metric the v0.121.0 checkpoint reads; without it
      // that checkpoint would be decorative, since enum membership is not instrumentation.
      void trackAnalyticsEvent({
        eventType: "QUIZ_SHARE_LINK_COMPLETED",
        entityId: quiz.quizId,
        metadata: { token, score: checkedResults.score, total: checkedResults.total },
      });
    } catch {
      setSubmitError("Could not submit answers. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }, [
    answers,
    currentIndex,
    hasSelection,
    isMultiSelect,
    multiAnswers,
    quiz,
    selectedAnswer,
    selectedMultiAnswers,
    submitting,
    token,
  ]);

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
          <Link href="/" className={buttonVariants({ wrap: true, className: "w-full sm:w-auto" })}>
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
              const userMultiAnswers = multiAnswers[index];
              const correctIndex = result?.correctIndex ?? -1;
              // One rule, no format branching: the server sends a set only for MULTI_SELECT.
              const correctIndices = result?.correctIndices ?? [];
              return (
                <Card key={`${question.question}-${index}`} className="space-y-4 p-4 sm:p-6">
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">
                      Question {index + 1}
                    </p>
                    <h2 className="text-lg font-semibold"><QuizQuestionText text={question.question} /></h2>
                  </div>
                  <div className="grid gap-2">
                    {question.choices.map((choice, choiceIndex) => {
                      const isCorrect = correctIndices.length > 0
                        ? correctIndices.includes(choiceIndex)
                        : choiceIndex === correctIndex;
                      const isUserAnswer = userMultiAnswers
                        ? userMultiAnswers.includes(choiceIndex)
                        : choiceIndex === userAnswerIndex;
                      return (
                        <div
                          key={`${choice}-${choiceIndex}`}
                          className={[
                            "rounded-lg border px-3 py-2 text-sm",
                            isCorrect ? "border-emerald-500/40 bg-emerald-500/10" : "border-border",
                            isUserAnswer && !isCorrect ? "border-red-500/40 bg-red-500/10" : "",
                          ].join(" ")}
                        >
                          <span className="font-semibold">{String.fromCharCode(65 + choiceIndex)}.</span> {renderMathText(choice)}
                          {isUserAnswer ? <span className="ml-2 text-xs text-foreground/60">Your answer</span> : null}
                          {isCorrect ? <span className="ml-2 text-xs text-emerald-700 dark:text-emerald-300">Correct</span> : null}
                        </div>
                      );
                    })}
                  </div>
                  {result?.explanation ? (
                    <p className="text-sm leading-6 text-foreground/75">{renderMathText(result.explanation)}</p>
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
              <h2 className="text-xl font-semibold leading-8"><QuizQuestionText text={currentQuestion.question} /></h2>
              {currentQuestion.concept ? (
                <p className="inline-flex w-fit items-center gap-1.5 rounded-full bg-muted px-2.5 py-1 text-xs text-foreground/70">
                  <span className="font-semibold uppercase tracking-wide text-foreground/55">Topic</span>
                  <span aria-hidden="true">·</span>
                  <span>{currentQuestion.concept}</span>
                </p>
              ) : null}
              {isMultiSelect ? (
                <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Select all that apply</p>
              ) : null}
            </div>
            <div className="grid gap-3">
              {currentQuestion.choices.map((choice, index) => {
                const isSelected = isMultiSelect ? selectedMultiAnswers.includes(index) : selectedAnswer === index;
                return (
                  <button
                    key={`${choice}-${index}`}
                    type="button"
                    aria-pressed={isSelected}
                    className={[
                      "motion-pressable flex w-full items-start gap-2 rounded-xl border px-4 py-3 text-left text-sm font-medium transition-colors",
                      isSelected
                        ? "border-primary bg-primary/10 text-foreground"
                        : "border-border bg-background hover:bg-highlight disabled:hover:bg-background",
                    ].join(" ")}
                    onClick={() => (isMultiSelect ? toggleMultiAnswer(index) : setSelectedAnswer(index))}
                    // A single-choice answer is committed on click, as it always has been. A multi-select
                    // answer stays editable until Continue, or the recipient could never pick a second one.
                    disabled={!isMultiSelect && selectedAnswer !== null}
                  >
                    {isMultiSelect ? (
                      <span
                        className={[
                          "mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-[3px] border text-[10px] leading-none",
                          isSelected
                            ? "border-primary bg-primary text-primary-foreground"
                            : "border-foreground/35 bg-background text-transparent",
                        ].join(" ")}
                        aria-hidden="true"
                      >
                        {isSelected ? "\u2713" : ""}
                      </span>
                    ) : null}
                    <span className="min-w-0 flex-1">
                      <span className="mr-2 font-semibold">{String.fromCharCode(65 + index)}.</span>
                      {renderMathText(choice)}
                    </span>
                  </button>
                );
              })}
            </div>
            {submitError ? (
              <p className="text-sm text-red-600 dark:text-red-400">{submitError}</p>
            ) : null}
            <div className="flex justify-end">
              <Button
                type="button"
                onClick={() => void handleContinue()}
                disabled={!hasSelection}
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
