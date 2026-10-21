import {
  ApiRequestError,
  isChallengePreparationFailedError,
  isChallengePreparationInProgressError,
  isChallengeSessionNotInProgressError,
  isInvalidPreparedDiscardError,
  isPreparedChallengeExpiredError,
  startChallengeQuizSession,
  startPreparedChallengeQuiz,
  startRedoMissedChallengeQuizSession,
  markChallengeQuizReadySeen,
  getChallengeQuizSessionStatus,
  getChallengeQuizSession,
} from "./api";

describe("Challenge Quiz preparation API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);
  });
  afterEach(() => { globalThis.fetch = originalFetch; });

  it("starts a prepared quiz with a bodyless POST", async () => {
    await startPreparedChallengeQuiz("session-1");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/challenge-quiz/sessions/session-1/start-quiz",
      { method: "POST", headers: {} },
    );
  });

  it("marks the ready screen seen with a bodyless POST", async () => {
    await markChallengeQuizReadySeen("session-1");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/challenge-quiz/sessions/session-1/ready-seen",
      { method: "POST", headers: {} },
    );
  });

  it("reads the exact session status with GET", async () => {
    await getChallengeQuizSessionStatus("session-1");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/challenge-quiz/sessions/session-1/status",
      { method: "GET", headers: {} },
    );
  });

  it("reads the exact session payload without starting it", async () => {
    await getChallengeQuizSession("session-1");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/challenge-quiz/sessions/session-1",
      { method: "GET", headers: {} },
    );
  });

  it("keeps redo missed bodyless when nothing is discarded", async () => {
    await startRedoMissedChallengeQuizSession("note-1");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes/note-1/challenge-quiz/redo-missed",
      { method: "POST", headers: {} },
    );
  });

  it("sends JSON and Content-Type when redo missed discards a prepared quiz", async () => {
    await startRedoMissedChallengeQuizSession("note-1", { discardPreparedSessionId: "session-1" });
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes/note-1/challenge-quiz/redo-missed",
      { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ discardPreparedSessionId: "session-1" }) },
    );
  });

  it("carries the discard id in an ordinary start request", async () => {
    await startChallengeQuizSession("note-1", { mode: "board_exam", additionalStudyPackIds: ["note-2"],
      sourceCollectionId: "collection-1", discardPreparedSessionId: "session-1" });
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes/note-1/challenge-quiz/start",
      { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({
        mode: "board_exam", additionalStudyPackIds: ["note-2"], sourceCollectionId: "collection-1",
        discardPreparedSessionId: "session-1",
      }) },
    );
  });

  it.each([
    ["PREPARED_CHALLENGE_EXPIRED", isPreparedChallengeExpiredError],
    ["SESSION_NOT_IN_PROGRESS", isChallengeSessionNotInProgressError],
    ["INVALID_PREPARED_CHALLENGE_DISCARD", isInvalidPreparedDiscardError],
    ["PREPARATION_IN_PROGRESS", isChallengePreparationInProgressError],
    ["PREPARATION_FAILED", isChallengePreparationFailedError],
  ])("recognizes only %s", (code, helper) => {
    expect(helper(new ApiRequestError("error", { code, status: 409 }))).toBe(true);
    expect(helper(new ApiRequestError("error", { code: "OTHER", status: 409 }))).toBe(false);
    expect(helper(new Error(code))).toBe(false);
  });
});
