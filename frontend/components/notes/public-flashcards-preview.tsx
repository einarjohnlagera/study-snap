"use client";

import { useEffect, useState } from "react";
import { RotateCcw } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { getAuthUser } from "@/lib/auth";
import { buildMatchedFlashcards } from "@/lib/flashcards";
import type { QuizItem } from "@/lib/api";
import { normalizePublicNoteText } from "@/lib/public-note-text";
import { PublicSeoCopyCta } from "./public-seo-copy-cta";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

const MAX_PREVIEW_CARDS = 3;

type PublicFlashcardsPreviewProps = Readonly<{
  keyConcepts: string[];
  quiz: QuizItem[];
  noteId: string;
}>;

export function PublicFlashcardsPreview({ keyConcepts, quiz, noteId }: PublicFlashcardsPreviewProps) {
  const previewCards = buildMatchedFlashcards(keyConcepts, quiz).slice(0, MAX_PREVIEW_CARDS);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(() => !!getAuthUser());

  useEffect(() => {
    const syncAuth = () => setIsAuthenticated(!!getAuthUser());
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => globalThis.removeEventListener("studysnap-auth-change", syncAuth);
  }, []);

  if (previewCards.length === 0) {
    return null;
  }

  const total = previewCards.length;
  const currentCard = previewCards[currentIndex];
  const isLastCard = currentIndex + 1 >= total;

  const handleNext = () => {
    if (isLastCard) {
      setCompleted(true);
    } else {
      setCurrentIndex((value) => value + 1);
      setFlipped(false);
    }
  };

  if (completed) {
    return (
      <Card className="space-y-4 p-4 sm:p-6" aria-label="Flashcards preview complete">
        <div className="space-y-1">
          <h2 className="text-base font-semibold sm:text-lg">🎉 Flashcards Preview Complete</h2>
          <p className="text-sm text-foreground/65">
            You&apos;ve previewed {total} flashcard{total !== 1 ? "s" : ""}. Want to keep learning this way?
          </p>
        </div>

        <div className="space-y-3 rounded-xl border border-primary/25 bg-primary/5 p-4">
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">Keep the momentum going.</p>
            <p className="text-sm text-foreground/70">
              {isAuthenticated
                ? "Copy this note to your library and continue learning with the full deck."
                : "Create a free account to continue learning with the full deck."}
            </p>
          </div>
          <PublicSeoCopyCta
            noteId={noteId}
            label="Continue Learning"
            analyticsEvent="PUBLIC_NOTE_FLASHCARDS_CLICKED"
            authModalTitle="Continue learning with Flashcards"
            authModalBody="Create a free account or log in to review the full flashcard deck for this note."
          />
        </div>
      </Card>
    );
  }

  return (
    <Card className="space-y-4 p-4 sm:p-6" aria-label="Flashcards preview">
      <div className="flex items-start justify-between gap-2">
        <div className="space-y-0.5">
          <h2 className="text-base font-semibold sm:text-lg">🧠 Flashcards Preview</h2>
          <p className="text-xs text-foreground/55">Tap a card to reveal the definition.</p>
        </div>
        {total > 1 ? (
          <span className="shrink-0 rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-foreground/60">
            {currentIndex + 1} / {total}
          </span>
        ) : null}
      </div>

      <button
        type="button"
        aria-label={flipped ? "Flip to concept" : "Flip to definition"}
        onClick={() => setFlipped((value) => !value)}
        className="flex min-h-[12rem] w-full flex-col items-center justify-center rounded-2xl border border-border bg-card px-5 py-8 text-center shadow-sm transition hover:border-foreground/25 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 sm:min-h-[14rem]"
      >
        {flipped ? (
          <div className="max-w-2xl space-y-3">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/50">
              {normalizePublicNoteText(currentCard.concept)}
            </p>
            <p className="text-lg font-medium leading-relaxed text-foreground sm:text-xl">
              {renderMathText(normalizePublicNoteText(currentCard.explanation))}
            </p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-3">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/50">Concept</p>
            <p className="text-2xl font-semibold leading-tight text-foreground sm:text-3xl">
              {normalizePublicNoteText(currentCard.concept)}
            </p>
          </div>
        )}
      </button>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Button type="button" variant="secondary" onClick={() => setFlipped((value) => !value)}>
          <RotateCcw className="mr-2 h-4 w-4" aria-hidden="true" />
          {flipped ? "Show concept" : "Show definition"}
        </Button>
        <Button type="button" onClick={handleNext} className="w-full sm:w-auto">
          {isLastCard ? "See Results" : "Next Card →"}
        </Button>
      </div>
    </Card>
  );
}
