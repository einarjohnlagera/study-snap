"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Copy, FileText, Link2, MoreHorizontal, RotateCcw } from "lucide-react";
import { useRouter } from "next/navigation";
import { PaywallModal, type PaywallModalVariant } from "@/components/billing/paywall-modal";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { LoadingSpinner } from "@/components/ui/loading-spinner";
import { QuizExportModal, type QuizDocxVersionCount, type QuizExportModalMode } from "@/components/ui/quiz-export-modal";
import { Skeleton } from "@/components/ui/skeleton";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { getAuthUser } from "@/lib/auth";
import {
  createQuizShareLink,
  exportGeneratedQuizDocx,
  getGeneratedQuiz,
  generateGeneratedQuiz,
  getMe,
  getNote,
  getQuizShareLinkByQuizId,
  isExportLimitReachedError,
  isMultipleExamVersionsNotAllowedError,
  isQuizShareLinkLimitExceededError,
  toggleQuizShareLink,
  trackAnalyticsEvent,
  type GeneratedQuizResponse,
  type QuizDocxHeaderOverride,
  type NoteResponse,
  type QuizDocxExportMode,
  type QuizShareLinkResponse,
} from "@/lib/api";
import { isQuizLimitReachedMessage, resolveRemainingUsageCredits } from "@/lib/plans";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { resolveQuizCorrectIndex } from "@/lib/quiz";
import { GuidanceTip } from "@/components/ui/guidance-tip";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

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

function normalizePlan(plan: string | null | undefined): AppPlanType {
  return plan === "PLUS" || plan === "PRO" ? plan : "FREE";
}

export function GeneratedQuizPreviewPageClient({ noteId }: Readonly<GeneratedQuizPreviewPageClientProps>) {
  const router = useRouter();
  const startRouteProgress = useRouteProgress();
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [generatedQuiz, setGeneratedQuiz] = useState<GeneratedQuizResponse | null>(null);
  const [profileSchoolName, setProfileSchoolName] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [shareLink, setShareLink] = useState<QuizShareLinkResponse | null>(null);
  const [creatingShareLink, setCreatingShareLink] = useState(false);
  const [togglingShareLink, setTogglingShareLink] = useState(false);
  const [shareLimitReached, setShareLimitReached] = useState(false);
  const [showRegenerateConfirm, setShowRegenerateConfirm] = useState(false);
  const [showExportModal, setShowExportModal] = useState(false);
  const [activePaywallModal, setActivePaywallModal] = useState<PaywallModalVariant | null>(null);
  const [regenerateMenuOpen, setRegenerateMenuOpen] = useState(false);
  const regenerateMenuRef = useRef<HTMLDivElement | null>(null);
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
      const [loadedNote, loadedQuiz, me] = await Promise.all([
        getNote(noteId),
        getGeneratedQuiz(noteId),
        getMe().catch(() => null),
      ]);
      setNote(loadedNote);
      setGeneratedQuiz(loadedQuiz);
      setProfileSchoolName(me?.schoolName ?? null);
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

  useEffect(() => {
    if (!regenerateMenuOpen) {
      return undefined;
    }

    const handlePointerDown = (event: MouseEvent) => {
      if (!regenerateMenuRef.current || regenerateMenuRef.current.contains(event.target as Node)) {
        return;
      }
      setRegenerateMenuOpen(false);
    };
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setRegenerateMenuOpen(false);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [regenerateMenuOpen]);

  const noteTitle = note?.title?.trim() || "Untitled note";
  const authUser = getAuthUser();
  const canExportDocx = authUser?.role === "ADMIN" || authUser?.profileType === "TEACHER";

  useEffect(() => {
    if (!generatedQuiz?.id || !canExportDocx) {
      return;
    }
    void getQuizShareLinkByQuizId(generatedQuiz.id).then((link) => {
      if (link) {
        setShareLink(link);
      }
    });
  }, [canExportDocx, generatedQuiz?.id]);

  const generatedQuestionCount = generatedQuiz?.questions.length ?? 0;
  const generatedQuestionCountLabel = `${generatedQuestionCount} question${generatedQuestionCount === 1 ? "" : "s"}`;
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
  const quizGenerationPaywallVariant: PaywallModalVariant = "quiz-generation-limit";
  const currentPlan = normalizePlan(authUser?.planType ?? usageSummary?.plan);
  const shareUpgradeCtas = useMemo(
    () => getUpgradeCtas(currentPlan, { profileType: "TEACHER" }),
    [currentPlan],
  );
  const primaryShareUpgradeCta = shareUpgradeCtas.primary;
  const secondaryShareUpgradeCta = shareUpgradeCtas.secondary;

  const handleExport = useCallback(async (
    mode: QuizDocxExportMode | QuizExportModalMode,
    headerOverride?: QuizDocxHeaderOverride,
    versionCount?: QuizDocxVersionCount,
  ) => {
    if (!generatedQuiz?.id || exporting) {
      return;
    }
    setExporting(true);
    setShowExportModal(false);
    setToast("Exporting DOCX...");
    try {
      await exportGeneratedQuizDocx(generatedQuiz.id, mode, headerOverride, versionCount);
      setToast("DOCX ready");
    } catch (err) {
      if (isExportLimitReachedError(err)) {
        setError(null);
        setToast(null);
        setActivePaywallModal("export-limit");
        return;
      }
      if (isMultipleExamVersionsNotAllowedError(err)) {
        setError(null);
        setToast(null);
        setActivePaywallModal("teacher-exam-versions");
        return;
      }
      const message = err instanceof Error ? err.message : "Could not export quiz.";
      setError(message);
      setToast(null);
    } finally {
      setExporting(false);
    }
  }, [exporting, generatedQuiz]);

  const handleRegenerate = useCallback(async () => {
    if (regenerating) {
      return;
    }
    if (hasReachedChallengeQuizLimit) {
      setShowRegenerateConfirm(false);
      setActivePaywallModal(quizGenerationPaywallVariant);
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
        setActivePaywallModal(quizGenerationPaywallVariant);
        return;
      }
      setError(message);
    } finally {
      setRegenerating(false);
    }
  }, [hasReachedChallengeQuizLimit, noteId, quizGenerationPaywallVariant, refreshUsageSummary, regenerating, usageSummary?.plan]);

  const handleCreateShareLink = useCallback(async () => {
    if (!generatedQuiz?.id || creatingShareLink) {
      return;
    }
    setCreatingShareLink(true);
    setShareLimitReached(false);
    setError(null);
    try {
      const created = await createQuizShareLink(generatedQuiz.id);
      setShareLink(created);
      void navigator.clipboard.writeText(created.shareUrl).catch(() => null);
      setToast("Link created and copied.");
      void refreshUsageSummary();
      void trackAnalyticsEvent({
        eventType: "QUIZ_SHARE_LINK_CREATED",
        entityId: generatedQuiz.id,
        metadata: { token: created.token },
      });
    } catch (err) {
      if (isQuizShareLinkLimitExceededError(err)) {
        setShareLimitReached(true);
        setToast(null);
        void refreshUsageSummary();
        return;
      }
      const message = err instanceof Error ? err.message : "Could not create share link.";
      setError(message);
    } finally {
      setCreatingShareLink(false);
    }
  }, [creatingShareLink, generatedQuiz?.id, refreshUsageSummary]);

  const handleToggleShareLink = useCallback(async () => {
    if (!shareLink || togglingShareLink) {
      return;
    }
    setTogglingShareLink(true);
    setError(null);
    try {
      const updated = await toggleQuizShareLink(shareLink.token);
      setShareLink(updated);
      setToast(updated.isActive ? "Sharing turned on." : "Sharing turned off.");
      void trackAnalyticsEvent({
        eventType: "QUIZ_SHARE_LINK_TOGGLED",
        entityId: generatedQuiz?.id ?? null,
        metadata: { token: updated.token, isActive: updated.isActive },
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update share link.";
      setError(message);
    } finally {
      setTogglingShareLink(false);
    }
  }, [generatedQuiz?.id, shareLink, togglingShareLink]);

  const handleCopyShareUrl = useCallback(async () => {
    if (!shareLink?.shareUrl) {
      return;
    }
    try {
      await navigator.clipboard.writeText(shareLink.shareUrl);
      setToast("Link copied.");
    } catch {
      setToast("Copy failed.");
    }
  }, [shareLink?.shareUrl]);

  const handleUpgradeClick = useCallback((targetPlan: string) => {
    startRouteProgress();
    router.push(`/settings?section=plans&plan=${targetPlan}`);
  }, [router, startRouteProgress]);

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
                <h1 className="text-2xl font-semibold sm:text-3xl">{noteTitle}</h1>
                <p className="text-sm text-foreground/75">
                  Review the generated quiz with answers and explanations before exporting it for class use.
                </p>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full border border-blue-500/30 bg-blue-500/5 px-3 py-1 text-xs font-medium text-blue-700 dark:text-blue-300">
                    Generated Quiz - Ready for export
                  </span>
                  <span className="text-xs text-foreground/65">{generatedQuestionCountLabel}</span>
                </div>
                {generatedAtLabel ? (
                  <p className="text-xs text-foreground/60">Generated {generatedAtLabel}</p>
                ) : null}
              </div>
              <div className="flex flex-col items-stretch gap-1.5 sm:flex-row sm:items-center">
                {canExportDocx ? (
                  <Button
                    type="button"
                    className="gap-2"
                    onClick={() => setShowExportModal(true)}
                    loading={exporting}
                    loadingText="Exporting..."
                  >
                    <FileText className="h-4 w-4" aria-hidden="true" />
                    <span>Export</span>
                  </Button>
                ) : null}
                <div className="relative" ref={regenerateMenuRef}>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-10 w-10 p-0"
                    onClick={() => setRegenerateMenuOpen((previous) => !previous)}
                    disabled={regenerating}
                    aria-label="More quiz actions"
                    aria-haspopup="menu"
                    aria-expanded={regenerateMenuOpen}
                  >
                    {regenerating ? <LoadingSpinner /> : <MoreHorizontal className="h-4 w-4" aria-hidden="true" />}
                    <span className="sr-only">More</span>
                  </Button>
                  {regenerateMenuOpen ? (
                    <div
                      role="menu"
                      aria-label="Quiz actions"
                      className="fixed inset-x-4 bottom-4 z-50 overflow-hidden rounded-2xl border border-border bg-background shadow-xl sm:absolute sm:bottom-auto sm:inset-x-auto sm:right-0 sm:top-full sm:mt-1 sm:min-w-48 sm:rounded-md sm:shadow-md"
                    >
                      <div className="p-2 sm:p-1">
                        <button
                          type="button"
                          role="menuitem"
                          className="motion-lift flex w-full items-center gap-2 rounded-lg px-3 py-3 text-left text-sm font-medium transition-colors hover:bg-highlight active:bg-highlight-strong disabled:cursor-not-allowed disabled:opacity-50 sm:rounded sm:py-2"
                          onClick={() => {
                            setRegenerateMenuOpen(false);
                            setShowRegenerateConfirm(true);
                          }}
                          disabled={regenerating}
                        >
                          {regenerating ? <LoadingSpinner /> : <RotateCcw className="h-4 w-4" aria-hidden="true" />}
                          <span>{regenerating ? "Regenerating..." : "Regenerate quiz"}</span>
                        </button>
                      </div>
                    </div>
                  ) : null}
                </div>
              </div>
            </div>
          </Card>

          {canExportDocx ? (
            <GuidanceTip
              tipId="teacher-docx-export"
              message="Download as DOCX and open in Word or Google Docs — format it your way before distributing to students."
            />
          ) : null}

          {canExportDocx ? (
            <Card className="space-y-4 p-4 sm:p-6">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                    Share Quiz
                  </p>
                  <h2 className="text-lg font-semibold text-foreground">Share with Students</h2>
                  <p className="max-w-2xl text-sm leading-6 text-foreground/75">
                    Create a public quiz link students can open without an account. They answer in-browser and see their score before signing up.
                  </p>
                </div>
                {!shareLink ? (
                  <Button
                    type="button"
                    className="gap-2 sm:self-start"
                    onClick={() => void handleCreateShareLink()}
                    loading={creatingShareLink}
                    loadingText="Creating..."
                    disabled={!generatedQuiz.id}
                  >
                    <Link2 className="h-4 w-4" aria-hidden="true" />
                    <span>Share with Students</span>
                  </Button>
                ) : (
                  <Button
                    type="button"
                    variant={shareLink.isActive ? "outline" : "default"}
                    className="sm:self-start"
                    onClick={() => void handleToggleShareLink()}
                    loading={togglingShareLink}
                    loadingText="Updating..."
                  >
                    {shareLink.isActive ? "Turn Sharing Off" : "Turn Sharing On"}
                  </Button>
                )}
              </div>

              {shareLimitReached ? (
                <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4">
                  <p className="text-sm font-semibold text-foreground">Monthly share link limit reached</p>
                  <p className="mt-1 text-sm leading-6 text-foreground/75">
                    Shareable quiz links help distribute quizzes to a whole class. Upgrade for more monthly links.
                  </p>
                  <div className="mt-3 flex flex-col gap-2 sm:flex-row">
                    {primaryShareUpgradeCta ? (
                      <Button type="button" size="sm" onClick={() => handleUpgradeClick(primaryShareUpgradeCta.targetPlan)}>
                        {primaryShareUpgradeCta.label}
                      </Button>
                    ) : null}
                    {secondaryShareUpgradeCta ? (
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleUpgradeClick(secondaryShareUpgradeCta.targetPlan)}
                      >
                        {secondaryShareUpgradeCta.label}
                      </Button>
                    ) : null}
                  </div>
                </div>
              ) : null}

              {shareLink ? (
                <div className="space-y-2">
                  <div className="flex flex-col gap-2 rounded-xl border border-border bg-highlight/40 p-3 sm:flex-row sm:items-center">
                    <div className="min-w-0 flex-1">
                      <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">
                        {shareLink.isActive ? "Sharing on" : "Sharing off"}
                      </p>
                      <p className={`truncate text-sm font-medium ${shareLink.isActive ? "text-foreground" : "text-foreground/45"}`}>
                        {shareLink.shareUrl}
                      </p>
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="gap-2"
                      onClick={() => void handleCopyShareUrl()}
                    >
                      <Copy className="h-4 w-4" aria-hidden="true" />
                      <span>Copy</span>
                    </Button>
                  </div>
                  {!shareLink.isActive ? (
                    <p className="text-xs text-foreground/60">
                      Students who open this link will see that the quiz is no longer active.
                    </p>
                  ) : null}
                </div>
              ) : null}
            </Card>
          ) : null}

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
        description="This will create a new set of questions and counts toward your monthly quiz generation limit."
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

      <QuizExportModal
        isOpen={showExportModal}
        title="Export Quiz"
        description="Choose an export format for this quiz."
        exporting={exporting}
        showTeacherExportDetails={authUser?.profileType === "TEACHER"}
        schoolName={profileSchoolName}
        teacherPlan={authUser?.planType}
        onClose={() => setShowExportModal(false)}
        onSelect={(mode, headerOverride, versionCount) => void handleExport(mode, headerOverride, versionCount)}
      />

      <PaywallModal
        isOpen={activePaywallModal !== null}
        variant={activePaywallModal ?? quizGenerationPaywallVariant}
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
