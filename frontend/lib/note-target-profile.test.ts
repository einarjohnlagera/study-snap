import {
  isQuizMasteryLockBypassed,
} from "@/lib/note-target-profile";
import type { ProfileType, UserRole } from "@/lib/api";

describe("isQuizMasteryLockBypassed", () => {
  it.each<ProfileType | null | undefined>([
    "STUDENT",
    "BOARD_EXAM",
    "PARENT",
    "PROFESSIONAL",
    null,
    undefined,
  ])("does not bypass the lock for a %s user profile", (profileType) => {
    expect(isQuizMasteryLockBypassed(profileType, "USER")).toBe(false);
  });

  it("bypasses the lock for teachers", () => {
    expect(isQuizMasteryLockBypassed("TEACHER", "USER")).toBe(true);
  });

  it.each<ProfileType>([
    "STUDENT",
    "BOARD_EXAM",
    "TEACHER",
    "PARENT",
    "PROFESSIONAL",
  ])("bypasses the lock for an admin with a %s profile", (profileType) => {
    const role: UserRole = "ADMIN";
    expect(isQuizMasteryLockBypassed(profileType, role)).toBe(true);
  });
});
