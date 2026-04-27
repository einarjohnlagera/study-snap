"use client";

import { type ReactNode, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  completeOnboarding,
  createNote,
  createStudyPackFromNote,
  generateNoteFromTopic,
  getMe,
  getNote,
  trackAnalyticsEvent,
  type NoteResponse,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { getSelectionCardClassName } from "@/lib/clickable-card";
import { mapProfileTypeToNoteTargetProfile } from "@/lib/note-target-profile";
import {
  clearDeferredOnboardingCompletion,
  clearOnboardingDraft,
  createEmptyOnboardingDraft,
  loadOnboardingDraft,
  saveOnboardingDraft,
  setDeferredOnboardingCompletion,
  type OnboardingDraft,
  type OnboardingGoal,
  type OnboardingInputMethod,
  type OnboardingProfileType,
} from "@/lib/onboarding-v2";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";

type GenerationSectionKey = "summary" | "concepts" | "quiz";
type StepName = "profile" | "goal" | "input" | "study-pack" | "completion";

const TOPIC_MIN_LENGTH = 3;
const NOTE_CONTENT_MIN_LENGTH = 50;
const DESKTOP_BREAKPOINT_PX = 768;
const STUDY_PACK_GENERATION_POLL_INTERVAL_MS = 2000;
const MAX_CONCEPT_PREVIEW_COUNT = 4;
const STEP_FOUR_ERROR_MESSAGE =
  "Something went wrong generating your Study Pack. Try a shorter topic or check your connection.";
const STEP_FIVE_SAVE_ERROR_MESSAGE =
  "We couldn't save your profile. Your Study Pack is still available.";
const STEP_FOUR_BACK_NOTICE =
  "Going back will start a new Study Pack. Your current one will be saved.";

const PROFILE_OPTIONS: Array<{
  value: OnboardingProfileType;
  label: string;
  description: string;
}> = [
  {
    value: "STUDENT",
    label: "Student",
    description: "Reviewing notes and preparing for quizzes",
  },
  {
    value: "BOARD_EXAM",
    label: "Board Taker",
    description: "Preparing for a board or licensure exam",
  },
  {
    value: "TEACHER",
    label: "Teacher",
    description: "Creating study materials for students",
  },
];

const GOAL_OPTIONS: Record<OnboardingProfileType, Array<{ value: OnboardingGoal; label: string }>> = {
  STUDENT: [
    { value: "UNDERSTAND_TOPIC_IN_DEPTH", label: "Understand a topic in depth" },
    { value: "PRACTICE_WITH_QUIZZES", label: "Practice and test myself with quizzes" },
    { value: "REVIEW_EXISTING_NOTES", label: "Review notes I already have" },
  ],
  BOARD_EXAM: [
    { value: "UNDERSTAND_BEFORE_EXAM_DAY", label: "Understand a topic before exam day" },
    { value: "PRACTICE_EXAM_STYLE", label: "Practice under exam-style conditions" },
    { value: "REINFORCE_WEAK_CONCEPTS", label: "Review and reinforce weak concepts" },
  ],
  TEACHER: [
    { value: "CREATE_STUDY_MATERIALS", label: "Create study material for students" },
    { value: "GENERATE_QUIZ_OR_EXAM", label: "Generate a quiz or exam" },
    { value: "UNDERSTAND_TO_TEACH", label: "Understand a topic to teach it" },
  ],
};

const STEP_NAMES: Record<number, StepName> = {
  1: "profile",
  2: "goal",
  3: "input",
  4: "study-pack",
  5: "completion",
};

const COMPLETION_COPY: Record<OnboardingProfileType, string> = {
  STUDENT: "Your Study Pack is ready. Start practicing when you're ready.",
  BOARD_EXAM: "Your first practice material is ready. Keep going and build toward exam day.",
  TEACHER: "Your Study Pack is ready. You can export a quiz for your students any time.",
};

function isOnboardingProfileType(value: string | null | undefined): value is OnboardingProfileType {
  return value === "STUDENT" || value === "BOARD_EXAM" || value === "TEACHER";
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
      <div className="space-y-2 text-left">
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
  const [completionError, setCompletionError] = useState<string | null>(null);
  const [isDesktop, setIsDesktop] = useState(true);
  const [previewOpen, setPreviewOpen] = useState<Record<GenerationSectionKey, boolean>>({
    summary: true,
    concepts: true,
    quiz: true,
  });

  const startedTrackedRef = useRef(false);
  const completionTrackedRef = useRef(false);
  const generationTrackedRef = useRef<string | null>(null);
  const completionAttemptedRef = useRef(false);
  const shouldTrackAbandonmentRef = useRef(true);
  const userIdRef = useRef<string | null>(null);

  const profileType = draft.profileType;
  const currentStep = draft.currentStep;
  const currentStepName = getStepName(currentStep);
  const goalOptions = profileType ? GOAL_OPTIONS[profileType] : [];
  const selectedGoal = draft.goal;
  const selectedInputMethod = draft.inputMethod;
  const generatedNoteReady = draft.generatedNoteReady;
  const topicLength = draft.topic.trim().length;
  const noteLength = draft.noteContent.trim().length;
  const studyPackStatus = note?.studyPackStatus ?? "DRAFT";
  const studyPackReady = studyPackStatus === "STUDY_PACK_READY";
  const studyPackGenerating = studyPackStatus === "GENERATING";
  const completionCopy = profileType ? COMPLETION_COPY[profileType] : COMPLETION_COPY.STUDENT;
  const conceptPreview = note?.keyConcepts.slice(0, MAX_CONCEPT_PREVIEW_COUNT) ?? [];
  const extraConceptCount = Math.max((note?.keyConcepts.length ?? 0) - conceptPreview.length, 0);
  const quizPreview = note?.quiz[0] ?? null;

  const canContinueFromStepOne = profileType !== null;
  const canContinueFromStepTwo = selectedGoal !== null;
  const canGenerateNoteDraft = selectedInputMethod === "generate" && topicLength >= TOPIC_MIN_LENGTH;
  const canStartStudyPack = selectedInputMethod === "generate"
    ? generatedNoteReady && noteLength >= NOTE_CONTENT_MIN_LENGTH
    : selectedInputMethod === "own_note" && noteLength >= NOTE_CONTENT_MIN_LENGTH;
  const stepTransitionKey = currentStep === 3
    ? `step-3-${selectedInputMethod ?? "none"}-${generatedNoteReady ? "generated" : "initial"}`
    : `step-${currentStep}`;

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
            goal: draft.goal,
            method: draft.inputMethod,
            time_elapsed_seconds: Math.max(0, Math.round((Date.now() - draft.startedAtMs) / 1000)),
          });
        }
        setCompletingOnboarding(false);
      });
  }, [currentStep, draft.examDate, draft.goal, draft.inputMethod, draft.startedAtMs, profileType]);

  const selectProfileType = (value: OnboardingProfileType) => {
    setDraft((previous) => ({
      ...previous,
      profileType: value,
      goal: previous.profileType === value ? previous.goal : null,
      examDate: value === "BOARD_EXAM" ? previous.examDate : "",
    }));
    trackOnboardingEvent("ONBOARDING_V2_PROFILE_SELECTED", {
      profile_type: value,
    });
  };

  const selectGoal = (value: OnboardingGoal) => {
    if (!profileType) {
      return;
    }
    setDraft((previous) => ({
      ...previous,
      goal: value,
    }));
    trackOnboardingEvent("ONBOARDING_V2_GOAL_SELECTED", {
      goal: value,
      profile_type: profileType,
    });
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
    if (!canGenerateNoteDraft || isGeneratingNote) {
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
    } catch (error) {
      setStepThreeError(
        error instanceof Error ? error.message : "We could not generate a note right now. Please try again.",
      );
    } finally {
      setIsGeneratingNote(false);
    }
  };

  const handleStartStudyPack = async () => {
    if (!profileType || !selectedInputMethod || startingStudyPack || !canStartStudyPack) {
      return;
    }

    setStartingStudyPack(true);
    setStepThreeError(null);
    setGenerationError(null);

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

      const queuedNote = await createStudyPackFromNote(createdNote.id);
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
    generationTrackedRef.current = null;
    try {
      const queuedNote = await createStudyPackFromNote(note.id);
      setNote(queuedNote);
      setDraft((previous) => ({
        ...previous,
        currentStep: 4,
        noteId: queuedNote.id,
        studyPackId: queuedNote.studyPackId ?? previous.studyPackId,
      }));
    } catch (error) {
      const message = error instanceof Error ? error.message : STEP_FOUR_ERROR_MESSAGE;
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

  const togglePreviewSection = (sectionKey: GenerationSectionKey) => {
    setPreviewOpen((previous) => ({
      ...previous,
      [sectionKey]: !previous[sectionKey],
    }));
  };

  const renderStepContent = () => {
    if (currentStep === 1) {
      return (
        <div className="mx-auto flex min-h-full w-full max-w-[680px] flex-col justify-center space-y-6">
          <div className="space-y-2 text-center sm:text-left">
            <CardTitle className="text-2xl sm:text-3xl">Welcome to NoteLib. Let&apos;s set things up.</CardTitle>
            <CardDescription>Select the profile that best matches how you&apos;ll use NoteLib.</CardDescription>
          </div>

          <div className="grid gap-3">
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
                  <div className="text-base font-semibold text-foreground sm:text-lg">{option.label}</div>
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
        <div className="mx-auto flex min-h-full w-full max-w-[680px] flex-col justify-center space-y-6">
          <div className="space-y-2 text-center sm:text-left">
            <CardTitle className="text-2xl sm:text-3xl">What&apos;s your goal right now?</CardTitle>
            <CardDescription>Choose the goal that fits your first study session.</CardDescription>
          </div>

          <div className="grid gap-3">
            {goalOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                className={getSelectionCardClassName({
                  selected: selectedGoal === option.value,
                  className: "p-4 sm:p-5",
                })}
                onClick={() => selectGoal(option.value)}
                aria-pressed={selectedGoal === option.value}
              >
                <div className="text-left text-base font-medium text-foreground sm:text-lg">{option.label}</div>
              </button>
            ))}
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
                className="min-h-12 w-full rounded-xl border border-border bg-background px-4 py-3 text-base text-foreground outline-none ring-0 transition-colors focus:border-blue-500"
                aria-label="When is your exam? (optional)"
              />
            </label>
          ) : null}
        </div>
      );
    }

    if (currentStep === 3) {
      return (
        <div className="mx-auto flex min-h-full w-full max-w-[680px] flex-col justify-center space-y-6">
          <div className="space-y-2 text-center sm:text-left">
            <CardTitle className="text-2xl sm:text-3xl">How do you want to start?</CardTitle>
            <CardDescription>Pick one path, then continue from your note into a Study Pack.</CardDescription>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
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
            <div className="motion-onboarding-step space-y-4 rounded-2xl border border-border bg-surface-alt/80 p-4 sm:p-5">
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
                  className="min-h-12 w-full rounded-xl border border-border bg-background px-4 py-3 text-base text-foreground outline-none ring-0 transition-colors focus:border-blue-500"
                />
              </label>

              {generatedNoteReady ? (
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
                    rows={10}
                    className="min-h-[220px] w-full rounded-xl border border-border bg-background px-4 py-3 text-base text-foreground outline-none ring-0 transition-colors focus:border-blue-500 md:min-h-[260px]"
                    placeholder="Your generated note will appear here."
                  />
                  <p className="text-sm text-foreground/60">{noteLength} / 50 minimum</p>
                </label>
              ) : (
                <p className="rounded-xl border border-dashed border-border px-4 py-4 text-sm text-foreground/70 sm:text-base">
                  Generate a note first, then review and edit it before creating your Study Pack.
                </p>
              )}
            </div>
          ) : null}

          {selectedInputMethod === "own_note" ? (
            <div className="motion-onboarding-step space-y-4 rounded-2xl border border-border bg-surface-alt/80 p-4 sm:p-5">
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
                  rows={10}
                  className="min-h-[220px] w-full rounded-xl border border-border bg-background px-4 py-3 text-base text-foreground outline-none ring-0 transition-colors focus:border-blue-500 md:min-h-[260px]"
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
        <div className="mx-auto flex min-h-full w-full max-w-[720px] flex-col justify-center space-y-4">
          <div className="space-y-2 text-center sm:text-left">
            <CardTitle className="text-2xl sm:text-3xl">
              {studyPackReady ? "Your Study Pack is ready." : "Building your Study Pack..."}
            </CardTitle>
            <CardDescription>
              {studyPackReady
                ? "Preview what NoteLib generated before you continue."
                : "We’re turning your note into a complete study flow."}
            </CardDescription>
          </div>

          <p className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900 dark:border-blue-900/50 dark:bg-blue-950/30 dark:text-blue-100">
            {STEP_FOUR_BACK_NOTICE}
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
            <Card className="p-4 sm:p-5">
              <p className="text-sm text-foreground/75 sm:text-base">
                Your Study Pack is generating. This usually takes a few moments.
              </p>
            </Card>
          ) : null}

          {studyPackReady ? (
            <div className="grid gap-4">
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

    return (
      <div className="mx-auto flex min-h-full w-full max-w-[680px] flex-col justify-center space-y-6">
        <div className="space-y-2 text-center sm:text-left">
          <CardTitle className="text-2xl sm:text-3xl">You just started your study loop.</CardTitle>
          <CardDescription>Create ✓ → Understand ● → Practice → Challenge → Improve</CardDescription>
        </div>

        <p className="text-base text-foreground/80">{completionCopy}</p>

        <div className="flex flex-col gap-3">
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
                Generate Note ✨
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
            <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
              <Button
                type="button"
                variant="outline"
                className="min-h-12 text-base sm:min-w-44"
                onClick={() => void handleGenerateNoteDraft()}
                disabled={!canGenerateNoteDraft}
                loading={isGeneratingNote}
                loadingText="Generating..."
              >
                Regenerate Note ✨
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
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between">
        <Button type="button" variant="outline" className="min-h-12 text-base sm:min-w-32" onClick={handleBack}>
          Back
        </Button>
        <Button type="button" className="min-h-12 text-base sm:min-w-44" disabled>
          {startingStudyPack ? "Starting..." : "Building your Study Pack..."}
        </Button>
      </div>
    );
  };

  const renderCardShell = (content: ReactNode, footer: ReactNode | null) => (
    <main className="mx-auto flex min-h-[calc(100dvh-1.5rem)] w-full max-w-[820px] flex-col justify-center px-4 py-3 sm:px-6 md:min-h-[calc(100dvh-5rem)] md:overflow-hidden md:py-5">
      <div className="flex flex-1 flex-col overflow-hidden rounded-[28px] border border-border bg-background shadow-[0_24px_80px_rgba(15,23,42,0.08)] md:h-[calc(100dvh-5rem)] md:max-h-[780px]">
        <div className="border-b border-border px-5 py-5 sm:px-6 sm:py-6">
          <div className="space-y-3">
            <p className="text-sm font-medium uppercase tracking-[0.18em] text-foreground/50">
              Step {currentStep} of 5
            </p>
            <div className="h-2 overflow-hidden rounded-full bg-foreground/10">
              <div
                className="motion-progress-bar h-full rounded-full bg-primary transition-[width] duration-200 ease-out"
                style={{ width: `${(currentStep / 5) * 100}%` }}
              />
            </div>
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5 sm:px-6 sm:py-6">
          <div key={stepTransitionKey} className="motion-onboarding-step min-h-full">
            {content}
          </div>
        </div>

        {footer ? (
          <div className="border-t border-border bg-background/95 px-5 py-4 backdrop-blur supports-[backdrop-filter]:bg-background/90 sm:px-6 sm:py-5">
            {footer}
          </div>
        ) : null}
      </div>
    </main>
  );

  if (loading) {
    return renderCardShell(
      <div className="mx-auto flex min-h-full w-full max-w-[680px] flex-col justify-center space-y-4">
        <div className="h-8 w-52 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-40 w-full animate-pulse rounded-2xl bg-foreground/10" />
      </div>,
      null,
    );
  }

  if (loadError) {
    return renderCardShell(
      <div className="mx-auto flex min-h-full w-full max-w-[680px] flex-col justify-center space-y-4">
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

  return renderCardShell(renderStepContent(), renderFooterActions());
}
