"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import {
  getInProgressQuickReviewSession,
  getMyStudyPack,
  listRecentQuickReviewSessions,
  startQuickReviewSession,
  type QuickReviewSessionSummaryResponse,
  type StudyPackResponse,
} from "@/lib/api";

function StudyPackDetailLoading() {
  return (
    <div className="space-y-6">
      <Card className="space-y-3">
        <div className="h-4 w-28 animate-pulse rounded bg-foreground/10" />
        <div className="h-8 w-2/3 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-1/3 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-3">
        <div className="h-6 w-28 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-5/6 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-2/3 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-3">
        <div className="h-6 w-36 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-4">
        <div className="h-6 w-24 animate-pulse rounded bg-foreground/10" />
        {Array.from({ length: 3 }).map((_, index) => (
          <div key={`quiz-skeleton-${index}`} className="space-y-2">
            <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-1/2 animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-2/3 animate-pulse rounded bg-foreground/10" />
          </div>
        ))}
      </Card>
    </div>
  );
}

export default function StudyPackDetailPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const [studyPack, setStudyPack] = useState<StudyPackResponse | null>(null);
  const [recentSessions, setRecentSessions] = useState<QuickReviewSessionSummaryResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [startingQuickReview, setStartingQuickReview] = useState(false);
  const [hasInProgressQuickReview, setHasInProgressQuickReview] = useState(false);

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

    if (!requireVerifiedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const detail = await getMyStudyPack(studyPackId);
      setStudyPack(detail);
      try {
        const history = await listRecentQuickReviewSessions(studyPackId, 5);
        setRecentSessions(history);
        setHistoryError(null);
      } catch (historyErr) {
        const message = historyErr instanceof Error ? historyErr.message : "Could not load recent sessions.";
        setHistoryError(message);
        setRecentSessions([]);
      }
      try {
        const inProgress = await getInProgressQuickReviewSession(studyPackId);
        setHasInProgressQuickReview(Boolean(inProgress.sessionId));
      } catch {
        setHasInProgressQuickReview(false);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load this Study Pack.";
      setError(message);
      setStudyPack(null);
      setRecentSessions([]);
      setHistoryError(null);
      setHasInProgressQuickReview(false);
    } finally {
      setLoading(false);
    }
  }, [router, studyPackId]);

  useEffect(() => {
    void loadStudyPack();
  }, [loadStudyPack]);

  const isNotFound = error?.toLowerCase().includes("not found") ?? false;

  const handleStartQuickReview = async () => {
    if (!studyPack) {
      return;
    }
    setStartingQuickReview(true);
    try {
      const started = await startQuickReviewSession(studyPack.id);
      if (started.sessionId) {
        router.push(`/study-packs/${studyPack.id}/quick-review?sessionId=${started.sessionId}`);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not start Quick Review.";
      setHistoryError(message);
    } finally {
      setStartingQuickReview(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-6 py-10">
      <div className="flex items-center justify-between gap-3">
        <Link href="/dashboard" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
          Back to Dashboard
        </Link>
      </div>

      {loading ? (
        <StudyPackDetailLoading />
      ) : error ? (
        <Card className="space-y-4">
          <h1 className="text-2xl font-semibold">
            {isNotFound ? "Study Pack not found" : "Could not load this Study Pack"}
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
            <Link href="/dashboard">
              <Button type="button" variant="outline">
                Back to Dashboard
              </Button>
            </Link>
          </div>
        </Card>
      ) : studyPack ? (
        <div className="space-y-6">
          <Card className="space-y-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Study Pack
            </p>
            <h1 className="text-3xl font-semibold tracking-tight">{studyPack.title}</h1>
            <div className="flex flex-wrap gap-3 text-xs text-foreground/70">
              <span>{new Date(studyPack.createdAt).toLocaleString()}</span>
              <span>{studyPack.quiz.length} quiz questions</span>
            </div>
            {studyPack.tags.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {studyPack.tags.map((tag) => (
                  <span
                    key={tag}
                    className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            ) : null}
            <div>
              <Button type="button" variant="outline" onClick={() => void handleStartQuickReview()} disabled={startingQuickReview}>
                {startingQuickReview
                  ? (hasInProgressQuickReview ? "Resuming..." : "Starting...")
                  : (hasInProgressQuickReview ? "Resume Quick Review" : "Start Quick Review")}
              </Button>
            </div>
          </Card>

          <Card className="space-y-3">
            <h2 className="text-xl font-semibold">Summary</h2>
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/85">{studyPack.summary}</p>
          </Card>

          <Card className="space-y-3">
            <h2 className="text-xl font-semibold">Key Concepts</h2>
            <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
              {studyPack.keyConcepts.map((concept, index) => (
                <li key={`${studyPack.id}-concept-${index}`}>{concept}</li>
              ))}
            </ul>
          </Card>

          <Card className="space-y-3">
            <h2 className="text-xl font-semibold">Recent Review Sessions</h2>
            {historyError ? (
              <p className="text-sm text-foreground/75">{historyError}</p>
            ) : recentSessions.length === 0 ? (
              <p className="text-sm text-foreground/75">No completed Quick Review sessions yet.</p>
            ) : (
              <div className="space-y-2">
                {recentSessions.map((session) => (
                  <div
                    key={session.id}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-background px-3 py-2 text-sm"
                  >
                    <span className="text-foreground/75">
                      {session.completedAt
                        ? new Date(session.completedAt).toLocaleString()
                        : new Date(session.createdAt).toLocaleString()}
                    </span>
                    <span className="font-medium text-foreground">
                      {session.correctAnswers}/{session.totalQuestions} ({session.scorePercentage}%)
                    </span>
                  </div>
                ))}
              </div>
            )}
          </Card>

          <PracticeQuizCard quiz={studyPack.quiz} />
        </div>
      ) : null}
    </main>
  );
}
