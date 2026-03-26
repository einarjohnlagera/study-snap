"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { PaywallModal } from "@/components/billing/paywall-modal";
import {
  completeProductOnboarding,
  createNote,
  createStudyPackFromNote,
  extractNoteTextFromFile,
  getNote,
  isEmailNotVerifiedError,
  isOcrLimitReachedError,
  trackAnalyticsEvent,
  type NoteResponse,
  updateNote,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import {
  hasReachedUsageLimit,
  isStudyPackLimitReachedMessage,
  shouldShowNearStudyPackLimitBanner,
} from "@/lib/plans";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { ToastMessage } from "@/components/ui/toast-message";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { AiSuggestionModal } from "@/components/notes/ai-suggestion-modal";
import { NoteEditorForm, type NoteEditorDraft } from "@/components/notes/note-editor-form";
import {
  clearFirstStudyOnboardingStep,
  getFirstStudyOnboardingStep,
  hasPendingFirstStudyOnboarding,
  setFirstStudyOnboardingStep,
  type FirstStudyOnboardingStep,
} from "@/lib/first-study-onboarding";

type NoteEditorPageClientProps = {
  noteId?: string;
};

type PendingSuggestion = {
  noteId: string;
  title: string;
  subject: string | null;
  tags: string[];
};

function normalizeOptional(value: string): string | null {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function toDraft(note: NoteResponse): NoteEditorDraft {
  return {
    title: note.title ?? "",
    subject: note.subject ?? "",
    content: note.content,
    tags: note.tags ?? [],
  };
}

function hasExistingMetadata(note: NoteResponse): boolean {
  return Boolean(
    (note.title && note.title.trim().length > 0)
    || (note.subject && note.subject.trim().length > 0)
    || (note.tags && note.tags.length > 0),
  );
}

export function NoteEditorPageClient({ noteId }: Readonly<NoteEditorPageClientProps>) {
  const router = useRouter();
  const pathname = usePathname();
  const isDetailPage = Boolean(noteId);
  const [studyPackStatus, setStudyPackStatus] = useState<NoteResponse["studyPackStatus"] | null>(null);
  const [draft, setDraft] = useState<NoteEditorDraft>({
    title: "",
    subject: "",
    content: "",
    tags: [],
  });
  const [currentNoteId, setCurrentNoteId] = useState<string | null>(noteId ?? null);
  const [loadingNote, setLoadingNote] = useState(isDetailPage);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [saveStateLabel, setSaveStateLabel] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastTone, setToastTone] = useState<"success" | "error" | "info">("info");
  const [pendingSuggestion, setPendingSuggestion] = useState<PendingSuggestion | null>(null);
  const [applyingSuggestion, setApplyingSuggestion] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importFileInputKey, setImportFileInputKey] = useState(0);
  const [importFlowState, setImportFlowState] = useState<"idle" | "uploading" | "extracting" | "success" | "failure">("idle");
  const [importStatusMessage, setImportStatusMessage] = useState<string | null>(null);
  const [importReviewMessage, setImportReviewMessage] = useState<string | null>(null);
  const [isEmailVerified, setIsEmailVerified] = useState(Boolean(getAuthUser()?.emailVerifiedAt));
  const [firstStudyStep, setFirstStudyStep] = useState<FirstStudyOnboardingStep | null>(null);
  const [showLimitReachedModal, setShowLimitReachedModal] = useState(false);
  const [showOcrLimitModal, setShowOcrLimitModal] = useState(false);
  const { usageSummary } = useBillingUsageSummary();
  const currentPlan = usageSummary?.plan ?? (getAuthUser()?.planType ?? "FREE");

  useEffect(() => {
    const syncAuthState = () => {
      const authUser = getAuthUser();
      setIsEmailVerified(Boolean(authUser?.emailVerifiedAt));
      if (authUser && hasPendingFirstStudyOnboarding(authUser)) {
        setFirstStudyStep(getFirstStudyOnboardingStep(authUser.id));
        return;
      }
      setFirstStudyStep(null);
    };
    syncAuthState();
    globalThis.addEventListener("studysnap-auth-change", syncAuthState);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuthState);
    };
  }, []);

  useEffect(() => {
    if (!toastMessage) {
      return;
    }
    const timeout = globalThis.setTimeout(() => {
      setToastMessage(null);
    }, 3200);
    return () => globalThis.clearTimeout(timeout);
  }, [toastMessage]);

  const showToast = useCallback((message: string, tone: "success" | "error" | "info" = "info") => {
    setToastTone(tone);
    setToastMessage(message);
  }, []);

  const appendExtractedTextToContent = useCallback((extractedText: string) => {
    const normalized = extractedText.trim();
    if (normalized.length === 0) {
      return false;
    }
    setDraft((previous) => {
      const currentContent = previous.content.trim();
      const nextContent = currentContent.length === 0
        ? normalized
        : `${previous.content.trimEnd()}\n\n${normalized}`;
      return {
        ...previous,
        content: nextContent,
      };
    });
    return true;
  }, []);

  const contentEmpty = useMemo(() => draft.content.trim().length === 0, [draft.content]);
  const contentLocked = isDetailPage && studyPackStatus === "STUDY_PACK_READY";
  const openLockedFeaturePaywall = useCallback((variant: "study-pack-limit" | "ocr-limit", source: string) => {
    void trackAnalyticsEvent({
      eventType: "FEATURE_LOCKED_CLICKED",
      metadata: {
        feature: variant === "study-pack-limit" ? "study_pack_limit" : "ocr_limit",
        source,
        path: pathname,
        noteId: currentNoteId,
      },
    });
    if (variant === "study-pack-limit") {
      setShowLimitReachedModal(true);
      return;
    }
    setShowOcrLimitModal(true);
  }, [currentNoteId, pathname]);

  const handleImportFileChange = useCallback(async (file: File | null) => {
    const resetImportInput = () => {
      setImportFileInputKey((previous) => previous + 1);
    };

    if (contentLocked) {
      setImportFile(null);
      resetImportInput();
      setImportFlowState("failure");
      setImportStatusMessage("Note content is locked after generating a Study Pack. Make a copy to change the note itself.");
      showToast("Note content is locked after generating a Study Pack. Make a copy to change the note itself.", "info");
      return;
    }

    if (!file) {
      setImportFile(null);
      setImportFlowState("idle");
      setImportStatusMessage(null);
      setImportReviewMessage(null);
      return;
    }

    setImportFile(file);
    setImportReviewMessage(null);
    setImportFlowState("uploading");
    setImportStatusMessage("Uploading file...");

    const extractingTimer = globalThis.setTimeout(() => {
      setImportFlowState("extracting");
      setImportStatusMessage("Extracting text into your notes...");
    }, 350);

    try {
      const extracted = await extractNoteTextFromFile(file);
      globalThis.clearTimeout(extractingTimer);

      const didAppend = appendExtractedTextToContent(extracted.extractedText);
      if (!didAppend) {
        setImportFlowState("failure");
        setImportStatusMessage("This file is empty or has no readable text.");
        resetImportInput();
        showToast("This file is empty or has no readable text.", "info");
        return;
      }

      setImportFlowState("success");
      setImportStatusMessage("File content added to Content.");
      if (extracted.meta.lowConfidence) {
        resetImportInput();
        setImportReviewMessage(
          "OCR may be inaccurate. Please review and edit the extracted text before saving or generating a Study Pack.",
        );
        showToast("Extracted text added to Content. Review it before continuing.", "info");
        return;
      }

      resetImportInput();
      showToast("Extracted text added to Content.", "success");
    } catch (error) {
      globalThis.clearTimeout(extractingTimer);
      const message = error instanceof Error ? error.message : "Could not import this file.";
      if (isEmailNotVerifiedError(error)) {
        const verificationMessage = "Verify your email before using OCR upload.";
        setImportFlowState("failure");
        setImportStatusMessage(verificationMessage);
        setImportReviewMessage(null);
        resetImportInput();
        showToast(verificationMessage, "info");
        return;
      }
      if (isOcrLimitReachedError(error)) {
        setImportFlowState("failure");
        setImportStatusMessage(null);
        setImportReviewMessage(null);
        resetImportInput();
        if (currentPlan === "FREE") {
          openLockedFeaturePaywall("ocr-limit", "note_editor_ocr_limit");
        } else {
          setShowOcrLimitModal(true);
        }
        return;
      }
      setImportFlowState("failure");
      setImportStatusMessage(message);
      setImportReviewMessage(null);
      resetImportInput();
      showToast(message, "error");
    }
  }, [appendExtractedTextToContent, contentLocked, currentPlan, openLockedFeaturePaywall, showToast]);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    if (!noteId) {
      setLoadingNote(false);
      return;
    }

    let active = true;
    setLoadingNote(true);
    setLoadError(null);

    void getNote(noteId)
      .then((note) => {
        if (!active) {
          return;
        }
        setDraft(toDraft(note));
        setCurrentNoteId(note.id);
        setStudyPackStatus(note.studyPackStatus ?? "DRAFT");
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        const message = error instanceof Error ? error.message : "Could not load note.";
        setLoadError(message);
      })
      .finally(() => {
        if (active) {
          setLoadingNote(false);
        }
      });

    return () => {
      active = false;
    };
  }, [noteId, router]);
  const studyPacksUsed = usageSummary?.usage.studyPacksUsed ?? 0;
  const studyPacksLimit = usageSummary?.limits.studyPacksPerMonth ?? 0;
  const hasReachedStudyPackLimit = usageSummary?.plan === "FREE"
    && hasReachedUsageLimit(studyPacksUsed, studyPacksLimit);
  const shouldShowNearLimitBanner = usageSummary
    ? shouldShowNearStudyPackLimitBanner(usageSummary.plan, studyPacksUsed, studyPacksLimit)
    : false;

  const buildRequest = useCallback(() => ({
    title: normalizeOptional(draft.title),
    subject: normalizeOptional(draft.subject),
    tags: draft.tags,
    content: draft.content,
  }), [draft.content, draft.subject, draft.tags, draft.title]);

  const upsertNote = useCallback(async (): Promise<NoteResponse | null> => {
    if (contentEmpty) {
      showToast("Please add note content first.", "info");
      return null;
    }

    const payload = buildRequest();
    const saved = currentNoteId
      ? await updateNote(currentNoteId, payload)
      : await createNote(payload);

    setCurrentNoteId(saved.id);
    setDraft(toDraft(saved));
    setStudyPackStatus(saved.studyPackStatus ?? "DRAFT");
    return saved;
  }, [buildRequest, contentEmpty, currentNoteId, showToast]);

  const handleSave = useCallback(async () => {
    if (isSaving || isGenerating || contentEmpty) {
      return;
    }

    setIsSaving(true);
    setSaveStateLabel("Saving...");
    try {
      const saved = await upsertNote();
      if (!saved) {
        return;
      }
      const authUser = getAuthUser();
      if (authUser && firstStudyStep === "create-note") {
        setFirstStudyOnboardingStep(authUser.id, "saved-note");
        setFirstStudyStep("saved-note");
      }
      setSaveStateLabel("Saved");
      if (isDetailPage) {
        router.push(`/notes/${saved.id}?saved=1`);
        return;
      }
      router.push(`/notes/${saved.id}`);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not save note.";
      showToast(message, "error");
      setSaveStateLabel(null);
    } finally {
      setIsSaving(false);
    }
  }, [contentEmpty, firstStudyStep, isDetailPage, isGenerating, isSaving, router, showToast, upsertNote]);

  const finalizeGenerationRedirect = useCallback((noteIdToOpen: string) => {
    router.push(`/notes/${noteIdToOpen}?from=notes&created=1`);
  }, [router]);

  const handleGenerate = useCallback(async () => {
    if (isGenerating || isSaving || contentEmpty) {
      return;
    }
    if (!isEmailVerified) {
      showToast("Email verification is required before generating Study Packs.", "info");
      return;
    }
    if (hasReachedStudyPackLimit) {
      openLockedFeaturePaywall("study-pack-limit", "note_editor_generate");
      return;
    }

    setIsGenerating(true);
    try {
      const saved = await upsertNote();
      if (!saved) {
        return;
      }

      const generated = await createStudyPackFromNote(saved.id);
      const hasUserMetadata = hasExistingMetadata(saved);

      if (!hasUserMetadata) {
        const autoFillPayload = {
          title: generated.title,
          subject: generated.subject ?? null,
          tags: generated.tags ?? [],
          content: saved.content,
        };
        const updated = await updateNote(saved.id, autoFillPayload);
        setDraft(toDraft(updated));
        setCurrentNoteId(updated.id);
        finalizeGenerationRedirect(saved.id);
        return;
      }

      setPendingSuggestion({
        noteId: saved.id,
        title: generated.title,
        subject: generated.subject ?? null,
        tags: generated.tags ?? [],
      });
    } catch (error) {
      if (isEmailNotVerifiedError(error)) {
        showToast("Email verification is required before generating Study Packs.", "info");
      } else {
        const message = error instanceof Error ? error.message : "Could not generate Study Pack.";
        if (isStudyPackLimitReachedMessage(message)) {
          openLockedFeaturePaywall("study-pack-limit", "note_editor_generate_error");
        } else {
          showToast(message, "error");
        }
      }
    } finally {
      setIsGenerating(false);
    }
  }, [
    contentEmpty,
    finalizeGenerationRedirect,
    hasReachedStudyPackLimit,
    isEmailVerified,
    isGenerating,
    isSaving,
    openLockedFeaturePaywall,
    showToast,
    upsertNote,
  ]);

  const applySuggestions = useCallback(async () => {
    if (!pendingSuggestion || applyingSuggestion) {
      return;
    }

    setApplyingSuggestion(true);
    try {
      const updated = await updateNote(pendingSuggestion.noteId, {
        title: pendingSuggestion.title,
        subject: pendingSuggestion.subject,
        tags: pendingSuggestion.tags,
        content: draft.content,
      });
      setDraft(toDraft(updated));
      setCurrentNoteId(updated.id);
      setPendingSuggestion(null);
      finalizeGenerationRedirect(pendingSuggestion.noteId);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not apply suggestions.";
      showToast(message, "error");
    } finally {
      setApplyingSuggestion(false);
    }
  }, [applyingSuggestion, draft.content, finalizeGenerationRedirect, pendingSuggestion, showToast]);

  const keepMineAndContinue = useCallback(() => {
    if (!pendingSuggestion) {
      return;
    }
    const noteIdToOpen = pendingSuggestion.noteId;
    setPendingSuggestion(null);
    finalizeGenerationRedirect(noteIdToOpen);
  }, [finalizeGenerationRedirect, pendingSuggestion]);

  const pageTitle = isDetailPage ? "Note" : "New Note";
  const studyPackMessage = isDetailPage
    ? "Generate a Study Pack from this note when you are ready."
    : "Save your note for later, or generate immediately when the content is ready.";
  const showFirstStudyHint = !isDetailPage && firstStudyStep === "create-note";

  const dismissFirstStudyHint = useCallback(async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      return;
    }
    try {
      const me = await completeProductOnboarding(true);
      setAuthUser({
        ...authUser,
        displayName: me.displayName,
        profileType: me.profileType,
        emailVerifiedAt: me.emailVerifiedAt,
        onboardingCompletedAt: me.onboardingCompletedAt,
        productOnboardingCompletedAt: me.productOnboardingCompletedAt,
      });
    } catch {
      // Best-effort onboarding persistence should not block editing.
    } finally {
      clearFirstStudyOnboardingStep(authUser.id);
      setFirstStudyStep(null);
    }
  }, []);

  if (loadingNote) {
    return (
      <main className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6">
        <Card className="space-y-3 p-4 sm:p-6">
          <div className="h-6 w-40 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
          <div className="h-52 w-full animate-pulse rounded bg-foreground/10" />
        </Card>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6">
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-lg font-semibold sm:text-xl">Could not load note</h1>
          <p className="text-sm text-foreground/75">{loadError}</p>
          <Button type="button" variant="outline" onClick={() => router.refresh()}>
            Retry
          </Button>
        </Card>
      </main>
    );
  }

  return (
    <>
      {shouldShowNearLimitBanner ? (
        <div className="mx-auto w-full max-w-4xl px-4 pt-6 sm:px-6 sm:pt-8">
          <NearLimitBanner />
        </div>
      ) : null}

      <NoteEditorForm
        pageTitle={pageTitle}
        note={draft}
        onTitleChange={(value) => setDraft((previous) => ({ ...previous, title: value }))}
        onSubjectChange={(value) => setDraft((previous) => ({ ...previous, subject: value }))}
        onContentChange={(value) => setDraft((previous) => ({ ...previous, content: value }))}
        onTagsChange={
          isDetailPage
            ? (nextTags) => setDraft((previous) => ({ ...previous, tags: nextTags }))
            : undefined
        }
        onSave={() => {
          void handleSave();
        }}
        onGenerate={() => {
          void handleGenerate();
        }}
        isSaving={isSaving}
        isGenerating={isGenerating}
        saveStateLabel={saveStateLabel}
        helperText="Create or import your notes first, then generate a Study Pack when you are ready."
        showTagsSection={isDetailPage}
        studyPackMessage={studyPackMessage}
        importFile={importFile}
        importFileInputKey={importFileInputKey}
        importFlowState={importFlowState}
        importStatusMessage={importStatusMessage}
        importReviewMessage={importReviewMessage}
        onImportFileChange={(file) => {
          void handleImportFileChange(file);
        }}
        disableContentEditing={contentLocked}
        contentLockHint="Note content is locked after generating a Study Pack. Make a copy to change the note itself."
        disableGenerateAction={!isEmailVerified}
        firstStudyHintVisible={showFirstStudyHint}
        onDismissFirstStudyHint={showFirstStudyHint ? () => {
          void dismissFirstStudyHint();
        } : undefined}
      />

      <AiSuggestionModal
        open={Boolean(pendingSuggestion)}
        title={pendingSuggestion?.title ?? ""}
        subject={pendingSuggestion?.subject ?? null}
        tags={pendingSuggestion?.tags ?? []}
        applying={applyingSuggestion}
        onApply={() => {
          void applySuggestions();
        }}
        onKeepMine={keepMineAndContinue}
      />

      <PaywallModal
        isOpen={showLimitReachedModal}
        variant="study-pack-limit"
        source="note_editor"
        onClose={() => setShowLimitReachedModal(false)}
      />

      {currentPlan === "FREE" ? (
        <PaywallModal
          isOpen={showOcrLimitModal}
          variant="ocr-limit"
          source="note_editor_ocr_limit"
          onClose={() => setShowOcrLimitModal(false)}
        />
      ) : (
        <AppModal
          isOpen={showOcrLimitModal}
          title="OCR limit reached"
          description="You’ve reached your OCR limit for this billing cycle. Your limits will reset on your next billing date."
          onClose={() => setShowOcrLimitModal(false)}
          actions={(
            <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
              <Button
                type="button"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={() => setShowOcrLimitModal(false)}
              >
                OK
              </Button>
            </div>
          )}
        />
      )}

      {toastMessage ? <ToastMessage message={toastMessage} tone={toastTone} /> : null}
    </>
  );
}
