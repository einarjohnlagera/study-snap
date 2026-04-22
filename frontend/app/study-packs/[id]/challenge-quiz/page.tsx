"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { ChevronDown } from "lucide-react";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { QuizFeedbackPanel } from "@/components/feedback/quiz-feedback-panel";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { BackLink } from "@/components/ui/back-link";
import { AppModal } from "@/components/ui/app-modal";
import { QuizAnswerReview } from "@/components/study-pack/quiz-answer-review";
import { QuizGenerationOverlay } from "@/components/study-pack/quiz-generation-overlay";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { useQuizSessionGuard } from "@/components/study-pack/quiz-session-guard";
import { getAuthUser } from "@/lib/auth";
import { clearFirstStudyOnboardingStep, getFirstStudyOnboardingStep } from "@/lib/first-study-onboarding";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  type ChallengeQuizStartRequest,
  type ChallengeQuizMode,
  completeChallengeQuizSession,
  forfeitChallengeQuizSession,
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
import {
  computeScore,
  mapPerformanceLevel,
} from "@/lib/challenge-quiz-results";
import {
  type BoardExamTimerState,
  resolveBoardExamTimerState,
  resolveDeadlineEpochSeconds,
  resolveRemainingSecondsFromDeadline,
} from "@/lib/challenge-quiz-timer";
import {
  resolveQuizCorrectIndex,
  serializeSelectedChoiceIndexRecord,
  toSelectedChoiceIndexRecord,
} from "@/lib/quiz";
import {
  CHALLENGE_QUIZ_ENTRY_QUERY_PARAM,
  isModeSelectionChallengeQuizEntry,
} from "@/lib/challenge-quiz-entry";
import { cn } from "@/lib/utils";

type ChallengePhase = "prestart" | "generating" | "running" | "complete" | "limit-reached";
type ChallengePrestartStep = "mode-selection" | "challenge-setup" | "board-exam-setup";
type ChallengeSessionStatePayload = {
  selectedChoices?: Record<string, number> | Record<string, string>;
  timerStartedAtEpochSeconds?: number;
};
type ChallengeDifficulty = NonNullable<ChallengeQuizStartRequest["difficulty"]>;
type ChallengeViewerProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER" | null;
type ChallengeViewerPlanType = "FREE" | "PREMIUM" | null;

const CHALLENGE_MODE: ChallengeQuizMode = "challenge";
const BOARD_EXAM_MODE: ChallengeQuizMode = "board_exam";
const TIMER_TICK_INTERVAL_MS = 1000;
const BOARD_EXAM_TOOLTIP_STORAGE_KEY_PREFIX = "notelib-board-exam-mode-tip-dismissed";
const BOARD_EXAM_START_CONFIRM_TITLE = "Start Board Exam Mode?";
const BOARD_EXAM_LEAVE_TITLE = "Leave exam?";
const BOARD_EXAM_LEAVE_DESCRIPTION = "Your progress will be submitted and counted as complete.";
const BOARD_EXAM_LEAVE_ERROR = "Could not submit and leave. Please try again.";
const BOARD_EXAM_BEFORE_UNLOAD_MESSAGE = "You are currently in Board Exam Mode. Leaving will submit your current answers and end the exam.";
const BOARD_EXAM_FOCUS_TIP = "Board Exam Mode hides distractions to simulate a real test environment.";
const MOBILE_NAVIGATOR_MEDIA_QUERY = "(max-width: 639px)";

function isMobileQuestionNavigatorViewport(): boolean {
  if (globalThis.window === undefined || typeof globalThis.matchMedia !== "function") {
    return false;
  }
  return globalThis.matchMedia(MOBILE_NAVIGATOR_MEDIA_QUERY).matches;
}

function shouldCollapseQuestionNavigatorByDefault(
  mode: ChallengeQuizMode,
  isMobileViewport: boolean,
): boolean {
  if (mode === BOARD_EXAM_MODE) {
    return true;
  }
  return isMobileViewport;
}

function getQuestionCountForDifficulty(difficulty: ChallengeDifficulty): number {
  if (difficulty === "easy") {
    return 10;
  }
  if (difficulty === "hard") {
    return 15;
  }
  return 12;
}

function getQuestionCountSummary(
  difficultySelectionAvailable: boolean | undefined,
  selectedDifficulty: ChallengeDifficulty,
): string {
  if (difficultySelectionAvailable) {
    return `${getQuestionCountForDifficulty(selectedDifficulty)} questions for the selected difficulty.`;
  }
  return "Recommended question count based on your recent performance.";
}

function normalizePracticeDifficulty(
  difficulty: ChallengeQuizStartResponse["selectedDifficulty"] | null | undefined,
): ChallengeDifficulty {
  if (difficulty === "easy" || difficulty === "hard") {
    return difficulty;
  }
  return "medium";
}

function resolveRecoveryPrestartStep(mode: ChallengeQuizMode): ChallengePrestartStep {
  if (mode === BOARD_EXAM_MODE) {
    return "board-exam-setup";
  }
  return "challenge-setup";
}

function resolveInitialPrestartStep(): ChallengePrestartStep {
  return "mode-selection";
}

function shouldShowChallengeQuizLimitPage(planType: ChallengeViewerPlanType): boolean {
  return planType === "PREMIUM";
}

function resolvePreferredChallengeMode(profileType: string | null | undefined): ChallengeQuizMode {
  return profileType === "BOARD_EXAM" ? BOARD_EXAM_MODE : CHALLENGE_MODE;
}

function isChallengeViewerProfileType(value: string | null | undefined): value is Exclude<ChallengeViewerProfileType, null> {
  return value === "STUDENT" || value === "BOARD_EXAM" || value === "TEACHER";
}

async function requestBoardExamFullscreen() {
  const element = globalThis.document?.documentElement;
  if (!element || typeof element.requestFullscreen !== "function" || globalThis.document.fullscreenElement) {
    return;
  }
  await element.requestFullscreen().catch(() => undefined);
}

function formatTimer(seconds: number): string {
  const safeSeconds = Math.max(0, seconds);
  const minutes = Math.floor(safeSeconds / 60);
  const remaining = safeSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(remaining).padStart(2, "0")}`;
}

function getNowEpochSeconds(): number {
  return Math.floor(Date.now() / 1000);
}

function getBoardExamTimerDescription(timerState: BoardExamTimerState): string | null {
  if (timerState === "warning") {
    return "Less than 3 minutes remaining.";
  }
  if (timerState === "urgent") {
    return "Final minute.";
  }
  if (timerState === "expired") {
    return "Time has expired.";
  }
  return null;
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

function getChallengeResultMessage(scorePercentage: number, mode: ChallengeQuizMode) {
  const level = mapPerformanceLevel(scorePercentage);
  if (mode === BOARD_EXAM_MODE) {
    if (level === "Excellent") return "Exam complete. You performed at an excellent level.";
    if (level === "Good") return "Exam complete. Your performance is strong with room to sharpen a few topics.";
    if (level === "Fair") return "Exam complete. Review the weak concepts below before your next quiz.";
    return "Exam complete. Use the weak concepts below to rebuild confidence before retaking.";
  }

  if (level === "Excellent") return "Outstanding. You've mastered this material.";
  if (level === "Good") return "Great work. You have a solid grasp of this.";
  if (level === "Fair") return "Good effort. Review the weak concepts below to improve.";
  return "Keep going. Focus on the weak concepts below to build confidence.";
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

export default function ChallengeQuizPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const params = useParams<{ id: string }>();
  const progressRef = useRef<{ currentIndex: number; selectedChoices: Record<number, number> }>({
    currentIndex: 0,
    selectedChoices: {},
  });
  const remainingSecondsRef = useRef(0);
  const challengeSessionRef = useRef<ChallengeQuizStartResponse | null>(null);
  const startInFlightRef = useRef(false);
  const submitInFlightRef = useRef(false);
  const timeoutAutoSubmitRequestedRef = useRef(false);
  const weakConceptsRef = useRef<HTMLDivElement | null>(null);
  const legacyRedirectTargetRef = useRef<string | null>(null);
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [challengeSession, setChallengeSession] = useState<ChallengeQuizStartResponse | null>(null);
  const [result, setResult] = useState<ChallengeQuizSessionResponse | null>(null);
  const [phase, setPhase] = useState<ChallengePhase>("prestart");
  const [prestartStep, setPrestartStep] = useState<ChallengePrestartStep>(resolveInitialPrestartStep);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [deadlineEpochSeconds, setDeadlineEpochSeconds] = useState<number | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedChoices, setSelectedChoices] = useState<Record<number, number>>({});
  const [timedOut, setTimedOut] = useState(false);
  const [showAnswerReview, setShowAnswerReview] = useState(false);
  const [showFirstQuizCompletionBanner, setShowFirstQuizCompletionBanner] = useState(false);
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [viewerId, setViewerId] = useState<string | null>(null);
  const [viewerPlanType, setViewerPlanType] = useState<ChallengeViewerPlanType>(null);
  const [viewerProfileType, setViewerProfileType] = useState<ChallengeViewerProfileType>(null);
  const [activePaywallModal, setActivePaywallModal] = useState<"board-exam-mode" | "difficulty-selection" | "challenge-quiz-limit" | null>(null);
  const [selectedDifficulty, setSelectedDifficulty] = useState<ChallengeDifficulty>("medium");
  const [selectedMode, setSelectedMode] = useState<ChallengeQuizMode>(() => (
    resolvePreferredChallengeMode(getAuthUser()?.profileType)
  ));
  const [showVerifyEmailModal, setShowVerifyEmailModal] = useState(false);
  const [showBoardExamStartModal, setShowBoardExamStartModal] = useState(false);
  const [showBoardExamFocusTip, setShowBoardExamFocusTip] = useState(false);
  const [isMobileNavigatorViewport, setIsMobileNavigatorViewport] = useState(isMobileQuestionNavigatorViewport);
  const [isQuestionNavigatorCollapsed, setIsQuestionNavigatorCollapsed] = useState(false);

  const noteId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);
  const hasModeSelectionEntryQuery = useMemo(
    () => isModeSelectionChallengeQuizEntry(searchParams.get(CHALLENGE_QUIZ_ENTRY_QUERY_PARAM)),
    [searchParams],
  );
  const [sharedModeSelectionEntryRequested, setSharedModeSelectionEntryRequested] = useState(hasModeSelectionEntryQuery);
  const noteDetailHref = useMemo(() => (note ? `/notes/${note.id}` : "/library"), [note]);
  const syncProgressRef = useCallback((nextIndex: number, nextSelectedChoices: Record<number, number>) => {
    progressRef.current = {
      currentIndex: nextIndex,
      selectedChoices: nextSelectedChoices,
    };
  }, []);
  const openLockedFeaturePaywall = useCallback(
    (variant: "board-exam-mode" | "difficulty-selection" | "challenge-quiz-limit", source: string) => {
      const feature = variant === "difficulty-selection"
        ? "difficulty"
        : variant === "challenge-quiz-limit"
          ? "quiz_limit"
          : "board_exam";
      void trackAnalyticsEvent({
        eventType: "FEATURE_LOCKED_CLICKED",
        metadata: {
          feature,
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
      const authUser = getAuthUser();
      setIsEmailVerified(Boolean(authUser?.emailVerifiedAt));
      setViewerId(authUser?.id ?? null);
      setViewerPlanType(authUser?.planType === "FREE" || authUser?.planType === "PREMIUM" ? authUser.planType : null);
      setViewerProfileType(isChallengeViewerProfileType(authUser?.profileType) ? authUser.profileType : null);
    };
    syncVerification();
    globalThis.addEventListener("studysnap-auth-change", syncVerification);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncVerification);
    };
  }, []);

  useEffect(() => {
    if (!hasModeSelectionEntryQuery) {
      return;
    }
    setSharedModeSelectionEntryRequested(true);
  }, [hasModeSelectionEntryQuery]);

  useEffect(() => {
    if (!hasModeSelectionEntryQuery) {
      return;
    }
    const nextSearchParams = new URLSearchParams(searchParams.toString());
    nextSearchParams.delete(CHALLENGE_QUIZ_ENTRY_QUERY_PARAM);
    router.replace(nextSearchParams.size > 0 ? `${pathname}?${nextSearchParams.toString()}` : pathname, { scroll: false });
  }, [hasModeSelectionEntryQuery, pathname, router, searchParams]);

  useEffect(() => {
    if (!sharedModeSelectionEntryRequested || phase !== "prestart" || challengeSession?.sessionId) {
      return;
    }
    setSelectedMode(resolvePreferredChallengeMode(viewerProfileType));
    setPrestartStep(resolveInitialPrestartStep());
  }, [challengeSession?.sessionId, phase, sharedModeSelectionEntryRequested, viewerProfileType]);

  useEffect(() => {
    challengeSessionRef.current = challengeSession;
  }, [challengeSession]);

  useEffect(() => {
    if (globalThis.window === undefined || typeof globalThis.matchMedia !== "function") {
      return;
    }
    const mediaQuery = globalThis.matchMedia(MOBILE_NAVIGATOR_MEDIA_QUERY);
    const updateViewport = (event?: MediaQueryListEvent) => {
      setIsMobileNavigatorViewport(event ? event.matches : mediaQuery.matches);
    };
    updateViewport();
    if (typeof mediaQuery.addEventListener === "function") {
      mediaQuery.addEventListener("change", updateViewport);
      return () => {
        mediaQuery.removeEventListener("change", updateViewport);
      };
    }
    mediaQuery.addListener(updateViewport);
    return () => {
      mediaQuery.removeListener(updateViewport);
    };
  }, []);

  const applyStartedSession = useCallback((started: ChallengeQuizStartResponse, forceRunning = false) => {
    timeoutAutoSubmitRequestedRef.current = false;
    setSelectedMode(started.mode ?? CHALLENGE_MODE);
    setSelectedDifficulty(normalizePracticeDifficulty(started.selectedDifficulty));

    if (started.status === "GENERATING") {
      syncProgressRef(0, {});
      setChallengeSession(started);
      setResult(null);
      setError(null);
      setSelectedChoices({});
      setCurrentIndex(0);
      setDeadlineEpochSeconds(null);
      setRemainingSeconds(0);
      setTimedOut(false);
      setShowAnswerReview(false);
      setPrestartStep(resolveRecoveryPrestartStep(started.mode));
      setPhase("generating");
      return;
    }

    if (started.status === "FAILED") {
      syncProgressRef(0, {});
      setChallengeSession(started);
      setResult(null);
      setSelectedChoices({});
      setCurrentIndex(0);
      setDeadlineEpochSeconds(null);
      setRemainingSeconds(0);
      setTimedOut(false);
      setShowAnswerReview(false);
      setPrestartStep(resolveRecoveryPrestartStep(started.mode));
      setError(started.mode === BOARD_EXAM_MODE
        ? "We couldn't generate Board Exam Mode this time. Try again."
        : "We couldn't generate the Challenge Quiz this time. Try again.");
      setPhase("prestart");
      return;
    }

    if (!started.sessionId || started.quiz.length === 0) {
      syncProgressRef(0, {});
      setChallengeSession(started);
      setResult(null);
      setError(null);
      setSelectedChoices({});
      setCurrentIndex(0);
      setDeadlineEpochSeconds(null);
      setRemainingSeconds(0);
      setTimedOut(false);
      setShowAnswerReview(false);
      setPrestartStep(resolveRecoveryPrestartStep(started.mode));
      setPhase("prestart");
      return;
    }

    const state = (started.sessionState ?? {}) as ChallengeSessionStatePayload;
    const restoredChoices = toSelectedChoiceIndexRecord(state.selectedChoices, started.quiz);
    const normalizedIndex = Math.max(0, Math.min(started.currentQuestionIndex ?? 0, Math.max(0, started.quiz.length - 1)));
    const nextDeadlineEpochSeconds = resolveDeadlineEpochSeconds(
      started.timeLimitSeconds,
      state,
      getNowEpochSeconds(),
    );
    syncProgressRef(normalizedIndex, restoredChoices);

    setChallengeSession(started);
    setResult(null);
    setError(null);
    setSelectedChoices(restoredChoices);
    setCurrentIndex(normalizedIndex);
    setDeadlineEpochSeconds(nextDeadlineEpochSeconds);
    setRemainingSeconds(resolveRemainingSecondsFromDeadline(nextDeadlineEpochSeconds, getNowEpochSeconds()));
    setTimedOut(false);
    setShowAnswerReview(false);
    setPhase(forceRunning || Boolean(started.sessionId) ? "running" : "prestart");
  }, [syncProgressRef]);

  const persistProgress = useCallback(
    (nextIndex: number, nextSelectedChoices: Record<number, number>, keepalive = false) => {
      if (!challengeSession?.sessionId) {
        return;
      }

      const sessionState = {
        selectedChoices: serializeSelectedChoiceIndexRecord(nextSelectedChoices),
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
    timeoutAutoSubmitRequestedRef.current = false;
    try {
      const detail = await getNote(noteId);
      if (detail.studyPackStatus !== "STUDY_PACK_READY") {
        setNote(detail);
        setError("Generate a Study Pack first.");
        return;
      }
      setNote(detail);
      const authUser = getAuthUser();
      const preferredMode = resolvePreferredChallengeMode(authUser?.profileType);
      const resolvedViewerPlanType = authUser?.planType === "FREE" || authUser?.planType === "PREMIUM"
        ? authUser.planType
        : null;
      setIsEmailVerified(Boolean(authUser?.emailVerifiedAt));
      setViewerPlanType(resolvedViewerPlanType);
      setViewerProfileType(isChallengeViewerProfileType(authUser?.profileType) ? authUser.profileType : null);
      if (!authUser?.emailVerifiedAt) {
        syncProgressRef(0, {});
        setChallengeSession(null);
        setResult(null);
        setSelectedChoices({});
        setCurrentIndex(0);
        setDeadlineEpochSeconds(null);
        setRemainingSeconds(0);
        setTimedOut(false);
        setShowAnswerReview(false);
        setSelectedMode(preferredMode);
        setPrestartStep(resolveInitialPrestartStep());
        setActivePaywallModal(null);
        setPhase("prestart");
        return;
      }

      const inProgress = await getInProgressChallengeQuizSession(detail.id);
      if (sharedModeSelectionEntryRequested) {
        setSelectedDifficulty(normalizePracticeDifficulty(inProgress.selectedDifficulty));
        syncProgressRef(0, {});
        setChallengeSession(null);
        setResult(null);
        setSelectedChoices({});
        setCurrentIndex(0);
        setDeadlineEpochSeconds(null);
        setRemainingSeconds(0);
        setTimedOut(false);
        setShowAnswerReview(false);
        setSelectedMode(preferredMode);
        setPrestartStep(resolveInitialPrestartStep());
        const hasReachedMonthlyLimit = inProgress.usedThisMonth >= inProgress.monthlyLimit;
        const shouldShowLimitPage = hasReachedMonthlyLimit && shouldShowChallengeQuizLimitPage(resolvedViewerPlanType);
        setPhase(shouldShowLimitPage ? "limit-reached" : "prestart");
        if (hasReachedMonthlyLimit) {
          setActivePaywallModal(shouldShowLimitPage ? null : "challenge-quiz-limit");
        } else {
          setActivePaywallModal(null);
        }
        return;
      }

      setSelectedMode(inProgress.mode ?? preferredMode);
      if (inProgress.sessionId) {
        setSelectedDifficulty(normalizePracticeDifficulty(inProgress.selectedDifficulty));
        setActivePaywallModal(null);
        applyStartedSession(inProgress, true);
      } else {
        setSelectedDifficulty(normalizePracticeDifficulty(inProgress.selectedDifficulty));
        syncProgressRef(0, {});
        setChallengeSession(null);
        setResult(null);
        setSelectedChoices({});
        setCurrentIndex(0);
        setDeadlineEpochSeconds(null);
        setRemainingSeconds(0);
        setTimedOut(false);
        setShowAnswerReview(false);
        setSelectedMode(preferredMode);
        setPrestartStep(resolveInitialPrestartStep());
        const hasReachedMonthlyLimit = inProgress.usedThisMonth >= inProgress.monthlyLimit;
        const shouldShowLimitPage = hasReachedMonthlyLimit && shouldShowChallengeQuizLimitPage(resolvedViewerPlanType);
        setPhase(shouldShowLimitPage ? "limit-reached" : "prestart");
        if (hasReachedMonthlyLimit) {
          setActivePaywallModal(shouldShowLimitPage ? null : "challenge-quiz-limit");
        } else {
          setActivePaywallModal(null);
        }
      }
    } catch (err) {
      if (pathname.startsWith("/study-packs/")) {
        const byStudyPack = await getMyStudyPack(noteId).catch(() => null);
        if (byStudyPack?.noteId) {
          const nextQuery = globalThis.window === undefined
            ? ""
            : globalThis.location.search.replace(/^\?/, "");
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
  }, [applyStartedSession, noteId, pathname, router, sharedModeSelectionEntryRequested, syncProgressRef]);

  useEffect(() => {
    void loadNote();
  }, [loadNote]);

  const quiz = useMemo(() => challengeSession?.quiz ?? [], [challengeSession]);
  const totalQuestions = quiz.length;
  const answeredCount = useMemo(() => Object.keys(selectedChoices).length, [selectedChoices]);
  const currentQuestion = totalQuestions > 0 && currentIndex < totalQuestions ? quiz[currentIndex] : null;
  const selectedChoiceIndex = selectedChoices[currentIndex] ?? null;
  const activeMode = challengeSession?.mode ?? selectedMode;
  const isBoardExamMode = activeMode === BOARD_EXAM_MODE;

  useEffect(() => {
    progressRef.current = {
      currentIndex,
      selectedChoices,
    };
  }, [currentIndex, selectedChoices]);

  useEffect(() => {
    remainingSecondsRef.current = remainingSeconds;
  }, [remainingSeconds]);

  const persistLatestProgress = useCallback((keepalive = false) => {
    const latest = progressRef.current;
    persistProgress(latest.currentIndex, latest.selectedChoices, keepalive);
  }, [persistProgress]);

  const finalizeChallengeSession = useCallback(async ({
    timeoutTriggered,
    persistResultToPage,
  }: {
    timeoutTriggered: boolean;
    persistResultToPage: boolean;
  }) => {
    const activeSession = challengeSessionRef.current;
    if (!activeSession?.sessionId || submitInFlightRef.current) {
      return null;
    }

    if (timeoutTriggered) {
      timeoutAutoSubmitRequestedRef.current = true;
    }

    const latestSelectedChoices = progressRef.current.selectedChoices;
    const { correctAnswers, totalQuestions: total } = computeScore(activeSession.quiz, latestSelectedChoices);
    const durationSeconds = Math.max(0, activeSession.timeLimitSeconds - remainingSecondsRef.current);

    submitInFlightRef.current = true;
    setSubmitting(true);
    setError(null);
    if (persistResultToPage) {
      setTimedOut(timeoutTriggered);
    }
    try {
      const completed = await completeChallengeQuizSession(activeSession.sessionId, {
        correctAnswers,
        totalQuestions: total,
        durationSeconds,
      });
      const authUser = getAuthUser();
      if (authUser?.id && getFirstStudyOnboardingStep(authUser.id) === "study-pack-ready") {
        clearFirstStudyOnboardingStep(authUser.id);
        if (persistResultToPage) {
          setShowFirstQuizCompletionBanner(true);
        }
      }
      if (persistResultToPage) {
        setResult(completed);
        setPhase("complete");
      }
      return completed;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not save Challenge Quiz results.";
      setError(message);
      throw err instanceof Error ? err : new Error(message);
    } finally {
      submitInFlightRef.current = false;
      setSubmitting(false);
    }
  }, []);

  const handleSubmit = useCallback(async (timeoutTriggered: boolean) => {
    try {
      await finalizeChallengeSession({
        timeoutTriggered,
        persistResultToPage: true,
      });
    } catch {
      // Submission errors are surfaced through the shared page error state.
    }
  }, [finalizeChallengeSession]);

  useEffect(() => {
    if (phase !== "running" || !challengeSession || submitting || deadlineEpochSeconds === null) {
      return;
    }

    const syncTimerState = () => {
      const nextRemainingSeconds = resolveRemainingSecondsFromDeadline(deadlineEpochSeconds, getNowEpochSeconds());
      remainingSecondsRef.current = nextRemainingSeconds;
      setRemainingSeconds(nextRemainingSeconds);
      if (nextRemainingSeconds <= 0) {
        if (!timeoutAutoSubmitRequestedRef.current) {
          void handleSubmit(true);
        }
      }
    };

    syncTimerState();
    const timer = globalThis.setInterval(syncTimerState, TIMER_TICK_INTERVAL_MS);

    return () => {
      globalThis.clearInterval(timer);
    };
  }, [challengeSession, deadlineEpochSeconds, handleSubmit, phase, submitting]);

  useEffect(() => {
    if (phase !== "generating" || !note) {
      return;
    }

    let isMounted = true;
    const pollGenerationStatus = async () => {
      try {
        const nextSession = await getInProgressChallengeQuizSession(note.id);
        if (!isMounted) {
          return;
        }
        if (nextSession.status === "GENERATING") {
          setChallengeSession(nextSession);
          return;
        }
        applyStartedSession(nextSession, Boolean(nextSession.sessionId));
      } catch (err) {
        if (isMounted) {
          const message = err instanceof Error ? err.message : "Could not load Challenge Quiz generation status.";
          setError(message);
          setPhase("prestart");
        }
      }
    };

    void pollGenerationStatus();
    const intervalId = globalThis.setInterval(() => {
      void pollGenerationStatus();
    }, 2000);

    return () => {
      isMounted = false;
      globalThis.clearInterval(intervalId);
    };
  }, [applyStartedSession, note, phase]);

  useEffect(() => {
    if (phase !== "running" || deadlineEpochSeconds === null) {
      return;
    }

    const syncVisibleTimerState = () => {
      const nextRemainingSeconds = resolveRemainingSecondsFromDeadline(deadlineEpochSeconds, getNowEpochSeconds());
      remainingSecondsRef.current = nextRemainingSeconds;
      setRemainingSeconds(nextRemainingSeconds);
      if (nextRemainingSeconds <= 0 && !submitInFlightRef.current && !timeoutAutoSubmitRequestedRef.current) {
        void handleSubmit(true);
      }
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "hidden") {
        persistLatestProgress(true);
        return;
      }
      syncVisibleTimerState();
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    globalThis.addEventListener("focus", syncVisibleTimerState);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      globalThis.removeEventListener("focus", syncVisibleTimerState);
    };
  }, [deadlineEpochSeconds, handleSubmit, persistLatestProgress, phase]);

  const handleStartChallenge = useCallback(async (modeOverride?: ChallengeQuizMode) => {
    if (!note || startInFlightRef.current) {
      return;
    }
    const nextMode = modeOverride ?? selectedMode;
    if (!isEmailVerified) {
      setError("Verify your email to use this feature.");
      setShowVerifyEmailModal(true);
      return;
    }

    startInFlightRef.current = true;
    setStarting(true);
    setError(null);
    if (nextMode === BOARD_EXAM_MODE) {
      void requestBoardExamFullscreen();
    }
    try {
      const request: ChallengeQuizStartRequest = nextMode === CHALLENGE_MODE && note.difficultySelectionAvailable
        ? { difficulty: selectedDifficulty, mode: nextMode }
        : { mode: nextMode };
      const started = await startChallengeQuizSession(note.id, request);
      if (!started.sessionId) {
        throw new Error(nextMode === BOARD_EXAM_MODE ? "Could not start Board Exam Mode." : "Could not start Challenge Quiz.");
      }
      setSelectedDifficulty(normalizePracticeDifficulty(started.selectedDifficulty));
      applyStartedSession(started, true);
    } catch (err) {
      const message = isEmailNotVerifiedError(err)
        ? "Verify your email to use this feature."
        : err instanceof Error
          ? err.message
          : nextMode === BOARD_EXAM_MODE
            ? "Could not start Board Exam Mode."
            : "Could not start Challenge Quiz.";
      if (isEmailNotVerifiedError(err)) {
        setShowVerifyEmailModal(true);
      }
      setError(message);
      if (message.toLowerCase().includes("monthly challenge quiz limit")) {
        if (shouldShowChallengeQuizLimitPage(viewerPlanType)) {
          setActivePaywallModal(null);
          setPhase("limit-reached");
        } else {
          setPhase("prestart");
          openLockedFeaturePaywall("challenge-quiz-limit", "challenge_quiz_start");
        }
      }
    } finally {
      startInFlightRef.current = false;
      setStarting(false);
    }
  }, [applyStartedSession, isEmailVerified, note, openLockedFeaturePaywall, selectedDifficulty, selectedMode]);

  const handleRetry = () => {
    timeoutAutoSubmitRequestedRef.current = false;
    syncProgressRef(0, {});
    setChallengeSession(null);
    setResult(null);
    setSelectedChoices({});
    setCurrentIndex(0);
    setDeadlineEpochSeconds(null);
    setRemainingSeconds(0);
    setTimedOut(false);
    setError(null);
    setShowAnswerReview(false);
    setShowBoardExamStartModal(false);
    setPrestartStep(resolveRecoveryPrestartStep(challengeSession?.mode ?? selectedMode));
    setPhase("prestart");
  };

  const challengeGenerationLocked = starting || phase === "generating";
  const challengeQuizActive = phase === "running" && Boolean(challengeSession?.sessionId);
  const boardExamTimerExpired = isBoardExamMode && remainingSeconds <= 0;
  const quizInteractionDisabled = submitting || boardExamTimerExpired;
  const quizModeLabel = isBoardExamMode ? "Board Exam Mode" : "Challenge Quiz";
  const quizResultLabel = isBoardExamMode ? "Exam Result" : "Challenge Quiz Result";
  const submitButtonLabel = isBoardExamMode ? "Submit Exam" : "Submit Challenge Quiz";
  const retryButtonLabel = isBoardExamMode ? "Take Another Board Exam" : "Start Another Challenge";
  const generationOverlayTitle = isBoardExamMode ? "Preparing your board exam..." : "Generating your quiz...";
  const generationOverlayMessage = isBoardExamMode
    ? "Creating a stricter exam simulation from your notes"
    : "Creating personalized questions from your notes";
  const questionCountSummary = getQuestionCountSummary(note?.difficultySelectionAvailable, selectedDifficulty);
  const canChooseChallengeDifficulty = Boolean(note?.difficultySelectionAvailable);
  const boardExamAvailable = viewerPlanType === "PREMIUM";
  const prefersBoardExam = viewerProfileType === "BOARD_EXAM";
  const boardExamTimerState = useMemo(
    () => resolveBoardExamTimerState(remainingSeconds),
    [remainingSeconds],
  );
  const boardExamTimerDescription = getBoardExamTimerDescription(boardExamTimerState);
  const questionNavigatorSummary = `Question Navigator · ${Math.min(currentIndex + 1, totalQuestions)} of ${totalQuestions} · ${answeredCount} answered`;
  const boardExamFocusTipStorageKey = useMemo(
    () => (viewerId ? `${BOARD_EXAM_TOOLTIP_STORAGE_KEY_PREFIX}:${viewerId}` : null),
    [viewerId],
  );
  const handleSelectChallengeQuizMode = useCallback(() => {
    setSelectedMode(CHALLENGE_MODE);
    setError(null);
    setPrestartStep("challenge-setup");
  }, []);
  const handleSelectBoardExamMode = useCallback(() => {
    setSelectedMode(BOARD_EXAM_MODE);
    setError(null);
    if (!boardExamAvailable) {
      openLockedFeaturePaywall("board-exam-mode", "board_exam_mode_selection");
      return;
    }
    setPrestartStep("board-exam-setup");
  }, [boardExamAvailable, openLockedFeaturePaywall]);
  const returnToModeSelection = useCallback(() => {
    setError(null);
    setShowBoardExamStartModal(false);
    setPrestartStep("mode-selection");
    setSelectedMode(prefersBoardExam ? BOARD_EXAM_MODE : CHALLENGE_MODE);
  }, [prefersBoardExam]);
  const { requestLeave, LeaveQuizModal } = useQuizSessionGuard({
    active: challengeQuizActive,
    fallbackHref: noteDetailHref,
    onBeforeRouteLeave: () => persistLatestProgress(true),
    onConfirmLeave: async () => {
      if (!challengeSession?.sessionId) {
        return;
      }
      if (submitInFlightRef.current) {
        throw new Error("Challenge Quiz submission is already in progress.");
      }
      persistLatestProgress(true);
      if (activeMode === BOARD_EXAM_MODE) {
        await finalizeChallengeSession({
          timeoutTriggered: false,
          persistResultToPage: false,
        });
        return;
      }
      await forfeitChallengeQuizSession(challengeSession.sessionId);
    },
    dialogTitle: isBoardExamMode ? BOARD_EXAM_LEAVE_TITLE : undefined,
    dialogDescription: isBoardExamMode ? BOARD_EXAM_LEAVE_DESCRIPTION : undefined,
    confirmLabel: isBoardExamMode ? "Submit & Leave" : undefined,
    confirmLoadingLabel: isBoardExamMode ? "Submitting..." : undefined,
    leaveErrorMessage: isBoardExamMode ? BOARD_EXAM_LEAVE_ERROR : undefined,
    beforeUnloadMessage: isBoardExamMode ? BOARD_EXAM_BEFORE_UNLOAD_MESSAGE : undefined,
  });
  const { LeaveQuizModal: GenerationLockModal } = useQuizSessionGuard({
    active: challengeGenerationLocked,
    fallbackHref: noteDetailHref,
    onConfirmLeave: () => undefined,
    blockWithoutConfirmation: true,
  });

  useEffect(() => {
    if (!isBoardExamMode || phase !== "running" || !boardExamFocusTipStorageKey) {
      setShowBoardExamFocusTip(false);
      return;
    }
    setShowBoardExamFocusTip(globalThis.localStorage.getItem(boardExamFocusTipStorageKey) !== "dismissed");
  }, [boardExamFocusTipStorageKey, isBoardExamMode, phase]);

  const dismissBoardExamFocusTip = useCallback(() => {
    if (boardExamFocusTipStorageKey) {
      globalThis.localStorage.setItem(boardExamFocusTipStorageKey, "dismissed");
    }
    setShowBoardExamFocusTip(false);
  }, [boardExamFocusTipStorageKey]);

  useEffect(() => {
    if (phase !== "running" || !challengeSession?.sessionId || totalQuestions === 0) {
      return;
    }
    setIsQuestionNavigatorCollapsed(
      shouldCollapseQuestionNavigatorByDefault(activeMode, isMobileNavigatorViewport),
    );
  }, [activeMode, challengeSession?.sessionId, isMobileNavigatorViewport, phase, totalQuestions]);

  const isNotFound = error?.toLowerCase().includes("not found") ?? false;

  return (
    <main className={cn(
      "mx-auto w-full max-w-3xl space-y-4 px-4 py-6 sm:px-6 sm:py-10",
      phase === "running" && "pb-28 sm:pb-10",
    )}>
      {phase === "running" ? (
        <div
          data-testid="challenge-quiz-top-bar"
          className={cn(
            "sticky top-16 z-20 -mx-4 flex items-center gap-3 border-b bg-background/95 px-4 py-3 backdrop-blur sm:mx-0 sm:rounded-xl sm:border",
            isBoardExamMode ? "border-foreground/15" : "border-border",
          )}
        >
          <Button type="button" variant="outline" size="sm" className="shrink-0 px-3" onClick={() => requestLeave()} disabled={submitting}>
            {isBoardExamMode ? "Leave Exam" : "Leave Quiz"}
          </Button>
          <div className="min-w-0 flex-1 text-center">
            <p className={cn(
              "truncate text-sm font-semibold",
              isBoardExamMode ? "text-foreground" : "text-blue-700 dark:text-blue-300",
            )}>
              {quizModeLabel}
            </p>
          </div>
          <div
            data-testid={isBoardExamMode ? "board-exam-timer" : "challenge-quiz-timer"}
            data-timer-state={isBoardExamMode ? boardExamTimerState : undefined}
            className={cn(
              "shrink-0 rounded-full border px-3 py-1 text-sm font-semibold",
              isBoardExamMode
                ? boardExamTimerState === "normal"
                  ? "border-foreground/15 bg-background text-foreground"
                  : boardExamTimerState === "warning"
                    ? "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300"
                    : "border-red-500/45 bg-red-500/10 text-red-700 dark:text-red-300"
                : "border-border bg-background text-foreground",
            )}
            aria-label="Exam timer"
          >
            {formatTimer(remainingSeconds)}
          </div>
        </div>
      ) : (
        <div className="flex items-center justify-between gap-3">
          <BackLink href={noteDetailHref} label="Note" />
        </div>
      )}

      {challengeGenerationLocked ? (
        <QuizGenerationOverlay
          title={generationOverlayTitle}
          message={generationOverlayMessage}
        />
      ) : null}

      {loading ? (
        <ChallengeQuizLoading />
      ) : error && !note ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">
            {isNotFound ? "Note not found" : `Could not load ${quizModeLabel}`}
          </h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            {!isNotFound ? (
              <Button type="button" className="w-full sm:w-auto" onClick={() => void loadNote()}>
                Retry
              </Button>
            ) : null}
          </div>
        </Card>
      ) : phase === "limit-reached" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Challenge Quiz
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">You’ve reached your quiz limit for this month</h1>
          <p className="text-sm text-foreground/75">Your quiz limit resets on your next billing cycle.</p>
          <BackLink href={noteDetailHref} label="Note" />
        </Card>
      ) : phase === "generating" ? (
        <ChallengeQuizLoading />
      ) : phase === "prestart" ? (
        prestartStep === "mode-selection" ? (
          <Card className="space-y-4 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Challenge Quiz
            </p>
            <h1 className="text-xl font-semibold sm:text-2xl">Choose your quiz mode</h1>
            <p className="text-sm text-foreground/80">
              {prefersBoardExam
                ? `Choose how you want to prepare with ${note?.title ?? "this note"}. Board Exam Mode emphasizes exam simulation, while Challenge Quiz stays flexible for regular practice.`
                : `Choose how you want to practice with ${note?.title ?? "this note"}. Challenge Quiz stays flexible, while Board Exam Mode uses a stricter exam-style flow.`}
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
              <button
                type="button"
                aria-pressed={selectedMode === CHALLENGE_MODE}
                className={cn(
                  "rounded-xl border bg-background p-4 text-left transition hover:border-blue-400/50 hover:bg-blue-500/[0.04]",
                  selectedMode === CHALLENGE_MODE
                    ? "border-blue-500 bg-blue-500/[0.06] shadow-sm"
                    : "border-border",
                )}
                onClick={() => void handleSelectChallengeQuizMode()}
                disabled={challengeGenerationLocked}
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm font-semibold text-foreground">Challenge Quiz</p>
                  {selectedMode === CHALLENGE_MODE ? (
                    <span className="rounded-full border border-blue-500/30 bg-blue-500/10 px-2 py-0.5 text-[11px] font-medium text-blue-700 dark:text-blue-300">
                      {prefersBoardExam ? "Alternate" : "Recommended"}
                    </span>
                  ) : null}
                </div>
                <p className="mt-1 text-sm text-foreground/70">
                  {prefersBoardExam
                    ? "Flexible timed practice when you want a lighter setup before full exam simulation."
                    : "Flexible quiz mode with optional difficulty selection."}
                </p>
                <p className="mt-3 text-xs text-foreground/60">
                  {canChooseChallengeDifficulty
                    ? "Premium users can choose the challenge difficulty before generation."
                    : "Review the recommended setup before you start."}
                </p>
              </button>
              <button
                type="button"
                aria-pressed={selectedMode === BOARD_EXAM_MODE}
                className={cn(
                  "rounded-xl border bg-background p-4 text-left transition hover:border-foreground/30 hover:bg-foreground/[0.03]",
                  selectedMode === BOARD_EXAM_MODE
                    ? "border-foreground/35 bg-foreground/[0.03] shadow-sm"
                    : "border-border",
                )}
                onClick={() => handleSelectBoardExamMode()}
                disabled={challengeGenerationLocked}
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm font-semibold text-foreground">Board Exam Mode</p>
                  {selectedMode === BOARD_EXAM_MODE ? (
                    <span className="rounded-full border border-foreground/20 bg-foreground/[0.06] px-2 py-0.5 text-[11px] font-medium text-foreground/80">
                      {prefersBoardExam ? "Recommended" : "Alternate"}
                    </span>
                  ) : null}
                </div>
                <p className="mt-1 text-sm text-foreground/70">
                  {prefersBoardExam
                    ? "Simulate a real exam with mixed difficulty and a stricter, more controlled flow."
                    : "Simulate a real exam with mixed difficulty and a stricter, more controlled flow."}
                </p>
                <p className="mt-3 text-xs text-foreground/60">
                  {boardExamAvailable
                    ? "Counts toward your monthly quiz limit, the same as the standard Challenge Quiz flow."
                    : "Premium only. Upgrade to unlock a stricter board-style exam flow."}
                </p>
              </button>
            </div>
            <p className="text-xs text-foreground/60">
              Both modes count toward your monthly quiz limit.
            </p>
            {challengeGenerationLocked ? (
              <p className="text-sm text-foreground/75">Preparing your {quizModeLabel.toLowerCase()}...</p>
            ) : null}
            {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
          </Card>
        ) : prestartStep === "challenge-setup" ? (
          <Card className="space-y-4 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Challenge Quiz
            </p>
            <h1 className="text-xl font-semibold sm:text-2xl">Challenge Quiz Setup</h1>
            <p className="text-sm text-foreground/80">
              Review the quiz setup for {note?.title ?? "this note"} before you begin.
            </p>
            <div className="space-y-3 rounded-xl border border-border bg-background p-4 text-sm text-foreground/80">
              <div className="space-y-1">
                <p className="font-medium text-foreground">Difficulty</p>
                {canChooseChallengeDifficulty ? (
                  <p>Premium lets you choose the level before you start.</p>
                ) : (
                  <>
                    <p>Recommended difficulty: Medium</p>
                    <p className="text-foreground/65">Choose difficulty (Premium)</p>
                  </>
                )}
              </div>
              {canChooseChallengeDifficulty ? (
                <div className="grid gap-2 sm:grid-cols-3">
                  {(["easy", "medium", "hard"] as const).map((difficulty) => (
                    <button
                      key={difficulty}
                      type="button"
                      className={cn(
                        "rounded-md border px-3 py-2 text-left capitalize transition",
                        selectedDifficulty === difficulty
                          ? "border-blue-500 bg-blue-500/10 text-foreground"
                          : "border-border bg-background",
                      )}
                      onClick={() => {
                        if (!starting) {
                          setSelectedDifficulty(difficulty);
                        }
                      }}
                      disabled={challengeGenerationLocked}
                    >
                      {difficulty}
                    </button>
                  ))}
                </div>
              ) : null}
              <div className="grid gap-3 border-t border-border pt-3 sm:grid-cols-3">
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Timer</p>
                  <p>10 minutes. Timer runs until submission or expiration.</p>
                </div>
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Question count</p>
                  <p>{canChooseChallengeDifficulty ? questionCountSummary : "Recommended based on your recent performance."}</p>
                </div>
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Monthly limit</p>
                  <p>Counts toward your monthly quiz limit.</p>
                </div>
              </div>
            </div>
            {challengeGenerationLocked ? (
              <p className="text-sm text-foreground/75">Preparing your Challenge Quiz...</p>
            ) : null}
            {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button
                type="button"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={returnToModeSelection}
                disabled={challengeGenerationLocked}
              >
                Choose another mode
              </Button>
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleStartChallenge(CHALLENGE_MODE)}
                disabled={challengeGenerationLocked}
              >
                {challengeGenerationLocked ? "Starting..." : "Start Quiz"}
              </Button>
            </div>
          </Card>
        ) : (
          <Card className="space-y-4 border-foreground/15 bg-muted/20 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/70">
              Board Exam Mode
            </p>
            <h1 className="text-xl font-semibold sm:text-2xl">Board Exam Setup</h1>
            <p className="text-sm text-foreground/80">
              Review the exam setup for {note?.title ?? "this note"} before you begin.
            </p>

            <div className="space-y-3 rounded-xl border border-foreground/15 bg-background/80 p-4 text-sm text-foreground/80">
              <div className="space-y-2">
                <h2 className="text-base font-semibold text-foreground">Description</h2>
                <p>Simulate a focused exam session with mixed difficulty.</p>
                {!boardExamAvailable ? (
                  <p className="text-amber-700 dark:text-amber-300">Premium required to start Board Exam Mode.</p>
                ) : null}
              </div>
            </div>

            <div className="space-y-3 rounded-xl border border-foreground/15 bg-background/80 p-4 text-sm text-foreground/80">
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Timer</p>
                  <p>Strict timed session.</p>
                </div>
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Rules</p>
                  <ul className="list-disc space-y-1 pl-5">
                    <li>No navigation during exam</li>
                    <li>Results shown after completion</li>
                    <li>Leaving counts as submission</li>
                  </ul>
                </div>
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Monthly limit</p>
                  <p>{boardExamAvailable ? "Counts toward your monthly quiz limit." : "Unlock with Premium. Challenge Quiz stays available on your current plan."}</p>
                </div>
              </div>
            </div>

            {challengeGenerationLocked ? (
              <p className="text-sm text-foreground/75">Preparing your board exam...</p>
            ) : null}
            {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button
                type="button"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={returnToModeSelection}
                disabled={challengeGenerationLocked}
              >
                Choose another mode
              </Button>
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => {
                  if (!boardExamAvailable) {
                    openLockedFeaturePaywall("board-exam-mode", "board_exam_setup");
                    return;
                  }
                  setShowBoardExamStartModal(true);
                }}
                disabled={challengeGenerationLocked}
              >
                {boardExamAvailable
                  ? challengeGenerationLocked ? "Starting..." : "Start Exam"
                  : "Unlock Board Exam Mode"}
              </Button>
            </div>
          </Card>
        )
      ) : phase === "running" && challengeSession ? (
        <div className="space-y-4">
          {isBoardExamMode && showBoardExamFocusTip ? (
            <div className="flex flex-col gap-3 rounded-xl border border-foreground/15 bg-muted/20 p-4 text-sm text-foreground/80 sm:flex-row sm:items-center sm:justify-between">
              <p>{BOARD_EXAM_FOCUS_TIP}</p>
              <Button type="button" variant="outline" size="sm" className="w-full sm:w-auto" onClick={dismissBoardExamFocusTip}>
                Got it
              </Button>
            </div>
          ) : null}
          <Card className={cn("space-y-4 p-4 sm:p-5", isBoardExamMode ? "border-foreground/15 bg-card" : "")}>
            {currentQuestion ? (
              <div className="space-y-4">
                <div className="space-y-1">
                  <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">
                    Question {Math.min(currentIndex + 1, totalQuestions)} of {totalQuestions}
                  </p>
                  {isBoardExamMode && boardExamTimerDescription ? (
                    <p className={cn(
                      "text-xs font-medium",
                      boardExamTimerState === "warning"
                        ? "text-amber-700 dark:text-amber-300"
                        : boardExamTimerState === "urgent" || boardExamTimerState === "expired"
                          ? "text-red-700 dark:text-red-300"
                          : "text-foreground/65",
                    )}>
                      {boardExamTimerDescription}
                    </p>
                  ) : null}
                </div>
                <h2 className="text-lg font-semibold leading-7 sm:text-xl">{currentQuestion.question}</h2>
                <QuizChoiceList
                  questionKey={currentQuestion.question}
                  choices={currentQuestion.choices}
                  correctIndex={resolveQuizCorrectIndex(currentQuestion)}
                  selectedChoiceIndex={selectedChoiceIndex}
                  revealAnswer={false}
                  disabled={quizInteractionDisabled}
                  selectionStyle={isBoardExamMode ? "board-exam" : "exam"}
                  onSelectChoice={(choiceIndex) => {
                    setSelectedChoices((previous) => {
                      const next = { ...previous, [currentIndex]: choiceIndex };
                      syncProgressRef(currentIndex, next);
                      persistProgress(currentIndex, next);
                      return next;
                    });
                  }}
                />
                <p className="text-xs text-foreground/65">Answers are graded only after submission.</p>
                <div className={cn(
                  "rounded-md border p-3",
                  isBoardExamMode ? "border-foreground/15 bg-muted/10" : "border-border bg-background",
                )}>
                  <button
                    type="button"
                    className="motion-pressable flex w-full items-center justify-between gap-3 rounded-md text-left"
                    onClick={() => setIsQuestionNavigatorCollapsed((current) => !current)}
                    aria-expanded={!isQuestionNavigatorCollapsed}
                    aria-controls="challenge-question-navigator-grid"
                  >
                    <div className="space-y-1">
                      <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Question Navigator</p>
                      <p className="text-sm text-foreground/75">{questionNavigatorSummary}</p>
                    </div>
                    <ChevronDown
                      className={cn(
                        "h-4 w-4 shrink-0 text-foreground/65 transition-transform",
                        !isQuestionNavigatorCollapsed && "rotate-180",
                      )}
                      aria-hidden="true"
                    />
                  </button>
                  <div
                    id="challenge-question-navigator-grid"
                    data-testid="challenge-question-navigator-disclosure"
                    className="motion-collapse mt-3"
                    data-state={isQuestionNavigatorCollapsed ? "collapsed" : "expanded"}
                    aria-hidden={isQuestionNavigatorCollapsed}
                  >
                    <div className="motion-collapse-inner">
                      <div className="grid grid-cols-5 gap-2 sm:grid-cols-8" aria-label="Question navigator">
                        {quiz.map((item, index) => {
                          const isCurrentQuestion = index === currentIndex;
                          const isAnswered = selectedChoices[index] != null;
                          return (
                            <button
                              key={`${item.question}-${index}`}
                              type="button"
                              tabIndex={isQuestionNavigatorCollapsed ? -1 : 0}
                              aria-label={`Go to question ${index + 1}${isAnswered ? " (answered)" : " (unanswered)"}`}
                              className={cn(
                                "motion-pressable rounded-md border px-2 py-1.5 text-sm font-medium",
                                isCurrentQuestion
                                  ? isBoardExamMode
                                    ? "border-foreground/40 bg-foreground/[0.06] text-foreground"
                                    : "border-blue-500 bg-blue-500/10 text-blue-700 dark:text-blue-300"
                                  : isAnswered
                                    ? "border-foreground/30 bg-muted/60 text-foreground"
                                    : "border-border bg-background text-foreground/70",
                              )}
                              onClick={() => {
                                syncProgressRef(index, selectedChoices);
                                setCurrentIndex(index);
                                persistProgress(index, selectedChoices);
                              }}
                              disabled={quizInteractionDisabled}
                            >
                              {index + 1}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ) : null}
            {timedOut && submitting && isBoardExamMode ? (
              <div className="rounded-md border border-foreground/15 bg-muted/20 px-3 py-2 text-sm text-foreground/80">
                Time&apos;s up. Submitting your exam...
              </div>
            ) : null}
            {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
          </Card>
          <div
            data-testid="challenge-quiz-action-bar"
            className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-background/95 px-4 py-3 backdrop-blur sm:static sm:border-0 sm:bg-transparent sm:px-0 sm:py-0"
          >
            <div className="mx-auto flex w-full max-w-3xl gap-2 sm:justify-start">
              <Button
                type="button"
                variant="outline"
                className="w-28 shrink-0 sm:w-auto"
                onClick={() => {
                  const nextIndex = Math.max(0, currentIndex - 1);
                  syncProgressRef(nextIndex, selectedChoices);
                  setCurrentIndex(nextIndex);
                  persistProgress(nextIndex, selectedChoices);
                }}
                disabled={currentIndex <= 0 || quizInteractionDisabled}
              >
                Previous
              </Button>
              {currentIndex < totalQuestions - 1 && !boardExamTimerExpired ? (
                <Button
                  type="button"
                  className="flex-1 sm:w-auto sm:flex-none"
                  onClick={() => {
                    const nextIndex = Math.min(totalQuestions - 1, currentIndex + 1);
                    syncProgressRef(nextIndex, selectedChoices);
                    setCurrentIndex(nextIndex);
                    persistProgress(nextIndex, selectedChoices);
                  }}
                  disabled={quizInteractionDisabled}
                >
                  Next
                </Button>
              ) : (
                <Button
                  type="button"
                  className="flex-1 sm:w-auto sm:flex-none"
                  onClick={() => void handleSubmit(false)}
                  disabled={submitting}
                >
                  {submitting ? "Submitting..." : submitButtonLabel}
                </Button>
              )}
            </div>
          </div>
        </div>
      ) : phase === "complete" && result ? (
        <Card className={cn("motion-fade-enter space-y-4 p-4 sm:p-6", isBoardExamMode ? "border-foreground/15 bg-card" : "")}>
          <p className={cn(
            "text-xs font-semibold uppercase tracking-wide",
            isBoardExamMode ? "text-foreground/70" : "text-blue-600 dark:text-blue-400",
          )}>
            {isBoardExamMode ? "Board Exam Mode" : quizResultLabel}
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">
            {isBoardExamMode ? "Exam Result" : (note?.title ?? quizModeLabel)}
          </h1>
          <p className="text-sm text-foreground/75">
            {isBoardExamMode
              ? "Your board exam simulation is complete. Review your performance first, then use the follow-up actions to recover weak areas."
              : "Your Challenge Quiz is complete. Review the result summary first, then choose the next study action."}
          </p>
          {showFirstQuizCompletionBanner ? (
            <Card className="space-y-3 border-emerald-500/30 bg-emerald-500/5 p-4">
              <div className="space-y-1">
                <h2 className="text-lg font-semibold">Great job! Keep studying and improve your weak areas.</h2>
                <p className="text-sm text-foreground/80">
                  Review the concepts below to see what needs more attention next.
                </p>
              </div>
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => {
                  setShowFirstQuizCompletionBanner(false);
                  weakConceptsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
                }}
              >
                View Weak Concepts
              </Button>
            </Card>
          ) : null}
          <div className={cn(
            "rounded-md border bg-background p-4",
            isBoardExamMode ? "border-foreground/15" : "border-border",
          )}>
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
            <p className="mt-2 text-sm text-foreground/75">{getChallengeResultMessage(result.scorePercentage, activeMode)}</p>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Performance</span>
              <div className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium ${getPerformanceBadgeClass(result.performanceLevel)}`}>
                {result.performanceLevel}
              </div>
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
          <div ref={weakConceptsRef}>
            <Card className="space-y-3 p-4">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Weak Concepts</h2>
              {result.weakConcepts.length > 0 ? (
                <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/85">
                  {result.weakConcepts.map((concept) => (
                    <li key={concept}>{concept}</li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-foreground/70">
                  {isBoardExamMode
                    ? "No weak concepts were identified in this exam. Review your answers or take another Board Exam when ready."
                    : "No weak concepts identified in this challenge. Review your answers or start another challenge when ready."}
                </p>
              )}
            </Card>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            {result.weakConcepts.length > 0 ? (
              <Link href={note ? `/notes/${note.id}/adaptive-practice` : "/dashboard"} className="w-full sm:w-auto">
                <Button type="button" className="w-full sm:w-auto">
                  Practice Weak Concepts
                </Button>
              </Link>
            ) : null}
            <Button
              type="button"
              variant={result.weakConcepts.length > 0 ? "outline" : "default"}
              className="w-full sm:w-auto"
              onClick={handleRetry}
            >
              {retryButtonLabel}
            </Button>
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShowAnswerReview((previous) => !previous)}>
              {showAnswerReview ? "Hide Answer Review" : "Review Answers"}
            </Button>
          </div>
          <div className="pt-1">
            <BackLink href={noteDetailHref} label="Back to Note" />
          </div>
          {showAnswerReview ? (
            <QuizAnswerReview
              quiz={quiz}
              selectedChoices={selectedChoices}
              className="mt-2"
              footer={(
                <div className="flex flex-col gap-2 sm:flex-row">
                  {result.weakConcepts.length > 0 && note?.adaptivePracticeAvailable ? (
                    <Link href={`/notes/${note.id}/adaptive-practice`} className="w-full sm:w-auto">
                      <Button type="button" className="w-full sm:w-auto">
                        Practice Weak Concepts
                      </Button>
                    </Link>
                  ) : null}
                  <Link href={noteDetailHref} className="w-full sm:w-auto">
                    <Button
                      type="button"
                      variant={result.weakConcepts.length > 0 && note?.adaptivePracticeAvailable ? "outline" : "default"}
                      className="w-full sm:w-auto"
                    >
                      Review Study Pack
                    </Button>
                  </Link>
                </div>
              )}
            />
          ) : null}
          <QuizFeedbackPanel
            quizLabel={isBoardExamMode ? "Board Exam Mode" : "Challenge Quiz"}
            noteTitle={note?.title}
            section={showAnswerReview ? "review" : "results"}
          />
        </Card>
      ) : null}

      <AppModal
        isOpen={showBoardExamStartModal}
        title={BOARD_EXAM_START_CONFIRM_TITLE}
        onClose={() => {
          if (!challengeGenerationLocked) {
            setShowBoardExamStartModal(false);
          }
        }}
        contentClassName="space-y-2"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowBoardExamStartModal(false)}
              disabled={challengeGenerationLocked}
            >
              Cancel
            </Button>
            <Button
              type="button"
              onClick={() => {
                setShowBoardExamStartModal(false);
                void handleStartChallenge(BOARD_EXAM_MODE);
              }}
              disabled={challengeGenerationLocked}
            >
              {challengeGenerationLocked ? "Starting..." : "Start Exam"}
            </Button>
          </div>
        )}
      >
        <p className="text-sm leading-relaxed text-foreground/80">
          You are about to start a board exam simulation.
        </p>
        <p className="text-sm leading-relaxed text-foreground/80">
          You will not see results until the end, and navigation will be limited during the exam.
        </p>
      </AppModal>
      <PaywallModal
        isOpen={activePaywallModal !== null}
        variant={activePaywallModal ?? "challenge-quiz-limit"}
        source="challenge_quiz_page"
        onClose={() => setActivePaywallModal(null)}
      />
      <VerifyEmailRequiredModal
        isOpen={showVerifyEmailModal}
        onClose={() => setShowVerifyEmailModal(false)}
      />
      <LeaveQuizModal />
      <GenerationLockModal />
    </main>
  );
}
