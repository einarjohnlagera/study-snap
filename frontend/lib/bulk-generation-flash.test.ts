import { consumeBulkQueuedFlash, setBulkQueuedFlash } from "./bulk-generation-flash";

describe("bulk-generation-flash", () => {
  beforeEach(() => {
    globalThis.sessionStorage.clear();
  });

  it("round-trips a queued count and clears it after reading", () => {
    setBulkQueuedFlash(3);
    expect(consumeBulkQueuedFlash()).toBe(3);
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
});
