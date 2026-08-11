"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { QuizFeedbackPanel } from "@/components/feedback/quiz-feedback-panel";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizAnswerReview } from "@/components/study-pack/quiz-answer-review";
import { QuizGenerationOverlay } from "@/components/study-pack/quiz-generation-overlay";
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
import { hasComputationalWorkingSolution, QuizWorkingSolution } from "@/components/study-pack/quiz-working-solution";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { getAuthUser } from "@/lib/auth";
import { resolveRemainingUsageCredits } from "@/lib/plans";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  completeAdaptivePracticeSession,
  forfeitAdaptivePracticeSession,
  generateAdaptiveQuickReviewQuiz,
  getCollectionGoal,
  getInProgressAdaptivePracticeSession,
  getMe,
  getMyStudyPack,
  getNote,
  getPostSessionNextStep,
  isEmailNotVerifiedError,
  trackAnalyticsEvent,
  type AdaptiveConceptSelectionReason,
  type CompanionContent,
  type NoteResponse,
  type AdaptivePracticeCompleteResponse,
  type PostSessionNextStepResponse,
  type QuickReviewAdaptiveQuizResponse,
} from "@/lib/api";
import { getCollectionLabels } from "@/lib/collection-labels";
import { buildConceptAnchorId, normalizeConceptKey } from "@/lib/concepts";
import { isQuizSelectionCorrect, resolveQuizCorrectIndex, resolveQuizItemGroupAt } from "@/lib/quiz";
import { mapPerformanceLevel } from "@/lib/challenge-quiz-results";
import type { AppPlanType } from "@/src/config/plans";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

function AdaptivePracticeLoading() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="h-4 w-40 animate-pulse rounded bg-foreground/10" />
      <div className="h-7 w-3/4 animate-pulse rounded bg-foreground/10" />
      <div className="space-y-2">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={`adaptive-choice-${index}`} className="h-10 w-full animate-pulse rounded bg-foreground/10" />
        ))}
      </div>
    </Card>
  );
}

function formatSelectionRationale(
  concept: string | null | undefined,
  reason: AdaptiveConceptSelectionReason | null | undefined,
) {
  const normalizedConcept = concept?.trim();
  if (!normalizedConcept || !reason) {
    return null;
  }
  let reasonLabel: string;
  switch (reason) {
    case "DUE":
      reasonLabel = "due for review";
      break;
    case "WEAK":
      reasonLabel = "missed last time";
      break;
    case "BOTH":
      reasonLabel = "missed last time and due for review";
      break;
    default:
      return null;
  }
  return `Reviewing: ${normalizedConcept} — ${reasonLabel}`;
}

export default function AdaptivePracticePage() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const requestInFlightRef = useRef(false);
  const [completionResult, setCompletionResult] = useState<AdaptivePracticeCompleteResponse | null>(null);
  const [completionSignalLoaded, setCompletionSignalLoaded] = useState(false);
  const loadedForNoteRef = useRef<string | null>(null);
  const legacyRedirectTargetRef = useRef<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [startingAdaptive, setStartingAdaptive] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [adaptiveQuiz, setAdaptiveQuiz] = useState<QuickReviewAdaptiveQuizResponse | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedChoices, setSelectedChoices] = useState<Record<number, number>>({});
  const [selectedMultiChoices, setSelectedMultiChoices] = useState<Record<number, number[]>>({});
  const [completionTracked, setCompletionTracked] = useState(false);
  const [premiumLocked, setPremiumLocked] = useState(false);
  const [quizStarted, setQuizStarted] = useState(false);
  const [sessionStartedAt, setSessionStartedAt] = useState<number | null>(null);
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [showPremiumPaywall, setShowPremiumPaywall] = useState(false);
  const [showVerifyEmailModal, setShowVerifyEmailModal] = useState(false);
  const [showAnswerReview, setShowAnswerReview] = useState(false);
  const [showLimitReachedState, setShowLimitReachedState] = useState(false);
  const [nextStepResponse, setNextStepResponse] = useState<PostSessionNextStepResponse | null>(null);
  const [weeklyPacingWeeksRemaining, setWeeklyPacingWeeksRemaining] = useState<number | null>(null);
  const [primaryCollectionId, setPrimaryCollectionId] = useState<string | null>(null);
  const [primaryCollectionCompanion, setPrimaryCollectionCompanion] = useState<CompanionContent | null>(null);
  const { usageSummary } = useBillingUsageSummary();

  const noteId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);
  const noteDetailHref = useMemo(() => (note ? `/notes/${note.id}` : "/library"), [note]);
  const currentPlan = usageSummary?.plan ?? getAuthUser()?.planType ?? "FREE";
  const hasNextStepGuidance = nextStepResponse !== null || weeklyPacingWeeksRemaining !== null;
  const hasCompanionExcerpt = hasCompanionResultBridgeExcerpt(primaryCollectionCompanion);
  const hasTwiceMissedCompanionGuidance = shouldRenderTwiceMissedCta(
    completionResult?.twiceMissedConcepts ?? [],
    currentPlan as AppPlanType,
    primaryCollectionId,
    primaryCollectionCompanion,
  );
  const hasCompanionGuidance = hasCompanionExcerpt || hasTwiceMissedCompanionGuidance;
  const adaptivePracticeRemaining = usageSummary
    ? resolveRemainingUsageCredits(
      usageSummary.usage.adaptivePracticeUsed,
      usageSummary.limits.adaptivePracticePerMonth,
      usageSummary.remaining?.adaptivePracticeRemaining,
    )
    : null;
  const hasAdaptivePracticeQuota = (usageSummary?.limits.adaptivePracticePerMonth ?? 0) > 0;
  const hasReachedAdaptivePracticeLimit = hasAdaptivePracticeQuota
    && adaptivePracticeRemaining !== null
    && adaptivePracticeRemaining <= 0;
  const shouldUpgradeForAdaptivePracticeLimit = currentPlan !== "PRO" && hasReachedAdaptivePracticeLimit;
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
    setShowPremiumPaywall(true);
  }, [noteId, pathname]);

  const applyAdaptiveSession = useCallback((response: QuickReviewAdaptiveQuizResponse) => {
    setAdaptiveQuiz(response);
    setCurrentIndex(0);
    setSelectedChoices({});
    setSelectedMultiChoices({});
    setCompletionTracked(false);
    setShowAnswerReview(false);
    setNextStepResponse(null);
    if (response.status === "IN_PROGRESS" && response.quiz.length > 0) {
      setQuizStarted(true);
      setSessionStartedAt(Date.now());
      setError(null);
      return;
    }
    setQuizStarted(false);
    setSessionStartedAt(null);
    setError(null);
  }, []);

  const loadAdaptiveQuiz = useCallback(async (force = false) => {
    if (requestInFlightRef.current) {
      return;
    }
    if (!noteId) {
      setError("Note not found.");
      setLoading(false);
      return;
    }

    if (!force && loadedForNoteRef.current === noteId) {
      return;
    }

    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    requestInFlightRef.current = true;
    setError(null);
    setPremiumLocked(false);
    setShowLimitReachedState(false);
    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      setAdaptiveQuiz(null);
      setError("Verify your email to use this feature.");
      setShowVerifyEmailModal(true);
      setLoading(false);
      requestInFlightRef.current = false;
      return;
    }

    try {
      const detail = await getNote(noteId);
      loadedForNoteRef.current = noteId;
      if (detail.studyPackStatus !== "STUDY_PACK_READY") {
        setNote(detail);
        setError("Generate a Study Pack first.");
        setAdaptiveQuiz(null);
        return;
      }
      setNote(detail);
      if (!detail.adaptivePracticeAvailable) {
        setAdaptiveQuiz(null);
        setPremiumLocked(true);
        setShowPremiumPaywall(true);
        return;
      }
      const response = await getInProgressAdaptivePracticeSession(detail.id);
      if (!response.sessionId && hasReachedAdaptivePracticeLimit) {
        setAdaptiveQuiz(null);
        if (shouldUpgradeForAdaptivePracticeLimit) {
          setShowPremiumPaywall(true);
        } else {
          setShowLimitReachedState(true);
        }
        return;
      }
      applyAdaptiveSession(response);
    } catch (err) {
      if (pathname.startsWith("/study-packs/")) {
        const byStudyPack = await getMyStudyPack(noteId).catch(() => null);
        if (byStudyPack?.noteId) {
          const nextQuery = searchParams.toString();
          const targetHref = nextQuery
            ? `/notes/${byStudyPack.noteId}/adaptive-practice?${nextQuery}`
            : `/notes/${byStudyPack.noteId}/adaptive-practice`;
          if (legacyRedirectTargetRef.current !== targetHref) {
            legacyRedirectTargetRef.current = targetHref;
            router.replace(targetHref);
          }
          return;
        }
      }
      loadedForNoteRef.current = null;
      const message = isEmailNotVerifiedError(err)
        ? "Verify your email to use this feature."
        : err instanceof Error
          ? err.message
          : "Could not generate adaptive practice.";
      if (isEmailNotVerifiedError(err)) {
        setShowVerifyEmailModal(true);
      }
      setError(message);
      setAdaptiveQuiz(null);
    } finally {
      setLoading(false);
      requestInFlightRef.current = false;
    }
  }, [applyAdaptiveSession, hasReachedAdaptivePracticeLimit, noteId, pathname, router, searchParams, shouldUpgradeForAdaptivePracticeLimit]);

  useEffect(() => {
    if (!noteId) {
      return;
    }
    if (loadedForNoteRef.current === noteId) {
      return;
    }
    void loadAdaptiveQuiz();
  }, [loadAdaptiveQuiz, noteId]);

  useEffect(() => {
    if (!hasReachedAdaptivePracticeLimit || adaptiveQuiz?.sessionId) {
      return;
    }
    if (shouldUpgradeForAdaptivePracticeLimit) {
      setPremiumLocked(false);
      setShowLimitReachedState(false);
      setShowPremiumPaywall(true);
      return;
    }
    setPremiumLocked(false);
    setShowPremiumPaywall(false);
    setShowLimitReachedState(true);
  }, [adaptiveQuiz?.sessionId, hasReachedAdaptivePracticeLimit, shouldUpgradeForAdaptivePracticeLimit]);

  const quiz = useMemo(() => adaptiveQuiz?.quiz ?? [], [adaptiveQuiz]);
  const hasQuestions = quiz.length > 0;
  const currentQuestion = hasQuestions ? quiz[currentIndex] : null;
  const currentMatchingGroup = resolveQuizItemGroupAt(quiz, currentIndex);
  const currentRationales = (() => {
    const questionIndexes = currentMatchingGroup
      ? currentMatchingGroup.items.map((_, offset) => currentMatchingGroup.startIndex + offset)
      : [currentIndex];
    const uniqueRationales = new Set<string>();
    questionIndexes.forEach((questionIndex) => {
      const question = quiz[questionIndex];
      const rationale = formatSelectionRationale(
        question?.concept,
        adaptiveQuiz?.conceptSelectionReasons?.[questionIndex],
      );
      if (rationale) {
        uniqueRationales.add(rationale);
      }
    });
    return Array.from(uniqueRationales);
  })();
  const selectedChoiceIndex = selectedChoices[currentIndex] ?? null;
  const selectedMultiChoiceIndices = selectedMultiChoices[currentIndex] ?? [];
  const currentQuestionIsMultiSelect = currentQuestion?.questionFormat === "MULTI_SELECT";
  const hasAnsweredCurrent = currentMatchingGroup
    ? currentMatchingGroup.items.every((_, offset) => selectedChoices[currentMatchingGroup.startIndex + offset] != null)
    : currentQuestionIsMultiSelect ? selectedMultiChoiceIndices.length > 0 : selectedChoiceIndex !== null;
  const isComplete = hasQuestions && currentIndex >= quiz.length;
  const score = useMemo(() => {
    return quiz.reduce((count, question, index) => {
      const selected = question.questionFormat === "MULTI_SELECT" ? selectedMultiChoices[index] : selectedChoices[index];
      return isQuizSelectionCorrect(question, selected) ? count + 1 : count;
    }, 0);
  }, [quiz, selectedChoices, selectedMultiChoices]);
  const scorePercentage = useMemo(() => {
    if (quiz.length === 0) {
      return 0;
    }
    return Number(((score / quiz.length) * 100).toFixed(0));
  }, [quiz.length, score]);
  const completionMessage = useMemo(() => {
    const level = mapPerformanceLevel(scorePercentage);
    if (level === "Excellent") return "Outstanding. You've mastered these weak areas.";
    if (level === "Good") return "Great work. You're making strong progress on these concepts.";
    if (level === "Fair") return "Good effort. Keep reviewing these concepts to build confidence.";
    return "Keep going. These concepts need more practice — try again when ready.";
  }, [scorePercentage]);

  useEffect(() => {
    if (!isComplete) {
      return;
    }
    void getMe().then((me) => {
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
    if (adaptiveQuiz?.status !== "GENERATING" || !note) {
      return;
    }

    let isMounted = true;
    const pollGenerationStatus = async () => {
      try {
        const response = await getInProgressAdaptivePracticeSession(note.id);
        if (!isMounted) {
          return;
        }
        applyAdaptiveSession(response);
      } catch (err) {
        if (isMounted) {
          const message = err instanceof Error ? err.message : "Could not load Adaptive Practice generation status.";
          setError(message);
          setAdaptiveQuiz(null);
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
  }, [adaptiveQuiz?.status, applyAdaptiveSession, note]);

  const handleStartAdaptivePractice = useCallback(async () => {
    if (!note || requestInFlightRef.current) {
      return;
    }

    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      setError("Verify your email to use this feature.");
      setShowVerifyEmailModal(true);
      return;
    }
    if (!note.adaptivePracticeAvailable) {
      setPremiumLocked(true);
      openAdaptivePracticePaywall("adaptive_practice_start");
      return;
    }
    if (hasReachedAdaptivePracticeLimit) {
      if (shouldUpgradeForAdaptivePracticeLimit) {
        openAdaptivePracticePaywall("adaptive_practice_limit_reached");
        return;
      }
      setShowLimitReachedState(true);
      return;
    }

    requestInFlightRef.current = true;
    setStartingAdaptive(true);
    setError(null);
    try {
      const response = await generateAdaptiveQuickReviewQuiz(note.id);
      applyAdaptiveSession(response);
    } catch (err) {
      const message = isEmailNotVerifiedError(err)
        ? "Verify your email to use this feature."
        : err instanceof Error
          ? err.message
          : "Could not generate adaptive practice.";
      if (isEmailNotVerifiedError(err)) {
        setShowVerifyEmailModal(true);
      }
      if (message.toLowerCase().includes("monthly adaptive practice limit")) {
        if (currentPlan !== "PRO") {
          openAdaptivePracticePaywall("adaptive_practice_limit_reached");
        } else {
          setShowLimitReachedState(true);
        }
      } else {
        setError(message);
      }
    } finally {
      requestInFlightRef.current = false;
      setStartingAdaptive(false);
    }
  }, [applyAdaptiveSession, currentPlan, hasReachedAdaptivePracticeLimit, note, openAdaptivePracticePaywall, shouldUpgradeForAdaptivePracticeLimit]);

  const adaptiveGenerationLocked = startingAdaptive || adaptiveQuiz?.status === "GENERATING";
  const adaptiveQuizActive = Boolean(
    adaptiveQuiz?.sessionId
    && adaptiveQuiz.status === "IN_PROGRESS"
    && quizStarted
    && hasQuestions
    && !isComplete
    && !completionTracked
    && !error,
  );
  const { requestLeave, LeaveQuizModal } = useQuizSessionGuard({
    active: adaptiveQuizActive,
    fallbackHref: noteDetailHref,
    onConfirmLeave: async () => {
      if (!adaptiveQuiz?.sessionId) {
        return;
      }
      await forfeitAdaptivePracticeSession(adaptiveQuiz.sessionId);
    },
  });
  const { LeaveQuizModal: GenerationLockModal } = useQuizSessionGuard({
    active: adaptiveGenerationLocked,
    fallbackHref: noteDetailHref,
    onConfirmLeave: () => undefined,
    blockWithoutConfirmation: true,
  });

  const handleSelectChoice = (choiceIndex: number) => {
    if (!currentQuestion || hasAnsweredCurrent) {
      return;
    }
    setSelectedChoices((prev) => ({
      ...prev,
      [currentIndex]: choiceIndex,
    }));
  };

  const handleSelectMatchingChoice = (questionIndex: number, choiceIndex: number) => {
    if (!currentMatchingGroup || hasAnsweredCurrent) {
      return;
    }
    setSelectedChoices((prev) => ({
      ...prev,
      [questionIndex]: choiceIndex,
    }));
  };

  const handleSelectMultiChoices = (choiceIndices: number[]) => {
    if (!currentQuestion || !currentQuestionIsMultiSelect) {
      return;
    }
    setSelectedMultiChoices((prev) => ({
      ...prev,
      [currentIndex]: choiceIndices,
    }));
  };

  const handleNext = () => {
    if (!hasAnsweredCurrent) {
      return;
    }
    const nextIndex = currentMatchingGroup ? currentMatchingGroup.endIndex + 1 : currentIndex + 1;
    if (nextIndex >= quiz.length && !completionTracked && noteId) {
      setCompletionTracked(true);
      void trackAnalyticsEvent({
        eventType: "ADAPTIVE_PRACTICE_COMPLETED",
        entityId: adaptiveQuiz?.sessionId,
        metadata: {
          scorePercentage: quiz.length > 0 ? Math.round((score / quiz.length) * 100) : 0,
          totalQuestions: quiz.length,
        },
      });
      const durationSeconds = sessionStartedAt
        ? Math.max(0, Math.round((Date.now() - sessionStartedAt) / 1000))
        : undefined;
      if (adaptiveQuiz?.sessionId) {
        const correctConceptNames = quiz
          .filter((question, index) => {
            const selected = question.questionFormat === "MULTI_SELECT"
              ? selectedMultiChoices[index]
              : selectedChoices[index];
            return isQuizSelectionCorrect(question, selected);
          })
          .map((question) => question.concept)
          .filter((concept): concept is string => Boolean(concept?.trim()));
        const completeRequest: Parameters<typeof completeAdaptivePracticeSession>[1] = {
          correctAnswers: score,
          totalQuestions: quiz.length,
          durationSeconds,
        };
        if (correctConceptNames.length > 0) {
          completeRequest.correctConceptNames = correctConceptNames;
        }
        setNextStepResponse(null);
        void completeAdaptivePracticeSession(adaptiveQuiz.sessionId, completeRequest)
          .then((completed) => {
            setCompletionResult(completed);
            setCompletionSignalLoaded(true);
            return getPostSessionNextStep(adaptiveQuiz.studyPackId);
          })
          .then(setNextStepResponse)
          .catch(() => {
            // Completion and next-step persistence should not block adaptive practice flow.
            setCompletionSignalLoaded(true);
            setNextStepResponse(null);
          });
      } else {
        // No session id to complete against (e.g. a response applied without one) — there is no
        // first-quiz-ever signal to wait for, so fall back to the generic feedback panel immediately
        // instead of leaving it permanently unrendered.
        setCompletionSignalLoaded(true);
      }
    }
    setCurrentIndex(nextIndex);
  };

  return (
    <main className="mx-auto w-full max-w-3xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        {adaptiveQuizActive ? (
          <>
            <p className="text-sm font-medium text-foreground/80">Adaptive Practice in progress</p>
            <Button type="button" variant="outline" size="sm" onClick={() => requestLeave()}>
              Leave Quiz
            </Button>
          </>
        ) : (
          <BackLink href={noteDetailHref} label="Note" />
        )}
      </div>

      {adaptiveGenerationLocked ? (
        <QuizGenerationOverlay />
      ) : null}

      {loading ? (
        <AdaptivePracticeLoading />
      ) : showLimitReachedState ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">You’ve reached your quiz limit for this month</h1>
          <p className="text-sm text-foreground/75">Your Adaptive Practice limit resets on your next billing cycle.</p>
          <BackLink href={noteDetailHref} label="Note" />
        </Card>
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not generate adaptive practice</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={() => void loadAdaptiveQuiz(true)}>
              Try Again
            </Button>
          </div>
        </Card>
      ) : premiumLocked ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Adaptive Practice unavailable</h1>
          <p className="text-sm text-foreground/75">
            Adaptive Practice is not available on your current plan.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={() => openAdaptivePracticePaywall("adaptive_practice_page_card")}>
              See upgrade options
            </Button>
          </div>
        </Card>
      ) : adaptiveQuiz?.status === "FAILED" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Could not generate adaptive practice</h1>
          <p className="text-sm text-foreground/75">
            {adaptiveQuiz.message}
          </p>
          <Button
            type="button"
            className="w-full sm:w-auto"
            onClick={() => void handleStartAdaptivePractice()}
            disabled={adaptiveGenerationLocked}
          >
            {adaptiveGenerationLocked ? "Starting..." : "Try Again"}
          </Button>
        </Card>
      ) : !adaptiveQuiz || (!hasQuestions && adaptiveQuiz.weakConcepts.length === 0) ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Practice Weak Areas</h1>
          <p className="text-sm text-foreground/75">
            {adaptiveQuiz?.message ?? "Adaptive practice is unavailable right now."}
          </p>
          <Link href={note ? `/notes/${note.id}/quick-review` : "/dashboard"} className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              Start Quick Review
            </Button>
          </Link>
        </Card>
      ) : !hasQuestions || !quizStarted ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">{adaptiveQuiz.title}</h1>
          <div className="rounded-md border border-blue-500/30 bg-blue-500/10 p-3 text-sm text-foreground/85">
            <p className="font-medium text-foreground">
              Focusing on concepts you need to improve.
            </p>
          </div>
          <div className="space-y-2 rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
            <p className="font-medium text-foreground">Weak concepts:</p>
            {adaptiveQuiz.weakConcepts.length > 0 ? (
              <ul className="list-disc space-y-1 pl-5">
                {adaptiveQuiz.weakConcepts.map((concept) => (
                  <li key={`weak-concept-${concept}`}>{concept}</li>
                ))}
              </ul>
            ) : (
              <p>No specific weak concepts were found. This set still focuses on recent review gaps.</p>
            )}
          </div>
          <Button
            type="button"
            className="w-full sm:w-auto"
            onClick={() => void handleStartAdaptivePractice()}
            disabled={adaptiveGenerationLocked}
          >
            {adaptiveGenerationLocked ? "Starting..." : "Start Adaptive Practice"}
          </Button>
        </Card>
      ) : isComplete ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice Complete
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">{adaptiveQuiz.title}</h1>
          <p className="text-sm text-foreground/75">
            Score: {score} / {quiz.length} ({scorePercentage}%)
          </p>
          <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/85">
            {completionMessage}
          </div>
          {adaptiveQuiz.weakConcepts.length > 0 ? (
            <div className="rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-foreground/80">
              <p className="text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
                Targeted Weak Areas
              </p>
              <ul className="mt-2 list-disc space-y-1 pl-5">
                {adaptiveQuiz.weakConcepts.map((concept) => (
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
            <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/75">
              <p className="font-medium text-foreground">Targeted Weak Areas</p>
              <p className="mt-1">
                No targeted weak areas were attached to this set. Generate a new set or review your answers to keep practicing.
              </p>
            </div>
          )}
          <ReviewCommitmentPrompt
            isFirstCompletedSessionEver={completionResult?.isFirstCompletedSessionEver}
            noteId={note?.id ?? null}
          />
          {hasNextStepGuidance ? (
            <ResultGuidanceGroup label="What to do next" testId="adaptive-next-step-guidance">
              <PostSessionNextStep
                response={nextStepResponse}
                currentPlan={currentPlan}
                noteId={note?.id ?? null}
                onOpenPaywall={() => openAdaptivePracticePaywall("adaptive_practice_results_next_step")}
                contained
              />
              {nextStepResponse?.goalNudge ? (
                <GoalNudgeCard goalNudge={nextStepResponse.goalNudge} noteId={note?.id ?? null} contained />
              ) : null}
              <WeeklyPacingEchoCard
                weeksRemaining={weeklyPacingWeeksRemaining}
                goalLabel={getCollectionLabels(getAuthUser()?.profileType ?? null).goalSingular}
                contained
              />
            </ResultGuidanceGroup>
          ) : null}
          {hasCompanionGuidance ? (
            <ResultGuidanceGroup label="Companion guidance" testId="adaptive-companion-guidance">
              <CompanionResultBridgeCard
                companion={primaryCollectionCompanion}
                reviewSetLabel={getCollectionLabels(getAuthUser()?.profileType ?? null).singular}
                contained
              />
              <TwiceMissedAskCompanionCard
                twiceMissedConcepts={completionResult?.twiceMissedConcepts ?? []}
                currentPlan={currentPlan as AppPlanType}
                primaryCollectionId={primaryCollectionId}
                companion={primaryCollectionCompanion}
                contained
              />
            </ResultGuidanceGroup>
          ) : null}
          <div className="flex flex-col gap-2 sm:flex-row">
            {nextStepResponse === null ? (
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleStartAdaptivePractice()}
                disabled={adaptiveGenerationLocked}
              >
                {adaptiveGenerationLocked ? "Starting..." : "Generate New Set"}
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => setShowAnswerReview((previous) => !previous)}
            >
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
              planType={currentPlan}
            />
          ) : null}
          {completionSignalLoaded ? (
            <QuizFeedbackPanel
              quizLabel="Adaptive Practice"
              noteTitle={note?.title}
              section={showAnswerReview ? "review" : "results"}
              isFirstCompletedSessionEver={completionResult?.isFirstCompletedSessionEver}
              isSecondCompletedSessionEver={completionResult?.isSecondCompletedSessionEver}
              userId={getAuthUser()?.id}
            />
          ) : null}
        </Card>
      ) : (
        <div className="space-y-4">
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Adaptive Practice
            </p>
            <h1 className="text-xl font-semibold sm:text-2xl">{adaptiveQuiz.title}</h1>
            <p className="text-sm text-foreground/75">
              New follow-up practice based on your weak areas.
            </p>
            {adaptiveQuiz.weakConcepts.length > 0 ? (
              <p className="text-sm text-foreground/75">
                Focus concepts: {adaptiveQuiz.weakConcepts.join(", ")}
              </p>
            ) : null}
            <p className="text-sm text-foreground/75">
              Question {currentIndex + 1} of {quiz.length}
            </p>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            {currentRationales.length > 0 ? (
              <div className="flex flex-wrap gap-2" aria-label="Why these questions">
                {currentRationales.map((rationale) => (
                  <p
                    key={rationale}
                    className="w-fit max-w-full rounded-full border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-medium leading-relaxed text-blue-700 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-300"
                  >
                    {rationale}
                  </p>
                ))}
              </div>
            ) : null}
            {currentMatchingGroup ? (
              <QuizMatchingGroup
                items={currentMatchingGroup.items}
                groupStartIndex={currentMatchingGroup.startIndex}
                selectedChoices={selectedChoices}
                revealAnswer={hasAnsweredCurrent}
                onSelectChoice={handleSelectMatchingChoice}
              />
            ) : currentQuestion ? (
              <>
                <h2 className="text-lg font-semibold">
                  {currentIndex + 1}. <QuizQuestionText text={currentQuestion.question} />
                </h2>
                <QuizChoiceList
                  questionKey={currentQuestion.question}
                  choices={currentQuestion.choices}
                  correctIndex={resolveQuizCorrectIndex(currentQuestion)}
                  correctIndices={currentQuestion.correctIndices}
                  questionFormat={currentQuestion.questionFormat}
                  selectedChoiceIndex={selectedChoiceIndex}
                  selectedMultiChoiceIndices={selectedMultiChoiceIndices}
                  revealAnswer={hasAnsweredCurrent && !currentQuestionIsMultiSelect}
                  onSelectChoice={handleSelectChoice}
                  onSelectMultiChoices={handleSelectMultiChoices}
                />
              </>
            ) : null}

            {hasAnsweredCurrent && currentQuestion && !currentQuestionIsMultiSelect && !currentMatchingGroup ? (
              <div className="space-y-3 rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
                <p>
                  <span className="font-medium text-foreground">Explanation:</span>{" "}
                  {renderMathText(currentQuestion.explanation)}
                </p>
                {hasComputationalWorkingSolution(currentQuestion) ? (
                  <QuizWorkingSolution
                    workingSolution={currentQuestion.workingSolution}
                    planType={currentPlan}
                  />
                ) : null}
              </div>
            ) : null}

            <div className="flex justify-stretch sm:justify-end">
              <Button type="button" className="w-full sm:w-auto" onClick={handleNext} disabled={!hasAnsweredCurrent}>
                {currentIndex + 1 >= quiz.length ? "Finish Adaptive Practice" : "Next Question"}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <PaywallModal
        isOpen={showPremiumPaywall}
        variant="adaptive-practice"
        source="adaptive_practice_page"
        onClose={() => setShowPremiumPaywall(false)}
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
