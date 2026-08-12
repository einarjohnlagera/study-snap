import {
  clearPendingLightweightProfileCompletion,
  createEmptyOnboardingDraft,
  hasPendingLightweightProfileCompletion,
  loadOnboardingDraft,
  saveOnboardingDraft,
  setPendingLightweightProfileCompletion,
} from "./onboarding-v2";

describe("onboarding-v2 draft storage", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("defaults learning context fields on empty drafts", () => {
    expect(createEmptyOnboardingDraft()).toEqual(expect.objectContaining({
      schemaVersion: 1,
      learnerLevel: null,
      courseProgram: "",
    }));
  });

  it("loads legacy drafts without learner context fields", () => {
    const legacyDraft = {
      startedAtMs: 1000,
      currentStep: 2,
      profileType: "STUDENT",
      examDate: "",
      inputMethod: null,
      topic: "",
      noteContent: "",
      generatedNoteReady: false,
      noteId: null,
      studyPackId: null,
    };

    window.localStorage.setItem("notelib.onboarding-v2:user-1", JSON.stringify(legacyDraft));

    expect(loadOnboardingDraft("user-1")).toEqual(expect.objectContaining({
      learnerLevel: null,
      courseProgram: "",
      currentStep: 2,
      profileType: "STUDENT",
    }));
  });

  it("migrates a pre-split draft by preserving answers and resuming at the earliest unanswered screen", () => {
    const legacyDraft = {
      startedAtMs: 1000,
      currentStep: 5,
      profileType: "STUDENT",
      learnerLevel: null,
      courseProgram: "Nursing",
      intent: "own_notes",
      reviewSetAvailable: false,
      inputMethod: "own_note",
      topic: "",
      noteContent: "A typed note that must survive the screen renumbering.",
      generatedNoteReady: false,
      noteId: null,
      studyPackId: null,
    };

    window.localStorage.setItem("notelib.onboarding-v2:user-1", JSON.stringify(legacyDraft));

    expect(loadOnboardingDraft("user-1")).toEqual(expect.objectContaining({
      schemaVersion: 1,
      currentStep: 3,
      profileType: "STUDENT",
      courseProgram: "Nursing",
      intent: "own_notes",
      inputMethod: "own_note",
      noteContent: "A typed note that must survive the screen renumbering.",
    }));
  });

  it("clamps a current-schema step beyond the eight-screen range", () => {
    const draft = {
      ...createEmptyOnboardingDraft(),
      currentStep: 99,
      profileType: "STUDENT" as const,
      courseProgram: "Nursing",
      learnerLevel: "COLLEGE" as const,
      intent: "own_notes" as const,
      inputMethod: "own_note" as const,
      noteId: "note-1",
    };
    window.localStorage.setItem("notelib.onboarding-v2:user-1", JSON.stringify(draft));

    expect(loadOnboardingDraft("user-1")?.currentStep).toBe(8);
  });

  it("does not let an out-of-order current draft skip an earlier required answer", () => {
    const draft = {
      ...createEmptyOnboardingDraft(),
      currentStep: 6,
      courseProgram: "Nursing",
      learnerLevel: "COLLEGE" as const,
      intent: "own_notes" as const,
      inputMethod: "own_note" as const,
    };
    window.localStorage.setItem("notelib.onboarding-v2:user-1", JSON.stringify(draft));

    expect(loadOnboardingDraft("user-1")?.currentStep).toBe(1);
  });

  it("round-trips learning context fields", () => {
    const draft = {
      ...createEmptyOnboardingDraft(),
      learnerLevel: "PROFESSIONAL" as const,
      courseProgram: "AWS Certification",
    };

    saveOnboardingDraft("user-1", draft);

    expect(loadOnboardingDraft("user-1")).toEqual(expect.objectContaining({
      learnerLevel: "PROFESSIONAL",
      courseProgram: "AWS Certification",
    }));
  });

  it("stores lightweight profile completion separately from deferred onboarding completion", () => {
    setPendingLightweightProfileCompletion("user-1");

    expect(hasPendingLightweightProfileCompletion("user-1")).toBe(true);
    expect(window.localStorage.getItem("notelib.lightweight-profile-completion-pending:user-1")).toBe("1");

    clearPendingLightweightProfileCompletion("user-1");
    expect(hasPendingLightweightProfileCompletion("user-1")).toBe(false);
  });

  it("fails open when lightweight completion storage is unavailable", () => {
    const setItemSpy = jest.spyOn(Storage.prototype, "setItem").mockImplementationOnce(() => {
      throw new Error("Storage unavailable");
    });

    setPendingLightweightProfileCompletion("user-1");

    expect(hasPendingLightweightProfileCompletion("user-1")).toBe(false);
    setItemSpy.mockRestore();
  });

  it("keeps navigation usable when saving the onboarding draft fails", () => {
    const setItemSpy = jest.spyOn(Storage.prototype, "setItem").mockImplementationOnce(() => {
      throw new Error("Storage unavailable");
    });

    expect(() => saveOnboardingDraft("user-1", createEmptyOnboardingDraft())).not.toThrow();
    setItemSpy.mockRestore();
  });
});
