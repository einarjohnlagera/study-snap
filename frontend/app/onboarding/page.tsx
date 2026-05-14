"use client";

import { type ReactNode, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  completeOnboarding,
  createNote,
  createStudyPackFromNote,
  generateNoteFromTopic,
  getMe,
  isNoteGenerationLimitReachedError,
  getNote,
  trackAnalyticsEvent,
  updateLearningProfileContext,
  type LearnerLevel,
  type NoteResponse,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { getSelectionCardClassName } from "@/lib/clickable-card";
import { getPaidPlanCtaLabel } from "@/src/config/plans";
import { mapProfileTypeToNoteTargetProfile } from "@/lib/note-target-profile";
import {
  clearDeferredOnboardingCompletion,
  clearOnboardingDraft,
  createEmptyOnboardingDraft,
  loadOnboardingDraft,
  saveOnboardingDraft,
  setDeferredOnboardingCompletion,
  type OnboardingDraft,
  type OnboardingInputMethod,
  type OnboardingProfileType,
} from "@/lib/onboarding-v2";
import {
  COURSE_PROGRAM_SUGGESTIONS,
  getGroupedLearnerLevels,
} from "@/lib/learning-profile";
import {
  formatStudyPackResetDate,
  isStudyPackLimitReachedMessage,
  resolveRemainingUsageCredits,
} from "@/lib/plans";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";

type GenerationSectionKey = "summary" | "concepts" | "quiz";
type StepName = "profile" | "learning-context" | "input" | "study-pack" | "completion";

const TOPIC_MIN_LENGTH = 3;
const NOTE_CONTENT_MIN_LENGTH = 50;
const DESKTOP_BREAKPOINT_PX = 768;
const STUDY_PACK_GENERATION_POLL_INTERVAL_MS = 2000;
const NOTE_CONTENT_SCROLL_DELAY_MS = 140;
const MAX_CONCEPT_PREVIEW_COUNT = 4;
const STEP_FOUR_ERROR_MESSAGE =
  "Something went wrong generating your Study Pack. Try a shorter topic or check your connection.";
const STEP_FIVE_SAVE_ERROR_MESSAGE =
  "We couldn't save your profile. Your Study Pack is still available.";
const STEP_FOUR_BACK_NOTICE =
  "Going back will start a new Study Pack. Your current one will be saved.";

const PROFILE_OPTIONS: Array<{
  value: OnboardingProfileType;
  icon: string;
  label: string;
  description: string;
}> = [
  {
    value: "STUDENT",
    icon: "🎓",
    label: "Student",
    description: "Reviewing notes and preparing for quizzes",
  },
  {
    value: "BOARD_EXAM",
    icon: "📋",
    label: "Board Taker",
    description: "Preparing for a board or licensure exam",
  },
  {
    value: "TEACHER",
    icon: "🏫",
    label: "Teacher",
    description: "Creating study materials for students",
  },
  {
    value: "PROFESSIONAL",
    icon: "💼",
    label: "Professional",
    description: "Certification prep and applied knowledge review",
  },
];

const STEP_NAMES: Record<number, StepName> = {
  1: "profile",
  2: "learning-context",
  3: "input",
  4: "study-pack",
  5: "completion",
};

const COMPLETION_COPY: Record<OnboardingProfileType, string> = {
  STUDENT: "Your Study Pack is ready. Start practicing when you're ready.",
  BOARD_EXAM: "Your first practice material is ready. Keep going and build toward exam day.",
  TEACHER: "Your Study Pack is ready. You can export a quiz for your students any time.",
  PROFESSIONAL: "Your study material is ready. Start your certification review whenever you're set.",
};

function isOnboardingProfileType(value: string | null | undefined): value is OnboardingProfileType {
  return value === "STUDENT" || value === "BOARD_EXAM" || value === "TEACHER" || value === "PROFESSIONAL";
}

function getStepName(step: number): StepName {
  return STEP_NAMES[step] ?? "profile";
}

function getIsDesktopViewport(): boolean {
  if (globalThis.window === undefined) {
    return true;
  }
  return globalThis.window.innerWidth >= DESKTOP_BREAKPOINT_PX;
}

function countDaysUntilExam(examDate: string): number | null {
  if (!examDate) {
    return null;
  }
  const selectedDate = new Date(`${examDate}T00:00:00`);
  if (Number.isNaN(selectedDate.getTime())) {
    return null;
  }
  const today = new Date();
  const todayUtc = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  const selectedUtc = Date.UTC(
    selectedDate.getFullYear(),
    selectedDate.getMonth(),
    selectedDate.getDate(),
  );
  return Math.round((selectedUtc - todayUtc) / 86400000);
}

function buildContinueStudyHref(note: NoteResponse | null): string {
  if (note?.studyPackId) {
    return `/study-packs/${note.studyPackId}`;
  }
  if (note?.id) {
    return `/notes/${note.id}?tab=summary`;
  }
  return "/dashboard";
}

function ModeOptionButton({
  icon,
  label,
  description,
  selected,
  onClick,
}: Readonly<{
  icon: "✨" | "📝";
  label: string;
  description: string;
  selected: boolean;
  onClick: () => void;
}>) {
  return (
    <button
      type="button"
      className={getSelectionCardClassName({
        selected,
        className: "min-h-24 p-4 sm:min-h-28 sm:p-5",
      })}
      onClick={onClick}
      aria-pressed={selected}
      aria-label={label}
    >
      <div className="space-y-1.5 text-left">
        <div className="text-base font-semibold text-foreground sm:text-lg">{icon} {label}</div>
        <p className="text-sm leading-relaxed text-foreground/70 sm:text-base">{description}</p>
      </div>
    </button>
  );
}

function GenerationSection({
  title,
  sectionKey,
  isDesktop,
  open,
  onToggle,
  children,
}: Readonly<{
  title: string;
  sectionKey: GenerationSectionKey;
  isDesktop: boolean;
  open: boolean;
  onToggle: (key: GenerationSectionKey) => void;
  children: ReactNode;
}>) {
  const content = (
    <div className="pt-3 text-sm text-foreground/80 sm:text-base">
      {children}
    </div>
  );

  if (isDesktop) {
    return (
      <Card className="p-4 sm:p-5">
        <CardTitle className="text-base sm:text-lg">{title}</CardTitle>
        {content}
      </Card>
    );
  }

  return (
    <Card className="p-4">
      <button
        type="button"
        className="flex w-full items-center justify-between gap-3 text-left"
        onClick={() => onToggle(sectionKey)}
        aria-expanded={open}
      >
        <CardTitle className="text-base">{title}</CardTitle>
        <span className="text-sm text-foreground/60">{open ? "Hide" : "Show"}</span>
      </button>
      {open ? content : null}
    </Card>
  );
}

export default function OnboardingPage() {
  const router = useRouter();
  const [draft, setDraft] = useState<OnboardingDraft>(createEmptyOnboardingDraft());
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [isGeneratingNote, setIsGeneratingNote] = useState(false);
  const [startingStudyPack, setStartingStudyPack] = useState(false);
  const [retryingGeneration, setRetryingGeneration] = useState(false);
  const [completingOnboarding, setCompletingOnboarding] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [stepThreeError, setStepThreeError] = useState<string | null>(null);
  const [generationError, setGenerationError] = useState<string | null>(null);
  const [studyPackLimitReached, setStudyPackLimitReached] = useState(false);
  const [completionError, setCompletionError] = useState<string | null>(null);
  const [showNoteGenerationLimitModal, setShowNoteGenerationLimitModal] = useState(false);
  const [isDesktop, setIsDesktop] = useState(true);
  const [previewOpen, setPreviewOpen] = useState<Record<GenerationSectionKey, boolean>>({
    summary: true,
    concepts: true,
    quiz: true,
  });
  const { usageSummary, refreshUsageSummary } = useBillingUsageSummary();
  const generatedNoteSectionRef = useRef<HTMLDivElement | null>(null);
  const [generatedNoteRefreshToken, setGeneratedNoteRefreshToken] = useState(0);

  const startedTrackedRef = useRef(false);
  const completionTrackedRef = useRef(false);
  const generationTrackedRef = useRef<string | null>(null);
  const completionAttemptedRef = useRef(false);
  const shouldTrackAbandonmentRef = useRef(true);
  const userIdRef = useRef<string | null>(null);

  const profileType = draft.profileType;
  const currentStep = draft.currentStep;
  const currentStepName = getStepName(currentStep);
  const groupedLearnerLevels = getGroupedLearnerLevels(profileType);
  const selectedInputMethod = draft.inputMethod;
  const generatedNoteReady = draft.generatedNoteReady;
  const topicLength = draft.topic.trim().length;
  const noteLength = draft.noteContent.trim().length;
  const studyPackStatus = note?.studyPackStatus ?? "DRAFT";
  const currentPlan = usageSummary?.plan ?? (getAuthUser()?.planType ?? "FREE");
  const usageResetDateLabel = formatStudyPackResetDate(usageSummary?.usageCycle?.endsAt);
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
  const hasReachedNoteGenerationLimit = typeof noteGenerationsRemaining === "number" && noteGenerationsRemaining <= 0;
  const noteGenerationRemainingLabel = typeof noteGenerationsRemaining === "number" && noteGenerationsRemaining > 0
    ? `${noteGenerationsRemaining} note generation${noteGenerationsRemaining === 1 ? "" : "s"} left this month.`
    : null;
  const studyPackReady = studyPackStatus === "STUDY_PACK_READY";
  const studyPackGenerating = studyPackStatus === "GENERATING";
  const completionCopy = profileType ? COMPLETION_COPY[profileType] : COMPLETION_COPY.STUDENT;
  const conceptPreview = note?.keyConcepts.slice(0, MAX_CONCEPT_PREVIEW_COUNT) ?? [];
  const extraConceptCount = Math.max((note?.keyConcepts.length ?? 0) - conceptPreview.length, 0);
  const quizPreview = note?.quiz[0] ?? null;

  const canContinueFromStepOne = profileType !== null;
  const canContinueFromStepTwo = true;
  const canGenerateNoteDraft = selectedInputMethod === "generate" && topicLength >= TOPIC_MIN_LENGTH;
  const canStartStudyPack = selectedInputMethod === "generate"
    ? generatedNoteReady && noteLength >= NOTE_CONTENT_MIN_LENGTH
    : selectedInputMethod === "own_note" && noteLength >= NOTE_CONTENT_MIN_LENGTH;
  const stepTransitionKey = currentStep === 3
    ? `step-3-${selectedInputMethod ?? "none"}-${generatedNoteReady ? "generated" : "initial"}`
    : `step-${currentStep}`;
  const stepUsesScrollableShell = currentStep === 3;

  const trackOnboardingEvent = (
    eventType: Parameters<typeof trackAnalyticsEvent>[0]["eventType"],
    metadata?: Record<string, unknown>,
  ) => {
    void trackAnalyticsEvent({
      eventType,
      metadata: metadata ?? {},
    });
  };

  useEffect(() => {
    setIsDesktop(getIsDesktopViewport());
    const handleResize = () => {
      setIsDesktop(getIsDesktopViewport());
    };
    globalThis.window?.addEventListener("resize", handleResize);
    return () => {
      globalThis.window?.removeEventListener("resize", handleResize);
    };
  }, []);

  useEffect(() => {
    setPreviewOpen(
      isDesktop
        ? { summary: true, concepts: true, quiz: true }
        : { summary: true, concepts: false, quiz: false },
    );
  }, [isDesktop]);

  useEffect(() => {
    if (generatedNoteRefreshToken === 0) {
      return;
    }
    const timeoutId = globalThis.setTimeout(() => {
      generatedNoteSectionRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
      const generatedNoteInput = generatedNoteSectionRef.current?.querySelector("textarea");
      if (generatedNoteInput instanceof HTMLTextAreaElement) {
        generatedNoteInput.scrollTop = 0;
      }
    }, NOTE_CONTENT_SCROLL_DELAY_MS);
    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [generatedNoteRefreshToken]);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser) {
      redirectToLoginWithCurrentDestination(router);
      return;
    }
    userIdRef.current = authUser.id;
    if (!authUser.emailVerifiedAt) {
      router.replace("/verify-email");
      return;
    }
    if (authUser.onboardingCompletedAt) {
      router.replace("/dashboard");
      return;
    }

    let cancelled = false;
    void getMe()
      .then(async (me) => {
        if (cancelled) {
          return;
        }
        if (me.onboardingCompletedAt) {
          router.replace("/dashboard");
          return;
        }

        const savedDraft = loadOnboardingDraft(authUser.id);
        const nextDraft = savedDraft ?? createEmptyOnboardingDraft();
        if (!nextDraft.profileType && isOnboardingProfileType(me.profileType)) {
          nextDraft.profileType = me.profileType;
        }
        if (!nextDraft.examDate && me.examDate) {
          nextDraft.examDate = me.examDate;
        }
        if (nextDraft.currentStep > 3 && !nextDraft.noteId) {
          nextDraft.currentStep = 3;
        }
        if (
          nextDraft.inputMethod === "generate"
          && nextDraft.noteContent.trim().length > 0
          && !nextDraft.generatedNoteReady
          && !nextDraft.noteId
        ) {
          nextDraft.generatedNoteReady = true;
        }
        setDraft(nextDraft);

        if (!startedTrackedRef.current) {
          startedTrackedRef.current = true;
          trackOnboardingEvent("ONBOARDING_V2_STARTED", {
            source: savedDraft ? "resuming" : "signup",
          });
        }

        if (!nextDraft.noteId) {
          return;
        }

        const loadedNote = await getNote(nextDraft.noteId).catch(() => null);
        if (!loadedNote || cancelled) {
          return;
        }
        setNote(loadedNote);
        if (loadedNote.studyPackStatus === "FAILED") {
          setGenerationError(STEP_FOUR_ERROR_MESSAGE);
        }
        setDraft((previous) => ({
          ...previous,
          studyPackId: loadedNote.studyPackId ?? previous.studyPackId,
          currentStep: previous.currentStep >= 5 ? 5 : 4,
        }));
      })
      .catch((error) => {
        if (cancelled) {
          return;
        }
        setLoadError(error instanceof Error ? error.message : "Could not load onboarding.");
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [router]);

  useEffect(() => {
    const userId = userIdRef.current;
    if (!userId || loading) {
      return;
    }
    saveOnboardingDraft(userId, draft);
  }, [draft, loading]);

  useEffect(() => {
    if (loading) {
      return;
    }
    trackOnboardingEvent("ONBOARDING_V2_STEP_VIEWED", {
      step: currentStep,
      step_name: currentStepName,
    });
  }, [currentStep, currentStepName, loading]);

  useEffect(() => {
    return () => {
      if (!shouldTrackAbandonmentRef.current) {
        return;
      }
      trackOnboardingEvent("ONBOARDING_V2_ABANDONED", {
        last_step: draft.currentStep,
      });
    };
  }, [draft.currentStep]);

  useEffect(() => {
    if (!note?.id || !studyPackGenerating) {
      return;
    }

    let active = true;
    const intervalId = globalThis.setInterval(() => {
      void getNote(note.id)
        .then((loadedNote) => {
          if (!active) {
            return;
          }
          setNote(loadedNote);
          setDraft((previous) => ({
            ...previous,
            noteId: loadedNote.id,
            studyPackId: loadedNote.studyPackId ?? previous.studyPackId,
          }));
          if (
            loadedNote.studyPackStatus === "STUDY_PACK_READY"
            && generationTrackedRef.current !== loadedNote.id
          ) {
            generationTrackedRef.current = loadedNote.id;
            trackOnboardingEvent("ONBOARDING_V2_STUDY_PACK_GENERATED", {
              method: draft.inputMethod,
              summary_length: loadedNote.summary?.length ?? 0,
              concepts_count: loadedNote.keyConcepts.length,
              quiz_questions_count: loadedNote.quiz.length,
            });
          }
          if (loadedNote.studyPackStatus === "FAILED") {
            setGenerationError(STEP_FOUR_ERROR_MESSAGE);
          }
        })
        .catch(() => {
          // Keep polling. A later request can recover without breaking the flow.
        });
    }, STUDY_PACK_GENERATION_POLL_INTERVAL_MS);

    return () => {
      active = false;
      globalThis.clearInterval(intervalId);
    };
  }, [draft.inputMethod, note?.id, studyPackGenerating]);

  useEffect(() => {
    if (currentStep !== 5 || completionAttemptedRef.current || !profileType) {
      return;
    }

    completionAttemptedRef.current = true;
    setCompletingOnboarding(true);
    setCompletionError(null);

    void completeOnboarding({
      profileType,
      examDate: profileType === "BOARD_EXAM" && draft.examDate ? draft.examDate : null,
    })
      .then((me) => {
        const authUser = getAuthUser();
        if (authUser) {
          clearDeferredOnboardingCompletion(authUser.id);
          setAuthUser({
            ...authUser,
            displayName: me.displayName,
            profileType: me.profileType,
            emailVerifiedAt: me.emailVerifiedAt,
            onboardingCompletedAt: me.onboardingCompletedAt,
            productOnboardingCompletedAt: me.productOnboardingCompletedAt,
          });
        }
        if (draft.learnerLevel !== null || draft.courseProgram.trim() !== "") {
          void updateLearningProfileContext(draft.learnerLevel, draft.courseProgram.trim() || null)
            .catch(() => {
              // Learning context can be adjusted later in profile settings.
            });
        }
      })
      .catch(() => {
        const userId = userIdRef.current;
        if (userId) {
          setDeferredOnboardingCompletion(userId);
        }
        setCompletionError(STEP_FIVE_SAVE_ERROR_MESSAGE);
      })
      .finally(() => {
        if (!completionTrackedRef.current) {
          completionTrackedRef.current = true;
          shouldTrackAbandonmentRef.current = false;
          trackOnboardingEvent("ONBOARDING_V2_COMPLETED", {
            profile_type: profileType,
            learner_level: draft.learnerLevel ?? null,
            course_program: draft.courseProgram.trim() || null,
            method: draft.inputMethod,
            time_elapsed_seconds: Math.max(0, Math.round((Date.now() - draft.startedAtMs) / 1000)),
          });
        }
        setCompletingOnboarding(false);
      });
  }, [
    currentStep,
    draft.courseProgram,
    draft.examDate,
    draft.inputMethod,
    draft.learnerLevel,
    draft.startedAtMs,
    profileType,
  ]);

  const selectProfileType = (value: OnboardingProfileType) => {
    setDraft((previous) => ({
      ...previous,
      profileType: value,
      examDate: value === "BOARD_EXAM" ? previous.examDate : "",
    }));
    trackOnboardingEvent("ONBOARDING_V2_PROFILE_SELECTED", {
      profile_type: value,
    });
  };

  const updateCourseProgram = (value: string) => {
    setDraft((previous) => ({
      ...previous,
      courseProgram: value,
    }));
  };

  const skipLearningContext = () => {
    setDraft((previous) => ({
      ...previous,
      learnerLevel: null,
      courseProgram: "",
      currentStep: 3,
    }));
  };

  const selectInputMethod = (value: OnboardingInputMethod) => {
    setStepThreeError(null);
    setDraft((previous) => ({
      ...previous,
      inputMethod: value,
    }));
    trackOnboardingEvent("ONBOARDING_V2_INPUT_METHOD_SELECTED", {
      method: value,
      profile_type: profileType,
    });
  };

  const goToStep = (nextStep: number) => {
    setDraft((previous) => ({
      ...previous,
      currentStep: nextStep,
    }));
  };

  const handleBack = () => {
    if (currentStep <= 1) {
      return;
    }

    trackOnboardingEvent("ONBOARDING_V2_BACK_NAVIGATED", {
      from_step: currentStep,
    });

    if (currentStep === 4) {
      setGenerationError(null);
      setNote(null);
      generationTrackedRef.current = null;
      setDraft((previous) => ({
        ...previous,
        currentStep: 3,
        noteId: null,
        studyPackId: null,
      }));
      return;
    }

    goToStep(currentStep - 1);
  };

  const handleGenerateNoteDraft = async () => {
    if (!canGenerateNoteDraft || isGeneratingNote || generatedNoteReady) {
      return;
    }
    if (hasReachedNoteGenerationLimit) {
      setShowNoteGenerationLimitModal(true);
      return;
    }

    setIsGeneratingNote(true);
    setStepThreeError(null);
    setGenerationError(null);
    trackOnboardingEvent("ONBOARDING_V2_TOPIC_SUBMITTED", {
      topic_length: topicLength,
    });

    try {
      const generated = await generateNoteFromTopic(draft.topic.trim());
      setNote(null);
      setDraft((previous) => ({
        ...previous,
        noteContent: generated.content,
        generatedNoteReady: true,
        noteId: null,
        studyPackId: null,
      }));
      setGeneratedNoteRefreshToken((previous) => previous + 1);
      void refreshUsageSummary();
    } catch (error) {
      if (isNoteGenerationLimitReachedError(error)) {
        await refreshUsageSummary();
        setShowNoteGenerationLimitModal(true);
      } else {
        setStepThreeError(
          error instanceof Error ? error.message : "We could not generate a note right now. Please try again.",
        );
      }
    } finally {
      setIsGeneratingNote(false);
    }
  };

  const handleStartStudyPack = async () => {
    if (!profileType || !selectedInputMethod || startingStudyPack || !canStartStudyPack) {
      return;
    }
    if (draft.noteId) {
      goToStep(4);
      return;
    }

    setStartingStudyPack(true);
    setStepThreeError(null);
    setGenerationError(null);
    setStudyPackLimitReached(false);

    if (selectedInputMethod === "own_note") {
      trackOnboardingEvent("ONBOARDING_V2_OWN_NOTE_SUBMITTED", {
        note_length: noteLength,
      });
    }

    let savedNote: NoteResponse | null = null;
    try {
      const createdNote = await createNote({
        title: selectedInputMethod === "generate" ? draft.topic.trim() : null,
        targetProfileType: mapProfileTypeToNoteTargetProfile(profileType),
        content: draft.noteContent,
      });
      savedNote = createdNote;
      setNote(createdNote);
      setDraft((previous) => ({
        ...previous,
        currentStep: 4,
        noteId: createdNote.id,
        studyPackId: createdNote.studyPackId ?? null,
      }));

      const queuedNote = await createStudyPackFromNote(createdNote.id, { autoApplyMetadata: true });
      setNote(queuedNote);
      setDraft((previous) => ({
        ...previous,
        currentStep: 4,
        noteId: queuedNote.id,
        studyPackId: queuedNote.studyPackId ?? previous.studyPackId,
      }));
    } catch (error) {
      const message = error instanceof Error ? error.message : STEP_FOUR_ERROR_MESSAGE;
      if (!savedNote) {
        setStepThreeError(message);
      } else if (isStudyPackLimitReachedMessage(message)) {
        setStudyPackLimitReached(true);
        setGenerationError(null);
        setDraft((previous) => ({
          ...previous,
          currentStep: 5,
        }));
        trackOnboardingEvent("ONBOARDING_V2_STUDY_PACK_ERROR", {
          method: selectedInputMethod,
          error_type: "study_pack_limit_reached",
        });
      } else {
        setGenerationError(STEP_FOUR_ERROR_MESSAGE);
        trackOnboardingEvent("ONBOARDING_V2_STUDY_PACK_ERROR", {
          method: selectedInputMethod,
          error_type: message,
        });
      }
    } finally {
      setStartingStudyPack(false);
    }
  };

  const handleRetryGeneration = async () => {
    if (!note?.id || retryingGeneration) {
      return;
    }

    setRetryingGeneration(true);
    setGenerationError(null);
    setStudyPackLimitReached(false);
    generationTrackedRef.current = null;
    try {
      const queuedNote = await createStudyPackFromNote(note.id, { autoApplyMetadata: true });
      setNote(queuedNote);
      setDraft((previous) => ({
        ...previous,
        currentStep: 4,
        noteId: queuedNote.id,
        studyPackId: queuedNote.studyPackId ?? previous.studyPackId,
      }));
    } catch (error) {
      const message = error instanceof Error ? error.message : STEP_FOUR_ERROR_MESSAGE;
      if (isStudyPackLimitReachedMessage(message)) {
        setStudyPackLimitReached(true);
        setGenerationError(null);
        setDraft((previous) => ({
          ...previous,
          currentStep: 5,
        }));
        trackOnboardingEvent("ONBOARDING_V2_STUDY_PACK_ERROR", {
          method: draft.inputMethod,
          error_type: "study_pack_limit_reached",
        });
        return;
      }
      setGenerationError(STEP_FOUR_ERROR_MESSAGE);
      trackOnboardingEvent("ONBOARDING_V2_STUDY_PACK_ERROR", {
        method: draft.inputMethod,
        error_type: message,
      });
    } finally {
      setRetryingGeneration(false);
    }
  };

  const handleContinueToStudyPack = () => {
    shouldTrackAbandonmentRef.current = false;
    if (userIdRef.current) {
      clearOnboardingDraft(userIdRef.current);
    }
    trackOnboardingEvent("ONBOARDING_V2_CTA_CONTINUE_STUDYING");
    router.push(buildContinueStudyHref(note));
  };

  const handleGoToDashboard = () => {
    shouldTrackAbandonmentRef.current = false;
    if (userIdRef.current) {
      clearOnboardingDraft(userIdRef.current);
    }
    trackOnboardingEvent("ONBOARDING_V2_CTA_GO_TO_DASHBOARD");
    router.push("/dashboard");
  };

  const handleGoToSavedNote = () => {
    shouldTrackAbandonmentRef.current = false;
    if (userIdRef.current) {
      clearOnboardingDraft(userIdRef.current);
    }
    trackOnboardingEvent("ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE");
    router.push(note?.id ? `/notes/${note.id}` : "/library");
  };

  const togglePreviewSection = (sectionKey: GenerationSectionKey) => {
    setPreviewOpen((previous) => ({
      ...previous,
      [sectionKey]: !previous[sectionKey],
    }));
  };

  const renderStepContent = () => {
    if (currentStep === 1) {
      return (
        <div className="mx-auto flex w-full max-w-[560px] flex-col space-y-5">
          <div className="space-y-2 text-center sm:text-left">
            <CardTitle className="text-2xl leading-tight sm:text-3xl">
              Welcome to NoteLib. Let&apos;s set things up.
            </CardTitle>
            <CardDescription className="text-sm">
              Select the profile that best matches how you&apos;ll use NoteLib.
            </CardDescription>
          </div>

          <div className="grid gap-2.5 sm:grid-cols-2">
            {PROFILE_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={getSelectionCardClassName({
                  selected: profileType === option.value,
                  className: "p-4 sm:p-5",
                })}
                onClick={() => selectProfileType(option.value)}
                aria-pressed={profileType === option.value}
                aria-label={option.label}
              >
                <div className="space-y-1 text-left">
                  <div className="text-base font-semibold text-foreground sm:text-lg">{option.icon} {option.label}</div>
                  <p className="text-sm text-foreground/70 sm:text-base">{option.description}</p>
                </div>
              </button>
            ))}
          </div>
        </div>
      );
    }

    if (currentStep === 2) {
      return (
        <div className="mx-auto flex w-full max-w-[560px] flex-col space-y-5">
          <div className="space-y-2 text-center sm:text-left">
            <CardTitle className="text-2xl leading-tight sm:text-3xl">
              Set up your learning profile
            </CardTitle>
            <CardDescription className="text-sm">
              This helps us tailor quizzes and explanations to your level and field. You can update these anytime in Settings.
            </CardDescription>
          </div>

          <div className="space-y-5">
            <section className="space-y-2">
              <div className="space-y-1">
                <p className="text-sm font-medium text-foreground">Learner Level</p>
                <p className="text-xs text-foreground/60">Optional. Choose the level that best fits this study material.</p>
              </div>
              <select
                value={draft.learnerLevel ?? ""}
                onChange={(event) => {
                  const nextValue = event.target.value;
                  setDraft((previous) => ({
                    ...previous,
                    learnerLevel: nextValue ? nextValue as LearnerLevel : null,
                  }));
                }}
                className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                aria-label="Learner Level"
              >
                <option value="">Choose your learner level</option>
                <optgroup label={groupedLearnerLevels.recommendedGroupLabel}>
                  {groupedLearnerLevels.recommended.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </optgroup>
                {groupedLearnerLevels.other.length > 0 ? (
                  <optgroup label="Other options">
                    {groupedLearnerLevels.other.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </optgroup>
                ) : null}
              </select>
            </section>

            <section className="space-y-2">
              <p className="text-sm font-medium text-foreground">Course / Program</p>
              <CourseProgramCombobox
                id="onboarding-course-program"
                value={draft.courseProgram}
                suggestions={COURSE_PROGRAM_SUGGESTIONS}
                onChange={updateCourseProgram}
                learnerLevel={draft.learnerLevel}
                ariaLabel="Course / Program"
                placeholder="Choose or type your course / program"
                context="onboarding"
              />
            </section>
          </div>

          {profileType === "BOARD_EXAM" ? (
            <label className="block space-y-2">
              <span className="text-sm font-medium text-foreground">When is your exam? (optional)</span>
              <input
                type="date"
                value={draft.examDate}
                onChange={(event) => {
                  const nextValue = event.target.value;
                  setDraft((previous) => ({
                    ...previous,
                    examDate: nextValue,
                  }));
                  if (nextValue) {
                    trackOnboardingEvent("ONBOARDING_V2_EXAM_DATE_SET", {
                      days_until_exam: countDaysUntilExam(nextValue),
                    });
                  }
                }}
                className="min-h-11 w-full rounded-xl border border-border bg-background px-4 py-2.5 text-base text-foreground outline-none ring-0 transition-colors focus:border-blue-500"
                aria-label="When is your exam? (optional)"
              />
            </label>
          ) : null}

          <button
            type="button"
            className="w-fit text-sm font-medium text-foreground/65 underline-offset-4 hover:text-foreground hover:underline"
            onClick={skipLearningContext}
          >
            Skip for now
          </button>
        </div>
      );
    }

    if (currentStep === 3) {
      return (
        <div className="mx-auto flex w-full max-w-[560px] flex-col space-y-5">
          <div className="space-y-2 text-center sm:text-left">
            <CardTitle className="text-2xl leading-tight sm:text-3xl">
              How do you want to start?
            </CardTitle>
            <CardDescription className="text-sm">
              Pick one path, then continue from your note into a Study Pack.
            </CardDescription>
          </div>

          <div className="grid gap-2.5 sm:grid-cols-2">
            <ModeOptionButton
              icon="✨"
              label="Generate a note"
              description="Start with a topic, review the draft, then generate your Study Pack."
              selected={selectedInputMethod === "generate"}
              onClick={() => selectInputMethod("generate")}
            />
            <ModeOptionButton
              icon="📝"
              label="Write or paste my own note"
              description="Write or paste your material first, then generate your Study Pack."
              selected={selectedInputMethod === "own_note"}
              onClick={() => selectInputMethod("own_note")}
            />
          </div>

          {selectedInputMethod === "generate" ? (
            <div className="motion-onboarding-step space-y-3.5 rounded-2xl border border-border bg-surface-alt/80 p-4">
              <label className="block space-y-2">
                <span className="text-sm font-medium text-foreground">Topic</span>
                <input
                  type="text"
                  value={draft.topic}
                  onChange={(event) => {
                    setDraft((previous) => ({
                      ...previous,
                      topic: event.target.value,
                    }));
                    setStepThreeError(null);
                  }}
                  placeholder="Create a note about Newton’s Laws of Motion..."
                  className="min-h-11 w-full rounded-xl border border-border bg-background px-4 py-2.5 text-base text-foreground outline-none ring-0 transition-colors focus:border-blue-500"
                />
              </label>

              {hasReachedNoteGenerationLimit ? (
                currentPlan === "FREE" ? (
                  <div className="flex flex-col gap-2 rounded-xl border border-amber-300/70 bg-amber-50/80 p-3 text-sm text-amber-950 dark:border-amber-800/70 dark:bg-amber-950/30 dark:text-amber-100">
                    <p>You&apos;ve reached your topic note generation limit for this month.</p>
                    <Button
                      type="button"
                      variant="outline"
                      className="w-full sm:w-fit"
                      onClick={() => setShowNoteGenerationLimitModal(true)}
                    >
                      {getPaidPlanCtaLabel("PLUS")}
                    </Button>
                  </div>
                ) : (
                  <div className="rounded-xl border border-border/80 bg-muted/30 p-3 text-sm text-foreground/70">
                    You&apos;ve reached your topic note generation limit for this billing cycle. Continue with Pro if you want more room to generate note drafts this month.
                  </div>
                )
              ) : noteGenerationRemainingLabel ? (
                <p className="text-sm text-foreground/60">{noteGenerationRemainingLabel}</p>
              ) : null}

              {generatedNoteReady ? (
                <div
                  key={`onboarding-generated-note-${generatedNoteRefreshToken}`}
                  ref={generatedNoteSectionRef}
                  className={generatedNoteRefreshToken > 0 ? "motion-note-content-enter" : undefined}
                >
                  <label className="block space-y-2">
                    <span className="text-sm font-medium text-foreground">Generated note</span>
                    <textarea
                      value={draft.noteContent}
                      onChange={(event) => {
                        setDraft((previous) => ({
                          ...previous,
                          noteContent: event.target.value,
                        }));
                        setStepThreeError(null);
                      }}
                      rows={8}
                      className="min-h-[180px] w-full rounded-xl border border-border bg-background px-4 py-3 text-base text-foreground outline-none ring-0 transition-colors focus:border-blue-500 md:min-h-[210px]"
                      placeholder="Your generated note will appear here."
                    />
                    <div className="space-y-1">
                      <p className="text-sm text-foreground/60">{noteLength} / 50 minimum</p>
                      <p className="text-sm text-foreground/55">
                        Your note is ready. You can edit it before generating your Study Pack.
                      </p>
                    </div>
                  </label>
                </div>
              ) : (
                <p className="rounded-xl border border-dashed border-border px-4 py-4 text-sm text-foreground/70 sm:text-base">
                  Generate a note first, then review and edit it before creating your Study Pack.
                </p>
              )}
            </div>
          ) : null}

          {selectedInputMethod === "own_note" ? (
            <div className="motion-onboarding-step space-y-3.5 rounded-2xl border border-border bg-surface-alt/80 p-4">
              <label className="block space-y-2">
                <span className="text-sm font-medium text-foreground">Your note</span>
                <textarea
                  value={draft.noteContent}
                  onChange={(event) => {
                    setDraft((previous) => ({
                      ...previous,
                      noteContent: event.target.value,
                    }));
                    setStepThreeError(null);
                  }}
                  rows={8}
                  className="min-h-[180px] w-full rounded-xl border border-border bg-background px-4 py-3 text-base text-foreground outline-none ring-0 transition-colors focus:border-blue-500 md:min-h-[210px]"
                  placeholder="Paste or write your notes here..."
                />
                <p className="text-sm text-foreground/60">{noteLength} / 50 minimum</p>
              </label>
            </div>
          ) : null}

          {stepThreeError ? <p className="text-sm text-red-600 dark:text-red-400">{stepThreeError}</p> : null}
        </div>
      );
    }

    if (currentStep === 4) {
      return (
        <div className="mx-auto flex w-full max-w-[560px] flex-col space-y-4">
          <div className="space-y-2 text-center sm:text-left">
            <CardTitle className="text-2xl leading-tight sm:text-3xl">
              {studyPackReady ? "Your Study Pack is ready." : "Building your Study Pack..."}
            </CardTitle>
            <CardDescription className="text-sm">
              {studyPackReady
                ? "Preview what NoteLib generated before you continue."
                : "We’re turning your note into a complete study flow."}
            </CardDescription>
          </div>

          <p className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-2.5 text-sm text-blue-900 dark:border-blue-900/50 dark:bg-blue-950/30 dark:text-blue-100">
            {studyPackGenerating || startingStudyPack
              ? "Your Study Pack is being created. This step can't be undone."
              : STEP_FOUR_BACK_NOTICE}
          </p>

          {generationError ? (
            <div className="space-y-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/20 dark:text-red-200">
              <p>{generationError}</p>
              <Button
                type="button"
                variant="outline"
                onClick={() => void handleRetryGeneration()}
                loading={retryingGeneration}
                loadingText="Retrying..."
              >
                Retry
              </Button>
            </div>
          ) : null}

          {studyPackGenerating ? (
            <Card className="p-4">
              <p className="text-sm text-foreground/75 sm:text-base">
                Your Study Pack is generating. This usually takes a few moments.
              </p>
            </Card>
          ) : null}

          {studyPackReady ? (
            <div className="grid gap-3">
              <GenerationSection
                title="Summary"
                sectionKey="summary"
                isDesktop={isDesktop}
                open={previewOpen.summary}
                onToggle={togglePreviewSection}
              >
                <p>{note?.summary ?? "Summary preview will be available when you open your Study Pack."}</p>
              </GenerationSection>

              <GenerationSection
                title="Key Concepts"
                sectionKey="concepts"
                isDesktop={isDesktop}
                open={previewOpen.concepts}
                onToggle={togglePreviewSection}
              >
                {conceptPreview.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {conceptPreview.map((concept) => (
                      <span
                        key={concept}
                        className="rounded-full border border-border bg-background px-3 py-2 text-sm text-foreground"
                      >
                        {concept}
                      </span>
                    ))}
                    {extraConceptCount > 0 ? (
                      <span className="rounded-full border border-border bg-background px-3 py-2 text-sm text-foreground/70">
                        +{extraConceptCount} more
                      </span>
                    ) : null}
                  </div>
                ) : (
                  <p>No key concepts found</p>
                )}
              </GenerationSection>

              <GenerationSection
                title="Quiz Preview"
                sectionKey="quiz"
                isDesktop={isDesktop}
                open={previewOpen.quiz}
                onToggle={togglePreviewSection}
              >
                {quizPreview ? (
                  <div className="space-y-3">
                    <p className="font-medium text-foreground">{quizPreview.question}</p>
                    {quizPreview.choices.length > 0 ? (
                      <ul className="space-y-2">
                        {quizPreview.choices.map((choice, index) => (
                          <li
                            key={`${quizPreview.question}-${choice}-${index}`}
                            className="rounded-xl border border-border bg-background px-3 py-2"
                          >
                            {choice}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </div>
                ) : (
                  <p>Quiz questions will be available when you open your Study Pack.</p>
                )}
              </GenerationSection>
            </div>
          ) : null}
        </div>
      );
    }

    if (studyPackLimitReached) {
      return (
        <div className="mx-auto flex w-full max-w-[560px] flex-col space-y-5">
          <div className="space-y-2 text-center sm:text-left">
            <CardTitle className="text-2xl leading-tight sm:text-3xl">
              Your note is saved.
            </CardTitle>
            <CardDescription className="text-sm">
              Create ✓ → Understand ○ → Practice → Challenge → Improve
            </CardDescription>
          </div>

          <NearLimitBanner
            planType={currentPlan}
            remainingCredits={0}
            resetDateLabel={usageResetDateLabel}
          />

          <p className="text-[0.95rem] text-foreground/80 sm:text-base">
            We saved your note in your Library. You can generate the Study Pack when your limit
            resets on {usageResetDateLabel}, or upgrade your plan to continue without waiting.
          </p>

          <div className="flex flex-col gap-2.5">
            <Button type="button" className="min-h-12 text-base" onClick={handleGoToSavedNote}>
              Go to My Note
            </Button>
            <Button type="button" variant="outline" className="min-h-12 text-base" onClick={handleGoToDashboard}>
              Go to Dashboard
            </Button>
          </div>

          {completingOnboarding ? (
            <p className="text-sm text-foreground/60">Saving your profile...</p>
          ) : null}
          {completionError ? <p className="text-sm text-red-600 dark:text-red-400">{completionError}</p> : null}
        </div>
      );
    }

    return (
      <div className="mx-auto flex w-full max-w-[560px] flex-col space-y-5">
        <div className="space-y-2 text-center sm:text-left">
          <CardTitle className="text-2xl leading-tight sm:text-3xl">
            You just started your study loop.
          </CardTitle>
          <CardDescription className="text-sm">
            Create ✓ → Understand ● → Practice → Challenge → Improve
          </CardDescription>
        </div>

        <p className="text-[0.95rem] text-foreground/80 sm:text-base">{completionCopy}</p>

        <div className="flex flex-col gap-2.5">
          <Button type="button" className="min-h-12 text-base" onClick={handleContinueToStudyPack}>
            Continue Studying
          </Button>
          <Button type="button" variant="outline" className="min-h-12 text-base" onClick={handleGoToDashboard}>
            Go to Dashboard
          </Button>
        </div>

        {completingOnboarding ? (
          <p className="text-sm text-foreground/60">Saving your profile...</p>
        ) : null}
        {completionError ? <p className="text-sm text-red-600 dark:text-red-400">{completionError}</p> : null}
      </div>
    );
  };

  const renderFooterActions = () => {
    if (currentStep === 5) {
      return null;
    }

    if (currentStep === 1) {
      return (
        <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
          <Button
            type="button"
            className="min-h-12 text-base sm:min-w-40"
            onClick={() => goToStep(2)}
            disabled={!canContinueFromStepOne}
          >
            Continue
          </Button>
        </div>
      );
    }

    if (currentStep === 2) {
      return (
        <div className="flex flex-col gap-3 sm:flex-row sm:justify-between">
          <Button type="button" variant="outline" className="min-h-12 text-base sm:min-w-32" onClick={handleBack}>
            Back
          </Button>
          <Button
            type="button"
            className="min-h-12 text-base sm:min-w-40"
            onClick={() => goToStep(3)}
            disabled={!canContinueFromStepTwo}
          >
            Continue
          </Button>
        </div>
      );
    }

    if (currentStep === 3) {
      if (selectedInputMethod === "generate" && !generatedNoteReady) {
        return (
          <div className="flex flex-col gap-3 sm:flex-row sm:justify-between">
            <Button type="button" variant="outline" className="min-h-12 text-base sm:min-w-32" onClick={handleBack}>
              Back
            </Button>
            <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
              <Button
                type="button"
                variant="outline"
                className="min-h-12 text-base sm:min-w-48"
                disabled
              >
                Generate Study Pack →
              </Button>
              <Button
                type="button"
                className="min-h-12 text-base sm:min-w-40"
                onClick={() => void handleGenerateNoteDraft()}
                disabled={!canGenerateNoteDraft}
                loading={isGeneratingNote}
                loadingText="Generating..."
              >
                Generate Note
              </Button>
            </div>
          </div>
        );
      }

      if (selectedInputMethod === "generate" && generatedNoteReady) {
        return (
          <div className="flex flex-col gap-3 sm:flex-row sm:justify-between">
            <Button type="button" variant="outline" className="min-h-12 text-base sm:min-w-32" onClick={handleBack}>
              Back
            </Button>
            <Button
              type="button"
              className="min-h-12 text-base sm:min-w-48"
              onClick={() => void handleStartStudyPack()}
              disabled={!canStartStudyPack}
              loading={startingStudyPack}
              loadingText="Starting..."
            >
              Generate Study Pack →
            </Button>
          </div>
        );
      }

      return (
        <div className="flex flex-col gap-3 sm:flex-row sm:justify-between">
          <Button type="button" variant="outline" className="min-h-12 text-base sm:min-w-32" onClick={handleBack}>
            Back
          </Button>
          <Button
            type="button"
            className="min-h-12 text-base sm:min-w-48"
            onClick={() => void handleStartStudyPack()}
            disabled={!canStartStudyPack}
            loading={startingStudyPack}
            loadingText="Starting..."
          >
            Generate Study Pack →
          </Button>
        </div>
      );
    }

    if (studyPackReady) {
      return (
        <div className="flex flex-col gap-3 sm:flex-row sm:justify-between">
          <Button type="button" variant="outline" className="min-h-12 text-base sm:min-w-32" onClick={handleBack}>
            Back
          </Button>
          <Button type="button" className="min-h-12 text-base sm:min-w-40" onClick={() => goToStep(5)}>
            Continue
          </Button>
        </div>
      );
    }

    if (generationError && note?.id) {
      return (
        <div className="flex flex-col gap-3 sm:flex-row sm:justify-between">
          <Button type="button" variant="outline" className="min-h-12 text-base sm:min-w-32" onClick={handleBack}>
            Back
          </Button>
          <Button
            type="button"
            className="min-h-12 text-base sm:min-w-40"
            onClick={() => void handleRetryGeneration()}
            loading={retryingGeneration}
            loadingText="Retrying..."
          >
            Retry
          </Button>
        </div>
      );
    }

    return (
      <div className="flex justify-end">
        <Button type="button" className="min-h-12 text-base sm:min-w-44" disabled>
          {startingStudyPack ? "Starting..." : "Building your Study Pack..."}
        </Button>
      </div>
    );
  };

  const renderCardShell = (content: ReactNode, footer: ReactNode | null) => (
    <main className="mx-auto flex min-h-[calc(100dvh-4rem)] w-full max-w-[620px] flex-col justify-center px-4 py-4 sm:px-5 sm:py-5">
      <div
        className={`flex w-full flex-col rounded-[24px] border border-border bg-background shadow-[0_16px_48px_rgba(15,23,42,0.08)] ${
          stepUsesScrollableShell ? "h-[calc(100dvh-6rem)] max-h-[720px] overflow-hidden" : "overflow-visible"
        }`}
      >
        <div className="border-b border-border px-5 py-4 sm:px-6 sm:py-4">
          <div className="space-y-2.5">
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-foreground/50">
              Step {currentStep} of 5
            </p>
            <div className="h-1.5 overflow-hidden rounded-full bg-foreground/10">
              <div
                className="motion-progress-bar h-full rounded-full bg-primary transition-[width] duration-200 ease-out"
                style={{ width: `${(currentStep / 5) * 100}%` }}
              />
            </div>
          </div>
        </div>

        <div
          className={`px-5 py-4 sm:px-6 sm:py-5 ${
            stepUsesScrollableShell ? "min-h-0 flex-1 overflow-y-auto" : "overflow-visible"
          }`}
        >
          <div key={stepTransitionKey} className="motion-onboarding-step">
            {content}
          </div>
        </div>

        {footer ? (
          <div className="border-t border-border bg-background/95 px-5 py-4 backdrop-blur supports-[backdrop-filter]:bg-background/90 sm:px-6 sm:py-4">
            {footer}
          </div>
        ) : null}
      </div>
    </main>
  );

  if (loading) {
    return renderCardShell(
      <div className="mx-auto flex w-full max-w-[560px] flex-col space-y-4">
        <div className="h-8 w-52 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-40 w-full animate-pulse rounded-2xl bg-foreground/10" />
      </div>,
      null,
    );
  }

  if (loadError) {
    return renderCardShell(
      <div className="mx-auto flex w-full max-w-[560px] flex-col space-y-4">
        <CardTitle>Could not load onboarding</CardTitle>
        <CardDescription>{loadError}</CardDescription>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Button type="button" variant="outline" className="min-h-12 text-base" onClick={() => router.refresh()}>
            Retry
          </Button>
        </div>
      </div>,
      null,
    );
  }

  return (
    <>
      {renderCardShell(renderStepContent(), renderFooterActions())}
      {currentPlan === "PRO" ? (
        <AppModal
          isOpen={showNoteGenerationLimitModal}
          title="Note generation limit reached"
          description="You’ve reached your topic-based note generation limit for this billing cycle. Your limits will reset on your next billing date."
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
          context={{ type: "GENERATE_NOTE_LIMIT" }}
          source="onboarding_note_generation_limit"
          onClose={() => setShowNoteGenerationLimitModal(false)}
        />
      )}
    </>
  );
}
