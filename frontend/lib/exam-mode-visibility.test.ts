import { getAvailableExamModes, resolvePlanPremiumExamMode } from "./exam-mode-visibility";

describe("getAvailableExamModes", () => {
  it("returns Challenge Quiz and Long Exam for STUDENT", () => {
    const modes = getAvailableExamModes("STUDENT");
    expect(modes.map((m) => m.id)).toEqual(["challenge", "long_exam"]);
  });

  it("returns Challenge Quiz and Long Exam for null profile", () => {
    const modes = getAvailableExamModes(null);
    expect(modes.map((m) => m.id)).toEqual(["challenge", "long_exam"]);
  });

  it("returns Challenge Quiz and Long Exam for undefined profile", () => {
    const modes = getAvailableExamModes(undefined);
    expect(modes.map((m) => m.id)).toEqual(["challenge", "long_exam"]);
  });

  it("returns Challenge Quiz and Board Exam for BOARD_EXAM", () => {
    const modes = getAvailableExamModes("BOARD_EXAM");
    expect(modes.map((m) => m.id)).toEqual(["challenge", "board_exam"]);
  });

  it("returns only Challenge Quiz for TEACHER", () => {
    const modes = getAvailableExamModes("TEACHER");
    expect(modes.map((m) => m.id)).toEqual(["challenge"]);
  });

  it("returns Certification Review and Full Practice Exam for PROFESSIONAL", () => {
    const modes = getAvailableExamModes("PROFESSIONAL");
    expect(modes).toHaveLength(2);
    expect(modes.map((m) => m.id)).toEqual(["challenge", "long_exam"]);
    expect(modes.map((m) => m.label)).toEqual(["Certification Review", "Full Practice Exam"]);
  });

  it("keeps Professional label overrides display-only", () => {
    const modes = getAvailableExamModes("PROFESSIONAL");
    expect(modes[0]).toMatchObject({
      id: "challenge",
      label: "Certification Review",
      description: "Practice with real-world scenarios at your own pace.",
      recommended: true,
      comingSoon: false,
    });
    expect(modes[1]).toMatchObject({
      id: "long_exam",
      label: "Full Practice Exam",
      description: "Comprehensive practice exam to test your certification readiness.",
      recommended: false,
      comingSoon: false,
    });
  });

  it("marks Challenge Quiz as recommended for STUDENT", () => {
    const modes = getAvailableExamModes("STUDENT");
    expect(modes.find((m) => m.id === "challenge")?.recommended).toBe(true);
  });

  it("marks Long Exam as not recommended and available for STUDENT", () => {
    const modes = getAvailableExamModes("STUDENT");
    const longExam = modes.find((m) => m.id === "long_exam");
    expect(longExam?.recommended).toBe(false);
    expect(longExam?.comingSoon).toBe(false);
  });

  it("marks Board Exam as recommended and not coming soon for BOARD_EXAM", () => {
    const modes = getAvailableExamModes("BOARD_EXAM");
    const boardExam = modes.find((m) => m.id === "board_exam");
    expect(boardExam?.recommended).toBe(true);
    expect(boardExam?.comingSoon).toBe(false);
  });

  it("marks Challenge Quiz as not recommended for BOARD_EXAM", () => {
    const modes = getAvailableExamModes("BOARD_EXAM");
    expect(modes.find((m) => m.id === "challenge")?.recommended).toBe(false);
  });

  it("does not include Board Exam for STUDENT", () => {
    const modes = getAvailableExamModes("STUDENT");
    expect(modes.some((m) => m.id === "board_exam")).toBe(false);
  });

  it("does not include Long Exam for BOARD_EXAM", () => {
    const modes = getAvailableExamModes("BOARD_EXAM");
    expect(modes.some((m) => m.id === "long_exam")).toBe(false);
  });

  it("does not include Board Exam for PROFESSIONAL", () => {
    const modes = getAvailableExamModes("PROFESSIONAL");
    expect(modes.some((m) => m.id === "board_exam")).toBe(false);
  });
});

describe("resolvePlanPremiumExamMode", () => {
  it("maps learner profiles to their Study Plan premium exam mode", () => {
    expect(resolvePlanPremiumExamMode("STUDENT", "PRO")).toBe("long_exam");
    expect(resolvePlanPremiumExamMode("BOARD_EXAM", "PRO")).toBe("board_exam");
    expect(resolvePlanPremiumExamMode("PROFESSIONAL", "PRO")).toBe("interview");
    expect(resolvePlanPremiumExamMode("PARENT", "PRO")).toBe("challenge");
    expect(resolvePlanPremiumExamMode("STUDENT", "FREE")).toBe("challenge");
    expect(resolvePlanPremiumExamMode("PROFESSIONAL", "PLUS")).toBe("challenge");
  });

  it("returns null for profiles without a Study Plan premium exam mode", () => {
    expect(resolvePlanPremiumExamMode("TEACHER", "FREE")).toBeNull();
    expect(resolvePlanPremiumExamMode(null, "PRO")).toBeNull();
    expect(resolvePlanPremiumExamMode(undefined, "PLUS")).toBeNull();
  });
});
