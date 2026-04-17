import type { ProfileType } from "./api";

// --- Mode types ---

/**
 * Behaviour mode derived from profile type.
 * Use mode checks in shared components — never branch on profile type name directly.
 */
export type ProfileMode = "LEARNING" | "CREATOR";

/**
 * Quiz session recording mode.
 * LEARNING — session is scored and performance is stored.
 * PREVIEW   — quiz runs but scores are not recorded (default for Creator mode).
 */
export type SessionMode = "LEARNING" | "PREVIEW";

// --- Profile type availability ---

export const ACTIVE_PROFILE_TYPES: readonly ProfileType[] = [
  "STUDENT",
  "BOARD_EXAM",
  "TEACHER",
];

export const DISABLED_PROFILE_TYPES: readonly ProfileType[] = [
  "PROFESSIONAL",
  "PARENT",
];

export function isActiveProfileType(profileType: ProfileType): boolean {
  return (ACTIVE_PROFILE_TYPES as readonly string[]).includes(profileType);
}

// --- Mode resolution ---

export function resolveProfileMode(
  profileType: ProfileType | null | undefined,
): ProfileMode {
  if (profileType === "TEACHER") {
    return "CREATOR";
  }
  return "LEARNING";
}

/**
 * Returns the default quiz session mode for a given profile type.
 * Creator mode (Teacher) defaults to PREVIEW — scores not recorded until the user
 * explicitly chooses "Take Quiz". Learning mode always defaults to LEARNING.
 */
export function resolveDefaultSessionMode(
  profileType: ProfileType | null | undefined,
): SessionMode {
  if (resolveProfileMode(profileType) === "CREATOR") {
    return "PREVIEW";
  }
  return "LEARNING";
}

// --- Profile type switch confirmation content ---

export type ProfileTypeSwitchContent = {
  title: string;
  body: string[];
  note: string;
  toast: string;
};

type ActiveProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER";

const SWITCH_NOTE = "You can switch back anytime.";

const PROFILE_TYPE_SWITCH_CONTENT: Record<
  ActiveProfileType,
  ProfileTypeSwitchContent
> = {
  STUDENT: {
    title: "Switch to Student mode?",
    body: [
      "Student mode is designed for active learning.",
      "Your quiz sessions will be scored and tracked to help you understand your progress.",
    ],
    note: SWITCH_NOTE,
    toast: "You're now in Student mode — focused on active learning.",
  },
  BOARD_EXAM: {
    title: "Switch to Board Taker mode?",
    body: [
      "Board Taker mode is designed for exam preparation.",
      "Your dashboard will focus on performance, weak concepts, and targeted review.",
    ],
    note: SWITCH_NOTE,
    toast: "You're now in Board Taker mode — focused for exam prep.",
  },
  TEACHER: {
    title: "Switch to Teacher mode?",
    body: [
      "Teacher mode is designed for creating and exporting study materials.",
      "You can preview quizzes and export them directly.",
      "Quiz scores are only recorded when taking quizzes in learning mode.",
    ],
    note: SWITCH_NOTE,
    toast: "You're now in Teacher mode — quizzes open in preview by default.",
  },
};

export function getProfileTypeSwitchContent(
  profileType: ActiveProfileType,
): ProfileTypeSwitchContent {
  return PROFILE_TYPE_SWITCH_CONTENT[profileType];
}

export function isActiveProfileTypeForSwitch(
  profileType: string,
): profileType is ActiveProfileType {
  return (
    profileType === "STUDENT" ||
    profileType === "BOARD_EXAM" ||
    profileType === "TEACHER"
  );
}
