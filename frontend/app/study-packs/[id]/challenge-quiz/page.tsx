"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { EyeOff, Hourglass, ListChecks, Maximize2 } from "lucide-react";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { useAppShellTitleOverride } from "@/components/app-shell-title-context";
import { ExamTopBar } from "@/components/exam-mode/exam-top-bar";
import { useExamFocusMode } from "@/components/exam-mode/exam-focus-context";
import { QuestionNavigator } from "@/components/exam-mode/question-navigator";
import { ScoreReveal } from "@/components/exam-mode/score-reveal";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { PostSuccessUpgradeNudge } from "@/components/billing/post-success-upgrade-nudge";
import { QuizFeedbackPanel } from "@/components/feedback/quiz-feedback-panel";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { BackLink } from "@/components/ui/back-link";
import { AppModal } from "@/components/ui/app-modal";
import { QuizAnswerReview } from "@/components/study-pack/quiz-answer-review";
import { GoalNudgeCard } from "@/components/study-pack/goal-nudge-card";
import { PostSessionNextStep } from "@/components/study-pack/post-session-next-step";
import { StickyAssessmentFooter } from "@/components/ui/sticky-assessment-footer";
import { QuizGenerationOverlay } from "@/components/study-pack/quiz-generation-overlay";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { QuizMatchingGroup } from "@/components/study-pack/quiz-matching-group";
import { QuizQuestionText } from "@/components/study-pack/quiz-question-text";
import { useQuizSessionGuard } from "@/components/study-pack/quiz-session-guard";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { getAuthUser } from "@/lib/auth";
import { getCollectionLabels } from "@/lib/collection-labels";
import { clearFirstStudyOnboardingStep, getFirstStudyOnboardingStep } from "@/lib/first-study-onboarding";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  type ChallengeQuizStartRequest,
  type ChallengeQuizMode,
  completeChallengeQuizSession,
  forfeitChallengeQuizSession,
  generateMoreChallengeQuizQuestions,
  getCollection,
  getInProgressChallengeQuizSession,
  getMe,
  getMyStudyPack,
  getNote,
  getPostSessionNextStep,
  listNotes,
  isEmailNotVerifiedError,
  isNotEnoughNewQuestionsError,
  startChallengeQuizSession,
  trackAnalyticsEvent,
  updateChallengeQuizSessionProgress,
  updateProfileLearnerLevel,
  type LearnerLevel,
  type NoteListItemResponse,
  type NoteResponse,
  type PostSessionNextStepResponse,
  type ChallengeQuizSessionResponse,
  type ChallengeQuizStartResponse,
} from "@/lib/api";
import { getGroupedLearnerLevels } from "@/lib/learning-profile";
import { ToastMessage } from "@/components/ui/toast-message";
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
  resolveQuizItemGroupAt,
  serializeSelectedChoiceIndexRecord,
  serializeSelectedMultiChoiceIndicesRecord,
  toSelectedChoiceIndexRecord,
  toSelectedMultiChoiceIndicesRecord,
} from "@/lib/quiz";
import {
  CHALLENGE_QUIZ_ENTRY_QUERY_PARAM,
  isModeSelectionChallengeQuizEntry,
} from "@/lib/challenge-quiz-entry";
import { resolveCollectionScopedSourceNotes } from "@/lib/collection-exam";
import { getAvailableExamModes } from "@/lib/exam-mode-visibility";
import { cn } from "@/lib/utils";
import { getSelectionCardClassName } from "@/lib/clickable-card";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

type ChallengePhase = "prestart" | "generating" | "running" | "complete" | "limit-reached";
type ChallengePrestartStep = "mode-selection" | "challenge-setup" | "board-exam-setup";
type ChallengeSessionStatePayload = {
  selectedChoices?: Record<string, number> | Record<string, string>;
  selectedMultiChoices?: Record<string, number[]>;
  timerStartedAtEpochSeconds?: number;
};
type ChallengeDifficulty = NonNullable<ChallengeQuizStartRequest["difficulty"]>;
type ChallengeViewerProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PROFESSIONAL" | null;
type ChallengeViewerPlanType = "FREE" | "PLUS" | "PRO" | null;
type ChallengePaywallVariant =
  | "board-exam-mode"
  | "board-exam-limit"
  | "long-exam-mode"
  | "interview-practice-limit"
  | "difficulty-selection"
  | "challenge-quiz-limit"
  | "adaptive-practice";

const CHALLENGE_MODE: ChallengeQuizMode = "challenge";
const BOARD_EXAM_MODE: ChallengeQuizMode = "board_exam";
const MAX_SESSION_QUESTIONS = 20;
const TIMER_TICK_INTERVAL_MS = 1000;
const BOARD_EXAM_TOOLTIP_STORAGE_KEY_PREFIX = "notelib-board-exam-mode-tip-dismissed";
const BOARD_EXAM_START_CONFIRM_TITLE = "Start Board Exam Mode?";
const BOARD_EXAM_LEAVE_TITLE = "Leave exam?";
const BOARD_EXAM_LEAVE_DESCRIPTION = "Your progress will be submitted and counted as complete.";
const BOARD_EXAM_LEAVE_ERROR = "Could not submit and leave. Please try again.";
const BOARD_EXAM_BEFORE_UNLOAD_MESSAGE = "You are currently in Board Exam Mode. Leaving will submit your current answers and end the exam.";
const BOARD_EXAM_FOCUS_TIP = "Board Exam Mode hides distractions to simulate a real test environment.";
const BOARD_EXAM_MAX_ADDITIONAL_NOTES = 2;
const BOARD_EXAM_MULTI_NOTE_EMPTY_HINT = "Create another note with the same subject to unlock multi-note exam mode";
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


function getQuestionCountSummary(
  difficultySelectionAvailable: boolean | undefined,
): string {
  if (difficultySelectionAvailable) {
    return "Starts with 5 questions. Generate more after answering.";
  }
  return "Starts with 5 questions. Generate more after answering.";
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

function resolveInitialPrestartStep(profileType?: string | null): ChallengePrestartStep {
  if (profileType === "TEACHER") return "challenge-setup";
  return "mode-selection";
}

function shouldShowChallengeQuizLimitPage(planType: ChallengeViewerPlanType): boolean {
  return planType === "PRO";
}

function resolvePreferredChallengeMode(profileType: string | null | undefined): ChallengeQuizMode {
  return profileType === "BOARD_EXAM" ? BOARD_EXAM_MODE : CHALLENGE_MODE;
}

function isChallengeViewerProfileType(value: string | null | undefined): value is Exclude<ChallengeViewerProfileType, null> {
  return value === "STUDENT" || value === "BOARD_EXAM" || value === "TEACHER" || value === "PROFESSIONAL";
}

async function requestBoardExamFullscreen() {
  const element = globalThis.document?.documentElement;
  if (!element || typeof element.requestFullscreen !== "function" || globalThis.document.fullscreenElement) {
    return;
  }
  await element.requestFullscreen().catch(() => undefined);
}

async function exitBoardExamFullscreen() {
  if (typeof globalThis.document === "undefined" || !globalThis.document.fullscreenElement) {
    return;
  }
  if (typeof globalThis.document.exitFullscreen !== "function") {
    return;
  }
  await globalThis.document.exitFullscreen().catch(() => undefined);
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

function normalizeSubjectForMatch(subject?: string | null): string {
  return subject?.trim().toLocaleLowerCase("en") ?? "";
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
    .filter((candidate) => normalizeSubjectForMatch(candidate.subject) === currentSubject);
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
  const progressRef = useRef<{
    currentIndex: number;
    selectedChoices: Record<number, number>;
    selectedMultiChoices: Record<number, number[]>;
  }>({
    currentIndex: 0,
    selectedChoices: {},
    selectedMultiChoices: {},
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
  const [generatingMore, setGeneratingMore] = useState(false);
  const [noMoreQuestions, setNoMoreQuestions] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [deadlineEpochSeconds, setDeadlineEpochSeconds] = useState<number | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedChoices, setSelectedChoices] = useState<Record<number, number>>({});
  const [selectedMultiChoices, setSelectedMultiChoices] = useState<Record<number, number[]>>({});
  const [timedOut, setTimedOut] = useState(false);
  const [showAnswerReview, setShowAnswerReview] = useState(false);
  const [showFirstQuizCompletionBanner, setShowFirstQuizCompletionBanner] = useState(false);
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [viewerId, setViewerId] = useState<string | null>(null);
  const [viewerPlanType, setViewerPlanType] = useState<ChallengeViewerPlanType>(null);
  const [viewerProfileType, setViewerProfileType] = useState<ChallengeViewerProfileType>(null);
  const [activePaywallModal, setActivePaywallModal] = useState<ChallengePaywallVariant | null>(null);
  const [nextStepResponse, setNextStepResponse] = useState<PostSessionNextStepResponse | null>(null);
  const [challengeQuizLimitReached, setChallengeQuizLimitReached] = useState(false);
  const [boardExamUsedThisMonth, setBoardExamUsedThisMonth] = useState(0);
  const [boardExamMonthlyLimit, setBoardExamMonthlyLimit] = useState(0);
  const [selectedDifficulty, setSelectedDifficulty] = useState<ChallengeDifficulty>("medium");
  const [selectedMode, setSelectedMode] = useState<ChallengeQuizMode>(() => (
    resolvePreferredChallengeMode(getAuthUser()?.profileType)
  ));
  const [showVerifyEmailModal, setShowVerifyEmailModal] = useState(false);
  const [showBoardExamStartModal, setShowBoardExamStartModal] = useState(false);
  const [showBoardExamFocusTip, setShowBoardExamFocusTip] = useState(false);
  const [availableBoardExamSourceNotes, setAvailableBoardExamSourceNotes] = useState<NoteListItemResponse[]>([]);
  const [selectedBoardExamAdditionalStudyPackIds, setSelectedBoardExamAdditionalStudyPackIds] = useState<string[]>([]);
  const [sourceNotesLoading, setSourceNotesLoading] = useState(false);
  const [sourceNotesError, setSourceNotesError] = useState<string | null>(null);
  const [isMobileNavigatorViewport, setIsMobileNavigatorViewport] = useState(isMobileQuestionNavigatorViewport);
  const { usageSummary } = useBillingUsageSummary();

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
  const collectionId = useMemo(() => searchParams.get("collectionId")?.trim() || null, [searchParams]);
  const [sharedModeSelectionEntryRequested, setSharedModeSelectionEntryRequested] = useState(hasModeSelectionEntryQuery);
  const [currentLearnerLevel, setCurrentLearnerLevel] = useState<LearnerLevel | null>(null);
  const [savingLearnerLevel, setSavingLearnerLevel] = useState(false);
  const [learnerLevelToast, setLearnerLevelToast] = useState<string | null>(null);
  const [generateMoreToast, setGenerateMoreToast] = useState<string | null>(null);
  const noteDetailHref = useMemo(() => (note ? `/notes/${note.id}` : "/library"), [note]);
  const planBackHref = collectionId ? `/collections/${collectionId}` : noteDetailHref;
  const planBackLabel = collectionId ? getCollectionLabels(getAuthUser()?.profileType).singular : "Note";
  const currentPlan = usageSummary?.plan ?? viewerPlanType ?? "FREE";
  const groupedLearnerLevels = useMemo(
    () => getGroupedLearnerLevels(viewerProfileType as Parameters<typeof getGroupedLearnerLevels>[0]),
    [viewerProfileType],
  );
  const syncProgressRef = useCallback((
    nextIndex: number,
    nextSelectedChoices: Record<number, number>,
    nextSelectedMultiChoices: Record<number, number[]> = progressRef.current.selectedMultiChoices,
  ) => {
    progressRef.current = {
      currentIndex: nextIndex,
      selectedChoices: nextSelectedChoices,
      selectedMultiChoices: nextSelectedMultiChoices,
    };
  }, []);
  const openLockedFeaturePaywall = useCallback(
    (variant: ChallengePaywallVariant, source: string) => {
      const feature = variant === "difficulty-selection"
        ? "difficulty"
        : variant === "challenge-quiz-limit"
          ? "quiz_limit"
          : variant === "board-exam-limit"
            ? "board_exam_limit"
            : variant === "adaptive-practice"
              ? "adaptive"
              : variant === "long-exam-mode"
                ? "long_exam"
                : variant === "interview-practice-limit"
                  ? "interview_practice"
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
      setViewerPlanType(authUser?.planType === "FREE" || authUser?.planType === "PLUS" || authUser?.planType === "PRO" ? authUser.planType : null);
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
    setSelectedMode(collectionId ? BOARD_EXAM_MODE : resolvePreferredChallengeMode(viewerProfileType));
    setPrestartStep(collectionId ? "board-exam-setup" : resolveInitialPrestartStep(viewerProfileType));
  }, [challengeSession?.sessionId, collectionId, phase, sharedModeSelectionEntryRequested, viewerProfileType]);

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

  useEffect(() => {
    if (phase !== "complete") {
      return;
    }
    void getMe().then((me) => {
      if (me.learnerLevel) {
        setCurrentLearnerLevel(me.learnerLevel);
      }
    }).catch(() => undefined);
  }, [phase]);

  useEffect(() => {
    if (!learnerLevelToast) {
      return;
    }
    const timer = setTimeout(() => setLearnerLevelToast(null), 3500);
    return () => clearTimeout(timer);
  }, [learnerLevelToast]);

  useEffect(() => {
    if (!generateMoreToast) {
      return;
    }
    const timer = setTimeout(() => setGenerateMoreToast(null), 3000);
    return () => clearTimeout(timer);
  }, [generateMoreToast]);

  const handleChangeLearnerLevel = async (level: LearnerLevel) => {
    if (savingLearnerLevel) {
      return;
    }
    setSavingLearnerLevel(true);
    try {
      await updateProfileLearnerLevel(level);
      setCurrentLearnerLevel(level);
      setLearnerLevelToast("Learner level updated. Future Study Packs and quizzes will match this level.");
    } catch {
      setLearnerLevelToast("Could not update learner level. Please try again.");
    } finally {
      setSavingLearnerLevel(false);
    }
  };

  const refreshBoardExamSourceNotes = useCallback(async (noteDetail: NoteResponse) => {
    setSourceNotesLoading(true);
    setSourceNotesError(null);
    try {
      const notes = await listNotes();
      if (collectionId) {
        try {
          const collection = await getCollection(collectionId);
          const collectionSourceNotes = resolveCollectionScopedSourceNotes(
            collection,
            notes,
            noteDetail.id,
            { requireStudyPackId: true },
          );
          setAvailableBoardExamSourceNotes(collectionSourceNotes);
          setSelectedBoardExamAdditionalStudyPackIds(
            collectionSourceNotes
              .map((sourceNote) => sourceNote.studyPackId)
              .filter((studyPackId): studyPackId is string => Boolean(studyPackId))
              .slice(0, BOARD_EXAM_MAX_ADDITIONAL_NOTES),
          );
          return;
        } catch {
          // Fall back to the normal single-note/same-subject setup when the plan cannot be loaded.
        }
      }
      const sameSubjectNotes = resolveSameSubjectSourceNotes(noteDetail, notes);
      setAvailableBoardExamSourceNotes(sameSubjectNotes);
      setSelectedBoardExamAdditionalStudyPackIds((current) => {
        const availableStudyPackIds = new Set(sameSubjectNotes.map((sourceNote) => sourceNote.studyPackId).filter(Boolean));
        return current.filter((studyPackId) => availableStudyPackIds.has(studyPackId));
      });
    } catch {
      setAvailableBoardExamSourceNotes([]);
      setSelectedBoardExamAdditionalStudyPackIds([]);
      setSourceNotesError("Could not load same-subject notes.");
    } finally {
      setSourceNotesLoading(false);
    }
  }, [collectionId]);

  const applyStartedSession = useCallback((started: ChallengeQuizStartResponse, forceRunning = false) => {
    timeoutAutoSubmitRequestedRef.current = false;
    setNextStepResponse(null);
    setSelectedMode(started.mode ?? CHALLENGE_MODE);
    setSelectedDifficulty(normalizePracticeDifficulty(started.selectedDifficulty));
    setBoardExamUsedThisMonth(started.boardExamUsedThisMonth ?? 0);
    setBoardExamMonthlyLimit(started.boardExamMonthlyLimit ?? 0);

    if (started.status === "GENERATING") {
      syncProgressRef(0, {}, {});
      setChallengeSession(started);
      setResult(null);
      setError(null);
      setSelectedChoices({});
      setSelectedMultiChoices({});
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
      syncProgressRef(0, {}, {});
      setChallengeSession(started);
      setResult(null);
      setSelectedChoices({});
      setSelectedMultiChoices({});
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
      syncProgressRef(0, {}, {});
      setChallengeSession(started);
      setResult(null);
      setError(null);
      setSelectedChoices({});
      setSelectedMultiChoices({});
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
    const restoredMultiChoices = toSelectedMultiChoiceIndicesRecord(state.selectedMultiChoices, started.quiz);
    const normalizedIndex = Math.max(0, Math.min(started.currentQuestionIndex ?? 0, Math.max(0, started.quiz.length - 1)));
    const nextDeadlineEpochSeconds = resolveDeadlineEpochSeconds(
      started.timeLimitSeconds,
      state,
      getNowEpochSeconds(),
    );
    syncProgressRef(normalizedIndex, restoredChoices, restoredMultiChoices);

    setChallengeSession(started);
    setResult(null);
    setError(null);
    setSelectedChoices(restoredChoices);
    setSelectedMultiChoices(restoredMultiChoices);
    setCurrentIndex(normalizedIndex);
    setDeadlineEpochSeconds(nextDeadlineEpochSeconds);
    setRemainingSeconds(resolveRemainingSecondsFromDeadline(nextDeadlineEpochSeconds, getNowEpochSeconds()));
    setTimedOut(false);
    setShowAnswerReview(false);
    setPhase(forceRunning || Boolean(started.sessionId) ? "running" : "prestart");
  }, [syncProgressRef]);

  const persistProgress = useCallback(
    (
      nextIndex: number,
      nextSelectedChoices: Record<number, number>,
      nextSelectedMultiChoices: Record<number, number[]> = progressRef.current.selectedMultiChoices,
      keepalive = false,
    ) => {
      if (!challengeSession?.sessionId) {
        return;
      }

      const sessionState = {
        selectedChoices: serializeSelectedChoiceIndexRecord(nextSelectedChoices),
        selectedMultiChoices: serializeSelectedMultiChoiceIndicesRecord(nextSelectedMultiChoices),
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
      void refreshBoardExamSourceNotes(detail);
      const authUser = getAuthUser();
      const preferredMode = resolvePreferredChallengeMode(authUser?.profileType);
      const requestedPrestartMode = collectionId ? BOARD_EXAM_MODE : preferredMode;
      const requestedPrestartStep: ChallengePrestartStep = collectionId ? "board-exam-setup" : resolveInitialPrestartStep(authUser?.profileType);
      const resolvedViewerPlanType = authUser?.planType === "FREE" || authUser?.planType === "PLUS" || authUser?.planType === "PRO"
        ? authUser.planType
        : null;
      setIsEmailVerified(Boolean(authUser?.emailVerifiedAt));
      setViewerPlanType(resolvedViewerPlanType);
      setViewerProfileType(isChallengeViewerProfileType(authUser?.profileType) ? authUser.profileType : null);
      if (!authUser?.emailVerifiedAt) {
        syncProgressRef(0, {}, {});
        setChallengeSession(null);
        setResult(null);
        setSelectedChoices({});
        setSelectedMultiChoices({});
        setCurrentIndex(0);
        setDeadlineEpochSeconds(null);
        setRemainingSeconds(0);
        setTimedOut(false);
        setShowAnswerReview(false);
        setSelectedMode(requestedPrestartMode);
        setPrestartStep(requestedPrestartStep);
        setActivePaywallModal(null);
        setPhase("prestart");
        return;
      }

      const inProgress = await getInProgressChallengeQuizSession(detail.id);
      setBoardExamUsedThisMonth(inProgress.boardExamUsedThisMonth ?? 0);
      setBoardExamMonthlyLimit(inProgress.boardExamMonthlyLimit ?? 0);
      if (sharedModeSelectionEntryRequested) {
        setSelectedDifficulty(normalizePracticeDifficulty(inProgress.selectedDifficulty));
        syncProgressRef(0, {}, {});
        setChallengeSession(null);
        setResult(null);
        setSelectedChoices({});
        setSelectedMultiChoices({});
        setCurrentIndex(0);
        setDeadlineEpochSeconds(null);
        setRemainingSeconds(0);
        setTimedOut(false);
        setShowAnswerReview(false);
        setSelectedMode(requestedPrestartMode);
        setPrestartStep(requestedPrestartStep);
        setChallengeQuizLimitReached(inProgress.usedThisMonth >= inProgress.monthlyLimit);
        setPhase("prestart");
        setActivePaywallModal(null);
        return;
      }

      setSelectedMode(inProgress.mode ?? preferredMode);
      if (inProgress.sessionId) {
        setSelectedDifficulty(normalizePracticeDifficulty(inProgress.selectedDifficulty));
        setActivePaywallModal(null);
        applyStartedSession(inProgress, true);
      } else {
        setSelectedDifficulty(normalizePracticeDifficulty(inProgress.selectedDifficulty));
        syncProgressRef(0, {}, {});
        setChallengeSession(null);
        setResult(null);
        setSelectedChoices({});
        setSelectedMultiChoices({});
        setCurrentIndex(0);
        setDeadlineEpochSeconds(null);
        setRemainingSeconds(0);
        setTimedOut(false);
        setShowAnswerReview(false);
        setSelectedMode(requestedPrestartMode);
        setPrestartStep(requestedPrestartStep);
        setChallengeQuizLimitReached(inProgress.usedThisMonth >= inProgress.monthlyLimit);
        setPhase("prestart");
        setActivePaywallModal(null);
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
  }, [applyStartedSession, collectionId, noteId, pathname, refreshBoardExamSourceNotes, router, sharedModeSelectionEntryRequested, syncProgressRef]);

  useEffect(() => {
    void loadNote();
  }, [loadNote]);

  const quiz = useMemo(() => challengeSession?.quiz ?? [], [challengeSession]);
  const totalQuestions = quiz.length;
  const answeredCount = useMemo(() => new Set([
    ...Object.keys(selectedChoices),
    ...Object.entries(selectedMultiChoices)
      .filter(([, value]) => value.length > 0)
      .map(([key]) => key),
  ]).size, [selectedChoices, selectedMultiChoices]);
  const activeMode = challengeSession?.mode ?? selectedMode;
  const isBoardExamMode = activeMode === BOARD_EXAM_MODE;
  const activeSourceNoteRefs = challengeSession?.sourceNoteRefs ?? [];
  const selectedBoardExamSourceCount = 1 + selectedBoardExamAdditionalStudyPackIds.length;
  const boardExamRemaining = Math.max(0, boardExamMonthlyLimit - boardExamUsedThisMonth);
  const boardExamLimitReached = boardExamMonthlyLimit > 0 && boardExamUsedThisMonth >= boardExamMonthlyLimit;
  const boardExamUpgradeCtas = getUpgradeCtas((viewerPlanType ?? "FREE") as AppPlanType);
  const currentQuestion = totalQuestions > 0 && currentIndex < totalQuestions ? quiz[currentIndex] : null;
  const currentMatchingGroup = !isBoardExamMode ? resolveQuizItemGroupAt(quiz, currentIndex) : null;
  const selectedChoiceIndex = selectedChoices[currentIndex] ?? null;
  const selectedMultiChoiceIndices = selectedMultiChoices[currentIndex] ?? [];
  const currentMatchingGroupAnswered = currentMatchingGroup
    ? currentMatchingGroup.items.every((_, offset) => selectedChoices[currentMatchingGroup.startIndex + offset] != null)
    : false;
  useAppShellTitleOverride(activeMode === BOARD_EXAM_MODE ? "Board Exam" : null);

  const toggleBoardExamAdditionalSource = useCallback((studyPackIdToToggle: string) => {
    setSelectedBoardExamAdditionalStudyPackIds((current) => {
      if (current.includes(studyPackIdToToggle)) {
        return current.filter((studyPackIdValue) => studyPackIdValue !== studyPackIdToToggle);
      }
      if (current.length >= BOARD_EXAM_MAX_ADDITIONAL_NOTES) {
        return current;
      }
      return [...current, studyPackIdToToggle];
    });
  }, []);

  useEffect(() => {
    progressRef.current = {
      currentIndex,
      selectedChoices,
      selectedMultiChoices,
    };
  }, [currentIndex, selectedChoices, selectedMultiChoices]);

  useEffect(() => {
    remainingSecondsRef.current = remainingSeconds;
  }, [remainingSeconds]);

  const persistLatestProgress = useCallback((keepalive = false) => {
    const latest = progressRef.current;
    persistProgress(latest.currentIndex, latest.selectedChoices, latest.selectedMultiChoices, keepalive);
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
    const latestSelectedMultiChoices = progressRef.current.selectedMultiChoices;
    const { correctAnswers, totalQuestions: total } = computeScore(activeSession.quiz, latestSelectedChoices, latestSelectedMultiChoices);
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
        setNextStepResponse(null);
        void getPostSessionNextStep(completed.studyPackId)
          .then(setNextStepResponse)
          .catch(() => setNextStepResponse(null));
        void trackAnalyticsEvent({
          eventType: "CHALLENGE_QUIZ_COMPLETED",
          entityId: completed.sessionId,
          metadata: {
            scorePercentage: completed.scorePercentage,
            weakConceptCount: completed.weakConcepts.length,
          },
        });
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
      if (nextMode === BOARD_EXAM_MODE && selectedBoardExamAdditionalStudyPackIds.length > 0) {
        request.additionalStudyPackIds = selectedBoardExamAdditionalStudyPackIds;
      }
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
      } else if (message.toLowerCase().includes("monthly board exam limit")) {
        setPhase("prestart");
        openLockedFeaturePaywall("board-exam-limit", "board_exam_start");
      }
    } finally {
      startInFlightRef.current = false;
      setStarting(false);
    }
  }, [applyStartedSession, isEmailVerified, note, openLockedFeaturePaywall, selectedBoardExamAdditionalStudyPackIds, selectedDifficulty, selectedMode, viewerPlanType]);

  const handleRetry = () => {
    timeoutAutoSubmitRequestedRef.current = false;
    syncProgressRef(0, {}, {});
    setChallengeSession(null);
    setResult(null);
    setSelectedChoices({});
    setSelectedMultiChoices({});
    setCurrentIndex(0);
    setDeadlineEpochSeconds(null);
    setRemainingSeconds(0);
    setTimedOut(false);
    setError(null);
    setShowAnswerReview(false);
    setShowBoardExamStartModal(false);
    setGeneratingMore(false);
    setNoMoreQuestions(false);
    setGenerateMoreToast(null);
    setNextStepResponse(null);
    setPrestartStep(resolveRecoveryPrestartStep(challengeSession?.mode ?? selectedMode));
    setPhase("prestart");
  };

  const handleGenerateMore = useCallback(async () => {
    if (!challengeSession?.sessionId || generatingMore) {
      return;
    }
    setGeneratingMore(true);
    setError(null);
    try {
      const response = await generateMoreChallengeQuizQuestions(challengeSession.sessionId);
      setChallengeSession((prev) => {
        if (!prev) {
          return prev;
        }
        return {
          ...prev,
          quiz: [...prev.quiz, ...response.newQuestions],
          totalQuestions: response.totalQuestions,
        };
      });
      const nextDeadline = resolveDeadlineEpochSeconds(
        response.timeLimitSeconds,
        { timerStartedAtEpochSeconds: response.timerStartedAtEpochSeconds },
        getNowEpochSeconds(),
      );
      const nextRemainingSeconds = resolveRemainingSecondsFromDeadline(nextDeadline, getNowEpochSeconds());
      setDeadlineEpochSeconds(nextDeadline);
      setRemainingSeconds(nextRemainingSeconds);
      remainingSecondsRef.current = nextRemainingSeconds;
      const nextIndex = challengeSession.quiz.length;
      syncProgressRef(nextIndex, progressRef.current.selectedChoices, progressRef.current.selectedMultiChoices);
      setCurrentIndex(nextIndex);
      setGenerateMoreToast(
        response.totalQuestions >= MAX_SESSION_QUESTIONS
          ? `Full challenge unlocked: ${MAX_SESSION_QUESTIONS} questions`
          : `Challenge extended to ${response.totalQuestions} questions`,
      );
      if (challengeSession.quiz.length + response.newQuestions.length >= MAX_SESSION_QUESTIONS) {
        setNoMoreQuestions(true);
      }
    } catch (err) {
      if (isNotEnoughNewQuestionsError(err)) {
        setNoMoreQuestions(true);
      } else {
        setError(err instanceof Error ? err.message : "Could not generate more questions.");
      }
    } finally {
      setGeneratingMore(false);
    }
  }, [challengeSession, generatingMore, syncProgressRef]);

  const challengeGenerationLocked = starting || phase === "generating";
  const challengeQuizActive = phase === "running" && Boolean(challengeSession?.sessionId);
  const boardExamTimerExpired = isBoardExamMode && remainingSeconds <= 0;
  useExamFocusMode(isBoardExamMode && phase === "running");

  useEffect(() => {
    if (!isBoardExamMode || phase === "running") {
      return;
    }
    void exitBoardExamFullscreen();
  }, [isBoardExamMode, phase]);
  const quizInteractionDisabled = submitting || generatingMore || boardExamTimerExpired;
  const quizModeLabel = isBoardExamMode ? "Board Exam Mode" : "Challenge Quiz";
  const quizResultLabel = isBoardExamMode ? "Exam Result" : "Challenge Quiz Result";
  const submitButtonLabel = isBoardExamMode ? "Submit Exam" : "Submit Challenge Quiz";
  const retryButtonLabel = isBoardExamMode ? "Take Another Board Exam" : "Start Another Challenge";
  const generationOverlayTitle = isBoardExamMode ? "Preparing your board exam..." : "Generating your quiz...";
  const generationOverlayMessage = isBoardExamMode
    ? "Creating a stricter exam simulation from your notes"
    : "Creating personalized questions from your notes";
  const questionCountSummary = getQuestionCountSummary(note?.difficultySelectionAvailable);
  const canChooseChallengeDifficulty = Boolean(note?.difficultySelectionAvailable);
  const boardExamAvailable = viewerPlanType === "PRO";
  const availableExamModes = useMemo(
    () => getAvailableExamModes(viewerProfileType),
    [viewerProfileType],
  );
  const challengeModeCard = availableExamModes.find((mode) => mode.id === "challenge");
  const boardExamModeCard = availableExamModes.find((mode) => mode.id === "board_exam");
  const longExamModeCard = availableExamModes.find((mode) => mode.id === "long_exam");
  const showInterviewPracticeModeCard = viewerProfileType === "PROFESSIONAL" && note?.studyPackStatus === "STUDY_PACK_READY";
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
    setError(null);
    if (challengeQuizLimitReached) {
      if (shouldShowChallengeQuizLimitPage(viewerPlanType)) {
        setPhase("limit-reached");
      } else {
        openLockedFeaturePaywall("challenge-quiz-limit", "challenge_mode_selection");
      }
      return;
    }
    setSelectedMode(CHALLENGE_MODE);
    setPrestartStep("challenge-setup");
  }, [challengeQuizLimitReached, openLockedFeaturePaywall, viewerPlanType]);
  const handleSelectBoardExamMode = useCallback(() => {
    setSelectedMode(BOARD_EXAM_MODE);
    setError(null);
    if (!boardExamAvailable) {
      setPrestartStep("board-exam-setup");
      return;
    }
    if (challengeQuizLimitReached) {
      if (shouldShowChallengeQuizLimitPage(viewerPlanType)) {
        setPhase("limit-reached");
      } else {
        openLockedFeaturePaywall("challenge-quiz-limit", "board_exam_mode_selection");
      }
      return;
    }
    if (boardExamLimitReached) {
      openLockedFeaturePaywall("board-exam-limit", "board_exam_mode_selection");
      return;
    }
    setPrestartStep("board-exam-setup");
  }, [boardExamAvailable, boardExamLimitReached, challengeQuizLimitReached, openLockedFeaturePaywall, viewerPlanType]);
  const handleSelectLongExamMode = useCallback(() => {
    setError(null);
    router.push(`/notes/${noteId}/long-exam`);
  }, [noteId, router]);
  const handleSelectInterviewPracticeMode = useCallback(() => {
    setError(null);
    router.push(`/notes/${noteId}/interview-practice`);
  }, [noteId, router]);
  const returnToModeSelection = useCallback(() => {
    setError(null);
    setShowBoardExamStartModal(false);
    setPhase("prestart");
    setPrestartStep("mode-selection");
    setSelectedMode(resolvePreferredChallengeMode(viewerProfileType));
  }, [viewerProfileType]);
  const handleBeforeRouteLeave = useCallback(() => {
    persistLatestProgress(true);
  }, [persistLatestProgress]);

  const handleLeaveSession = useCallback(async () => {
    const activeSession = challengeSessionRef.current;
    if (!activeSession?.sessionId) {
      return;
    }
    if (submitInFlightRef.current) {
      throw new Error("Challenge Quiz submission is already in progress.");
    }
    persistLatestProgress(true);
    if (activeSession.mode === BOARD_EXAM_MODE) {
      await finalizeChallengeSession({
        timeoutTriggered: false,
        persistResultToPage: false,
      });
      return;
    }
    await forfeitChallengeQuizSession(activeSession.sessionId);
  }, [finalizeChallengeSession, persistLatestProgress]);

  const { requestLeave, LeaveQuizModal } = useQuizSessionGuard({
    active: challengeQuizActive,
    fallbackHref: noteDetailHref,
    onBeforeRouteLeave: handleBeforeRouteLeave,
    onConfirmLeave: handleLeaveSession,
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


  const isNotFound = error?.toLowerCase().includes("not found") ?? false;

  return (
    <main className={cn(
      "mx-auto w-full max-w-3xl space-y-4 px-4 py-6 sm:px-6 sm:py-10",
      phase === "running" && "pb-28 sm:pb-28",
    )}>
      {phase === "running" ? (
        isBoardExamMode ? (
          <ExamTopBar
            modeLabel="Board Exam"
            leaveLabel="Leave Exam"
            onLeave={() => requestLeave()}
            leaveDisabled={submitting}
            remainingSeconds={remainingSeconds}
            timerState={boardExamTimerState}
            tone="board-exam"
            testId="challenge-quiz-top-bar"
            timerTestId="board-exam-timer"
          />
        ) : (
          <div
            data-testid="challenge-quiz-top-bar"
            className="sticky top-16 z-20 -mx-4 flex items-center gap-3 border-b border-border bg-background/95 px-4 py-3 backdrop-blur sm:mx-0 sm:rounded-xl sm:border"
          >
            <Button type="button" variant="outline" size="sm" className="shrink-0 px-3" onClick={() => requestLeave()} disabled={submitting}>
              Leave Quiz
            </Button>
            <div className="min-w-0 flex-1 text-center">
              <p className="truncate text-sm font-semibold text-blue-700 dark:text-blue-300">
                {quizModeLabel}
              </p>
              {note?.title ? (
                <p className="truncate text-xs text-foreground/55">{note.title}</p>
              ) : null}
            </div>
            <div
              data-testid="challenge-quiz-timer"
              className="shrink-0 rounded-full border border-border bg-background px-3 py-1 text-sm font-semibold text-foreground"
              aria-label="Exam timer"
            >
              {formatTimer(remainingSeconds)}
            </div>
          </div>
        )
      ) : (
        <div className="flex items-center justify-between gap-3">
          <BackLink href={planBackHref} label={planBackLabel} />
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
          <div>
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={returnToModeSelection}
            >
              Choose another mode
            </Button>
          </div>
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
              {viewerProfileType === "BOARD_EXAM"
                ? `Choose how you want to prepare with ${note?.title ?? "this note"}. Board Exam Mode emphasizes exam simulation, while Challenge Quiz stays flexible for regular practice.`
                : `Choose how you want to study ${note?.title ?? "this note"} today.`}
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
              {challengeModeCard ? (
                <button
                  type="button"
                  aria-pressed={selectedMode === CHALLENGE_MODE}
                  className={getSelectionCardClassName({
                    selected: selectedMode === CHALLENGE_MODE,
                    disabled: challengeGenerationLocked,
                    className: "p-4",
                  })}
                  onClick={() => void handleSelectChallengeQuizMode()}
                  disabled={challengeGenerationLocked}
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-foreground">{challengeModeCard.label}</p>
                    {selectedMode === CHALLENGE_MODE ? (
                      <span className="rounded-full border border-blue-500/30 bg-blue-500/10 px-2 py-0.5 text-[11px] font-medium text-blue-700 dark:text-blue-300">
                        {challengeModeCard.recommended ? "Recommended" : "Alternate"}
                      </span>
                    ) : null}
                  </div>
                  <p className="mt-1 text-sm text-foreground/70">
                    {challengeModeCard.description}
                  </p>
                  <p className="mt-3 text-xs text-foreground/60">
                    {canChooseChallengeDifficulty
                      ? "Pro users can choose the challenge difficulty before generation."
                      : "Review the recommended setup before you start."}
                  </p>
                </button>
              ) : null}
              {boardExamModeCard ? (
                <button
                  type="button"
                  aria-pressed={selectedMode === BOARD_EXAM_MODE}
                  className={cn(
                    getSelectionCardClassName({
                      selected: selectedMode === BOARD_EXAM_MODE,
                      disabled: challengeGenerationLocked,
                      className: "p-4",
                    }),
                    selectedMode === BOARD_EXAM_MODE
                      ? "border-foreground/35 bg-foreground/3 dark:bg-foreground/6"
                      : "hover:border-foreground/30 hover:bg-foreground/3 dark:hover:bg-foreground/5",
                  )}
                  onClick={() => handleSelectBoardExamMode()}
                  disabled={challengeGenerationLocked}
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-foreground">{boardExamModeCard.label}</p>
                    {selectedMode === BOARD_EXAM_MODE ? (
                      <span className="rounded-full border border-foreground/20 bg-foreground/6 px-2 py-0.5 text-[11px] font-medium text-foreground/80">
                        Recommended
                      </span>
                    ) : null}
                  </div>
                  <p className="mt-1 text-sm text-foreground/70">
                    {boardExamModeCard.description}
                  </p>
                  <p className="mt-3 text-xs text-foreground/60">
                    {boardExamAvailable
                      ? "Counts toward your monthly quiz limit, the same as the standard Challenge Quiz flow."
                      : "Pro only. Upgrade to unlock a stricter board-style exam flow."}
                  </p>
                </button>
              ) : null}
              {longExamModeCard ? (
                <button
                  type="button"
                  aria-pressed={false}
                  className={cn(
                    getSelectionCardClassName({
                      selected: false,
                      disabled: challengeGenerationLocked,
                      className: "p-4",
                    }),
                    "hover:border-foreground/30 hover:bg-foreground/3 dark:hover:bg-foreground/5",
                  )}
                  onClick={() => handleSelectLongExamMode()}
                  disabled={challengeGenerationLocked}
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-foreground">{longExamModeCard.label}</p>
                    {viewerPlanType !== "PRO" ? (
                      <span className="rounded-full border border-foreground/20 bg-foreground/6 px-2 py-0.5 text-[11px] font-medium text-foreground/80">
                        Pro
                      </span>
                    ) : null}
                  </div>
                  <p className="mt-1 text-sm text-foreground/70">
                    {longExamModeCard.description}
                  </p>
                  <p className="mt-3 text-xs text-foreground/60">
                    {viewerPlanType === "PRO"
                      ? "A longer session mode for deeper mastery testing."
                      : "Pro only. Upgrade to unlock full-length mastery exams."}
                  </p>
                </button>
              ) : null}
              {showInterviewPracticeModeCard ? (
                <button
                  type="button"
                  aria-pressed={false}
                  className={cn(
                    getSelectionCardClassName({
                      selected: false,
                      disabled: challengeGenerationLocked,
                      className: "p-4",
                    }),
                    "hover:border-foreground/30 hover:bg-foreground/3 dark:hover:bg-foreground/5",
                  )}
                  onClick={() => handleSelectInterviewPracticeMode()}
                  disabled={challengeGenerationLocked}
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-foreground">Interview Practice</p>
                    {viewerPlanType !== "PRO" ? (
                      <span className="rounded-full border border-foreground/20 bg-foreground/6 px-2 py-0.5 text-[11px] font-medium text-foreground/80">
                        Pro
                      </span>
                    ) : null}
                  </div>
                  <p className="mt-1 text-sm text-foreground/70">
                    Coached scenario-based interview prep with per-answer AI critique.
                  </p>
                  <p className="mt-3 text-xs text-foreground/60">
                    {viewerPlanType === "PRO"
                      ? "Practice professional scenarios from this note with guided critique."
                      : "Pro only. Upgrade to unlock coached interview preparation."}
                  </p>
                </button>
              ) : null}
            </div>
            <p className="text-xs text-foreground/60">
              {viewerProfileType === "BOARD_EXAM"
                ? "Both modes count toward your monthly quiz limit."
                : "Challenge Quiz counts toward your monthly quiz limit."}
            </p>
            {viewerProfileType !== "BOARD_EXAM" && viewerProfileType !== "TEACHER" ? (
              <p className="text-xs text-foreground/55">
                Preparing for boards?{" "}
                <Link href="/profile" className="underline underline-offset-2">Switch your profile in Settings</Link>
                {" "}to enable Board Exam Mode.
              </p>
            ) : null}
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
                  <p>Pro lets you choose the level before you start.</p>
                ) : (
                  <>
                    <p>Recommended difficulty: Medium</p>
                    <p className="text-foreground/65">Choose difficulty (Pro)</p>
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
          <section className="space-y-6 sm:space-y-8">
            <header className="space-y-3">
              <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
                Board Exam
              </h1>
              <p className="max-w-2xl text-base leading-relaxed text-foreground/70 sm:text-lg">
                A timed simulation. The clock does not pause. Unanswered questions count against you.
              </p>
              {note?.title ? (
                <p className="text-sm text-foreground/55">
                  Built from <span className="font-medium text-foreground/80">{note.title}</span>
                </p>
              ) : null}
            </header>

            <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
              <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">
                Pre-flight checklist
              </h2>
              <ul className="mt-5 space-y-5">
                <li className="flex gap-4">
                  <EyeOff className="mt-0.5 h-5 w-5 shrink-0 text-foreground/70" aria-hidden="true" />
                  <div className="space-y-1">
                    <p className="text-sm font-medium text-foreground">Do not leave the page</p>
                    <p className="text-sm leading-relaxed text-foreground/70">
                      Refreshing or navigating away counts as submission.
                    </p>
                  </div>
                </li>
                <li className="flex gap-4">
                  <Maximize2 className="mt-0.5 h-5 w-5 shrink-0 text-foreground/70" aria-hidden="true" />
                  <div className="space-y-1">
                    <p className="text-sm font-medium text-foreground">Fullscreen recommended</p>
                    <p className="text-sm leading-relaxed text-foreground/70">
                      The exam will request fullscreen for a focused sitting.
                    </p>
                  </div>
                </li>
                <li className="flex gap-4">
                  <Hourglass className="mt-0.5 h-5 w-5 shrink-0 text-foreground/70" aria-hidden="true" />
                  <div className="space-y-1">
                    <p className="text-sm font-medium text-foreground">~1 minute per question</p>
                    <p className="text-sm leading-relaxed text-foreground/70">
                      A fixed board-style set. Timer is set at start and does not extend.
                    </p>
                  </div>
                </li>
                <li className="flex gap-4">
                  <ListChecks className="mt-0.5 h-5 w-5 shrink-0 text-foreground/70" aria-hidden="true" />
                  <div className="space-y-1">
                    <p className="text-sm font-medium text-foreground">Scored against every question</p>
                    <p className="text-sm leading-relaxed text-foreground/70">
                      Unanswered items count as wrong. Your score report appears only after you submit.
                    </p>
                  </div>
                </li>
              </ul>
            </div>

            <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
              <div className="space-y-1">
                <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">
                  Span this exam across more notes
                </h2>
                <p className="text-sm text-foreground/70">
                  {collectionId
                    ? `Add up to ${BOARD_EXAM_MAX_ADDITIONAL_NOTES} more notes from this plan.`
                    : `Add up to ${BOARD_EXAM_MAX_ADDITIONAL_NOTES} ready Study Packs from this subject.`}
                </p>
              </div>
              {sourceNotesLoading ? (
                <p className="mt-4 text-sm text-foreground/60">Loading same-subject notes...</p>
              ) : sourceNotesError ? (
                <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <p className="text-sm text-foreground/60">
                    {sourceNotesError} Single-note Board Exam is still available.
                  </p>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="w-full sm:w-auto"
                    onClick={() => {
                      if (note) {
                        void refreshBoardExamSourceNotes(note);
                      }
                    }}
                  >
                    Retry
                  </Button>
                </div>
              ) : availableBoardExamSourceNotes.length > 0 ? (
                <div className="mt-4 grid gap-2">
                  {availableBoardExamSourceNotes.map((sourceNote) => {
                    const sourceStudyPackId = sourceNote.studyPackId ?? "";
                    const selected = selectedBoardExamAdditionalStudyPackIds.includes(sourceStudyPackId);
                    const capped = !selected && selectedBoardExamAdditionalStudyPackIds.length >= BOARD_EXAM_MAX_ADDITIONAL_NOTES;
                    return (
                      <button
                        key={sourceNote.id}
                        type="button"
                        className={cn(
                          "rounded-xl border px-4 py-3 text-left transition",
                          selected
                            ? "border-foreground/40 bg-foreground/5 text-foreground"
                            : "border-border bg-background text-foreground/75 hover:border-foreground/25 hover:bg-muted/30",
                          capped && "opacity-60",
                        )}
                        aria-pressed={selected}
                        onClick={() => toggleBoardExamAdditionalSource(sourceStudyPackId)}
                      >
                        <span className="block text-sm font-medium text-foreground">{sourceNote.title ?? "Untitled note"}</span>
                        <span className="block text-xs text-foreground/60">{sourceNote.subject}</span>
                      </button>
                    );
                  })}
                  {selectedBoardExamAdditionalStudyPackIds.length > 0 ? (
                    <>
                      <p className="flex items-center gap-2 text-sm text-foreground/70">
                        <Hourglass className="h-4 w-4 shrink-0" aria-hidden="true" />
                        <span>Generating from multiple notes may take up to a minute.</span>
                      </p>
                      {boardExamAvailable ? (
                        <>
                          <p className="text-sm text-foreground/70">
                            This session uses 1 of your {boardExamRemaining} remaining Board Exam sessions.
                          </p>
                        </>
                      ) : null}
                    </>
                  ) : null}
                </div>
              ) : (
                <p className="mt-4 text-sm text-foreground/55">{BOARD_EXAM_MULTI_NOTE_EMPTY_HINT}</p>
              )}
            </div>

            {challengeGenerationLocked ? (
              <p className="text-sm text-foreground/75">Preparing your board exam...</p>
            ) : null}
            {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
            {boardExamLimitReached ? (
              <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-500/40 dark:bg-amber-950/25 dark:text-amber-100">
                <p className="font-medium">You&apos;ve used all {boardExamMonthlyLimit} Board Exam sessions for this month.</p>
                <p className="mt-1 text-amber-900/80 dark:text-amber-100/80">
                  You can still review existing results. Start a new Board Exam when your quota resets.
                </p>
                {boardExamUpgradeCtas.primary ? (
                  <Button
                    type="button"
                    className="mt-3 w-full sm:w-auto"
                    onClick={() => router.push("/settings?section=plans")}
                  >
                    {boardExamUpgradeCtas.primary.label}
                  </Button>
                ) : null}
              </div>
            ) : null}

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm text-foreground/65">
                {selectedBoardExamSourceCount} {selectedBoardExamSourceCount === 1 ? "note" : "notes"} · Counts toward your monthly Board Exam usage.
              </p>
              <div className="flex flex-col gap-2 sm:flex-row">
                {collectionId ? null : (
                  <Button
                    type="button"
                    variant="outline"
                    className="w-full sm:w-auto"
                    onClick={returnToModeSelection}
                    disabled={challengeGenerationLocked}
                  >
                    Choose another mode
                  </Button>
                )}
                {!boardExamLimitReached ? (
                  <Button
                    type="button"
                    className="w-full sm:w-auto"
                    onClick={() => {
                      if (!boardExamAvailable) {
                        openLockedFeaturePaywall("board-exam-mode", "board_exam_start");
                        return;
                      }
                      setShowBoardExamStartModal(true);
                    }}
                    disabled={challengeGenerationLocked}
                  >
                    {boardExamAvailable
                      ? challengeGenerationLocked ? "Starting..." : "Begin Board Exam"
                      : "Unlock Board Exam - Pro"}
                  </Button>
                ) : null}
              </div>
            </div>
          </section>
        )
      ) : phase === "running" && challengeSession ? (
        <div className="space-y-4">
          {!isBoardExamMode ? (
            <p className="text-xs text-foreground/55">
              Start with 5 questions. Generate more as you go (up to {MAX_SESSION_QUESTIONS}).
            </p>
          ) : null}
          {isBoardExamMode && showBoardExamFocusTip ? (
            <div className="flex flex-col gap-3 rounded-xl border border-foreground/15 bg-muted/20 p-4 text-sm text-foreground/80 sm:flex-row sm:items-center sm:justify-between">
              <p>{BOARD_EXAM_FOCUS_TIP}</p>
              <Button type="button" variant="outline" size="sm" className="w-full sm:w-auto" onClick={dismissBoardExamFocusTip}>
                Got it
              </Button>
            </div>
          ) : null}
          {isBoardExamMode && activeSourceNoteRefs.length > 1 ? (
            <div className="rounded-xl border border-foreground/15 bg-muted/20 p-4 text-sm text-foreground/80">
              <p className="font-medium text-foreground">Sources · {activeSourceNoteRefs.length} notes</p>
              <p className="mt-1 text-foreground/65">
                {activeSourceNoteRefs.map((source) => source.noteTitle || "Untitled note").join(", ")}
              </p>
            </div>
          ) : null}
          <Card className={cn("space-y-4 p-4 sm:p-5", isBoardExamMode ? "border-foreground/15 bg-card" : "")}>
            {currentQuestion ? (
              <div className="space-y-4">
                <div className="space-y-1">
                  <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">
                    Question {Math.min(currentIndex + 1, totalQuestions)} of {totalQuestions}
                    {isBoardExamMode && activeSourceNoteRefs.length > 1 ? ` · ${activeSourceNoteRefs.length} notes` : ""}
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
                {currentMatchingGroup ? (
                  <QuizMatchingGroup
                    items={currentMatchingGroup.items}
                    groupStartIndex={currentMatchingGroup.startIndex}
                    selectedChoices={selectedChoices}
                    revealAnswer={false}
                    disabled={quizInteractionDisabled}
                    selectionStyle="exam"
                    onSelectChoice={(questionIndex, choiceIndex) => {
                      setSelectedChoices((previous) => {
                        const next = { ...previous, [questionIndex]: choiceIndex };
                        syncProgressRef(currentIndex, next, selectedMultiChoices);
                        persistProgress(currentIndex, next, selectedMultiChoices);
                        return next;
                      });
                    }}
                  />
                ) : (
                  <>
                    <h2 className="text-lg font-semibold leading-7 sm:text-xl"><QuizQuestionText text={currentQuestion.question} /></h2>
                    <QuizChoiceList
                      questionKey={currentQuestion.question}
                      choices={currentQuestion.choices}
                      correctIndex={resolveQuizCorrectIndex(currentQuestion)}
                      correctIndices={currentQuestion.correctIndices}
                      questionFormat={isBoardExamMode ? currentQuestion.questionFormat === "TRUE_FALSE" ? "TRUE_FALSE" : "MCQ" : currentQuestion.questionFormat}
                      selectedChoiceIndex={selectedChoiceIndex}
                      selectedMultiChoiceIndices={selectedMultiChoiceIndices}
                      revealAnswer={false}
                      disabled={quizInteractionDisabled}
                      selectionStyle={isBoardExamMode ? "board-exam" : "exam"}
                      onSelectChoice={(choiceIndex) => {
                        setSelectedChoices((previous) => {
                          const next = { ...previous, [currentIndex]: choiceIndex };
                          syncProgressRef(currentIndex, next, selectedMultiChoices);
                          persistProgress(currentIndex, next, selectedMultiChoices);
                          return next;
                        });
                      }}
                      onSelectMultiChoices={(choiceIndices) => {
                        if (isBoardExamMode) {
                          return;
                        }
                        setSelectedMultiChoices((previous) => {
                          const next = { ...previous, [currentIndex]: choiceIndices };
                          syncProgressRef(currentIndex, progressRef.current.selectedChoices, next);
                          persistProgress(currentIndex, progressRef.current.selectedChoices, next);
                          return next;
                        });
                      }}
                    />
                  </>
                )}
                <p className="text-xs text-foreground/65">Answers are graded only after submission.</p>
                {!isBoardExamMode ? (
                  <p className="text-xs text-foreground/50">You can finish anytime. Score is based on answered questions.</p>
                ) : null}
                <QuestionNavigator
                  total={totalQuestions}
                  currentIndex={currentIndex}
                  isAnswered={(index) => selectedChoices[index] != null || (selectedMultiChoices[index]?.length ?? 0) > 0}
                  onSelect={(index) => {
                    syncProgressRef(index, selectedChoices, selectedMultiChoices);
                    setCurrentIndex(index);
                    persistProgress(index, selectedChoices, selectedMultiChoices);
                  }}
                  summary={questionNavigatorSummary}
                  disabled={quizInteractionDisabled}
                  defaultCollapsed={shouldCollapseQuestionNavigatorByDefault(activeMode, isMobileNavigatorViewport)}
                  tone={isBoardExamMode ? "board-exam" : "challenge"}
                  testId="challenge-question-navigator"
                  disclosureTestId="challenge-question-navigator-disclosure"
                />
              </div>
            ) : null}
            {timedOut && submitting && isBoardExamMode ? (
              <div className="rounded-md border border-foreground/15 bg-muted/20 px-3 py-2 text-sm text-foreground/80">
                Time&apos;s up. Submitting your exam...
              </div>
            ) : null}
            {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
          </Card>
          <StickyAssessmentFooter
            data-testid="challenge-quiz-action-bar"
            variant={isBoardExamMode ? "board-exam" : "default"}
            hint={!isBoardExamMode && currentIndex >= totalQuestions - 1 && !boardExamTimerExpired
              ? noMoreQuestions || totalQuestions >= MAX_SESSION_QUESTIONS
                ? `You've answered all ${totalQuestions} questions — ready to submit?`
                : totalQuestions === 5
                  ? "Good start — want to keep going?"
                  : totalQuestions === 10
                    ? "10 questions in — push to 15?"
                    : totalQuestions === 15
                      ? "Almost there — finish with all 20?"
                      : "What would you like to do next?"
              : undefined}
          >
            <div className="flex items-center justify-between">
              <Button
                type="button"
                variant="outline"
                className="w-28 shrink-0 sm:w-auto"
                onClick={() => {
                  const nextIndex = Math.max(0, (currentMatchingGroup?.startIndex ?? currentIndex) - 1);
                  syncProgressRef(nextIndex, selectedChoices, selectedMultiChoices);
                  setCurrentIndex(nextIndex);
                  persistProgress(nextIndex, selectedChoices, selectedMultiChoices);
                }}
                disabled={currentIndex <= 0 || quizInteractionDisabled}
              >
                Previous
              </Button>
              <div className="flex gap-2">
                {(currentMatchingGroup?.endIndex ?? currentIndex) < totalQuestions - 1 && !boardExamTimerExpired ? (
                  <Button
                    type="button"
                    className="flex-1 sm:w-auto sm:flex-none"
                    onClick={() => {
                      const nextIndex = Math.min(totalQuestions - 1, currentMatchingGroup ? currentMatchingGroup.endIndex + 1 : currentIndex + 1);
                      syncProgressRef(nextIndex, selectedChoices, selectedMultiChoices);
                      setCurrentIndex(nextIndex);
                      persistProgress(nextIndex, selectedChoices, selectedMultiChoices);
                    }}
                    disabled={quizInteractionDisabled || Boolean(currentMatchingGroup && !currentMatchingGroupAnswered)}
                  >
                    Next
                  </Button>
                ) : isBoardExamMode ? (
                  <Button
                    type="button"
                    className="flex-1 sm:w-auto sm:flex-none"
                    onClick={() => void handleSubmit(false)}
                    disabled={submitting}
                  >
                    {submitting ? "Submitting..." : submitButtonLabel}
                  </Button>
                ) : (
                  <>
                    {!noMoreQuestions && totalQuestions < MAX_SESSION_QUESTIONS ? (
                      <Button
                        type="button"
                        variant="outline"
                        className="flex-1 sm:w-auto sm:flex-none"
                        onClick={() => void handleGenerateMore()}
                        disabled={generatingMore || submitting}
                      >
                        {generatingMore ? "Adding..." : "+5 Questions"}
                      </Button>
                    ) : null}
                    <Button
                      type="button"
                      className="flex-1 sm:w-auto sm:flex-none"
                      onClick={() => void handleSubmit(false)}
                      disabled={submitting || generatingMore}
                    >
                      {submitting ? "Submitting..." : "Complete Quiz"}
                    </Button>
                  </>
                )}
              </div>
            </div>
          </StickyAssessmentFooter>
        </div>
      ) : phase === "complete" && result && isBoardExamMode ? (
        <section className="motion-fade-enter space-y-6 sm:space-y-8">
          <header className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">Score Report</p>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">Board Exam Result</h1>
          </header>

          <ScoreReveal
            percentage={result.scorePercentage}
            label="Score"
            supportingLine={`${result.correctAnswers} of ${result.totalQuestions} correct · ${formatTimer(result.durationSeconds ?? 0)}`}
            performanceLevel={result.performanceLevel}
            tone="board-exam"
          />

          {timedOut ? (
            <p className="text-center text-sm text-foreground/70">
              Time ran out. Your answers were submitted automatically.
            </p>
          ) : null}

          <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
            <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">Concept Breakdown</h2>
            {result.conceptBreakdown.length > 0 ? (
              <ul className="mt-4 space-y-4" aria-label="Concept breakdown">
                {result.conceptBreakdown.map((stat) => (
                  <li key={stat.concept} className="space-y-1.5">
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="text-sm font-medium text-foreground">{stat.concept}</span>
                      <span className="text-sm font-semibold tabular-nums text-foreground/85">
                        {stat.accuracyPercentage}%
                      </span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-foreground/8" aria-hidden="true">
                      <div
                        className="h-full rounded-full bg-foreground/70 transition-all"
                        style={{ width: `${Math.max(0, Math.min(100, stat.accuracyPercentage))}%` }}
                      />
                    </div>
                    <p className="text-xs text-foreground/55">
                      {stat.correctAnswers} of {stat.totalQuestions} correct
                    </p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-3 text-sm text-foreground/70">No concept breakdown is available for this exam.</p>
            )}
          </div>

          <div ref={weakConceptsRef} className="space-y-4">
            <PostSessionNextStep
              response={nextStepResponse}
              currentPlan={currentPlan}
              noteId={note?.id ?? null}
              onOpenPaywall={() => openLockedFeaturePaywall("adaptive-practice", "board_exam_results_next_step")}
            />
            {nextStepResponse?.goalNudge ? (
              <GoalNudgeCard goalNudge={nextStepResponse.goalNudge} noteId={note?.id ?? null} />
            ) : null}
            {nextStepResponse === null ? (
              <>
                <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
                  <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">Weak Concepts</h2>
                  {result.weakConcepts.length > 0 ? (
                    <div className="mt-4 flex flex-wrap gap-2">
                      {result.weakConcepts.map((concept) => (
                        <span key={concept}
                              className="rounded-full border border-amber-600/40 bg-transparent px-3 py-1 text-xs font-medium text-amber-700 dark:text-amber-300">
                          {concept}
                        </span>
                      ))}
                    </div>
                  ) : (
                    <p className="mt-3 text-sm text-foreground/70">
                      No weak concepts were identified in this exam. Review your answers or take another Board Exam when ready.
                    </p>
                  )}
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
                </div>
              </>
            ) : null}
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShowAnswerReview((previous) => !previous)}>
              {showAnswerReview ? "Hide Answer Review" : "Review Answers"}
            </Button>
          </div>
          <div>
            <BackLink href={noteDetailHref} label="Back to Note" />
          </div>
          {showAnswerReview ? (
            <QuizAnswerReview
              quiz={quiz}
              selectedChoices={selectedChoices}
              selectedMultiChoices={selectedMultiChoices}
              className="mt-2"
              planType={viewerPlanType}
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
            quizLabel="Board Exam Mode"
            noteTitle={note?.title}
            section={showAnswerReview ? "review" : "results"}
          />
        </section>
      ) : phase === "complete" && result ? (
        <Card className="motion-fade-enter space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            {quizResultLabel}
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">
            {note?.title ?? quizModeLabel}
          </h1>
          <p className="text-sm text-foreground/75">
            Your Challenge Quiz is complete. Review the result summary first, then choose the next study action.
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
          {(() => {
            const totalGenerated = quiz.length > 0 ? quiz.length : result.totalQuestions;
            const overallScorePercentage = totalGenerated > 0 ? Math.round((result.correctAnswers / totalGenerated) * 100) : 0;
            const hasUnansweredQuestions = !isBoardExamMode && totalGenerated > result.totalQuestions;
            return (
              <>
                <div className={cn(
                  "rounded-md border bg-background p-4",
                  isBoardExamMode ? "border-foreground/15" : "border-border",
                )}>
                  {hasUnansweredQuestions ? (
                    <div className="mb-4 grid gap-3 sm:grid-cols-2">
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Answered Accuracy</p>
                        <p className="mt-1 text-2xl font-bold">{result.scorePercentage}%</p>
                        <p className="mt-0.5 text-xs text-foreground/65">{result.correctAnswers} correct of {result.totalQuestions} answered</p>
                      </div>
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Overall Completion Score</p>
                        <p className="mt-1 text-2xl font-bold text-foreground/70">{overallScorePercentage}%</p>
                        <p className="mt-0.5 text-xs text-foreground/65">{result.correctAnswers} correct of {totalGenerated} total</p>
                      </div>
                    </div>
                  ) : (
                    <p className="text-lg font-semibold">{result.scorePercentage}%</p>
                  )}
                  {!hasUnansweredQuestions ? (
                    <p className="mt-1 text-sm text-foreground/80">
                      {result.correctAnswers} of {result.totalQuestions} answered correctly
                    </p>
                  ) : (
                    <p className="text-xs text-foreground/60">
                      Answered Accuracy shows performance on attempted questions. Overall Completion Score counts unanswered questions as incomplete.
                    </p>
                  )}
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
                  <div className={cn("grid gap-2", hasUnansweredQuestions ? "sm:grid-cols-4" : "sm:grid-cols-3")}>
                    <div className="rounded-md border border-border bg-background px-3 py-2">
                      <p className="text-xs text-foreground/65">Correct</p>
                      <p className="text-sm font-semibold">{result.correctAnswers}</p>
                    </div>
                    <div className="rounded-md border border-border bg-background px-3 py-2">
                      <p className="text-xs text-foreground/65">Answered Questions</p>
                      <p className="text-sm font-semibold">{result.totalQuestions}</p>
                    </div>
                    {hasUnansweredQuestions ? (
                      <div className="rounded-md border border-border bg-background px-3 py-2">
                        <p className="text-xs text-foreground/65">Total Questions</p>
                        <p className="text-sm font-semibold">{totalGenerated}</p>
                      </div>
                    ) : null}
                    <div className="rounded-md border border-border bg-background px-3 py-2">
                      <p className="text-xs text-foreground/65">{hasUnansweredQuestions ? "Answered Accuracy" : "Percentage"}</p>
                      <p className="text-sm font-semibold">{result.scorePercentage}%</p>
                    </div>
                  </div>
                </Card>
              </>
            );
          })()}
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
          <div ref={weakConceptsRef} className="space-y-4">
            <PostSessionNextStep
              response={nextStepResponse}
              currentPlan={currentPlan}
              noteId={note?.id ?? null}
              onOpenPaywall={() => openLockedFeaturePaywall("adaptive-practice", "challenge_quiz_results_next_step")}
            />
            {nextStepResponse?.goalNudge ? (
              <GoalNudgeCard goalNudge={nextStepResponse.goalNudge} noteId={note?.id ?? null} />
            ) : null}
            {nextStepResponse === null ? (
              <>
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
                </div>
              </>
            ) : null}
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
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
              selectedMultiChoices={selectedMultiChoices}
              className="mt-2"
              planType={viewerPlanType}
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
          {!isBoardExamMode && currentLearnerLevel ? (
            <div className="space-y-2 border-t border-border pt-4 text-sm">
              <p className="font-medium text-foreground">Adjust difficulty level</p>
              <div className="space-y-2">
                <div className="space-y-1.5">
                  <p className="text-xs text-foreground/50">{groupedLearnerLevels.recommendedGroupLabel}</p>
                  <div className="flex flex-wrap gap-2">
                    {groupedLearnerLevels.recommended.map((option) => (
                      <button
                        key={option.value}
                        type="button"
                        disabled={savingLearnerLevel}
                        onClick={() => void handleChangeLearnerLevel(option.value)}
                        className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                          currentLearnerLevel === option.value
                            ? "border-primary bg-primary text-primary-foreground"
                            : "border-border bg-background text-foreground/70 hover:border-foreground/30"
                        }`}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                </div>
                {groupedLearnerLevels.other.length > 0 ? (
                  <div className="space-y-1.5">
                    <p className="text-xs text-foreground/50">Other Learning Styles</p>
                    <div className="flex flex-wrap gap-2">
                      {groupedLearnerLevels.other.map((option) => (
                        <button
                          key={option.value}
                          type="button"
                          disabled={savingLearnerLevel}
                          onClick={() => void handleChangeLearnerLevel(option.value)}
                          className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                            currentLearnerLevel === option.value
                              ? "border-primary bg-primary text-primary-foreground"
                              : "border-border bg-background text-foreground/70 hover:border-foreground/30"
                          }`}
                        >
                          {option.label}
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}
              </div>
              <p className="text-xs text-foreground/55">This applies to future generations.</p>
            </div>
          ) : null}
          {!isBoardExamMode && !note?.adaptivePracticeAvailable ? (
            <PostSuccessUpgradeNudge trigger="challenge-quiz" />
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
      {generateMoreToast ? (
        <ToastMessage message={generateMoreToast} tone="success" />
      ) : learnerLevelToast ? (
        <ToastMessage message={learnerLevelToast} tone="success" />
      ) : null}
    </main>
  );
}
