"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { PaywallModal } from "@/components/billing/paywall-modal";
import {
  confirmStudyPackText,
  createNote,
  createStudyPackFromImage,
  createStudyPackFromNote,
  getNote,
  isEmailNotVerifiedError,
  isNeedsTextConfirmationResponse,
  type NoteResponse,
  type StudyPackResponse,
  updateNote,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import {
  PLAN_BILLING_PATH,
  hasReachedUsageLimit,
  isStudyPackLimitReachedMessage,
  shouldShowNearStudyPackLimitBanner,
} from "@/lib/plans";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { ToastMessage } from "@/components/ui/toast-message";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AiSuggestionModal } from "@/components/notes/ai-suggestion-modal";
import { NoteEditorForm, type NoteEditorDraft } from "@/components/notes/note-editor-form";

type NoteEditorPageClientProps = {
  noteId?: string;
};

type PendingSuggestion = {
  noteId: string;
  title: string;
  subject: string | null;
  tags: string[];
};

const ALLOWED_IMAGE_TYPES = ["image/png", "image/jpeg", "image/webp"];
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

function normalizeOptional(value: string): string | null {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function toFriendlyOcrErrorMessage(message: string): string {
  const normalized = message.toLowerCase();
  if (
    normalized.includes("unsupported")
    || normalized.includes("file type")
    || normalized.includes("content type")
    || normalized.includes("format")
  ) {
    return "Unsupported image type. Upload a PNG, JPEG, or WEBP image.";
  }
  if (normalized.includes("too large") || normalized.includes("max") || normalized.includes("size")) {
    return "Image is too large. Try an image smaller than 5 MB.";
  }
  if (
    normalized.includes("no readable text")
    || normalized.includes("no text")
    || normalized.includes("text detected")
    || normalized.includes("text not detected")
  ) {
    return "No readable text was detected. Retake the photo with better lighting and focus, then try again.";
  }
  return "We could not extract text from this image right now. Try another image or paste notes manually.";
}

function resolveExtractedText(response: StudyPackResponse, confirmedTextFallback?: string): string {
  const candidate = [response.extractedText, response.sourceText, confirmedTextFallback]
    .find((value) => typeof value === "string" && value.trim().length > 0);
  return candidate?.trim() ?? "";
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

export function NoteEditorPageClient({ noteId }: NoteEditorPageClientProps) {
  const router = useRouter();
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
  const [ocrImageFile, setOcrImageFile] = useState<File | null>(null);
  const [ocrImageInputKey, setOcrImageInputKey] = useState(0);
  const [ocrFlowState, setOcrFlowState] = useState<"idle" | "uploading" | "extracting" | "success" | "failure">("idle");
  const [ocrStatusMessage, setOcrStatusMessage] = useState<string | null>(null);
  const [ocrDraftId, setOcrDraftId] = useState<string | null>(null);
  const [ocrConfirmedText, setOcrConfirmedText] = useState("");
  const [isConfirmingOcrText, setIsConfirmingOcrText] = useState(false);
  const [isEmailVerified, setIsEmailVerified] = useState(Boolean(getAuthUser()?.emailVerifiedAt));
  const [showLimitReachedModal, setShowLimitReachedModal] = useState(false);
  const { usageSummary } = useBillingUsageSummary();

  useEffect(() => {
    const syncAuthState = () => {
      setIsEmailVerified(Boolean(getAuthUser()?.emailVerifiedAt));
    };
    window.addEventListener("studysnap-auth-change", syncAuthState);
    return () => {
      window.removeEventListener("studysnap-auth-change", syncAuthState);
    };
  }, []);

  useEffect(() => {
    if (!toastMessage) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setToastMessage(null);
    }, 3200);
    return () => window.clearTimeout(timeout);
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

  const contentEmpty = useMemo(() => draft.content.trim().length === 0, [draft.content]);
  const contentLocked = isDetailPage && studyPackStatus === "STUDY_PACK_READY";
  const studyPacksUsed = usageSummary?.studyPacksUsed ?? 0;
  const studyPacksLimit = usageSummary?.studyPacksLimit ?? 0;
  const hasReachedStudyPackLimit = usageSummary?.planType === "FREE"
    && hasReachedUsageLimit(studyPacksUsed, studyPacksLimit);
  const shouldShowNearLimitBanner = usageSummary
    ? shouldShowNearStudyPackLimitBanner(usageSummary.planType, studyPacksUsed, studyPacksLimit)
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
  }, [contentEmpty, isDetailPage, isGenerating, isSaving, router, showToast, upsertNote]);

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
      setShowLimitReachedModal(true);
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
          setShowLimitReachedModal(true);
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

  const handleOcrImageFileChange = useCallback(async (file: File | null) => {
    if (contentLocked) {
      setOcrImageFile(null);
      setOcrImageInputKey((previous) => previous + 1);
      setOcrFlowState("failure");
      setOcrStatusMessage("Note content is locked after generating a Study Pack. Make a copy to change the note itself.");
      showToast("Note content is locked after generating a Study Pack. Make a copy to change the note itself.", "info");
      return;
    }

    if (!file) {
      setOcrImageFile(null);
      setOcrFlowState("idle");
      setOcrStatusMessage(null);
      setOcrDraftId(null);
      setOcrConfirmedText("");
      return;
    }

    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      const message = "Unsupported image type. Upload a PNG, JPEG, or WEBP image.";
      setOcrImageFile(null);
      setOcrImageInputKey((previous) => previous + 1);
      setOcrFlowState("failure");
      setOcrStatusMessage(message);
      showToast(message, "error");
      return;
    }

    if (file.size > MAX_IMAGE_BYTES) {
      const message = "Image is too large. Try an image smaller than 5 MB.";
      setOcrImageFile(null);
      setOcrImageInputKey((previous) => previous + 1);
      setOcrFlowState("failure");
      setOcrStatusMessage(message);
      showToast(message, "error");
      return;
    }

    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      const message = "Verify your email before using OCR upload.";
      setOcrImageFile(null);
      setOcrImageInputKey((previous) => previous + 1);
      setOcrFlowState("failure");
      setOcrStatusMessage(message);
      showToast(message, "info");
      return;
    }

    setOcrImageFile(file);
    setOcrDraftId(null);
    setOcrConfirmedText("");
    setOcrFlowState("uploading");
    setOcrStatusMessage("Uploading image...");

    const extractingTimer = window.setTimeout(() => {
      setOcrFlowState("extracting");
      setOcrStatusMessage("Extracting text from image...");
    }, 500);

    try {
      const response = await createStudyPackFromImage(file);
      window.clearTimeout(extractingTimer);

      if (isNeedsTextConfirmationResponse(response)) {
        setOcrDraftId(response.id);
        setOcrConfirmedText(response.extractedText);
        setOcrFlowState("success");
        setOcrStatusMessage("Text extracted. Review and confirm before adding it to Content.");
        return;
      }

      const extractedText = resolveExtractedText(response);
      const didAppend = appendExtractedTextToContent(extractedText);
      if (!didAppend) {
        setOcrFlowState("failure");
        setOcrStatusMessage("No readable text was extracted from this image.");
        showToast("No readable text was detected. Retake the photo and try again.", "info");
        return;
      }

      setOcrFlowState("success");
      setOcrStatusMessage("Text extracted and added to Content.");
      showToast("OCR text added to Content.", "success");
    } catch (error) {
      window.clearTimeout(extractingTimer);
      if (isEmailNotVerifiedError(error)) {
        const message = "Verify your email before using OCR upload.";
        setOcrFlowState("failure");
        setOcrStatusMessage(message);
        showToast(message, "info");
        return;
      }

      const rawMessage = error instanceof Error ? error.message : "OCR request failed.";
      const message = toFriendlyOcrErrorMessage(rawMessage);
      setOcrFlowState("failure");
      setOcrStatusMessage(message);
      showToast(message, "error");
    }
  }, [appendExtractedTextToContent, contentLocked, showToast]);

  const handleConfirmOcrText = useCallback(async () => {
    if (contentLocked) {
      setOcrFlowState("failure");
      setOcrStatusMessage("Note content is locked after generating a Study Pack. Make a copy to change the note itself.");
      showToast("Note content is locked after generating a Study Pack. Make a copy to change the note itself.", "info");
      return;
    }

    if (!ocrDraftId || isConfirmingOcrText || ocrConfirmedText.trim().length === 0) {
      return;
    }

    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      const message = "Verify your email before using OCR upload.";
      setOcrFlowState("failure");
      setOcrStatusMessage(message);
      showToast(message, "info");
      return;
    }

    setIsConfirmingOcrText(true);
    setOcrFlowState("extracting");
    setOcrStatusMessage("Confirming OCR text...");

    try {
      const response = await confirmStudyPackText(ocrDraftId, ocrConfirmedText);
      const extractedText = resolveExtractedText(response, ocrConfirmedText);
      const didAppend = appendExtractedTextToContent(extractedText);
      if (!didAppend) {
        setOcrFlowState("failure");
        setOcrStatusMessage("No readable text was extracted from this image.");
        showToast("No readable text was detected. Try editing and confirming again.", "info");
        return;
      }

      setOcrDraftId(null);
      setOcrConfirmedText("");
      setOcrFlowState("success");
      setOcrStatusMessage("Confirmed text added to Content.");
      showToast("OCR text added to Content.", "success");
    } catch (error) {
      if (isEmailNotVerifiedError(error)) {
        const message = "Verify your email before using OCR upload.";
        setOcrFlowState("failure");
        setOcrStatusMessage(message);
        showToast(message, "info");
        return;
      }
      const rawMessage = error instanceof Error ? error.message : "Could not confirm OCR text.";
      const message = toFriendlyOcrErrorMessage(rawMessage);
      setOcrFlowState("failure");
      setOcrStatusMessage(message);
      showToast(message, "error");
    } finally {
      setIsConfirmingOcrText(false);
    }
  }, [appendExtractedTextToContent, contentLocked, isConfirmingOcrText, ocrConfirmedText, ocrDraftId, showToast]);

  const pageTitle = isDetailPage ? "Note" : "New Note";
  const studyPackMessage = isDetailPage
    ? "Generate a Study Pack from this note when you are ready."
    : "Save your note for later, or generate immediately when the content is ready.";

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
        helperText="Save your note for later, or generate a Study Pack instantly using 1 credit."
        showTagsSection={isDetailPage}
        studyPackMessage={studyPackMessage}
        ocrImageFile={ocrImageFile}
        ocrImageInputKey={ocrImageInputKey}
        ocrFlowState={ocrFlowState}
        ocrStatusMessage={ocrStatusMessage}
        ocrConfirmedText={ocrConfirmedText}
        ocrNeedsConfirmation={Boolean(ocrDraftId)}
        isConfirmingOcrText={isConfirmingOcrText}
        onOcrImageFileChange={(file) => {
          void handleOcrImageFileChange(file);
        }}
        onOcrConfirmedTextChange={setOcrConfirmedText}
        onConfirmOcrText={() => {
          void handleConfirmOcrText();
        }}
        disableContentEditing={contentLocked}
        contentLockHint="Note content is locked after generating a Study Pack. Make a copy to change the note itself."
        disableGenerateAction={!isEmailVerified}
        disableOcrUpload={!isEmailVerified}
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
        onClose={() => setShowLimitReachedModal(false)}
        onUpgrade={() => {
          setShowLimitReachedModal(false);
          router.push(PLAN_BILLING_PATH);
        }}
      />

      {toastMessage ? <ToastMessage message={toastMessage} tone={toastTone} /> : null}
    </>
  );
}
