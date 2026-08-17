import {
  consumeBulkGenerationRetryStash,
  consumeBulkQueuedFlash,
  setBulkGenerationRetryStash,
  setBulkQueuedFlash,
} from "./bulk-generation-flash";

describe("bulk-generation-flash", () => {
  beforeEach(() => {
    globalThis.sessionStorage.clear();
  });

  it("round-trips a queued count and clears it after reading", () => {
    setBulkQueuedFlash(3, "result-1");
    expect(consumeBulkQueuedFlash()).toEqual({ queuedCount: 3, resultId: "result-1" });
    // one-shot: a second read returns null
    expect(consumeBulkQueuedFlash()).toBeNull();
  });

  it("returns null when nothing was flashed", () => {
    expect(consumeBulkQueuedFlash()).toBeNull();
  });

  it("ignores non-positive or invalid stored values", () => {
    setBulkQueuedFlash(0);
    expect(consumeBulkQueuedFlash()).toBeNull();
    globalThis.sessionStorage.setItem("notelib.bulk.queuedFlash", "not-a-number");
    expect(consumeBulkQueuedFlash()).toBeNull();
  });

  it("round-trips retry stash and clears it after reading", () => {
    setBulkGenerationRetryStash({
      subject: "Maternal Health",
      courseProgram: "Nursing",
      domainContext: "NURSING",
      learnerLevel: "BOARD_EXAM_REVIEW",
      makePublic: true,
      topics: ["Prenatal Care", "Labor Stages"],
    });

    expect(consumeBulkGenerationRetryStash()).toEqual({
      subject: "Maternal Health",
      courseProgram: "Nursing",
      domainContext: "NURSING",
      learnerLevel: "BOARD_EXAM_REVIEW",
      makePublic: true,
      topics: ["Prenatal Care", "Labor Stages"],
      // Absent from the written stash, so it reads back as an explicit null rather than
      // being dropped — which is what a pre-v0.75.0 stash looks like.
      collectionId: null,
    });
    expect(consumeBulkGenerationRetryStash()).toBeNull();
  });

  it("restores an old-shape retry stash while ignoring its retired audience key", () => {
    globalThis.sessionStorage.setItem("notelib.bulk.retryTopics", JSON.stringify({
      subject: "Maternal Health",
      courseProgram: "Nursing",
      domainContext: "NURSING",
      learnerLevel: "BOARD_EXAM_REVIEW",
      targetProfileType: "BOARD_TAKER",
      makePublic: true,
      topics: ["Prenatal Care"],
      collectionId: "plan-1",
    }));

    expect(consumeBulkGenerationRetryStash()).toEqual({
      subject: "Maternal Health",
      courseProgram: "Nursing",
      domainContext: "NURSING",
      learnerLevel: "BOARD_EXAM_REVIEW",
      makePublic: true,
      topics: ["Prenatal Care"],
      collectionId: "plan-1",
    });
  });
});
