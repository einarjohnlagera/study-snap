"use client";

import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import Link from "next/link";
import {useParams, useRouter, useSearchParams} from "next/navigation";
import {BarChart3, BookOpen, Hourglass, ListChecks} from "lucide-react";
import {BackLink} from "@/components/ui/back-link";
import {Button} from "@/components/ui/button";
import {Card} from "@/components/ui/card";
import {ExamTopBar} from "@/components/exam-mode/exam-top-bar";
import {useBottomViewportClaim, useExamFocusMode} from "@/components/exam-mode/exam-focus-context";
import {QuestionNavigator} from "@/components/exam-mode/question-navigator";
import {ScoreReveal} from "@/components/exam-mode/score-reveal";
import {QuizChoiceList} from "@/components/study-pack/quiz-choice-list";
import {QuizMatchingGroup} from "@/components/study-pack/quiz-matching-group";
import { QuizQuestionText } from "@/components/study-pack/quiz-question-text";
import {QuizGenerationOverlay} from "@/components/study-pack/quiz-generation-overlay";
import {ReviewCommitmentPrompt} from "@/components/study-pack/review-commitment-prompt";
import {useQuizSessionGuard} from "@/components/study-pack/quiz-session-guard";
import {StickyAssessmentFooter} from "@/components/ui/sticky-assessment-footer";
import {PaywallModal} from "@/components/billing/paywall-modal";
import {QuizFeedbackPanel} from "@/components/feedback/quiz-feedback-panel";
import {ToastMessage} from "@/components/ui/toast-message";
import {getAuthUser} from "@/lib/auth";
import {getCollectionLabels} from "@/lib/collection-labels";
import {
    completeLongExamSession,
    forfeitLongExamSession,
    getActiveLongExamSession,
    getCollection,
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
import {resolveCollectionScopedSourceNotes} from "@/lib/collection-exam";
import {resolveQuizCorrectIndex, resolveQuizItemGroupAt} from "@/lib/quiz";
import {cn} from "@/lib/utils";
import {getUpgradeCtas, type AppPlanType} from "@/src/config/plans";

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

function normalizeSelectedMultiChoices(selectedChoices?: Record<string, number[]> | null): Record<string, number[]> {
    return {...selectedChoices};
}

function getSelectedChoice(
    selectedChoices: Record<string, number>,
    questionIndex: number,
): number | null {
    return selectedChoices[String(questionIndex)] ?? null;
}

function getSelectedMultiChoices(
    selectedChoices: Record<string, number[]>,
    questionIndex: number,
): number[] {
    return selectedChoices[String(questionIndex)] ?? [];
}

function getAnsweredCount(selectedChoices: Record<string, number>, selectedMultiChoices: Record<string, number[]>): number {
    return new Set([
        ...Object.keys(selectedChoices),
        ...Object.entries(selectedMultiChoices)
            .filter(([, value]) => value.length > 0)
            .map(([key]) => key),
    ]).size;
}

function getNowEpochSeconds(): number {
    return Math.floor(Date.now() / 1000);
}

function normalizeSubjectForMatch(subject?: string | null): string {
    return subject?.trim().toLocaleLowerCase("en") ?? "";
}

function isLongExamSessionExpired(activeSession: LongExamStartResponse): boolean {
    if (!activeSession.timeLimitSeconds || !activeSession.timerStartedAtEpochSeconds) {
        return false;
    }
    const deadline = resolveDeadlineEpochSeconds(
        activeSession.timeLimitSeconds,
        { timerStartedAtEpochSeconds: activeSession.timerStartedAtEpochSeconds },
        getNowEpochSeconds(),
    );
    return resolveRemainingSecondsFromDeadline(deadline, getNowEpochSeconds()) <= 0;
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
    const searchParams = useSearchParams();
    const params = useParams<{ id: string }>();
    const noteId = useMemo(() => {
        if (!params?.id) {
            return "";
        }
        return Array.isArray(params.id) ? params.id[0] : params.id;
    }, [params]);
    const collectionId = useMemo(() => searchParams.get("collectionId")?.trim() || null, [searchParams]);

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
    const [showLongExamPaywall, setShowLongExamPaywall] = useState(false);
    const [longExamUsedThisMonth, setLongExamUsedThisMonth] = useState(0);
    const [longExamMonthlyLimit, setLongExamMonthlyLimit] = useState(0);
    const [activeStartResponse, setActiveStartResponse] = useState<LongExamStartResponse | null>(null);
    const [sessionId, setSessionId] = useState<string | null>(null);
    const [quiz, setQuiz] = useState<QuizItem[]>([]);
    const [selectedChoices, setSelectedChoices] = useState<Record<string, number>>({});
    const [selectedMultiChoices, setSelectedMultiChoices] = useState<Record<string, number[]>>({});
    const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
    const [masteryReport, setMasteryReport] = useState<LongExamMasteryReportResponse | null>(null);
    const [deadlineEpochSeconds, setDeadlineEpochSeconds] = useState<number | null>(null);
    const [remainingSeconds, setRemainingSeconds] = useState(0);
    const [timeLimitSeconds, setTimeLimitSeconds] = useState(0);
    const remainingSecondsRef = useRef(0);
    const timeoutAutoSubmitRequestedRef = useRef(false);
    const startedTrackedRef = useRef(false);

    const noteDetailHref = useMemo(() => (note ? `/notes/${note.id}` : `/notes/${noteId}`), [note, noteId]);
    const planBackHref = collectionId ? `/collections/${collectionId}` : noteDetailHref;
    const planBackLabel = collectionId ? getCollectionLabels(getAuthUser()?.profileType).singular : "Note";
    const studyPackId = note?.studyPackId ?? null;
    const modeSelectHref = studyPackId ? `/study-packs/${studyPackId}/challenge-quiz` : noteDetailHref;
    const totalQuestions = quiz.length;
    const expectedQuestionCount = resolveExpectedLongExamQuestionCount(profileLearnerLevel);
    const selectedSourceCount = 1 + selectedAdditionalStudyPackIds.length;
    const longExamRemaining = Math.max(0, longExamMonthlyLimit - longExamUsedThisMonth);
    const longExamLimitReached = longExamMonthlyLimit > 0 && longExamUsedThisMonth >= longExamMonthlyLimit;
    const currentPlanType = getAuthUser()?.planType ?? "FREE";
    const longExamUpgradeCtas = getUpgradeCtas(currentPlanType as AppPlanType);
    const longExamStartDisabled = !studyPackId || starting || (currentPlanType === "PRO" && longExamLimitReached);
    const currentQuestion = totalQuestions > 0 ? quiz[currentQuestionIndex] ?? null : null;
    const currentMatchingGroup = resolveQuizItemGroupAt(quiz, currentQuestionIndex);
    const answeredCount = useMemo(() => getAnsweredCount(selectedChoices, selectedMultiChoices), [selectedChoices, selectedMultiChoices]);
    const progressPercentage = totalQuestions > 0 ? Math.round((answeredCount / totalQuestions) * 100) : 0;
    const hasActiveInProgressPrompt = activeStartResponse?.status === "IN_PROGRESS" && activeStartResponse.canResume;
    const timerState = resolveBoardExamTimerState(remainingSeconds);
    const longExamActive = phase === "running" && Boolean(sessionId);
    const isLastQuestion = (currentMatchingGroup?.endIndex ?? currentQuestionIndex) === totalQuestions - 1;
    useBottomViewportClaim(longExamActive);
    useExamFocusMode(phase === "running");

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
        sessionId: string | null;
        totalQuestions?: number;
        difficulty?: string | null
    }) => {
        if (startedTrackedRef.current || !response.sessionId) {
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
        if (!response.sessionId) {
            return;
        }
        setActiveStartResponse(null);
        setSessionId(response.sessionId);
        setQuiz(response.quiz);
        setSourceNoteRefs(response.sourceNoteRefs ?? []);
        setSelectedChoices({});
        setSelectedMultiChoices({});
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
        setSelectedMultiChoices(normalizeSelectedMultiChoices(response.selectedMultiChoices));
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
                if (collectionId) {
                    try {
                        const collection = await getCollection(collectionId);
                        const collectionSourceNotes = resolveCollectionScopedSourceNotes(
                            collection,
                            notes,
                            noteDetail.id,
                            {requireStudyPackId: true},
                        );
                        setAvailableSourceNotes(collectionSourceNotes);
                        setSelectedAdditionalStudyPackIds(
                            collectionSourceNotes
                                .map((sourceNote) => sourceNote.studyPackId)
                                .filter((studyPackId): studyPackId is string => Boolean(studyPackId))
                                .slice(0, LONG_EXAM_MAX_ADDITIONAL_NOTES),
                        );
                    } catch {
                        setAvailableSourceNotes(resolveSameSubjectSourceNotes(noteDetail, notes));
                        setSelectedAdditionalStudyPackIds([]);
                    }
                } else {
                    setAvailableSourceNotes(resolveSameSubjectSourceNotes(noteDetail, notes));
                    setSelectedAdditionalStudyPackIds([]);
                }
            } catch {
                setAvailableSourceNotes([]);
                setSelectedAdditionalStudyPackIds([]);
            }
            if (getAuthUser()?.planType !== "PRO") {
                setActiveStartResponse(null);
                return;
            }

            const activeSession = await getActiveLongExamSession(noteDetail.studyPackId);
            setLongExamUsedThisMonth(activeSession?.usedThisMonth ?? 0);
            setLongExamMonthlyLimit(activeSession?.monthlyLimit ?? 0);
            if (!activeSession?.sessionId || !activeSession.status) {
                setActiveStartResponse(null);
                return;
            }

            if (activeSession.status === "IN_PROGRESS" && isLongExamSessionExpired(activeSession)) {
                try {
                    await forfeitLongExamSession(activeSession.sessionId);
                } catch {
                    // If forfeit fails we still treat the session as gone locally; the user can start fresh.
                }
                setActiveStartResponse(null);
                setSessionId(null);
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
    }, [collectionId, enterRunningFromStart, noteId, router, trackStarted]);

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
        if (getAuthUser()?.planType !== "PRO") {
            setShowLongExamPaywall(true);
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
            setLongExamUsedThisMonth(response.usedThisMonth);
            setLongExamMonthlyLimit(response.monthlyLimit);
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
            const message = err instanceof Error ? err.message : "Could not start Long Exam.";
            setError(message.toLowerCase().includes("monthly long exam limit")
                ? "You've reached your monthly Long Exam limit."
                : message);
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
            setSelectedMultiChoices({});
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

    const handleSelectMultiChoices = useCallback(async (choiceIndices: number[]) => {
        if (!sessionId || !currentQuestion || savingProgress || currentQuestion.questionFormat !== "MULTI_SELECT") {
            return;
        }
        const choiceKey = String(currentQuestionIndex);
        setSelectedMultiChoices((current) => ({
            ...current,
            [choiceKey]: choiceIndices,
        }));
        setSavingProgress(true);
        try {
            const response = await saveLongExamProgress(sessionId, {
                questionIndex: currentQuestionIndex,
                selectedChoiceIndex: choiceIndices[0] ?? 0,
                selectedMultiChoiceIndices: choiceIndices,
            });
            setSelectedChoices(normalizeSelectedChoices(response.selectedChoices));
            setSelectedMultiChoices(normalizeSelectedMultiChoices(response.selectedMultiChoices));
            setCurrentQuestionIndex(Math.min(Math.max(response.currentQuestionIndex, 0), Math.max(response.totalQuestions - 1, 0)));
        } catch {
            showToast("Could not save that answer. Your selection is still visible.", "error");
        } finally {
            setSavingProgress(false);
        }
    }, [currentQuestion, currentQuestionIndex, savingProgress, sessionId, showToast]);

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
            setSelectedMultiChoices(normalizeSelectedMultiChoices(response.selectedMultiChoices));
            setCurrentQuestionIndex(Math.min(Math.max(response.currentQuestionIndex, 0), Math.max(response.totalQuestions - 1, 0)));
        } catch {
            showToast("Could not save that answer. Your selection is still visible.", "error");
        } finally {
            setSavingProgress(false);
        }
    }, [currentQuestion, currentQuestionIndex, savingProgress, sessionId, showToast]);

    const handleSelectMatchingChoice = useCallback(async (questionIndex: number, choiceIndex: number) => {
        if (!sessionId || savingProgress || !currentMatchingGroup) {
            return;
        }
        const choiceKey = String(questionIndex);
        setSelectedChoices((current) => ({
            ...current,
            [choiceKey]: choiceIndex,
        }));
        setSavingProgress(true);
        try {
            const response = await saveLongExamProgress(sessionId, {
                questionIndex,
                selectedChoiceIndex: choiceIndex,
            });
            setSelectedChoices(normalizeSelectedChoices(response.selectedChoices));
            setSelectedMultiChoices(normalizeSelectedMultiChoices(response.selectedMultiChoices));
            setCurrentQuestionIndex(currentMatchingGroup.startIndex);
        } catch {
            showToast("Could not save that answer. Your selection is still visible.", "error");
        } finally {
            setSavingProgress(false);
        }
    }, [currentMatchingGroup, savingProgress, sessionId, showToast]);

    const handlePrevious = useCallback(() => {
        setCurrentQuestionIndex((index) => Math.max((currentMatchingGroup?.startIndex ?? index) - 1, 0));
    }, [currentMatchingGroup]);

    const handleNext = useCallback(() => {
        setCurrentQuestionIndex((index) => Math.min(currentMatchingGroup ? currentMatchingGroup.endIndex + 1 : index + 1, totalQuestions - 1));
    }, [currentMatchingGroup, totalQuestions]);

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

    if (loading) {
        return (
            <main className="mx-auto max-w-4xl space-y-4 p-4 sm:p-6">
                <BackLink href={planBackHref} label={planBackLabel} />
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
                <ExamTopBar
                    modeLabel="Long Exam"
                    leaveLabel="Leave Exam"
                    onLeave={() => requestLeave()}
                    leaveDisabled={submitting}
                    remainingSeconds={remainingSeconds}
                    timerState={timerState}
                    tone="long-exam"
                    testId="long-exam-top-bar"
                    timerTestId="long-exam-timer"
                />
            ) : (
                <div className="flex items-center justify-between gap-3">
                    <BackLink href={planBackHref} label={planBackLabel}/>
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
                                            {collectionId
                                                ? `Add up to ${LONG_EXAM_MAX_ADDITIONAL_NOTES} more notes from this plan.`
                                                : `Add up to ${LONG_EXAM_MAX_ADDITIONAL_NOTES} ready Study Packs from this subject.`}
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
                                        {selectedAdditionalStudyPackIds.length > 0 ? (
                                            <>
                                                <p className="flex items-center gap-2 text-sm text-foreground/70">
                                                    <Hourglass className="h-4 w-4 shrink-0" aria-hidden="true"/>
                                                    <span>Generating from multiple notes may take up to a minute.</span>
                                                </p>
                                                {currentPlanType === "PRO" ? (
                                                    <>
                                                        <p className="text-sm text-foreground/70">
                                                            This session uses 1 of your {longExamRemaining} remaining Long Exam sessions.
                                                        </p>
                                                    </>
                                                ) : null}
                                            </>
                                        ) : null}
                                    </div>
                                </div>
                            ) : note?.subject ? (
                                <p className="text-sm text-foreground/55">
                                    Want a multi-note exam?{" "}
                                    <Link href="/notes/new" className="underline underline-offset-2 hover:text-foreground/80">
                                        Create another note
                                    </Link>{" "}
                                    with the subject <span className="font-medium text-foreground/75">{note.subject}</span> and it will appear here.
                                </p>
                            ) : null}

                            {longExamLimitReached ? (
                                <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-500/40 dark:bg-amber-950/25 dark:text-amber-100">
                                    <p className="font-medium">You&apos;ve used all {longExamMonthlyLimit} Long Exam sessions for this month.</p>
                                    <p className="mt-1 text-amber-900/80 dark:text-amber-100/80">
                                        You can still review existing results. Start a new Long Exam when your quota resets.
                                    </p>
                                    {longExamUpgradeCtas.primary ? (
                                        <Button
                                            type="button"
                                            className="mt-3 w-full sm:w-auto"
                                            onClick={() => router.push("/settings?section=plans")}
                                        >
                                            {longExamUpgradeCtas.primary.label}
                                        </Button>
                                    ) : null}
                                </div>
                            ) : null}

                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                <p className="text-sm text-foreground/65">
                                    {selectedSourceCount} {selectedSourceCount === 1 ? "note" : "notes"} · {expectedQuestionCount} questions
                                </p>
                                <div className="flex flex-col gap-2 sm:flex-row">
                                    {collectionId ? null : (
                                        <Button
                                            type="button"
                                            variant="outline"
                                            className="w-full sm:w-auto"
                                            onClick={() => router.push(modeSelectHref)}
                                            disabled={starting}
                                        >
                                            Choose another mode
                                        </Button>
                                    )}
                                    <Button
                                        type="button"
                                        className="w-full sm:w-auto"
                                        onClick={() => void handleStartExam()}
                                        disabled={longExamStartDisabled}
                                    >
                                        {currentPlanType === "PRO"
                                            ? starting ? "Starting..." : "Begin Long Exam"
                                            : "Unlock Long Exam - Pro"}
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
                <div className="space-y-5">
                    <div className="space-y-2 px-1">
                        <div className="flex flex-wrap items-baseline justify-between gap-2">
                            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">
                                Question {currentQuestionIndex + 1} of {totalQuestions}
                            </p>
                            <p className="text-xs tabular-nums text-foreground/55">
                                {answeredCount} / {totalQuestions} answered{sourceNoteRefs.length > 1 ? ` · ${sourceNoteRefs.length} notes` : ""}
                            </p>
                        </div>
                        <div className="h-1 overflow-hidden rounded-full bg-foreground/8" aria-hidden="true">
                            <div className="h-full rounded-full bg-foreground/60 transition-all"
                                 style={{width: `${progressPercentage}%`}}/>
                        </div>
                    </div>

                    {sourceNoteRefs.length > 1 ? (
                        <div className="rounded-xl border border-foreground/15 bg-muted/20 p-4 text-sm text-foreground/80">
                            <p className="font-medium text-foreground">Sources · {sourceNoteRefs.length} notes</p>
                            <p className="mt-1 text-foreground/65">
                                {sourceNoteRefs.map((source) => source.noteTitle || "Untitled note").join(", ")}
                            </p>
                        </div>
                    ) : null}

                    <Card className="space-y-5 border-foreground/15 bg-card p-5 sm:p-6">
                        {currentMatchingGroup ? (
                            <QuizMatchingGroup
                                items={currentMatchingGroup.items}
                                groupStartIndex={currentMatchingGroup.startIndex}
                                selectedChoices={Object.fromEntries(
                                    Object.entries(selectedChoices).map(([key, value]) => [Number(key), value]),
                                )}
                                revealAnswer={false}
                                selectionStyle="board-exam"
                                disabled={submitting}
                                onSelectChoice={(questionIndex, choiceIndex) => void handleSelectMatchingChoice(questionIndex, choiceIndex)}
                            />
                        ) : (
                            <>
                                <h1 className="text-xl font-semibold leading-relaxed text-foreground sm:text-2xl"><QuizQuestionText text={currentQuestion.question} /></h1>
                                <QuizChoiceList
                                    questionKey={currentQuestion.question}
                                    choices={currentQuestion.choices}
                                    correctIndex={resolveQuizCorrectIndex(currentQuestion)}
                                    correctIndices={currentQuestion.correctIndices}
                                    questionFormat={currentQuestion.questionFormat}
                                    selectedChoiceIndex={getSelectedChoice(selectedChoices, currentQuestionIndex)}
                                    selectedMultiChoiceIndices={getSelectedMultiChoices(selectedMultiChoices, currentQuestionIndex)}
                                    revealAnswer={false}
                                    selectionStyle="board-exam"
                                    disabled={submitting}
                                    onSelectChoice={(choiceIndex) => void handleSelectChoice(choiceIndex)}
                                    onSelectMultiChoices={(choiceIndices) => void handleSelectMultiChoices(choiceIndices)}
                                />
                            </>
                        )}
                        {savingProgress ? (
                            <p className="text-xs text-foreground/55">Saving answer...</p>
                        ) : null}
                    </Card>

                    <QuestionNavigator
                        total={totalQuestions}
                        currentIndex={currentQuestionIndex}
                        isAnswered={(index) => getSelectedChoice(selectedChoices, index) !== null || getSelectedMultiChoices(selectedMultiChoices, index).length > 0}
                        onSelect={(index) => setCurrentQuestionIndex(index)}
                        summary={`Question ${currentQuestionIndex + 1} of ${totalQuestions} · ${answeredCount} answered`}
                        disabled={submitting}
                        tone="long-exam"
                        testId="long-exam-question-navigator"
                        disclosureTestId="long-exam-question-navigator-disclosure"
                    />
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

                    <ReviewCommitmentPrompt
                        isFirstCompletedSessionEver={masteryReport.isFirstCompletedSessionEver}
                        noteId={noteId}
                    />

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

                    <QuizFeedbackPanel
                        quizLabel="Long Exam"
                        noteTitle={note?.title}
                        section="results"
                        isFirstCompletedSessionEver={masteryReport.isFirstCompletedSessionEver}
                        isSecondCompletedSessionEver={masteryReport.isSecondCompletedSessionEver}
                        userId={getAuthUser()?.id}
                    />
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
            {showLongExamPaywall ? (
                <PaywallModal
                    isOpen={showLongExamPaywall}
                    variant="long-exam-mode"
                    source="long_exam_start"
                    onClose={() => setShowLongExamPaywall(false)}
                />
            ) : null}

            {toast ? <ToastMessage message={toast.message} tone={toast.tone}/> : null}
        </main>
    );
}
