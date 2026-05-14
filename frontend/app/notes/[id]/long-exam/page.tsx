"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { ToastMessage } from "@/components/ui/toast-message";
import { getAuthUser } from "@/lib/auth";
import {
  completeLongExamSession,
  forfeitLongExamSession,
  getActiveLongExamSession,
  getLongExamSession,
  getNote,
  pauseLongExamSession,
  resumeLongExamSession,
  saveLongExamProgress,
  startLongExam,
  trackAnalyticsEvent,
  type LongExamMasteryReportResponse,
  type LongExamSessionResponse,
  type LongExamStartResponse,
  type NoteResponse,
  type QuizItem,
} from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  resolveBoardExamTimerState,
  resolveDeadlineEpochSeconds,
  resolveRemainingSecondsFromDeadline,
} from "@/lib/challenge-quiz-timer";
import { resolveQuizCorrectIndex } from "@/lib/quiz";
import { cn } from "@/lib/utils";

type LongExamPhase = "prestart" | "generating" | "paused-recovery" | "running" | "complete";
type LongExamDifficulty = "easy" | "medium" | "hard" | "mixed";
type ToastState = {
  message: string;
  tone: "success" | "error" | "info";
};

const LONG_EXAM_POLL_INTERVAL_MS = 2000;
const LONG_EXAM_DIFFICULTIES: Array<{ value: LongExamDifficulty; label: string; description: string }> = [
  { value: "easy", label: "Easy", description: "Build confidence with lighter recall." },
  { value: "medium", label: "Medium", description: "Balanced recall and application." },
  { value: "hard", label: "Hard", description: "Push deeper understanding." },
  { value: "mixed", label: "Mixed", description: "Blend difficulty across the exam." },
];

function normalizeSelectedChoices(selectedChoices?: Record<string, number> | null): Record<string, number> {
  return { ...(selectedChoices ?? {}) };
}

function getSelectedChoice(
  selectedChoices: Record<string, number>,
  questionIndex: number,
): number | null {
  return selectedChoices[String(questionIndex)] ?? null;
}

function getAnsweredCount(selectedChoices: Record<string, number>): number {
  return Object.keys(selectedChoices).length;
}

function getNowEpochSeconds(): number {
  return Math.floor(Date.now() / 1000);
}

function formatTimer(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export default function LongExamPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const noteId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);

  const [phase, setPhase] = useState<LongExamPhase>("prestart");
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [savingProgress, setSavingProgress] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [pausing, setPausing] = useState(false);
  const [forfeiting, setForfeiting] = useState(false);
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [difficulty, setDifficulty] = useState<LongExamDifficulty>("medium");
  const [activeStartResponse, setActiveStartResponse] = useState<LongExamStartResponse | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [quiz, setQuiz] = useState<QuizItem[]>([]);
  const [selectedChoices, setSelectedChoices] = useState<Record<string, number>>({});
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [masteryReport, setMasteryReport] = useState<LongExamMasteryReportResponse | null>(null);
  const [deadlineEpochSeconds, setDeadlineEpochSeconds] = useState<number | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [timeLimitSeconds, setTimeLimitSeconds] = useState(0);
  const remainingSecondsRef = useRef(0);
  const timeoutAutoSubmitRequestedRef = useRef(false);
  const startedTrackedRef = useRef(false);

  const noteDetailHref = useMemo(() => (note ? `/notes/${note.id}` : `/notes/${noteId}`), [note, noteId]);
  const studyPackId = note?.studyPackId ?? null;
  const totalQuestions = quiz.length;
  const currentQuestion = totalQuestions > 0 ? quiz[currentQuestionIndex] ?? null : null;
  const answeredCount = useMemo(() => getAnsweredCount(selectedChoices), [selectedChoices]);
  const progressPercentage = totalQuestions > 0 ? Math.round((answeredCount / totalQuestions) * 100) : 0;
  const hasActiveInProgressPrompt = activeStartResponse?.status === "IN_PROGRESS" && activeStartResponse.canResume;
  const timerState = resolveBoardExamTimerState(remainingSeconds);

  const showToast = useCallback((message: string, tone: ToastState["tone"] = "info") => {
    setToast({ message, tone });
  }, []);

  const applyTimer = useCallback((response: { timeLimitSeconds: number; timerStartedAtEpochSeconds: number }) => {
    const deadline = resolveDeadlineEpochSeconds(
      response.timeLimitSeconds,
      { timerStartedAtEpochSeconds: response.timerStartedAtEpochSeconds },
      getNowEpochSeconds(),
    );
    const nextRemainingSeconds = resolveRemainingSecondsFromDeadline(deadline, getNowEpochSeconds());
    setTimeLimitSeconds(response.timeLimitSeconds);
    setDeadlineEpochSeconds(deadline);
    setRemainingSeconds(nextRemainingSeconds);
    remainingSecondsRef.current = nextRemainingSeconds;
    timeoutAutoSubmitRequestedRef.current = false;
  }, []);

  const trackStarted = useCallback((response: { sessionId: string; totalQuestions?: number; difficulty?: string | null }) => {
    if (startedTrackedRef.current) {
      return;
    }
    startedTrackedRef.current = true;
    void trackAnalyticsEvent({
      eventType: "LONG_EXAM_STARTED",
      entityId: response.sessionId,
      metadata: {
        noteId,
        studyPackId,
        totalQuestions: response.totalQuestions ?? null,
        difficulty: response.difficulty ?? null,
      },
    });
  }, [noteId, studyPackId]);

  const enterRunningFromStart = useCallback((response: LongExamStartResponse) => {
    setActiveStartResponse(null);
    setSessionId(response.sessionId);
    setQuiz(response.quiz);
    setSelectedChoices({});
    setCurrentQuestionIndex(0);
    applyTimer(response);
    setPhase("running");
    setError(null);
    trackStarted(response);
  }, [applyTimer, trackStarted]);

  const enterRunningFromSession = useCallback((response: LongExamSessionResponse) => {
    setActiveStartResponse(null);
    setSessionId(response.sessionId);
    setQuiz(response.quiz);
    setSelectedChoices(normalizeSelectedChoices(response.selectedChoices));
    setCurrentQuestionIndex(Math.min(Math.max(response.currentQuestionIndex, 0), Math.max(response.totalQuestions - 1, 0)));
    applyTimer(response);
    setPhase("running");
    setError(null);
    trackStarted(response);
  }, [applyTimer, trackStarted]);

  const loadInitialState = useCallback(async () => {
    if (!noteId) {
      setError("Note not found.");
      setLoading(false);
      return;
    }
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    const authUser = getAuthUser();
    if (authUser?.planType !== "PRO") {
      router.replace(`/notes/${noteId}`);
      return;
    }

    try {
      const noteDetail = await getNote(noteId);
      setNote(noteDetail);
      if (!noteDetail.studyPackId || noteDetail.studyPackStatus !== "STUDY_PACK_READY") {
        setError("Generate a Study Pack before starting a Long Exam.");
        return;
      }

      const activeSession = await getActiveLongExamSession(noteDetail.studyPackId);
      if (!activeSession) {
        setActiveStartResponse(null);
        return;
      }

      setActiveStartResponse(activeSession);
      setSessionId(activeSession.sessionId);
      if (activeSession.status === "GENERATING") {
        setPhase("generating");
        trackStarted(activeSession);
        return;
      }
      if (activeSession.status === "PAUSED" && activeSession.canResume) {
        setPhase("paused-recovery");
        return;
      }
      if (activeSession.status === "IN_PROGRESS" && !activeSession.canResume && activeSession.quiz.length > 0) {
        enterRunningFromStart(activeSession);
        return;
      }
      if (activeSession.status === "COMPLETED") {
        router.replace(`/notes/${noteDetail.id}`);
        return;
      }
      if (activeSession.status === "FAILED") {
        setError("The previous Long Exam could not be generated. You can start again.");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load Long Exam.");
    } finally {
      setLoading(false);
    }
  }, [enterRunningFromStart, noteId, router, trackStarted]);

  useEffect(() => {
    void loadInitialState();
  }, [loadInitialState]);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timeoutId = globalThis.setTimeout(() => setToast(null), 3000);
    return () => globalThis.clearTimeout(timeoutId);
  }, [toast]);

  useEffect(() => {
    if (phase !== "generating" || !studyPackId) {
      return;
    }

    const pollActiveSession = async () => {
      try {
        const activeSession = await getActiveLongExamSession(studyPackId);
        if (!activeSession || activeSession.status === "GENERATING") {
          return;
        }
        setActiveStartResponse(activeSession);
        setSessionId(activeSession.sessionId);
        if (activeSession.status === "PAUSED" && activeSession.canResume) {
          setPhase("paused-recovery");
          return;
        }
        if (activeSession.status === "IN_PROGRESS" && activeSession.quiz.length > 0) {
          enterRunningFromStart(activeSession);
          return;
        }
        if (activeSession.status === "FAILED") {
          setError("Long Exam generation failed. Please try again.");
          setPhase("prestart");
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Could not check Long Exam generation.");
        setPhase("prestart");
      }
    };

    const intervalId = globalThis.setInterval(() => {
      void pollActiveSession();
    }, LONG_EXAM_POLL_INTERVAL_MS);
    void pollActiveSession();
    return () => globalThis.clearInterval(intervalId);
  }, [enterRunningFromStart, phase, studyPackId]);

  useEffect(() => {
    if (phase !== "paused-recovery" || !sessionId) {
      return;
    }

    const resumePausedSession = async () => {
      try {
        const response = await resumeLongExamSession(sessionId);
        enterRunningFromSession(response);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Could not resume this Long Exam.");
        setPhase("prestart");
      }
    };

    void resumePausedSession();
  }, [enterRunningFromSession, phase, sessionId]);

  const handleStartExam = useCallback(async () => {
    if (!studyPackId || starting) {
      return;
    }
    setStarting(true);
    setError(null);
    startedTrackedRef.current = false;
    try {
      const response = await startLongExam(studyPackId, { difficulty });
      setActiveStartResponse(response);
      setSessionId(response.sessionId);
      trackStarted(response);
      if (response.status === "GENERATING") {
        setPhase("generating");
        return;
      }
      if (response.status === "PAUSED" && response.canResume) {
        setPhase("paused-recovery");
        return;
      }
      if (response.status === "IN_PROGRESS" && response.quiz.length > 0) {
        enterRunningFromStart(response);
        return;
      }
      if (response.status === "FAILED") {
        setError("Long Exam generation failed. Please try again.");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start Long Exam.");
    } finally {
      setStarting(false);
    }
  }, [difficulty, enterRunningFromStart, starting, studyPackId, trackStarted]);

  const handleResumeActiveSession = useCallback(async () => {
    if (!activeStartResponse?.sessionId) {
      return;
    }
    setError(null);
    try {
      const response = activeStartResponse.status === "PAUSED"
        ? await resumeLongExamSession(activeStartResponse.sessionId)
        : await getLongExamSession(activeStartResponse.sessionId);
      enterRunningFromSession(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not resume this Long Exam.");
    }
  }, [activeStartResponse, enterRunningFromSession]);

  const handleStartFresh = useCallback(async () => {
    if (!activeStartResponse?.sessionId) {
      await handleStartExam();
      return;
    }
    if (!globalThis.confirm("Start a fresh Long Exam? Your active session will be forfeited.")) {
      return;
    }
    setForfeiting(true);
    setError(null);
    try {
      await forfeitLongExamSession(activeStartResponse.sessionId);
      setActiveStartResponse(null);
      setSessionId(null);
      setQuiz([]);
      setSelectedChoices({});
      setCurrentQuestionIndex(0);
      setDeadlineEpochSeconds(null);
      setRemainingSeconds(0);
      setTimeLimitSeconds(0);
      remainingSecondsRef.current = 0;
      timeoutAutoSubmitRequestedRef.current = false;
      startedTrackedRef.current = false;
      await handleStartExam();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start a fresh Long Exam.");
    } finally {
      setForfeiting(false);
    }
  }, [activeStartResponse, handleStartExam]);

  const handleSelectChoice = useCallback(async (choiceIndex: number) => {
    if (!sessionId || !currentQuestion || savingProgress) {
      return;
    }
    const choiceKey = String(currentQuestionIndex);
    setSelectedChoices((current) => ({
      ...current,
      [choiceKey]: choiceIndex,
    }));
    setSavingProgress(true);
    try {
      const response = await saveLongExamProgress(sessionId, {
        questionIndex: currentQuestionIndex,
        selectedChoiceIndex: choiceIndex,
      });
      setSelectedChoices(normalizeSelectedChoices(response.selectedChoices));
      setCurrentQuestionIndex(Math.min(Math.max(response.currentQuestionIndex, 0), Math.max(response.totalQuestions - 1, 0)));
    } catch {
      showToast("Could not save that answer. Your selection is still visible.", "error");
    } finally {
      setSavingProgress(false);
    }
  }, [currentQuestion, currentQuestionIndex, savingProgress, sessionId, showToast]);

  const handlePause = useCallback(async () => {
    if (!sessionId || pausing) {
      return;
    }
    setPausing(true);
    setError(null);
    try {
      await pauseLongExamSession(sessionId);
      router.push(noteDetailHref);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not pause this Long Exam.");
    } finally {
      setPausing(false);
    }
  }, [noteDetailHref, pausing, router, sessionId]);

  const handleComplete = useCallback(async (timeoutTriggered = false) => {
    if (!sessionId || submitting) {
      return;
    }
    if (timeoutTriggered) {
      timeoutAutoSubmitRequestedRef.current = true;
    }
    setSubmitting(true);
    setError(null);
    try {
      const durationSeconds = Math.max(0, timeLimitSeconds - remainingSecondsRef.current);
      const response = await completeLongExamSession(sessionId, { durationSeconds });
      setMasteryReport(response);
      setPhase("complete");
      void trackAnalyticsEvent({
        eventType: "LONG_EXAM_COMPLETED",
        entityId: response.sessionId,
        metadata: {
          noteId,
          studyPackId,
          scorePercentage: response.scorePercentage,
          answeredQuestions: response.answeredQuestions,
          totalQuestions: response.totalQuestions,
          weakDomainCount: response.weakDomains.length,
        },
      });
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Could not submit this Long Exam.", "error");
    } finally {
      setSubmitting(false);
    }
  }, [noteId, sessionId, showToast, studyPackId, submitting, timeLimitSeconds]);

  useEffect(() => {
    if (phase !== "running" || deadlineEpochSeconds === null) {
      return;
    }

    const tick = () => {
      const nextRemainingSeconds = resolveRemainingSecondsFromDeadline(deadlineEpochSeconds, getNowEpochSeconds());
      remainingSecondsRef.current = nextRemainingSeconds;
      setRemainingSeconds(nextRemainingSeconds);
      if (nextRemainingSeconds <= 0 && !timeoutAutoSubmitRequestedRef.current) {
        timeoutAutoSubmitRequestedRef.current = true;
        void handleComplete(true);
      }
    };

    tick();
    const intervalId = globalThis.setInterval(tick, 1000);
    return () => globalThis.clearInterval(intervalId);
  }, [deadlineEpochSeconds, handleComplete, phase]);

  useEffect(() => {
    if (phase !== "running" || deadlineEpochSeconds === null) {
      return;
    }

    const syncOnVisible = () => {
      const nextRemainingSeconds = resolveRemainingSecondsFromDeadline(deadlineEpochSeconds, getNowEpochSeconds());
      remainingSecondsRef.current = nextRemainingSeconds;
      setRemainingSeconds(nextRemainingSeconds);
      if (nextRemainingSeconds <= 0 && !timeoutAutoSubmitRequestedRef.current) {
        timeoutAutoSubmitRequestedRef.current = true;
        void handleComplete(true);
      }
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        syncOnVisible();
      }
    };

    document.addEventListener("visibilitychange", onVisibilityChange);
    globalThis.addEventListener("focus", syncOnVisible);
    return () => {
      document.removeEventListener("visibilitychange", onVisibilityChange);
      globalThis.removeEventListener("focus", syncOnVisible);
    };
  }, [deadlineEpochSeconds, handleComplete, phase]);

  const handleForfeit = useCallback(async () => {
    if (!sessionId || forfeiting) {
      return;
    }
    if (!globalThis.confirm("Forfeit this Long Exam? Your current session will end.")) {
      return;
    }
    setForfeiting(true);
    setError(null);
    try {
      await forfeitLongExamSession(sessionId);
      void trackAnalyticsEvent({
        eventType: "LONG_EXAM_FORFEITED",
        entityId: sessionId,
        metadata: {
          noteId,
          studyPackId,
          answeredQuestions: answeredCount,
          totalQuestions,
        },
      });
      router.push(noteDetailHref);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not forfeit this Long Exam.");
    } finally {
      setForfeiting(false);
    }
  }, [answeredCount, forfeiting, noteDetailHref, noteId, router, sessionId, studyPackId, totalQuestions]);

  if (getAuthUser()?.planType !== "PRO") {
    return null;
  }

  if (loading) {
    return (
      <main className="mx-auto max-w-4xl space-y-4 p-4 sm:p-6">
        <BackLink href={noteDetailHref} label="Back to Note" />
        <Card className="space-y-4 p-4 sm:p-6">
          <div className="h-4 w-36 animate-pulse rounded bg-foreground/10" />
          <div className="h-8 w-3/4 animate-pulse rounded bg-foreground/10" />
          <div className="h-24 w-full animate-pulse rounded bg-foreground/10" />
        </Card>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-4xl space-y-4 p-4 sm:p-6">
      <BackLink href={noteDetailHref} label="Back to Note" />

      {error ? (
        <Card className="space-y-3 border-red-500/30 bg-red-500/5 p-4 text-sm text-red-700 dark:text-red-300 sm:p-5">
          <p>{error}</p>
          <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => router.push(noteDetailHref)}>
            Back to Note
          </Button>
        </Card>
      ) : null}

      {phase === "prestart" ? (
        <Card className="space-y-5 p-4 sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Long Exam Mode
            </p>
            <h1 className="text-2xl font-semibold">Start Long Exam</h1>
            <p className="text-sm text-foreground/75">
              Generate a fixed, full-length exam from {note?.title ?? "this note"} and complete it at your own pace.
            </p>
          </div>

          {hasActiveInProgressPrompt ? (
            <div className="space-y-3 rounded-xl border border-foreground/15 bg-muted/20 p-4 text-sm">
              <p className="font-medium text-foreground">You have an active session. Resume it?</p>
              <p className="text-foreground/70">
                Resume to keep your saved answers and current question position, or start fresh to forfeit the active session.
              </p>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Button type="button" className="w-full sm:w-auto" onClick={() => void handleResumeActiveSession()}>
                  Resume
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  className="w-full sm:w-auto"
                  onClick={() => void handleStartFresh()}
                  disabled={forfeiting || starting}
                >
                  {forfeiting || starting ? "Starting..." : "Start Fresh"}
                </Button>
              </div>
            </div>
          ) : (
            <>
              <div className="space-y-3">
                <p className="text-sm font-medium text-foreground">Difficulty</p>
                <div className="grid gap-2 sm:grid-cols-2">
                  {LONG_EXAM_DIFFICULTIES.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={cn(
                        "rounded-md border px-3 py-3 text-left transition",
                        difficulty === option.value
                          ? "border-blue-500 bg-blue-500/10 text-foreground"
                          : "border-border bg-background text-foreground/75 hover:border-foreground/30",
                      )}
                      onClick={() => setDifficulty(option.value)}
                      disabled={starting}
                    >
                      <span className="block text-sm font-medium">{option.label}</span>
                      <span className="mt-1 block text-xs text-foreground/60">{option.description}</span>
                    </button>
                  ))}
                </div>
              </div>

              <div className="rounded-xl border border-foreground/15 bg-background/80 p-4 text-sm text-foreground/75">
                <p className="font-medium text-foreground">Before you begin</p>
                <p className="mt-1">
                  The full question set is generated before the exam starts. You can pause and resume later, and your mastery report appears after submission.
                </p>
              </div>

              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleStartExam()}
                disabled={!studyPackId || starting}
              >
                {starting ? "Starting..." : "Start Long Exam"}
              </Button>
            </>
          )}
        </Card>
      ) : null}

      {phase === "generating" ? (
        <Card className="space-y-4 p-4 text-center sm:p-8">
          <div className="mx-auto h-10 w-10 animate-spin rounded-full border-2 border-blue-500 border-t-transparent" aria-hidden="true" />
          <div className="space-y-1">
            <h1 className="text-xl font-semibold">Generating your exam...</h1>
            <p className="text-sm text-foreground/70">
              Creating a fixed Long Exam from the full Study Pack. This may take a moment.
            </p>
          </div>
        </Card>
      ) : null}

      {phase === "paused-recovery" ? (
        <Card className="space-y-4 p-4 text-center sm:p-8">
          <div className="mx-auto h-10 w-10 animate-spin rounded-full border-2 border-blue-500 border-t-transparent" aria-hidden="true" />
          <div className="space-y-1">
            <h1 className="text-xl font-semibold">Resuming exam...</h1>
            <p className="text-sm text-foreground/70">Restoring your saved answers and question position.</p>
          </div>
        </Card>
      ) : null}

      {phase === "running" && currentQuestion ? (
        <div className="space-y-4">
          <Card className="space-y-4 p-4 sm:p-5">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div className="space-y-1">
                <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">
                  Question {currentQuestionIndex + 1} of {totalQuestions}
                </p>
                <p className="text-sm text-foreground/65">
                  {answeredCount} of {totalQuestions} answered
                </p>
              </div>
              <div className="flex flex-col gap-2 sm:items-end">
                <p
                  className={cn(
                    "rounded-full border px-3 py-1 text-sm font-semibold tabular-nums",
                    timerState === "urgent" || timerState === "expired"
                      ? "border-red-500/40 bg-red-500/10 text-red-700 dark:text-red-300"
                      : timerState === "warning"
                        ? "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300"
                        : "border-border bg-background text-foreground/75",
                  )}
                >
                  {formatTimer(remainingSeconds)}
                </p>
                <Button type="button" variant="outline" size="sm" className="w-full sm:w-auto" onClick={() => void handlePause()} disabled={pausing || submitting}>
                  {pausing ? "Pausing..." : "Pause"}
                </Button>
              </div>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-muted">
              <div className="h-full bg-blue-500 transition-all" style={{ width: `${progressPercentage}%` }} />
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-5">
            <h1 className="text-lg font-semibold leading-relaxed sm:text-xl">{currentQuestion.question}</h1>
            <QuizChoiceList
              questionKey={`${currentQuestion.question}-${currentQuestionIndex}`}
              choices={currentQuestion.choices}
              correctIndex={resolveQuizCorrectIndex(currentQuestion)}
              selectedChoiceIndex={getSelectedChoice(selectedChoices, currentQuestionIndex)}
              revealAnswer={false}
              selectionStyle="exam"
              disabled={submitting}
              onSelectChoice={(choiceIndex) => void handleSelectChoice(choiceIndex)}
            />
            {savingProgress ? (
              <p className="text-xs text-foreground/55">Saving answer...</p>
            ) : null}
          </Card>

          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button
                type="button"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={() => setCurrentQuestionIndex((index) => Math.max(index - 1, 0))}
                disabled={currentQuestionIndex === 0 || submitting}
              >
                Previous
              </Button>
              {currentQuestionIndex < totalQuestions - 1 ? (
                <Button
                  type="button"
                  className="w-full sm:w-auto"
                  onClick={() => setCurrentQuestionIndex((index) => Math.min(index + 1, totalQuestions - 1))}
                  disabled={submitting}
                >
                  Next
                </Button>
              ) : (
                <Button
                  type="button"
                  className="w-full sm:w-auto"
                  onClick={() => void handleComplete()}
                  disabled={submitting}
                >
                  {submitting ? "Submitting..." : "Submit Long Exam"}
                </Button>
              )}
            </div>
            <Button
              type="button"
              variant="ghost"
              className="w-full text-red-600 hover:text-red-700 dark:text-red-400 sm:w-auto"
              onClick={() => void handleForfeit()}
              disabled={forfeiting || submitting}
            >
              {forfeiting ? "Forfeiting..." : "Forfeit"}
            </Button>
          </div>
        </div>
      ) : null}

      {phase === "complete" && masteryReport ? (
        <Card className="space-y-5 border-foreground/15 p-4 sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Mastery Report</p>
            <h1 className="text-2xl font-semibold">Long Exam Complete</h1>
            <p className="text-sm text-foreground/70">{masteryReport.performanceSummary}</p>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-xl border border-border bg-background p-4">
              <p className="text-xs font-medium uppercase tracking-wide text-foreground/55">Score</p>
              <p className="mt-1 text-3xl font-semibold">{masteryReport.scorePercentage}%</p>
            </div>
            <div className="rounded-xl border border-border bg-background p-4">
              <p className="text-xs font-medium uppercase tracking-wide text-foreground/55">Answered</p>
              <p className="mt-1 text-3xl font-semibold">
                {masteryReport.answeredQuestions} / {masteryReport.totalQuestions}
              </p>
            </div>
          </div>

          <div className="space-y-2">
            <h2 className="text-base font-semibold">Domain Breakdown</h2>
            <div className="overflow-x-auto rounded-xl border border-border">
              <table className="w-full min-w-[520px] text-left text-sm">
                <thead className="bg-muted/60 text-xs uppercase tracking-wide text-foreground/55">
                  <tr>
                    <th className="px-3 py-2 font-medium">Domain</th>
                    <th className="px-3 py-2 font-medium">Correct</th>
                    <th className="px-3 py-2 font-medium">Accuracy</th>
                  </tr>
                </thead>
                <tbody>
                  {masteryReport.domainBreakdown.map((domain) => (
                    <tr key={domain.domain} className="border-t border-border">
                      <td className="px-3 py-2 text-foreground">{domain.domain}</td>
                      <td className="px-3 py-2 text-foreground/75">
                        {domain.correctAnswers} / {domain.totalQuestions}
                      </td>
                      <td className="px-3 py-2 text-foreground/75">{domain.accuracyPercentage}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="space-y-2">
            <h2 className="text-base font-semibold">Weak Domains</h2>
            {masteryReport.weakDomains.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {masteryReport.weakDomains.map((domain) => (
                  <span key={domain} className="rounded-full border border-amber-500/35 bg-amber-500/10 px-3 py-1 text-xs font-medium text-amber-800 dark:text-amber-200">
                    {domain}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-sm text-foreground/70">No weak domains flagged.</p>
            )}
          </div>

          <div className="rounded-xl border border-border bg-background p-4 text-sm text-foreground/75">
            <p className="font-medium text-foreground">Suggested Next Step</p>
            <p className="mt-1">{masteryReport.suggestedNextStep}</p>
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={() => router.push(noteDetailHref)}>
              Back to Note
            </Button>
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => router.push(noteDetailHref)}>
              Study Again
            </Button>
          </div>
        </Card>
      ) : null}

      {toast ? <ToastMessage message={toast.message} tone={toast.tone} /> : null}
    </main>
  );
}
