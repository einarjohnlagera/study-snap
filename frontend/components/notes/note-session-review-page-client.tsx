"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Card } from "@/components/ui/card";
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

  const sessionMode = useMemo(
    () => fromNoteSessionReviewRouteMode(searchParams.get(NOTE_SESSION_REVIEW_QUERY_PARAMS.routeMode)),
    [searchParams],
  );
  const noteTab = useMemo(
    () => normalizeNoteDetailTab(searchParams.get("tab")),
    [searchParams],
  );
  const backHref = useMemo(() => buildNoteSessionReviewBackPath(noteId, noteTab), [noteId, noteTab]);

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
                  {sessionMode === "CHALLENGE" ? "Challenge Quiz" : "Quick Review"} session for this note.
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <span className="rounded-full border border-border bg-muted/40 px-3 py-1 text-xs font-medium text-foreground/70">
                  {sessionMode === "CHALLENGE" ? "Challenge Quiz" : "Quick Review"}
                </span>
                {note?.courseProgram?.trim() ? (
                  <span className="rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-foreground/70">
                    {note.courseProgram}
                  </span>
                ) : null}
              </div>
            </div>
          </Card>

          <QuizSessionReviewContent review={review} title="Session Review" />
        </div>
      ) : null}
    </div>
  );
}
