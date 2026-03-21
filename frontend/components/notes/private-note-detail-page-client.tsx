"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import {
  copyNote,
  createStudyPackFromNote,
  getChallengeQuizPerformanceSummary,
  getMyStudyPack,
  getNote,
  updateNoteVisibility,
  getQuickReviewPerformanceSummary,
  startQuickReviewSession,
  type ChallengeQuizPerformanceSummaryResponse,
  type NoteVisibility,
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

function visibilityChip(visibility: NoteVisibility) {
  if (visibility === "PUBLIC") {
    return "border-blue-500/40 bg-blue-500/10 text-blue-700 dark:text-blue-300";
  }
  return "border-border bg-muted/50 text-foreground/70";
}

type PrivateNoteDetailPageClientProps = {
  routeId: string;
};

export function PrivateNoteDetailPageClient({ routeId }: PrivateNoteDetailPageClientProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const visibilityMenuRef = useRef<HTMLDivElement | null>(null);
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [studyPack, setStudyPack] = useState<StudyPackResponse | null>(null);
  const [quickSummary, setQuickSummary] = useState<QuickReviewPerformanceSummaryResponse | null>(null);
  const [challengeSummary, setChallengeSummary] = useState<ChallengeQuizPerformanceSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);
  const [copying, setCopying] = useState(false);
  const [togglingVisibility, setTogglingVisibility] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [visibilityMenuOpen, setVisibilityMenuOpen] = useState(false);
  const [showMakePublicConfirm, setShowMakePublicConfirm] = useState(false);
  const [showLockedEditModal, setShowLockedEditModal] = useState(false);
  const [shareError, setShareError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const normalizedRouteId = useMemo(() => routeId, [routeId]);

  const loadDetail = useCallback(async () => {
    if (!normalizedRouteId) {
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
        loadedNote = await getNote(normalizedRouteId);
      } catch (noteError) {
        const byStudyPack = await getMyStudyPack(normalizedRouteId).catch(() => null);
        if (byStudyPack?.noteId) {
          const nextQuery = searchParams.toString();
          router.replace(nextQuery ? `/notes/${byStudyPack.noteId}?${nextQuery}` : `/notes/${byStudyPack.noteId}`);
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
  }, [normalizedRouteId, router, searchParams]);

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
    if (!visibilityMenuOpen) {
      return;
    }
    const handleOutsideClick = (event: MouseEvent) => {
      if (!visibilityMenuRef.current) {
        return;
      }
      if (!visibilityMenuRef.current.contains(event.target as Node)) {
        setVisibilityMenuOpen(false);
      }
    };
    window.addEventListener("mousedown", handleOutsideClick);
    return () => window.removeEventListener("mousedown", handleOutsideClick);
  }, [visibilityMenuOpen]);

  useEffect(() => {
    const created = searchParams.get("created") === "1";
    const copied = searchParams.get("copied") === "1";
    const saved = searchParams.get("saved") === "1";
    if (!created && !copied && !saved) {
      return;
    }
    if (created) {
      setToast("Study Pack generated successfully.");
    } else if (copied) {
      setToast("Copied to My Library");
    } else {
      setToast("Note saved.");
    }
    const next = new URLSearchParams(searchParams.toString());
    next.delete("created");
    next.delete("copied");
    next.delete("saved");
    router.replace(next.size > 0 ? `${pathname}?${next.toString()}` : pathname);
  }, [pathname, router, searchParams]);

  const isDraft = !studyPack || note?.studyPackStatus !== "STUDY_PACK_READY";
  const linkedStudyPackId = studyPack?.id ?? note?.studyPackId ?? null;
  const title = note?.title?.trim() || studyPack?.title || "Untitled note";
  const subject = note?.subject?.trim() || studyPack?.subject?.trim() || "No subject";
  const tags = note?.tags ?? [];
  const visibility = (note?.visibility ?? "PRIVATE") as NoteVisibility;
  const isPublic = visibility === "PUBLIC";

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

  const handleCopyAndEdit = async () => {
    if (!note || copying) {
      return;
    }
    setCopying(true);
    try {
      const copied = await copyNote(note.id);
      router.push(`/notes/${copied.id}/edit?copied=1`);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not copy note.";
      setError(message);
    } finally {
      setCopying(false);
    }
  };

  const performVisibilityUpdate = async (nextVisibility: NoteVisibility) => {
    if (!note || togglingVisibility || visibility === nextVisibility) {
      return;
    }
    setTogglingVisibility(true);
    setVisibilityMenuOpen(false);
    try {
      const updated = await updateNoteVisibility(note.id, nextVisibility);
      setNote(updated);
      setToast(nextVisibility === "PUBLIC" ? "Note is now public." : "Note is now private.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update note visibility.";
      setError(message);
    } finally {
      setTogglingVisibility(false);
    }
  };

  const handleSelectVisibility = (nextVisibility: NoteVisibility) => {
    if (nextVisibility === visibility || togglingVisibility) {
      setVisibilityMenuOpen(false);
      return;
    }
    if (nextVisibility === "PUBLIC") {
      setVisibilityMenuOpen(false);
      setShowMakePublicConfirm(true);
      return;
    }
    void performVisibilityUpdate("PRIVATE");
  };

  const handleEdit = () => {
    if (!note) {
      return;
    }
    if (isDraft) {
      router.push(`/notes/${note.id}/edit`);
      return;
    }
    setShowLockedEditModal(true);
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
    if (!note || sharing) {
      return;
    }
    setSharing(true);
    setShareError(null);
    try {
      if (!isPublic) {
        setShareError("Make this note public to share it.");
        return;
      }
      const shareUrl = new URL(`/public/notes/${note.id}`, window.location.origin).toString();
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
      <Link href="/library" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">Back to My Library</Link>

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
            <div className="flex items-start justify-between gap-3">
              <div className="space-y-3">
                <h1 className="text-2xl font-semibold sm:text-3xl">{title}</h1>
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${stateChip(isDraft ? "DRAFT" : "STUDY_PACK_READY")}`}>
                    {isDraft ? "📝 Draft" : "✨ Study Pack"}
                  </span>
                  <div className="relative" ref={visibilityMenuRef}>
                    <button
                      type="button"
                      className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${visibilityChip(visibility)}`}
                      onClick={() => setVisibilityMenuOpen((open) => !open)}
                      aria-haspopup="menu"
                      aria-expanded={visibilityMenuOpen}
                      disabled={togglingVisibility}
                    >
                      {visibility === "PUBLIC" ? "🌍 Public ▼" : "🔒 Private ▼"}
                    </button>
                    {visibilityMenuOpen ? (
                      <div className="absolute left-0 top-8 z-20 w-64 rounded-md border border-border bg-background p-1 shadow-sm">
                        <button
                          type="button"
                          className="w-full rounded px-3 py-2 text-left hover:bg-muted/60"
                          onClick={() => handleSelectVisibility("PRIVATE")}
                        >
                          <p className="text-sm font-medium">🔒 Private</p>
                          <p className="text-xs text-foreground/70">Only visible in My Library</p>
                        </button>
                        <button
                          type="button"
                          className="w-full rounded px-3 py-2 text-left hover:bg-muted/60"
                          onClick={() => handleSelectVisibility("PUBLIC")}
                        >
                          <p className="text-sm font-medium">🌍 Public</p>
                          <p className="text-xs text-foreground/70">Visible in Public Library</p>
                        </button>
                      </div>
                    ) : null}
                  </div>
                </div>
              </div>
              <Button type="button" variant="outline" size="sm" onClick={handleEdit}>
                Edit
              </Button>
            </div>
            <p className="text-sm text-foreground/75">{subject}</p>
            <div className="flex flex-wrap gap-2">
              {tags.length > 0 ? tags.map((tag, index) => (
                <span key={`${tag}-${index}`} className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75">{tag}</span>
              )) : (
                <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">No tags</span>
              )}
            </div>
            {isDraft ? (
              <p className="text-xs text-foreground/70">
                Generating locks this note to preserve its Study Pack. Need changes later? Use Copy and Edit.
              </p>
            ) : null}
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                {isDraft ? (
                  <Button type="button" onClick={() => void handleGenerate()} disabled={generating}>
                    {generating ? "Generating..." : "Generate Study Pack"}
                  </Button>
                ) : (
                  <Button type="button" onClick={() => void handleStartQuickReview()}>
                    Start Quick Review
                  </Button>
                )}
              </div>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Button type="button" variant="outline" onClick={() => void handleCopyAndEdit()} disabled={copying}>
                  {copying ? "Copying..." : "Copy and Edit"}
                </Button>
                <Button type="button" variant="outline" onClick={() => void handleCopyLink()} disabled={sharing}>
                  {sharing ? "Sharing..." : "Share"}
                </Button>
              </div>
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
              {isDraft ? "No summary yet. Generate a Study Pack to turn this note into a structured study guide." : studyPack.summary}
            </p>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Key Concepts</h2>
            {isDraft ? (
              <p className="text-sm text-foreground/75">No key concepts yet. Generate a Study Pack to extract the most important ideas from this note.</p>
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
              <p className="text-sm text-foreground/75">No quiz yet. Generate a Study Pack to create practice questions from this note.</p>
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

      {showMakePublicConfirm ? (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/50 px-4">
          <Card className="w-full max-w-md space-y-4 p-5">
            <div className="space-y-2">
              <h2 className="text-lg font-semibold">Make this note public?</h2>
              <p className="text-sm text-foreground/75">
                This will make your note visible in the Public Library. Other students will be able to view and copy this note.
              </p>
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setShowMakePublicConfirm(false)}>
                Cancel
              </Button>
              <Button
                type="button"
                onClick={() => {
                  setShowMakePublicConfirm(false);
                  void performVisibilityUpdate("PUBLIC");
                }}
                disabled={togglingVisibility}
              >
                {togglingVisibility ? "Updating..." : "Make Public"}
              </Button>
            </div>
          </Card>
        </div>
      ) : null}

      {showLockedEditModal ? (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/50 px-4">
          <Card className="w-full max-w-md space-y-4 p-5">
            <div className="space-y-2">
              <h2 className="text-lg font-semibold">This note is locked</h2>
              <p className="text-sm text-foreground/75">
                This note already has a Study Pack. To preserve your summary, concepts, and quizzes, editing is disabled.
              </p>
              <p className="text-sm text-foreground/75">
                If you want to make changes, create a copy of this note and generate a new Study Pack.
              </p>
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setShowLockedEditModal(false)}>
                Cancel
              </Button>
              <Button
                type="button"
                onClick={() => {
                  setShowLockedEditModal(false);
                  void handleCopyAndEdit();
                }}
                disabled={copying}
              >
                {copying ? "Copying..." : "Copy and Edit"}
              </Button>
            </div>
          </Card>
        </div>
      ) : null}
    </main>
  );
}
