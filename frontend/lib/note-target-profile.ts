import type { NoteTargetProfileType, ProfileType, UserRole } from "@/lib/api";

export const NOTE_TARGET_PROFILE_STORAGE_KEY = "notelib.note.targetProfileType";
export const PUBLIC_LIBRARY_TARGET_PROFILE_STORAGE_KEY = "notelib.publicLibrary.targetProfileType";
export const NOTE_TARGET_PROFILE_ALL = "ALL";

export type NoteTargetProfileFilter = NoteTargetProfileType | typeof NOTE_TARGET_PROFILE_ALL;

export const SELECTABLE_NOTE_TARGET_PROFILE_TYPES: NoteTargetProfileType[] = ["STUDENT", "BOARD_TAKER", "PROFESSIONAL"];
export const PUBLIC_NOTE_TARGET_PROFILE_TYPES: NoteTargetProfileType[] = ["STUDENT", "BOARD_TAKER"];

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

export function mapProfileTypeToNoteTargetProfile(profileType: ProfileType | null | undefined): NoteTargetProfileType {
  if (profileType === "BOARD_EXAM") {
    return "BOARD_TAKER";
  }
  if (profileType === "PROFESSIONAL") {
    return "PROFESSIONAL";
  }
  return "STUDENT";
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
  return NOTE_TARGET_PROFILE_ALL;
}

export function isTeacherSelectableNoteTarget(
  profileType: ProfileType | null | undefined,
  role: UserRole | null | undefined,
): boolean {
  return role === "ADMIN" || profileType === "TEACHER";
}

export function toSelectableNoteTargetProfile(
  value: NoteTargetProfileType | null | undefined,
): NoteTargetProfileType | "" {
  if (value === "STUDENT" || value === "BOARD_TAKER" || value === "PROFESSIONAL") {
    return value;
  }
  return "";
}

export function isPublicNoteTargetProfileFilter(
  value: string | null | undefined,
): value is NoteTargetProfileFilter {
  return value === NOTE_TARGET_PROFILE_ALL
    || value === "STUDENT"
    || value === "BOARD_TAKER";
}
