"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { Lock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { getAuthUser } from "@/lib/auth";
import { PLAN_BILLING_PATH } from "@/lib/plans";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import {
  cloneNote,
  createStudyPackFromNote,
  createStudyPackShareLink,
  getChallengeQuizPerformanceSummary,
  getMyStudyPack,
  getNote,
  getQuickReviewPerformanceSummary,
  startQuickReviewSession,
  type ChallengeQuizPerformanceSummaryResponse,
  type NoteResponse,
  type QuickReviewPerformanceSummaryResponse,
  type StudyPackResponse,
} from "@/lib/api";

function stateChip(status: "DRAFT" | "STUDY_PACK_READY") {
  if (status === "STUDY_PACK_READY") {
    return "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300";
  }
  return "border-border bg-muted/50 text-foreground/70";
}

export default function NoteDetailPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const params = useParams<{ id: string }>();
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [studyPack, setStudyPack] = useState<StudyPackResponse | null>(null);
  const [quickSummary, setQuickSummary] = useState<QuickReviewPerformanceSummaryResponse | null>(null);
  const [challengeSummary, setChallengeSummary] = useState<ChallengeQuizPerformanceSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isPremiumUser, setIsPremiumUser] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [cloning, setCloning] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [shareError, setShareError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const routeId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);

  useEffect(() => {
    const syncPlan = () => {
      const authUser = getAuthUser();
      setIsPremiumUser(authUser?.planType === "PREMIUM");
    };
    syncPlan();
    window.addEventListener("studysnap-auth-change", syncPlan);
    return () => window.removeEventListener("studysnap-auth-change", syncPlan);
  }, []);

  const loadDetail = useCallback(async () => {
    if (!routeId) {
      setError("Note not found.");
      setLoading(false);
      return;
    }
    if (!requireVerifiedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      let loadedNote: NoteResponse | null = null;
      try {
        loadedNote = await getNote(routeId);
      } catch (noteError) {
        const byStudyPack = await getMyStudyPack(routeId).catch(() => null);
        if (byStudyPack?.noteId) {
          const nextQuery = searchParams.toString();
          router.replace(nextQuery ? `/study-packs/${byStudyPack.noteId}?${nextQuery}` : `/study-packs/${byStudyPack.noteId}`);
          return;
        }
        throw noteError;
      }

      setNote(loadedNote);
      setShareError(null);

      if (!loadedNote.studyPackId || loadedNote.studyPackStatus === "DRAFT") {
        setStudyPack(null);
        setQuickSummary(null);
        setChallengeSummary(null);
        return;
      }

      const linkedStudyPack = await getMyStudyPack(loadedNote.studyPackId);
      setStudyPack(linkedStudyPack);

      const [quick, challenge] = await Promise.allSettled([
        getQuickReviewPerformanceSummary(linkedStudyPack.id),
        getChallengeQuizPerformanceSummary(linkedStudyPack.id),
      ]);
      setQuickSummary(quick.status === "fulfilled" ? quick.value : null);
      setChallengeSummary(challenge.status === "fulfilled" ? challenge.value : null);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load this note.";
      setError(message);
      setNote(null);
      setStudyPack(null);
      setQuickSummary(null);
      setChallengeSummary(null);
    } finally {
      setLoading(false);
    }
  }, [routeId, router, searchParams]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timeout = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  useEffect(() => {
    const created = searchParams.get("created") === "1";
    const cloned = searchParams.get("cloned") === "1";
    const saved = searchParams.get("saved") === "1";
    if (!created && !cloned && !saved) {
      return;
    }
    if (created) {
      setToast("Study Pack generated successfully.");
    } else if (cloned) {
      setToast("Note cloned. You can edit and generate a new Study Pack.");
    } else {
      setToast("Note saved.");
    }
    const next = new URLSearchParams(searchParams.toString());
    next.delete("created");
    next.delete("cloned");
    next.delete("saved");
    router.replace(next.size > 0 ? `${pathname}?${next.toString()}` : pathname);
  }, [pathname, router, searchParams]);

  const isDraft = !studyPack || note?.studyPackStatus !== "STUDY_PACK_READY";
  const linkedStudyPackId = studyPack?.id ?? note?.studyPackId ?? null;
  const title = note?.title?.trim() || studyPack?.title || "Untitled note";
  const subject = note?.subject?.trim() || studyPack?.subject?.trim() || "No subject";
  const tags = note?.tags ?? [];

  const handleGenerate = async () => {
    if (!note || generating || !isDraft) {
      return;
    }
    setGenerating(true);
    try {
      await createStudyPackFromNote(note.id);
      const next = new URLSearchParams(searchParams.toString());
      next.set("created", "1");
      router.replace(`${pathname}?${next.toString()}`);
      void loadDetail();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not generate Study Pack.";
      setError(message);
    } finally {
      setGenerating(false);
    }
  };

  const handleClone = async () => {
    if (!note || cloning) {
      return;
    }
    setCloning(true);
    try {
      const cloned = await cloneNote(note.id);
      router.push(`/study-packs/${cloned.id}?from=library&cloned=1`);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not clone note.";
      setError(message);
    } finally {
      setCloning(false);
    }
  };

  const handleStartQuickReview = async () => {
    if (!linkedStudyPackId) {
      return;
    }
    const started = await startQuickReviewSession(linkedStudyPackId);
    if (started.sessionId) {
      router.push(`/study-packs/${linkedStudyPackId}/quick-review?sessionId=${started.sessionId}`);
    }
  };

  const handleCopyLink = async () => {
    if (!linkedStudyPackId || sharing) {
      return;
    }
    setSharing(true);
    setShareError(null);
    try {
      const share = await createStudyPackShareLink(linkedStudyPackId);
      const shareUrl = share.shareUrl.startsWith("http")
        ? share.shareUrl
        : new URL(share.shareUrl, window.location.origin).toString();
      await navigator.clipboard.writeText(shareUrl);
      setToast("Share link copied");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not copy share link.";
      setShareError(message);
    } finally {
      setSharing(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <Link href="/library" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">Back to My Notes</Link>

      {loading ? (
        <Card className="p-6">Loading note...</Card>
      ) : error ? (
        <Card className="space-y-3 p-6">
          <h1 className="text-xl font-semibold">Could not load note</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <Button type="button" onClick={() => void loadDetail()}>Retry</Button>
        </Card>
      ) : note ? (
        <div className="space-y-6">
          <Card className="space-y-4 p-4 sm:p-6">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Note</span>
              <span className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${stateChip(isDraft ? "DRAFT" : "STUDY_PACK_READY")}`}>
                {isDraft ? "Draft" : "Study Pack Ready"}
              </span>
            </div>
            <h1 className="text-2xl font-semibold sm:text-3xl">{title}</h1>
            <p className="text-sm text-foreground/75">{subject}</p>
            <div className="flex flex-wrap gap-2">
              {tags.length > 0 ? tags.map((tag, index) => (
                <span key={`${tag}-${index}`} className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75">{tag}</span>
              )) : (
                <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">No tags</span>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" onClick={() => void handleClone()} disabled={cloning}>
                {cloning ? "Cloning..." : "Clone Note"}
              </Button>
              {isDraft ? (
                <Button type="button" onClick={() => void handleGenerate()} disabled={generating}>
                  {generating ? "Generating..." : "Generate Study Pack (1 credit)"}
                </Button>
              ) : (
                <>
                  <Button type="button" onClick={() => void handleStartQuickReview()}>Start Quick Review</Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => {
                      if (!linkedStudyPackId) return;
                      if (!isPremiumUser) {
                        router.push(PLAN_BILLING_PATH);
                        return;
                      }
                      router.push(`/study-packs/${linkedStudyPackId}/challenge-quiz`);
                    }}
                  >
                    {!isPremiumUser ? <Lock className="h-4 w-4" aria-hidden="true" /> : null}
                    Challenge Quiz
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => {
                      if (!linkedStudyPackId) return;
                      if (!isPremiumUser) {
                        router.push(PLAN_BILLING_PATH);
                        return;
                      }
                      router.push(`/study-packs/${linkedStudyPackId}/adaptive-practice`);
                    }}
                  >
                    {!isPremiumUser ? <Lock className="h-4 w-4" aria-hidden="true" /> : null}
                    Adaptive Practice
                  </Button>
                  <Button type="button" variant="outline" onClick={() => void handleCopyLink()} disabled={sharing}>
                    {sharing ? "Copying..." : "Copy Link"}
                  </Button>
                </>
              )}
            </div>
            {shareError ? <p className="text-xs text-red-600 dark:text-red-400">{shareError}</p> : null}
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Note Content</h2>
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/85">
              {note.content.trim().length > 0 ? note.content : "No content yet."}
            </p>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
            <p className="text-sm text-foreground/75">
              {isDraft ? "Generate a Study Pack to see the summary." : studyPack.summary}
            </p>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Key Concepts</h2>
            {isDraft ? (
              <p className="text-sm text-foreground/75">Generate a Study Pack to see key concepts.</p>
            ) : (
              <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
                {studyPack.keyConcepts.map((concept, index) => (
                  <li key={`${studyPack.id}-concept-${index}`}>{concept}</li>
                ))}
              </ul>
            )}
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Performance Overview</h2>
            {isDraft ? (
              <p className="text-sm text-foreground/75">Performance will appear after Quick Review or Challenge Quiz.</p>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Quick Review</p>
                  <p className="mt-1 text-sm text-foreground/80">Attempts: {quickSummary?.attempts ?? 0}</p>
                  <p className="text-sm text-foreground/80">Last score: {quickSummary?.lastScorePercentage ?? "-"}</p>
                </div>
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Challenge Quiz</p>
                  <p className="mt-1 text-sm text-foreground/80">Attempts: {challengeSummary?.attempts ?? 0}</p>
                  <p className="text-sm text-foreground/80">Best score: {challengeSummary?.bestScorePercentage ?? "-"}</p>
                </div>
              </div>
            )}
          </Card>

          {isDraft ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-lg font-semibold sm:text-xl">Practice Quiz</h2>
              <p className="text-sm text-foreground/75">Generate a Study Pack to see quiz questions.</p>
            </Card>
          ) : (
            <PracticeQuizCard quiz={studyPack.quiz} />
          )}
        </div>
      ) : null}

      {toast ? (
        <div role="status" aria-live="polite" className="fixed right-4 bottom-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
          {toast}
        </div>
      ) : null}
    </main>
  );
}
