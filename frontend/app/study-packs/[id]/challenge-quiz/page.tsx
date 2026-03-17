"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { getAuthUser } from "@/lib/auth";
import { PLAN_BILLING_PATH } from "@/lib/plans";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import {
  completeChallengeQuizSession,
  getMyStudyPack,
  startChallengeQuizSession,
  type ChallengeQuizSessionResponse,
  type ChallengeQuizStartResponse,
  type StudyPackResponse,
} from "@/lib/api";

type ChallengePhase = "prestart" | "running" | "complete" | "premium-locked" | "limit-reached";

function formatTimer(seconds: number): string {
  const safeSeconds = Math.max(0, seconds);
  const minutes = Math.floor(safeSeconds / 60);
  const remaining = safeSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(remaining).padStart(2, "0")}`;
}

function ChallengeQuizLoading() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="h-4 w-40 animate-pulse rounded bg-foreground/10" />
      <div className="h-7 w-3/4 animate-pulse rounded bg-foreground/10" />
      <div className="h-10 w-1/3 animate-pulse rounded bg-foreground/10" />
    </Card>
  );
}

export default function ChallengeQuizPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const [studyPack, setStudyPack] = useState<StudyPackResponse | null>(null);
  const [challengeSession, setChallengeSession] = useState<ChallengeQuizStartResponse | null>(null);
  const [result, setResult] = useState<ChallengeQuizSessionResponse | null>(null);
  const [phase, setPhase] = useState<ChallengePhase>("prestart");
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedChoices, setSelectedChoices] = useState<Record<number, string>>({});
  const [timedOut, setTimedOut] = useState(false);

  const studyPackId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);

  const loadStudyPack = useCallback(async () => {
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
      const detail = await getMyStudyPack(studyPackId);
      setStudyPack(detail);
      const planType = getAuthUser()?.planType ?? "FREE";
      setPhase(planType === "PREMIUM" ? "prestart" : "premium-locked");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load this Study Pack.";
      setError(message);
      setStudyPack(null);
    } finally {
      setLoading(false);
    }
  }, [router, studyPackId]);

  useEffect(() => {
    void loadStudyPack();
  }, [loadStudyPack]);

  const quiz = useMemo(() => challengeSession?.quiz ?? [], [challengeSession]);
  const totalQuestions = quiz.length;
  const answeredCount = useMemo(() => Object.keys(selectedChoices).length, [selectedChoices]);
  const currentQuestion = totalQuestions > 0 && currentIndex < totalQuestions ? quiz[currentIndex] : null;
  const selectedChoice = selectedChoices[currentIndex] ?? null;

  const handleSubmit = useCallback(async (timeoutTriggered: boolean) => {
    if (!challengeSession || submitting) {
      return;
    }

    const total = challengeSession.quiz.length;
    const correctAnswers = challengeSession.quiz.reduce((count, item, index) => {
      return selectedChoices[index] === item.answer ? count + 1 : count;
    }, 0);
    const durationSeconds = Math.max(0, challengeSession.timeLimitSeconds - remainingSeconds);

    setSubmitting(true);
    setError(null);
    setTimedOut(timeoutTriggered);
    try {
      const completed = await completeChallengeQuizSession(challengeSession.sessionId, {
        correctAnswers,
        totalQuestions: total,
        durationSeconds,
      });
      setResult(completed);
      setPhase("complete");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not save Challenge Quiz results.";
      setError(message);
    } finally {
      setSubmitting(false);
    }
  }, [challengeSession, remainingSeconds, selectedChoices, submitting]);

  useEffect(() => {
    if (phase !== "running" || !challengeSession || submitting) {
      return;
    }
    if (remainingSeconds <= 0) {
      void handleSubmit(true);
      return;
    }

    const timer = window.setTimeout(() => {
      setRemainingSeconds((previous) => Math.max(0, previous - 1));
    }, 1000);

    return () => {
      window.clearTimeout(timer);
    };
  }, [challengeSession, handleSubmit, phase, remainingSeconds, submitting]);

  const handleStartChallenge = useCallback(async () => {
    if (!studyPack || starting) {
      return;
    }

    setStarting(true);
    setError(null);
    try {
      const started = await startChallengeQuizSession(studyPack.id);
      setChallengeSession(started);
      setResult(null);
      setSelectedChoices({});
      setCurrentIndex(0);
      setRemainingSeconds(started.timeLimitSeconds);
      setTimedOut(false);
      setPhase("running");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not start Challenge Quiz.";
      setError(message);
      if (message.toLowerCase().includes("monthly challenge quiz limit")) {
        setPhase("limit-reached");
      } else if (message.toLowerCase().includes("premium")) {
        setPhase("premium-locked");
      }
    } finally {
      setStarting(false);
    }
  }, [starting, studyPack]);

  const handleRetry = () => {
    setChallengeSession(null);
    setResult(null);
    setSelectedChoices({});
    setCurrentIndex(0);
    setRemainingSeconds(0);
    setTimedOut(false);
    setError(null);
    setPhase("prestart");
  };

  const isNotFound = error?.toLowerCase().includes("not found") ?? false;

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
        <ChallengeQuizLoading />
      ) : error && !studyPack ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">
            {isNotFound ? "Study Pack not found" : "Could not load Challenge Quiz"}
          </h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            {!isNotFound ? (
              <Button type="button" className="w-full sm:w-auto" onClick={() => void loadStudyPack()}>
                Retry
              </Button>
            ) : null}
            <Link href={studyPackId ? `/study-packs/${studyPackId}` : "/dashboard"} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Study Pack
              </Button>
            </Link>
          </div>
        </Card>
      ) : phase === "premium-locked" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Challenge Quiz
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Premium feature</h1>
          <p className="text-sm text-foreground/75">This feature is available in the Premium plan.</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link href={PLAN_BILLING_PATH} className="w-full sm:w-auto">
              <Button type="button" className="w-full sm:w-auto">
                Upgrade to Premium
              </Button>
            </Link>
            <Link href={studyPackId ? `/study-packs/${studyPackId}` : "/dashboard"} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Study Pack
              </Button>
            </Link>
          </div>
        </Card>
      ) : phase === "limit-reached" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Challenge Quiz
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Monthly limit reached</h1>
          <p className="text-sm text-foreground/75">You&apos;ve reached your monthly Challenge Quiz limit.</p>
          <Link href={studyPackId ? `/study-packs/${studyPackId}` : "/dashboard"} className="w-full sm:w-auto">
            <Button type="button" variant="outline" className="w-full sm:w-auto">
              Back to Study Pack
            </Button>
          </Link>
        </Card>
      ) : phase === "prestart" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Challenge Quiz
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">{studyPack?.title ?? "Challenge Quiz"}</h1>
          <p className="text-sm text-foreground/80">
            Timed exam-style quiz based on this Study Pack&apos;s existing questions. No new generation call is made.
          </p>
          <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
            <p>Rules:</p>
            <ul className="mt-2 list-disc space-y-1 pl-5">
              <li>10-minute timer</li>
              <li>10 to 20 shuffled questions</li>
              <li>Submit before time runs out for your final score</li>
            </ul>
          </div>
          {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
          <Button type="button" className="w-full sm:w-auto" onClick={() => void handleStartChallenge()} disabled={starting}>
            {starting ? "Starting..." : "Start Challenge Quiz"}
          </Button>
        </Card>
      ) : phase === "running" && challengeSession ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Challenge Quiz
            </p>
            <p className="rounded-md border border-border bg-background px-3 py-1 text-sm font-semibold">
              {formatTimer(remainingSeconds)}
            </p>
          </div>
          <div className="flex items-center justify-between gap-3 text-sm text-foreground/75">
            <p>Question {Math.min(currentIndex + 1, totalQuestions)} of {totalQuestions}</p>
            <p>{answeredCount} answered</p>
          </div>
          {currentQuestion ? (
            <div className="space-y-3">
              <h2 className="text-base font-semibold sm:text-lg">{currentQuestion.question}</h2>
              <QuizChoiceList
                choices={currentQuestion.choices}
                correctAnswer={currentQuestion.answer}
                selectedChoice={selectedChoice}
                revealAnswer={false}
                onSelectChoice={(choice) => {
                  setSelectedChoices((previous) => ({ ...previous, [currentIndex]: choice }));
                }}
              />
            </div>
          ) : null}
          {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => setCurrentIndex((previous) => Math.max(0, previous - 1))}
              disabled={currentIndex <= 0 || submitting}
            >
              Previous
            </Button>
            {currentIndex < totalQuestions - 1 ? (
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => setCurrentIndex((previous) => Math.min(totalQuestions - 1, previous + 1))}
                disabled={submitting}
              >
                Next
              </Button>
            ) : (
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleSubmit(false)}
                disabled={submitting}
              >
                {submitting ? "Submitting..." : "Submit Challenge Quiz"}
              </Button>
            )}
          </div>
        </Card>
      ) : phase === "complete" && result ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Challenge Quiz Result
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">{studyPack?.title ?? "Challenge Quiz"}</h1>
          <div className="rounded-md border border-border bg-background p-4">
            <p className="text-lg font-semibold">{result.scorePercentage}%</p>
            <p className="mt-1 text-sm text-foreground/80">
              {result.correctAnswers} / {result.totalQuestions} correct
            </p>
            <p className="mt-1 text-sm text-foreground/70">
              Duration: {formatTimer(result.durationSeconds ?? 0)}
            </p>
            {timedOut ? (
              <p className="mt-2 text-sm text-foreground/75">Time ran out. Your answers were submitted automatically.</p>
            ) : null}
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={handleRetry}>
              Start Another Challenge
            </Button>
            <Link href={studyPackId ? `/study-packs/${studyPackId}` : "/dashboard"} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Study Pack
              </Button>
            </Link>
          </div>
        </Card>
      ) : null}
    </main>
  );
}
