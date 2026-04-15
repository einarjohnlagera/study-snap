"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, FileText } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ToastMessage } from "@/components/ui/toast-message";
import { QuizSessionReviewContent } from "@/components/notes/quiz-session-review-content";
import {
  getNote,
  getChallengeQuizSessionReview,
  getQuickReviewSessionReview,
  type NoteResponse,
  type QuizSessionReviewResponse,
} from "@/lib/api";
import { normalizeNoteDetailTab } from "@/lib/note-entry";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { exportQuizSessionReviewDocument } from "@/lib/quiz-session-export";
import {
  buildNoteSessionReviewBackPath,
  fromNoteSessionReviewRouteMode,
  NOTE_SESSION_REVIEW_QUERY_PARAMS,
} from "@/lib/note-session-review";

type NoteSessionReviewPageClientProps = {
  noteId: string;
  sessionId: string;
};

export function NoteSessionReviewPageClient({
  noteId,
  sessionId,
}: Readonly<NoteSessionReviewPageClientProps>) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [review, setReview] = useState<QuizSessionReviewResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportMenuOpen, setExportMenuOpen] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastTone, setToastTone] = useState<"success" | "error" | "info">("info");
  const exportMenuRef = useRef<HTMLDivElement>(null);

  const sessionMode = useMemo(
    () => fromNoteSessionReviewRouteMode(searchParams.get(NOTE_SESSION_REVIEW_QUERY_PARAMS.routeMode)),
    [searchParams],
  );
  const noteTab = useMemo(
    () => normalizeNoteDetailTab(searchParams.get("tab")),
    [searchParams],
  );
  const backHref = useMemo(() => buildNoteSessionReviewBackPath(noteId, noteTab), [noteId, noteTab]);
  const quizTypeLabel = sessionMode === "CHALLENGE" ? "Challenge Quiz" : "Quick Review";

  useEffect(() => {
    if (!toastMessage) {
      return undefined;
    }
    const timeoutId = globalThis.setTimeout(() => {
      setToastMessage(null);
    }, 2200);
    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [toastMessage]);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    if (!sessionMode) {
      setLoading(false);
      setError("Session review not found.");
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);
    setReview(null);
    setNote(null);

    const loadReview = async () => {
      try {
        const [nextReview, nextNote] = await Promise.all([
          sessionMode === "CHALLENGE"
            ? getChallengeQuizSessionReview(noteId, sessionId)
            : getQuickReviewSessionReview(noteId, sessionId),
          getNote(noteId),
        ]);
        if (!active) {
          return;
        }
        setReview(nextReview);
        setNote(nextNote);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err instanceof Error ? err.message : "Could not load this session review.");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadReview();
    return () => {
      active = false;
    };
  }, [noteId, router, sessionId, sessionMode]);

  useEffect(() => {
    if (!exportMenuOpen) {
      return undefined;
    }
    const handleClickOutside = (event: MouseEvent) => {
      if (exportMenuRef.current && !exportMenuRef.current.contains(event.target as Node)) {
        setExportMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [exportMenuOpen]);

  const handleExport = async (exportType: "full" | "mistakes-only") => {
    if (!review || exporting) {
      return;
    }

    setExportMenuOpen(false);
    setExporting(true);
    setToastTone("info");
    setToastMessage("Exporting PDF...");

    try {
      await new Promise<void>((resolve) => {
        globalThis.setTimeout(() => resolve(), 0);
      });
      await exportQuizSessionReviewDocument({
        format: "pdf",
        exportType,
        noteTitle: note?.title,
        noteSubject: note?.subject,
        quizTypeLabel,
        review,
      });
      setToastTone("success");
      setToastMessage("PDF ready");
    } catch (exportError) {
      setToastTone("error");
      setToastMessage(exportError instanceof Error ? exportError.message : "Could not export PDF.");
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-5xl space-y-4 px-4 py-4 sm:px-6 sm:py-6">
      <BackLink href={backHref} label="Note" />

      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Session Review
        </p>
        <h1 className="text-2xl font-semibold text-foreground">Focused review</h1>
        <p className="text-sm leading-relaxed text-foreground/75">
          Review your answers, weak concepts, and explanations in one dedicated page.
        </p>
      </div>

      {loading ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <p className="text-sm font-medium text-foreground">Loading session review...</p>
          <div className="h-4 w-2/3 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-5/6 animate-pulse rounded bg-foreground/10" />
        </Card>
      ) : null}

      {!loading && error ? (
        <Card className="space-y-2 border-red-500/30 bg-red-500/5 p-4 sm:p-6">
          <h2 className="text-lg font-semibold text-foreground">Couldn&apos;t open this session review</h2>
          <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
        </Card>
      ) : null}

      {!loading && review ? (
        <div className="space-y-4">
          <Card className="space-y-3 p-4 sm:p-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div className="space-y-2">
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
                  {note?.subject?.trim() || "Study Session"}
                </p>
                <h2 className="text-xl font-semibold text-foreground sm:text-2xl">
                  {note?.title?.trim() || "Untitled note"}
                </h2>
                <p className="text-sm leading-relaxed text-foreground/75">
                  {quizTypeLabel} session for this note.
                </p>
              </div>
              <div className="flex flex-col items-start gap-3 sm:items-end">
                <div className="relative" ref={exportMenuRef}>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="gap-2"
                    onClick={() => setExportMenuOpen((prev) => !prev)}
                    disabled={exporting}
                  >
                    <FileText className="h-4 w-4" aria-hidden="true" />
                    <span>{exporting ? "Exporting..." : "Export"}</span>
                    <ChevronDown className="h-3 w-3" aria-hidden="true" />
                  </Button>
                  {exportMenuOpen ? (
                    <div className="absolute right-0 top-full z-10 mt-1 min-w-[148px] rounded-md border border-border bg-background p-1 shadow-md">
                      <button
                        type="button"
                        className="w-full rounded px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/60 active:bg-muted/70"
                        onClick={() => void handleExport("full")}
                      >
                        Full Quiz
                      </button>
                      <button
                        type="button"
                        className="w-full rounded px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/60 active:bg-muted/70"
                        onClick={() => void handleExport("mistakes-only")}
                      >
                        Mistakes Only
                      </button>
                    </div>
                  ) : null}
                </div>
                <div className="flex flex-wrap gap-2">
                  <span className="rounded-full border border-border bg-muted/40 px-3 py-1 text-xs font-medium text-foreground/70">
                    {quizTypeLabel}
                  </span>
                  {note?.courseProgram?.trim() ? (
                    <span className="rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-foreground/70">
                      {note.courseProgram}
                    </span>
                  ) : null}
                </div>
              </div>
            </div>
          </Card>

          <QuizSessionReviewContent review={review} title="Session Review" />
        </div>
      ) : null}

      {toastMessage ? <ToastMessage message={toastMessage} tone={toastTone} /> : null}
    </div>
  );
}
