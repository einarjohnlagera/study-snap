"use client";

import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useParams, useRouter} from "next/navigation";
import {BarChart3, BookOpen, Hourglass, ListChecks} from "lucide-react";
import {BackLink} from "@/components/ui/back-link";
import {Button} from "@/components/ui/button";
import {Card} from "@/components/ui/card";
import {ScoreReveal} from "@/components/exam-mode/score-reveal";
import {QuizChoiceList} from "@/components/study-pack/quiz-choice-list";
import {QuizGenerationOverlay} from "@/components/study-pack/quiz-generation-overlay";
import {useQuizSessionGuard} from "@/components/study-pack/quiz-session-guard";
import {StickyAssessmentFooter} from "@/components/ui/sticky-assessment-footer";
import {ToastMessage} from "@/components/ui/toast-message";
import {getAuthUser} from "@/lib/auth";
import {
    completeLongExamSession,
    forfeitLongExamSession,
    getActiveLongExamSession,
    getLongExamSession,
    getMe,
    getNote,
    listNotes,
    resumeLongExamSession,
    saveLongExamProgress,
    startLongExam,
    trackAnalyticsEvent,
    type LongExamMasteryReportResponse,
    type LongExamSessionResponse,
    type LongExamSourceNoteRef,
    type LongExamStartResponse,
    type NoteListItemResponse,
    type NoteResponse,
    type QuizItem,
} from "@/lib/api";
import {requireAuthenticatedOnboardedUser} from "@/lib/route-guards";
import {
    resolveBoardExamTimerState,
    resolveDeadlineEpochSeconds,
    resolveRemainingSecondsFromDeadline,
} from "@/lib/challenge-quiz-timer";
import {resolveQuizCorrectIndex} from "@/lib/quiz";
import {cn} from "@/lib/utils";

type LongExamPhase = "prestart" | "generating" | "paused-recovery" | "running" | "complete";
type ToastState = {
    message: string;
    tone: "success" | "error" | "info";
};

const LONG_EXAM_POLL_INTERVAL_MS = 2000;
const LONG_EXAM_LEAVE_TITLE = "Leave exam?";
const LONG_EXAM_LEAVE_DESCRIPTION = "Your progress will be forfeited. This action cannot be undone.";
const LONG_EXAM_LEAVE_CONFIRM_LABEL = "Forfeit Exam";
const LONG_EXAM_LEAVE_ERROR = "Could not forfeit exam. Please try again.";
const LONG_EXAM_BEFORE_UNLOAD_MESSAGE = "You are currently in Long Exam Mode. Leaving will forfeit your progress.";
const LONG_EXAM_FOCUS_TIP_STORAGE_KEY = "notelib-long-exam-mode-tip-dismissed";
const LONG_EXAM_FOCUS_TIP = "Long Exam Mode simulates a comprehensive exam. Stay focused — leaving will forfeit your session.";
const LONG_EXAM_MAX_ADDITIONAL_NOTES = 3;

function normalizeSelectedChoices(selectedChoices?: Record<string, number> | null): Record<string, number> {
    return {...selectedChoices};
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

function normalizeSubjectForMatch(subject?: string | null): string {
    return subject?.trim().toLowerCase() ?? "";
}

function resolveLongExamPerformanceLevel(scorePercentage: number): string {
    if (scorePercentage >= 90) return "Excellent";
    if (scorePercentage >= 70) return "Good";
    if (scorePercentage >= 50) return "Fair";
    return "Needs Improvement";
}

function resolveExpectedLongExamQuestionCount(learnerLevel?: string | null): number {
    switch (learnerLevel) {
        case "GRADE_SCHOOL":
        case "JUNIOR_HIGH":
            return 20;
        case "BOARD_EXAM_REVIEW":
        case "PROFESSIONAL":
            return 30;
        case "SENIOR_HIGH":
        case "COLLEGE":
        case "PERSONAL_LEARNING":
        default:
            return 25;
    }
}

function resolveSameSubjectSourceNotes(
    currentNote: NoteResponse,
    notes: NoteListItemResponse[],
): NoteListItemResponse[] {
    const currentSubject = normalizeSubjectForMatch(currentNote.subject);
    if (!currentSubject) {
        return [];
    }
    return notes
    .filter((candidate) => candidate.id !== currentNote.id)
    .filter((candidate) => candidate.studyPackStatus === "STUDY_PACK_READY" && Boolean(candidate.studyPackId))
    .filter((candidate) => normalizeSubjectForMatch(candidate.subject) === currentSubject)
    .slice(0, LONG_EXAM_MAX_ADDITIONAL_NOTES);
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
    const [forfeiting, setForfeiting] = useState(false);
    const [showFocusTip, setShowFocusTip] = useState(false);
    const [note, setNote] = useState<NoteResponse | null>(null);
    const [profileLearnerLevel, setProfileLearnerLevel] = useState<string | null>(null);
    const [availableSourceNotes, setAvailableSourceNotes] = useState<NoteListItemResponse[]>([]);
    const [selectedAdditionalStudyPackIds, setSelectedAdditionalStudyPackIds] = useState<string[]>([]);
    const [sourceNoteRefs, setSourceNoteRefs] = useState<LongExamSourceNoteRef[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [toast, setToast] = useState<ToastState | null>(null);
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
    const modeSelectHref = studyPackId ? `/study-packs/${studyPackId}/challenge-quiz` : noteDetailHref;
    const totalQuestions = quiz.length;
    const expectedQuestionCount = resolveExpectedLongExamQuestionCount(profileLearnerLevel);
    const selectedSourceCount = 1 + selectedAdditionalStudyPackIds.length;
    const currentQuestion = totalQuestions > 0 ? quiz[currentQuestionIndex] ?? null : null;
    const answeredCount = useMemo(() => getAnsweredCount(selectedChoices), [selectedChoices]);
    const progressPercentage = totalQuestions > 0 ? Math.round((answeredCount / totalQuestions) * 100) : 0;
    const hasActiveInProgressPrompt = activeStartResponse?.status === "IN_PROGRESS" && activeStartResponse.canResume;
    const timerState = resolveBoardExamTimerState(remainingSeconds);
    const longExamActive = phase === "running" && Boolean(sessionId);
    const isLastQuestion = currentQuestionIndex === totalQuestions - 1;

    const showToast = useCallback((message: string, tone: ToastState["tone"] = "info") => {
        setToast({message, tone});
    }, []);

    const applyTimer = useCallback((response: { timeLimitSeconds: number; timerStartedAtEpochSeconds: number }) => {
        const deadline = resolveDeadlineEpochSeconds(
            response.timeLimitSeconds,
            {timerStartedAtEpochSeconds: response.timerStartedAtEpochSeconds},
            getNowEpochSeconds(),
        );
        const nextRemainingSeconds = resolveRemainingSecondsFromDeadline(deadline, getNowEpochSeconds());
        setTimeLimitSeconds(response.timeLimitSeconds);
        setDeadlineEpochSeconds(deadline);
        setRemainingSeconds(nextRemainingSeconds);
        remainingSecondsRef.current = nextRemainingSeconds;
        timeoutAutoSubmitRequestedRef.current = false;
    }, []);

    const trackStarted = useCallback((response: {
        sessionId: string;
        totalQuestions?: number;
        difficulty?: string | null
    }) => {
        if (startedTrackedRef.current) {
            return;
        }
        startedTrackedRef.current = true;
        trackAnalyticsEvent({
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
        setSourceNoteRefs(response.sourceNoteRefs ?? []);
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
        setSourceNoteRefs(response.sourceNoteRefs ?? []);
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
            const [noteDetail, me] = await Promise.all([getNote(noteId), getMe().catch(() => null)]);
            setNote(noteDetail);
            setProfileLearnerLevel(me?.learnerLevel ?? null);
            if (!noteDetail.studyPackId || noteDetail.studyPackStatus !== "STUDY_PACK_READY") {
                setError("Generate a Study Pack before starting a Long Exam.");
                return;
            }
            try {
                const notes = await listNotes();
                setAvailableSourceNotes(resolveSameSubjectSourceNotes(noteDetail, notes));
            } catch {
                setAvailableSourceNotes([]);
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
        if (phase !== "running") {
            setShowFocusTip(false);
            return;
        }
        setShowFocusTip(globalThis.localStorage?.getItem(LONG_EXAM_FOCUS_TIP_STORAGE_KEY) !== "dismissed");
    }, [phase]);

    const dismissFocusTip = useCallback(() => {
        globalThis.localStorage?.setItem(LONG_EXAM_FOCUS_TIP_STORAGE_KEY, "dismissed");
        setShowFocusTip(false);
    }, []);

    const toggleAdditionalSource = useCallback((studyPackIdToToggle: string) => {
        setSelectedAdditionalStudyPackIds((current) => {
            if (current.includes(studyPackIdToToggle)) {
                return current.filter((studyPackIdValue) => studyPackIdValue !== studyPackIdToToggle);
            }
            if (current.length >= LONG_EXAM_MAX_ADDITIONAL_NOTES) {
                return current;
            }
            return [...current, studyPackIdToToggle];
        });
    }, []);

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
            const requestBody = selectedAdditionalStudyPackIds.length > 0
                ? {additionalStudyPackIds: selectedAdditionalStudyPackIds}
                : {};
            const response = await startLongExam(studyPackId, requestBody);
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
    }, [enterRunningFromStart, selectedAdditionalStudyPackIds, starting, studyPackId, trackStarted]);

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
            setSourceNoteRefs([]);
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

    const handlePrevious = useCallback(() => {
        setCurrentQuestionIndex((index) => Math.max(index - 1, 0));
    }, []);

    const handleNext = useCallback(() => {
        setCurrentQuestionIndex((index) => Math.min(index + 1, totalQuestions - 1));
    }, [totalQuestions]);

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
            const response = await completeLongExamSession(sessionId, {durationSeconds});
            setMasteryReport(response);
            setPhase("complete");
            trackAnalyticsEvent({
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

    const handleLeaveExam = useCallback(async () => {
        if (!sessionId) {
            return;
        }
        await forfeitLongExamSession(sessionId);
        trackAnalyticsEvent({
            eventType: "LONG_EXAM_FORFEITED",
            entityId: sessionId,
            metadata: {
                noteId,
                studyPackId,
                answeredQuestions: answeredCount,
                totalQuestions,
            },
        });
    }, [answeredCount, noteId, sessionId, studyPackId, totalQuestions]);

    const {requestLeave, LeaveQuizModal} = useQuizSessionGuard({
        active: longExamActive,
        fallbackHref: noteDetailHref,
        onConfirmLeave: handleLeaveExam,
        dialogTitle: LONG_EXAM_LEAVE_TITLE,
        dialogDescription: LONG_EXAM_LEAVE_DESCRIPTION,
        confirmLabel: LONG_EXAM_LEAVE_CONFIRM_LABEL,
        confirmLoadingLabel: "Forfeiting...",
        leaveErrorMessage: LONG_EXAM_LEAVE_ERROR,
        beforeUnloadMessage: LONG_EXAM_BEFORE_UNLOAD_MESSAGE,
    });

    if (getAuthUser()?.planType !== "PRO") {
        return null;
    }

    if (loading) {
        return (
            <main className="mx-auto max-w-4xl space-y-4 p-4 sm:p-6">
                <BackLink href={noteDetailHref} label="Back to Note"/>
                <Card className="space-y-4 p-4 sm:p-6">
                    <div className="h-4 w-36 animate-pulse rounded bg-foreground/10"/>
                    <div className="h-8 w-3/4 animate-pulse rounded bg-foreground/10"/>
                    <div className="h-24 w-full animate-pulse rounded bg-foreground/10"/>
                </Card>
            </main>
        );
    }

    return (
        <main
            className={cn(
                "mx-auto w-full max-w-3xl space-y-4 px-4 py-6 sm:px-6 sm:py-10",
                phase === "running" && "pb-28 sm:pb-28",
            )}
        >
            {phase === "running" ? (
                <div
                    data-testid="long-exam-top-bar"
                    className="sticky top-16 z-20 -mx-4 flex items-center gap-3 border-b border-foreground/15 bg-background/95 px-4 py-3 backdrop-blur sm:mx-0 sm:rounded-xl sm:border"
                >
                    <Button variant="outline" size="sm" className="shrink-0 px-3" onClick={() => requestLeave()}
                            disabled={submitting}>
                        Leave Exam
                    </Button>
                    <div className="min-w-0 flex-1 text-center">
                        <p className="truncate text-sm font-semibold text-foreground">Long Exam Mode</p>
                    </div>
                    <div
                        data-testid="long-exam-timer"
                        data-timer-state={timerState}
                        className={cn(
                            "shrink-0 rounded-full border px-3 py-1 text-sm font-semibold",
                            timerState === "normal"
                                ? "border-foreground/15 bg-background text-foreground"
                                : timerState === "warning"
                                    ? "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300"
                                    : "border-red-500/45 bg-red-500/10 text-red-700 dark:text-red-300",
                        )}
                        aria-label="Exam timer"
                    >
                        {formatTimer(remainingSeconds)}
                    </div>
                </div>
            ) : (
                <div className="flex items-center justify-between gap-3">
                    <BackLink href={noteDetailHref} label="Note"/>
                </div>
            )}

            {phase === "running" && showFocusTip ? (
                <div
                    className="flex items-center justify-between gap-3 rounded-lg border border-border bg-muted/50 px-4 py-2.5 text-sm text-foreground/75">
                    <span>{LONG_EXAM_FOCUS_TIP}</span>
                    <Button variant="ghost" size="sm" className="shrink-0" onClick={dismissFocusTip}>
                        Got it
                    </Button>
                </div>
            ) : null}

            {error ? (
                <Card
                    className="space-y-3 border-red-500/30 bg-red-500/5 p-4 text-sm text-red-700 dark:text-red-300 sm:p-5">
                    <p>{error}</p>
                    <Button type="button" variant="outline" className="w-full sm:w-auto"
                            onClick={() => router.push(noteDetailHref)}>
                        Back to Note
                    </Button>
                </Card>
            ) : null}

            {phase === "prestart" ? (
                <section className="space-y-6 sm:space-y-8">
                    <header className="space-y-3">
                        <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
                            Long Exam
                        </h1>
                        <p className="max-w-2xl text-base leading-relaxed text-foreground/70 sm:text-lg">
                            A comprehensive mastery sitting built from your note. Treat it as a focused study session, not a quick quiz.
                        </p>
                        {note?.title ? (
                            <p className="text-sm text-foreground/55">
                                Built from <span className="font-medium text-foreground/80">{note.title}</span>
                            </p>
                        ) : null}
                    </header>

                    {hasActiveInProgressPrompt ? (
                        <div className="space-y-3 rounded-2xl border border-border bg-card p-5 sm:p-6">
                            <p className="text-base font-medium text-foreground">You have an active session.</p>
                            <p className="text-sm leading-relaxed text-foreground/70">
                                Resume to keep your saved answers and current question position, or start fresh to forfeit the active session.
                            </p>
                            <div className="flex flex-col gap-2 sm:flex-row">
                                <Button type="button" className="w-full sm:w-auto"
                                        onClick={() => void handleResumeActiveSession()}>
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
                            <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
                                <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">
                                    What to expect
                                </h2>
                                <ul className="mt-5 space-y-5">
                                    <li className="flex gap-4">
                                        <BookOpen className="mt-0.5 h-5 w-5 shrink-0 text-foreground/70" aria-hidden="true"/>
                                        <div className="space-y-1">
                                            <p className="text-sm font-medium text-foreground">Fixed question set</p>
                                            <p className="text-sm leading-relaxed text-foreground/70">
                                                Generated upfront — no progressive add. {expectedQuestionCount} questions
                                                {selectedSourceCount > 1 ? ` spanning ${selectedSourceCount} notes` : " from this note"}.
                                            </p>
                                        </div>
                                    </li>
                                    <li className="flex gap-4">
                                        <Hourglass className="mt-0.5 h-5 w-5 shrink-0 text-foreground/70" aria-hidden="true"/>
                                        <div className="space-y-1">
                                            <p className="text-sm font-medium text-foreground">The clock does not pause</p>
                                            <p className="text-sm leading-relaxed text-foreground/70">
                                                About 90 seconds per question, fixed at start. Leaving forfeits the session.
                                            </p>
                                        </div>
                                    </li>
                                    <li className="flex gap-4">
                                        <ListChecks className="mt-0.5 h-5 w-5 shrink-0 text-foreground/70" aria-hidden="true"/>
                                        <div className="space-y-1">
                                            <p className="text-sm font-medium text-foreground">Scored against every question</p>
                                            <p className="text-sm leading-relaxed text-foreground/70">
                                                Unanswered items count toward your final score.
                                            </p>
                                        </div>
                                    </li>
                                    <li className="flex gap-4">
                                        <BarChart3 className="mt-0.5 h-5 w-5 shrink-0 text-foreground/70" aria-hidden="true"/>
                                        <div className="space-y-1">
                                            <p className="text-sm font-medium text-foreground">Mastery report at the end</p>
                                            <p className="text-sm leading-relaxed text-foreground/70">
                                                Domain breakdown, weak areas, and a suggested next step.
                                            </p>
                                        </div>
                                    </li>
                                </ul>
                            </div>

                            {availableSourceNotes.length > 0 ? (
                                <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
                                    <div className="space-y-1">
                                        <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">
                                            Span this exam across more notes
                                        </h2>
                                        <p className="text-sm text-foreground/70">
                                            Add up to 3 ready Study Packs from this subject.
                                        </p>
                                    </div>
                                    <div className="mt-4 grid gap-2">
                                        {availableSourceNotes.map((sourceNote) => {
                                            const sourceStudyPackId = sourceNote.studyPackId ?? "";
                                            const selected = selectedAdditionalStudyPackIds.includes(sourceStudyPackId);
                                            return (
                                                <button
                                                    key={sourceNote.id}
                                                    type="button"
                                                    className={cn(
                                                        "rounded-xl border px-4 py-3 text-left transition",
                                                        selected
                                                            ? "border-foreground/40 bg-foreground/5 text-foreground"
                                                            : "border-border bg-background text-foreground/75 hover:border-foreground/25 hover:bg-muted/30",
                                                    )}
                                                    aria-pressed={selected}
                                                    onClick={() => toggleAdditionalSource(sourceStudyPackId)}
                                                >
                                                    <span className="block text-sm font-medium text-foreground">{sourceNote.title ?? "Untitled note"}</span>
                                                    <span className="block text-xs text-foreground/60">{sourceNote.subject}</span>
                                                </button>
                                            );
                                        })}
                                    </div>
                                </div>
                            ) : null}

                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                <p className="text-sm text-foreground/65">
                                    {selectedSourceCount} {selectedSourceCount === 1 ? "note" : "notes"} · {expectedQuestionCount} questions
                                </p>
                                <div className="flex flex-col gap-2 sm:flex-row">
                                    <Button
                                        type="button"
                                        variant="outline"
                                        className="w-full sm:w-auto"
                                        onClick={() => router.push(modeSelectHref)}
                                        disabled={starting}
                                    >
                                        Choose another mode
                                    </Button>
                                    <Button
                                        type="button"
                                        className="w-full sm:w-auto"
                                        onClick={() => void handleStartExam()}
                                        disabled={!studyPackId || starting}
                                    >
                                        {starting ? "Starting..." : "Begin Long Exam"}
                                    </Button>
                                </div>
                            </div>
                        </>
                    )}
                </section>
            ) : null}

            {phase === "generating" ? (
                <QuizGenerationOverlay
                    title="Generating your long exam..."
                    message="Building a comprehensive exam from your study material"
                    rotatingMessages={[
                        "Analyzing your study material...",
                        "Distributing questions across topics...",
                        "Setting exam difficulty...",
                        "Generating comprehensive questions...",
                        "Almost ready...",
                    ]}
                />
            ) : null}

            {phase === "paused-recovery" ? (
                <Card className="space-y-4 p-4 text-center sm:p-8">
                    <div
                        className="mx-auto h-10 w-10 animate-spin rounded-full border-2 border-blue-500 border-t-transparent"
                        aria-hidden="true"/>
                    <div className="space-y-1">
                        <h1 className="text-xl font-semibold">Resuming exam...</h1>
                        <p className="text-sm text-foreground/70">Restoring your saved answers and question
                            position.</p>
                    </div>
                </Card>
            ) : null}

            {phase === "running" && currentQuestion ? (
                <div className="space-y-4">
                    <Card className="space-y-4 p-4 sm:p-5">
                        <div className="space-y-1">
                            <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">
                                Question {currentQuestionIndex + 1} of {totalQuestions}
                            </p>
                            <p className="text-sm text-foreground/65">
                                {answeredCount} of {totalQuestions} answered
                                {sourceNoteRefs.length > 1 ? ` | Spanning ${sourceNoteRefs.length} notes` : ""}
                            </p>
                        </div>
                        <div className="h-2 overflow-hidden rounded-full bg-muted">
                            <div className="h-full bg-blue-500 transition-all"
                                 style={{width: `${progressPercentage}%`}}/>
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
                </div>
            ) : null}

            {phase === "complete" && masteryReport ? (
                <section className="space-y-6 sm:space-y-8">
                    <header className="space-y-2">
                        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">Mastery Report</p>
                        <h1 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
                            Long Exam Complete
                        </h1>
                    </header>

                    <ScoreReveal
                        percentage={masteryReport.scorePercentage}
                        label="Overall Mastery"
                        supportingLine={`${masteryReport.answeredQuestions} of ${masteryReport.totalQuestions} answered`}
                        performanceLevel={resolveLongExamPerformanceLevel(masteryReport.scorePercentage)}
                        tone="long-exam"
                    />

                    {masteryReport.performanceSummary ? (
                        <p className="mx-auto max-w-2xl text-center text-sm leading-relaxed text-foreground/70 sm:text-base">
                            {masteryReport.performanceSummary}
                        </p>
                    ) : null}

                    <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
                        <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">Domain Breakdown</h2>
                        <ul className="mt-4 space-y-4" aria-label="Domain breakdown">
                            {masteryReport.domainBreakdown.map((domain) => (
                                <li key={domain.domain} className="space-y-1.5">
                                    <div className="flex items-baseline justify-between gap-3">
                                        <span className="text-sm font-medium text-foreground">{domain.domain}</span>
                                        <span className="text-sm font-semibold tabular-nums text-foreground/85">
                                            {domain.accuracyPercentage}%
                                        </span>
                                    </div>
                                    <div className="h-2 overflow-hidden rounded-full bg-foreground/8" aria-hidden="true">
                                        <div
                                            className="h-full rounded-full bg-foreground/70 transition-all"
                                            style={{width: `${Math.max(0, Math.min(100, domain.accuracyPercentage))}%`}}
                                        />
                                    </div>
                                    <p className="text-xs text-foreground/55">
                                        {domain.correctAnswers} of {domain.totalQuestions} correct
                                    </p>
                                </li>
                            ))}
                        </ul>
                    </div>

                    {(masteryReport.sourceNotes ?? []).length > 1 ? (
                        <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
                            <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">Sources Covered</h2>
                            <div className="mt-4 flex flex-wrap gap-2">
                                {(masteryReport.sourceNotes ?? []).map((sourceNote) => (
                                    <span key={sourceNote.noteId}
                                          className="rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-foreground/75">
                                        {sourceNote.noteTitle}
                                    </span>
                                ))}
                            </div>
                        </div>
                    ) : null}

                    <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
                        <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">Weak Domains</h2>
                        {masteryReport.weakDomains.length > 0 ? (
                            <div className="mt-4 flex flex-wrap gap-2">
                                {masteryReport.weakDomains.map((domain) => (
                                    <span key={domain}
                                          className="rounded-full border border-amber-600/40 bg-transparent px-3 py-1 text-xs font-medium text-amber-700 dark:text-amber-300">
                                        {domain}
                                    </span>
                                ))}
                            </div>
                        ) : (
                            <p className="mt-3 text-sm text-foreground/70">No weak domains flagged.</p>
                        )}
                    </div>

                    <div className="rounded-2xl border border-border bg-card p-6 sm:p-8">
                        <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">Suggested Next Step</h2>
                        <p className="mt-3 text-base leading-relaxed text-foreground sm:text-lg">
                            {masteryReport.suggestedNextStep}
                        </p>
                    </div>

                    <div className="flex flex-col gap-2 sm:flex-row">
                        <Button type="button" className="w-full sm:w-auto" onClick={() => router.push(noteDetailHref)}>
                            Back to Note
                        </Button>
                        <Button type="button" variant="outline" className="w-full sm:w-auto"
                                onClick={() => router.push(noteDetailHref)}>
                            Study Again
                        </Button>
                    </div>
                </section>
            ) : null}

            {phase === "running" ? (
                <StickyAssessmentFooter>
                    <div className="flex items-center justify-between">
                        <Button
                            type="button"
                            variant="outline"
                            onClick={handlePrevious}
                            disabled={currentQuestionIndex === 0 || submitting}
                        >
                            Previous
                        </Button>
                        <div className="flex gap-2">
                            {isLastQuestion ? (
                                <Button type="button" onClick={() => void handleComplete(false)} disabled={submitting}>
                                    {submitting ? "Submitting..." : "Submit Exam"}
                                </Button>
                            ) : (
                                <Button type="button" onClick={handleNext} disabled={submitting}>
                                    Next
                                </Button>
                            )}
                        </div>
                    </div>
                </StickyAssessmentFooter>
            ) : null}

            <LeaveQuizModal/>

            {toast ? <ToastMessage message={toast.message} tone={toast.tone}/> : null}
        </main>
    );
}
