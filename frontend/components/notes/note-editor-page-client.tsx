"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { NearLimitBanner, resolveAppPlan } from "@/components/billing/near-limit-banner";
import { QuotaLimitBanner } from "@/components/billing/quota-limit-banner";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { StudyPackLimitModal } from "@/components/billing/study-pack-limit-modal";
import {
  completeProductOnboarding,
  copyNote,
  createNote,
  createStudyPackFromNote,
  extractNoteTextFromFile,
  generateNoteFromTopic,
  getCourseProgramCatalog,
  getMe,
  getNote,
  getNoteApplicablePrograms,
  isEmailNotVerifiedError,
  isOcrDisabledError,
  isNoteGenerationLimitReachedError,
  isOcrLimitReachedError,
  listCoursePrograms,
  listSubjects,
  type CourseProgramCatalogItem,
  type DomainContext,
  type LearnerLevel,
  type NoteTargetProfileType,
  trackAnalyticsEvent,
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
import {
  NoteEditorForm,
  type NoteEditorDraft,
  type NoteEditorEntryOption,
} from "@/components/notes/note-editor-form";
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
import {
  clearNoteUpgradeDraft,
  loadNoteUpgradeDraft,
  resolveNoteUpgradeDraftReturnPath,
  saveNoteUpgradeDraft,
  shouldRestoreNoteUpgradeDraft,
} from "@/lib/note-upgrade-draft";
import { buildStudyPackResumePath } from "@/lib/paywall-upgrade-context";
import { applyAiSuggestionSelection, type AiSuggestionSelection } from "@/lib/note-metadata";
import { hasSeenTip, markTipSeen } from "@/lib/guidance";
import type { PaywallContextType } from "@/lib/paywall-content";
import {
  isTeacherSelectableNoteTarget,
  mapProfileTypeToNoteTargetProfile,
  toSelectableNoteTargetProfile,
} from "@/lib/note-target-profile";
import { OcrDisabledNotice } from "@/components/notes/ocr-disabled-notice";

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
    domainContext: note.domainContext ?? "",
    learnerLevel: note.learnerLevel ?? "",
    targetProfileType: toSelectableNoteTargetProfile(note.targetProfileType),
    content: note.content,
    tags: note.tags ?? [],
  };
}

function resolveGenerateLabel(_profileType: string | null | undefined): string {
  return "Generate Study Pack";
}

function resolveGenerateHelperText(_profileType: string | null | undefined): string {
  return "Turn this note into summaries, key concepts, quizzes, and practice.";
}

const GENERATE_NOTE_TIP_ID = "create-note-generate-topic";
const MULTI_PROGRAM_DOMAIN_CONTEXT_MESSAGE =
  "A note shared across several programs needs a Domain Context, so the AI knows which academic domain to write in.";
const NOTE_CONTENT_SCROLL_DELAY_MS = 140;
const IMPORT_LOADING_MESSAGE = "Extracting text from your file...";
const IMPORT_SUCCESS_MESSAGE = "Text imported. Review and edit it before continuing.";
const IMPORT_EMPTY_MESSAGE = "This file is empty or has no readable text.";
const IMPORT_GENERIC_ERROR_MESSAGE = "We couldn’t extract text from this file. Try another image or file.";
const IMPORT_UNSUPPORTED_FILE_MESSAGE = "Unsupported file type. Upload PNG, JPG, JPEG, WEBP, TXT, PDF, or DOCX.";
const IMPORT_SCANNED_PDF_MESSAGE = "This PDF appears to be scanned or image-based. Please upload images for OCR instead.";
const CHECKOUT_DRAFT_FALLBACK_MESSAGE = "We couldn't save your draft before checkout. Your note was preserved locally and will be restored when you return.";

function resolveGenerateFromTopicCourseProgram(
  draftCourseProgram: string,
  profileCourseProgram: string,
) {
  return normalizeOptional(draftCourseProgram) ?? normalizeOptional(profileCourseProgram) ?? undefined;
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
    domainContext: "",
    learnerLevel: "",
    targetProfileType: "",
    content: "",
    tags: [],
  });
  const [entryOption, setEntryOption] = useState<NoteEditorEntryOption>(initialSource === "upload" ? "import" : "write");
  const [generateTopic, setGenerateTopic] = useState("");
  const [currentNoteId, setCurrentNoteId] = useState<string | null>(noteId ?? null);
  const [loadingNote, setLoadingNote] = useState(isEditMode);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isGeneratingNote, setIsGeneratingNote] = useState(false);
  const [isCopying, setIsCopying] = useState(false);
  const [saveStateLabel, setSaveStateLabel] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastTone, setToastTone] = useState<"success" | "error" | "info" | "warning">("info");
  const [pendingSuggestion, setPendingSuggestion] = useState<PendingSuggestion | null>(null);
  const [applyingSuggestion, setApplyingSuggestion] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importFileInputKey, setImportFileInputKey] = useState(0);
  const [importFlowState, setImportFlowState] = useState<"idle" | "uploading" | "extracting" | "success" | "failure">("idle");
  const [importStatusMessage, setImportStatusMessage] = useState<string | null>(null);
  const [importReviewMessage, setImportReviewMessage] = useState<string | null>(null);
  const [importOcrDisabledMessage, setImportOcrDisabledMessage] = useState<string | null>(null);
  const [isEmailVerified, setIsEmailVerified] = useState(Boolean(getAuthUser()?.emailVerifiedAt));
  const [firstStudyStep, setFirstStudyStep] = useState<FirstStudyOnboardingStep | null>(null);
  const [showGenerateNoteTip, setShowGenerateNoteTip] = useState(false);
  const [showLimitReachedModal, setShowLimitReachedModal] = useState(false);
  const [showOcrLimitModal, setShowOcrLimitModal] = useState(false);
  const [showNoteGenerationLimitModal, setShowNoteGenerationLimitModal] = useState(false);
  const [revealOptionalDetailsSignal, setRevealOptionalDetailsSignal] = useState(0);
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [courseProgramSuggestions, setCourseProgramSuggestions] = useState<string[]>([]);
  const [applicableProgramCatalog, setApplicableProgramCatalog] = useState<CourseProgramCatalogItem[]>([]);
  const [applicableProgramIds, setApplicableProgramIds] = useState<string[]>([]);
  const [savedApplicableProgramIds, setSavedApplicableProgramIds] = useState<string[]>([]);
  const [savedApplicableProgramNames, setSavedApplicableProgramNames] = useState<string[]>([]);
  const [courseProgramShadowed, setCourseProgramShadowed] = useState<boolean | null>(null);
  const [copiedFromNoteId, setCopiedFromNoteId] = useState<string | null>(null);
  const [applicableProgramsLoading, setApplicableProgramsLoading] = useState(false);
  const [applicableProgramsError, setApplicableProgramsError] = useState<string | null>(null);
  const [applicableProgramsDirty, setApplicableProgramsDirty] = useState(false);
  const [applicableProgramsRetryToken, setApplicableProgramsRetryToken] = useState(0);
  const [profileCourseProgram, setProfileCourseProgram] = useState("");
  const [catalogLoaded, setCatalogLoaded] = useState(false);
  const { usageSummary, refreshUsageSummary } = useBillingUsageSummary();
  const generatedContentSectionRef = useRef<HTMLElement | null>(null);
  const [generatedContentRefreshToken, setGeneratedContentRefreshToken] = useState(0);
  const [hasGeneratedTopicDraft, setHasGeneratedTopicDraft] = useState(false);
  const currentPlan = usageSummary?.plan ?? (authUser?.planType ?? "FREE");
  const currentProfileType = authUser?.profileType ?? "STUDENT";
  const currentUserRole = authUser?.role ?? "USER";
  const showTargetProfileTypeField = isTeacherSelectableNoteTarget(currentProfileType, currentUserRole);
  const generateLabel = resolveGenerateLabel(currentProfileType);
  const generateHelperText = resolveGenerateHelperText(currentProfileType);
  const targetProfileTypeHelperText = isEditMode
    ? "Changing audience will affect future quiz generation."
    : "Choose the learner audience for this note.";
  const generatingLabel = "Generating Study Pack...";

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
    if (!showTargetProfileTypeField && !noteId) {
      return;
    }
    let active = true;
    setApplicableProgramsLoading(true);
    setApplicableProgramsError(null);
    const catalogRequest = showTargetProfileTypeField ? getCourseProgramCatalog() : Promise.resolve([]);
    const programsRequest = noteId ? getNoteApplicablePrograms(noteId) : Promise.resolve(null);
    void Promise.all([catalogRequest, programsRequest])
      .then(([catalog, response]) => {
        if (!active) {
          return;
        }
        setApplicableProgramCatalog(catalog);
        if (response) {
          const selectedIds = response.programs.map((program) => program.id);
          setApplicableProgramIds(selectedIds);
          setSavedApplicableProgramIds(selectedIds);
          setSavedApplicableProgramNames(response.programs.map((program) => program.name));
          setCourseProgramShadowed(response.courseProgramShadowed);
          setApplicableProgramsDirty(false);
        }
        setCatalogLoaded(true);
      })
      .catch((error) => {
        if (active) {
          setApplicableProgramsError(
            error instanceof Error ? error.message : "Could not load course programs.",
          );
        }
      })
      .finally(() => {
        if (active) {
          setApplicableProgramsLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [applicableProgramsRetryToken, noteId, showTargetProfileTypeField]);

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
    if (isEditMode || hasSeenTip(GENERATE_NOTE_TIP_ID)) {
      return;
    }
    setShowGenerateNoteTip(true);
    markTipSeen(GENERATE_NOTE_TIP_ID);
  }, [isEditMode]);

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

  // Seed a curator's Course / Program(s) from their profile on a NEW note, matching the long-standing
  // behaviour of the single-valued field. It needs both the profile value and the catalog to map a
  // name onto an id, so it cannot live in either fetch effect alone. Only ever a pre-fill: it never
  // runs in edit mode, and never once the author has touched the selection.
  useEffect(() => {
    if (isEditMode || !showTargetProfileTypeField || !catalogLoaded) {
      return;
    }
    if (applicableProgramsDirty || applicableProgramIds.length > 0) {
      return;
    }
    const profileProgram = profileCourseProgram.trim();
    if (!profileProgram) {
      return;
    }
    const match = applicableProgramCatalog.find(
      (program) => program.name.toLocaleLowerCase("en") === profileProgram.toLocaleLowerCase("en"),
    );
    if (match) {
      setApplicableProgramIds([match.id]);
    }
  }, [
    applicableProgramCatalog,
    applicableProgramIds.length,
    applicableProgramsDirty,
    catalogLoaded,
    isEditMode,
    profileCourseProgram,
    showTargetProfileTypeField,
  ]);

  useEffect(() => {
    let active = true;
    void getMe()
      .then((me) => {
        if (!active) {
          return;
        }
        setProfileCourseProgram(me.courseProgram ?? "");
        if (!isEditMode) {
          setDraft((previous) => (
            previous.courseProgram.trim().length > 0
              ? previous
              : {
                ...previous,
                courseProgram: previous.courseProgram.trim().length > 0 ? previous.courseProgram : me.courseProgram ?? "",
              }
          ));
        }
      })
      .catch(() => {
        // Best-effort defaults. Note creation and editing still work without profile metadata.
        if (active) {
          setProfileCourseProgram("");
        }
      });

    return () => {
      active = false;
    };
  }, [isEditMode]);

  const showToast = useCallback((message: string, tone: "success" | "error" | "info" | "warning" = "info") => {
    setToastTone(tone);
    setToastMessage(message);
  }, []);

  useEffect(() => {
    if (isEditMode || !authUser?.id || !shouldRestoreNoteUpgradeDraft()) {
      return;
    }
    const snapshot = loadNoteUpgradeDraft(authUser.id);
    if (!snapshot) {
      return;
    }
    setDraft(snapshot.draft);
    setEntryOption(snapshot.entryOption);
    setGenerateTopic(snapshot.generateTopic);
    clearNoteUpgradeDraft(authUser.id);
    showToast("Draft restored after returning from billing.", "info");
  }, [authUser?.id, isEditMode, showToast]);

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
  const hasGeneratedStudyPack = studyPackStatus === "STUDY_PACK_READY";
  const availableCourseProgramSuggestions = useMemo(
    () => mergeCourseProgramSuggestions(
      COURSE_PROGRAM_SUGGESTIONS,
      courseProgramSuggestions,
      [draft.courseProgram],
    ),
    [courseProgramSuggestions, draft.courseProgram],
  );
  const openLockedFeaturePaywall = useCallback((
    variant: "study-pack-limit" | "ocr-limit" | "note-generation-limit",
    source: string,
  ) => {
    const feature = variant === "study-pack-limit"
      ? "study_pack_limit"
      : variant === "ocr-limit"
        ? "ocr_limit"
        : "note_generation_limit";
    void trackAnalyticsEvent({
      eventType: "FEATURE_LOCKED_CLICKED",
      metadata: {
        feature,
        source,
        path: pathname,
        noteId: currentNoteId,
      },
    });
    if (variant === "study-pack-limit") {
      setShowLimitReachedModal(true);
      return;
    }
    if (variant === "ocr-limit") {
      setShowOcrLimitModal(true);
      return;
    }
    setShowNoteGenerationLimitModal(true);
  }, [currentNoteId, pathname]);

  const handleImportFileChange = useCallback(async (file: File | null) => {
    const resetImportInput = () => {
      setImportFileInputKey((previous) => previous + 1);
    };

    if (!file) {
      setImportFile(null);
      setImportFlowState("idle");
      setImportStatusMessage(null);
      setImportReviewMessage(null);
      setImportOcrDisabledMessage(null);
      return;
    }

    setImportFile(file);
    setImportReviewMessage(null);
    setImportOcrDisabledMessage(null);
    setImportFlowState("uploading");
    setImportStatusMessage(IMPORT_LOADING_MESSAGE);

    const extractingTimer = globalThis.setTimeout(() => {
      setImportFlowState("extracting");
      setImportStatusMessage(IMPORT_LOADING_MESSAGE);
    }, 350);

    try {
      const extracted = await extractNoteTextFromFile(file);
      globalThis.clearTimeout(extractingTimer);

      const didAppend = appendExtractedTextToContent(extracted.extractedText);
      if (!didAppend) {
        setImportFlowState("failure");
        setImportStatusMessage(IMPORT_EMPTY_MESSAGE);
        resetImportInput();
        showToast(IMPORT_EMPTY_MESSAGE, "info");
        return;
      }

      setImportFlowState("success");
      setImportStatusMessage(IMPORT_SUCCESS_MESSAGE);
      setGeneratedContentRefreshToken((previous) => previous + 1);
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
      if (isEmailNotVerifiedError(error)) {
        const verificationMessage = "Verify your email before using OCR upload.";
        setImportFlowState("failure");
        setImportStatusMessage(verificationMessage);
        setImportReviewMessage(null);
        setImportOcrDisabledMessage(null);
        resetImportInput();
        showToast(verificationMessage, "info");
        return;
      }
      if (isOcrLimitReachedError(error)) {
        setImportFlowState("failure");
        setImportStatusMessage(null);
        setImportReviewMessage(null);
        setImportOcrDisabledMessage(null);
        resetImportInput();
        if (currentPlan === "FREE") {
          openLockedFeaturePaywall("ocr-limit", "note_editor_ocr_limit");
        } else {
          setShowOcrLimitModal(true);
        }
        return;
      }
      if (isOcrDisabledError(error)) {
        setImportFlowState("failure");
        setImportStatusMessage(null);
        setImportReviewMessage(null);
        setImportOcrDisabledMessage(error.message);
        resetImportInput();
        return;
      }
      const rawMessage = error instanceof Error ? error.message : IMPORT_GENERIC_ERROR_MESSAGE;
      const resolvedMessage = rawMessage === IMPORT_UNSUPPORTED_FILE_MESSAGE || rawMessage === IMPORT_SCANNED_PDF_MESSAGE
        ? rawMessage
        : IMPORT_GENERIC_ERROR_MESSAGE;
      setImportFlowState("failure");
      setImportStatusMessage(resolvedMessage);
      setImportReviewMessage(null);
      setImportOcrDisabledMessage(null);
      resetImportInput();
      showToast(resolvedMessage, "error");
    }
  }, [appendExtractedTextToContent, currentPlan, openLockedFeaturePaywall, showToast]);

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
        setCopiedFromNoteId(note.copiedFromNoteId);
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

  useEffect(() => {
    if (generateTopic.trim().length > 0 || !hasGeneratedTopicDraft) {
      return;
    }
    setHasGeneratedTopicDraft(false);
  }, [generateTopic, hasGeneratedTopicDraft]);

  useEffect(() => {
    if (generatedContentRefreshToken === 0) {
      return;
    }
    const timeoutId = globalThis.setTimeout(() => {
      generatedContentSectionRef.current?.scrollIntoView?.({
        behavior: "smooth",
        block: "start",
      });
      const contentElement = globalThis.document.getElementById("note-content");
      if (contentElement instanceof HTMLTextAreaElement) {
        contentElement.scrollTop = 0;
      }
    }, NOTE_CONTENT_SCROLL_DELAY_MS);
    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [generatedContentRefreshToken]);

  const studyPacksRemaining = usageSummary
    ? resolveRemainingUsageCredits(
      usageSummary.usage.studyPacksUsed,
      usageSummary.limits.studyPacksPerMonth,
      usageSummary.remaining?.studyPacksRemaining,
    )
    : null;
  const noteGenerationLimit = usageSummary?.limits.noteGenerationsPerMonth;
  const noteGenerationsUsed = usageSummary?.usage.noteGenerationsUsed;
  const noteGenerationsRemaining = usageSummary
    && typeof noteGenerationLimit === "number"
    && typeof noteGenerationsUsed === "number"
    ? resolveRemainingUsageCredits(
      noteGenerationsUsed,
      noteGenerationLimit,
      usageSummary.remaining?.noteGenerationsRemaining,
    )
    : null;
  const usageResetDateLabel = formatStudyPackResetDate(usageSummary?.usageCycle?.endsAt);
  const hasReachedStudyPackLimit = isStudyPackLimitReached(studyPacksRemaining);
  const hasReachedNoteGenerationLimit = typeof noteGenerationsRemaining === "number" && noteGenerationsRemaining <= 0;
  const shouldShowNearLimitBanner = usageSummary
    ? shouldShowNearStudyPackLimitBanner(usageSummary.plan, studyPacksRemaining)
    : false;
  const noteGenerationRemainingLabel = typeof noteGenerationsRemaining === "number" && noteGenerationsRemaining > 0
    ? `${noteGenerationsRemaining} topic note${noteGenerationsRemaining === 1 ? "" : "s"} left this month.`
    : null;
  const normalizedGenerateTopic = generateTopic.trim();
  const generateNoteButtonLabel = hasGeneratedTopicDraft && normalizedGenerateTopic.length > 0
    ? "Create Again"
    : "Create a Note";
  const contentStatusText = entryOption === "generate" && hasGeneratedTopicDraft && normalizedGenerateTopic.length > 0
    ? (isGeneratingNote
      ? "Creating a new version..."
      : "Not quite right? Try refining your topic before creating again.")
    : importFlowState === "success"
      ? IMPORT_SUCCESS_MESSAGE
      : null;

  const resolveTargetProfileType = useCallback((): NoteTargetProfileType | null => {
    if (showTargetProfileTypeField) {
      if (!draft.targetProfileType) {
        setRevealOptionalDetailsSignal((previous) => previous + 1);
        showToast("Please select an audience", "info");
        return null;
      }
      return draft.targetProfileType;
    }
    return mapProfileTypeToNoteTargetProfile(currentProfileType);
  }, [currentProfileType, draft.targetProfileType, showTargetProfileTypeField, showToast]);

  const buildRequest = useCallback(async () => {
    const targetProfileType = resolveTargetProfileType();
    if (!targetProfileType) {
      return null;
    }
    // The profile program may reduce friction when CREATING a note, but it must never become an
    // existing note's persisted metadata without an explicit author decision (ADR-001). Editing a
    // note whose course/program is null previously resolved to the editor's own profile value and
    // submitted it while the field rendered empty. In edit mode the note's own value is now the
    // only source; a missing one falls through to the validation prompt below, which asks the
    // author to classify the note rather than silently classifying it for them.
    let resolvedCourseProgram = isEditMode
      ? normalizeOptional(draft.courseProgram)
      : normalizeOptional(draft.courseProgram) ?? normalizeOptional(profileCourseProgram);
    if (!resolvedCourseProgram && !isEditMode) {
      try {
        const me = await getMe();
        resolvedCourseProgram = resolvedCourseProgram ?? normalizeOptional(me.courseProgram ?? "");
        setProfileCourseProgram(me.courseProgram ?? "");
        setDraft((previous) => (
          previous.courseProgram.trim().length > 0
            ? previous
            : {
              ...previous,
              courseProgram: previous.courseProgram.trim().length > 0 ? previous.courseProgram : me.courseProgram ?? "",
            }
        ));
      } catch {
        resolvedCourseProgram = resolvedCourseProgram ?? null;
      }
    }
    const missing: string[] = [];
    if (showTargetProfileTypeField
      ? applicableProgramIds.length === 0
      : (!isEditMode || courseProgramShadowed === false) && !resolvedCourseProgram) {
      missing.push("Course / Program(s)");
    }
    if (missing.length > 0) {
      setRevealOptionalDetailsSignal((previous) => previous + 1);
      showToast(`Please complete: ${missing.join(", ")}.`, "warning");
      return null;
    }
    // C6. The three sibling surfaces (Create a Note, Note Detail, Bulk Generate) all pre-validate this
    // rule; Save did not, so a curator who family-expanded to several programs with "Add details"
    // collapsed -- the default -- got a raw 400 naming a field that was off screen inside the closed
    // accordion, with no way to act on it. Reveal the panel as well as reporting it.
    if (showTargetProfileTypeField && applicableProgramIds.length > 1 && !draft.domainContext) {
      setRevealOptionalDetailsSignal((previous) => previous + 1);
      setFormError(MULTI_PROGRAM_DOMAIN_CONTEXT_MESSAGE);
      showToast(MULTI_PROGRAM_DOMAIN_CONTEXT_MESSAGE, "warning");
      return null;
    }
    return {
      title: normalizeOptional(draft.title),
      subject: normalizeOptional(draft.subject),
      courseProgramText: showTargetProfileTypeField ? null : resolvedCourseProgram,
      courseProgramIds: showTargetProfileTypeField ? applicableProgramIds : [],
      domainContext: draft.domainContext || null,
      learnerLevel: draft.learnerLevel || null,
      tags: draft.tags,
      targetProfileType,
      content: draft.content,
    };
  }, [
    draft.content,
    draft.courseProgram,
    draft.domainContext,
    draft.learnerLevel,
    draft.subject,
    draft.tags,
    draft.title,
    profileCourseProgram,
    isEditMode,
    resolveTargetProfileType,
    setRevealOptionalDetailsSignal,
    showToast,
    showTargetProfileTypeField,
    applicableProgramIds,
    courseProgramShadowed,
  ]);

  const upsertNote = useCallback(async (): Promise<NoteResponse | null> => {
    if (contentEmpty) {
      showToast("Please add note content first.", "info");
      return null;
    }

    const payload = await buildRequest();
    if (!payload) {
      return null;
    }
    const saved = currentNoteId
      ? await updateNote(currentNoteId, payload)
      : await createNote(payload);

    if (showTargetProfileTypeField) {
      setSavedApplicableProgramIds(applicableProgramIds);
      setApplicableProgramsDirty(false);
    }

    setCurrentNoteId(saved.id);
    setDraft(toDraft(saved));
    setStudyPackStatus(saved.studyPackStatus ?? "DRAFT");
    return saved;
  }, [
    applicableProgramIds,
    applicableProgramsDirty,
    applicableProgramsError,
    buildRequest,
    contentEmpty,
    currentNoteId,
    showTargetProfileTypeField,
    showToast,
  ]);

  const prepareUpgradeContext = useCallback(async (
    contextType: PaywallContextType,
  ): Promise<Partial<{ returnPath: string | null; noteId: string | null }>> => {
    const fallbackReturnPath = resolveNoteUpgradeDraftReturnPath();
    const shouldPreserveDraft = draft.content.trim().length > 0
      || draft.title.trim().length > 0
      || draft.subject.trim().length > 0
      || draft.courseProgram.trim().length > 0
      || draft.tags.length > 0
      || generateTopic.trim().length > 0
      || entryOption !== "write";

    if (!isEditMode && authUser?.id && shouldPreserveDraft) {
      saveNoteUpgradeDraft(authUser.id, {
        draft,
        entryOption,
        generateTopic,
        savedAtMs: Date.now(),
      });
    }

    if (draft.content.trim().length === 0) {
      return {
        noteId: currentNoteId,
        returnPath: !isEditMode && shouldPreserveDraft ? fallbackReturnPath : pathname,
      };
    }

    try {
      const saved = await upsertNote();
      if (saved) {
        if (authUser?.id) {
          clearNoteUpgradeDraft(authUser.id);
        }
        return {
          noteId: saved.id,
          returnPath: contextType === "GENERATE_STUDY_PACK_LIMIT"
            ? buildStudyPackResumePath(saved.id)
            : `/notes/${saved.id}/edit`,
        };
      }
    } catch {
      if (!isEditMode && authUser?.id && shouldPreserveDraft) {
        showToast(CHECKOUT_DRAFT_FALLBACK_MESSAGE, "info");
      }
    }

    return {
      noteId: currentNoteId,
      returnPath: !isEditMode && shouldPreserveDraft ? fallbackReturnPath : pathname,
    };
  }, [
    authUser?.id,
    currentNoteId,
    draft,
    entryOption,
    generateTopic,
    isEditMode,
    pathname,
    showToast,
    upsertNote,
  ]);

  const handleSave = useCallback(async () => {
    if (isSaving || isGenerating || contentEmpty) {
      return;
    }

    setIsSaving(true);
    setFormError(null);
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
      if (authUser?.id) {
        clearNoteUpgradeDraft(authUser.id);
      }
      if (isEditMode) {
        router.push(`/notes/${saved.id}?saved=1`);
        return;
      }
      router.push(`/notes/${saved.id}`);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not save note.";
      setFormError(message);
      showToast(message, "error");
      setSaveStateLabel(null);
    } finally {
      setIsSaving(false);
    }
  }, [authUser?.id, contentEmpty, firstStudyStep, isEditMode, isGenerating, isSaving, router, showToast, upsertNote]);

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
    setFormError(null);
    try {
      const saved = await upsertNote();
      if (!saved) {
        return;
      }

      const queued = await createStudyPackFromNote(saved.id);
      setStudyPackStatus(queued.studyPackStatus ?? "GENERATING");
      if (authUser?.id) {
        clearNoteUpgradeDraft(authUser.id);
      }
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
          setFormError(message);
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
    authUser?.id,
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
        courseProgramText: showTargetProfileTypeField ? null : normalizeOptional(draft.courseProgram),
        courseProgramIds: showTargetProfileTypeField ? applicableProgramIds : [],
        domainContext: draft.domainContext || null,
        learnerLevel: draft.learnerLevel || null,
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
    draft.domainContext,
    draft.learnerLevel,
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

  const handleGenerateNote = useCallback(async () => {
    if (isGeneratingNote || isSaving || isGenerating) {
      return;
    }

    const normalizedTopic = normalizedGenerateTopic;
    if (normalizedTopic.length === 0) {
      showToast("Please enter a topic first.", "info");
      return;
    }
    if (!isEmailVerified) {
      showToast("Email verification is required before creating notes.", "info");
      return;
    }
    if (hasReachedNoteGenerationLimit) {
      if (currentPlan === "FREE") {
        openLockedFeaturePaywall("note-generation-limit", "note_editor_note_generation_limit");
      } else {
        setShowNoteGenerationLimitModal(true);
      }
      return;
    }

    const isReplacingContent = draft.content.trim().length > 0;
    if (showTargetProfileTypeField && applicableProgramIds.length === 0) {
      const message = "Please complete: Course / Program(s).";
      setFormError(message);
      showToast(message, "warning");
      return;
    }
    if (showTargetProfileTypeField && applicableProgramIds.length > 1 && !draft.domainContext) {
      setFormError(MULTI_PROGRAM_DOMAIN_CONTEXT_MESSAGE);
      showToast(MULTI_PROGRAM_DOMAIN_CONTEXT_MESSAGE, "warning");
      return;
    }
    setIsGeneratingNote(true);
    setFormError(null);
    try {
      const resolvedCourseProgram = resolveGenerateFromTopicCourseProgram(draft.courseProgram, profileCourseProgram);
      let response;
      const selectedProgramIds = showTargetProfileTypeField ? applicableProgramIds : undefined;
      if (draft.domainContext) {
        response = await generateNoteFromTopic(
          normalizedTopic,
          showTargetProfileTypeField ? undefined : resolvedCourseProgram,
          draft.domainContext,
          selectedProgramIds,
        );
      } else if (showTargetProfileTypeField && selectedProgramIds?.length) {
        response = await generateNoteFromTopic(
          normalizedTopic,
          undefined,
          undefined,
          selectedProgramIds,
        );
      } else if (resolvedCourseProgram) {
        response = await generateNoteFromTopic(normalizedTopic, resolvedCourseProgram);
      } else {
        response = await generateNoteFromTopic(normalizedTopic);
      }
      setDraft((previous) => ({
        ...previous,
        title: previous.title.trim().length > 0 ? previous.title : normalizedTopic,
        content: response.content,
      }));
      setHasGeneratedTopicDraft(true);
      setGeneratedContentRefreshToken((previous) => previous + 1);
      showToast(
        isReplacingContent
          ? "Your new note replaced the current content. Review and edit it before saving."
          : "Your new note is ready. Review and edit it before saving.",
        "success",
      );
      void refreshUsageSummary();
    } catch (error) {
      if (isEmailNotVerifiedError(error)) {
        showToast("Email verification is required before creating notes.", "info");
      } else if (isNoteGenerationLimitReachedError(error)) {
        const latestUsageSummary = await refreshUsageSummary();
        const limitPlan = latestUsageSummary?.plan ?? currentPlan;
        if (limitPlan === "FREE") {
          openLockedFeaturePaywall("note-generation-limit", "note_editor_note_generation_limit");
        } else {
          setShowNoteGenerationLimitModal(true);
        }
      } else {
        const message = error instanceof Error ? error.message : "Could not create note.";
        setFormError(message);
        showToast(message, "error");
      }
    } finally {
      setIsGeneratingNote(false);
    }
  }, [
    draft.content,
    normalizedGenerateTopic,
    isEmailVerified,
    isGenerating,
    isGeneratingNote,
    isSaving,
    hasReachedNoteGenerationLimit,
    currentPlan,
    draft.courseProgram,
    draft.domainContext,
    applicableProgramIds,
    showTargetProfileTypeField,
    openLockedFeaturePaywall,
    profileCourseProgram,
    refreshUsageSummary,
    showToast,
  ]);

  const generateNoteFooter = hasReachedNoteGenerationLimit ? (
    <QuotaLimitBanner
      title="You've reached your topic note limit for this month."
      resetDateLabel={usageResetDateLabel}
      plan={resolveAppPlan(currentPlan)}
      ctaContext="note-generation-limit"
      onUpgrade={() => openLockedFeaturePaywall("note-generation-limit", "note_editor_note_generation_limit")}
    />
  ) : noteGenerationRemainingLabel ? (
    <p className="text-xs text-foreground/60">
      {noteGenerationRemainingLabel}
    </p>
  ) : null;

  const pageTitle = isEditMode ? "Edit Note" : "New Note";
  const pageTitleLabel = !isEditMode && initialMode === "quiz"
    ? "Generate Study Pack"
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
      ? "Paste your material into Content first. You can add details later, then generate a Study Pack to open quiz practice first."
      : initialSource === "upload"
        ? "Upload your material first. You can add details later, then generate a Study Pack to open quiz practice first."
        : "Start with your note content. You can add details now or later, then generate a Study Pack when you're ready.";
  const studyPackMessage = hasGeneratedStudyPack
    ? "This note already has a Study Pack. You can edit the source note, then regenerate the Study Pack from the note detail page."
    : isEditMode
      ? "Generate a Study Pack from this note when you are ready."
      : "Save your note for later, or generate immediately when the content is ready.";
  const actionLabel = hasGeneratedStudyPack ? "Make a Copy" : generateLabel;
  const actionHelperText = hasGeneratedStudyPack
    ? "Make a separate copy if you want to branch this note without changing the current source."
    : generateHelperText;
  const actionLoadingLabel = hasGeneratedStudyPack ? "Copying..." : generatingLabel;
  const showFirstStudyHint = !isEditMode && firstStudyStep === "create-note";
  const autoFocusContent = !isEditMode && (initialMode === "quiz" || initialSource === "paste");
  const autoFocusImport = !isEditMode && initialSource === "upload";
  const showGenerateNoteEntry = !isEditMode;
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
            analyticsSource="note_editor_study_pack_near_limit"
            onUpgrade={() => openLockedFeaturePaywall("study-pack-limit", "note_editor_study_pack_limit")}
          />
        </div>
      ) : null}

      <NoteEditorForm
        pageTitle={pageTitleLabel}
        note={draft}
        onTitleChange={(value) => setDraft((previous) => ({ ...previous, title: value }))}
        onSubjectChange={(value) => setDraft((previous) => ({ ...previous, subject: value }))}
        onCourseProgramChange={(value) => setDraft((previous) => ({ ...previous, courseProgram: value }))}
        onDomainContextChange={(value: DomainContext | "") => {
          setDraft((previous) => ({ ...previous, domainContext: value }));
        }}
        onLearnerLevelChange={(value: LearnerLevel | "") => {
          setDraft((previous) => ({ ...previous, learnerLevel: value }));
        }}
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
        formError={formError}
        importFile={importFile}
        importFileInputKey={importFileInputKey}
        importFlowState={importFlowState}
        importStatusMessage={importStatusMessage}
        importReviewMessage={importReviewMessage}
        importNotice={importOcrDisabledMessage !== null ? (
          <OcrDisabledNotice message={importOcrDisabledMessage} source="note_editor_import" />
        ) : null}
        onImportFileChange={(file) => {
          void handleImportFileChange(file);
        }}
        entryOption={entryOption}
        onEntryOptionChange={(value) => setEntryOption(value)}
        generateTopic={generateTopic}
        onGenerateTopicChange={(value) => setGenerateTopic(value)}
        onGenerateNote={() => {
          void handleGenerateNote();
        }}
        isGeneratingNote={isGeneratingNote}
        disableGenerateNote={!isEmailVerified}
        generateNoteLabel={generateNoteButtonLabel}
        generateNoteLoadingLabel="Creating..."
        generateNoteFooter={generateNoteFooter}
        showGenerateNoteEntry={showGenerateNoteEntry}
        showGenerateNoteTip={showGenerateNoteTip}
        collapseOptionalDetailsByDefault={!isEditMode}
        revealOptionalDetailsSignal={revealOptionalDetailsSignal}
        contentSectionRef={generatedContentSectionRef}
        contentAnimationKey={generatedContentRefreshToken}
        contentStatusText={contentStatusText}
        disableContentEditing={false}
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
        resolvedCourseProgram={isEditMode
          ? normalizeOptional(draft.courseProgram)
          : normalizeOptional(draft.courseProgram) ?? normalizeOptional(profileCourseProgram)}
        showTargetProfileTypeField={showTargetProfileTypeField}
        showAuthoringMetadataFields={showTargetProfileTypeField}
        applicableProgramCatalog={applicableProgramCatalog}
        applicableProgramIds={applicableProgramIds}
        onApplicableProgramIdsChange={(selectedIds) => {
          setApplicableProgramIds(selectedIds);
          setApplicableProgramsDirty(true);
        }}
        applicableProgramsLoading={applicableProgramsLoading}
        applicableProgramsError={applicableProgramsError}
        onRetryApplicablePrograms={() => setApplicableProgramsRetryToken((value) => value + 1)}
        courseProgramShadowed={courseProgramShadowed ?? (
          isEditMode && (applicableProgramsLoading || Boolean(applicableProgramsError)) ? true : null
        )}
        savedApplicableProgramNames={savedApplicableProgramNames}
        copiedFromNoteId={copiedFromNoteId}
        targetProfileTypeHelperText={targetProfileTypeHelperText}
        backHref={isEditMode ? (noteId ? `/notes/${noteId}` : "/library") : "/library"}
        backLabel={isEditMode ? "Note" : "Library"}
        isTeacherCreateMode={!isEditMode && currentProfileType === "TEACHER"}
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

      {currentPlan === "PRO" ? (
        <StudyPackLimitModal
          isOpen={showLimitReachedModal}
          planType={currentPlan}
          resetDateLabel={usageResetDateLabel}
          analyticsSource="note_editor_study_pack_limit_modal"
          onClose={() => setShowLimitReachedModal(false)}
        />
      ) : (
        <PaywallModal
          isOpen={showLimitReachedModal}
          context={{
            type: "GENERATE_STUDY_PACK_LIMIT",
            noteId: currentNoteId,
          }}
          source="note_editor_study_pack_limit"
          resolveContext={() => prepareUpgradeContext("GENERATE_STUDY_PACK_LIMIT")}
          onClose={() => setShowLimitReachedModal(false)}
        />
      )}

      {currentPlan === "PRO" ? (
        <AppModal
          isOpen={showNoteGenerationLimitModal}
          title="Topic note limit reached"
          description="You’ve reached your topic note limit for this billing cycle. Your limits will reset on your next billing date."
          onClose={() => setShowNoteGenerationLimitModal(false)}
          actions={(
            <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
              <Button
                type="button"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={() => setShowNoteGenerationLimitModal(false)}
              >
                OK
              </Button>
            </div>
          )}
        />
      ) : (
        <PaywallModal
          isOpen={showNoteGenerationLimitModal}
          context={{
            type: "GENERATE_NOTE_LIMIT",
            noteId: currentNoteId,
          }}
          source="note_editor_note_generation_limit"
          resolveContext={() => prepareUpgradeContext("GENERATE_NOTE_LIMIT")}
          onClose={() => setShowNoteGenerationLimitModal(false)}
        />
      )}

      {currentPlan === "FREE" ? (
        <PaywallModal
          isOpen={showOcrLimitModal}
          context={{
            type: "OCR_LIMIT",
            noteId: currentNoteId,
          }}
          source="note_editor_ocr_limit"
          resolveContext={() => prepareUpgradeContext("OCR_LIMIT")}
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
