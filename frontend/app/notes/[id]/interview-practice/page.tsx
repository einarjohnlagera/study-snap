"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { QuizGenerationOverlay } from "@/components/study-pack/quiz-generation-overlay";
import { useQuizSessionGuard } from "@/components/study-pack/quiz-session-guard";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  answerInterviewPracticeQuestion,
  completeInterviewPracticeSession,
  forfeitInterviewPracticeSession,
  getMe,
  getNote,
  listNotes,
  startInterviewPractice,
  type InterviewPracticeAnswerResponse,
  type InterviewReadinessReportResponse,
  type NoteListItemResponse,
  type NoteResponse,
  type QuizItem,
} from "@/lib/api";
import { BackLink } from "@/components/ui/back-link";
import { cn } from "@/lib/utils";

type InterviewPhase = "prestart" | "generating" | "running" | "completed" | "forfeited" | "error";
type ChoiceLetter = "A" | "B" | "C" | "D";

const QUESTION_COUNT_OPTIONS = [5, 10] as const;
const MAX_ADDITIONAL_NOTES = 2;
const NOTE_CHIP_TITLE_MAX_LENGTH = 40;
const SOFT_TIMER_SECONDS = 120;
const TIMER_EXPIRED_COPY = "Try to wrap up - you can keep going.";
const LEAVE_TITLE = "Leave interview practice?";
const LEAVE_DESCRIPTION = "Your session will be forfeited. This action cannot be undone.";
const BEFORE_UNLOAD_MESSAGE = "You are currently in Interview Practice. Leaving will forfeit your progress.";

function formatTimer(seconds: number) {
  const safeSeconds = Math.max(0, seconds);
  const minutes = Math.floor(safeSeconds / 60);
  const remaining = safeSeconds % 60;
  return `${minutes}:${remaining.toString().padStart(2, "0")}`;
}

function toChoiceLetter(index: number): ChoiceLetter {
  return ["A", "B", "C", "D"][index] as ChoiceLetter;
}

function resolveBandLabel(band: InterviewReadinessReportResponse["band"]) {
  if (band === "READY") return "Ready";
  if (band === "ALMOST_READY") return "Almost ready";
  return "Needs more practice";
}

function truncateNoteTitle(title: string | null) {
  const label = title?.trim() || "Untitled note";
  if (label.length <= NOTE_CHIP_TITLE_MAX_LENGTH) {
    return label;
  }
  return `${label.slice(0, NOTE_CHIP_TITLE_MAX_LENGTH - 1)}...`;
}

export default function InterviewPracticePage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const noteId = params.id;
  const noteHref = `/notes/${noteId}`;
  const [phase, setPhase] = useState<InterviewPhase>("prestart");
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [availableNotes, setAvailableNotes] = useState<NoteListItemResponse[]>([]);
  const [additionalNoteIds, setAdditionalNoteIds] = useState<string[]>([]);
  const [questionCount, setQuestionCount] = useState<5 | 10>(5);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [questions, setQuestions] = useState<QuizItem[]>([]);
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [selectedChoice, setSelectedChoice] = useState<ChoiceLetter | null>(null);
  const [critique, setCritique] = useState<InterviewPracticeAnswerResponse | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(SOFT_TIMER_SECONDS);
  const [questionStartedAtMs, setQuestionStartedAtMs] = useState<number | null>(null);
  const [submittingAnswer, setSubmittingAnswer] = useState(false);
  const [submittingComplete, setSubmittingComplete] = useState(false);
  const [report, setReport] = useState<InterviewReadinessReportResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const completingRef = useRef(false);
  const modeSelectHref = note?.studyPackId ? `/study-packs/${note.studyPackId}/challenge-quiz` : noteHref;

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const [noteResult, me, notes] = await Promise.all([
          getNote(noteId),
          getMe(),
          listNotes().catch(() => [] as NoteListItemResponse[]),
        ]);
        if (!active) return;
        setAvailableNotes(
          notes.filter((item) => item.id !== noteId && item.studyPackStatus === "STUDY_PACK_READY"),
        );
        if (me.profileType !== "PROFESSIONAL" || me.planType !== "PRO") {
          router.replace(noteHref);
          return;
        }
        if (noteResult.studyPackStatus !== "STUDY_PACK_READY") {
          setError("Generate a Study Pack for this note before starting Interview Practice.");
          setPhase("error");
          return;
        }
        setNote(noteResult);
      } catch (err) {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Could not load Interview Practice.");
        setPhase("error");
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [noteHref, noteId, router]);

  useEffect(() => {
    if (phase !== "running" || critique) {
      return;
    }
    const tick = () => {
      if (questionStartedAtMs === null) {
        return;
      }
      const elapsed = Math.floor((Date.now() - questionStartedAtMs) / 1000);
      setRemainingSeconds(Math.max(0, SOFT_TIMER_SECONDS - elapsed));
    };
    tick();
    const timerId = globalThis.setInterval(tick, 1000);
    return () => globalThis.clearInterval(timerId);
  }, [critique, phase, questionStartedAtMs]);

  const currentQuestion = questions[currentQuestionIndex] ?? null;
  const isLastQuestion = currentQuestionIndex >= Math.max(0, questionCount - 1);
  const answeredCount = critique ? currentQuestionIndex + 1 : currentQuestionIndex;
  const progressPct = questionCount > 0 ? Math.round((answeredCount / questionCount) * 100) : 0;

  const resetQuestionState = useCallback((nextIndex: number) => {
    setCurrentQuestionIndex(nextIndex);
    setSelectedChoice(null);
    setCritique(null);
    setRemainingSeconds(SOFT_TIMER_SECONDS);
    setQuestionStartedAtMs(Date.now());
  }, []);

  const handleToggleAdditionalNote = useCallback((selectedNoteId: string) => {
    setAdditionalNoteIds((current) => {
      if (current.includes(selectedNoteId)) {
        return current.filter((id) => id !== selectedNoteId);
      }
      if (current.length >= MAX_ADDITIONAL_NOTES) {
        return current;
      }
      return [...current, selectedNoteId];
    });
  }, []);

  const handleStart = useCallback(async () => {
    setPhase("generating");
    setError(null);
    try {
      const response = await startInterviewPractice({
        noteId,
        questionCount,
        ...(additionalNoteIds.length > 0 ? { additionalNoteIds } : {}),
      });
      setSessionId(response.sessionId);
      if (response.status === "GENERATING") {
        setPhase("generating");
        return;
      }
      if (!response.question) {
        throw new Error("Interview Practice started without a question.");
      }
      setQuestions([response.question]);
      setQuestionCount(response.questionCount === 10 ? 10 : 5);
      resetQuestionState(response.currentQuestionIndex ?? 0);
      setPhase("running");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start Interview Practice.");
      setPhase("error");
    }
  }, [additionalNoteIds, noteId, questionCount, resetQuestionState]);

  const handleAnswer = useCallback(async () => {
    if (!sessionId || !currentQuestion || !selectedChoice || submittingAnswer) {
      return;
    }
    setSubmittingAnswer(true);
    setError(null);
    try {
      const elapsed = questionStartedAtMs === null
        ? 0
        : Math.max(0, Math.floor((Date.now() - questionStartedAtMs) / 1000));
      const response = await answerInterviewPracticeQuestion(sessionId, {
        questionIndex: currentQuestionIndex,
        selectedChoice,
        timeSpentSeconds: elapsed,
      });
      if (response.nextQuestion) {
        setQuestions((prev) => {
          const next = [...prev];
          next[currentQuestionIndex + 1] = response.nextQuestion as QuizItem;
          return next;
        });
      }
      setCritique(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not review your answer.");
    } finally {
      setSubmittingAnswer(false);
    }
  }, [currentQuestion, currentQuestionIndex, questionStartedAtMs, selectedChoice, sessionId, submittingAnswer]);

  const handleComplete = useCallback(async () => {
    if (!sessionId || completingRef.current) {
      return;
    }
    completingRef.current = true;
    setSubmittingComplete(true);
    setError(null);
    try {
      const nextReport = await completeInterviewPracticeSession(sessionId);
      setReport(nextReport);
      setPhase("completed");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not complete Interview Practice.");
    } finally {
      setSubmittingComplete(false);
      completingRef.current = false;
    }
  }, [sessionId]);

  const handleNext = useCallback(() => {
    resetQuestionState(currentQuestionIndex + 1);
  }, [currentQuestionIndex, resetQuestionState]);

  const handleForfeit = useCallback(async () => {
    if (!sessionId) return;
    await forfeitInterviewPracticeSession(sessionId);
    setPhase("forfeited");
  }, [sessionId]);

  const { requestLeave, LeaveQuizModal } = useQuizSessionGuard({
    active: phase === "running" && Boolean(sessionId),
    fallbackHref: noteHref,
    onConfirmLeave: handleForfeit,
    dialogTitle: LEAVE_TITLE,
    dialogDescription: LEAVE_DESCRIPTION,
    confirmLabel: "Forfeit",
    confirmLoadingLabel: "Forfeiting...",
    beforeUnloadMessage: BEFORE_UNLOAD_MESSAGE,
  });

  const bandClassName = useMemo(() => {
    if (!report) return "";
    if (report.band === "READY") return "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300";
    if (report.band === "ALMOST_READY") return "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300";
    return "border-red-500/40 bg-red-500/10 text-red-700 dark:text-red-300";
  }, [report]);

  return (
    <main className="mx-auto w-full max-w-3xl space-y-4 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        {phase === "running" ? (
          <Button variant="outline" size="sm" onClick={() => requestLeave()}>
            Leave Practice
          </Button>
        ) : (
          <BackLink href={noteHref} label="Note" />
        )}
        {phase === "running" ? (
          <div className="text-right">
            <p className="text-sm font-semibold text-foreground">Interview Practice</p>
            <p className="text-xs text-foreground/60">Question {currentQuestionIndex + 1} of {questionCount}</p>
          </div>
        ) : null}
      </div>

      {phase === "prestart" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">INTERVIEW PRACTICE</p>
            <h1 className="text-2xl font-semibold">Start Interview Practice</h1>
            <p className="text-sm text-foreground/80">
              Review your practice setup for {note?.title ?? "this note"}.
            </p>
          </div>

          <div className="space-y-3 rounded-xl border border-border bg-background p-4 text-sm text-foreground/80">
            <div className="space-y-2">
              <div className="space-y-1">
                <p className="font-medium text-foreground">Session length</p>
                <p>Pick how many scenarios to run.</p>
              </div>
              <div className="grid gap-2 sm:grid-cols-2">
                {QUESTION_COUNT_OPTIONS.map((count) => (
                  <button
                    key={count}
                    type="button"
                    onClick={() => setQuestionCount(count)}
                    className={cn(
                      "rounded-md border px-3 py-2 text-left transition",
                      questionCount === count
                        ? "border-blue-500 bg-blue-500/10 text-foreground"
                        : "border-border bg-background text-foreground/75 hover:border-foreground/30",
                    )}
                  >
                    <span className="block text-sm font-medium">{count} questions</span>
                    <span className="mt-1 block text-xs text-foreground/60">
                      {count === 5 ? "Focused warm-up" : "Full practice session"}
                    </span>
                  </button>
                ))}
              </div>
            </div>
            {availableNotes.length > 0 ? (
              <div className="space-y-2 border-t border-border pt-3">
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Add more notes (optional)</p>
                  <p>Practice across multiple notes for a cross-domain session.</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {availableNotes.map((availableNote) => {
                    const selected = additionalNoteIds.includes(availableNote.id);
                    const disabled = !selected && additionalNoteIds.length >= MAX_ADDITIONAL_NOTES;
                    return (
                      <button
                        key={availableNote.id}
                        type="button"
                        disabled={disabled}
                        onClick={() => handleToggleAdditionalNote(availableNote.id)}
                        title={availableNote.title ?? "Untitled note"}
                        className={cn(
                          "rounded-full border px-3 py-1.5 text-xs font-medium transition",
                          selected
                            ? "border-blue-500 bg-blue-500/10 text-foreground"
                            : "border-border bg-background text-foreground/75 hover:border-foreground/30",
                          disabled ? "cursor-not-allowed opacity-45 hover:border-border" : "",
                        )}
                      >
                        {truncateNoteTitle(availableNote.title)}
                      </button>
                    );
                  })}
                </div>
              </div>
            ) : null}
            <div className="grid gap-3 border-t border-border pt-3 sm:grid-cols-3">
              <div className="space-y-1">
                <p className="font-medium text-foreground">Soft timer</p>
                <p>2 min per question. Non-enforcing - you can keep going past the timer.</p>
              </div>
              <div className="space-y-1">
                <p className="font-medium text-foreground">Format</p>
                <p>Scenario MCQ with AI critique after each answer.</p>
              </div>
              <div className="space-y-1">
                <p className="font-medium text-foreground">Monthly limit</p>
                <p>Counts toward your monthly Interview Practice limit (10/mo).</p>
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" variant="outline" onClick={() => router.push(modeSelectHref)}>
              Choose another mode
            </Button>
            <Button type="button" onClick={() => void handleStart()}>
              Start Interview Practice
            </Button>
          </div>
        </Card>
      ) : null}

      {phase === "generating" ? (
        <QuizGenerationOverlay
          title="Building your interview session..."
          message="Creating scenario questions from your notes"
          rotatingMessages={[
            "Building scenario questions...",
            "Setting difficulty for your level...",
            "Preparing follow-up criteria...",
            "Almost ready...",
          ]}
        />
      ) : null}

      {phase === "running" && currentQuestion ? (
        <div className="space-y-4">
          <div className="h-2 overflow-hidden rounded-full bg-muted">
            <div className="h-full bg-primary transition-all" style={{ width: `${progressPct}%` }} />
          </div>
          <Card className="space-y-5 p-4 sm:p-6">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-medium text-foreground/70">{currentQuestion.concept ?? "Scenario"}</p>
              <div className={cn(
                "rounded-full border px-3 py-1 text-sm font-semibold",
                remainingSeconds === 0 ? "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300" : "border-foreground/15 bg-background",
              )}>
                {formatTimer(remainingSeconds)}
              </div>
            </div>
            <h1 className="text-xl font-semibold leading-relaxed">{currentQuestion.question}</h1>
            <div className="space-y-2">
              {currentQuestion.choices.map((choice, index) => {
                const letter = toChoiceLetter(index);
                return (
                  <button
                    key={`${letter}-${choice}`}
                    type="button"
                    disabled={Boolean(critique) || submittingAnswer}
                    onClick={() => setSelectedChoice(letter)}
                    className={cn(
                      "w-full rounded-lg border px-4 py-3 text-left text-sm transition-colors",
                      selectedChoice === letter
                        ? "border-primary bg-primary/10 text-foreground"
                        : "border-border bg-background text-foreground/80 hover:border-foreground/30",
                    )}
                  >
                    <span className="font-semibold">{letter}.</span> {choice}
                  </button>
                );
              })}
            </div>
            {remainingSeconds === 0 && !critique ? (
              <p className="text-sm text-amber-700 dark:text-amber-300">{TIMER_EXPIRED_COPY}</p>
            ) : null}
            {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
            {!critique ? (
              <Button type="button" onClick={() => void handleAnswer()} disabled={!selectedChoice || submittingAnswer}>
                {submittingAnswer ? "Reviewing..." : "Submit Answer"}
              </Button>
            ) : (
              <div className="space-y-4 rounded-lg border border-border bg-muted/30 p-4">
                <div className="space-y-2">
                  <span className="inline-flex rounded-full border border-foreground/15 bg-background px-3 py-1 text-xs font-semibold">
                    {critique.verdict === "STRONG" ? "Strong" : critique.verdict === "WORKABLE" ? "Workable" : "Reconsider"}
                  </span>
                  <p className="text-sm text-foreground/80">{critique.rationale}</p>
                  <p className="text-sm font-medium text-foreground">{critique.followUp}</p>
                </div>
                {isLastQuestion ? (
                  <Button type="button" onClick={() => void handleComplete()} disabled={submittingComplete}>
                    {submittingComplete ? "Completing..." : "Complete"}
                  </Button>
                ) : (
                  <Button type="button" onClick={handleNext}>
                    Next Question
                  </Button>
                )}
              </div>
            )}
          </Card>
        </div>
      ) : null}

      {phase === "completed" && report ? (
        <Card className="space-y-5 p-4 sm:p-6">
          <div className="space-y-2">
            <span className={cn("inline-flex rounded-full border px-3 py-1 text-sm font-semibold", bandClassName)}>
              {resolveBandLabel(report.band)}
            </span>
            <h1 className="text-2xl font-semibold">Interview Readiness Report</h1>
            <p className="text-sm text-foreground/70">
              Score: {report.scorePercentage}% ({report.correctAnswers}/{report.totalQuestions})
            </p>
          </div>
          <ReportList title="Strengths" items={report.strengths} emptyText="No clear strengths yet." />
          <div className="space-y-2">
            <h2 className="text-base font-semibold">Gaps</h2>
            {report.gaps.length > 0 ? (
              <div className="space-y-2">
                {report.gaps.map((gap) => (
                  <button
                    key={`${gap.noteId}-${gap.concept}`}
                    type="button"
                    onClick={() => router.push(`/notes/${gap.noteId}/adaptive-practice`)}
                    className="block text-left text-sm text-primary hover:underline"
                  >
                    {gap.concept}
                  </button>
                ))}
              </div>
            ) : (
              <p className="text-sm text-foreground/60">No weak gaps flagged.</p>
            )}
          </div>
          <ReportList title="Talking Points" items={report.talkingPoints} emptyText="Complete more answers to build talking points." />
          {report.pacingNotes.length > 0 ? (
            <ReportList title="Pacing Notes" items={report.pacingNotes.map((index) => `Question ${index + 1} exceeded 2 minutes.`)} />
          ) : null}
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" onClick={() => {
              setReport(null);
              setSessionId(null);
              setQuestions([]);
              setPhase("prestart");
            }}>
              Practice Again
            </Button>
            <Button type="button" variant="secondary" onClick={() => router.push("/dashboard")}>
              Back to Dashboard
            </Button>
          </div>
        </Card>
      ) : null}

      {phase === "forfeited" ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <h1 className="text-xl font-semibold">Session forfeited</h1>
          <p className="text-sm text-foreground/70">Your Interview Practice session has ended.</p>
          <Button type="button" onClick={() => router.push(noteHref)}>Back to Note</Button>
        </Card>
      ) : null}

      {phase === "error" ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <h1 className="text-xl font-semibold">Interview Practice unavailable</h1>
          <p className="text-sm text-foreground/70">{error ?? "Please try again later."}</p>
          <Button type="button" onClick={() => router.push(noteHref)}>Back to Note</Button>
        </Card>
      ) : null}

      <LeaveQuizModal />
    </main>
  );
}

function ReportList({
  title,
  items,
  emptyText,
}: Readonly<{
  title: string;
  items: string[];
  emptyText?: string;
}>) {
  return (
    <div className="space-y-2">
      <h2 className="text-base font-semibold">{title}</h2>
      {items.length > 0 ? (
        <ul className="space-y-1 text-sm text-foreground/75">
          {items.map((item) => <li key={item}>- {item}</li>)}
        </ul>
      ) : (
        <p className="text-sm text-foreground/60">{emptyText ?? "None."}</p>
      )}
    </div>
  );
}
