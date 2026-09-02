import { getCollectionLabels, getCollectionTerminalAction } from "./collection-labels";

describe("getCollectionLabels", () => {
  it("returns teacher lesson-plan labels", () => {
    expect(getCollectionLabels("TEACHER")).toMatchObject({
      singular: "Lesson Plan",
      plural: "Lesson Plans",
      navLabel: "Lesson Plans",
      newCtaLabel: "New Lesson Plan",
    });
  });

  it("returns student study-plan labels", () => {
    expect(getCollectionLabels("STUDENT")).toMatchObject({
      singular: "Study Plan",
      plural: "Study Plans",
      navLabel: "Study Plans",
      newCtaLabel: "New Study Plan",
    });
  });

  it("returns board-exam review-set labels", () => {
    expect(getCollectionLabels("BOARD_EXAM")).toMatchObject({
      singular: "Review Set",
      plural: "Review Sets",
      navLabel: "Review Sets",
      newCtaLabel: "New Review Set",
    });
  });

  it("returns default collection labels for professional and unknown profiles", () => {
    expect(getCollectionLabels("PROFESSIONAL")).toMatchObject({
      singular: "Collection",
      plural: "Collections",
      navLabel: "Collections",
      newCtaLabel: "New Collection",
    });
    expect(getCollectionLabels(null)).toMatchObject({
      singular: "Collection",
      plural: "Collections",
      navLabel: "Collections",
      newCtaLabel: "New Collection",
    });
  });

  it("returns profile-aware primary labels", () => {
    expect(getCollectionLabels("TEACHER").primarySingular).toBe("Primary Lesson Plan");
    expect(getCollectionLabels("STUDENT").primarySingular).toBe("Primary Study Plan");
    expect(getCollectionLabels("BOARD_EXAM").primarySingular).toBe("Primary Review Set");
    expect(getCollectionLabels("PROFESSIONAL").primarySingular).toBe("Primary Collection");
    expect(getCollectionLabels(null).primarySingular).toBe("Primary Collection");
  });
});

describe("getCollectionTerminalAction", () => {
  it("returns the teacher Exam Builder action", () => {
    expect(getCollectionTerminalAction("TEACHER", "FREE")).toEqual({
      kind: "exam-builder",
      label: "Build Exam",
    });
  });

  it("returns premium exam actions for learner profiles", () => {
    expect(getCollectionTerminalAction("STUDENT", "PRO")).toEqual({
      kind: "premium-exam",
      mode: "long_exam",
      label: "Take the Long Exam",
    });
    expect(getCollectionTerminalAction("BOARD_EXAM", "PRO")).toEqual({
      kind: "premium-exam",
      mode: "board_exam",
      label: "Take the Board Exam",
    });
    expect(getCollectionTerminalAction("PROFESSIONAL", "PRO")).toEqual({
      kind: "premium-exam",
      mode: "interview",
      label: "Start Interview Practice",
    });
  });

  it("uses Challenge Quiz for Free/Plus learners and handles PARENT explicitly", () => {
    expect(getCollectionTerminalAction("STUDENT", "FREE")).toEqual({
      kind: "premium-exam",
      mode: "challenge",
      label: "Start Challenge Quiz",
    });
    expect(getCollectionTerminalAction("PARENT", "PLUS")).toEqual({
      kind: "premium-exam",
      mode: "challenge",
      label: "Start Challenge Quiz",
    });
    expect(getCollectionTerminalAction(null, "FREE")).toBeNull();
  });
});
