import type { ProfileType, UserRole } from "@/lib/api";

// The Target Audience helpers this module was named for were removed in v0.83.0 along with the
// axis itself. What remains are two curator-role checks that never had anything to do with it —
// they share this file only because the audience select was the first surface to gate on curator
// status. Keep them here rather than renaming the module while `notes.target_profile_type` still
// exists; the file goes away with the column in phase 4.
function isCurator(
  profileType: ProfileType | null | undefined,
  role: UserRole | null | undefined,
): boolean {
  return role === "ADMIN" || profileType === "TEACHER";
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
