"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { PublicLibraryBackLink } from "@/components/notes/public-library-back-link";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import { Card } from "@/components/ui/card";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { copyNote, getPublicNote, type PublicNoteDetailResponse } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

function stateChip(status: "DRAFT" | "STUDY_PACK_READY") {
  if (status === "STUDY_PACK_READY") {
    return "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300";
  }
  return "border-border bg-muted/50 text-foreground/70";
}

type PublicNoteDetailPageClientProps = {
  noteId: string;
};

export function PublicNoteDetailPageClient({ noteId }: PublicNoteDetailPageClientProps) {
  const router = useRouter();
  const [note, setNote] = useState<PublicNoteDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copying, setCopying] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [copyError, setCopyError] = useState<string | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  const routeId = useMemo(() => noteId, [noteId]);

  useEffect(() => {
    const syncAuth = () => {
      setIsAuthenticated(Boolean(getAuthUser()));
    };
    syncAuth();
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuth);
    };
  }, []);

  const loadDetail = useCallback(async () => {
    if (!routeId) {
      setError("Note not found.");
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const loaded = await getPublicNote(routeId);
      setNote(loaded);
      setCopyError(null);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load this public note.";
      setError(message);
      setNote(null);
    } finally {
      setLoading(false);
    }
  }, [routeId]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timeout = globalThis.setTimeout(() => setToast(null), 2600);
    return () => globalThis.clearTimeout(timeout);
  }, [toast]);

  const isDraft = !note || note.studyPackStatus !== "STUDY_PACK_READY";
  const title = note?.title?.trim() || "Untitled note";
  const subject = note?.subject?.trim() || "No subject";
  const tags = note?.tags ?? [];

  const handleCopy = async () => {
    if (!note || copying) {
      return;
    }
    if (!isAuthenticated) {
      router.push("/auth");
      return;
    }
    setCopying(true);
    setCopyError(null);
    try {
      const copied = await copyNote(note.id);
      setToast("Copied to Library");
      router.push(`/notes/${copied.id}?copied=1`);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not copy note.";
      setCopyError(message);
    } finally {
      setCopying(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <PublicLibraryBackLink className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" />

      {loading ? (
        <Card className="p-6">Loading public note...</Card>
      ) : error ? (
        <Card className="space-y-3 p-6">
          <h1 className="text-xl font-semibold">Could not load public note</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <ResponsiveActionButton type="button" onClick={() => void loadDetail()} action="retry" label="Retry" />
        </Card>
      ) : note ? (
        <div className="space-y-6">
          <Card className="space-y-4 p-4 sm:p-6">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Public Note</span>
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
            <div className="space-y-2">
              {isAuthenticated ? (
                <ResponsiveActionButton type="button" onClick={() => void handleCopy()} disabled={copying} action="copy" label={copying ? "Copying..." : "Copy to Library"} />
              ) : (
                <ResponsiveActionLink href="/auth" action="open" label="Login or Sign up" className="w-full sm:w-auto" showTextOnMobile />
              )}
              {copyError ? <p className="text-xs text-red-600 dark:text-red-400">{copyError}</p> : null}
            </div>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
            <p className="text-sm text-foreground/75">
              {isDraft ? "This public note does not have generated summary yet." : note.summary}
            </p>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Key Concepts</h2>
            {isDraft ? (
              <p className="text-sm text-foreground/75">No generated key concepts yet.</p>
            ) : (
              <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
                {note.keyConcepts.map((concept, index) => (
                  <li key={`${note.id}-concept-${index}`}>{concept}</li>
                ))}
              </ul>
            )}
          </Card>

          {isDraft ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-lg font-semibold sm:text-xl">Practice Quiz</h2>
              <p className="text-sm text-foreground/75">No generated quiz yet.</p>
            </Card>
          ) : (
            <PracticeQuizCard quiz={note.quiz} />
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
