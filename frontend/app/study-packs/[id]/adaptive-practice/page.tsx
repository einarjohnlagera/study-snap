"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { getAuthUser } from "@/lib/auth";
import { PLAN_BILLING_PATH } from "@/lib/plans";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  completeAdaptivePracticeSession,
  generateAdaptiveQuickReviewQuiz,
  getMyStudyPack,
  getNote,
  isEmailNotVerifiedError,
  type NoteResponse,
  type QuickReviewAdaptiveQuizResponse,
} from "@/lib/api";

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

export default function AdaptivePracticePage() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const requestInFlightRef = useRef(false);
  const loadedForNoteRef = useRef<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [adaptiveQuiz, setAdaptiveQuiz] = useState<QuickReviewAdaptiveQuizResponse | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedChoices, setSelectedChoices] = useState<Record<number, string>>({});
  const [completionTracked, setCompletionTracked] = useState(false);
  const [premiumLocked, setPremiumLocked] = useState(false);
  const [quizStarted, setQuizStarted] = useState(false);
  const [sessionStartedAt, setSessionStartedAt] = useState<number | null>(null);
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [showPremiumPaywall, setShowPremiumPaywall] = useState(false);

  const noteId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);
  const noteDetailHref = useMemo(() => (note ? `/notes/${note.id}` : "/library"), [note]);

  const loadAdaptiveQuiz = useCallback(async () => {
    if (requestInFlightRef.current) {
      return;
    }
    if (!noteId) {
      setError("Note not found.");
      setLoading(false);
      return;
    }

    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    requestInFlightRef.current = true;
    setError(null);
    setPremiumLocked(false);
    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      setAdaptiveQuiz(null);
      setError("Verify your email to use this feature.");
      setLoading(false);
      requestInFlightRef.current = false;
      return;
    }
    if (authUser?.planType !== "PREMIUM") {
      setAdaptiveQuiz(null);
      setPremiumLocked(true);
      setLoading(false);
      requestInFlightRef.current = false;
      return;
    }

    try {
      const detail = await getNote(noteId);
      if (detail.studyPackStatus !== "STUDY_PACK_READY") {
        setNote(detail);
        setError("Generate a Study Pack first.");
        setAdaptiveQuiz(null);
        return;
      }
      setNote(detail);
      const response = await generateAdaptiveQuickReviewQuiz(detail.id);
      setAdaptiveQuiz(response);
      setCurrentIndex(0);
      setSelectedChoices({});
      setCompletionTracked(false);
      setQuizStarted(false);
      setSessionStartedAt(null);
    } catch (err) {
      if (pathname.startsWith("/study-packs/")) {
        const byStudyPack = await getMyStudyPack(noteId).catch(() => null);
        if (byStudyPack?.noteId) {
          const nextQuery = searchParams.toString();
          router.replace(
            nextQuery
              ? `/notes/${byStudyPack.noteId}/adaptive-practice?${nextQuery}`
              : `/notes/${byStudyPack.noteId}/adaptive-practice`,
          );
          return;
        }
      }
      const message = isEmailNotVerifiedError(err)
        ? "Verify your email to use this feature."
        : err instanceof Error
          ? err.message
          : "Could not generate adaptive practice.";
      setError(message);
      setAdaptiveQuiz(null);
    } finally {
      setLoading(false);
      requestInFlightRef.current = false;
    }
  }, [noteId, pathname, router, searchParams]);

  useEffect(() => {
    if (!noteId) {
      return;
    }
    if (loadedForNoteRef.current === noteId) {
      return;
    }
    loadedForNoteRef.current = noteId;
    void loadAdaptiveQuiz();
  }, [loadAdaptiveQuiz, noteId]);

  const quiz = useMemo(() => adaptiveQuiz?.quiz ?? [], [adaptiveQuiz]);
  const hasQuestions = quiz.length > 0;
  const currentQuestion = hasQuestions ? quiz[currentIndex] : null;
  const selectedChoice = selectedChoices[currentIndex] ?? null;
  const hasAnsweredCurrent = Boolean(selectedChoice);
  const isComplete = hasQuestions && currentIndex >= quiz.length;
  const score = useMemo(() => {
    return quiz.reduce((count, question, index) => {
      return selectedChoices[index] === question.answer ? count + 1 : count;
    }, 0);
  }, [quiz, selectedChoices]);
  const scorePercentage = useMemo(() => {
    if (quiz.length === 0) {
      return 0;
    }
    return Number(((score / quiz.length) * 100).toFixed(0));
  }, [quiz.length, score]);
  const completionMessage = useMemo(() => {
    if (scorePercentage >= 80) {
      return "Great work. You're mastering this.";
    }
    return "Keep going - you're improving.";
  }, [scorePercentage]);

  const handleSelectChoice = (choice: string) => {
    if (!currentQuestion || hasAnsweredCurrent) {
      return;
    }
    setSelectedChoices((prev) => ({
      ...prev,
      [currentIndex]: choice,
    }));
  };

  const handleNext = () => {
    if (!hasAnsweredCurrent) {
      return;
    }
    const nextIndex = currentIndex + 1;
    if (nextIndex >= quiz.length && !completionTracked && noteId) {
      setCompletionTracked(true);
      const durationSeconds = sessionStartedAt
        ? Math.max(0, Math.round((Date.now() - sessionStartedAt) / 1000))
        : undefined;
      if (adaptiveQuiz?.sessionId) {
        void completeAdaptivePracticeSession(adaptiveQuiz.sessionId, {
          correctAnswers: score,
          totalQuestions: quiz.length,
          durationSeconds,
        }).catch(() => {
          // Completion persistence should not block adaptive practice flow.
        });
      }
    }
    setCurrentIndex(nextIndex);
  };

  return (
    <main className="mx-auto w-full max-w-3xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        <Link
          href={noteDetailHref}
          className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
        >
          Back to Note
        </Link>
      </div>

      {loading ? (
        <AdaptivePracticeLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not generate adaptive practice</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={() => void loadAdaptiveQuiz()}>
              Try Again
            </Button>
            <Link href={noteDetailHref} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Note
              </Button>
            </Link>
          </div>
        </Card>
      ) : premiumLocked ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Premium feature</h1>
          <p className="text-sm text-foreground/75">
            This feature is available in the Premium plan.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={() => setShowPremiumPaywall(true)}>
              See Premium options
            </Button>
            <Link href={noteDetailHref} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Note
              </Button>
            </Link>
          </div>
        </Card>
      ) : !adaptiveQuiz || !hasQuestions ? (
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
      ) : !quizStarted ? (
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
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button
              type="button"
              className="w-full sm:w-auto"
              onClick={() => {
                setQuizStarted(true);
                setSessionStartedAt(Date.now());
              }}
            >
              Start Adaptive Practice
            </Button>
            <Link href={noteDetailHref} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Note
              </Button>
            </Link>
          </div>
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
                  <li key={concept}>{concept}</li>
                ))}
              </ul>
            </div>
          ) : null}
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link href={noteDetailHref} className="w-full sm:w-auto">
              <Button type="button" className="w-full sm:w-auto">
                Back to Note
              </Button>
            </Link>
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => void loadAdaptiveQuiz()} disabled={loading}>
              Generate New Set
            </Button>
          </div>
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
            <h2 className="text-lg font-semibold">
              {currentIndex + 1}. {currentQuestion?.question}
            </h2>
            {currentQuestion ? (
              <QuizChoiceList
                choices={currentQuestion.choices}
                correctAnswer={currentQuestion.answer}
                selectedChoice={selectedChoice}
                revealAnswer={hasAnsweredCurrent}
                onSelectChoice={handleSelectChoice}
              />
            ) : null}

            {hasAnsweredCurrent && currentQuestion ? (
              <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
                <p>
                  <span className="font-medium text-foreground">Explanation:</span>{" "}
                  {currentQuestion.explanation}
                </p>
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
        onClose={() => setShowPremiumPaywall(false)}
        onUpgrade={() => {
          setShowPremiumPaywall(false);
          router.push(PLAN_BILLING_PATH);
        }}
      />
    </main>
  );
}
