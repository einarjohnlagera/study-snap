"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { getAuthUser } from "@/lib/auth";
import { getMyStudyPack, trackQuickReviewActivity, type StudyPackResponse } from "@/lib/api";

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

export default function QuickReviewPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const [studyPack, setStudyPack] = useState<StudyPackResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedChoices, setSelectedChoices] = useState<Record<number, string>>({});
  const [completionTracked, setCompletionTracked] = useState(false);

  const studyPackId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);

  const loadStudyPack = useCallback(async () => {
    if (!studyPackId) {
      setError("Study Pack not found.");
      setLoading(false);
      return;
    }

    const authUser = getAuthUser();
    if (!authUser) {
      router.replace("/login");
      return;
    }
    if (!authUser.emailVerifiedAt) {
      router.replace("/verify-email");
      return;
    }
    if (!authUser.profileType) {
      router.replace("/onboarding");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const detail = await getMyStudyPack(studyPackId);
      setStudyPack(detail);
      setCurrentIndex(0);
      setSelectedChoices({});
      setCompletionTracked(false);
      try {
        await trackQuickReviewActivity(studyPackId, "STARTED_QUICK_REVIEW");
      } catch {
        // Activity tracking failures should not block review flow.
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load this Study Pack.";
      setError(message);
      setStudyPack(null);
    } finally {
      setLoading(false);
    }
  }, [router, studyPackId]);

  useEffect(() => {
    void loadStudyPack();
  }, [loadStudyPack]);

  const quiz = studyPack?.quiz ?? [];
  const totalQuestions = quiz.length;
  const isNotFound = error?.toLowerCase().includes("not found") ?? false;
  const isComplete = totalQuestions > 0 && currentIndex >= totalQuestions;
  const currentQuestion = !isComplete ? quiz[currentIndex] : null;
  const selectedChoice = currentQuestion ? selectedChoices[currentIndex] ?? null : null;
  const hasAnsweredCurrent = Boolean(selectedChoice);
  const score = useMemo(
    () =>
      quiz.reduce((count, item, index) => {
        return selectedChoices[index] === item.answer ? count + 1 : count;
      }, 0),
    [quiz, selectedChoices],
  );

  useEffect(() => {
    if (!isComplete || completionTracked || !studyPackId) {
      return;
    }

    let isMounted = true;
    void (async () => {
      try {
        await trackQuickReviewActivity(studyPackId, "COMPLETED_QUICK_REVIEW");
      } catch {
        // Activity tracking failures should not block review flow.
      } finally {
        if (isMounted) {
          setCompletionTracked(true);
        }
      }
    })();

    return () => {
      isMounted = false;
    };
  }, [completionTracked, isComplete, studyPackId]);

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
    setCurrentIndex((prev) => prev + 1);
  };

  const handleRetry = () => {
    setCurrentIndex(0);
    setSelectedChoices({});
    setCompletionTracked(false);
    if (studyPackId) {
      void trackQuickReviewActivity(studyPackId, "STARTED_QUICK_REVIEW").catch(() => {
        // Activity tracking failures should not block review flow.
      });
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

      {loading ? (
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
      ) : studyPack && isComplete ? (
        <Card className="space-y-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Quick Review Complete
          </p>
          <h1 className="text-2xl font-semibold">Your results</h1>
          <p className="text-sm text-foreground/75">
            Score: {score} / {totalQuestions}
          </p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" onClick={handleRetry}>
              Retry Quick Review
            </Button>
            <Link href={`/study-packs/${studyPack.id}`}>
              <Button type="button" variant="outline">
                Return to Study Pack
              </Button>
            </Link>
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
              Question {currentIndex + 1} of {totalQuestions}
            </p>
          </Card>

          <Card className="space-y-4">
            <h2 className="text-lg font-semibold">
              {currentIndex + 1}. {currentQuestion.question}
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
                {currentIndex + 1 === totalQuestions ? "Finish Quick Review" : "Next Question"}
              </Button>
            </div>
          </Card>
        </div>
      ) : null}
    </main>
  );
}
