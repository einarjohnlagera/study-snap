"use client";

import { useEffect, useState } from "react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { getAuthUser } from "@/lib/auth";
import { getDisplayedQuizChoices, resolveQuizCorrectIndex } from "@/lib/quiz";
import type { QuizItem } from "@/lib/api";
import { normalizePublicNoteText } from "@/lib/public-note-text";
import { PublicSeoCopyCta } from "./public-seo-copy-cta";

type PublicMiniQuizPreviewProps = Readonly<{
  quiz: QuizItem[];
  noteId: string;
}>;

export function PublicMiniQuizPreview({ quiz, noteId }: PublicMiniQuizPreviewProps) {
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    setIsAuthenticated(!!getAuthUser());
    const syncAuth = () => setIsAuthenticated(!!getAuthUser());
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => globalThis.removeEventListener("studysnap-auth-change", syncAuth);
  }, []);

  if (quiz.length === 0) {
    return null;
  }

  const item = quiz[0];
  const choices = getDisplayedQuizChoices(item);
  const correctCanonicalIndex = resolveQuizCorrectIndex(item);

  const handleSubmit = () => {
    if (selectedIndex === null) return;
    setSubmitted(true);
  };

  const getChoiceStyle = (canonicalIndex: number): string => {
    const base = "w-full rounded-xl border px-4 py-3 text-left text-sm transition-colors";
    if (!submitted) {
      return selectedIndex === canonicalIndex
        ? `${base} border-primary bg-primary/10 text-foreground`
        : `${base} border-border bg-background text-foreground/85 hover:bg-highlight`;
    }
    if (canonicalIndex === correctCanonicalIndex) {
      return `${base} border-emerald-500/60 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300`;
    }
    if (canonicalIndex === selectedIndex) {
      return `${base} border-red-400/60 bg-red-400/10 text-red-700 dark:text-red-400`;
    }
    return `${base} border-border bg-background text-foreground/55`;
  };

  const normalizedQuestion = normalizePublicNoteText(item.question);
  const normalizedExplanation = normalizePublicNoteText(item.explanation);

  return (
    <Card className="space-y-4 p-4 sm:p-6" aria-label="Quick Check mini quiz">
      <div className="space-y-1">
        <h2 className="text-base font-semibold sm:text-lg">🧠 Quick Check</h2>
        <p className="text-sm text-foreground/65">Quick check: see what you remember from the summary.</p>
      </div>

      <p className="text-sm font-medium text-foreground">{normalizedQuestion}</p>

      <div className="space-y-2" role="group" aria-label="Answer choices">
        {choices.map((choice) => (
          <button
            key={`choice-${choice.canonicalIndex}`}
            type="button"
            disabled={submitted}
            aria-pressed={selectedIndex === choice.canonicalIndex}
            className={getChoiceStyle(choice.canonicalIndex)}
            onClick={() => setSelectedIndex(choice.canonicalIndex)}
          >
            <span className="mr-2 font-semibold">{choice.label}.</span>
            {choice.text}
          </button>
        ))}
      </div>

      {!submitted ? (
        <Button
          type="button"
          onClick={handleSubmit}
          disabled={selectedIndex === null}
          className="w-full sm:w-auto"
        >
          Check Answer
        </Button>
      ) : null}

      {submitted ? (
        <div className="space-y-4">
          <div className="rounded-xl border border-border bg-muted/30 p-4 text-sm">
            <p className="mb-1 font-medium text-foreground/90">
              {selectedIndex === correctCanonicalIndex ? "✓ Correct!" : "✗ Not quite."}
            </p>
            {normalizedExplanation ? (
              <p className="leading-relaxed text-foreground/75">{normalizedExplanation}</p>
            ) : null}
          </div>

          <div className="space-y-3 rounded-xl border border-primary/25 bg-primary/5 p-4">
            <div className="space-y-1">
              <p className="text-sm font-medium text-foreground">Want more practice like this?</p>
              <p className="text-sm text-foreground/70">
                {isAuthenticated
                  ? "Save this note or turn it into your own Study Pack to keep reviewing in your workspace."
                  : "Create a free account or save this note to keep reviewing in your own workspace."}
              </p>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <PublicSeoCopyCta
                noteId={noteId}
                label="Create your own Study Pack"
                redirectTarget="generate"
                guestAuthMode="signup"
              />
              <PublicSeoCopyCta
                noteId={noteId}
                label="Copy to My Library"
                guestAuthMode="signup"
              />
            </div>
          </div>
        </div>
      ) : null}
    </Card>
  );
}
