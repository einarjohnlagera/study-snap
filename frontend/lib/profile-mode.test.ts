import {
  ACTIVE_PROFILE_TYPES,
  DISABLED_PROFILE_TYPES,
  getProfileTypeSwitchContent,
  isActiveProfileType,
  isActiveProfileTypeForSwitch,
  resolveProfileMode,
  shouldShowQuizReadyIndicator,
} from "./profile-mode";

describe("resolveProfileMode", () => {
  it("returns TEACHING for TEACHER", () => {
    expect(resolveProfileMode("TEACHER")).toBe("TEACHING");
  });

  it("returns LEARNING for STUDENT", () => {
    expect(resolveProfileMode("STUDENT")).toBe("LEARNING");
  });

  it("returns LEARNING for BOARD_EXAM", () => {
    expect(resolveProfileMode("BOARD_EXAM")).toBe("LEARNING");
  });

  it("returns LEARNING for null", () => {
    expect(resolveProfileMode(null)).toBe("LEARNING");
  });

  it("returns LEARNING for undefined", () => {
    expect(resolveProfileMode(undefined)).toBe("LEARNING");
  });

  it("returns LEARNING for PARENT (disabled type)", () => {
    expect(resolveProfileMode("PARENT")).toBe("LEARNING");
  });

  it("returns LEARNING for PROFESSIONAL (disabled type)", () => {
    expect(resolveProfileMode("PROFESSIONAL")).toBe("LEARNING");
  });
});

describe("isActiveProfileType", () => {
  it.each(ACTIVE_PROFILE_TYPES)("returns true for %s", (type) => {
    expect(isActiveProfileType(type)).toBe(true);
  });

  it.each(DISABLED_PROFILE_TYPES)("returns false for %s", (type) => {
    expect(isActiveProfileType(type)).toBe(false);
  });
});

describe("getProfileTypeSwitchContent", () => {
  it("returns content with note for STUDENT", () => {
    const content = getProfileTypeSwitchContent("STUDENT");
    expect(content.title).toBe("Switch to Student mode?");
    expect(content.body.length).toBeGreaterThan(0);
    expect(content.note).toBe("You can switch back anytime.");
    expect(content.toast).toContain("Student");
  });

  it("returns content with note for BOARD_EXAM", () => {
    const content = getProfileTypeSwitchContent("BOARD_EXAM");
    expect(content.title).toBe("Switch to Board Taker mode?");
    expect(content.toast).toContain("Board Taker");
  });

  it("returns content for TEACHER mentioning quiz review and export", () => {
    const content = getProfileTypeSwitchContent("TEACHER");
    expect(content.title).toBe("Switch to Teacher mode?");
    expect(content.body.some((line) => line.toLowerCase().includes("export"))).toBe(true);
    expect(content.toast).toContain("Teacher");
  });

  it("all active types include the switch-back note", () => {
    const types = ["STUDENT", "BOARD_EXAM", "TEACHER"] as const;
    for (const type of types) {
      const content = getProfileTypeSwitchContent(type);
      expect(content.note).toBe("You can switch back anytime.");
    }
  });
});

describe("isActiveProfileTypeForSwitch", () => {
  it("accepts active profile types", () => {
    expect(isActiveProfileTypeForSwitch("STUDENT")).toBe(true);
    expect(isActiveProfileTypeForSwitch("BOARD_EXAM")).toBe(true);
    expect(isActiveProfileTypeForSwitch("TEACHER")).toBe(true);
  });

  it("rejects disabled profile types", () => {
    expect(isActiveProfileTypeForSwitch("PROFESSIONAL")).toBe(false);
    expect(isActiveProfileTypeForSwitch("PARENT")).toBe(false);
  });

  it("rejects arbitrary strings", () => {
    expect(isActiveProfileTypeForSwitch("UNKNOWN")).toBe(false);
  });
});

describe("shouldShowQuizReadyIndicator", () => {
  it("shows Quiz Ready only for teacher profile in private library", () => {
    expect(shouldShowQuizReadyIndicator("TEACHER", "PRIVATE_LIBRARY", "USER")).toBe(true);
    expect(shouldShowQuizReadyIndicator("STUDENT", "PRIVATE_LIBRARY", "USER")).toBe(false);
    expect(shouldShowQuizReadyIndicator("BOARD_EXAM", "PRIVATE_LIBRARY", "USER")).toBe(false);
  });

  it("keeps public library free of teacher-specific Quiz Ready UI", () => {
    expect(shouldShowQuizReadyIndicator("TEACHER", "PUBLIC_LIBRARY", "USER")).toBe(false);
  });

  it("allows internal exam-builder readiness logic", () => {
    expect(shouldShowQuizReadyIndicator("STUDENT", "EXAM_BUILDER", "USER")).toBe(true);
  });
});
