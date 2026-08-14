import { generateAdaptiveQuickReviewQuiz } from "./api";
import { ADAPTIVE_PRACTICE_DASHBOARD_TODAY_FOCUS_ENTRY } from "./adaptive-practice-entry";

describe("Adaptive Practice API", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("appends a known entry to the start request", async () => {
    await generateAdaptiveQuickReviewQuiz("note-1", ADAPTIVE_PRACTICE_DASHBOARD_TODAY_FOCUS_ENTRY);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes/note-1/adaptive-practice/start?entry=dashboard-today-focus",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("omits the entry query parameter entirely when it is absent", async () => {
    await generateAdaptiveQuickReviewQuiz("note-1");

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes/note-1/adaptive-practice/start",
      expect.objectContaining({ method: "POST" }),
    );
  });
});
