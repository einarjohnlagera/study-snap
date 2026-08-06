"use client";

import { useEffect, useState } from "react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { getAuthUser } from "@/lib/auth";
import { getDisplayedQuizChoices, resolveQuizCorrectIndex } from "@/lib/quiz";
import type { QuizItem } from "@/lib/api";
import { normalizePublicNoteText } from "@/lib/public-note-text";
import { buildPublicLibraryNotePath, buildPublicLibrarySubjectPath } from "@/lib/public-note-path";
import { resolveCardExcerpt } from "@/components/notes/shared-note-card";
import { PublicLibraryReturnLink } from "@/components/notes/public-library-return-link";
import { PublicSeoCopyCta } from "./public-seo-copy-cta";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

const MAX_PREVIEW_QUESTIONS = 3;

type QuestionState = {
  selectedIndex: number | null;
  submitted: boolean;
};

type RelatedNote = {
  id: string;
  title: string | null | undefined;
  subject: string | null | undefined;
  summaryPreview: string | null | undefined;
  contentPreview: string | null | undefined;
};

type PublicMiniQuizPreviewProps = Readonly<{
  quiz: QuizItem[];
  noteId: string;
  relatedNotes?: RelatedNote[];
}>;

function getCorrectFeedback(questionIndex: number): string {
  return (["✅ Correct!", "🧠 Nice work!", "✅ That's right!"] as const)[questionIndex % 3];
}

export function PublicMiniQuizPreview({ quiz, noteId, relatedNotes }: PublicMiniQuizPreviewProps) {
  const previewItems = quiz.slice(0, MAX_PREVIEW_QUESTIONS);
  const [questionStates, setQuestionStates] = useState<QuestionState[]>(() =>
    previewItems.map(() => ({ selectedIndex: null, submitted: false })),
  );
  const [currentIndex, setCurrentIndex] = useState(0);
  const [completed, setCompleted] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(() => !!getAuthUser());

  useEffect(() => {
    const syncAuth = () => setIsAuthenticated(!!getAuthUser());
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => globalThis.removeEventListener("studysnap-auth-change", syncAuth);
  }, []);

  if (previewItems.length === 0) {
    return null;
  }

  const total = previewItems.length;
  const currentItem = previewItems[currentIndex];
  const currentState = questionStates[currentIndex];
  const choices = getDisplayedQuizChoices(currentItem);
  const correctCanonicalIndex = resolveQuizCorrectIndex(currentItem);
  const isCorrect = currentState.selectedIndex === correctCanonicalIndex;

  const handleSelectChoice = (canonicalIndex: number) => {
    if (currentState.submitted) return;
    setQuestionStates((prev) => {
      const next = [...prev];
      next[currentIndex] = { ...next[currentIndex], selectedIndex: canonicalIndex };
      return next;
    });
  };

  const handleSubmit = () => {
    if (currentState.selectedIndex === null) return;
    setQuestionStates((prev) => {
      const next = [...prev];
      next[currentIndex] = { ...next[currentIndex], submitted: true };
      return next;
    });
  };

  const handleNext = () => {
    if (currentIndex + 1 >= total) {
      setCompleted(true);
    } else {
      setCurrentIndex(currentIndex + 1);
    }
  };

  const getChoiceStyle = (canonicalIndex: number): string => {
    const base = "w-full rounded-xl border px-4 py-3 text-left text-sm transition-colors";
    if (!currentState.submitted) {
      return currentState.selectedIndex === canonicalIndex
        ? `${base} border-primary bg-primary/10 text-foreground`
        : `${base} border-border bg-background text-foreground/85 hover:bg-highlight`;
    }
    if (canonicalIndex === correctCanonicalIndex) {
      return `${base} border-emerald-500/60 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300`;
    }
    if (canonicalIndex === currentState.selectedIndex) {
      return `${base} border-red-400/60 bg-red-400/10 text-red-700 dark:text-red-400`;
    }
    return `${base} border-border bg-background text-foreground/55`;
  };

  const normalizedQuestion = normalizePublicNoteText(currentItem.question);
  const normalizedExplanation = normalizePublicNoteText(currentItem.explanation);
  const isLastQuestion = currentIndex + 1 >= total;

  if (completed) {
    return (
      <Card className="space-y-4 p-4 sm:p-6" aria-label="Quick Check complete">
        <div className="space-y-1">
          <h2 className="text-base font-semibold sm:text-lg">🎉 Quick Check Complete</h2>
          <p className="text-sm text-foreground/65">
            You completed all {total} preview question{total !== 1 ? "s" : ""}. Want the full quiz and your results saved?
          </p>
        </div>

        <div className="space-y-3 rounded-xl border border-primary/25 bg-primary/5 p-4">
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">Quiz yourself on this note.</p>
            <p className="text-sm text-foreground/70">
              {isAuthenticated
                ? "Copy this note to your library and continue studying with the full quiz experience."
                : "Create a free account to access the full quiz, save notes, and study from your own materials."}
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <PublicSeoCopyCta
              noteId={noteId}
              label="Quiz yourself on this note"
              redirectTarget="quick-review"
              action="quickReview"
              analyticsEvent="PUBLIC_NOTE_QUIZ_YOURSELF_CLICKED"
              authModalTitle="Quiz yourself on this note"
              authModalBody="Create a free account or log in to quiz yourself on this note and keep practicing."
            />
            <PublicSeoCopyCta
              noteId={noteId}
              label="Copy to My Library"
              variant="outline"
            />
          </div>
        </div>

        {relatedNotes && relatedNotes.length > 0 ? (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-foreground/80">
              More from {relatedNotes[0].subject ?? "this subject"}
            </h3>
            <div className="space-y-2">
              {relatedNotes.map((related) => {
                const relatedTitle = normalizePublicNoteText(related.title) || "Untitled note";
                const excerpt = resolveCardExcerpt(related.contentPreview, related.summaryPreview);
                const preview = excerpt.kind !== "none" ? normalizePublicNoteText(excerpt.text) : "";
                const href = buildPublicLibraryNotePath({ subject: related.subject, title: related.title });
                const returnUrl = buildPublicLibrarySubjectPath(related.subject);
                return (
                  <PublicLibraryReturnLink
                    key={related.id}
                    href={href}
                    returnUrl={returnUrl}
                    className="block rounded-xl border border-border bg-background p-3 transition-colors hover:border-blue-500/40 hover:bg-blue-500/5"
                  >
                    <p className="text-sm font-medium text-foreground">{relatedTitle}</p>
                    {preview ? (
                      <p className="mt-0.5 line-clamp-1 text-xs text-foreground/60">{preview}</p>
                    ) : null}
                  </PublicLibraryReturnLink>
                );
              })}
            </div>
          </div>
        ) : null}
      </Card>
    );
  }

  return (
    <Card className="space-y-4 p-4 sm:p-6" aria-label="Quick Check mini quiz">
      <div className="flex items-start justify-between gap-2">
        <div className="space-y-0.5">
          <h2 className="text-base font-semibold sm:text-lg">🧠 Quick Check</h2>
          <p className="text-xs text-foreground/55">See what you remember from the summary.</p>
        </div>
        {total > 1 ? (
          <span className="shrink-0 rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-foreground/60">
            {currentIndex + 1} / {total}
          </span>
        ) : null}
      </div>

      <p className="text-sm font-medium text-foreground">{renderMathText(normalizedQuestion)}</p>

      <div className="space-y-2" role="group" aria-label="Answer choices">
        {choices.map((choice) => (
          <button
            key={`choice-${choice.canonicalIndex}`}
            type="button"
            disabled={currentState.submitted}
            aria-pressed={currentState.selectedIndex === choice.canonicalIndex}
            className={getChoiceStyle(choice.canonicalIndex)}
            onClick={() => handleSelectChoice(choice.canonicalIndex)}
          >
            <span className="mr-2 font-semibold">{choice.label}.</span>
            {renderMathText(choice.text)}
          </button>
        ))}
      </div>

      {!currentState.submitted ? (
        <Button
          type="button"
          onClick={handleSubmit}
          disabled={currentState.selectedIndex === null}
          className="w-full sm:w-auto"
        >
          Check Answer
        </Button>
      ) : null}

      {currentState.submitted ? (
        <div className="space-y-3">
          <div className="rounded-xl border border-border bg-muted/30 p-4 text-sm">
            <p className="mb-1 font-medium text-foreground/90">
              {isCorrect ? getCorrectFeedback(currentIndex) : "Almost there."}
            </p>
            {normalizedExplanation ? (
              <p className="leading-relaxed text-foreground/75">{renderMathText(normalizedExplanation)}</p>
            ) : null}
          </div>

          <Button type="button" onClick={handleNext} className="w-full sm:w-auto">
            {isLastQuestion ? "See Results" : "Next Question →"}
          </Button>
        </div>
      ) : null}
    </Card>
  );
}
