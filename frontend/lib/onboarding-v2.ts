import type { ProfileType } from "@/lib/api";

export type OnboardingProfileType = Extract<ProfileType, "STUDENT" | "BOARD_EXAM" | "TEACHER">;
export type OnboardingGoal =
  | "UNDERSTAND_TOPIC_IN_DEPTH"
  | "PRACTICE_WITH_QUIZZES"
  | "REVIEW_EXISTING_NOTES"
  | "UNDERSTAND_BEFORE_EXAM_DAY"
  | "PRACTICE_EXAM_STYLE"
  | "REINFORCE_WEAK_CONCEPTS"
  | "CREATE_STUDY_MATERIALS"
  | "GENERATE_QUIZ_OR_EXAM"
  | "UNDERSTAND_TO_TEACH";
export type OnboardingInputMethod = "generate" | "own_note";

export type OnboardingDraft = {
  startedAtMs: number;
  currentStep: number;
  profileType: OnboardingProfileType | null;
  goal: OnboardingGoal | null;
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

function resolveKey(prefix: string, userId: string): string {
  return `${prefix}:${userId}`;
}

export function createEmptyOnboardingDraft(): OnboardingDraft {
  return {
    startedAtMs: Date.now(),
    currentStep: 1,
    profileType: null,
    goal: null,
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
