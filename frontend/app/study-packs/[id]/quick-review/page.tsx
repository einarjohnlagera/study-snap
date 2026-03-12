"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Trophy } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import {
  completeQuickReviewSession,
  generateQuickReviewStudyTip,
  getMyStudyPack,
  startQuickReviewSession,
  updateQuickReviewSessionProgress,
  type QuickReviewSessionStartResponse,
  type QuickReviewSessionSummaryResponse,
  type QuickReviewStudyTipRequest,
  type StudyPackResponse,
} from "@/lib/api";

type QuickReviewPhase = "initial" | "retry-transition" | "retry" | "complete";
type SessionStatePayload = {
  selectedChoices?: Record<string, string>;
  retryQuestionIndexes?: number[];
  activeQuestionIndexes?: number[];
  roundSelections?: Record<string, string>;
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

function toChoiceRecord(value: unknown): Record<number, string> {
  if (!value || typeof value !== "object") {
    return {};
  }
  const entries = Object.entries(value as Record<string, unknown>)
    .filter(([, v]) => typeof v === "string")
    .map(([k, v]) => [Number(k), v as string] as const)
    .filter(([k]) => Number.isInteger(k) && k >= 0);
  return Object.fromEntries(entries);
}

export default function QuickReviewPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const [studyPack, setStudyPack] = useState<StudyPackResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionInitializing, setSessionInitializing] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [phase, setPhase] = useState<QuickReviewPhase>("initial");
  const [activeQuestionIndexes, setActiveQuestionIndexes] = useState<number[]>([]);
  const [currentRoundIndex, setCurrentRoundIndex] = useState(0);
  const [retryQuestionIndexes, setRetryQuestionIndexes] = useState<number[]>([]);
  const [roundSelections, setRoundSelections] = useState<Record<number, string>>({});
  const [selectedChoices, setSelectedChoices] = useState<Record<number, string>>({});
  const [retryCount, setRetryCount] = useState(0);
  const [completionTracked, setCompletionTracked] = useState(false);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [sessionStartedAt, setSessionStartedAt] = useState<number | null>(null);
  const [persistedResult, setPersistedResult] = useState<QuickReviewSessionSummaryResponse | null>(null);
  const [recentSessions, setRecentSessions] = useState<QuickReviewSessionSummaryResponse[]>([]);
  const [studyTip, setStudyTip] = useState<string | null>(null);
  const [completingSession, setCompletingSession] = useState(false);

  const studyPackId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);

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
  }, []);

  const loadStudyPack = useCallback(async () => {
    if (!studyPackId) {
      setError("Study Pack not found.");
      setLoading(false);
      return;
    }

    if (!requireVerifiedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const detail = await getMyStudyPack(studyPackId);
      setStudyPack(detail);
      resetQuickReviewState(detail.quiz.map((_, index) => index));
      setRecentSessions([]);
      setCurrentSessionId(null);
      setSessionStartedAt(Date.now());
      setSessionInitializing(true);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load this Study Pack.";
      setError(message);
      setStudyPack(null);
    } finally {
      setLoading(false);
    }
  }, [resetQuickReviewState, router, studyPackId]);

  useEffect(() => {
    void loadStudyPack();
  }, [loadStudyPack]);

  const quiz = useMemo(() => studyPack?.quiz ?? [], [studyPack]);
  const totalQuestions = quiz.length;
  const isNotFound = error?.toLowerCase().includes("not found") ?? false;
  const isComplete = phase === "complete";
  const currentQuestionIndex = currentRoundIndex < activeQuestionIndexes.length
    ? activeQuestionIndexes[currentRoundIndex]
    : null;
  const currentQuestion = currentQuestionIndex !== null ? quiz[currentQuestionIndex] : null;
  const selectedChoice = currentQuestionIndex !== null ? roundSelections[currentQuestionIndex] ?? null : null;
  const hasAnsweredCurrent = Boolean(selectedChoice);
  const score = useMemo(
    () =>
      quiz.reduce((count, item, index) => {
        return selectedChoices[index] === item.answer ? count + 1 : count;
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
  const isPerfectScore = totalQuestions > 0 && score === totalQuestions;
  const displayedRetryCount = persistedResult?.retryCount ?? retryCount;
  const currentRoundType = phase === "retry" ? "RETRY" : "INITIAL";
  const incorrectQuestionsForStudyTip = useMemo<QuickReviewStudyTipRequest["incorrectQuestions"]>(() => {
    return quiz
      .map((item, index) => {
        if (selectedChoices[index] === item.answer) {
          return null;
        }
        return {
          question: item.question,
          correctAnswer: item.answer,
          explanation: item.explanation,
        };
      })
      .filter((item): item is QuickReviewStudyTipRequest["incorrectQuestions"][number] => item !== null);
  }, [quiz, selectedChoices]);
  const weakConcepts = useMemo(() => {
    const concepts = quiz
      .map((item, index) => {
        if (selectedChoices[index] === item.answer) {
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

  const persistProgress = useCallback((next: {
    currentQuestionIndex: number;
    currentRound: "INITIAL" | "RETRY";
    retryCount: number;
    selectedChoices: Record<number, string>;
    retryQuestionIndexes: number[];
    activeQuestionIndexes: number[];
    roundSelections: Record<number, string>;
  }) => {
    if (!currentSessionId) {
      return;
    }
    const sessionState: SessionStatePayload = {
      selectedChoices: Object.fromEntries(Object.entries(next.selectedChoices).map(([k, v]) => [String(k), v])),
      retryQuestionIndexes: next.retryQuestionIndexes,
      activeQuestionIndexes: next.activeQuestionIndexes,
      roundSelections: Object.fromEntries(Object.entries(next.roundSelections).map(([k, v]) => [String(k), v])),
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

  useEffect(() => {
    if (!studyPack || !sessionInitializing) {
      return;
    }

    let isMounted = true;
    void (async () => {
      try {
        const started = await startQuickReviewSession(studyPack.id);
        if (!isMounted || !started.sessionId) {
          return;
        }

        const state = (started.sessionState ?? {}) as SessionStatePayload;
        const restoredSelectedChoices = toChoiceRecord(state.selectedChoices);
        const restoredRetryQuestionIndexes = toNumberArray(state.retryQuestionIndexes);
        const restoredRoundSelections = toChoiceRecord(state.roundSelections);
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
  }, [quiz, sessionInitializing, studyPack]);

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
    if (!isComplete || !studyPack) {
      return;
    }
    if (incorrectQuestionsForStudyTip.length === 0) {
      setStudyTip(null);
      return;
    }

    let isMounted = true;
    void generateQuickReviewStudyTip(studyPack.id, {
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
  }, [incorrectQuestionsForStudyTip, isComplete, studyPack]);

  const handleSelectChoice = (choice: string) => {
    if (!currentQuestion || currentQuestionIndex === null || hasAnsweredCurrent) {
      return;
    }
    const nextRoundSelections = {
      ...roundSelections,
      [currentQuestionIndex]: choice,
    };
    const nextSelectedChoices = {
      ...selectedChoices,
      [currentQuestionIndex]: choice,
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
      const incorrectIndexes = activeQuestionIndexes.filter((index) => selectedChoices[index] !== quiz[index]?.answer);
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

  const handleRetry = () => {
    const allIndexes = quiz.map((_, index) => index);
    resetQuickReviewState(allIndexes);
    setSessionStartedAt(Date.now());
    if (studyPackId) {
      void startQuickReviewSession(studyPackId)
        .then((result: QuickReviewSessionStartResponse) => {
          if (!result.sessionId) {
            setCurrentSessionId(null);
            return;
          }
          setCurrentSessionId(result.sessionId);
        })
        .catch(() => setCurrentSessionId(null));
    }
  };

  return (
    <main className="mx-auto w-full max-w-3xl space-y-6 px-6 py-10">
      <div className="flex items-center justify-between gap-3">
        <Link
          href={studyPackId ? `/study-packs/${studyPackId}` : "/dashboard"}
          className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
        >
          Back to Study Pack
        </Link>
      </div>

      {loading || sessionInitializing ? (
        <QuickReviewLoading />
      ) : error ? (
        <Card className="space-y-4">
          <h1 className="text-2xl font-semibold">
            {isNotFound ? "Study Pack not found" : "Could not start Quick Review"}
          </h1>
          <p className="text-sm text-foreground/75">
            {isNotFound
              ? "This Study Pack is unavailable or does not belong to your account."
              : error}
          </p>
          <div className="flex flex-wrap gap-2">
            {!isNotFound ? (
              <Button type="button" onClick={() => void loadStudyPack()}>
                Retry
              </Button>
            ) : null}
            <Link href={studyPackId ? `/study-packs/${studyPackId}` : "/dashboard"}>
              <Button type="button" variant="outline">
                Back to Study Pack
              </Button>
            </Link>
          </div>
        </Card>
      ) : studyPack && totalQuestions === 0 ? (
        <Card className="space-y-4">
          <h1 className="text-2xl font-semibold">No quiz questions available</h1>
          <p className="text-sm text-foreground/75">
            This Study Pack does not have quiz questions yet. Generate another Study Pack to try Quick Review.
          </p>
          <Link href={`/study-packs/${studyPack.id}`}>
            <Button type="button" variant="outline">
              Back to Study Pack
            </Button>
          </Link>
        </Card>
      ) : studyPack && !currentSessionId ? (
        <Card className="space-y-4">
          <h1 className="text-2xl font-semibold">Quick Review not started</h1>
          <p className="text-sm text-foreground/75">
            Start Quick Review from the Study Pack detail page to create a session.
          </p>
          <Link href={`/study-packs/${studyPack.id}`}>
            <Button type="button" variant="outline">
              Back to Study Pack
            </Button>
          </Link>
        </Card>
      ) : studyPack && isComplete ? (
        <Card className="space-y-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Quick Review Complete
          </p>
          <h1 className="text-2xl font-semibold">Your results</h1>
          <div className="space-y-2 text-sm text-foreground/75">
            <ScoreProgressBlock score={score} totalQuestions={totalQuestions} scorePercentage={scorePercentage} />
            {isPerfectScore ? (
              <div className="rounded-md border border-emerald-500/40 bg-emerald-500/10 p-3">
                <div className="mb-1 flex items-center gap-2 text-emerald-700 dark:text-emerald-300">
                  <Trophy className="h-4 w-4" aria-hidden="true" />
                  <p className="font-medium">Excellent work! You mastered this topic.</p>
                </div>
                <p>Try another Study Pack to continue learning.</p>
              </div>
            ) : (
              <p>{scoreFeedback}</p>
            )}
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
            {displayedRetryCount > 0 ? <p>Retry count: {displayedRetryCount}</p> : null}
            {displayedWeakConcepts.length > 0 ? (
              <div className="space-y-1 rounded-md border border-amber-500/30 bg-amber-500/10 p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
                  Weak Concepts
                </p>
                <ul className="list-disc space-y-1 pl-5">
                  {displayedWeakConcepts.map((concept) => (
                    <li key={concept}>{concept}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            {studyTip ? (
              <div className="space-y-2 rounded-md border border-blue-500/30 bg-blue-500/10 p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                  Study Tip
                </p>
                <p className="whitespace-normal break-words leading-relaxed text-foreground/85">{studyTip}</p>
              </div>
            ) : null}
          </div>
          <div className="flex flex-wrap gap-2">
            <Link href={`/study-packs/${studyPack.id}`}>
              <Button type="button">
                Back to Study Pack
              </Button>
            </Link>
            {!isPerfectScore ? (
              <Button type="button" variant="outline" onClick={handleRetry}>
                Practice Again
              </Button>
            ) : null}
          </div>
        </Card>
      ) : studyPack && phase === "retry-transition" ? (
        <Card className="space-y-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Quick Review Progress
          </p>
          <h1 className="text-2xl font-semibold">You're making progress.</h1>
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
          <div className="flex flex-wrap gap-2">
            <Button type="button" onClick={handleStartRetryRound} disabled={completingSession}>
              Retry Incorrect Questions
            </Button>
            <Button type="button" variant="outline" onClick={handleFinishReview} disabled={completingSession}>
              Finish Review
            </Button>
          </div>
        </Card>
      ) : studyPack && currentQuestion ? (
        <div className="space-y-4">
          <Card className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Quick Review
            </p>
            <h1 className="text-2xl font-semibold">{studyPack.title}</h1>
            <p className="text-sm text-foreground/75">
              {phase === "retry"
                ? `Retry question ${currentRoundIndex + 1} of ${activeQuestionIndexes.length}`
                : `Question ${currentRoundIndex + 1} of ${totalQuestions}`}
            </p>
          </Card>

          <Card className="space-y-4">
            <h2 className="text-lg font-semibold">
              {(currentQuestionIndex ?? 0) + 1}. {currentQuestion.question}
            </h2>
            <QuizChoiceList
              choices={currentQuestion.choices}
              correctAnswer={currentQuestion.answer}
              selectedChoice={selectedChoice}
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

            <div className="flex justify-end">
              <Button type="button" onClick={handleNext} disabled={!hasAnsweredCurrent}>
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
    </main>
  );
}
