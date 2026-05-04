"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { getAuthUser, buildLoginPath } from "@/lib/auth";
import { getDisplayedQuizChoices, resolveQuizCorrectIndex } from "@/lib/quiz";
import type { QuizItem } from "@/lib/api";

type PublicMiniQuizPreviewProps = Readonly<{
  quiz: QuizItem[];
  noteId: string;
  currentPath: string;
}>;

export function PublicMiniQuizPreview({ quiz, noteId, currentPath }: PublicMiniQuizPreviewProps) {
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

  const loginHref = buildLoginPath({ redirectTo: currentPath });

  return (
    <Card className="space-y-4 p-4 sm:p-6" aria-label="Quick Check mini quiz">
      <div className="space-y-1">
        <h2 className="text-base font-semibold sm:text-lg">🧠 Quick Check</h2>
        <p className="text-sm text-foreground/65">Test your understanding with one question</p>
      </div>

      <p className="text-sm font-medium text-foreground">{item.question}</p>

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
            {item.explanation ? (
              <p className="leading-relaxed text-foreground/75">{item.explanation}</p>
            ) : null}
          </div>

          {isAuthenticated ? (
            <p className="text-sm text-foreground/65">
              Open your copy of this note to practice all questions and track your progress.
            </p>
          ) : (
            <div className="rounded-xl border border-primary/25 bg-primary/5 p-4 space-y-3">
              <p className="text-sm font-medium text-foreground">Want to continue?</p>
              <ul className="space-y-1 pl-4 text-sm text-foreground/75 list-disc">
                <li>Practice all questions</li>
                <li>Track progress and weak areas</li>
                <li>Generate your own Study Pack</li>
              </ul>
              <Link
                href={loginHref}
                className="inline-flex items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-hover active:bg-primary-active"
              >
                Continue with NoteLib →
              </Link>
            </div>
          )}
        </div>
      ) : null}
    </Card>
  );
}
