"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter } from "next/navigation";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { getAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  type ChallengeQuizStartRequest,
  completeChallengeQuizSession,
  getInProgressChallengeQuizSession,
  getMyStudyPack,
  getNote,
  isEmailNotVerifiedError,
  startChallengeQuizSession,
  trackAnalyticsEvent,
  updateChallengeQuizSessionProgress,
  type NoteResponse,
  type ChallengeQuizSessionResponse,
  type ChallengeQuizStartResponse,
} from "@/lib/api";

type ChallengePhase = "prestart" | "running" | "complete" | "limit-reached";
type ChallengeSessionStatePayload = {
  selectedChoices?: Record<string, string>;
  timerStartedAtEpochSeconds?: number;
};
type ChallengeDifficulty = NonNullable<ChallengeQuizStartRequest["difficulty"]>;

const LEAVE_WARNING_MESSAGE = "Leave Challenge Quiz? Your progress is saved, but the timer will continue.";

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

function getChallengeResultMessage(scorePercentage: number) {
  if (scorePercentage >= 80) {
    return "Great work. You're mastering this.";
  }
  return "Keep going - you're improving.";
}

function getPerformanceBadgeClass(performanceLevel: string): string {
  if (performanceLevel === "Excellent") {
    return "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300";
  }
  if (performanceLevel === "Good") {
    return "border-blue-500/40 bg-blue-500/10 text-blue-700 dark:text-blue-300";
  }
  if (performanceLevel === "Fair") {
    return "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300";
  }
  return "border-orange-500/40 bg-orange-500/10 text-orange-700 dark:text-orange-300";
}

function toChoiceRecord(value: unknown): Record<number, string> {
  if (!value || typeof value !== "object") {
    return {};
  }
  const entries = Object.entries(value as Record<string, unknown>)
    .filter(([, v]) => typeof v === "string")
    .map(([k, v]) => [Number(k), v as string] as const)
    .filter(([k]) => Number.isInteger(k) && k >= 0);
  return Object.fromEntries(entries);
}

function resolveTimerStartedAtEpochSeconds(
  session: ChallengeQuizStartResponse,
  sessionState: ChallengeSessionStatePayload,
): number {
  if (typeof sessionState.timerStartedAtEpochSeconds === "number") {
    return sessionState.timerStartedAtEpochSeconds;
  }
  return Math.floor(Date.now() / 1000);
}

function resolveDeadlineEpochSeconds(
  session: ChallengeQuizStartResponse,
  sessionState: ChallengeSessionStatePayload,
): number {
  const startedAtEpochSeconds = resolveTimerStartedAtEpochSeconds(session, sessionState);
  return startedAtEpochSeconds + session.timeLimitSeconds;
}

function resolveRemainingSeconds(deadlineEpochSeconds: number): number {
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  return Math.max(0, deadlineEpochSeconds - nowEpochSeconds);
}

export default function ChallengeQuizPage() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useParams<{ id: string }>();
  const progressRef = useRef<{ currentIndex: number; selectedChoices: Record<number, string> }>({
    currentIndex: 0,
    selectedChoices: {},
  });
  const leaveGuardInsertedRef = useRef(false);
  const showLeaveDialogRef = useRef(false);
  const legacyRedirectTargetRef = useRef<string | null>(null);
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [challengeSession, setChallengeSession] = useState<ChallengeQuizStartResponse | null>(null);
  const [result, setResult] = useState<ChallengeQuizSessionResponse | null>(null);
  const [phase, setPhase] = useState<ChallengePhase>("prestart");
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [deadlineEpochSeconds, setDeadlineEpochSeconds] = useState<number | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedChoices, setSelectedChoices] = useState<Record<number, string>>({});
  const [timedOut, setTimedOut] = useState(false);
  const [showLeaveDialog, setShowLeaveDialog] = useState(false);
  const [showAnswerReview, setShowAnswerReview] = useState(false);
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [activePaywallModal, setActivePaywallModal] = useState<"difficulty-selection" | "challenge-quiz-limit" | null>(null);
  const [selectedDifficulty, setSelectedDifficulty] = useState<ChallengeDifficulty>("medium");

  const noteId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);
  const noteDetailHref = useMemo(() => (note ? `/notes/${note.id}` : "/library"), [note]);
  const openLockedFeaturePaywall = useCallback(
    (variant: "difficulty-selection" | "challenge-quiz-limit", source: string) => {
      void trackAnalyticsEvent({
        eventType: "FEATURE_LOCKED_CLICKED",
        metadata: {
          feature: variant === "difficulty-selection" ? "difficulty" : "quiz_limit",
          source,
          path: pathname,
          noteId,
        },
      });
      setActivePaywallModal(variant);
    },
    [noteId, pathname],
  );

  useEffect(() => {
    const syncVerification = () => {
      setIsEmailVerified(Boolean(getAuthUser()?.emailVerifiedAt));
    };
    syncVerification();
    window.addEventListener("studysnap-auth-change", syncVerification);
    return () => {
      window.removeEventListener("studysnap-auth-change", syncVerification);
    };
  }, []);

  const applyStartedSession = useCallback((started: ChallengeQuizStartResponse, forceRunning = false) => {
    const state = (started.sessionState ?? {}) as ChallengeSessionStatePayload;
    const restoredChoices = toChoiceRecord(state.selectedChoices);
    const normalizedIndex = Math.max(0, Math.min(started.currentQuestionIndex ?? 0, Math.max(0, started.quiz.length - 1)));
    const nextDeadlineEpochSeconds = resolveDeadlineEpochSeconds(started, state);

    setChallengeSession(started);
    setResult(null);
    setSelectedChoices(restoredChoices);
    setCurrentIndex(normalizedIndex);
    setDeadlineEpochSeconds(nextDeadlineEpochSeconds);
    setRemainingSeconds(resolveRemainingSeconds(nextDeadlineEpochSeconds));
    setTimedOut(false);
    setShowAnswerReview(false);
    setPhase(forceRunning || Boolean(started.sessionId) ? "running" : "prestart");
  }, []);

  const persistProgress = useCallback(
    (nextIndex: number, nextSelectedChoices: Record<number, string>, keepalive = false) => {
      if (!challengeSession?.sessionId) {
        return;
      }

      const sessionState = {
        selectedChoices: Object.fromEntries(
          Object.entries(nextSelectedChoices).map(([key, value]) => [String(key), value]),
        ),
      };

      void updateChallengeQuizSessionProgress(
        challengeSession.sessionId,
        {
          currentQuestionIndex: nextIndex,
          sessionState,
        },
        { keepalive },
      ).catch(() => {
        // Challenge should continue even if a progress sync fails.
      });
    },
    [challengeSession?.sessionId],
  );

  const loadNote = useCallback(async () => {
    if (!noteId) {
      setError("Note not found.");
      setLoading(false);
      return;
    }

    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const detail = await getNote(noteId);
      if (detail.studyPackStatus !== "STUDY_PACK_READY") {
        setNote(detail);
        setError("Generate a Study Pack first.");
        return;
      }
      setNote(detail);
      const authUser = getAuthUser();
      setIsEmailVerified(Boolean(authUser?.emailVerifiedAt));
      if (!authUser?.emailVerifiedAt) {
        setChallengeSession(null);
        setResult(null);
        setSelectedChoices({});
        setCurrentIndex(0);
        setDeadlineEpochSeconds(null);
        setRemainingSeconds(0);
        setTimedOut(false);
        setShowAnswerReview(false);
        setPhase("prestart");
        return;
      }

      const inProgress = await getInProgressChallengeQuizSession(detail.id);
      if (inProgress.sessionId) {
        setSelectedDifficulty(inProgress.selectedDifficulty);
        applyStartedSession(inProgress, true);
      } else {
        setSelectedDifficulty(inProgress.selectedDifficulty);
        setChallengeSession(null);
        setResult(null);
        setSelectedChoices({});
        setCurrentIndex(0);
        setDeadlineEpochSeconds(null);
        setRemainingSeconds(0);
        setTimedOut(false);
        setShowAnswerReview(false);
        const hasReachedMonthlyLimit = inProgress.usedThisMonth >= inProgress.monthlyLimit;
        setPhase(hasReachedMonthlyLimit ? "limit-reached" : "prestart");
        if (hasReachedMonthlyLimit) {
          setActivePaywallModal("challenge-quiz-limit");
        }
      }
    } catch (err) {
      if (pathname.startsWith("/study-packs/")) {
        const byStudyPack = await getMyStudyPack(noteId).catch(() => null);
        if (byStudyPack?.noteId) {
          const nextQuery = typeof window === "undefined"
            ? ""
            : window.location.search.replace(/^\?/, "");
          const targetHref = nextQuery
            ? `/notes/${byStudyPack.noteId}/challenge-quiz?${nextQuery}`
            : `/notes/${byStudyPack.noteId}/challenge-quiz`;
          if (legacyRedirectTargetRef.current !== targetHref) {
            legacyRedirectTargetRef.current = targetHref;
            router.replace(targetHref);
          }
          return;
        }
      }
      const message = err instanceof Error ? err.message : "Could not load this note.";
      setError(message);
      setNote(null);
    } finally {
      setLoading(false);
    }
  }, [applyStartedSession, noteId, pathname, router]);

  useEffect(() => {
    void loadNote();
  }, [loadNote]);

  const quiz = useMemo(() => challengeSession?.quiz ?? [], [challengeSession]);
  const totalQuestions = quiz.length;
  const answeredCount = useMemo(() => Object.keys(selectedChoices).length, [selectedChoices]);
  const currentQuestion = totalQuestions > 0 && currentIndex < totalQuestions ? quiz[currentIndex] : null;
  const selectedChoice = selectedChoices[currentIndex] ?? null;

  useEffect(() => {
    progressRef.current = {
      currentIndex,
      selectedChoices,
    };
  }, [currentIndex, selectedChoices]);

  const handleSubmit = useCallback(async (timeoutTriggered: boolean) => {
    if (!challengeSession?.sessionId || submitting) {
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
    if (phase !== "running" || !challengeSession || submitting || deadlineEpochSeconds === null) {
      return;
    }

    const tick = () => {
      const nextRemainingSeconds = resolveRemainingSeconds(deadlineEpochSeconds);
      setRemainingSeconds(nextRemainingSeconds);
      if (nextRemainingSeconds <= 0) {
        void handleSubmit(true);
      }
    };

    tick();
    const timer = window.setInterval(tick, 1000);

    return () => {
      window.clearInterval(timer);
    };
  }, [challengeSession, deadlineEpochSeconds, handleSubmit, phase, submitting]);

  useEffect(() => {
    showLeaveDialogRef.current = showLeaveDialog;
  }, [showLeaveDialog]);

  useEffect(() => {
    if (phase !== "running") {
      leaveGuardInsertedRef.current = false;
      return;
    }

    const persistLatestProgress = (keepalive = false) => {
      const latest = progressRef.current;
      persistProgress(latest.currentIndex, latest.selectedChoices, keepalive);
    };

    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      persistLatestProgress(true);
      event.preventDefault();
      event.returnValue = LEAVE_WARNING_MESSAGE;
      return LEAVE_WARNING_MESSAGE;
    };

    const handlePopState = () => {
      persistLatestProgress(true);
      if (showLeaveDialogRef.current) {
        window.history.pushState(null, "", window.location.href);
        return;
      }
      window.history.pushState(null, "", window.location.href);
      setShowLeaveDialog(true);
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "hidden") {
        persistLatestProgress(true);
      }
    };

    if (!leaveGuardInsertedRef.current) {
      window.history.pushState(null, "", window.location.href);
      leaveGuardInsertedRef.current = true;
    }
    window.addEventListener("beforeunload", handleBeforeUnload);
    window.addEventListener("popstate", handlePopState);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      window.removeEventListener("beforeunload", handleBeforeUnload);
      window.removeEventListener("popstate", handlePopState);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [persistProgress, phase]);

  const handleStartChallenge = useCallback(async () => {
    if (!note || starting) {
      return;
    }
    if (!isEmailVerified) {
      setError("Verify your email to use this feature.");
      return;
    }

    setStarting(true);
    setError(null);
    try {
      const request: ChallengeQuizStartRequest = note.difficultySelectionAvailable
        ? { difficulty: selectedDifficulty }
        : {};
      const started = await startChallengeQuizSession(note.id, request);
      if (!started.sessionId) {
        throw new Error("Could not start Challenge Quiz.");
      }
      setSelectedDifficulty(started.selectedDifficulty);
      applyStartedSession(started, true);
    } catch (err) {
      const message = isEmailNotVerifiedError(err)
        ? "Verify your email to use this feature."
        : err instanceof Error
          ? err.message
          : "Could not start Challenge Quiz.";
      setError(message);
      if (message.toLowerCase().includes("monthly challenge quiz limit")) {
        setPhase("limit-reached");
        openLockedFeaturePaywall("challenge-quiz-limit", "challenge_quiz_start");
      }
    } finally {
      setStarting(false);
    }
  }, [applyStartedSession, isEmailVerified, note, openLockedFeaturePaywall, selectedDifficulty, starting]);

  const handleRetry = () => {
    setChallengeSession(null);
    setResult(null);
    setSelectedChoices({});
    setCurrentIndex(0);
    setDeadlineEpochSeconds(null);
    setRemainingSeconds(0);
    setTimedOut(false);
    setError(null);
    setShowAnswerReview(false);
    setPhase("prestart");
  };

  const handleLeaveQuiz = () => {
    persistProgress(currentIndex, selectedChoices, true);
    setShowLeaveDialog(false);
    router.push(noteDetailHref);
  };

  const isNotFound = error?.toLowerCase().includes("not found") ?? false;

  return (
    <main className="mx-auto w-full max-w-3xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        {phase === "running" ? (
          <>
            <p className="text-sm font-medium text-foreground/80">Challenge Quiz in progress</p>
            <Button type="button" variant="outline" size="sm" onClick={() => setShowLeaveDialog(true)}>
              Leave Quiz
            </Button>
          </>
        ) : (
          <Link
            href={noteDetailHref}
            className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
          >
            Back to Note
          </Link>
        )}
      </div>

      {loading ? (
        <ChallengeQuizLoading />
      ) : error && !note ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">
            {isNotFound ? "Note not found" : "Could not load Challenge Quiz"}
          </h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            {!isNotFound ? (
              <Button type="button" className="w-full sm:w-auto" onClick={() => void loadNote()}>
                Retry
              </Button>
            ) : null}
            <Link href={noteDetailHref} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Note
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
          <Link href={noteDetailHref} className="w-full sm:w-auto">
            <Button type="button" variant="outline" className="w-full sm:w-auto">
              Back to Note
            </Button>
          </Link>
        </Card>
      ) : phase === "prestart" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Challenge Quiz
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">{note?.title ?? "Challenge Quiz"}</h1>
          <p className="text-sm text-foreground/80">
            We tailored this quiz based on your recent performance.
          </p>
          <div className="space-y-3 rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
            <div className="space-y-1">
              <p className="font-medium text-foreground">Difficulty</p>
              {note?.difficultySelectionAvailable ? (
                <p>Choose the level you want before you start.</p>
              ) : (
                <p>Difficulty selection is available on Premium. Free users get a recommended level automatically.</p>
              )}
            </div>
            {note?.difficultySelectionAvailable ? (
              <div className="grid gap-2 sm:grid-cols-3">
                {(["easy", "medium", "hard"] as const).map((difficulty) => (
                  <button
                    key={difficulty}
                    type="button"
                    className={`rounded-md border px-3 py-2 text-left capitalize transition ${
                      selectedDifficulty === difficulty
                        ? "border-blue-500 bg-blue-500/10 text-foreground"
                        : "border-border bg-background"
                    }`}
                    onClick={() => setSelectedDifficulty(difficulty)}
                  >
                    {difficulty}
                  </button>
                ))}
              </div>
            ) : (
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <p className="rounded-md border border-border bg-muted/40 px-3 py-2 text-sm capitalize">
                  Recommended difficulty: {selectedDifficulty}
                </p>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => openLockedFeaturePaywall("difficulty-selection", "challenge_quiz_difficulty_button")}
                >
                  Choose Difficulty (Premium)
                </Button>
              </div>
            )}
          </div>
          <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
            <p>Rules:</p>
            <ul className="mt-2 list-disc space-y-1 pl-5">
              <li>10-minute timer</li>
              <li>10 to 15 AI-generated questions</li>
              <li>If you leave, the timer still continues</li>
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
                selectionStyle="exam"
                onSelectChoice={(choice) => {
                  setSelectedChoices((previous) => {
                    const next = { ...previous, [currentIndex]: choice };
                    persistProgress(currentIndex, next);
                    return next;
                  });
                }}
              />
              <p className="text-xs text-foreground/65">Answers are graded only after submission.</p>
            </div>
          ) : null}
          {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => {
                const nextIndex = Math.max(0, currentIndex - 1);
                setCurrentIndex(nextIndex);
                persistProgress(nextIndex, selectedChoices);
              }}
              disabled={currentIndex <= 0 || submitting}
            >
              Previous
            </Button>
            {currentIndex < totalQuestions - 1 ? (
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => {
                  const nextIndex = Math.min(totalQuestions - 1, currentIndex + 1);
                  setCurrentIndex(nextIndex);
                  persistProgress(nextIndex, selectedChoices);
                }}
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
          <h1 className="text-xl font-semibold sm:text-2xl">{note?.title ?? "Challenge Quiz"}</h1>
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
            <p className="mt-2 text-sm text-foreground/75">{getChallengeResultMessage(result.scorePercentage)}</p>
            <div className={`mt-3 inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium ${getPerformanceBadgeClass(result.performanceLevel)}`}>
              {result.performanceLevel}
            </div>
          </div>
          <Card className="space-y-3 p-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Score Summary</h2>
            <div className="grid gap-2 sm:grid-cols-3">
              <div className="rounded-md border border-border bg-background px-3 py-2">
                <p className="text-xs text-foreground/65">Correct</p>
                <p className="text-sm font-semibold">{result.correctAnswers}</p>
              </div>
              <div className="rounded-md border border-border bg-background px-3 py-2">
                <p className="text-xs text-foreground/65">Total</p>
                <p className="text-sm font-semibold">{result.totalQuestions}</p>
              </div>
              <div className="rounded-md border border-border bg-background px-3 py-2">
                <p className="text-xs text-foreground/65">Percentage</p>
                <p className="text-sm font-semibold">{result.scorePercentage}%</p>
              </div>
            </div>
          </Card>
          <Card className="space-y-3 p-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Concept Breakdown</h2>
            {result.conceptBreakdown.length > 0 ? (
              <div className="space-y-2">
                {result.conceptBreakdown.map((stat) => (
                  <div key={stat.concept} className="rounded-md border border-border bg-background px-3 py-2">
                    <p className="text-sm font-medium">{stat.concept}</p>
                    <p className="text-xs text-foreground/70">
                      {stat.correctAnswers}/{stat.totalQuestions} correct ({stat.accuracyPercentage}%)
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-foreground/70">No concept breakdown is available for this session.</p>
            )}
          </Card>
          <Card className="space-y-3 p-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Weak Concepts</h2>
            {result.weakConcepts.length > 0 ? (
              <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/85">
                {result.weakConcepts.map((concept) => (
                  <li key={concept}>{concept}</li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-foreground/70">No weak concepts identified in this challenge.</p>
            )}
          </Card>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShowAnswerReview((previous) => !previous)}>
              {showAnswerReview ? "Hide Answer Review" : "Review Answers"}
            </Button>
            {result.weakConcepts.length > 0 ? (
              <Link href={note ? `/notes/${note.id}/adaptive-practice` : "/dashboard"} className="w-full sm:w-auto">
                <Button type="button" variant="outline" className="w-full sm:w-auto">
                  Practice Weak Concepts
                </Button>
              </Link>
            ) : null}
            <Button type="button" className="w-full sm:w-auto" onClick={handleRetry}>
              Start Another Challenge
            </Button>
            <Link href={noteDetailHref} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Note
              </Button>
            </Link>
          </div>
          {showAnswerReview ? (
            <div className="space-y-3 pt-2">
              {quiz.map((item, index) => (
                <Card key={`${item.question}-${index}`} className="space-y-3 p-4">
                  <h2 className="text-sm font-semibold">
                    {index + 1}. {item.question}
                  </h2>
                  <QuizChoiceList
                    choices={item.choices}
                    correctAnswer={item.answer}
                    selectedChoice={selectedChoices[index] ?? null}
                    revealAnswer
                  />
                </Card>
              ))}
            </div>
          ) : null}
        </Card>
      ) : null}

      <AppModal
        isOpen={showLeaveDialog}
        title="Leave Challenge Quiz?"
        description="Your progress is saved, but the timer will continue."
        onClose={() => setShowLeaveDialog(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setShowLeaveDialog(false)}>
              Stay
            </Button>
            <Button type="button" onClick={handleLeaveQuiz}>
              Leave Quiz
            </Button>
          </div>
        )}
      />

      <PaywallModal
        isOpen={activePaywallModal !== null}
        variant={activePaywallModal ?? "challenge-quiz-limit"}
        source="challenge_quiz_page"
        onClose={() => setActivePaywallModal(null)}
      />
    </main>
  );
}
