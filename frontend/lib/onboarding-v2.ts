import type { LearnerLevel, ProfileType } from "@/lib/api";

export type OnboardingProfileType = Extract<ProfileType, "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PROFESSIONAL">;
export type OnboardingInputMethod = "generate" | "own_note";
/**
 * The first-intent choice: what the learner wants to do first, which is not the same question as
 * `OnboardingInputMethod` ("generate from a topic vs paste my own"). Input method is now a sub-choice
 * inside the create branch.
 */
export type OnboardingIntent = "ready_made" | "own_notes";

export type OnboardingProfileOption = {
  value: OnboardingProfileType;
  icon: string;
  label: string;
  description: string;
};

export const ONBOARDING_PROFILE_OPTIONS: OnboardingProfileOption[] = [
  {
    value: "STUDENT",
    icon: "🎓",
    label: "Student",
    description: "Reviewing notes and preparing for quizzes",
  },
  {
    value: "BOARD_EXAM",
    icon: "📋",
    label: "Exam Reviewer",
    description: "Preparing for a board, licensure, or civil service exam",
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
    description: "Preparing for certifications or growing professionally",
  },
];

export type OnboardingDraft = {
  schemaVersion: number;
  startedAtMs: number;
  currentStep: number;
  profileType: OnboardingProfileType | null;
  learnerLevel: LearnerLevel | null;
  courseProgram: string;
  examDate: string;
  intent: OnboardingIntent | null;
  /**
   * Whether a qualifying Official Review Set resolved for the learner's program, decided at Step 2 submit
   * and carried forward so the intent step paints instantly. `null` means UNKNOWN, not absent -- the
   * lookup fails open, and an unknown value must never render the "no Review Set yet" line, because
   * asserting content does not exist when it does is the worse error.
   */
  reviewSetAvailable: boolean | null;
  inputMethod: OnboardingInputMethod | null;
  topic: string;
  noteContent: string;
  generatedNoteReady: boolean;
  noteId: string | null;
  studyPackId: string | null;
};

const ONBOARDING_DRAFT_PREFIX = "notelib.onboarding-v2";
export const ONBOARDING_DRAFT_SCHEMA_VERSION = 1;
export const ONBOARDING_FIRST_STEP = 1;
export const ONBOARDING_LAST_STEP = 8;
const ONBOARDING_COMPLETION_DEFERRED_PREFIX = "notelib.onboarding-completion-deferred";
const LIGHTWEIGHT_PROFILE_COMPLETION_PENDING_PREFIX = "notelib.lightweight-profile-completion-pending";

function resolveKey(prefix: string, userId: string): string {
  return `${prefix}:${userId}`;
}

export function createEmptyOnboardingDraft(): OnboardingDraft {
  return {
    schemaVersion: ONBOARDING_DRAFT_SCHEMA_VERSION,
    startedAtMs: Date.now(),
    currentStep: 1,
    profileType: null,
    learnerLevel: null,
    courseProgram: "",
    examDate: "",
    intent: null,
    reviewSetAvailable: null,
    inputMethod: null,
    topic: "",
    noteContent: "",
    generatedNoteReady: false,
    noteId: null,
    studyPackId: null,
  };
}

function clampOnboardingStep(step: number): number {
  return Math.max(ONBOARDING_FIRST_STEP, Math.min(Math.trunc(step), ONBOARDING_LAST_STEP));
}

function resolveEarliestUnansweredStep(draft: OnboardingDraft): number | null {
  if (!draft.profileType) {
    return 1;
  }
  if (!draft.courseProgram.trim()) {
    return 2;
  }
  if (!draft.learnerLevel) {
    return 3;
  }
  if (!draft.intent) {
    return 4;
  }
  // The ready-made branch is Screen 5 (the plan / unavailable-program screen) and never sets
  // inputMethod, so it resumes there rather than being sent back to the door it already chose.
  if (draft.intent === "ready_made") {
    return 5;
  }
  if (!draft.inputMethod) {
    return 5;
  }
  return null;
}

function resolveMigratedResumeStep(draft: OnboardingDraft): number {
  const earliestUnansweredStep = resolveEarliestUnansweredStep(draft);
  if (earliestUnansweredStep !== null) {
    return earliestUnansweredStep;
  }
  return draft.noteId ? 7 : 6;
}

export function loadOnboardingDraft(userId: string): OnboardingDraft | null {
  if (globalThis.window === undefined) {
    return null;
  }
  const raw = globalThis.localStorage.getItem(resolveKey(ONBOARDING_DRAFT_PREFIX, userId));
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<OnboardingDraft>;
    const draft = {
      ...createEmptyOnboardingDraft(),
      ...parsed,
      schemaVersion: ONBOARDING_DRAFT_SCHEMA_VERSION,
      startedAtMs: typeof parsed.startedAtMs === "number" ? parsed.startedAtMs : Date.now(),
      currentStep: ONBOARDING_FIRST_STEP,
    };
    const isCurrentSchema = parsed.schemaVersion === ONBOARDING_DRAFT_SCHEMA_VERSION;
    if (isCurrentSchema && typeof parsed.currentStep === "number") {
      const clampedStep = clampOnboardingStep(parsed.currentStep);
      const earliestUnansweredStep = resolveEarliestUnansweredStep(draft);
      draft.currentStep = earliestUnansweredStep !== null && clampedStep > earliestUnansweredStep
        ? earliestUnansweredStep
        : clampedStep;
    } else {
      draft.currentStep = resolveMigratedResumeStep(draft);
    }
    return draft;
  } catch {
    return null;
  }
}

export function saveOnboardingDraft(userId: string, draft: OnboardingDraft): void {
  if (globalThis.window === undefined) {
    return;
  }
  try {
    globalThis.localStorage.setItem(resolveKey(ONBOARDING_DRAFT_PREFIX, userId), JSON.stringify(draft));
  } catch {
    // The in-memory flow remains usable when storage is unavailable.
  }
}

export function clearOnboardingDraft(userId: string): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.removeItem(resolveKey(ONBOARDING_DRAFT_PREFIX, userId));
}

export function setDeferredOnboardingCompletion(userId: string): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.setItem(resolveKey(ONBOARDING_COMPLETION_DEFERRED_PREFIX, userId), "1");
}

export function clearDeferredOnboardingCompletion(userId: string): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.removeItem(resolveKey(ONBOARDING_COMPLETION_DEFERRED_PREFIX, userId));
}

export function hasDeferredOnboardingCompletion(userId: string | null | undefined): boolean {
  if (globalThis.window === undefined || !userId) {
    return false;
  }
  return globalThis.localStorage.getItem(resolveKey(ONBOARDING_COMPLETION_DEFERRED_PREFIX, userId)) === "1";
}

export function setPendingLightweightProfileCompletion(userId: string): void {
  if (globalThis.window === undefined) {
    return;
  }
  try {
    globalThis.localStorage.setItem(resolveKey(LIGHTWEIGHT_PROFILE_COMPLETION_PENDING_PREFIX, userId), "1");
  } catch {
    // Fail open to the existing onboarding redirect when storage is unavailable.
  }
}

export function clearPendingLightweightProfileCompletion(userId: string): void {
  if (globalThis.window === undefined) {
    return;
  }
  try {
    globalThis.localStorage.removeItem(resolveKey(LIGHTWEIGHT_PROFILE_COMPLETION_PENDING_PREFIX, userId));
  } catch {
    // The backend completion state still ends onboarding routing on a future load.
  }
}

export function hasPendingLightweightProfileCompletion(userId: string | null | undefined): boolean {
  if (globalThis.window === undefined || !userId) {
    return false;
  }
  try {
    return globalThis.localStorage.getItem(resolveKey(LIGHTWEIGHT_PROFILE_COMPLETION_PENDING_PREFIX, userId)) === "1";
  } catch {
    return false;
  }
}

/**
 * Screen 2's copy, by profile type (C9).
 *
 * Slice 5 moved profile type to Screen 1, so by the time this screen renders we know who we are talking
 * to -- yet the copy stayed byte-identical for all four types. That is wrong in more than tone: under the
 * two-mode authoring model a TEACHER's programme is what they *teach*, and asking "what are you studying?"
 * describes something they will never do here.
 */
export function getCourseProgramScreenCopy(profileType: OnboardingProfileType | null): {
  heading: string;
  description: string;
  placeholder: string;
} {
  if (profileType === "TEACHER") {
    return {
      heading: "What do you teach?",
      description: "This sets the programme your teaching materials are written for.",
      placeholder: "e.g. BS Civil Engineering",
    };
  }
  if (profileType === "BOARD_EXAM") {
    return {
      heading: "What are you reviewing for?",
      description: "This sets the exam your review material is written for.",
      placeholder: "e.g. Nursing",
    };
  }
  if (profileType === "PROFESSIONAL") {
    return {
      heading: "What field are you in?",
      description: "This sets the field your notes and practice are written for.",
      placeholder: "e.g. Civil Engineering",
    };
  }
  return {
    heading: "What are you studying?",
    description: "This sets the subject your notes and quizzes are written for.",
    placeholder: "e.g. BS Civil Engineering",
  };
}

/**
 * Screen 3's copy, by profile type.
 *
 * The description is not optional garnish here. "What are you studying?" has an obvious consequence;
 * "what level?" does not -- nothing else on the screen says it governs how hard quizzes are and how deep
 * explanations go. A typography pass removed this line and left the screen with a heading and a control,
 * which is lighter and also less informative than it should be.
 */
export function getLearnerLevelScreenCopy(profileType: OnboardingProfileType | null): {
  heading: string;
  description: string;
} {
  if (profileType === "TEACHER") {
    return {
      heading: "What level do you teach?",
      description: "This sets the default difficulty for quizzes you generate. You can change it per quiz.",
    };
  }
  if (profileType === "BOARD_EXAM") {
    return {
      heading: "What level are you reviewing at?",
      description: "This sets how demanding your practice questions are.",
    };
  }
  if (profileType === "PROFESSIONAL") {
    return {
      heading: "What level are you working at?",
      description: "This sets how advanced your practice material is.",
    };
  }
  return {
    heading: "What level are you studying at?",
    description: "This sets how deep your quizzes and explanations go.",
  };
}
