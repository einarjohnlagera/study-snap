import {
  ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY,
  ADAPTIVE_PRACTICE_DASHBOARD_FOCUS_AREAS_ENTRY,
  ADAPTIVE_PRACTICE_DASHBOARD_TODAY_FOCUS_ENTRY,
  ADAPTIVE_PRACTICE_INTERVIEW_PRACTICE_GAP_ENTRY,
  buildAdaptivePracticeHref,
  buildAdaptivePracticeSessionHref,
  normalizeAdaptivePracticeEntry,
  type AdaptivePracticeEntry,
} from "./adaptive-practice-entry";

describe("adaptive practice entry", () => {
  const entryCases: ReadonlyArray<readonly [AdaptivePracticeEntry, string]> = [
    [ADAPTIVE_PRACTICE_DASHBOARD_TODAY_FOCUS_ENTRY, "dashboard-today-focus"],
    [ADAPTIVE_PRACTICE_DASHBOARD_FOCUS_AREAS_ENTRY, "dashboard-focus-areas"],
    [ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY, "challenge-quiz-result"],
    [ADAPTIVE_PRACTICE_INTERVIEW_PRACTICE_GAP_ENTRY, "interview-practice-gap"],
  ];

  it.each(entryCases)("builds an attributed href for %s", (entry, expectedEntry) => {
    expect(buildAdaptivePracticeHref("note-1", { entry }))
      .toBe(`/notes/note-1/adaptive-practice?entry=${expectedEntry}`);
    expect(normalizeAdaptivePracticeEntry(entry)).toBe(entry);
  });

  it("keeps direct links bare and normalizes absent or unknown values to null", () => {
    expect(buildAdaptivePracticeHref("note-1")).toBe("/notes/note-1/adaptive-practice");
    expect(normalizeAdaptivePracticeEntry(null)).toBeNull();
    expect(normalizeAdaptivePracticeEntry("caller-controlled-value")).toBeNull();
  });

  it("builds the session-addressed route for collection-anchored practice", () => {
    expect(buildAdaptivePracticeSessionHref("session-1"))
      .toBe("/adaptive-practice/sessions/session-1");
  });
});
