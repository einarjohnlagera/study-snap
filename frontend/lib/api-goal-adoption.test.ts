import { adoptGoal, getGoalAdoptionStatus, waitForGoalAdoption } from "./api";

describe("Goal adoption API", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => { globalThis.fetch = originalFetch; });

  const started = {
    goalCollectionId: "personal-goal-1",
    collectionId: "personal-goal-1",
    jobId: "job-1",
    status: "STARTED" as const,
    adoptedSubjectCount: 0,
    skippedSubjectCount: 0,
    totalNotesCopied: 0,
    totalNotesSkipped: 0,
    processedSubjectCount: 0,
    totalSubjectCount: 2,
    alreadyAdopted: false,
  };

  it("parses the additive started response and polls the owner-scoped goal URL", async () => {
    const complete = {
      ...started,
      status: "COMPLETED",
      processedSubjectCount: 2,
      adoptedSubjectCount: 2,
      totalNotesCopied: 5,
    };
    globalThis.fetch = jest.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => started })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => complete });

    const response = await adoptGoal("source-goal-1");
    const onProgress = jest.fn();
    const finished = await waitForGoalAdoption(response, onProgress);

    expect(response).toEqual(started);
    expect(finished).toMatchObject({ status: "COMPLETED", adoptedSubjectCount: 2, totalNotesCopied: 5 });
    expect(onProgress).toHaveBeenCalledWith(complete);
    expect(globalThis.fetch).toHaveBeenNthCalledWith(1,
      "http://localhost:8080/api/collections/source-goal-1/adopt-goal", expect.objectContaining({ method: "POST" }));
    expect(globalThis.fetch).toHaveBeenNthCalledWith(2,
      "http://localhost:8080/api/collections/personal-goal-1/adoption-status", expect.objectContaining({ method: "GET" }));
  });

  it("surfaces an unreachable status read so the caller can offer retry", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({ ok: false, status: 503, json: async () => ({}) });
    await expect(getGoalAdoptionStatus(started.goalCollectionId)).rejects.toThrow(
      "Could not check Goal adoption progress. Try again.",
    );
  });
});
