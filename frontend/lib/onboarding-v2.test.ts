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
});
