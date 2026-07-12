import type { LearnerLevel, ProfileType } from "@/lib/api";

export type OnboardingProfileType = Extract<ProfileType, "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PROFESSIONAL">;
export type OnboardingInputMethod = "generate" | "own_note";

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
  startedAtMs: number;
  currentStep: number;
  profileType: OnboardingProfileType | null;
  learnerLevel: LearnerLevel | null;
  courseProgram: string;
  examDate: string;
  inputMethod: OnboardingInputMethod | null;
  topic: string;
  noteContent: string;
  generatedNoteReady: boolean;
  noteId: string | null;
  studyPackId: string | null;
};

const ONBOARDING_DRAFT_PREFIX = "notelib.onboarding-v2";
const ONBOARDING_COMPLETION_DEFERRED_PREFIX = "notelib.onboarding-completion-deferred";
const LIGHTWEIGHT_PROFILE_COMPLETION_PENDING_PREFIX = "notelib.lightweight-profile-completion-pending";

function resolveKey(prefix: string, userId: string): string {
  return `${prefix}:${userId}`;
}

export function createEmptyOnboardingDraft(): OnboardingDraft {
  return {
    startedAtMs: Date.now(),
    currentStep: 1,
    profileType: null,
    learnerLevel: null,
    courseProgram: "",
    examDate: "",
    inputMethod: null,
    topic: "",
    noteContent: "",
    generatedNoteReady: false,
    noteId: null,
    studyPackId: null,
  };
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
    return {
      ...createEmptyOnboardingDraft(),
      ...parsed,
      startedAtMs: typeof parsed.startedAtMs === "number" ? parsed.startedAtMs : Date.now(),
      currentStep: typeof parsed.currentStep === "number" ? parsed.currentStep : 1,
    };
  } catch {
    return null;
  }
}

export function saveOnboardingDraft(userId: string, draft: OnboardingDraft): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.setItem(resolveKey(ONBOARDING_DRAFT_PREFIX, userId), JSON.stringify(draft));
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
