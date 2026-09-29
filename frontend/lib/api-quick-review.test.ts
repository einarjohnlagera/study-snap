import { answerQuickReviewQuestion } from "./api";

describe("Quick Review API", () => {
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

  it("submits a retry-round multi-select answer as JSON", async () => {
    await answerQuickReviewQuestion("session-1", {
      questionIndex: 2,
      retryCount: 1,
      selectedMultiChoiceIndices: [0, 2],
    });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/quick-review/session-1/answer",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "Content-Type": "application/json" }),
        body: JSON.stringify({
          questionIndex: 2,
          retryCount: 1,
          selectedMultiChoiceIndices: [0, 2],
        }),
      }),
    );
  });

  it("submits a first-round single-choice answer as JSON", async () => {
    await answerQuickReviewQuestion("session-2", {
      questionIndex: 0,
      retryCount: 0,
      selectedChoiceIndex: 3,
    });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/quick-review/session-2/answer",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "Content-Type": "application/json" }),
        body: JSON.stringify({
          questionIndex: 0,
          retryCount: 0,
          selectedChoiceIndex: 3,
        }),
      }),
    );
  });
});
