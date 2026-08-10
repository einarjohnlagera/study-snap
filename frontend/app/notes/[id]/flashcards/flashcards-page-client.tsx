"use client";

import { useRouter } from "next/navigation";
import { BookMarked, ChevronLeft, ChevronRight, RotateCcw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { getAuthUser } from "@/lib/auth";
import { getNote, type NoteResponse } from "@/lib/api";
import { buildFlashcardDeck } from "@/lib/flashcards";
import { buildNoteDetailPathWithTab } from "@/lib/note-entry";
import { cn } from "@/lib/utils";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

type LoadState = "loading" | "ready" | "error";

function FlashcardsGuard({ title, message }: Readonly<{ title: string; message: string }>) {
  return (
    <Card className="space-y-4 p-5 sm:p-6">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">{title}</h1>
        <p className="text-sm leading-relaxed text-foreground/75">{message}</p>
      </div>
    </Card>
  );
}

export function FlashcardsPageClient({ noteId }: Readonly<{ noteId: string }>) {
  const { replace } = useRouter();
  const noteHref = buildNoteDetailPathWithTab(noteId, "key-concepts");
  const isTeacherMode = getAuthUser()?.profileType === "TEACHER";
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);

  useEffect(() => {
    if (isTeacherMode) {
      replace(noteHref);
      return;
    }

    let active = true;
    getNote(noteId)
      .then((loadedNote) => {
        if (!active) return;
        setNote(loadedNote);
        setCurrentIndex(0);
        setFlipped(false);
        setLoadState("ready");
      })
      .catch((err: unknown) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Could not load flashcards.");
        setLoadState("error");
      });

    return () => {
      active = false;
    };
  }, [isTeacherMode, noteHref, noteId, replace]);

  const deck = useMemo(() => buildFlashcardDeck(note?.keyConcepts ?? [], note?.quiz ?? []), [note]);
  const currentCard = deck[currentIndex] ?? null;
  const studyPackStatus = note?.studyPackStatus ?? "DRAFT";
  const hasGeneratedStudyPack = studyPackStatus === "STUDY_PACK_READY";

  const handlePrevious = () => {
    setCurrentIndex((value) => Math.max(0, value - 1));
    setFlipped(false);
  };

  const handleNext = () => {
    setCurrentIndex((value) => Math.min(deck.length - 1, value + 1));
    setFlipped(false);
  };

  if (isTeacherMode) {
    return (
      <main className="mx-auto w-full max-w-3xl space-y-4 px-4 py-6 sm:px-6 sm:py-10">
        <BackLink href={noteHref} label="Note" />
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-3xl space-y-5 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href={noteHref} label="Note" />

      {loadState === "loading" ? (
        <FlashcardsGuard
          title="Flashcards"
          message="Loading flashcards from this note's Study Pack."
        />
      ) : null}

      {loadState === "error" ? (
        <FlashcardsGuard
          title="Flashcards unavailable"
          message={error ?? "Could not load flashcards."}
        />
      ) : null}

      {loadState === "ready" && note ? (
        <>
          <header className="space-y-3">
            <div className="inline-flex items-center gap-2 rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-foreground/70">
              <BookMarked className="h-3.5 w-3.5" aria-hidden="true" />
              <span>Flashcards</span>
            </div>
            <div className="space-y-2">
              <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
                {note.title ?? "Untitled note"}
              </h1>
              <p className="max-w-2xl text-base leading-relaxed text-foreground/70">
                Review key concepts from this Study Pack without a score or timer.
              </p>
            </div>
          </header>

          {studyPackStatus === "GENERATING" ? (
            <FlashcardsGuard
              title="Key concepts are being generated"
              message="Key concepts are being generated from your note."
            />
          ) : null}

          {studyPackStatus === "FAILED" ? (
            <FlashcardsGuard
              title="Flashcards are not available yet"
              message="Generation did not complete, so key concepts are not available yet. Retry generation when you are ready."
            />
          ) : null}

          {!hasGeneratedStudyPack && studyPackStatus !== "GENERATING" && studyPackStatus !== "FAILED" ? (
            <FlashcardsGuard
              title="No key concepts yet"
              message="No key concepts yet. Generate a Study Pack to extract the most important ideas from this note."
            />
          ) : null}

          {hasGeneratedStudyPack && deck.length === 0 ? (
            <FlashcardsGuard
              title="Nothing to review yet"
              message="This Study Pack does not have key concepts to review yet."
            />
          ) : null}

          {hasGeneratedStudyPack && currentCard ? (
            <section className="space-y-4" aria-label="Flashcard deck">
              <div className="flex items-center justify-between gap-3 text-sm text-foreground/65">
                <span>Card {currentIndex + 1} of {deck.length}</span>
                <span>{flipped ? "Definition" : "Concept"}</span>
              </div>
              <button
                type="button"
                aria-label={flipped ? "Flip to concept" : "Flip to definition"}
                onClick={() => setFlipped((value) => !value)}
                className={cn(
                  "flex min-h-[20rem] w-full flex-col items-center justify-center rounded-2xl border border-border bg-card px-5 py-8 text-center shadow-sm transition hover:border-foreground/25 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 sm:min-h-[24rem] sm:px-10",
                  flipped ? "bg-muted/30" : "bg-card",
                )}
              >
                {flipped ? (
                  <div className="max-w-2xl space-y-4">
                    <p className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/50">
                      {currentCard.concept}
                    </p>
                    {currentCard.explanation ? (
                      <p className="text-xl font-medium leading-relaxed text-foreground sm:text-2xl">
                        {renderMathText(currentCard.explanation)}
                      </p>
                    ) : (
                      <div className="space-y-2 rounded-lg border border-dashed border-border bg-background/70 px-4 py-4">
                        <p className="text-base font-medium text-foreground">No definition yet for this concept.</p>
                        <p className="text-sm leading-relaxed text-foreground/65">
                          This Study Pack did not include a quiz explanation matched to this concept.
                        </p>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="max-w-2xl space-y-4">
                    <p className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/50">
                      Concept
                    </p>
                    <p className="text-3xl font-semibold leading-tight text-foreground sm:text-5xl">
                      {currentCard.concept}
                    </p>
                  </div>
                )}
              </button>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="grid grid-cols-2 gap-2 sm:flex">
                  <Button type="button" variant="outline" onClick={handlePrevious} disabled={currentIndex === 0}>
                    <ChevronLeft className="mr-2 h-4 w-4" aria-hidden="true" />
                    Previous
                  </Button>
                  <Button type="button" variant="outline" onClick={handleNext} disabled={currentIndex === deck.length - 1}>
                    Next
                    <ChevronRight className="ml-2 h-4 w-4" aria-hidden="true" />
                  </Button>
                </div>
                <Button type="button" variant="secondary" onClick={() => setFlipped((value) => !value)}>
                  <RotateCcw className="mr-2 h-4 w-4" aria-hidden="true" />
                  {flipped ? "Show concept" : "Show definition"}
                </Button>
              </div>
            </section>
          ) : null}
        </>
      ) : null}
    </main>
  );
}
