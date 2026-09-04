"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Copy, Link2 } from "lucide-react";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import {
  ApiRequestError,
  createCombinedQuizShareLink,
  getCombinedQuiz,
  getCombinedQuizShareLink,
  isQuizShareLinkLimitExceededError,
  toggleQuizShareLink,
  trackAnalyticsEvent,
  type CombinedQuizResponse,
  type QuizShareLinkResponse,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

function normalizePlan(plan: string | null | undefined): AppPlanType {
  return plan === "PLUS" || plan === "PRO" ? plan : "FREE";
}

export default function CombinedQuizSharePage() {
  const params = useParams<{ combinedQuizId: string }>();
  const router = useRouter();
  const combinedQuizId = useMemo(() => {
    if (!params?.combinedQuizId) {
      return "";
    }
    return Array.isArray(params.combinedQuizId) ? params.combinedQuizId[0] : params.combinedQuizId;
  }, [params]);
  const authUser = getAuthUser();
  const { usageSummary, refreshUsageSummary } = useBillingUsageSummary();
  const [combinedQuiz, setCombinedQuiz] = useState<CombinedQuizResponse | null>(null);
  const [shareLink, setShareLink] = useState<QuizShareLinkResponse | null>(null);
  const [loadingQuiz, setLoadingQuiz] = useState(true);
  const [quizNotFound, setQuizNotFound] = useState(false);
  const [quizLoadError, setQuizLoadError] = useState<string | null>(null);
  const [shareLoadError, setShareLoadError] = useState<string | null>(null);
  const [loadingShareLink, setLoadingShareLink] = useState(true);
  const [creating, setCreating] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [shareLimitReached, setShareLimitReached] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const loadShareLink = useCallback(async () => {
    if (!combinedQuizId) {
      return;
    }
    setLoadingShareLink(true);
    setShareLoadError(null);
    try {
      // Read-only lookup is intentional: POST is only for the explicit create action, never refresh.
      setShareLink(await getCombinedQuizShareLink(combinedQuizId));
    } catch (error) {
      setShareLoadError(error instanceof Error ? error.message : "Could not load share link.");
    } finally {
      setLoadingShareLink(false);
    }
  }, [combinedQuizId]);

  const loadPage = useCallback(async () => {
    if (!combinedQuizId) {
      setQuizNotFound(true);
      setLoadingQuiz(false);
      return;
    }
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    setLoadingQuiz(true);
    setQuizNotFound(false);
    setQuizLoadError(null);
    try {
      const quiz = await getCombinedQuiz(combinedQuizId);
      setCombinedQuiz(quiz);
      await loadShareLink();
    } catch (error) {
      setCombinedQuiz(null);
      if (error instanceof ApiRequestError && error.status === 404) {
        setQuizNotFound(true);
      } else {
        setQuizLoadError(error instanceof Error ? error.message : "Could not load combined quiz.");
      }
    } finally {
      setLoadingQuiz(false);
    }
  }, [combinedQuizId, loadShareLink, router]);

  useEffect(() => {
    void loadPage();
  }, [loadPage]);

  useEffect(() => {
    if (!message) {
      return undefined;
    }
    const timeoutId = globalThis.setTimeout(() => setMessage(null), 2500);
    return () => globalThis.clearTimeout(timeoutId);
  }, [message]);

  const questionCount = useMemo(
    () => combinedQuiz?.sections.reduce((total, section) => total + (section.questions?.length ?? 0), 0) ?? 0,
    [combinedQuiz],
  );
  const currentPlan = normalizePlan(authUser?.planType ?? usageSummary?.plan);
  const shareUpgradeCtas = useMemo(
    () => getUpgradeCtas(currentPlan, { profileType: authUser?.profileType ?? null }),
    [authUser?.profileType, currentPlan],
  );
  const shareLinksRemaining = usageSummary?.remaining?.quizShareLinksRemaining;

  const handleCreate = useCallback(async () => {
    if (!combinedQuizId || creating) {
      return;
    }
    setCreating(true);
    setShareLimitReached(false);
    setMessage(null);
    try {
      const created = await createCombinedQuizShareLink(combinedQuizId);
      setShareLink(created);
      setMessage("Share link created.");
      void refreshUsageSummary();
      void trackAnalyticsEvent({
        eventType: "QUIZ_SHARE_LINK_CREATED",
        entityId: combinedQuizId,
        metadata: { scope: "combined_quiz", sourceNoteCount: combinedQuiz?.sections.length ?? 0, token: created.token },
      });
    } catch (error) {
      if (isQuizShareLinkLimitExceededError(error)) {
        setShareLimitReached(true);
        void refreshUsageSummary();
      } else {
        setMessage(error instanceof Error ? error.message : "Could not create share link.");
      }
    } finally {
      setCreating(false);
    }
  }, [combinedQuiz?.sections.length, combinedQuizId, creating, refreshUsageSummary]);

  const handleToggle = useCallback(async () => {
    if (!shareLink || toggling) {
      return;
    }
    setToggling(true);
    setMessage(null);
    try {
      const updated = await toggleQuizShareLink(shareLink.token);
      setShareLink(updated);
      setMessage(updated.isActive ? "Sharing turned on." : "Sharing turned off.");
      void trackAnalyticsEvent({
        eventType: "QUIZ_SHARE_LINK_TOGGLED",
        entityId: combinedQuizId,
        metadata: { scope: "combined_quiz", sourceNoteCount: combinedQuiz?.sections.length ?? 0, token: updated.token, isActive: updated.isActive },
      });
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not update share link.");
    } finally {
      setToggling(false);
    }
  }, [combinedQuiz?.sections.length, combinedQuizId, shareLink, toggling]);

  const handleCopy = useCallback(async () => {
    if (!shareLink?.shareUrl) {
      return;
    }
    try {
      await globalThis.navigator.clipboard.writeText(shareLink.shareUrl);
      setMessage("Link copied.");
    } catch {
      setMessage("Copy failed. Select and copy the link manually.");
    }
  }, [shareLink?.shareUrl]);

  const handleUpgrade = useCallback((targetPlan: string) => {
    router.push(`/settings?section=plans&plan=${targetPlan}`);
  }, [router]);

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href="/library" label="Library" />
      <PageHeader
        eyebrow="COMBINED QUIZ"
        title={combinedQuiz?.title ?? "Combined Quiz"}
        description="Share an immutable quiz snapshot assembled from several notes."
        meta={combinedQuiz ? <span className="text-sm text-foreground/65">{combinedQuiz.sections.length} source note{combinedQuiz.sections.length === 1 ? "" : "s"} · {questionCount} question{questionCount === 1 ? "" : "s"}</span> : undefined}
      />
      {loadingQuiz ? (
        <Card className="space-y-3 p-4 sm:p-6" aria-label="Loading combined quiz">
          <div className="h-7 w-2/3 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        </Card>
      ) : quizNotFound ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Combined quiz not found</h1>
          <p className="text-sm text-foreground/75">This quiz does not exist or is not available in your library.</p>
          <Button type="button" onClick={() => router.push("/library")}>Library</Button>
        </Card>
      ) : quizLoadError ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not load combined quiz</h1>
          <p className="text-sm text-foreground/75">{quizLoadError}</p>
          <Button type="button" onClick={() => void loadPage()}>Retry</Button>
        </Card>
      ) : combinedQuiz ? (
        <>
          <Card className="space-y-4 p-4 sm:p-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div className="space-y-2">
                <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Share Quiz</p>
                <h2 className="text-lg font-semibold">Share with Someone</h2>
                <p className="max-w-2xl text-sm leading-6 text-foreground/75">Create a public link someone can open without an account. This quiz is an immutable snapshot, so there is no edit or re-assemble action here.</p>
              </div>
              {loadingShareLink ? null : shareLoadError ? null : !shareLink ? (
                <Button type="button" className="gap-2 sm:self-start" onClick={() => void handleCreate()} loading={creating} loadingText="Creating...">
                  <Link2 className="h-4 w-4" aria-hidden="true" />
                  Create share link
                </Button>
              ) : (
                <Button type="button" variant={shareLink.isActive ? "outline" : "default"} className="sm:self-start" onClick={() => void handleToggle()} loading={toggling} loadingText="Updating...">
                  {shareLink.isActive ? "Turn sharing off" : "Turn sharing on"}
                </Button>
              )}
            </div>

            {shareLoadError ? (
              <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-4">
                <p className="text-sm font-semibold">Could not load share link</p>
                <p className="mt-1 text-sm text-foreground/75">{shareLoadError}</p>
                <Button type="button" variant="outline" size="sm" className="mt-3" onClick={() => void loadShareLink()}>Retry</Button>
              </div>
            ) : null}

            {shareLinksRemaining !== undefined ? (
              <p className="text-sm text-foreground/70">Share links left this month: <span className="font-medium text-foreground">{shareLinksRemaining === null ? "Unlimited" : shareLinksRemaining}</span></p>
            ) : null}

            {shareLimitReached ? (
              <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4">
                <p className="text-sm font-semibold">Monthly share link limit reached</p>
                <p className="mt-1 text-sm leading-6 text-foreground/75">This assembled quiz remains available here. Upgrade for more monthly share links.</p>
                <div className="mt-3 flex flex-col gap-2 sm:flex-row">
                  {shareUpgradeCtas.primary ? <Button type="button" size="sm" onClick={() => handleUpgrade(shareUpgradeCtas.primary!.targetPlan)}>{shareUpgradeCtas.primary.label}</Button> : null}
                  {shareUpgradeCtas.secondary ? <Button type="button" variant="outline" size="sm" onClick={() => handleUpgrade(shareUpgradeCtas.secondary!.targetPlan)}>{shareUpgradeCtas.secondary.label}</Button> : null}
                </div>
              </div>
            ) : null}

            {shareLink ? (
              <div className="space-y-2">
                <div className="flex flex-col gap-2 rounded-xl border border-border bg-highlight/40 p-3 sm:flex-row sm:items-center">
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">{shareLink.isActive ? "Sharing on" : "Sharing off"}</p>
                    <p className={`truncate text-sm font-medium ${shareLink.isActive ? "text-foreground" : "text-foreground/45"}`}>{shareLink.shareUrl}</p>
                  </div>
                  <Button type="button" variant="outline" size="sm" className="gap-2" onClick={() => void handleCopy()}>
                    <Copy className="h-4 w-4" aria-hidden="true" />
                    Copy
                  </Button>
                </div>
                {!shareLink.isActive ? <p className="text-xs text-foreground/60">Anyone who opens this link will see that the quiz is no longer active.</p> : null}
              </div>
            ) : null}
          </Card>
        </>
      ) : null}
      {message ? <div role="status" aria-live="polite" className="fixed bottom-4 right-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">{message}</div> : null}
    </main>
  );
}
