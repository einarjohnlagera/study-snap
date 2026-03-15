"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import {
  createStudyPackShareLink,
  getQuickReviewPerformanceSummary,
  getInProgressQuickReviewSession,
  getMyStudyPack,
  listRecentQuickReviewSessions,
  startQuickReviewSession,
  type QuickReviewPerformanceSummaryResponse,
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
  const [performanceSummary, setPerformanceSummary] = useState<QuickReviewPerformanceSummaryResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [performanceError, setPerformanceError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [startingQuickReview, setStartingQuickReview] = useState(false);
  const [hasInProgressQuickReview, setHasInProgressQuickReview] = useState(false);
  const [creatingShareLink, setCreatingShareLink] = useState(false);
  const [shareError, setShareError] = useState<string | null>(null);
  const [shareToast, setShareToast] = useState<string | null>(null);
  const shareToastTimeoutRef = useRef<number | null>(null);

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
        const summary = await getQuickReviewPerformanceSummary(studyPackId);
        setPerformanceSummary(summary);
        setPerformanceError(null);
      } catch (performanceErr) {
        const message = performanceErr instanceof Error ? performanceErr.message : "Could not load review performance.";
        setPerformanceError(message);
        setPerformanceSummary(null);
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
      setPerformanceSummary(null);
      setHistoryError(null);
      setPerformanceError(null);
      setHasInProgressQuickReview(false);
    } finally {
      setLoading(false);
    }
  }, [router, studyPackId]);

  useEffect(() => {
    void loadStudyPack();
  }, [loadStudyPack]);

  useEffect(() => {
    return () => {
      if (shareToastTimeoutRef.current !== null) {
        window.clearTimeout(shareToastTimeoutRef.current);
      }
    };
  }, []);

  const isNotFound = error?.toLowerCase().includes("not found") ?? false;

  const formatScore = (value: number | null) => {
    if (value === null) {
      return "—";
    }
    if (Number.isInteger(value)) {
      return `${value}%`;
    }
    return `${value.toFixed(2).replace(/\.?0+$/, "")}%`;
  };

  const latestCompletedSession = recentSessions[0] ?? null;
  const focusAreas = Array.from(
    new Set(
      (latestCompletedSession?.weakConcepts ?? [])
        .map((concept) => concept.trim())
        .filter((concept) => concept.length > 0),
    ),
  ).slice(0, 4);

  const suggestedNextStep = (() => {
    if (!latestCompletedSession) {
      return "Start your first Quick Review to discover which concepts need more work.";
    }
    if (focusAreas.length > 0) {
      return "Practice weak concepts to strengthen this topic.";
    }
    return "Continue reviewing this Study Pack.";
  })();

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

  const showShareToast = useCallback((message: string) => {
    setShareToast(message);
    if (shareToastTimeoutRef.current !== null) {
      window.clearTimeout(shareToastTimeoutRef.current);
    }
    shareToastTimeoutRef.current = window.setTimeout(() => {
      setShareToast(null);
      shareToastTimeoutRef.current = null;
    }, 2200);
  }, []);

  const handleCopyShareLink = useCallback(async () => {
    if (!studyPack || creatingShareLink) {
      return;
    }

    setCreatingShareLink(true);
    setShareError(null);
    try {
      const share = await createStudyPackShareLink(studyPack.id);
      const shareUrl = share.shareUrl.startsWith("http")
        ? share.shareUrl
        : new URL(share.shareUrl, window.location.origin).toString();

      if (!navigator.clipboard) {
        throw new Error("Clipboard is not available in this browser.");
      }

      await navigator.clipboard.writeText(shareUrl);
      showShareToast("Share link copied");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not copy share link.";
      setShareError(message);
    } finally {
      setCreatingShareLink(false);
    }
  }, [creatingShareLink, showShareToast, studyPack]);

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        <Link href="/dashboard" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
          Back to Dashboard
        </Link>
      </div>

      {loading ? (
        <StudyPackDetailLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">
            {isNotFound ? "Study Pack not found" : "Could not load this Study Pack"}
          </h1>
          <p className="text-sm text-foreground/75">
            {isNotFound
              ? "This Study Pack is unavailable or does not belong to your account."
              : error}
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            {!isNotFound ? (
              <Button type="button" className="w-full sm:w-auto" onClick={() => void loadStudyPack()}>
                Retry
              </Button>
            ) : null}
            <Link href="/dashboard" className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Back to Dashboard
              </Button>
            </Link>
          </div>
        </Card>
      ) : studyPack ? (
        <div className="space-y-6">
          <Card className="space-y-3 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Study Pack
            </p>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{studyPack.title}</h1>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-foreground/70">
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
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => void handleStartQuickReview()} disabled={startingQuickReview}>
                  {startingQuickReview
                    ? (hasInProgressQuickReview ? "Resuming..." : "Starting...")
                    : (hasInProgressQuickReview ? "Resume Quick Review" : "Start Quick Review")}
                </Button>
              </div>
              <div className="space-y-1">
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Share Study Pack</p>
                <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => void handleCopyShareLink()} disabled={creatingShareLink}>
                  {creatingShareLink ? "Copying..." : "Copy Link"}
                </Button>
                {shareError ? <p className="text-xs text-red-600 dark:text-red-400">{shareError}</p> : null}
              </div>
            </div>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/85">{studyPack.summary}</p>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Key Concepts</h2>
            <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
              {studyPack.keyConcepts.map((concept, index) => (
                <li key={`${studyPack.id}-concept-${index}`}>{concept}</li>
              ))}
            </ul>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Review Performance</h2>
            {performanceError ? (
              <p className="text-sm text-foreground/75">{performanceError}</p>
            ) : !performanceSummary || performanceSummary.attempts === 0 ? (
              <p className="text-sm text-foreground/75">No Quick Reviews yet. Start your first review.</p>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Best Score</p>
                  <p className="mt-1 text-lg font-semibold">{formatScore(performanceSummary.bestScorePercentage)}</p>
                </div>
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Attempts</p>
                  <p className="mt-1 text-lg font-semibold">{performanceSummary.attempts}</p>
                </div>
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Last Score</p>
                  <p className="mt-1 text-lg font-semibold">{formatScore(performanceSummary.lastScorePercentage)}</p>
                </div>
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Last Reviewed</p>
                  <p className="mt-1 text-sm font-medium">
                    {performanceSummary.lastReviewedAt
                      ? new Date(performanceSummary.lastReviewedAt).toLocaleString()
                      : "—"}
                  </p>
                </div>
              </div>
            )}
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">AI Study Coach</h2>
            {historyError ? (
              <p className="text-sm text-foreground/75">{historyError}</p>
            ) : (
              <div className="space-y-3">
                {focusAreas.length > 0 ? (
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                      Focus Areas
                    </p>
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/85">
                      {focusAreas.map((concept) => (
                        <li key={`focus-area-${concept}`}>{concept}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
                    Suggested Next Step
                  </p>
                  <p className="mt-2 text-sm text-foreground/80">{suggestedNextStep}</p>
                </div>
              </div>
            )}
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Recent Review Sessions</h2>
            {historyError ? (
              <p className="text-sm text-foreground/75">{historyError}</p>
            ) : recentSessions.length === 0 ? (
              <p className="text-sm text-foreground/75">No completed Quick Review sessions yet.</p>
            ) : (
              <div className="space-y-2">
                {recentSessions.map((session) => (
                  <div
                    key={session.id}
                    className="flex flex-col gap-1 rounded-md border border-border bg-background px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between"
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
      {shareToast ? (
        <div
          role="status"
          aria-live="polite"
          className="fixed right-4 bottom-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm"
        >
          {shareToast}
        </div>
      ) : null}
    </main>
  );
}
