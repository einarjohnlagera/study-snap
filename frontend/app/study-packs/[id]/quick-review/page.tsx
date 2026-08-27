"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { Trophy } from "lucide-react";
import { PaywallModal, type PaywallModalVariant } from "@/components/billing/paywall-modal";
import { QuizFeedbackPanel } from "@/components/feedback/quiz-feedback-panel";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { QuizAnswerReview } from "@/components/study-pack/quiz-answer-review";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { QuizQuestionText } from "@/components/study-pack/quiz-question-text";
import { QuizMatchingGroup } from "@/components/study-pack/quiz-matching-group";
import { GoalNudgeCard } from "@/components/study-pack/goal-nudge-card";
import { PostSessionNextStep } from "@/components/study-pack/post-session-next-step";
import { ReviewCommitmentPrompt } from "@/components/study-pack/review-commitment-prompt";
import { WeeklyPacingEchoCard } from "@/components/study-pack/weekly-pacing-echo-card";
import { CompanionResultBridgeCard, hasCompanionResultBridgeExcerpt } from "@/components/study-pack/companion-result-bridge-card";
import { ResultGuidanceGroup } from "@/components/study-pack/result-guidance-group";
import { shouldRenderTwiceMissedCta, TwiceMissedAskCompanionCard } from "@/components/study-pack/twice-missed-ask-companion-card";
import { useQuizSessionGuard } from "@/components/study-pack/quiz-session-guard";
import { useBottomViewportClaim } from "@/components/exam-mode/exam-focus-context";
import { hasComputationalWorkingSolution, QuizWorkingSolution } from "@/components/study-pack/quiz-working-solution";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { type AppPlanType } from "@/src/config/plans";
import {
  completeProductOnboarding,
  completeQuickReviewSession,
  forfeitQuickReviewSession,
  generateQuickReviewStudyTip,
  getCollectionGoal,
  getMe,
  getMyStudyPack,
  getNote,
  getSharedNote,
  getSharedStudyPack,
  getPostSessionNextStep,
  saveQuickReviewConfidence,
  startQuickReviewSession,
  trackAnalyticsEvent,
  updateProfileLearnerLevel,
  updateQuickReviewSessionProgress,
  type CompanionContent,
  type LearnerLevel,
  type NoteResponse,
  type PostSessionNextStepResponse,
  type ProfileType,
  type QuickReviewConfidenceLevel,
  type QuickReviewSessionSummaryResponse,
  type QuickReviewStudyTipRequest,
} from "@/lib/api";
import { getCollectionLabels } from "@/lib/collection-labels";
import { getGroupedLearnerLevels } from "@/lib/learning-profile";
import { ToastMessage } from "@/components/ui/toast-message";
import { PostSuccessUpgradeNudge } from "@/components/billing/post-success-upgrade-nudge";
import {
  clearFirstStudyOnboardingStep,
  getFirstStudyOnboardingStep,
  hasPendingFirstStudyOnboarding,
} from "@/lib/first-study-onboarding";
import {
  isQuizSelectionCorrect,
  resolveQuizCorrectAnswer,
  resolveQuizCorrectIndex,
  resolveQuizItemGroupAt,
  serializeSelectedChoiceIndexRecord,
  serializeSelectedMultiChoiceIndicesRecord,
  toSelectedChoiceIndexRecord,
  toSelectedMultiChoiceIndicesRecord,
} from "@/lib/quiz";
import { buildConceptAnchorId, normalizeConceptKey } from "@/lib/concepts";
import { cn } from "@/lib/utils";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

type QuickReviewPhase = "initial" | "retry-transition" | "retry" | "complete";
type SessionStatePayload = {
  selectedChoices?: Record<string, number> | Record<string, string>;
  selectedMultiChoices?: Record<string, number[]>;
  retryQuestionIndexes?: number[];
  activeQuestionIndexes?: number[];
  roundSelections?: Record<string, number> | Record<string, string>;
  roundMultiSelections?: Record<string, number[]>;
};

function getMotivationalFeedback(scorePercentage: number) {
  if (scorePercentage >= 100) {
    return "Excellent work! You mastered this topic.";
  }
  if (scorePercentage >= 80) {
    return "Great job! You're very close to mastering this.";
  }
  if (scorePercentage >= 50) {
    return "Good effort. A quick retry can help reinforce what you missed.";
  }
  return "Good effort. Let's review the concepts and try again.";
}

function getPerformanceBadge(scorePercentage: number) {
  if (scorePercentage >= 100) {
    return {
      label: "🏆 Perfect Mastery",
      className: "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
    };
  }
  if (scorePercentage >= 80) {
    return {
      label: "⚡ Strong Understanding",
      className: "border-blue-500/40 bg-blue-500/10 text-blue-700 dark:text-blue-300",
    };
  }
  if (scorePercentage >= 60) {
    return {
      label: "👍 Good Progress",
      className: "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300",
    };
  }
  return {
    label: "📘 Needs Review",
    className: "border-orange-500/40 bg-orange-500/10 text-orange-700 dark:text-orange-300",
  };
}

function QuickReviewLoading() {
  return (
    <Card className="space-y-4">
      <div className="h-4 w-36 animate-pulse rounded bg-foreground/10" />
      <div className="h-6 w-4/5 animate-pulse rounded bg-foreground/10" />
      <div className="space-y-2">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={`quick-review-choice-${index}`} className="h-10 w-full animate-pulse rounded bg-foreground/10" />
        ))}
      </div>
      <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
    </Card>
  );
}

function ScoreProgressBlock({
  score,
  totalQuestions,
  scorePercentage,
}: Readonly<{
  score: number;
  totalQuestions: number;
  scorePercentage: number;
}>) {
  return (
    <div className="space-y-1">
      <p className="text-base font-medium text-foreground">Score: {score} / {totalQuestions} correct</p>
      <p className="font-medium text-foreground">{scorePercentage}%</p>
      <div className="h-2 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-blue-600 transition-all dark:bg-blue-400"
          style={{ width: `${scorePercentage}%` }}
        />
      </div>
    </div>
  );
}

function toNumberArray(value: unknown): number[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is number => typeof item === "number");
}

export default function QuickReviewPage() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionInitializing, setSessionInitializing] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [phase, setPhase] = useState<QuickReviewPhase>("initial");
  const [activeQuestionIndexes, setActiveQuestionIndexes] = useState<number[]>([]);
  const [currentRoundIndex, setCurrentRoundIndex] = useState(0);
  const [retryQuestionIndexes, setRetryQuestionIndexes] = useState<number[]>([]);
  const [roundSelections, setRoundSelections] = useState<Record<number, number>>({});
  const [roundMultiSelections, setRoundMultiSelections] = useState<Record<number, number[]>>({});
  const [selectedChoices, setSelectedChoices] = useState<Record<number, number>>({});
  const [selectedMultiChoices, setSelectedMultiChoices] = useState<Record<number, number[]>>({});
  const [retryCount, setRetryCount] = useState(0);
  const [completionTracked, setCompletionTracked] = useState(false);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [sessionStartedAt, setSessionStartedAt] = useState<number | null>(null);
  const [persistedResult, setPersistedResult] = useState<QuickReviewSessionSummaryResponse | null>(null);
  const [quizMasteredAfterCompletion, setQuizMasteredAfterCompletion] = useState(false);
  const [recentSessions, setRecentSessions] = useState<QuickReviewSessionSummaryResponse[]>([]);
  const [studyTip, setStudyTip] = useState<string | null>(null);
  const [completingSession, setCompletingSession] = useState(false);
  const [confidenceLevel, setConfidenceLevel] = useState<QuickReviewConfidenceLevel | null>(null);
  const [savingConfidence, setSavingConfidence] = useState(false);
  const [confidenceAcknowledged, setConfidenceAcknowledged] = useState(false);
  const [confidenceError, setConfidenceError] = useState<string | null>(null);
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [viewerProfileType, setViewerProfileType] = useState<string | null>(() => getAuthUser()?.profileType ?? null);
  const [viewerPlanType, setViewerPlanType] = useState<string | null>(() => getAuthUser()?.planType ?? null);
  const [activePaywallModal, setActivePaywallModal] = useState<PaywallModalVariant | null>(null);
  const [nextStepResponse, setNextStepResponse] = useState<PostSessionNextStepResponse | null>(null);
  const [showCompletionGuide, setShowCompletionGuide] = useState(false);
  const [showAnswerReview, setShowAnswerReview] = useState(false);
  const [multiSelectSubmitted, setMultiSelectSubmitted] = useState(false);
  const [currentLearnerLevel, setCurrentLearnerLevel] = useState<LearnerLevel | null>(null);
  const [weeklyPacingWeeksRemaining, setWeeklyPacingWeeksRemaining] = useState<number | null>(null);
  const [primaryCollectionId, setPrimaryCollectionId] = useState<string | null>(null);
  const [primaryCollectionCompanion, setPrimaryCollectionCompanion] = useState<CompanionContent | null>(null);
  const [savingLearnerLevel, setSavingLearnerLevel] = useState(false);
  const [learnerLevelToast, setLearnerLevelToast] = useState<string | null>(null);
  const { usageSummary } = useBillingUsageSummary();
  const loadedNoteIdRef = useRef<string | null>(null);
  const legacyRedirectTargetRef = useRef<string | null>(null);
  const openLoopTrackedSessionIdRef = useRef<string | null>(null);
  const digestLandingTrackedRef = useRef(false);
  const digestFirstAnswerTrackedRef = useRef<string | null>(null);

  const noteId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);
  const queryString = useMemo(() => searchParams.toString(), [searchParams]);
  const isSharedSource = useMemo(
    () => new URLSearchParams(queryString).get("source") === "shared",
    [queryString],
  );
  const isDueConceptsDigestVisit = useMemo(
    () => new URLSearchParams(queryString).get("source") === "due-concepts-digest",
    [queryString],
  );

  useEffect(() => {
    if (!isDueConceptsDigestVisit || !noteId || digestLandingTrackedRef.current) {
      return;
    }
    // Dedup across reloads, not just across renders. A plain ref resets on every remount, so a reload
    // or Back-navigation re-fired this -- inflating the denominator of the exact conversion this
    // release exists to measure, on a cohort small enough (~31) for that to matter. sessionStorage is
    // the right scope: one arrival per tab, while a genuinely new email click opens a new one.
    const arrivalKey = `notelib-digest-landed-${noteId}`;
    if (globalThis.sessionStorage?.getItem(arrivalKey)) {
      digestLandingTrackedRef.current = true;
      return;
    }
    digestLandingTrackedRef.current = true;
    globalThis.sessionStorage?.setItem(arrivalKey, "1");
    void trackAnalyticsEvent({
      eventType: "DUE_CONCEPTS_DIGEST_LANDED",
      entityId: noteId,
      metadata: { path: pathname },
    });
  }, [isDueConceptsDigestVisit, noteId, pathname]);

  const trackDigestFirstAnswer = useCallback(() => {
    // Keyed by session id, matching openLoopTrackedSessionIdRef above: once per quiz session rather
    // than once per mount, so a reload mid-session cannot report a second "first answer".
    if (!isDueConceptsDigestVisit || digestFirstAnswerTrackedRef.current === (currentSessionId ?? noteId)) {
      return;
    }
    digestFirstAnswerTrackedRef.current = currentSessionId ?? noteId;
    void trackAnalyticsEvent({
      eventType: "DUE_CONCEPTS_DIGEST_FIRST_ANSWER_SUBMITTED",
      entityId: currentSessionId ?? noteId,
      metadata: { noteId, path: pathname },
    });
  }, [currentSessionId, isDueConceptsDigestVisit, noteId, pathname]);
  const openAdaptivePracticePaywall = useCallback((source: string) => {
    void trackAnalyticsEvent({
      eventType: "FEATURE_LOCKED_CLICKED",
      metadata: {
        feature: "adaptive",
        source,
        path: pathname,
        noteId,
      },
    });
    setActivePaywallModal("adaptive-practice");
  }, [noteId, pathname]);

  const resetQuickReviewState = useCallback((allIndexes: number[]) => {
    setPhase("initial");
    setActiveQuestionIndexes(allIndexes);
    setCurrentRoundIndex(0);
    setRetryQuestionIndexes([]);
    setRoundSelections({});
    setRoundMultiSelections({});
    setSelectedChoices({});
    setSelectedMultiChoices({});
    setRetryCount(0);
    setCompletionTracked(false);
    setPersistedResult(null);
    setQuizMasteredAfterCompletion(false);
    setStudyTip(null);
    setCompletingSession(false);
    setConfidenceLevel(null);
    setSavingConfidence(false);
    setConfidenceAcknowledged(false);
    setConfidenceError(null);
    setShowAnswerReview(false);
    setMultiSelectSubmitted(false);
    setNextStepResponse(null);
  }, []);

  const loadNote = useCallback(async (force = false) => {
    if (!noteId) {
      setError("Note not found.");
      setLoading(false);
      return;
    }

    if (!force && loadedNoteIdRef.current === noteId) {
      return;
    }

    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      let detail: NoteResponse;
      if (isSharedSource) {
        const sharedNote = await getSharedNote(noteId);
        if (!sharedNote.studyPackId) {
          throw new Error("This shared note does not have a Study Pack yet.");
        }
        const sharedPack = await getSharedStudyPack(sharedNote.studyPackId);
        detail = {
          id: sharedNote.id,
          title: sharedNote.title,
          subject: sharedNote.subject,
          courseProgram: sharedNote.courseProgram,
          domainContext: null,
          learnerLevel: sharedNote.learnerLevel,
          tags: sharedNote.tags,
          content: sharedNote.content,
          visibility: "PRIVATE",
          createdAt: sharedNote.sharedAt,
          updatedAt: sharedNote.sharedAt,
          copiedFromNoteId: null,
          copiedFromUserId: null,
          copiedFromTitle: null,
          copiedFromPublic: false,
          copiedAt: null,
          studyPackId: sharedPack.id,
          studyPackStatus: "STUDY_PACK_READY",
          summary: sharedPack.summary,
          keyConcepts: sharedPack.keyConcepts,
          quiz: sharedPack.quiz,
          quizMastered: false,
          quizMasteredAt: null,
          generatedQuiz: null,
          lastUsedTargetLearnerLevel: null,
          quizCount: sharedPack.quiz.length,
          quickReviewAvailable: sharedPack.quiz.length > 0,
          challengeQuizAvailable: false,
          adaptivePracticeAvailable: false,
        };
      } else {
        detail = await getNote(noteId);
      }
      if (detail.studyPackStatus !== "STUDY_PACK_READY") {
        setNote(detail);
        loadedNoteIdRef.current = noteId;
        setError("Generate a Study Pack first.");
        return;
      }
      setNote(detail);
      loadedNoteIdRef.current = noteId;
      resetQuickReviewState(detail.quiz.map((_, index) => index));
      setRecentSessions([]);
      setCurrentSessionId(null);
      setSessionStartedAt(Date.now());
      setSessionInitializing(true);
    } catch (err) {
      if (pathname.startsWith("/study-packs/")) {
        const byStudyPack = await getMyStudyPack(noteId).catch(() => null);
        if (byStudyPack?.noteId) {
          const nextQuery = queryString;
          const targetHref = nextQuery
            ? `/notes/${byStudyPack.noteId}/quick-review?${nextQuery}`
            : `/notes/${byStudyPack.noteId}/quick-review`;
          if (legacyRedirectTargetRef.current === targetHref) {
            return;
          }
          legacyRedirectTargetRef.current = targetHref;
          router.replace(
            targetHref,
          );
          return;
        }
      }
      loadedNoteIdRef.current = null;
      const message = err instanceof Error ? err.message : "Could not load this note.";
      setError(message);
      setNote(null);
    } finally {
      setLoading(false);
    }
  }, [isSharedSource, noteId, pathname, queryString, resetQuickReviewState, router]);

  useEffect(() => {
    void loadNote();
  }, [loadNote]);

  const quiz = useMemo(() => note?.quiz ?? [], [note]);
  const totalQuestions = quiz.length;
  const isNotFound = error?.toLowerCase().includes("not found") ?? false;
  const isComplete = phase === "complete";
  const currentQuestionIndex = currentRoundIndex < activeQuestionIndexes.length
    ? activeQuestionIndexes[currentRoundIndex]
    : null;
  const currentQuestion = currentQuestionIndex !== null ? quiz[currentQuestionIndex] : null;
  const currentMatchingGroup = currentQuestionIndex !== null ? resolveQuizItemGroupAt(quiz, currentQuestionIndex) : null;
  const activeMatchingGroup = currentMatchingGroup
    && currentQuestionIndex === currentMatchingGroup.startIndex
    && currentMatchingGroup.items.every((_, offset) => activeQuestionIndexes[currentRoundIndex + offset] === currentMatchingGroup.startIndex + offset)
    ? currentMatchingGroup
    : null;
  const currentQuestionIsMultiSelect = currentQuestion?.questionFormat === "MULTI_SELECT";
  const selectedChoiceIndex = currentQuestionIndex !== null ? roundSelections[currentQuestionIndex] ?? null : null;
  const selectedMultiChoiceIndices = currentQuestionIndex !== null ? roundMultiSelections[currentQuestionIndex] ?? [] : [];
  const hasAnsweredCurrent = activeMatchingGroup
    ? activeMatchingGroup.items.every((_, offset) => roundSelections[activeMatchingGroup.startIndex + offset] != null)
    : currentQuestionIsMultiSelect ? selectedMultiChoiceIndices.length > 0 : selectedChoiceIndex !== null;
  const score = useMemo(
    () =>
      quiz.reduce((count, item, index) => {
        const selected = item.questionFormat === "MULTI_SELECT" ? selectedMultiChoices[index] : selectedChoices[index];
        return isQuizSelectionCorrect(item, selected) ? count + 1 : count;
      }, 0),
    [quiz, selectedChoices, selectedMultiChoices],
  );
  const scorePercentage = useMemo(() => {
    if (totalQuestions === 0) {
      return 0;
    }
    return Number(((score / totalQuestions) * 100).toFixed(0));
  }, [score, totalQuestions]);
  const incorrectCount = useMemo(() => {
    if (phase === "retry-transition") {
      return retryQuestionIndexes.length;
    }
    return Math.max(0, totalQuestions - score);
  }, [phase, retryQuestionIndexes.length, score, totalQuestions]);
  const previousAttempt = recentSessions.length > 0 ? recentSessions[0] : null;
  const bestPreviousCorrect = recentSessions.length > 0
    ? Math.max(...recentSessions.map((session) => session.correctAnswers))
    : null;
  const bestDisplayedCorrect = bestPreviousCorrect === null ? score : Math.max(bestPreviousCorrect, score);
  const improvedVsPrevious = previousAttempt ? score > previousAttempt.correctAnswers : false;
  const scoreFeedback = getMotivationalFeedback(scorePercentage);
  const performanceBadge = getPerformanceBadge(scorePercentage);
  const isPerfectScore = totalQuestions > 0 && score === totalQuestions;
  const displayedRetryCount = persistedResult?.retryCount ?? retryCount;
  const currentRoundType = phase === "retry" ? "RETRY" : "INITIAL";
  const incorrectQuestionsForStudyTip = useMemo<QuickReviewStudyTipRequest["incorrectQuestions"]>(() => {
    return quiz
      .map((item, index) => {
        const selected = item.questionFormat === "MULTI_SELECT" ? selectedMultiChoices[index] : selectedChoices[index];
        if (isQuizSelectionCorrect(item, selected)) {
          return null;
        }
        const correctAnswer = resolveQuizCorrectAnswer(item);
        if (!correctAnswer) {
          return null;
        }
        return {
          question: item.question,
          correctAnswer,
          explanation: item.explanation,
        };
      })
      .filter((item): item is QuickReviewStudyTipRequest["incorrectQuestions"][number] => item !== null);
  }, [quiz, selectedChoices, selectedMultiChoices]);
  const weakConcepts = useMemo(() => {
    const concepts = quiz
      .map((item, index) => {
        const selected = item.questionFormat === "MULTI_SELECT" ? selectedMultiChoices[index] : selectedChoices[index];
        if (isQuizSelectionCorrect(item, selected)) {
          return null;
        }
        const concept = item.concept?.trim();
        return concept || null;
      })
      .filter((concept): concept is string => concept !== null);
    return Array.from(new Set(concepts));
  }, [quiz, selectedChoices, selectedMultiChoices]);
  const totalConcepts = useMemo(() => {
    const concepts = quiz
      .map((item) => item.concept?.trim())
      .filter((concept): concept is string => Boolean(concept));
    return new Set(concepts).size;
  }, [quiz]);
  const securedCount = totalConcepts - weakConcepts.length;
  const shouldShowOpenLoop = isComplete
    && persistedResult?.isFirstCompletedQuiz === true
    && totalConcepts > 0
    && securedCount < totalConcepts;
  const displayedWeakConcepts = useMemo(() => {
    const persistedWeakConcepts = persistedResult?.weakConcepts?.filter((concept) => concept.trim().length > 0) ?? [];
    if (persistedWeakConcepts.length > 0) {
      return persistedWeakConcepts;
    }
    return weakConcepts;
  }, [persistedResult?.weakConcepts, weakConcepts]);
  const confidenceOptions = useMemo<Array<{ label: string; value: QuickReviewConfidenceLevel }>>(
    () => [
      { label: "Very confident", value: "HIGH" },
      { label: "Somewhat confident", value: "MEDIUM" },
      { label: "Not confident", value: "LOW" },
    ],
    [],
  );
  const isStruggling = !isPerfectScore && (displayedWeakConcepts.length > 0 || scorePercentage < 80);
  const showChallengeGuidedCta = !isStruggling;
  const noteDetailHref = useMemo(
    () => note ? (isSharedSource ? `/shared/notes/${note.id}` : `/notes/${note.id}`) : "/library",
    [isSharedSource, note],
  );
  const currentPlan = usageSummary?.plan ?? viewerPlanType ?? "FREE";
  const hasNextStepGuidance = nextStepResponse !== null || weeklyPacingWeeksRemaining !== null;
  const hasCompanionExcerpt = hasCompanionResultBridgeExcerpt(primaryCollectionCompanion);
  const hasTwiceMissedCompanionGuidance = shouldRenderTwiceMissedCta(
    persistedResult?.twiceMissedConcepts ?? [],
    currentPlan as AppPlanType,
    primaryCollectionId,
    primaryCollectionCompanion,
  );
  const hasCompanionGuidance = hasCompanionExcerpt || hasTwiceMissedCompanionGuidance;
  const groupedLearnerLevels = useMemo(
    () => getGroupedLearnerLevels(viewerProfileType as Parameters<typeof getGroupedLearnerLevels>[0]),
    [viewerProfileType],
  );
  const quickReviewProgressLabel = phase === "retry"
    ? `Retry ${Math.min(currentRoundIndex + 1, Math.max(activeQuestionIndexes.length, 1))} / ${Math.max(activeQuestionIndexes.length, 1)}`
    : `${Math.min(currentRoundIndex + 1, Math.max(totalQuestions, 1))} / ${Math.max(totalQuestions, 1)}`;

  useEffect(() => {
    const syncAuthState = () => {
      const authUser = getAuthUser();
      setIsEmailVerified(Boolean(authUser?.emailVerifiedAt));
      setViewerProfileType(authUser?.profileType ?? null);
      setViewerPlanType(authUser?.planType ?? null);
    };
    syncAuthState();
    globalThis.addEventListener("studysnap-auth-change", syncAuthState);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuthState);
    };
  }, []);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser || !hasPendingFirstStudyOnboarding(authUser)) {
      setShowCompletionGuide(false);
      return;
    }
    const firstStudyStep = getFirstStudyOnboardingStep(authUser.id);
    if (firstStudyStep === "study-pack-ready" && isComplete) {
      setShowCompletionGuide(true);
      return;
    }
    setShowCompletionGuide(false);
  }, [isComplete]);

  useEffect(() => {
    if (!isComplete) {
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
  }, [isComplete]);

  useEffect(() => {
    if (!learnerLevelToast) {
      return;
    }
    const timer = setTimeout(() => setLearnerLevelToast(null), 3500);
    return () => clearTimeout(timer);
  }, [learnerLevelToast]);

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

  const persistProgress = useCallback((next: {
    currentQuestionIndex: number;
    currentRound: "INITIAL" | "RETRY";
    retryCount: number;
    selectedChoices: Record<number, number>;
    selectedMultiChoices: Record<number, number[]>;
    retryQuestionIndexes: number[];
    activeQuestionIndexes: number[];
    roundSelections: Record<number, number>;
    roundMultiSelections: Record<number, number[]>;
  }) => {
    if (!currentSessionId) {
      return;
    }
    const sessionState: SessionStatePayload = {
      selectedChoices: serializeSelectedChoiceIndexRecord(next.selectedChoices),
      selectedMultiChoices: serializeSelectedMultiChoiceIndicesRecord(next.selectedMultiChoices),
      retryQuestionIndexes: next.retryQuestionIndexes,
      activeQuestionIndexes: next.activeQuestionIndexes,
      roundSelections: serializeSelectedChoiceIndexRecord(next.roundSelections),
      roundMultiSelections: serializeSelectedMultiChoiceIndicesRecord(next.roundMultiSelections),
    };
    updateQuickReviewSessionProgress(currentSessionId, {
      currentQuestionIndex: next.currentQuestionIndex,
      currentRound: next.currentRound,
      retryCount: next.retryCount,
      sessionState,
    }).catch(() => {
      // Progress persistence should not block review flow.
    });
  }, [currentSessionId]);

  const persistCurrentProgress = useCallback(() => {
    if (currentQuestionIndex === null) {
      return;
    }
    persistProgress({
      currentQuestionIndex: currentRoundIndex,
      currentRound: currentRoundType,
      retryCount,
      selectedChoices,
      selectedMultiChoices,
      retryQuestionIndexes,
      activeQuestionIndexes,
      roundSelections,
      roundMultiSelections,
    });
  }, [
    activeQuestionIndexes,
    currentQuestionIndex,
    currentRoundIndex,
    currentRoundType,
    persistProgress,
    retryCount,
    retryQuestionIndexes,
    roundSelections,
    roundMultiSelections,
    selectedChoices,
    selectedMultiChoices,
  ]);

  useEffect(() => {
    if (!note || !sessionInitializing) {
      return;
    }

    let isMounted = true;
    void (async () => {
      try {
        const started = await startQuickReviewSession(note.id);
        if (!isMounted || !started.sessionId) {
          return;
        }

        const state = (started.sessionState ?? {}) as SessionStatePayload;
        const restoredSelectedChoices = toSelectedChoiceIndexRecord(state.selectedChoices, quiz);
        const restoredSelectedMultiChoices = toSelectedMultiChoiceIndicesRecord(state.selectedMultiChoices, quiz);
        const restoredRetryQuestionIndexes = toNumberArray(state.retryQuestionIndexes);
        const restoredRoundSelections = toSelectedChoiceIndexRecord(state.roundSelections, quiz);
        const restoredRoundMultiSelections = toSelectedMultiChoiceIndicesRecord(state.roundMultiSelections, quiz);
        const allIndexes = quiz.map((_, index) => index);
        const round = started.currentRound ?? "INITIAL";
        const retryIndexes = restoredRetryQuestionIndexes;
        const restoredActiveIndexes = toNumberArray(state.activeQuestionIndexes);
        const activeIndexes = round === "RETRY"
          ? (restoredActiveIndexes.length > 0 ? restoredActiveIndexes : retryIndexes)
          : allIndexes;
        const restoredRoundIndex = Math.max(0, Math.min(started.currentQuestionIndex, Math.max(0, activeIndexes.length - 1)));
        const isRetryTransition = round === "INITIAL"
          && started.retryCount > 0
          && retryIndexes.length > 0
          && started.currentQuestionIndex >= allIndexes.length;

        setCurrentSessionId(started.sessionId);
        setSessionStartedAt(Date.now());
        setSelectedChoices(restoredSelectedChoices);
        setSelectedMultiChoices(restoredSelectedMultiChoices);
        setRetryQuestionIndexes(retryIndexes);
        setActiveQuestionIndexes(activeIndexes.length > 0 ? activeIndexes : allIndexes);
        setRoundSelections(restoredRoundSelections);
        setRoundMultiSelections(restoredRoundMultiSelections);
        setRetryCount(started.retryCount ?? 0);
        setCurrentRoundIndex(restoredRoundIndex);
        setPhase(isRetryTransition ? "retry-transition" : (round === "RETRY" ? "retry" : "initial"));
      } catch {
        if (isMounted) {
          setCurrentSessionId(null);
        }
      } finally {
        if (isMounted) {
          setSessionInitializing(false);
        }
      }
    })();

    return () => {
      isMounted = false;
    };
  }, [note, quiz, sessionInitializing]);

  const completeSessionIfNeeded = useCallback(async (finalRetryCount?: number) => {
    if (!currentSessionId || completionTracked || completingSession) {
      return;
    }

    setCompletingSession(true);
    const durationSeconds = sessionStartedAt
      ? Math.max(0, Math.round((Date.now() - sessionStartedAt) / 1000))
      : undefined;
    const effectiveRetryCount = finalRetryCount ?? retryCount;

    try {
      setNextStepResponse(null);
      const result = await completeQuickReviewSession(currentSessionId, {
        correctAnswers: score,
        totalQuestions,
        retryCount: effectiveRetryCount,
        durationSeconds,
        sessionMetadata: {
          weakConcepts,
        },
      });
      setPersistedResult(result);
      setQuizMasteredAfterCompletion(false);
      if (noteId) {
        void getNote(noteId)
          .then((detail) => {
            // `quizMastered` is sticky, so it is true for anyone who mastered this pack at ANY point.
            // Announcing "you earned access" off that alone congratulates a learner on an unlock they
            //has already had for weeks, every time they score perfectly again. `quizMasteredAt` is the
            // FIRST mastery (the query is min(completedAt)), so compare it against this session to
            // announce only the genuine transition — the same distinction the backend already draws
            // for STUDY_PACK_QUIZ_UNLOCKED.
            const masteredAt = detail.quizMasteredAt ? Date.parse(detail.quizMasteredAt) : Number.NaN;
            const unlockedByThisSession = detail.quizMastered === true
              && Number.isFinite(masteredAt)
              && sessionStartedAt !== null
              && masteredAt >= sessionStartedAt;
            setQuizMasteredAfterCompletion(unlockedByThisSession);
          })
          .catch(() => setQuizMasteredAfterCompletion(false));
      }
      void getPostSessionNextStep(result.studyPackId)
        .then(setNextStepResponse)
        .catch(() => setNextStepResponse(null));
    } catch {
      // Session persistence errors should not block the review experience.
    } finally {
      setCompletionTracked(true);
      setCompletingSession(false);
      void trackAnalyticsEvent({
        eventType: "QUICK_REVIEW_COMPLETED",
        entityId: currentSessionId,
        metadata: { scorePercentage: totalQuestions > 0 ? Math.round((score / totalQuestions) * 100) : 0, weakConceptCount: weakConcepts.length },
      });
    }
  }, [completingSession, completionTracked, currentSessionId, noteId, retryCount, score, sessionStartedAt, totalQuestions, weakConcepts]);

  useEffect(() => {
    if (!shouldShowOpenLoop || !persistedResult || openLoopTrackedSessionIdRef.current === persistedResult.id) {
      return;
    }

    openLoopTrackedSessionIdRef.current = persistedResult.id;
    void trackAnalyticsEvent({
      eventType: "QUICK_REVIEW_OPEN_LOOP_SHOWN",
      entityId: persistedResult.id,
      metadata: { securedCount, totalConcepts },
    });
  }, [persistedResult, securedCount, shouldShowOpenLoop, totalConcepts]);

  useEffect(() => {
    if (!isComplete || !note) {
      return;
    }
    if (!isEmailVerified) {
      setStudyTip(null);
      return;
    }
    if (incorrectQuestionsForStudyTip.length === 0) {
      setStudyTip(null);
      return;
    }

    let isMounted = true;
    generateQuickReviewStudyTip(note.id, {
      incorrectQuestions: incorrectQuestionsForStudyTip,
    })
      .then((response) => {
        if (!isMounted) {
          return;
        }
        setStudyTip(response.studyTip?.trim() ? response.studyTip.trim() : null);
      })
      .catch(() => {
        if (isMounted) {
          setStudyTip(null);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [incorrectQuestionsForStudyTip, isComplete, isEmailVerified, note]);

  const handleSelectChoice = (choiceIndex: number) => {
    if (!currentQuestion || currentQuestionIndex === null || hasAnsweredCurrent) {
      return;
    }
    const nextRoundSelections = {
      ...roundSelections,
      [currentQuestionIndex]: choiceIndex,
    };
    const nextSelectedChoices = {
      ...selectedChoices,
      [currentQuestionIndex]: choiceIndex,
    };
    setRoundSelections(nextRoundSelections);
    setSelectedChoices(nextSelectedChoices);
    trackDigestFirstAnswer();
    persistProgress({
      currentQuestionIndex: currentRoundIndex,
      currentRound: currentRoundType,
      retryCount,
      selectedChoices: nextSelectedChoices,
      selectedMultiChoices,
      retryQuestionIndexes,
      activeQuestionIndexes,
      roundSelections: nextRoundSelections,
      roundMultiSelections,
    });
  };

  const handleSelectMatchingChoice = (questionIndex: number, choiceIndex: number) => {
    if (!activeMatchingGroup || hasAnsweredCurrent) {
      return;
    }
    const nextRoundSelections = {
      ...roundSelections,
      [questionIndex]: choiceIndex,
    };
    const nextSelectedChoices = {
      ...selectedChoices,
      [questionIndex]: choiceIndex,
    };
    setRoundSelections(nextRoundSelections);
    setSelectedChoices(nextSelectedChoices);
    trackDigestFirstAnswer();
    persistProgress({
      currentQuestionIndex: currentRoundIndex,
      currentRound: currentRoundType,
      retryCount,
      selectedChoices: nextSelectedChoices,
      selectedMultiChoices,
      retryQuestionIndexes,
      activeQuestionIndexes,
      roundSelections: nextRoundSelections,
      roundMultiSelections,
    });
  };

  const handleSelectMultiChoices = (choiceIndices: number[]) => {
    if (!currentQuestion || currentQuestionIndex === null || !currentQuestionIsMultiSelect) {
      return;
    }
    const nextRoundMultiSelections = {
      ...roundMultiSelections,
      [currentQuestionIndex]: choiceIndices,
    };
    const nextSelectedMultiChoices = {
      ...selectedMultiChoices,
      [currentQuestionIndex]: choiceIndices,
    };
    setRoundMultiSelections(nextRoundMultiSelections);
    setSelectedMultiChoices(nextSelectedMultiChoices);
    persistProgress({
      currentQuestionIndex: currentRoundIndex,
      currentRound: currentRoundType,
      retryCount,
      selectedChoices,
      selectedMultiChoices: nextSelectedMultiChoices,
      retryQuestionIndexes,
      activeQuestionIndexes,
      roundSelections,
      roundMultiSelections: nextRoundMultiSelections,
    });
  };

  const handleSubmitMultiSelect = () => {
    setMultiSelectSubmitted(true);
    trackDigestFirstAnswer();
  };

  const handleNext = () => {
    if (!hasAnsweredCurrent) {
      return;
    }
    setMultiSelectSubmitted(false);
    const questionStep = activeMatchingGroup?.items.length ?? 1;
    const isLastInRound = currentRoundIndex + questionStep >= activeQuestionIndexes.length;
    if (!isLastInRound) {
      const nextRoundIndex = currentRoundIndex + questionStep;
      setCurrentRoundIndex(nextRoundIndex);
      persistProgress({
        currentQuestionIndex: nextRoundIndex,
        currentRound: currentRoundType,
        retryCount,
        selectedChoices,
        selectedMultiChoices,
        retryQuestionIndexes,
        activeQuestionIndexes,
        roundSelections,
        roundMultiSelections,
      });
      return;
    }

    if (phase === "initial") {
      const incorrectIndexes = activeQuestionIndexes.filter((index) => {
        const selected = quiz[index]?.questionFormat === "MULTI_SELECT" ? selectedMultiChoices[index] : selectedChoices[index];
        return !isQuizSelectionCorrect(quiz[index], selected);
      });
      if (incorrectIndexes.length === 0) {
        setPhase("complete");
        void completeSessionIfNeeded(0);
        return;
      }
      setRetryQuestionIndexes(incorrectIndexes);
      setRetryCount(1);
      setPhase("retry-transition");
      persistProgress({
        currentQuestionIndex: activeQuestionIndexes.length,
        currentRound: "INITIAL",
        retryCount: 1,
        selectedChoices,
        selectedMultiChoices,
        retryQuestionIndexes: incorrectIndexes,
        activeQuestionIndexes,
        roundSelections,
        roundMultiSelections,
      });
      return;
    }

    if (phase === "retry") {
      setPhase("complete");
      void completeSessionIfNeeded(retryCount);
    }
  };

  const handleStartRetryRound = () => {
    if (retryQuestionIndexes.length === 0) {
      setPhase("complete");
      return;
    }
    setPhase("retry");
    setActiveQuestionIndexes(retryQuestionIndexes);
    setCurrentRoundIndex(0);
    setMultiSelectSubmitted(false);
    setRoundSelections({});
    setRoundMultiSelections({});
    persistProgress({
      currentQuestionIndex: 0,
      currentRound: "RETRY",
      retryCount,
      selectedChoices,
      selectedMultiChoices,
      retryQuestionIndexes,
      activeQuestionIndexes: retryQuestionIndexes,
      roundSelections: {},
      roundMultiSelections: {},
    });
  };

  const handleFinishReview = () => {
    setPhase("complete");
    void completeSessionIfNeeded(0);
  };

  const handleSelectConfidence = useCallback(async (level: QuickReviewConfidenceLevel) => {
    if (!currentSessionId || savingConfidence || completingSession || !completionTracked) {
      return;
    }

    setSavingConfidence(true);
    setConfidenceError(null);
    try {
      const updated = await saveQuickReviewConfidence(currentSessionId, level);
      setPersistedResult((current) => ({
        ...updated,
        twiceMissedConcepts: current?.twiceMissedConcepts ?? [],
      }));
      setConfidenceLevel(updated.confidenceLevel ?? level);
      setConfidenceAcknowledged(true);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not save confidence feedback.";
      setConfidenceError(message);
    } finally {
      setSavingConfidence(false);
    }
  }, [completingSession, completionTracked, currentSessionId, savingConfidence]);

  const finalizeFirstStudyOnboarding = useCallback(async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      router.push("/dashboard");
      return;
    }
    try {
      const me = await completeProductOnboarding(false);
      setAuthUser({
        ...authUser,
        displayName: me.displayName,
        profileType: me.profileType,
        emailVerifiedAt: me.emailVerifiedAt,
        onboardingCompletedAt: me.onboardingCompletedAt,
        productOnboardingCompletedAt: me.productOnboardingCompletedAt,
      });
    } catch {
      // Best-effort completion only.
    } finally {
      clearFirstStudyOnboardingStep(authUser.id);
      setShowCompletionGuide(false);
      router.push("/dashboard");
    }
  }, [router]);

  const quizSessionActive = Boolean(note && currentSessionId && !isComplete && totalQuestions > 0 && !error);
  useBottomViewportClaim(quizSessionActive);
  const { requestLeave, LeaveQuizModal } = useQuizSessionGuard({
    active: quizSessionActive,
    fallbackHref: noteDetailHref,
    onBeforeRouteLeave: persistCurrentProgress,
    onConfirmLeave: async () => {
      if (!currentSessionId) {
        return;
      }
      await forfeitQuickReviewSession(currentSessionId);
    },
  });

  return (
    <main className={cn(
      "mx-auto w-full max-w-3xl space-y-4 px-4 py-6 sm:px-6 sm:py-10",
      note && currentQuestion && !isComplete && "pb-28 sm:pb-10",
    )}>
      {quizSessionActive ? (
        <div
          data-testid="quick-review-top-bar"
          className="sticky top-16 z-20 -mx-4 flex items-center gap-3 border-b border-border bg-background/95 px-4 py-3 backdrop-blur sm:mx-0 sm:rounded-xl sm:border"
        >
          <Button type="button" variant="outline" size="sm" className="shrink-0 px-3" onClick={() => requestLeave()}>
            Leave Quiz
          </Button>
          <div className="min-w-0 flex-1 text-center">
            <p className="truncate text-sm font-semibold text-foreground">Quick Review</p>
            {note?.title ? (
              <p className="truncate text-xs text-foreground/55">{note.title}</p>
            ) : null}
          </div>
          <div className="shrink-0 rounded-full border border-border bg-background px-3 py-1 text-sm font-semibold text-foreground">
            {quickReviewProgressLabel}
          </div>
        </div>
      ) : (
        <div className="flex items-center justify-between gap-3">
          <BackLink href={noteDetailHref} label="Note" />
        </div>
      )}

      {loading || sessionInitializing ? (
        <QuickReviewLoading />
      ) : error ? (
        <Card className="motion-fade-enter space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">
            {isNotFound ? "Note not found" : "Could not start Quick Review"}
          </h1>
          <p className="text-sm text-foreground/75">
            {isNotFound
              ? "This note is unavailable or does not belong to your account."
              : error}
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            {!isNotFound ? (
              <Button type="button" className="w-full sm:w-auto" onClick={() => void loadNote(true)}>
                Retry
              </Button>
            ) : null}
          </div>
        </Card>
      ) : note && totalQuestions === 0 ? (
        <Card className="motion-fade-enter space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">No quiz questions available</h1>
          <p className="text-sm text-foreground/75">
            This note does not have quiz questions yet. Generate a Study Pack to try Quick Review.
          </p>
          <BackLink href={noteDetailHref} label="Note" />
        </Card>
      ) : note && !currentSessionId ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Quick Review not started</h1>
          <p className="text-sm text-foreground/75">
            Start Quick Review from the note detail page to create a session.
          </p>
          <BackLink href={noteDetailHref} label="Note" />
        </Card>
      ) : note && isComplete ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Quick Review Complete
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">
            {shouldShowOpenLoop ? `${securedCount} of ${totalConcepts} ${totalConcepts === 1 ? "concept" : "concepts"} secured` : "Your results"}
          </h1>
          <p className="text-sm text-foreground/75">
            {shouldShowOpenLoop
              ? "The rest are best reviewed tomorrow — you're not done yet."
              : "Review your results, then choose your next study step."}
          </p>

          {/* Section 1: Score summary */}
          <div className="space-y-2 text-sm text-foreground/75">
            <div className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium ${performanceBadge.className}`}>
              {performanceBadge.label}
            </div>
            <ScoreProgressBlock score={score} totalQuestions={totalQuestions} scorePercentage={scorePercentage} />
            {persistedResult && isPerfectScore && quizMasteredAfterCompletion ? (
              <Card className="motion-fade-enter space-y-1 border-emerald-500/35 bg-emerald-500/10 p-4">
                <p className="font-semibold text-emerald-800 dark:text-emerald-200">🔓 Quiz Unlocked</p>
                <p className="text-sm leading-relaxed text-foreground/75">
                  You earned access to the saved Quiz questions. They are now available to practise with on your note.
                </p>
              </Card>
            ) : null}
            {previousAttempt ? (
              <div className="space-y-1 rounded-md border border-border bg-background p-3">
                <p>Previous Attempt: {previousAttempt.correctAnswers} / {previousAttempt.totalQuestions}</p>
                <p>Best Score: {bestDisplayedCorrect} / {totalQuestions}</p>
                {improvedVsPrevious ? (
                  <p className="font-medium text-emerald-700 dark:text-emerald-300">
                    Your Score: {score} / {totalQuestions} (improved)
                  </p>
                ) : null}
              </div>
            ) : null}
            {displayedRetryCount > 0 ? <p>Retries: {displayedRetryCount}</p> : null}
            {studyTip ? (
              <div className="space-y-2 rounded-md border border-blue-500/30 bg-blue-500/10 p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                  Study Tip
                </p>
                <p className="whitespace-normal wrap-break-word leading-relaxed text-foreground/85">{studyTip}</p>
              </div>
            ) : null}
          </div>

          <ReviewCommitmentPrompt
            isFirstCompletedSessionEver={persistedResult?.isFirstCompletedSessionEver}
            noteId={note?.id ?? null}
          />
          {hasNextStepGuidance ? (
            <ResultGuidanceGroup label="What to do next" testId="quick-review-next-step-guidance">
              <PostSessionNextStep
                response={nextStepResponse}
                currentPlan={currentPlan}
                noteId={note?.id ?? null}
                onOpenPaywall={() => openAdaptivePracticePaywall("quick_review_results_next_step")}
                originatingQuizMode="QUICK_REVIEW"
                contained
              />
              {nextStepResponse?.goalNudge ? (
                <GoalNudgeCard goalNudge={nextStepResponse.goalNudge} noteId={note?.id ?? null} contained />
              ) : null}
              <WeeklyPacingEchoCard
                weeksRemaining={weeklyPacingWeeksRemaining}
                goalLabel={getCollectionLabels(viewerProfileType as ProfileType | null).goalSingular}
                contained
              />
            </ResultGuidanceGroup>
          ) : null}
          {hasCompanionGuidance ? (
            <ResultGuidanceGroup label="Companion guidance" testId="quick-review-companion-guidance">
              <CompanionResultBridgeCard
                companion={primaryCollectionCompanion}
                reviewSetLabel={getCollectionLabels(viewerProfileType as ProfileType | null).singular}
                contained
              />
              <TwiceMissedAskCompanionCard
                twiceMissedConcepts={persistedResult?.twiceMissedConcepts ?? []}
                currentPlan={currentPlan as AppPlanType}
                primaryCollectionId={primaryCollectionId}
                companion={primaryCollectionCompanion}
                contained
              />
            </ResultGuidanceGroup>
          ) : null}

          {nextStepResponse === null ? (
            <>
              {/* Fallback guidance when the server-resolved next step is unavailable. */}
              {isPerfectScore ? (
                <div className="rounded-md border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm">
                  <div className="mb-1 flex items-center gap-2 text-emerald-700 dark:text-emerald-300">
                    <Trophy className="h-4 w-4" aria-hidden="true" />
                    <p className="font-medium">Excellent work! You mastered this topic.</p>
                  </div>
                  <p className="text-foreground/75">Try a challenge quiz to test yourself at a harder level.</p>
                </div>
              ) : displayedWeakConcepts.length > 0 ? (
                <div className="space-y-1 rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-sm">
                  <p className="text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
                    Weak Areas
                  </p>
                  <p className="text-foreground/75">Focus on these concepts to improve your score:</p>
                  <ul className="list-disc space-y-1 pl-5 text-foreground/85">
                    {displayedWeakConcepts.map((concept) => (
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
                </div>
              ) : (
                <p className="text-sm text-foreground/75">{scoreFeedback}</p>
              )}

              {/*
                Fallback for when the next-step fetch fails. Mirrors the server contract: Challenge
                once the learner is doing well, otherwise back to the notes. Adaptive Practice is
                deliberately absent — Quick Review no longer routes into it (EXAM_MODES.md).
              */}
              {showChallengeGuidedCta ? (
                <Link href={`/notes/${note.id}/challenge-quiz`} className="block">
                  <Button type="button" className="w-full">
                    Take Another Challenge
                  </Button>
                </Link>
              ) : (
                <Link href={`/notes/${note.id}`} className="block">
                  <Button type="button" className="w-full">
                    Review the Notes
                  </Button>
                </Link>
              )}
            </>
          ) : null}

          {/* Section 4: Secondary actions */}
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShowAnswerReview((previous) => !previous)}>
              {showAnswerReview ? "Hide Answer Review" : "Review Answers"}
            </Button>
          </div>
          <div className="pt-1">
            <BackLink href={noteDetailHref} label="Note" />
          </div>

          {showAnswerReview ? (
            <QuizAnswerReview
              quiz={quiz}
              selectedChoices={selectedChoices}
              selectedMultiChoices={selectedMultiChoices}
              className="mt-2"
              planType={viewerPlanType}
            />
          ) : null}

          {/* Upgrade nudge for non-Pro users */}
          {!note?.adaptivePracticeAvailable ? (
            <PostSuccessUpgradeNudge trigger="quick-review" />
          ) : null}

          {/* Confidence + Learner level (secondary section) */}
          <div className="space-y-4 border-t border-border pt-4">
            <div className="space-y-2 text-sm">
              <p className="font-medium text-foreground">How confident did you feel about this topic?</p>
              {confidenceAcknowledged ? (
                <div className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium ${
                  confidenceLevel === "HIGH"
                    ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
                    : confidenceLevel === "MEDIUM"
                      ? "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300"
                      : "border-orange-500/40 bg-orange-500/10 text-orange-700 dark:text-orange-300"
                }`}>
                  {confidenceLevel === "HIGH" ? "🟢 Confident" : confidenceLevel === "MEDIUM" ? "🟡 Improving" : "🔴 Needs Practice"}
                </div>
              ) : (
                <div className="flex flex-col gap-2 sm:flex-row">
                  {confidenceOptions.map((option) => (
                    <Button
                      key={option.value}
                      type="button"
                      size="sm"
                      variant={confidenceLevel === option.value ? "default" : "outline"}
                      className="w-full sm:w-auto"
                      disabled={savingConfidence || completingSession || !completionTracked}
                      onClick={() => void handleSelectConfidence(option.value)}
                    >
                      {option.label}
                    </Button>
                  ))}
                </div>
              )}
              {confidenceError ? (
                <p className="text-xs text-red-600 dark:text-red-400">{confidenceError}</p>
              ) : null}
            </div>
            {currentLearnerLevel ? (
              <div className="space-y-2 text-sm">
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
          </div>

          <QuizFeedbackPanel
            key={persistedResult?.isFirstCompletedSessionEver === true
              ? "first-quiz-feedback"
              : persistedResult?.isSecondCompletedSessionEver === true
                ? "second-quiz-feedback"
                : "quiz-feedback"}
            quizLabel="Quick Review"
            noteTitle={note.title}
            section={showAnswerReview ? "review" : "results"}
            isFirstCompletedSessionEver={persistedResult?.isFirstCompletedSessionEver}
            isSecondCompletedSessionEver={persistedResult?.isSecondCompletedSessionEver}
            userId={getAuthUser()?.id}
          />
        </Card>
      ) : note && phase === "retry-transition" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Quick Review Progress
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">You&apos;re making progress.</h1>
          <div className="space-y-2 rounded-md border border-border bg-background p-3 text-sm text-foreground/75">
            <ScoreProgressBlock score={score} totalQuestions={totalQuestions} scorePercentage={scorePercentage} />
          </div>
          <p className="text-sm text-foreground/75">
            You got {score} out of {totalQuestions} correct.
          </p>
          <p className="text-sm text-foreground/75">
            You missed {incorrectCount} {incorrectCount === 1 ? "question" : "questions"}. Review them now or finish
            with your current score.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={handleStartRetryRound} disabled={completingSession}>
              Retry Incorrect Questions
            </Button>
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={handleFinishReview} disabled={completingSession}>
              Finish Review
            </Button>
          </div>
        </Card>
      ) : note && currentQuestion ? (
        <div className="space-y-4">
          <Card className="space-y-4 p-4 sm:p-5">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">
                {phase === "retry"
                  ? `Retry question ${currentRoundIndex + 1} of ${activeQuestionIndexes.length}`
                  : `Question ${currentRoundIndex + 1} of ${totalQuestions}`}
              </p>
            </div>
            {activeMatchingGroup ? (
              <QuizMatchingGroup
                items={activeMatchingGroup.items}
                groupStartIndex={activeMatchingGroup.startIndex}
                selectedChoices={roundSelections}
                revealAnswer={hasAnsweredCurrent}
                onSelectChoice={handleSelectMatchingChoice}
              />
            ) : (
              <>
                <h2 className="text-lg font-semibold leading-7 sm:text-xl">
                  <QuizQuestionText text={currentQuestion.question} />
                </h2>
                <QuizChoiceList
                  questionKey={currentQuestion.question}
                  choices={currentQuestion.choices}
                  correctIndex={resolveQuizCorrectIndex(currentQuestion)}
                  correctIndices={currentQuestion.correctIndices}
                  questionFormat={currentQuestion.questionFormat}
                  selectedChoiceIndex={selectedChoiceIndex}
                  selectedMultiChoiceIndices={selectedMultiChoiceIndices}
                  revealAnswer={hasAnsweredCurrent && (!currentQuestionIsMultiSelect || multiSelectSubmitted)}
                  onSelectChoice={handleSelectChoice}
                  onSelectMultiChoices={handleSelectMultiChoices}
                />
              </>
            )}

            {hasAnsweredCurrent && !activeMatchingGroup && (!currentQuestionIsMultiSelect || multiSelectSubmitted) ? (
              <div className="space-y-3 rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
                <p>
                  <span className="font-medium text-foreground">Explanation:</span>{" "}
                  {renderMathText(currentQuestion.explanation)}
                </p>
                {hasComputationalWorkingSolution(currentQuestion) ? (
                  <QuizWorkingSolution
                    workingSolution={currentQuestion.workingSolution}
                    planType={viewerPlanType}
                  />
                ) : null}
              </div>
            ) : null}
          </Card>
          <div
            data-testid="quick-review-action-bar"
            className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-background/95 px-4 py-3 backdrop-blur sm:static sm:border-0 sm:bg-transparent sm:px-0 sm:py-0"
          >
            <div className="mx-auto flex w-full max-w-3xl">
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={currentQuestionIsMultiSelect && !multiSelectSubmitted ? handleSubmitMultiSelect : handleNext}
                disabled={!hasAnsweredCurrent}
              >
                {currentQuestionIsMultiSelect && !multiSelectSubmitted
                  ? "Submit"
                  : currentRoundIndex + 1 === activeQuestionIndexes.length
                    ? phase === "retry"
                      ? "Finish Retry"
                      : "Finish Quick Review"
                    : "Next"}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      <PaywallModal
        isOpen={activePaywallModal !== null}
        variant={activePaywallModal ?? "adaptive-practice"}
        source="quick_review_page"
        onClose={() => setActivePaywallModal(null)}
      />

      <AppModal
        isOpen={showCompletionGuide}
        title="You’re all set!"
        description="You can now track your progress, see weak concepts, and continue practicing anytime from your dashboard."
        onClose={() => {
          void finalizeFirstStudyOnboarding();
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              className="w-full sm:w-auto"
              onClick={() => {
                void finalizeFirstStudyOnboarding();
              }}
            >
              Go to Dashboard
            </Button>
          </div>
        )}
      />
      <LeaveQuizModal />
      {learnerLevelToast ? (
        <ToastMessage message={learnerLevelToast} tone="success" />
      ) : null}
    </main>
  );
}
