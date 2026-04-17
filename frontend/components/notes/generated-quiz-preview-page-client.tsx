"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { getGeneratedQuiz, generateGeneratedQuiz, getNote, type GeneratedQuizResponse, type NoteResponse } from "@/lib/api";
import { exportGeneratedQuizDocument, type GeneratedQuizExportType } from "@/lib/generated-quiz-export";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { resolveQuizCorrectIndex } from "@/lib/quiz";

type GeneratedQuizPreviewPageClientProps = {
  noteId: string;
};

const GENERATED_DATE_FORMATTER = new Intl.DateTimeFormat("en-US", {
  year: "numeric",
  month: "long",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
});

export function GeneratedQuizPreviewPageClient({ noteId }: Readonly<GeneratedQuizPreviewPageClientProps>) {
  const router = useRouter();
  const exportMenuRef = useRef<HTMLDivElement | null>(null);
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [generatedQuiz, setGeneratedQuiz] = useState<GeneratedQuizResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [exportMenuOpen, setExportMenuOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [showRegenerateConfirm, setShowRegenerateConfirm] = useState(false);

  const loadPage = useCallback(async () => {
    if (!noteId) {
      setError("Note not found.");
      setLoading(false);
      return;
    }
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const [loadedNote, loadedQuiz] = await Promise.all([
        getNote(noteId),
        getGeneratedQuiz(noteId),
      ]);
      setNote(loadedNote);
      setGeneratedQuiz(loadedQuiz);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load quiz preview.";
      setError(message);
      setGeneratedQuiz(null);
    } finally {
      setLoading(false);
    }
  }, [noteId, router]);

  useEffect(() => {
    void loadPage();
  }, [loadPage]);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timeoutId = window.setTimeout(() => setToast(null), 2200);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  useEffect(() => {
    if (!exportMenuOpen) {
      return undefined;
    }
    const handlePointerDown = (event: MouseEvent) => {
      if (!exportMenuRef.current || exportMenuRef.current.contains(event.target as Node)) {
        return;
      }
      setExportMenuOpen(false);
    };
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setExportMenuOpen(false);
      }
    };
    window.addEventListener("mousedown", handlePointerDown);
    window.addEventListener("keydown", handleEscape);
    return () => {
      window.removeEventListener("mousedown", handlePointerDown);
      window.removeEventListener("keydown", handleEscape);
    };
  }, [exportMenuOpen]);

  const noteTitle = note?.title?.trim() || "Untitled note";
  const noteSubject = note?.subject ?? null;
  const generatedAtLabel = useMemo(() => {
    if (!generatedQuiz?.generatedAt) {
      return null;
    }
    return GENERATED_DATE_FORMATTER.format(new Date(generatedQuiz.generatedAt));
  }, [generatedQuiz?.generatedAt]);

  const handleExport = useCallback(async (exportType: GeneratedQuizExportType) => {
    if (!generatedQuiz || exporting) {
      return;
    }
    setExportMenuOpen(false);
    setExporting(true);
    setToast("Exporting...");
    try {
      await exportGeneratedQuizDocument({
        exportType,
        noteTitle,
        noteSubject,
        quiz: generatedQuiz.questions,
      });
      setToast("Export ready.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not export quiz.";
      setError(message);
      setToast(null);
    } finally {
      setExporting(false);
    }
  }, [exporting, generatedQuiz, noteSubject, noteTitle]);

  const handleRegenerate = useCallback(async () => {
    if (regenerating) {
      return;
    }
    setRegenerating(true);
    setError(null);
    try {
      const nextQuiz = await generateGeneratedQuiz(noteId);
      setGeneratedQuiz(nextQuiz);
      setShowRegenerateConfirm(false);
      setToast("Quiz regenerated.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not regenerate quiz.";
      setError(message);
    } finally {
      setRegenerating(false);
    }
  }, [noteId, regenerating]);

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <BackLink href={`/notes/${noteId}`} label="Back to Note" />

      {loading ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <h1 className="text-xl font-semibold sm:text-2xl">Quiz Preview</h1>
          <p className="text-sm text-foreground/75">Loading your generated quiz...</p>
        </Card>
      ) : error ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <h1 className="text-xl font-semibold sm:text-2xl">Quiz Preview</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" onClick={() => void loadPage()}>
              Try Again
            </Button>
            <Button type="button" variant="outline" onClick={() => router.push(`/notes/${noteId}`)}>
              Back to Note
            </Button>
          </div>
        </Card>
      ) : !generatedQuiz ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <h1 className="text-xl font-semibold sm:text-2xl">Quiz Preview</h1>
          <p className="text-sm text-foreground/75">
            No generated quiz is available for this note yet. Generate one from Note Detail first.
          </p>
        </Card>
      ) : (
        <>
          <Card className="space-y-4 p-4 sm:p-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div className="space-y-2">
                <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                  Quiz Preview
                </p>
                <h1 className="text-2xl font-semibold sm:text-3xl">{noteTitle}</h1>
                <p className="text-sm text-foreground/75">
                  Review the generated quiz with answers and explanations visible before exporting it for class use.
                </p>
                {generatedAtLabel ? (
                  <p className="text-xs text-foreground/60">Generated {generatedAtLabel}</p>
                ) : null}
              </div>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <Button type="button" variant="outline" onClick={() => setShowRegenerateConfirm(true)} disabled={regenerating}>
                  {regenerating ? "Regenerating..." : "Regenerate Quiz"}
                </Button>
                <div className="relative" ref={exportMenuRef}>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setExportMenuOpen((previous) => !previous)}
                    disabled={exporting}
                    aria-haspopup="menu"
                    aria-expanded={exportMenuOpen}
                  >
                    {exporting ? "Exporting..." : "Export"}
                  </Button>
                  {exportMenuOpen ? (
                    <div
                      role="menu"
                      aria-label="Export quiz"
                      className="absolute right-0 top-12 z-20 min-w-60 rounded-xl border border-border bg-background p-1.5 shadow-sm"
                    >
                      <button
                        type="button"
                        role="menuitem"
                        className="w-full rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-highlight"
                        onClick={() => void handleExport("questions-only")}
                      >
                        Export Questions Only
                      </button>
                      <button
                        type="button"
                        role="menuitem"
                        className="w-full rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-highlight"
                        onClick={() => void handleExport("questions-answers")}
                      >
                        Export Questions + Answers
                      </button>
                      <button
                        type="button"
                        role="menuitem"
                        className="w-full rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-highlight"
                        onClick={() => void handleExport("answer-key")}
                      >
                        Export Answer Key
                      </button>
                    </div>
                  ) : null}
                </div>
              </div>
            </div>
          </Card>

          <div className="space-y-4">
            {generatedQuiz.questions.map((question, index) => (
              <Card key={`${question.question}-${index}`} className="space-y-4 p-4 sm:p-6">
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">
                    Question {index + 1}
                  </p>
                  <h2 className="text-lg font-semibold text-foreground">
                    {question.question}
                  </h2>
                </div>
                <QuizChoiceList
                  questionKey={question.question}
                  choices={question.choices}
                  correctIndex={resolveQuizCorrectIndex(question)}
                  revealAnswer
                />
                <div className="rounded-2xl border border-emerald-500/25 bg-emerald-500/5 px-4 py-3">
                  <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700 dark:text-emerald-300">
                    Correct Answer
                  </p>
                  <p className="mt-1 text-sm text-foreground">
                    {String.fromCharCode(65 + resolveQuizCorrectIndex(question))}. {question.choices[resolveQuizCorrectIndex(question)]}
                  </p>
                </div>
                <div className="space-y-1">
                  <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">
                    Explanation
                  </p>
                  <p className="text-sm leading-7 text-foreground/80">
                    {question.explanation}
                  </p>
                </div>
              </Card>
            ))}
          </div>
        </>
      )}

      <AppModal
        isOpen={showRegenerateConfirm}
        title="Regenerate quiz?"
        description="This will create a new set of questions and costs 1 credit."
        onClose={() => {
          if (!regenerating) {
            setShowRegenerateConfirm(false);
          }
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setShowRegenerateConfirm(false)} disabled={regenerating}>
              Cancel
            </Button>
            <Button type="button" onClick={() => void handleRegenerate()} disabled={regenerating}>
              {regenerating ? "Regenerating..." : "Regenerate Quiz"}
            </Button>
          </div>
        )}
      />

      {toast ? (
        <div role="status" aria-live="polite" className="fixed bottom-4 right-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
          {toast}
        </div>
      ) : null}
    </main>
  );
}
