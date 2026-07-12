import {
  dismissLightweightProfileCompletionPrompt,
  hasDismissedLightweightProfileCompletionPrompt,
} from "./lightweight-profile-completion-prompt";

describe("lightweight profile completion prompt dismissal", () => {
  beforeEach(() => {
    window.localStorage.clear();
    jest.useFakeTimers();
    jest.setSystemTime(new Date("2026-07-12T09:00:00.000Z"));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("dismisses for the current day without changing the completion marker contract", () => {
    dismissLightweightProfileCompletionPrompt("user-1");

    expect(hasDismissedLightweightProfileCompletionPrompt("user-1")).toBe(true);

    jest.setSystemTime(new Date("2026-07-13T09:00:00.000Z"));
    expect(hasDismissedLightweightProfileCompletionPrompt("user-1")).toBe(false);
  });
});
