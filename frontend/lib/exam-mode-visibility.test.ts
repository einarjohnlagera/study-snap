import { getAvailableExamModes } from "./exam-mode-visibility";

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

  it("marks Challenge Quiz as recommended for STUDENT", () => {
    const modes = getAvailableExamModes("STUDENT");
    expect(modes.find((m) => m.id === "challenge")?.recommended).toBe(true);
  });

  it("marks Long Exam as not recommended and coming soon for STUDENT", () => {
    const modes = getAvailableExamModes("STUDENT");
    const longExam = modes.find((m) => m.id === "long_exam");
    expect(longExam?.recommended).toBe(false);
    expect(longExam?.comingSoon).toBe(true);
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
});
