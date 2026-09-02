"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { EyeOff, Hourglass, ListChecks, Maximize2 } from "lucide-react";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { useAppShellTitleOverride } from "@/components/app-shell-title-context";
import { ExamTopBar } from "@/components/exam-mode/exam-top-bar";
import { useBottomViewportClaim, useExamFocusMode } from "@/components/exam-mode/exam-focus-context";
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
import { ReviewCommitmentPrompt } from "@/components/study-pack/review-commitment-prompt";
import { WeeklyPacingEchoCard } from "@/components/study-pack/weekly-pacing-echo-card";
import { CompanionResultBridgeCard, hasCompanionResultBridgeExcerpt } from "@/components/study-pack/companion-result-bridge-card";
import { ResultGuidanceGroup } from "@/components/study-pack/result-guidance-group";
import { shouldRenderTwiceMissedCta, TwiceMissedAskCompanionCard } from "@/components/study-pack/twice-missed-ask-companion-card";
import { StickyAssessmentFooter } from "@/components/ui/sticky-assessment-footer";
import { QuizGenerationOverlay } from "@/components/study-pack/quiz-generation-overlay";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { QuizIdentificationInput } from "@/components/study-pack/quiz-identification-input";
import { QuizEnumerationInput } from "@/components/study-pack/quiz-enumeration-input";
import { QuizMatchingGroup } from "@/components/study-pack/quiz-matching-group";
import { QuizQuestionText } from "@/components/study-pack/quiz-question-text";
import { useQuizSessionGuard } from "@/components/study-pack/quiz-session-guard";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { getAuthUser } from "@/lib/auth";
import { getCollectionLabels } from "@/lib/collection-labels";
import { clearFirstStudyOnboardingStep, getFirstStudyOnboardingStep } from "@/lib/first-study-onboarding";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  type ProfileType,
  ApiRequestError,
  type ChallengeQuizStartRequest,
  type ChallengeQuizMode,
  completeChallengeQuizSession,
  forfeitChallengeQuizSession,
  generateMoreChallengeQuizQuestions,
  getCollection,
  getCollectionGoal,
  getInProgressChallengeQuizSession,
  getMe,
  getMyStudyPack,
  getNote,
  getPostSessionNextStep,
  listNotes,
  isEmailNotVerifiedError,
  isNotEnoughMissedChallengeQuestionsError,
  isNotEnoughNewQuestionsError,
  startRedoMissedChallengeQuizSession,
  startChallengeQuizSession,
  trackAnalyticsEvent,
  updateChallengeQuizSessionProgress,
  updateProfileLearnerLevel,
  type CompanionContent,
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
  serializeSelectedEnumerationAnswersRecord,
  serializeSelectedIdentificationAnswerRecord,
  serializeSelectedMultiChoiceIndicesRecord,
  toSelectedEnumerationAnswersRecord,
  toSelectedIdentificationAnswerRecord,
  toSelectedChoiceIndexRecord,
  toSelectedMultiChoiceIndicesRecord,
} from "@/lib/quiz";
import {
  CHALLENGE_QUIZ_ENTRY_QUERY_PARAM,
  isModeSelectionChallengeQuizEntry,
  isRedoMissedChallengeQuizEntry,
} from "@/lib/challenge-quiz-entry";
import {
  ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY,
  buildAdaptivePracticeHref,
} from "@/lib/adaptive-practice-entry";
import { resolveCollectionScopedSourceNotes } from "@/lib/collection-exam";
import {
  resolvePlanPremiumExamMode, getAvailableExamModes } from "@/lib/exam-mode-visibility";
import { buildConceptAnchorId, normalizeConceptKey } from "@/lib/concepts";
import { cn } from "@/lib/utils";
import { getSelectionCardClassName } from "@/lib/clickable-card";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

type ChallengePhase = "prestart" | "generating" | "running" | "complete" | "limit-reached";
type ChallengePrestartStep = "mode-selection" | "challenge-setup" | "board-exam-setup";
type ChallengeSessionStatePayload = {
  selectedChoices?: Record<string, number> | Record<string, string>;
  selectedMultiChoices?: Record<string, number[]>;
  selectedIdentificationAnswers?: Record<string, string>;
  selectedEnumerationAnswers?: Record<string, string[]>;
  timerStartedAtEpochSeconds?: number;
};
type ChallengeViewerProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PROFESSIONAL" | null;
type ChallengeViewerPlanType = "FREE" | "PLUS" | "PRO" | null;
type ChallengePaywallVariant =
  | "board-exam-mode"
  | "board-exam-limit"
  | "long-exam-mode"
  | "interview-practice-limit"
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

function isChallengeQuizSessionExpired(activeSession: ChallengeQuizStartResponse): boolean {
  const sessionState = (activeSession.sessionState ?? {}) as ChallengeSessionStatePayload;
  if (!activeSession.timeLimitSeconds || !sessionState.timerStartedAtEpochSeconds) {
    return false;
  }
  const deadline = resolveDeadlineEpochSeconds(
    activeSession.timeLimitSeconds,
    { timerStartedAtEpochSeconds: sessionState.timerStartedAtEpochSeconds },
    getNowEpochSeconds(),
  );
  return resolveRemainingSecondsFromDeadline(deadline, getNowEpochSeconds()) <= 0;
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

export default function ChallengeQuizPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const params = useParams<{ id: string }>();
  const progressRef = useRef<{
    currentIndex: number;
    selectedChoices: Record<number, number>;
    selectedMultiChoices: Record<number, number[]>;
    selectedIdentificationAnswers: Record<number, string>;
    selectedEnumerationAnswers: Record<number, string[]>;
  }>({
    currentIndex: 0,
    selectedChoices: {},
    selectedMultiChoices: {},
    selectedIdentificationAnswers: {},
    selectedEnumerationAnswers: {},
  });
  const remainingSecondsRef = useRef(0);
  const challengeSessionRef = useRef<ChallengeQuizStartResponse | null>(null);
  const startInFlightRef = useRef(false);
  const submitInFlightRef = useRef(false);
  const timeoutAutoSubmitRequestedRef = useRef(false);
  const weakConceptsRef = useRef<HTMLDivElement | null>(null);
  const legacyRedirectTargetRef = useRef<string | null>(null);
  const redoMissedStartRequestedRef = useRef(false);
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [challengeSession, setChallengeSession] = useState<ChallengeQuizStartResponse | null>(null);
  const [resumeCandidate, setResumeCandidate] = useState<ChallengeQuizStartResponse | null>(null);
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
  const [selectedIdentificationAnswers, setSelectedIdentificationAnswers] = useState<Record<number, string>>({});
  const [selectedEnumerationAnswers, setSelectedEnumerationAnswers] = useState<Record<number, string[]>>({});
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
  const [selectedMode, setSelectedMode] = useState<ChallengeQuizMode>(() => (
    resolvePreferredChallengeMode(getAuthUser()?.profileType)
  ));
  const [showVerifyEmailModal, setShowVerifyEmailModal] = useState(false);
  const [showBoardExamStartModal, setShowBoardExamStartModal] = useState(false);
  const [showIncompleteSubmitModal, setShowIncompleteSubmitModal] = useState(false);
  const [showBoardExamFocusTip, setShowBoardExamFocusTip] = useState(false);
  const [availableBoardExamSourceNotes, setAvailableBoardExamSourceNotes] = useState<NoteListItemResponse[]>([]);
  const [selectedBoardExamAdditionalStudyPackIds, setSelectedBoardExamAdditionalStudyPackIds] = useState<string[]>([]);
  /**
   * Server-reported multi-note source cap, INCLUDING the primary.
   *
   * ⚠️ Held separately from `challengeSession` on purpose. That object is null at prestart by
   * construction — it is only set once a session starts — so deriving the cap from it made
   * `maxChallengeAdditionalNotes` permanently 0, which disabled every source button and meant the
   * capability could not be reached on any plan. The in-progress response carries the value and was
   * being discarded. This mirrors what the Long Exam page already does for the same reason.
   */
  const [prestartMaxSourceNotes, setPrestartMaxSourceNotes] = useState<number | null>(null);
  /**
   * The viewer's profile type as the account reports it, unnarrowed.
   *
   * ⚠️ `viewerProfileType` runs through `isChallengeViewerProfileType`, which deliberately excludes
   * PARENT — so it cannot represent the one profile whose plan CTA resolves to "challenge" on PRO.
   * Passing the narrowed value to `resolvePlanPremiumExamMode` silently returns null for a PRO parent
   * and routes them to Board Exam setup under a CTA reading "Start Challenge Quiz". The resolver must
   * see what the CTA saw.
   */
  const [viewerProfileTypeRaw, setViewerProfileTypeRaw] = useState<ProfileType | null>(null);
  const [selectedChallengeAdditionalStudyPackIds, setSelectedChallengeAdditionalStudyPackIds] = useState<string[]>([]);
  const [sourceNotesLoading, setSourceNotesLoading] = useState(false);
  const [sourceNotesError, setSourceNotesError] = useState<string | null>(null);
  /**
   * ⚠️ Whether the launch collection actually RESOLVED — not merely whether a collectionId is present.
   * The plan lookup falls back silently on failure, so gating the Board Exam picker on the id alone hid
   * it on the degraded path too, stranding the learner with no way to choose sources. Same distinction
   * v0.105.0 needed for Long Exam.
   */
  const [boardExamPlanScoped, setBoardExamPlanScoped] = useState(false);
  const [forfeitingExistingSession, setForfeitingExistingSession] = useState(false);
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
  const hasRedoMissedEntryQuery = useMemo(
    () => isRedoMissedChallengeQuizEntry(searchParams.get(CHALLENGE_QUIZ_ENTRY_QUERY_PARAM)),
    [searchParams],
  );
  const collectionId = useMemo(() => searchParams.get("collectionId")?.trim() || null, [searchParams]);
  const [sharedModeSelectionEntryRequested, setSharedModeSelectionEntryRequested] = useState(hasModeSelectionEntryQuery);
  const [redoMissedEntryRequested, setRedoMissedEntryRequested] = useState(hasRedoMissedEntryQuery);
  const [currentLearnerLevel, setCurrentLearnerLevel] = useState<LearnerLevel | null>(null);
  const [weeklyPacingWeeksRemaining, setWeeklyPacingWeeksRemaining] = useState<number | null>(null);
  const [primaryCollectionId, setPrimaryCollectionId] = useState<string | null>(null);
  const [primaryCollectionCompanion, setPrimaryCollectionCompanion] = useState<CompanionContent | null>(null);
  const [savingLearnerLevel, setSavingLearnerLevel] = useState(false);
  const [learnerLevelToast, setLearnerLevelToast] = useState<string | null>(null);
  const [generateMoreToast, setGenerateMoreToast] = useState<string | null>(null);
  const noteDetailHref = useMemo(() => (note ? `/notes/${note.id}` : "/library"), [note]);
  const planBackHref = collectionId ? `/collections/${collectionId}` : noteDetailHref;
  const planBackLabel = collectionId ? getCollectionLabels(getAuthUser()?.profileType).singular : "Note";
  const currentPlan = usageSummary?.plan ?? viewerPlanType ?? "FREE";
  const hasNextStepGuidance = nextStepResponse !== null || weeklyPacingWeeksRemaining !== null;
  const hasCompanionExcerpt = hasCompanionResultBridgeExcerpt(primaryCollectionCompanion);
  const hasTwiceMissedCompanionGuidance = shouldRenderTwiceMissedCta(
    result?.twiceMissedConcepts ?? [],
    currentPlan,
    primaryCollectionId,
    primaryCollectionCompanion,
  );
  const hasCompanionGuidance = hasCompanionExcerpt || hasTwiceMissedCompanionGuidance;
  const groupedLearnerLevels = useMemo(
    () => getGroupedLearnerLevels(viewerProfileType as Parameters<typeof getGroupedLearnerLevels>[0]),
    [viewerProfileType],
  );
  const syncProgressRef = useCallback((
    nextIndex: number,
    nextSelectedChoices: Record<number, number>,
    nextSelectedMultiChoices: Record<number, number[]> = progressRef.current.selectedMultiChoices,
    nextSelectedIdentificationAnswers: Record<number, string> = progressRef.current.selectedIdentificationAnswers,
    nextSelectedEnumerationAnswers: Record<number, string[]> = progressRef.current.selectedEnumerationAnswers,
  ) => {
    progressRef.current = {
      currentIndex: nextIndex,
      selectedChoices: nextSelectedChoices,
      selectedMultiChoices: nextSelectedMultiChoices,
      selectedIdentificationAnswers: nextSelectedIdentificationAnswers,
      selectedEnumerationAnswers: nextSelectedEnumerationAnswers,
    };
  }, []);
  const openLockedFeaturePaywall = useCallback(
    (variant: ChallengePaywallVariant, source: string) => {
      const feature = variant === "challenge-quiz-limit"
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
      setViewerProfileTypeRaw((authUser?.profileType as ProfileType | undefined) ?? null);
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
    if (!hasRedoMissedEntryQuery) {
      return;
    }
    setRedoMissedEntryRequested(true);
  }, [hasRedoMissedEntryQuery]);

  useEffect(() => {
    if (!hasRedoMissedEntryQuery) {
      return;
    }
    const nextSearchParams = new URLSearchParams(searchParams.toString());
    nextSearchParams.delete(CHALLENGE_QUIZ_ENTRY_QUERY_PARAM);
    router.replace(nextSearchParams.size > 0 ? `${pathname}?${nextSearchParams.toString()}` : pathname, { scroll: false });
  }, [hasRedoMissedEntryQuery, pathname, router, searchParams]);

  useEffect(() => {
    if (!sharedModeSelectionEntryRequested || phase !== "prestart" || challengeSession?.sessionId || resumeCandidate) {
      return;
    }
    // ⚠️ Derived from the SAME resolver the plan CTA uses, not re-derived from plan alone. A PRO
    // learner on a PARENT profile resolves to "challenge", and re-deriving here sent them to Board Exam
    // setup under a CTA reading "Start Challenge Quiz" — the page contradicting the button that opened it.
    const collectionStartsWithChallenge = collectionId !== null
        && resolvePlanPremiumExamMode(viewerProfileTypeRaw, viewerPlanType) === "challenge";
    setSelectedMode(collectionStartsWithChallenge ? CHALLENGE_MODE : collectionId ? BOARD_EXAM_MODE : resolvePreferredChallengeMode(viewerProfileType));
    setPrestartStep(collectionStartsWithChallenge ? "challenge-setup" : collectionId ? "board-exam-setup" : resolveInitialPrestartStep(viewerProfileType));
  }, [challengeSession?.sessionId, collectionId, phase, resumeCandidate, sharedModeSelectionEntryRequested, viewerPlanType, viewerProfileType, viewerProfileTypeRaw]);

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
      if (me.primaryCollectionId) {
        setPrimaryCollectionId(me.primaryCollectionId);
        setPrimaryCollectionCompanion(null);
        void getCollectionGoal(me.primaryCollectionId)
          .then((goal) => {
            setWeeklyPacingWeeksRemaining(goal.weeksRemaining);
            setPrimaryCollectionCompanion(goal.companion);
          })
          .catch(() => {
            setPrimaryCollectionId(null);
            setPrimaryCollectionCompanion(null);
          });
      } else {
        setPrimaryCollectionId(null);
        setPrimaryCollectionCompanion(null);
      }
    }).catch(() => {
      setPrimaryCollectionId(null);
      setPrimaryCollectionCompanion(null);
    });
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
    setBoardExamPlanScoped(false);
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
          setSelectedChallengeAdditionalStudyPackIds((current) => {
            const availableStudyPackIds = new Set(collectionSourceNotes.map((sourceNote) => sourceNote.studyPackId).filter(Boolean));
            return current.filter((studyPackId) => availableStudyPackIds.has(studyPackId));
          });
          setBoardExamPlanScoped(true);
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
      setSelectedChallengeAdditionalStudyPackIds([]);
      setSourceNotesError("Could not load same-subject notes.");
    } finally {
      setSourceNotesLoading(false);
    }
  }, [collectionId]);

  const applyStartedSession = useCallback((started: ChallengeQuizStartResponse, forceRunning = false) => {
    timeoutAutoSubmitRequestedRef.current = false;
    redoMissedStartRequestedRef.current = false;
    setShowIncompleteSubmitModal(false);
    setNextStepResponse(null);
    setSelectedMode(started.mode ?? CHALLENGE_MODE);
    setBoardExamUsedThisMonth(started.boardExamUsedThisMonth ?? 0);
    setBoardExamMonthlyLimit(started.boardExamMonthlyLimit ?? 0);

    if (started.status === "GENERATING") {
      syncProgressRef(0, {}, {}, {}, {});
      setChallengeSession(started);
      setResult(null);
      setError(null);
      setSelectedChoices({});
      setSelectedMultiChoices({});
      setSelectedIdentificationAnswers({});
      setSelectedEnumerationAnswers({});
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
      syncProgressRef(0, {}, {}, {}, {});
      setChallengeSession(started);
      setResult(null);
      setSelectedChoices({});
      setSelectedMultiChoices({});
      setSelectedIdentificationAnswers({});
      setSelectedEnumerationAnswers({});
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
      syncProgressRef(0, {}, {}, {}, {});
      setChallengeSession(started);
      setResult(null);
      setError(null);
      setSelectedChoices({});
      setSelectedMultiChoices({});
      setSelectedIdentificationAnswers({});
      setSelectedEnumerationAnswers({});
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
    const restoredIdentificationAnswers = toSelectedIdentificationAnswerRecord(state.selectedIdentificationAnswers, started.quiz);
    const restoredEnumerationAnswers = toSelectedEnumerationAnswersRecord(state.selectedEnumerationAnswers, started.quiz);
    const normalizedIndex = Math.max(0, Math.min(started.currentQuestionIndex ?? 0, Math.max(0, started.quiz.length - 1)));
    const nextDeadlineEpochSeconds = resolveDeadlineEpochSeconds(
      started.timeLimitSeconds,
      state,
      getNowEpochSeconds(),
    );
    syncProgressRef(normalizedIndex, restoredChoices, restoredMultiChoices, restoredIdentificationAnswers, restoredEnumerationAnswers);

    setChallengeSession(started);
    setResult(null);
    setError(null);
    setSelectedChoices(restoredChoices);
    setSelectedMultiChoices(restoredMultiChoices);
    setSelectedIdentificationAnswers(restoredIdentificationAnswers);
    setSelectedEnumerationAnswers(restoredEnumerationAnswers);
    setCurrentIndex(normalizedIndex);
    setDeadlineEpochSeconds(nextDeadlineEpochSeconds);
    setRemainingSeconds(resolveRemainingSecondsFromDeadline(nextDeadlineEpochSeconds, getNowEpochSeconds()));
    setTimedOut(false);
    setShowAnswerReview(false);
    setPhase(forceRunning || Boolean(started.sessionId) ? "running" : "prestart");
  }, [syncProgressRef]);

  const resetToPrestart = useCallback((mode = challengeSession?.mode ?? selectedMode) => {
    timeoutAutoSubmitRequestedRef.current = false;
    syncProgressRef(0, {}, {}, {}, {});
    setChallengeSession(null);
    setResumeCandidate(null);
    setResult(null);
    setSelectedChoices({});
    setSelectedMultiChoices({});
    setSelectedIdentificationAnswers({});
    setSelectedEnumerationAnswers({});
    setCurrentIndex(0);
    setDeadlineEpochSeconds(null);
    setRemainingSeconds(0);
    setTimedOut(false);
    setError(null);
    setShowAnswerReview(false);
    setShowBoardExamStartModal(false);
    setShowIncompleteSubmitModal(false);
    setGeneratingMore(false);
    setNoMoreQuestions(false);
    setGenerateMoreToast(null);
    setNextStepResponse(null);
    setPrestartStep(resolveRecoveryPrestartStep(mode));
    setPhase("prestart");
  }, [challengeSession?.mode, selectedMode, syncProgressRef]);

  const persistProgress = useCallback(
    (
      nextIndex: number,
      nextSelectedChoices: Record<number, number>,
      nextSelectedMultiChoices: Record<number, number[]> = progressRef.current.selectedMultiChoices,
      nextSelectedIdentificationAnswers: Record<number, string> = progressRef.current.selectedIdentificationAnswers,
      nextSelectedEnumerationAnswers: Record<number, string[]> = progressRef.current.selectedEnumerationAnswers,
      keepalive = false,
    ) => {
      if (!challengeSession?.sessionId) {
        return;
      }

      const sessionState = {
        selectedChoices: serializeSelectedChoiceIndexRecord(nextSelectedChoices),
        selectedMultiChoices: serializeSelectedMultiChoiceIndicesRecord(nextSelectedMultiChoices),
        selectedIdentificationAnswers: serializeSelectedIdentificationAnswerRecord(nextSelectedIdentificationAnswers),
        selectedEnumerationAnswers: serializeSelectedEnumerationAnswersRecord(nextSelectedEnumerationAnswers),
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
      const resolvedViewerPlanType = authUser?.planType === "FREE" || authUser?.planType === "PLUS" || authUser?.planType === "PRO"
        ? authUser.planType
        : null;
      const collectionStartsWithChallenge = collectionId !== null
        && resolvePlanPremiumExamMode(
          (authUser?.profileType as ProfileType | undefined) ?? null,
          resolvedViewerPlanType,
        ) === "challenge";
      const requestedPrestartMode = collectionStartsWithChallenge ? CHALLENGE_MODE : collectionId ? BOARD_EXAM_MODE : preferredMode;
      const requestedPrestartStep: ChallengePrestartStep = collectionStartsWithChallenge
        ? "challenge-setup"
        : collectionId ? "board-exam-setup" : resolveInitialPrestartStep(authUser?.profileType);
      setIsEmailVerified(Boolean(authUser?.emailVerifiedAt));
      setViewerPlanType(resolvedViewerPlanType);
      setViewerProfileType(isChallengeViewerProfileType(authUser?.profileType) ? authUser.profileType : null);
      if (!authUser?.emailVerifiedAt) {
        syncProgressRef(0, {}, {}, {}, {});
        setChallengeSession(null);
        setResumeCandidate(null);
        setResult(null);
        setSelectedChoices({});
        setSelectedMultiChoices({});
        setSelectedIdentificationAnswers({});
        setSelectedEnumerationAnswers({});
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
      setPrestartMaxSourceNotes(inProgress.maxSourceNotes ?? null);
      const isExpiredInProgressSession = inProgress.status === "IN_PROGRESS"
        && isChallengeQuizSessionExpired(inProgress);
      if (sharedModeSelectionEntryRequested) {
        if (isExpiredInProgressSession && inProgress.sessionId) {
          try {
            await forfeitChallengeQuizSession(inProgress.sessionId);
          } catch {
            // Treat expired sessions as gone locally even if the cleanup request fails.
          }
        }
        const hasResumeCandidate = Boolean(
          inProgress.sessionId && inProgress.status === "IN_PROGRESS" && !isExpiredInProgressSession,
        );
        syncProgressRef(0, {}, {}, {}, {});
        setChallengeSession(null);
        setResumeCandidate(hasResumeCandidate ? inProgress : null);
        setResult(null);
        setSelectedChoices({});
        setSelectedMultiChoices({});
        setSelectedIdentificationAnswers({});
        setSelectedEnumerationAnswers({});
        setCurrentIndex(0);
        setDeadlineEpochSeconds(null);
        setRemainingSeconds(0);
        setTimedOut(false);
        setShowAnswerReview(false);
        setSelectedMode(requestedPrestartMode);
        // A live resumable session must always surface the Resume/Start Fresh
        // choice, even for profiles (e.g. TEACHER) whose initial step otherwise
        // skips mode-selection entirely.
        setPrestartStep(hasResumeCandidate ? "mode-selection" : requestedPrestartStep);
        setChallengeQuizLimitReached(inProgress.usedThisMonth >= inProgress.monthlyLimit);
        setPhase("prestart");
        setActivePaywallModal(null);
        return;
      }

      setSelectedMode(inProgress.mode ?? preferredMode);
      if (isExpiredInProgressSession && inProgress.sessionId) {
        try {
          await forfeitChallengeQuizSession(inProgress.sessionId);
        } catch {
          // Treat expired sessions as gone locally even if the cleanup request fails.
        }
      }
      if (inProgress.sessionId && !isExpiredInProgressSession) {
        setActivePaywallModal(null);
        setResumeCandidate(null);
        applyStartedSession(inProgress, true);
      } else {
        syncProgressRef(0, {}, {}, {}, {});
        setChallengeSession(null);
        setResumeCandidate(null);
        setResult(null);
        setSelectedChoices({});
        setSelectedMultiChoices({});
        setSelectedIdentificationAnswers({});
        setSelectedEnumerationAnswers({});
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
  const answeredQuestionIndexes = useMemo(() => new Set([
    ...Object.keys(selectedChoices).map(Number),
    ...Object.entries(selectedMultiChoices)
      .filter(([, value]) => value.length > 0)
      .map(([key]) => Number(key)),
    ...Object.entries(selectedIdentificationAnswers)
      .filter(([, value]) => value.trim().length > 0)
      .map(([key]) => Number(key)),
    ...Object.entries(selectedEnumerationAnswers)
      .filter(([, values]) => values.some((value) => value.trim().length > 0))
      .map(([key]) => Number(key)),
  ]), [selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers]);
  const answeredCount = answeredQuestionIndexes.size;
  const unansweredCount = Math.max(0, totalQuestions - answeredCount);
  const firstUnansweredQuestionIndex = useMemo(
    () => quiz.findIndex((_, index) => !answeredQuestionIndexes.has(index)),
    [answeredQuestionIndexes, quiz],
  );
  const activeMode = challengeSession?.mode ?? selectedMode;
  const isBoardExamMode = activeMode === BOARD_EXAM_MODE;
  const activeSourceNoteRefs = challengeSession?.sourceNoteRefs ?? [];
  const selectedBoardExamSourceCount = 1 + selectedBoardExamAdditionalStudyPackIds.length;
  // Additional sources exclude the primary. Prefer the live session's value once one exists; before
  // that, use the prestart value from the in-progress read. Never fall back to a client-side constant —
  // the cap is server-derived and a local default would be the drift this release exists to remove.
  const resolvedMaxSourceNotes = challengeSession?.maxSourceNotes ?? prestartMaxSourceNotes;
  const maxChallengeAdditionalNotes = resolvedMaxSourceNotes === null
    ? 0
    : Math.max(0, resolvedMaxSourceNotes - 1);
  const selectedChallengeSourceCount = 1 + selectedChallengeAdditionalStudyPackIds.length;
  const boardExamRemaining = Math.max(0, boardExamMonthlyLimit - boardExamUsedThisMonth);
  const boardExamLimitReached = boardExamMonthlyLimit > 0 && boardExamUsedThisMonth >= boardExamMonthlyLimit;
  const boardExamUpgradeCtas = getUpgradeCtas((viewerPlanType ?? "FREE") as AppPlanType);
  const currentQuestion = totalQuestions > 0 && currentIndex < totalQuestions ? quiz[currentIndex] : null;
  const currentMatchingGroup = !isBoardExamMode ? resolveQuizItemGroupAt(quiz, currentIndex) : null;
  const selectedChoiceIndex = selectedChoices[currentIndex] ?? null;
  const selectedMultiChoiceIndices = selectedMultiChoices[currentIndex] ?? [];
  const selectedIdentificationAnswer = selectedIdentificationAnswers[currentIndex] ?? "";
  const selectedEnumerationAnswer = selectedEnumerationAnswers[currentIndex] ?? [];
  const isIdentificationQuestion = !isBoardExamMode && currentQuestion?.questionFormat === "IDENTIFICATION";
  const isEnumerationQuestion = !isBoardExamMode && currentQuestion?.questionFormat === "ENUMERATION";
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

  const toggleChallengeAdditionalSource = useCallback((studyPackIdToToggle: string) => {
    setSelectedChallengeAdditionalStudyPackIds((current) => {
      if (current.includes(studyPackIdToToggle)) {
        return current.filter((studyPackIdValue) => studyPackIdValue !== studyPackIdToToggle);
      }
      if (current.length >= maxChallengeAdditionalNotes) {
        return current;
      }
      return [...current, studyPackIdToToggle];
    });
  }, [maxChallengeAdditionalNotes]);

  useEffect(() => {
    progressRef.current = {
      currentIndex,
      selectedChoices,
      selectedMultiChoices,
      selectedIdentificationAnswers,
      selectedEnumerationAnswers,
    };
  }, [currentIndex, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers]);

  useEffect(() => {
    remainingSecondsRef.current = remainingSeconds;
  }, [remainingSeconds]);

  const persistLatestProgress = useCallback((keepalive = false) => {
    const latest = progressRef.current;
    persistProgress(
      latest.currentIndex,
      latest.selectedChoices,
      latest.selectedMultiChoices,
      latest.selectedIdentificationAnswers,
      latest.selectedEnumerationAnswers,
      keepalive,
    );
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
    const latestSelectedIdentificationAnswers = progressRef.current.selectedIdentificationAnswers;
    const latestSelectedEnumerationAnswers = progressRef.current.selectedEnumerationAnswers;
    const { correctAnswers, totalQuestions: total } = computeScore(
      activeSession.quiz,
      latestSelectedChoices,
      latestSelectedMultiChoices,
      latestSelectedIdentificationAnswers,
      latestSelectedEnumerationAnswers,
    );
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

  const handleManualSubmit = useCallback(() => {
    if (unansweredCount === 0) {
      void handleSubmit(false);
      return;
    }
    setShowIncompleteSubmitModal(true);
  }, [handleSubmit, unansweredCount]);

  const handleReturnToFirstUnansweredQuestion = useCallback(() => {
    setShowIncompleteSubmitModal(false);
    if (firstUnansweredQuestionIndex < 0) {
      return;
    }
    syncProgressRef(
      firstUnansweredQuestionIndex,
      selectedChoices,
      selectedMultiChoices,
      selectedIdentificationAnswers,
      selectedEnumerationAnswers,
    );
    setCurrentIndex(firstUnansweredQuestionIndex);
    persistProgress(
      firstUnansweredQuestionIndex,
      selectedChoices,
      selectedMultiChoices,
      selectedIdentificationAnswers,
      selectedEnumerationAnswers,
    );
  }, [
    firstUnansweredQuestionIndex,
    persistProgress,
    selectedChoices,
    selectedEnumerationAnswers,
    selectedIdentificationAnswers,
    selectedMultiChoices,
    syncProgressRef,
  ]);

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

  const handleStartChallenge = useCallback(async (modeOverride?: ChallengeQuizMode, redoMissed = false) => {
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
      const request: ChallengeQuizStartRequest = { mode: nextMode };
      if (nextMode === BOARD_EXAM_MODE) {
        // ⚠️ A REVIEW-SET BOARD EXAM IS THE DEFAULT WHENEVER WE ARRIVED FROM A COLLECTION, AND IT NO LONGER
        // DEPENDS ON THE LEARNER PICKING NOTES. Previously sourceCollectionId was sent ONLY alongside a
        // picked list — and the server samples the set and now rejects that list — so the single route
        // into this capability was the one that invalidated itself. The server resolves the Review Set
        // from whatever collection we came from (walking up from a Subject Plan), so it needs no picks.
        // ⚠️ GATE ON A RESOLVED PLAN, NOT ON A PRESENT ID — the same distinction the picker uses.
        // Sending sourceCollectionId whenever an id was in the URL removed the degradation path entirely:
        // if the plan could not be loaded, or holds fewer than two ready Study Packs, the server now
        // rejects and the learner cannot start a Board Exam AT ALL, where before they simply got a
        // single-note one. It also made the picker decorative on that path — visible, and its picks
        // dropped, because this branch ignored them whenever an id was present.
        if (boardExamPlanScoped && collectionId) {
          request.sourceCollectionId = collectionId;
        } else if (selectedBoardExamAdditionalStudyPackIds.length > 0) {
          // No collection context: the legacy manual multi-note Board Exam, unchanged.
          request.additionalStudyPackIds = selectedBoardExamAdditionalStudyPackIds;
        }
      }
      if (nextMode === CHALLENGE_MODE && selectedChallengeAdditionalStudyPackIds.length > 0) {
        request.additionalStudyPackIds = selectedChallengeAdditionalStudyPackIds;
        if (collectionId) {
          request.sourceCollectionId = collectionId;
        }
      }
      const started = redoMissed
        ? await startRedoMissedChallengeQuizSession(note.id)
        : await startChallengeQuizSession(note.id, request);
      if (!started.sessionId) {
        throw new Error(nextMode === BOARD_EXAM_MODE ? "Could not start Board Exam Mode." : "Could not start Challenge Quiz.");
      }
      applyStartedSession(started, true);
    } catch (err) {
      if (redoMissed) {
        redoMissedStartRequestedRef.current = false;
      }
      const message = isEmailNotVerifiedError(err)
        ? "Verify your email to use this feature."
        : err instanceof Error
          ? err.message
          : nextMode === BOARD_EXAM_MODE
            ? "Could not start Board Exam Mode."
            : "Could not start Challenge Quiz.";
      if (redoMissed && isNotEnoughMissedChallengeQuestionsError(err)) {
        setRedoMissedEntryRequested(false);
        resetToPrestart(CHALLENGE_MODE);
        setError(message);
        return;
      }
      if (isEmailNotVerifiedError(err)) {
        setShowVerifyEmailModal(true);
      }
      setError(message);
      // ⚠️ Matched on the error CODE, not the message. As delivered this checked the message for
      // "monthly multi-note challenge quiz limit", which the exception never says — its text is
      // "You've used all N multi-note Challenge Quiz sessions in this billing period." So the multi-note
      // ceiling matched NEITHER branch and produced a bare error with no upgrade path, silently. The
      // other two limits are left on message-matching because their strings genuinely do match today.
      if (err instanceof ApiRequestError && err.code === "MONTHLY_MULTI_NOTE_LIMIT_REACHED") {
        setPhase("prestart");
        openLockedFeaturePaywall("challenge-quiz-limit", "multi_note_challenge_quiz_start");
      } else if (message.toLowerCase().includes("monthly challenge quiz limit")) {
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
  }, [applyStartedSession, collectionId, isEmailVerified, note, openLockedFeaturePaywall, resetToPrestart, selectedBoardExamAdditionalStudyPackIds, selectedChallengeAdditionalStudyPackIds, selectedMode, viewerPlanType]);

  useEffect(() => {
    if (
      !redoMissedEntryRequested
      || loading
      || !note
      || redoMissedStartRequestedRef.current
    ) {
      return;
    }
    if (phase !== "prestart" || challengeSession?.sessionId) {
      resetToPrestart(CHALLENGE_MODE);
      return;
    }
    redoMissedStartRequestedRef.current = true;
    setRedoMissedEntryRequested(false);
    void handleStartChallenge(CHALLENGE_MODE, true);
  }, [challengeSession?.sessionId, handleStartChallenge, loading, note, phase, redoMissedEntryRequested, resetToPrestart]);

  const handleRetry = () => {
    resetToPrestart();
  };

  const handleResumeExistingSession = () => {
    if (!resumeCandidate) {
      return;
    }
    setResumeCandidate(null);
    applyStartedSession(resumeCandidate, true);
  };

  const handleStartFreshExistingSession = async () => {
    if (!resumeCandidate?.sessionId || forfeitingExistingSession) {
      return;
    }
    setForfeitingExistingSession(true);
    setError(null);
    try {
      await forfeitChallengeQuizSession(resumeCandidate.sessionId);
      resetToPrestart(resumeCandidate.mode ?? selectedMode);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not forfeit the active session. Please try again.");
    } finally {
      setForfeitingExistingSession(false);
    }
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
      syncProgressRef(
        nextIndex,
        progressRef.current.selectedChoices,
        progressRef.current.selectedMultiChoices,
        progressRef.current.selectedIdentificationAnswers,
        progressRef.current.selectedEnumerationAnswers,
      );
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
  useBottomViewportClaim(challengeQuizActive);
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
            {resumeCandidate ? (
              <div className="space-y-4 rounded-xl border border-border bg-background p-4">
                <div className="space-y-1">
                  <p className="font-medium text-foreground">You have an active quiz session.</p>
                  <p className="text-sm text-foreground/70">
                    Resume where you left off, or start fresh to forfeit this session and choose a new quiz mode.
                  </p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button type="button" className="w-full sm:w-auto" onClick={handleResumeExistingSession}>
                    Resume
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    className="w-full sm:w-auto"
                    onClick={() => void handleStartFreshExistingSession()}
                    disabled={forfeitingExistingSession}
                  >
                    {forfeitingExistingSession ? "Starting..." : "Start Fresh"}
                  </Button>
                </div>
                {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
              </div>
            ) : (
              <>
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
                    Review the recommended setup before you start.
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
              </>
            )}
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
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Timer</p>
                  <p>10 minutes. Timer runs until submission or expiration.</p>
                </div>
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Question count</p>
                  <p>Recommended based on your recent performance.</p>
                </div>
                <div className="space-y-1">
                  <p className="font-medium text-foreground">Monthly limit</p>
                  <p>Counts toward your monthly quiz limit.</p>
                </div>
              </div>
            </div>
            {collectionId ? (
              <div className="rounded-xl border border-border bg-background p-4">
                <h2 className="text-sm font-semibold text-foreground">Practise across this plan</h2>
                <p className="mt-1 text-sm text-foreground/70">
                  {resolvedMaxSourceNotes
                    ? `This quiz can cover up to ${resolvedMaxSourceNotes} notes from this plan’s ${availableBoardExamSourceNotes.length + 1} ready notes.`
                    : "Choose extra notes from this plan when they load."}
                </p>
                {sourceNotesLoading ? (
                  <p className="mt-3 text-sm text-foreground/60">Loading plan notes…</p>
                ) : sourceNotesError ? (
                  <p className="mt-3 text-sm text-foreground/60">Could not load plan notes. Single-note Challenge Quiz is still available.</p>
                ) : availableBoardExamSourceNotes.length > 0 ? (
                  <div className="mt-3 grid gap-2">
                    {availableBoardExamSourceNotes.map((sourceNote) => {
                      const sourceStudyPackId = sourceNote.studyPackId ?? "";
                      const selected = selectedChallengeAdditionalStudyPackIds.includes(sourceStudyPackId);
                      const capped = !selected && selectedChallengeAdditionalStudyPackIds.length >= maxChallengeAdditionalNotes;
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
                          onClick={() => toggleChallengeAdditionalSource(sourceStudyPackId)}
                          disabled={capped}
                        >
                          <span className="block text-sm font-medium text-foreground">{sourceNote.title ?? "Untitled note"}</span>
                          <span className="block text-xs text-foreground/60">{sourceNote.subject}</span>
                        </button>
                      );
                    })}
                    <p className="text-xs text-foreground/60">
                      {selectedChallengeSourceCount} {selectedChallengeSourceCount === 1 ? "note" : "notes"} selected
                    </p>
                  </div>
                ) : (
                  <p className="mt-3 text-sm text-foreground/60">No other ready notes in this plan yet. Single-note Challenge Quiz is still available.</p>
                )}
              </div>
            ) : null}
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
                  {boardExamPlanScoped ? "Exam coverage" : "Span this exam across more notes"}
                </h2>
                <p className="text-sm text-foreground/70">
                  {boardExamPlanScoped
                    ? "This exam is sampled across your whole Review Set, spread over its subjects."
                    : `Add up to ${BOARD_EXAM_MAX_ADDITIONAL_NOTES} ready Study Packs from this subject.`}
                </p>
              </div>
              {/* ⚠️ NO PICKER WHEN THE SERVER SAMPLES. With a collection in context a Board Exam is drawn
                  across the whole Review Set and any picked list is neither sent nor accepted, so a
                  selector here would be a control that silently does nothing — the decorative-control
                  defect fixed for Long Exam in v0.105.0. Multi-note CHALLENGE keeps its picker: that is a
                  learner-chosen mode and its selection is still honoured. */}
              {boardExamPlanScoped ? null : sourceNotesLoading ? (
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
              Your starting question set adapts to your recent performance. Generate more as you go (up to {MAX_SESSION_QUESTIONS}).
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
                        syncProgressRef(currentIndex, next, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
                        persistProgress(currentIndex, next, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
                        return next;
                      });
                    }}
                  />
                ) : (
                  <>
                    <h2 className="text-lg font-semibold leading-7 sm:text-xl"><QuizQuestionText text={currentQuestion.question} /></h2>
                    {isIdentificationQuestion ? (
                      <QuizIdentificationInput
                        item={currentQuestion}
                        value={selectedIdentificationAnswer}
                        revealAnswer={false}
                        disabled={quizInteractionDisabled}
                        selectionStyle="exam"
                        onChangeAnswer={(answerText) => {
                          setSelectedIdentificationAnswers((previous) => {
                            const next = { ...previous };
                            if (answerText.trim().length > 0) {
                              next[currentIndex] = answerText;
                            } else {
                              delete next[currentIndex];
                            }
                            syncProgressRef(currentIndex, selectedChoices, selectedMultiChoices, next, selectedEnumerationAnswers);
                            persistProgress(currentIndex, selectedChoices, selectedMultiChoices, next, selectedEnumerationAnswers);
                            return next;
                          });
                        }}
                      />
                    ) : isEnumerationQuestion ? (
                      <QuizEnumerationInput
                        item={currentQuestion}
                        values={selectedEnumerationAnswer}
                        revealAnswer={false}
                        disabled={quizInteractionDisabled}
                        selectionStyle="exam"
                        onChangeAnswers={(answers) => {
                          setSelectedEnumerationAnswers((previous) => {
                            const next = { ...previous };
                            if (answers.some((answer) => answer.trim().length > 0)) {
                              next[currentIndex] = answers;
                            } else {
                              delete next[currentIndex];
                            }
                            syncProgressRef(currentIndex, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, next);
                            persistProgress(currentIndex, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, next);
                            return next;
                          });
                        }}
                      />
                    ) : (
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
                            syncProgressRef(currentIndex, next, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
                            persistProgress(currentIndex, next, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
                            return next;
                          });
                        }}
                        onSelectMultiChoices={(choiceIndices) => {
                          if (isBoardExamMode) {
                            return;
                          }
                          setSelectedMultiChoices((previous) => {
                            const next = { ...previous, [currentIndex]: choiceIndices };
                            syncProgressRef(currentIndex, progressRef.current.selectedChoices, next, selectedIdentificationAnswers, selectedEnumerationAnswers);
                            persistProgress(currentIndex, progressRef.current.selectedChoices, next, selectedIdentificationAnswers, selectedEnumerationAnswers);
                            return next;
                          });
                        }}
                      />
                    )}
                  </>
                )}
                <p className="text-xs text-foreground/65">Answers are graded only after submission.</p>
                {!isBoardExamMode ? (
                  <p className="text-xs text-foreground/50">You can finish anytime. Score is based on answered questions.</p>
                ) : null}
                <QuestionNavigator
                  total={totalQuestions}
                  currentIndex={currentIndex}
                  isAnswered={(index) => answeredQuestionIndexes.has(index)}
                  onSelect={(index) => {
                    syncProgressRef(index, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
                    setCurrentIndex(index);
                    persistProgress(index, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
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
                  syncProgressRef(nextIndex, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
                  setCurrentIndex(nextIndex);
                  persistProgress(nextIndex, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
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
                      syncProgressRef(nextIndex, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
                      setCurrentIndex(nextIndex);
                      persistProgress(nextIndex, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
                    }}
                    disabled={quizInteractionDisabled || Boolean(currentMatchingGroup && !currentMatchingGroupAnswered)}
                  >
                    Next
                  </Button>
                ) : isBoardExamMode ? (
                  <Button
                    type="button"
                    className="flex-1 sm:w-auto sm:flex-none"
                    onClick={handleManualSubmit}
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
                      onClick={handleManualSubmit}
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
            <ReviewCommitmentPrompt
              isFirstCompletedSessionEver={result.isFirstCompletedSessionEver}
              noteId={note?.id ?? null}
            />
            {hasNextStepGuidance ? (
              <ResultGuidanceGroup label="What to do next" testId="board-exam-next-step-guidance">
                <PostSessionNextStep
                  response={nextStepResponse}
                  currentPlan={currentPlan}
                  noteId={note?.id ?? null}
                  onOpenPaywall={() => openLockedFeaturePaywall("adaptive-practice", "board_exam_results_next_step")}
                  originatingQuizMode="BOARD_EXAM"
                  contained
                />
                {nextStepResponse?.goalNudge ? (
                  <GoalNudgeCard goalNudge={nextStepResponse.goalNudge} noteId={note?.id ?? null} contained />
                ) : null}
                <WeeklyPacingEchoCard
                  weeksRemaining={weeklyPacingWeeksRemaining}
                  goalLabel={getCollectionLabels(viewerProfileType).goalSingular}
                  contained
                />
              </ResultGuidanceGroup>
            ) : null}
            {/* Deliberately gated on hasCompanionExcerpt alone, not hasCompanionGuidance: Board
                Exam Mode has never shown the twice-missed CTA, and this branch renders only
                CompanionResultBridgeCard. Switching to hasCompanionGuidance would render an empty
                group whenever a twice-missed concept exists but no excerpt does. */}
            {hasCompanionExcerpt ? (
              <ResultGuidanceGroup label="Companion guidance" testId="board-exam-companion-guidance">
                <CompanionResultBridgeCard
                  companion={primaryCollectionCompanion}
                  reviewSetLabel={getCollectionLabels(viewerProfileType).singular}
                  contained
                />
              </ResultGuidanceGroup>
            ) : null}
            {nextStepResponse === null ? (
              <>
                <div className="rounded-2xl border border-border bg-card p-5 sm:p-6">
                  <h2 className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">Weak Concepts</h2>
                  {result.weakConcepts.length > 0 ? (
                    <div className="mt-4 flex flex-wrap gap-2">
                      {result.weakConcepts.map((concept) => (
                        note?.id && note.keyConcepts?.some((keyConcept) => (
                          normalizeConceptKey(keyConcept) === normalizeConceptKey(concept)
                        )) ? (
                          <Link
                            key={concept}
                            href={`/notes/${note.id}?tab=key-concepts#${buildConceptAnchorId(concept)}`}
                            className="rounded-full border border-amber-600/40 bg-transparent px-3 py-1 text-xs font-medium text-amber-700 dark:text-amber-300"
                          >
                            {concept}
                          </Link>
                        ) : (
                          <span
                            key={concept}
                            className="rounded-full border border-amber-600/40 bg-transparent px-3 py-1 text-xs font-medium text-amber-700 dark:text-amber-300"
                          >
                            {concept}
                          </span>
                        )
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
                    <Link
                      href={note ? buildAdaptivePracticeHref(note.id, {
                        entry: ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY,
                      }) : "/dashboard"}
                      className="w-full sm:w-auto"
                    >
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
              selectedIdentificationAnswers={selectedIdentificationAnswers}
              selectedEnumerationAnswers={selectedEnumerationAnswers}
              className="mt-2"
              planType={viewerPlanType}
              footer={(
                <div className="flex flex-col gap-2 sm:flex-row">
                  {result.weakConcepts.length > 0 && note?.adaptivePracticeAvailable ? (
                    <Link
                      href={buildAdaptivePracticeHref(note.id, {
                        entry: ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY,
                      })}
                      className="w-full sm:w-auto"
                    >
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
            isFirstCompletedSessionEver={result.isFirstCompletedSessionEver}
            isSecondCompletedSessionEver={result.isSecondCompletedSessionEver}
            userId={getAuthUser()?.id}
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
                <ScoreReveal
                  percentage={result.scorePercentage}
                  label={hasUnansweredQuestions ? "Answered Accuracy" : "Score"}
                  supportingLine={`${result.correctAnswers} of ${result.totalQuestions} correct · ${formatTimer(result.durationSeconds ?? 0)}`}
                  performanceLevel={result.performanceLevel}
                  secondaryMetric={hasUnansweredQuestions ? {
                    label: "Overall Completion Score",
                    percentage: overallScorePercentage,
                    description: `${result.correctAnswers} correct of ${totalGenerated} total`,
                  } : undefined}
                  tone="challenge-quiz"
                />
                {hasUnansweredQuestions ? (
                  <p className="text-center text-xs text-foreground/60">
                    Answered Accuracy shows performance on attempted questions. Overall Completion Score counts unanswered questions as incomplete.
                  </p>
                ) : null}
                <p className="text-center text-sm text-foreground/75">
                  {getChallengeResultMessage(result.scorePercentage, activeMode)}
                </p>
                {timedOut ? (
                  <p className="text-center text-sm text-foreground/75">
                    Time ran out. Your answers were submitted automatically.
                  </p>
                ) : null}
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
            <ReviewCommitmentPrompt
              isFirstCompletedSessionEver={result.isFirstCompletedSessionEver}
              noteId={note?.id ?? null}
            />
            {hasNextStepGuidance ? (
              <ResultGuidanceGroup label="What to do next" testId="challenge-quiz-next-step-guidance">
                <PostSessionNextStep
                  response={nextStepResponse}
                  currentPlan={currentPlan}
                  noteId={note?.id ?? null}
                  onOpenPaywall={() => openLockedFeaturePaywall("adaptive-practice", "challenge_quiz_results_next_step")}
                  originatingQuizMode="CHALLENGE"
                  contained
                />
                {nextStepResponse?.goalNudge ? (
                  <GoalNudgeCard goalNudge={nextStepResponse.goalNudge} noteId={note?.id ?? null} contained />
                ) : null}
                <WeeklyPacingEchoCard
                  weeksRemaining={weeklyPacingWeeksRemaining}
                  goalLabel={getCollectionLabels(viewerProfileType).goalSingular}
                  contained
                />
              </ResultGuidanceGroup>
            ) : null}
            {hasCompanionGuidance ? (
              <ResultGuidanceGroup label="Companion guidance" testId="challenge-quiz-companion-guidance">
                <CompanionResultBridgeCard
                  companion={primaryCollectionCompanion}
                  reviewSetLabel={getCollectionLabels(viewerProfileType).singular}
                  contained
                />
                <TwiceMissedAskCompanionCard
                  twiceMissedConcepts={result.twiceMissedConcepts ?? []}
                  currentPlan={currentPlan}
                  primaryCollectionId={primaryCollectionId}
                  companion={primaryCollectionCompanion}
                  contained
                />
              </ResultGuidanceGroup>
            ) : null}
            {nextStepResponse === null ? (
              <>
                <Card className="space-y-3 p-4">
                  <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Weak Concepts</h2>
                  {result.weakConcepts.length > 0 ? (
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/85">
                      {result.weakConcepts.map((concept) => (
                        <li key={concept}>
                          {note?.id && note.keyConcepts?.some((keyConcept) => (
                            normalizeConceptKey(keyConcept) === normalizeConceptKey(concept)
                          )) ? (
                            <Link
                              href={`/notes/${note.id}?tab=key-concepts#${buildConceptAnchorId(concept)}`}
                              className="font-medium text-amber-700 underline underline-offset-4 dark:text-amber-300"
                            >
                              {concept}
                            </Link>
                          ) : concept}
                        </li>
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
                    <Link
                      href={note ? buildAdaptivePracticeHref(note.id, {
                        entry: ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY,
                      }) : "/dashboard"}
                      className="w-full sm:w-auto"
                    >
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
              selectedIdentificationAnswers={selectedIdentificationAnswers}
              selectedEnumerationAnswers={selectedEnumerationAnswers}
              className="mt-2"
              planType={viewerPlanType}
              footer={(
                <div className="flex flex-col gap-2 sm:flex-row">
                  {result.weakConcepts.length > 0 && note?.adaptivePracticeAvailable ? (
                    <Link
                      href={buildAdaptivePracticeHref(note.id, {
                        entry: ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY,
                      })}
                      className="w-full sm:w-auto"
                    >
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
            isFirstCompletedSessionEver={result.isFirstCompletedSessionEver}
            isSecondCompletedSessionEver={result.isSecondCompletedSessionEver}
            userId={getAuthUser()?.id}
          />
        </Card>
      ) : null}

      <AppModal
        isOpen={showIncompleteSubmitModal}
        title="Submit with unanswered questions?"
        onClose={() => {
          if (!submitting) {
            setShowIncompleteSubmitModal(false);
          }
        }}
        contentClassName="space-y-2"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={handleReturnToFirstUnansweredQuestion}
              disabled={submitting}
            >
              Go back
            </Button>
            <Button
              type="button"
              onClick={() => {
                setShowIncompleteSubmitModal(false);
                void handleSubmit(false);
              }}
              disabled={submitting}
            >
              {submitting ? "Submitting..." : "Submit anyway"}
            </Button>
          </div>
        )}
      >
        <p className="text-sm leading-relaxed text-foreground/80">
          You have {unansweredCount} unanswered {unansweredCount === 1 ? "question" : "questions"}.
          You can go back to review them or submit anyway.
        </p>
      </AppModal>
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
