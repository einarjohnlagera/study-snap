"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import {
  ApiRequestError,
  createCombinedQuiz,
  listNotes,
  type NoteListItemResponse,
} from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  COMBINED_QUIZ_MAX_QUESTIONS as MAX_QUESTIONS,
  COMBINED_QUIZ_MAX_SOURCE_NOTES as MAX_SOURCE_NOTES,
  isCombinedQuizSelectionOverCap,
} from "@/lib/combined-quiz";

function parseSelectedNoteIds(value: string | null): string[] {
  if (!value) {
    return [];
  }
  return value
    .split(",")
    .map((noteId) => noteId.trim())
    .filter((noteId, index, allIds) => noteId.length > 0 && allIds.indexOf(noteId) === index);
}

function isQuizReady(note: NoteListItemResponse): boolean {
  return Boolean(note.generatedQuizId);
}

export default function CombinedQuizBuilderPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const notesParam = searchParams.get("notes");
  const selectedNoteIds = useMemo(() => parseSelectedNoteIds(notesParam), [notesParam]);
  const [selectedNotes, setSelectedNotes] = useState<NoteListItemResponse[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [title, setTitle] = useState("Combined quiz");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [assembling, setAssembling] = useState(false);

  const loadNotes = useCallback(async () => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    setLoadError(null);
    try {
      const notes = await listNotes();
      const notesById = new Map(notes.map((note) => [note.id, note]));
      setSelectedNotes(selectedNoteIds
        .map((noteId) => notesById.get(noteId))
        .filter((note): note is NoteListItemResponse => Boolean(note)));
    } catch (error) {
      setSelectedNotes(null);
      setLoadError(error instanceof Error ? error.message : "Could not load the selected notes.");
    }
  }, [router, selectedNoteIds]);

  useEffect(() => {
    void loadNotes();
  }, [loadNotes]);

  const eligibleNotes = useMemo(
    () => (selectedNotes ?? []).filter(isQuizReady),
    [selectedNotes],
  );
  const excludedCount = (selectedNotes?.length ?? 0) - eligibleNotes.length;
  const unavailableCount = selectedNoteIds.length - (selectedNotes?.length ?? 0);
  const totalQuestionCount = useMemo(
    () => eligibleNotes.reduce((total, note) => total + (note.generatedQuizQuestionCount ?? 0), 0),
    [eligibleNotes],
  );
  const overCap = isCombinedQuizSelectionOverCap(eligibleNotes.length, totalQuestionCount);
  const canAssemble = title.trim().length > 0
    && eligibleNotes.length > 0
    && unavailableCount === 0
    && !overCap
    && !assembling;

  const handleAssemble = useCallback(async () => {
    if (!canAssemble) {
      return;
    }
    setAssembling(true);
    setSubmitError(null);
    try {
      const assembled = await createCombinedQuiz({
        title: title.trim(),
        sections: eligibleNotes.map((note) => ({
          noteId: note.id,
          questionIndexes: Array.from({ length: note.generatedQuizQuestionCount ?? 0 }, (_, index) => index),
        })),
      });
      router.push(`/library/combined-quiz/${assembled.id}`);
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === "COMBINED_QUIZ_INVALID") {
        setSubmitError("A source quiz changed length or this selection is no longer valid. Review the totals, then try again without re-picking your notes.");
      } else {
        setSubmitError(error instanceof Error ? error.message : "Could not assemble combined quiz. Please try again.");
      }
    } finally {
      setAssembling(false);
    }
  }, [canAssemble, eligibleNotes, router, title]);

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href="/library" label="Library" />
      <PageHeader
        eyebrow="COMBINED QUIZ"
        title="Build Combined Quiz"
        description="Name one immutable quiz assembled from the generated quizzes in your selected notes."
      />

      {loadError ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load selected notes</h2>
          <p className="text-sm text-foreground/75">{loadError}</p>
          <Button type="button" onClick={() => void loadNotes()}>Retry</Button>
        </Card>
      ) : selectedNotes === null ? (
        <Card className="space-y-3 p-4 sm:p-6" aria-label="Loading selected notes">
          <div className="h-6 w-48 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        </Card>
      ) : (
        <Card className="space-y-5 p-4 sm:p-6">
          <div className="space-y-1">
            <h2 className="text-lg font-semibold">Confirm your quiz</h2>
            <p className="text-sm text-foreground/75">
              Every eligible note contributes all of its generated questions. This snapshot cannot be edited or re-assembled after it is created.
            </p>
          </div>

          <div className="space-y-2">
            <label htmlFor="combined-quiz-title" className="text-sm font-medium">Quiz title</label>
            <input
              id="combined-quiz-title"
              type="text"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              maxLength={150}
              required
              aria-describedby="combined-quiz-title-help"
              className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-blue-600"
            />
            <p id="combined-quiz-title-help" className="text-xs text-foreground/60">
              This is the heading your recipient sees. It is not taken from any one note.
            </p>
          </div>

          <div className="rounded-xl border border-border bg-highlight/40 p-4 text-sm">
            <p className="font-medium">{eligibleNotes.length} eligible source note{eligibleNotes.length === 1 ? "" : "s"} · {totalQuestionCount} of {MAX_QUESTIONS} questions</p>
            <p className="mt-1 text-foreground/70">Combined quizzes allow up to {MAX_SOURCE_NOTES} source notes and {MAX_QUESTIONS} questions.</p>
            {excludedCount > 0 ? (
              <p className="mt-2 font-medium text-amber-700 dark:text-amber-300">
                {excludedCount} selected note{excludedCount === 1 ? " has" : "s have"} no generated quiz and will not be included. Generate a quiz first to include {excludedCount === 1 ? "it" : "them"}.
              </p>
            ) : null}
            {unavailableCount > 0 ? (
              <p className="mt-2 font-medium text-amber-700 dark:text-amber-300">
                {unavailableCount} selected note{unavailableCount === 1 ? " is" : "s are"} no longer available. Return to Library and choose again.
              </p>
            ) : null}
            {overCap ? (
              <p className="mt-2 font-medium text-amber-700 dark:text-amber-300">
                This selection exceeds the combined-quiz limit. Remove notes before assembling; nothing has been created.
              </p>
            ) : null}
            {selectedNoteIds.length === 0 ? (
              <p className="mt-2 font-medium text-amber-700 dark:text-amber-300">Choose at least one note in Library before building a quiz.</p>
            ) : null}
            {selectedNoteIds.length > 0 && eligibleNotes.length === 0 ? (
              <p className="mt-2 font-medium text-amber-700 dark:text-amber-300">Generate a quiz for at least one selected note before assembling.</p>
            ) : null}
          </div>

          <p className="text-xs text-foreground/60">
            A note needs both a Study Pack generation and a quiz generation before it can contribute. Assembling this quiz costs nothing.
          </p>

          {submitError ? <p role="alert" className="rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-800 dark:text-red-200">{submitError}</p> : null}

          <div className="flex flex-col gap-2 sm:flex-row">
            <Button
              type="button"
              onClick={() => void handleAssemble()}
              loading={assembling}
              loadingText="Assembling..."
              aria-disabled={!canAssemble}
              className="aria-disabled:pointer-events-none aria-disabled:opacity-50"
            >
              Assemble quiz
            </Button>
            <Button type="button" variant="outline" onClick={() => router.push("/library")}>Library</Button>
          </div>
        </Card>
      )}
    </main>
  );
}
