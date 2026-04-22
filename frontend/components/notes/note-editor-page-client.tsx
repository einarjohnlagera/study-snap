"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { StudyPackLimitModal } from "@/components/billing/study-pack-limit-modal";
import {
  completeProductOnboarding,
  copyNote,
  createNote,
  createStudyPackFromNote,
  extractNoteTextFromFile,
  getMe,
  getNote,
  isEmailNotVerifiedError,
  isOcrLimitReachedError,
  listCoursePrograms,
  listSubjects,
  type NoteTargetProfileType,
  trackAnalyticsEvent,
  type LearnerLevel,
  type NoteResponse,
  updateNote,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import {
  formatStudyPackResetDate,
  isStudyPackLimitReached,
  isStudyPackLimitReachedMessage,
  resolveRemainingUsageCredits,
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
import {
  buildGeneratingNoteDetailPath,
  resolveGeneratedNoteTab,
  type NoteEntryMode,
  type NoteEntrySource,
} from "@/lib/note-entry";
import {
  COURSE_PROGRAM_SUGGESTIONS,
  mergeCourseProgramSuggestions,
} from "@/lib/learning-profile";
import { applyAiSuggestionSelection, type AiSuggestionSelection } from "@/lib/note-metadata";
import {
  isTeacherSelectableNoteTarget,
  mapProfileTypeToNoteTargetProfile,
  toSelectableNoteTargetProfile,
} from "@/lib/note-target-profile";

type NoteEditorPageClientProps = {
  noteId?: string;
  initialMode?: NoteEntryMode;
  initialSource?: NoteEntrySource;
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
    courseProgram: note.courseProgram ?? "",
    targetProfileType: toSelectableNoteTargetProfile(note.targetProfileType),
    content: note.content,
    tags: note.tags ?? [],
  };
}

function resolveGenerateLabel(_profileType: string | null | undefined): string {
  return "Generate";
}

function resolveGenerateHelperText(_profileType: string | null | undefined): string {
  return "Generates a Study Pack from your material.";
}

export function NoteEditorPageClient({
  noteId,
  initialMode = null,
  initialSource = null,
}: Readonly<NoteEditorPageClientProps>) {
  const authUser = getAuthUser();
  const router = useRouter();
  const pathname = usePathname();
  const isEditMode = Boolean(noteId);
  const [studyPackStatus, setStudyPackStatus] = useState<NoteResponse["studyPackStatus"] | null>(null);
  const [draft, setDraft] = useState<NoteEditorDraft>({
    title: "",
    subject: "",
    courseProgram: "",
    targetProfileType: "",
    content: "",
    tags: [],
  });
  const [currentNoteId, setCurrentNoteId] = useState<string | null>(noteId ?? null);
  const [loadingNote, setLoadingNote] = useState(isEditMode);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isCopying, setIsCopying] = useState(false);
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
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [courseProgramSuggestions, setCourseProgramSuggestions] = useState<string[]>([]);
  const [profileLearnerLevel, setProfileLearnerLevel] = useState<LearnerLevel | "">("");
  const { usageSummary, refreshUsageSummary } = useBillingUsageSummary();
  const currentPlan = usageSummary?.plan ?? (authUser?.planType ?? "FREE");
  const currentProfileType = authUser?.profileType ?? "STUDENT";
  const currentUserRole = authUser?.role ?? "USER";
  const showTargetProfileTypeField = isTeacherSelectableNoteTarget(currentProfileType, currentUserRole);
  const generateLabel = resolveGenerateLabel(currentProfileType);
  const generateHelperText = resolveGenerateHelperText(currentProfileType);
  const targetProfileTypeHelperText = isEditMode
    ? "Changing audience will affect future quiz generation."
    : "Choose the learner audience for this note.";
  const generatingLabel = currentProfileType === "BOARD_EXAM"
    ? "Preparing practice..."
    : currentProfileType === "TEACHER"
      ? "Creating quiz..."
      : "Generating...";

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

  useEffect(() => {
    let active = true;

    void Promise.allSettled([
      listSubjects("mine"),
      listCoursePrograms("mine"),
    ]).then(([subjectsResult, courseProgramsResult]) => {
      if (!active) {
        return;
      }
      setSubjectSuggestions(subjectsResult.status === "fulfilled" ? subjectsResult.value : []);
      setCourseProgramSuggestions(courseProgramsResult.status === "fulfilled" ? courseProgramsResult.value : []);
    });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    void getMe()
      .then((me) => {
        if (!active) {
          return;
        }
        setProfileLearnerLevel(me.learnerLevel ?? "");
        if (!isEditMode) {
          setDraft((previous) => (
            previous.courseProgram.trim().length > 0
              ? previous
              : { ...previous, courseProgram: me.courseProgram ?? "" }
          ));
        }
      })
      .catch(() => {
        // Best-effort defaults. Note creation and editing still work without profile metadata.
        if (active) {
          setProfileLearnerLevel("");
        }
      });

    return () => {
      active = false;
    };
  }, [isEditMode]);

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
  const contentLocked = isEditMode && studyPackStatus === "STUDY_PACK_READY";
  const hasGeneratedStudyPack = studyPackStatus === "STUDY_PACK_READY";
  const availableCourseProgramSuggestions = useMemo(
    () => mergeCourseProgramSuggestions(
      COURSE_PROGRAM_SUGGESTIONS,
      courseProgramSuggestions,
      [draft.courseProgram],
    ),
    [courseProgramSuggestions, draft.courseProgram],
  );
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
    // `router` identity is not stable in tests, and the effect only needs the current note id.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [noteId]);
  const studyPacksRemaining = usageSummary
    ? resolveRemainingUsageCredits(
      usageSummary.usage.studyPacksUsed,
      usageSummary.limits.studyPacksPerMonth,
      usageSummary.remaining?.studyPacksRemaining,
    )
    : null;
  const usageResetDateLabel = formatStudyPackResetDate(usageSummary?.usageCycle?.endsAt);
  const hasReachedStudyPackLimit = isStudyPackLimitReached(studyPacksRemaining);
  const shouldShowNearLimitBanner = usageSummary
    ? shouldShowNearStudyPackLimitBanner(usageSummary.plan, studyPacksRemaining)
    : false;

  const resolveTargetProfileType = useCallback((): NoteTargetProfileType | null => {
    if (showTargetProfileTypeField) {
      if (!draft.targetProfileType) {
        showToast("Please select an audience", "info");
        return null;
      }
      return draft.targetProfileType;
    }
    return mapProfileTypeToNoteTargetProfile(currentProfileType);
  }, [currentProfileType, draft.targetProfileType, showTargetProfileTypeField, showToast]);

  const buildRequest = useCallback(() => {
    const targetProfileType = resolveTargetProfileType();
    if (!targetProfileType) {
      return null;
    }
    return {
      title: normalizeOptional(draft.title),
      subject: normalizeOptional(draft.subject),
      courseProgram: normalizeOptional(draft.courseProgram),
      tags: draft.tags,
      targetProfileType,
      content: draft.content,
    };
  }, [draft.content, draft.courseProgram, draft.subject, draft.tags, draft.title, resolveTargetProfileType]);

  const upsertNote = useCallback(async (): Promise<NoteResponse | null> => {
    if (contentEmpty) {
      showToast("Please add note content first.", "info");
      return null;
    }

    const payload = buildRequest();
    if (!payload) {
      return null;
    }
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
      if (isEditMode) {
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
  }, [contentEmpty, firstStudyStep, isEditMode, isGenerating, isSaving, router, showToast, upsertNote]);

  const handleCancel = useCallback(() => {
    const destinationNoteId = currentNoteId ?? noteId;
    if (!destinationNoteId) {
      router.push("/notes/new");
      return;
    }
    router.push(`/notes/${destinationNoteId}`);
  }, [currentNoteId, noteId, router]);

  const finalizeGenerationRedirect = useCallback((noteIdToOpen: string) => {
    const tab = resolveGeneratedNoteTab(currentProfileType, initialMode, initialSource);
    router.push(buildGeneratingNoteDetailPath(noteIdToOpen, tab));
  }, [currentProfileType, initialMode, initialSource, router]);

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

      const queued = await createStudyPackFromNote(saved.id);
      setStudyPackStatus(queued.studyPackStatus ?? "GENERATING");
      finalizeGenerationRedirect(saved.id);
    } catch (error) {
      if (isEmailNotVerifiedError(error)) {
        showToast("Email verification is required before generating Study Packs.", "info");
      } else {
        const message = error instanceof Error ? error.message : "Could not generate Study Pack.";
        if (isStudyPackLimitReachedMessage(message)) {
          void refreshUsageSummary();
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
    refreshUsageSummary,
  ]);

  const applySuggestions = useCallback(async (selection: AiSuggestionSelection) => {
    if (!pendingSuggestion || applyingSuggestion) {
      return;
    }

    setApplyingSuggestion(true);
    try {
      const targetProfileType = resolveTargetProfileType();
      if (!targetProfileType) {
        return;
      }
      const nextMetadata = applyAiSuggestionSelection(
        {
          title: draft.title,
          subject: draft.subject,
          tags: draft.tags,
        },
        {
          title: pendingSuggestion.title,
          subject: pendingSuggestion.subject,
          tags: pendingSuggestion.tags,
        },
        selection,
      );
      const updated = await updateNote(pendingSuggestion.noteId, {
        title: nextMetadata.title,
        subject: nextMetadata.subject,
        courseProgram: normalizeOptional(draft.courseProgram),
        tags: nextMetadata.tags,
        targetProfileType,
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
  }, [
    applyingSuggestion,
    draft.content,
    draft.courseProgram,
    draft.subject,
    draft.tags,
    draft.title,
    finalizeGenerationRedirect,
    pendingSuggestion,
    resolveTargetProfileType,
    showToast,
  ]);

  const keepMineAndContinue = useCallback(() => {
    if (!pendingSuggestion) {
      return;
    }
    const noteIdToOpen = pendingSuggestion.noteId;
    setPendingSuggestion(null);
    finalizeGenerationRedirect(noteIdToOpen);
  }, [finalizeGenerationRedirect, pendingSuggestion]);

  const handleMakeCopy = useCallback(async () => {
    const sourceNoteId = currentNoteId ?? noteId;
    if (!sourceNoteId || isCopying) {
      return;
    }

    setIsCopying(true);
    try {
      const copied = await copyNote(sourceNoteId);
      router.push(`/notes/${copied.id}?copied=1`);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not copy note.";
      showToast(message, "error");
    } finally {
      setIsCopying(false);
    }
  }, [currentNoteId, isCopying, noteId, router, showToast]);

  const pageTitle = isEditMode ? "Edit Note" : "New Note";
  const pageTitleLabel = !isEditMode && initialMode === "quiz"
    ? "Create Quiz"
    : !isEditMode && initialSource === "paste"
      ? "Paste Material"
      : !isEditMode && initialSource === "upload"
        ? "Upload Material"
        : pageTitle;
  const helperText = isEditMode
    ? "Update your note details and content."
    : initialMode === "quiz"
    ? "Start with your material, then generate a Study Pack to open quiz practice first."
    : initialSource === "paste"
      ? "Paste your material into Content, then generate a Study Pack to open quiz practice first."
      : initialSource === "upload"
        ? "Upload your material first, then generate a Study Pack to open quiz practice first."
        : "Create your note first, then generate a Study Pack when you're ready.";
  const studyPackMessage = hasGeneratedStudyPack
    ? "This note already has a Study Pack. Save metadata changes here, or make a copy to create a new editable version."
    : isEditMode
      ? "Generate a Study Pack from this note when you are ready."
      : "Save your note for later, or generate immediately when the content is ready.";
  const actionLabel = hasGeneratedStudyPack ? "Make a Copy" : generateLabel;
  const actionHelperText = hasGeneratedStudyPack
    ? "Make a copy to create a new editable version while keeping this Study Pack intact."
    : generateHelperText;
  const actionLoadingLabel = hasGeneratedStudyPack ? "Copying..." : generatingLabel;
  const showFirstStudyHint = !isEditMode && firstStudyStep === "create-note";
  const autoFocusContent = !isEditMode && (initialMode === "quiz" || initialSource === "paste");
  const autoFocusImport = !isEditMode && initialSource === "upload";

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
          <NearLimitBanner
            planType={currentPlan}
            remainingCredits={studyPacksRemaining}
            resetDateLabel={usageResetDateLabel}
          />
        </div>
      ) : null}

      <NoteEditorForm
        pageTitle={pageTitleLabel}
        note={draft}
        onTitleChange={(value) => setDraft((previous) => ({ ...previous, title: value }))}
        onSubjectChange={(value) => setDraft((previous) => ({ ...previous, subject: value }))}
        onCourseProgramChange={(value) => setDraft((previous) => ({ ...previous, courseProgram: value }))}
        onTargetProfileTypeChange={(value) => {
          setDraft((previous) => ({ ...previous, targetProfileType: value }));
        }}
        onContentChange={(value) => setDraft((previous) => ({ ...previous, content: value }))}
        onTagsChange={(nextTags) => setDraft((previous) => ({ ...previous, tags: nextTags }))}
        onSave={() => {
          void handleSave();
        }}
        onGenerate={hasGeneratedStudyPack
          ? () => {
            void handleMakeCopy();
          }
          : () => {
            void handleGenerate();
          }}
        onCancel={isEditMode ? handleCancel : undefined}
        isSaving={isSaving}
        isGenerating={isGenerating}
        isCopying={isCopying}
        saveStateLabel={saveStateLabel}
        helperText={helperText}
        showTagsSection
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
        contentLockHint="Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, and tags."
        disableGenerateAction={!hasGeneratedStudyPack && !isEmailVerified}
        firstStudyHintVisible={showFirstStudyHint}
        autoFocusContent={autoFocusContent}
        autoFocusImport={autoFocusImport}
        importPanelHighlighted={autoFocusImport}
        saveLabel="Save Note"
        actionLabel={actionLabel}
        actionHelperText={actionHelperText}
        actionLoadingLabel={actionLoadingLabel}
        actionIcon={hasGeneratedStudyPack ? "copy" : "generate"}
        actionVariant={hasGeneratedStudyPack ? "outline" : "default"}
        subjectSuggestions={subjectSuggestions}
        courseProgramSuggestions={availableCourseProgramSuggestions}
        learnerLevel={profileLearnerLevel}
        showTargetProfileTypeField={showTargetProfileTypeField}
        targetProfileTypeHelperText={targetProfileTypeHelperText}
        backHref={isEditMode ? (noteId ? `/notes/${noteId}` : "/library") : "/library"}
        backLabel={isEditMode ? "Note" : "Library"}
        onDismissFirstStudyHint={showFirstStudyHint ? () => {
          void dismissFirstStudyHint();
        } : undefined}
      />

      <AiSuggestionModal
        open={Boolean(pendingSuggestion)}
        currentTitle={draft.title}
        currentSubject={normalizeOptional(draft.subject)}
        currentTags={draft.tags}
        suggestedTitle={pendingSuggestion?.title ?? ""}
        suggestedSubject={pendingSuggestion?.subject ?? null}
        suggestedTags={pendingSuggestion?.tags ?? []}
        applying={applyingSuggestion}
        onApply={(selection) => {
          void applySuggestions(selection);
        }}
        onSkip={keepMineAndContinue}
      />

      {currentPlan === "FREE" ? (
        <PaywallModal
          isOpen={showLimitReachedModal}
          variant="study-pack-limit"
          source="note_editor_study_pack_limit"
          onClose={() => setShowLimitReachedModal(false)}
        />
      ) : (
        <StudyPackLimitModal
          isOpen={showLimitReachedModal}
          planType={currentPlan}
          resetDateLabel={usageResetDateLabel}
          onClose={() => setShowLimitReachedModal(false)}
        />
      )}

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
