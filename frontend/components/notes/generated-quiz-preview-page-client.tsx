"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { RotateCcw } from "lucide-react";
import { useRouter } from "next/navigation";
import { PaywallModal, type PaywallModalVariant } from "@/components/billing/paywall-modal";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { ExportDropdownMenu } from "@/components/ui/export-dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { getGeneratedQuiz, generateGeneratedQuiz, getNote, type GeneratedQuizResponse, type NoteResponse } from "@/lib/api";
import { exportGeneratedQuizDocument, type GeneratedQuizExportType } from "@/lib/generated-quiz-export";
import { isQuizLimitReachedMessage, resolveRemainingUsageCredits } from "@/lib/plans";
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
  const startRouteProgress = useRouteProgress();
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [generatedQuiz, setGeneratedQuiz] = useState<GeneratedQuizResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [showRegenerateConfirm, setShowRegenerateConfirm] = useState(false);
  const [activePaywallModal, setActivePaywallModal] = useState<PaywallModalVariant | null>(null);
  const { usageSummary, refreshUsageSummary } = useBillingUsageSummary();

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
    const timeoutId = globalThis.setTimeout(() => setToast(null), 2200);
    return () => globalThis.clearTimeout(timeoutId);
  }, [toast]);

  const noteTitle = note?.title?.trim() || "Untitled note";
  const noteSubject = note?.subject ?? null;
  const generatedAtLabel = useMemo(() => {
    if (!generatedQuiz?.generatedAt) {
      return null;
    }
    return GENERATED_DATE_FORMATTER.format(new Date(generatedQuiz.generatedAt));
  }, [generatedQuiz?.generatedAt]);
  const challengeQuizzesRemaining = usageSummary
    ? resolveRemainingUsageCredits(
      usageSummary.usage.challengeQuizzesUsed,
      usageSummary.limits.challengeQuizzesPerMonth,
      usageSummary.remaining?.challengeQuizzesRemaining,
    )
    : null;
  const hasReachedChallengeQuizLimit = usageSummary?.plan === "FREE"
    && challengeQuizzesRemaining !== null
    && challengeQuizzesRemaining <= 0;

  const handleExport = useCallback(async (exportType: GeneratedQuizExportType) => {
    if (!generatedQuiz || exporting) {
      return;
    }
    setExporting(true);
    setToast("Exporting PDF...");
    try {
      await exportGeneratedQuizDocument({
        exportType,
        noteTitle,
        noteSubject,
        quiz: generatedQuiz.questions,
      });
      setToast("PDF ready");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not export PDF.";
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
    if (hasReachedChallengeQuizLimit) {
      setShowRegenerateConfirm(false);
      setActivePaywallModal("challenge-quiz-limit");
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
      if (usageSummary?.plan === "FREE" && isQuizLimitReachedMessage(message)) {
        void refreshUsageSummary();
        setShowRegenerateConfirm(false);
        setActivePaywallModal("challenge-quiz-limit");
        return;
      }
      setError(message);
    } finally {
      setRegenerating(false);
    }
  }, [hasReachedChallengeQuizLimit, noteId, refreshUsageSummary, regenerating, usageSummary?.plan]);

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <BackLink href={`/notes/${noteId}`} label="Back to Note" />

      {loading ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <Skeleton className="h-8 w-40" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
        </Card>
      ) : error ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <h1 className="text-xl font-semibold sm:text-2xl">Quiz Preview</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" onClick={() => void loadPage()}>
              Try Again
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                startRouteProgress();
                router.push(`/notes/${noteId}`);
              }}
            >
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
                <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Quiz Preview</p>
                <h1 className="text-2xl font-semibold sm:text-3xl">Quiz Preview</h1>
                <p className="text-sm text-foreground/75">
                  Review the generated quiz with answers and explanations before exporting it for class use.
                </p>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full border border-blue-500/30 bg-blue-500/5 px-3 py-1 text-xs font-medium text-blue-700 dark:text-blue-300">
                    Generated Quiz - Ready for export
                  </span>
                  <span className="text-sm text-foreground/70">{noteTitle}</span>
                </div>
                {generatedAtLabel ? (
                  <p className="text-xs text-foreground/60">Generated {generatedAtLabel}</p>
                ) : null}
              </div>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <ExportDropdownMenu
                  exporting={exporting}
                  buttonLabel="Export"
                  menuTitle="Export"
                  menuDescription="Choose what to export"
                  groupLabel="Quiz Materials"
                  items={[
                    {
                      key: "pdf",
                      label: "Export as PDF",
                      description: "Questions and choices for classroom use",
                      onSelect: () => void handleExport("pdf"),
                    },
                    {
                      key: "with-answers",
                      label: "Export with answers",
                      description: "Include answers and explanations",
                      onSelect: () => void handleExport("with-answers"),
                    },
                  ]}
                />
                <Button
                  type="button"
                  variant="outline"
                  className="gap-2"
                  onClick={() => setShowRegenerateConfirm(true)}
                  loading={regenerating}
                  loadingText="Regenerating..."
                >
                  <RotateCcw className="h-4 w-4" aria-hidden="true" />
                  <span>Regenerate</span>
                </Button>
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
        description="This will create a new set of questions and uses 1 quiz from your monthly usage."
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
            <Button
              type="button"
              onClick={() => void handleRegenerate()}
              loading={regenerating}
              loadingText="Regenerating..."
            >
              Regenerate Quiz
            </Button>
          </div>
        )}
      />

      <PaywallModal
        isOpen={activePaywallModal !== null}
        variant={activePaywallModal ?? "challenge-quiz-limit"}
        source="generated_quiz_preview"
        onClose={() => setActivePaywallModal(null)}
      />

      {toast ? (
        <div role="status" aria-live="polite" className="fixed bottom-4 right-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
          {toast}
        </div>
      ) : null}
    </main>
  );
}
