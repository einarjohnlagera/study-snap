import { getServerPublicCreatorProfile, getServerPublicProfile } from "./server-public-profiles";

describe("server public profile reads", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);
  });
  afterEach(() => { globalThis.fetch = originalFetch; });

  it("caches both full and full-corpus Learning Focus reads for 300 seconds", async () => {
    await getServerPublicProfile("owner-id");
    await getServerPublicCreatorProfile("creator");
    expect(globalThis.fetch).toHaveBeenNthCalledWith(1,
      "http://localhost:8080/api/public/profile/owner-id",
      { method: "GET", next: { revalidate: 300 } });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(2,
      "http://localhost:8080/api/public/profile/owner-id/learning-focus",
      { method: "GET", next: { revalidate: 300 } });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(3,
      "http://localhost:8080/api/public/creator/creator",
      { method: "GET", next: { revalidate: 300 } });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(4,
      "http://localhost:8080/api/public/creator/creator/learning-focus",
      { method: "GET", next: { revalidate: 300 } });
  });

  it("fires the profile and Learning Focus requests concurrently, not sequentially", async () => {
    // A slow profile response must not delay the focus request's own fetch call - if it did, a
    // visibility toggle between the two requests could 403 the second one, failing the whole page.
    let resolveProfile: (value: unknown) => void = () => {};
    const profileResponse = new Promise((resolve) => { resolveProfile = resolve; });
    globalThis.fetch = jest.fn().mockImplementation((url: string) => {
      if (url.endsWith("/learning-focus")) {
        return Promise.resolve({ ok: true, status: 200, json: jest.fn().mockResolvedValue({}) });
      }
      return profileResponse;
    }) as unknown as typeof globalThis.fetch;

    const resultPromise = getServerPublicProfile("owner-id");
    await Promise.resolve(); // let microtasks run so both fetch() calls fire
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/public/profile/owner-id/learning-focus",
      { method: "GET", next: { revalidate: 300 } },
    );

    resolveProfile({ ok: true, status: 200, json: jest.fn().mockResolvedValue({}) });
    const result = await resultPromise;
    expect(result.status).toBe("ok");
  });

  it("private profile branch never throws from an unawaited focus rejection", async () => {
    globalThis.fetch = jest.fn().mockImplementation((url: string) => {
      if (url.endsWith("/learning-focus")) {
        return Promise.resolve({ ok: false, status: 403 });
      }
      return Promise.resolve({ ok: false, status: 403 });
    }) as unknown as typeof globalThis.fetch;

    await expect(getServerPublicProfile("owner-id")).resolves.toEqual({ status: "private" });
  });
});
