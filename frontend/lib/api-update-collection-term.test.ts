import { updateCollection } from "./api";

/**
 * ⚠️ Executes the client's REAL request shape for the Academic Term PATCH. Component tests mock
 * `@/lib/api` wholesale, so nothing there proves the URL, method, JSON content type or the exact
 * `termLabel` / `termOrder` property names the backend's `UpdateNoteCollectionRequest` reads.
 */
describe("updateCollection term placement", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  function mockOk() {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({ id: "child-1", termLabel: "First Semester", termOrder: 1 }),
    } as unknown as Response);
  }

  it("PATCHes JSON with the exact term property names", async () => {
    mockOk();

    await updateCollection("child-1", { termLabel: "First Semester", termOrder: 1 });

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    const [url, init] = (globalThis.fetch as jest.Mock).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/collections/child-1");
    expect(init.method).toBe("PATCH");
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
    expect(JSON.parse(init.body)).toEqual({ termLabel: "First Semester", termOrder: 1 });
  });

  it("sends a blank label with no order to clear the term", async () => {
    mockOk();

    await updateCollection("child-1", { termLabel: "" });

    const [, init] = (globalThis.fetch as jest.Mock).mock.calls[0];
    expect(JSON.parse(init.body)).toEqual({ termLabel: "" });
  });
});
