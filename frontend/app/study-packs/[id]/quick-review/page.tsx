"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { Trophy } from "lucide-react";
import { PaywallModal, type PaywallModalVariant } from "@/components/billing/paywall-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { QuizAnswerReview } from "@/components/study-pack/quiz-answer-review";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { useQuizSessionGuard } from "@/components/study-pack/quiz-session-guard";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  completeProductOnboarding,
  completeQuickReviewSession,
  forfeitQuickReviewSession,
  generateQuickReviewStudyTip,
  getMyStudyPack,
  getNote,
  saveQuickReviewConfidence,
  startQuickReviewSession,
  trackAnalyticsEvent,
  updateQuickReviewSessionProgress,
  type NoteResponse,
  type QuickReviewConfidenceLevel,
  type QuickReviewSessionStartResponse,
  type QuickReviewSessionSummaryResponse,
  type QuickReviewStudyTipRequest,
} from "@/lib/api";
import {
  clearFirstStudyOnboardingStep,
  getFirstStudyOnboardingStep,
  hasPendingFirstStudyOnboarding,
} from "@/lib/first-study-onboarding";
import {
  isQuizSelectionCorrect,
  resolveQuizCorrectAnswer,
  resolveQuizCorrectIndex,
  serializeSelectedChoiceIndexRecord,
  toSelectedChoiceIndexRecord,
} from "@/lib/quiz";

type QuickReviewPhase = "initial" | "retry-transition" | "retry" | "complete";
type SessionStatePayload = {
  selectedChoices?: Record<string, number> | Record<string, string>;
  retryQuestionIndexes?: number[];
  activeQuestionIndexes?: number[];
  roundSelections?: Record<string, number> | Record<string, string>;
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
  return "Nice attempt. Let's review the concepts and try again.";
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
}: {
  score: number;
  totalQuestions: number;
  scorePercentage: number;
}) {
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
  const [selectedChoices, setSelectedChoices] = useState<Record<number, number>>({});
  const [retryCount, setRetryCount] = useState(0);
  const [completionTracked, setCompletionTracked] = useState(false);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [sessionStartedAt, setSessionStartedAt] = useState<number | null>(null);
  const [persistedResult, setPersistedResult] = useState<QuickReviewSessionSummaryResponse | null>(null);
  const [recentSessions, setRecentSessions] = useState<QuickReviewSessionSummaryResponse[]>([]);
  const [studyTip, setStudyTip] = useState<string | null>(null);
  const [completingSession, setCompletingSession] = useState(false);
  const [confidenceLevel, setConfidenceLevel] = useState<QuickReviewConfidenceLevel | null>(null);
  const [savingConfidence, setSavingConfidence] = useState(false);
  const [confidenceAcknowledged, setConfidenceAcknowledged] = useState(false);
  const [confidenceError, setConfidenceError] = useState<string | null>(null);
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [activePaywallModal, setActivePaywallModal] = useState<PaywallModalVariant | null>(null);
  const [showCompletionGuide, setShowCompletionGuide] = useState(false);
  const [showAnswerReview, setShowAnswerReview] = useState(false);
  const loadedNoteIdRef = useRef<string | null>(null);
  const legacyRedirectTargetRef = useRef<string | null>(null);

  const noteId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);
  const queryString = useMemo(() => searchParams.toString(), [searchParams]);
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
    setSelectedChoices({});
    setRetryCount(0);
    setCompletionTracked(false);
    setPersistedResult(null);
    setStudyTip(null);
    setCompletingSession(false);
    setConfidenceLevel(null);
    setSavingConfidence(false);
    setConfidenceAcknowledged(false);
    setConfidenceError(null);
    setShowAnswerReview(false);
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
      const detail = await getNote(noteId);
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
  }, [noteId, pathname, queryString, resetQuickReviewState, router]);

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
  const selectedChoiceIndex = currentQuestionIndex !== null ? roundSelections[currentQuestionIndex] ?? null : null;
  const hasAnsweredCurrent = selectedChoiceIndex !== null;
  const score = useMemo(
    () =>
      quiz.reduce((count, item, index) => {
        return isQuizSelectionCorrect(item, selectedChoices[index]) ? count + 1 : count;
      }, 0),
    [quiz, selectedChoices],
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
        if (isQuizSelectionCorrect(item, selectedChoices[index])) {
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
  }, [quiz, selectedChoices]);
  const weakConcepts = useMemo(() => {
    const concepts = quiz
      .map((item, index) => {
        if (isQuizSelectionCorrect(item, selectedChoices[index])) {
          return null;
        }
        const concept = item.concept?.trim();
        return concept ? concept : null;
      })
      .filter((concept): concept is string => concept !== null);
    return Array.from(new Set(concepts));
  }, [quiz, selectedChoices]);
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
  const showAdaptiveGuidedCta = isStruggling;
  const showChallengeGuidedCta = !isStruggling;
  const noteDetailHref = useMemo(() => (note ? `/notes/${note.id}` : "/library"), [note]);
  const showPracticeAgainPrimary = !isPerfectScore && (!showAdaptiveGuidedCta || !note?.adaptivePracticeAvailable);

  useEffect(() => {
    const syncAuthState = () => {
      const authUser = getAuthUser();
      setIsEmailVerified(Boolean(authUser?.emailVerifiedAt));
    };
    syncAuthState();
    window.addEventListener("studysnap-auth-change", syncAuthState);
    return () => {
      window.removeEventListener("studysnap-auth-change", syncAuthState);
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

  const persistProgress = useCallback((next: {
    currentQuestionIndex: number;
    currentRound: "INITIAL" | "RETRY";
    retryCount: number;
    selectedChoices: Record<number, number>;
    retryQuestionIndexes: number[];
    activeQuestionIndexes: number[];
    roundSelections: Record<number, number>;
  }) => {
    if (!currentSessionId) {
      return;
    }
    const sessionState: SessionStatePayload = {
      selectedChoices: serializeSelectedChoiceIndexRecord(next.selectedChoices),
      retryQuestionIndexes: next.retryQuestionIndexes,
      activeQuestionIndexes: next.activeQuestionIndexes,
      roundSelections: serializeSelectedChoiceIndexRecord(next.roundSelections),
    };
    void updateQuickReviewSessionProgress(currentSessionId, {
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
      retryQuestionIndexes,
      activeQuestionIndexes,
      roundSelections,
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
    selectedChoices,
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
        const restoredRetryQuestionIndexes = toNumberArray(state.retryQuestionIndexes);
        const restoredRoundSelections = toSelectedChoiceIndexRecord(state.roundSelections, quiz);
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
        setRetryQuestionIndexes(retryIndexes);
        setActiveQuestionIndexes(activeIndexes.length > 0 ? activeIndexes : allIndexes);
        setRoundSelections(restoredRoundSelections);
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
    } catch {
      // Session persistence errors should not block the review experience.
    } finally {
      setCompletionTracked(true);
      setCompletingSession(false);
    }
  }, [completingSession, completionTracked, currentSessionId, retryCount, score, sessionStartedAt, totalQuestions, weakConcepts]);

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
    void generateQuickReviewStudyTip(note.id, {
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
    persistProgress({
      currentQuestionIndex: currentRoundIndex,
      currentRound: currentRoundType,
      retryCount,
      selectedChoices: nextSelectedChoices,
      retryQuestionIndexes,
      activeQuestionIndexes,
      roundSelections: nextRoundSelections,
    });
  };

  const handleNext = () => {
    if (!hasAnsweredCurrent) {
      return;
    }
    const isLastInRound = currentRoundIndex + 1 >= activeQuestionIndexes.length;
    if (!isLastInRound) {
      const nextRoundIndex = currentRoundIndex + 1;
      setCurrentRoundIndex(nextRoundIndex);
      persistProgress({
        currentQuestionIndex: nextRoundIndex,
        currentRound: currentRoundType,
        retryCount,
        selectedChoices,
        retryQuestionIndexes,
        activeQuestionIndexes,
        roundSelections,
      });
      return;
    }

    if (phase === "initial") {
      const incorrectIndexes = activeQuestionIndexes.filter((index) => !isQuizSelectionCorrect(quiz[index], selectedChoices[index]));
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
        retryQuestionIndexes: incorrectIndexes,
        activeQuestionIndexes,
        roundSelections,
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
    setRoundSelections({});
    persistProgress({
      currentQuestionIndex: 0,
      currentRound: "RETRY",
      retryCount,
      selectedChoices,
      retryQuestionIndexes,
      activeQuestionIndexes: retryQuestionIndexes,
      roundSelections: {},
    });
  };

  const handleFinishReview = () => {
    setPhase("complete");
    void completeSessionIfNeeded(0);
  };

  const handleRetry = useCallback(() => {
    const allIndexes = quiz.map((_, index) => index);
    resetQuickReviewState(allIndexes);
    setSessionStartedAt(Date.now());
    if (note) {
      void startQuickReviewSession(note.id)
        .then((result: QuickReviewSessionStartResponse) => {
          if (!result.sessionId) {
            setCurrentSessionId(null);
            return;
          }
          setCurrentSessionId(result.sessionId);
        })
        .catch(() => setCurrentSessionId(null));
    }
  }, [note, quiz, resetQuickReviewState]);

  const handleSelectConfidence = useCallback(async (level: QuickReviewConfidenceLevel) => {
    if (!currentSessionId || savingConfidence || completingSession || !completionTracked) {
      return;
    }

    setSavingConfidence(true);
    setConfidenceError(null);
    try {
      const updated = await saveQuickReviewConfidence(currentSessionId, level);
      setPersistedResult(updated);
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
    <main className="mx-auto w-full max-w-3xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        {quizSessionActive ? (
          <>
            <p className="text-sm font-medium text-foreground/80">Quick Review in progress</p>
            <Button type="button" variant="outline" size="sm" onClick={() => requestLeave()}>
              Leave Quiz
            </Button>
          </>
        ) : (
          <BackLink href={noteDetailHref} label="Note" />
        )}
      </div>

      {loading || sessionInitializing ? (
        <QuickReviewLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
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
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">No quiz questions available</h1>
          <p className="text-sm text-foreground/75">
            This note does not have quiz questions yet. Generate a Study Pack to try Quick Review.
          </p>
          <BackLink href={noteDetailHref} label="Back to Note" />
        </Card>
      ) : note && !currentSessionId ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Quick Review not started</h1>
          <p className="text-sm text-foreground/75">
            Start Quick Review from the note detail page to create a session.
          </p>
          <BackLink href={noteDetailHref} label="Back to Note" />
        </Card>
      ) : note && isComplete ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Quick Review Complete
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Your results</h1>
          <div className="space-y-2 text-sm text-foreground/75">
            <div className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium ${performanceBadge.className}`}>
              {performanceBadge.label}
            </div>
            <ScoreProgressBlock score={score} totalQuestions={totalQuestions} scorePercentage={scorePercentage} />
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
            {isPerfectScore ? (
              <div className="rounded-md border border-emerald-500/40 bg-emerald-500/10 p-3">
                <div className="mb-1 flex items-center gap-2 text-emerald-700 dark:text-emerald-300">
                  <Trophy className="h-4 w-4" aria-hidden="true" />
                  <p className="font-medium">Excellent work! You mastered this topic.</p>
                </div>
                <p>Try another note to continue learning.</p>
              </div>
            ) : (
              <p>{scoreFeedback}</p>
            )}
            {!isPerfectScore && incorrectCount > 0 && displayedWeakConcepts.length > 0 ? (
              <div className="space-y-1 rounded-md border border-amber-500/30 bg-amber-500/10 p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
                  Weak Areas
                </p>
                <p>You may want to review these concepts:</p>
                <ul className="list-disc space-y-1 pl-5">
                  {displayedWeakConcepts.map((concept) => (
                    <li key={concept}>{concept}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            {!isPerfectScore && incorrectCount > 0 && !note?.adaptivePracticeAvailable ? (
              <div className="space-y-2 rounded-md border border-border bg-muted/40 p-3">
                <p className="text-sm text-foreground/85">
                  Adaptive Practice is available on Premium. Challenge Quiz stays available up to your monthly limit.
                </p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => openAdaptivePracticePaywall("quick_review_results_upgrade")}
                >
                    Upgrade to Premium
                </Button>
              </div>
            ) : null}
            {displayedRetryCount > 0 ? <p>Retry attempts: {displayedRetryCount}</p> : null}
            {studyTip ? (
              <div className="space-y-2 rounded-md border border-blue-500/30 bg-blue-500/10 p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                  Study Tip
                </p>
                <p className="whitespace-normal wrap-break-word leading-relaxed text-foreground/85">{studyTip}</p>
              </div>
            ) : null}
            <div className="space-y-2 rounded-md border border-border bg-background p-3">
              <p className="text-sm font-medium text-foreground">How confident did you feel about this topic?</p>
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
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            {showAdaptiveGuidedCta && note?.adaptivePracticeAvailable ? (
              <Link href={`/notes/${note.id}/adaptive-practice`} className="w-full sm:w-auto">
                <Button type="button" className="w-full sm:w-auto">
                  Practice Weak Concepts
                </Button>
              </Link>
            ) : null}
            {showPracticeAgainPrimary ? (
              <Button type="button" className="w-full sm:w-auto" onClick={handleRetry}>
                Practice Again
              </Button>
            ) : null}
            {showChallengeGuidedCta ? (
              <Link href={`/notes/${note.id}/challenge-quiz`} className="w-full sm:w-auto">
                <Button type="button" className="w-full sm:w-auto">
                  Start Challenge Quiz
                </Button>
              </Link>
            ) : null}
            {showAdaptiveGuidedCta && !note?.adaptivePracticeAvailable ? (
              <Button
                type="button"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={() => openAdaptivePracticePaywall("quick_review_results_practice_weak_concepts")}
              >
                Unlock Practice Weak Concepts
              </Button>
            ) : null}
            {!isPerfectScore && !showPracticeAgainPrimary ? (
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={handleRetry}>
                Practice Again
              </Button>
            ) : null}
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShowAnswerReview((previous) => !previous)}>
              {showAnswerReview ? "Hide Answer Review" : "Review Answers"}
            </Button>
          </div>
          <div className="pt-1">
            <BackLink href={noteDetailHref} label="Back to Note" />
          </div>
          {showAnswerReview ? (
            <QuizAnswerReview quiz={quiz} selectedChoices={selectedChoices} className="mt-2" />
          ) : null}
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
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Quick Review
            </p>
            <h1 className="text-xl font-semibold sm:text-2xl">{note.title ?? "Untitled note"}</h1>
            <p className="text-sm text-foreground/75">
              {phase === "retry"
                ? `Retry question ${currentRoundIndex + 1} of ${activeQuestionIndexes.length}`
                : `Question ${currentRoundIndex + 1} of ${totalQuestions}`}
            </p>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold">
              {(currentQuestionIndex ?? 0) + 1}. {currentQuestion.question}
            </h2>
            <QuizChoiceList
              questionKey={currentQuestion.question}
              choices={currentQuestion.choices}
              correctIndex={resolveQuizCorrectIndex(currentQuestion)}
              selectedChoiceIndex={selectedChoiceIndex}
              revealAnswer={hasAnsweredCurrent}
              onSelectChoice={handleSelectChoice}
            />

            {hasAnsweredCurrent ? (
              <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
                <p>
                  <span className="font-medium text-foreground">Explanation:</span>{" "}
                  {currentQuestion.explanation}
                </p>
              </div>
            ) : null}

            <div className="flex justify-stretch sm:justify-end">
              <Button type="button" className="w-full sm:w-auto" onClick={handleNext} disabled={!hasAnsweredCurrent}>
                {currentRoundIndex + 1 === activeQuestionIndexes.length
                  ? phase === "retry"
                    ? "Finish Retry"
                    : "Finish Quick Review"
                  : "Next Question"}
              </Button>
            </div>
          </Card>
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
    </main>
  );
}
