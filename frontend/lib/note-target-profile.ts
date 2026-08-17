import type { NoteTargetProfileType, ProfileType, UserRole } from "@/lib/api";

export const NOTE_TARGET_PROFILE_ALL = "ALL";

export type NoteTargetProfileFilter = NoteTargetProfileType | typeof NOTE_TARGET_PROFILE_ALL;

export const PUBLIC_NOTE_TARGET_PROFILE_TYPES: NoteTargetProfileType[] = ["STUDENT", "BOARD_TAKER", "PROFESSIONAL"];

function isCurator(
  profileType: ProfileType | null | undefined,
  role: UserRole | null | undefined,
): boolean {
  return role === "ADMIN" || profileType === "TEACHER";
}

export function getNoteTargetProfileLabel(value: NoteTargetProfileType): string {
  switch (value) {
    case "BOARD_TAKER":
      return "Exam Reviewer";
    case "PROFESSIONAL":
      return "Professional";
    case "STUDENT":
    default:
      return "Student";
  }
}

export function resolvePublicLibraryTargetProfileFilter(
  profileType: ProfileType | null | undefined,
): NoteTargetProfileFilter {
  if (profileType === "BOARD_EXAM") {
    return "BOARD_TAKER";
  }
  if (profileType === "STUDENT") {
    return "STUDENT";
  }
  if (profileType === "PROFESSIONAL") {
    return "PROFESSIONAL";
  }
  return NOTE_TARGET_PROFILE_ALL;
}

export function isTeacherSelectableNoteTarget(
  profileType: ProfileType | null | undefined,
  role: UserRole | null | undefined,
): boolean {
  return isCurator(profileType, role);
}

export function isQuizMasteryLockBypassed(
  profileType: ProfileType | null | undefined,
  role: UserRole | null | undefined,
): boolean {
  return isCurator(profileType, role);
}

export function isPublicNoteTargetProfileFilter(
  value: string | null | undefined,
): value is NoteTargetProfileFilter {
  return value === NOTE_TARGET_PROFILE_ALL
    || value === "STUDENT"
    || value === "BOARD_TAKER"
    || value === "PROFESSIONAL";
}
