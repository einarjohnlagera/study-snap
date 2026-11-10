import { CatalogBackoffError, resetCatalogCacheForTests } from "./catalog-cache";
import { getServerPublicCreatorProfile, getServerPublicProfile } from "./server-public-profiles";

const response = (status: number, body: object = {}) => ({
  ok: status >= 200 && status < 300,
  status,
  json: jest.fn().mockResolvedValue(body),
}) as unknown as Response;

describe("server public profile reads", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => {
    resetCatalogCacheForTests();
    jest.useFakeTimers();
    jest.setSystemTime(0);
    globalThis.fetch = jest.fn().mockResolvedValue(response(200));
  });
  afterEach(() => {
    globalThis.fetch = originalFetch;
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it("uses no-store on both full profile and Learning Focus requests", async () => {
    await getServerPublicProfile("owner-id");
    await getServerPublicCreatorProfile("creator");
    expect(globalThis.fetch).toHaveBeenCalledTimes(4);
    for (const [url, options] of (globalThis.fetch as jest.Mock).mock.calls) {
      expect(url).toMatch(/^http:\/\/localhost:8080\/api\/public\/(profile|creator)\//);
      expect(options).toEqual({ method: "GET", cache: "no-store", signal: expect.any(AbortSignal) });
      expect(options).not.toHaveProperty("next");
    }
  });

  it("hits before 120 seconds and refreshes at the exact boundary", async () => {
    (globalThis.fetch as jest.Mock).mockImplementation((url: string) => Promise.resolve(response(200, { version: url.endsWith("learning-focus") ? "focus" : "first" })));
    expect(await getServerPublicProfile("owner-id")).toMatchObject({ status: "ok", profile: { version: "first" } });
    jest.setSystemTime(119_999);
    await getServerPublicProfile("owner-id");
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    jest.setSystemTime(120_000);
    (globalThis.fetch as jest.Mock).mockImplementation((url: string) => Promise.resolve(response(200, { version: url.endsWith("learning-focus") ? "focus" : "second" })));
    expect(await getServerPublicProfile("owner-id")).toMatchObject({ status: "ok", profile: { version: "second" } });
    expect(globalThis.fetch).toHaveBeenCalledTimes(4);
  });

  it("throws on a failed refresh after expiry and never returns the old profile", async () => {
    (globalThis.fetch as jest.Mock).mockResolvedValue(response(200, { bio: "old" }));
    await getServerPublicCreatorProfile("alice");
    jest.setSystemTime(120_000);
    (globalThis.fetch as jest.Mock).mockImplementation((url: string) => Promise.resolve(response(url.endsWith("learning-focus") ? 200 : 500)));
    await expect(getServerPublicCreatorProfile("alice")).rejects.toThrow("Could not load public profile.");
    await expect(getServerPublicCreatorProfile("alice")).rejects.toBeInstanceOf(CatalogBackoffError);
  });

  it("caches ok and private, but not not_found", async () => {
    await getServerPublicProfile("ok");
    await getServerPublicProfile("ok");
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    (globalThis.fetch as jest.Mock).mockImplementation((url: string) => Promise.resolve(response(url.endsWith("learning-focus") ? 200 : 403)));
    await expect(getServerPublicProfile("private")).resolves.toEqual({ status: "private" });
    await expect(getServerPublicProfile("private")).resolves.toEqual({ status: "private" });
    expect(globalThis.fetch).toHaveBeenCalledTimes(4);
    (globalThis.fetch as jest.Mock).mockImplementation((url: string) => Promise.resolve(response(url.endsWith("learning-focus") ? 200 : 404)));
    await expect(getServerPublicProfile("missing")).resolves.toEqual({ status: "not_found" });
    await expect(getServerPublicProfile("missing")).resolves.toEqual({ status: "not_found" });
    expect(globalThis.fetch).toHaveBeenCalledTimes(8);
  });

  it("single-flights two concurrent readers into one backend pair", async () => {
    let resolveProfile!: (value: Response) => void;
    const slowProfile = new Promise<Response>((resolve) => { resolveProfile = resolve; });
    (globalThis.fetch as jest.Mock).mockImplementation((url: string) => url.endsWith("learning-focus") ? Promise.resolve(response(200)) : slowProfile);
    const first = getServerPublicCreatorProfile("Alice");
    const second = getServerPublicCreatorProfile("alice");
    await Promise.resolve();
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    resolveProfile(response(200));
    await expect(Promise.all([first, second])).resolves.toHaveLength(2);
  });

  it("backs off failures for 15 seconds without another request", async () => {
    (globalThis.fetch as jest.Mock).mockRejectedValue(new Error("network failed"));
    await expect(getServerPublicProfile("failed")).rejects.toThrow("network failed");
    const calls = (globalThis.fetch as jest.Mock).mock.calls.length;
    jest.setSystemTime(14_999);
    await expect(getServerPublicProfile("failed")).rejects.toBeInstanceOf(CatalogBackoffError);
    expect(globalThis.fetch).toHaveBeenCalledTimes(calls);
    jest.setSystemTime(15_000);
    (globalThis.fetch as jest.Mock).mockResolvedValue(response(200));
    await expect(getServerPublicProfile("failed")).resolves.toMatchObject({ status: "ok" });
  });

  it("aborts a timed-out request and enters backoff", async () => {
    (globalThis.fetch as jest.Mock).mockImplementation((_url: string, options: RequestInit) => new Promise((_resolve, reject) => {
      options.signal?.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
    }));
    const pending = getServerPublicProfile("slow");
    const assertion = expect(pending).rejects.toThrow("aborted");
    await jest.advanceTimersByTimeAsync(10_000);
    await assertion;
    expect((globalThis.fetch as jest.Mock).mock.calls).toHaveLength(2);
    await expect(getServerPublicProfile("slow")).rejects.toBeInstanceOf(CatalogBackoffError);
  });

  it("fires the profile and Learning Focus requests concurrently, not sequentially", async () => {
    let resolveProfile!: (value: Response) => void;
    const slowProfile = new Promise<Response>((resolve) => { resolveProfile = resolve; });
    (globalThis.fetch as jest.Mock).mockImplementation((url: string) => url.endsWith("learning-focus") ? Promise.resolve(response(200)) : slowProfile);
    const pending = getServerPublicProfile("owner-id");
    await Promise.resolve();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/public/profile/owner-id/learning-focus",
      { method: "GET", cache: "no-store", signal: expect.any(AbortSignal) },
    );
    resolveProfile(response(200));
    await expect(pending).resolves.toMatchObject({ status: "ok" });
  });

  it("private profile does not leak an unhandled focus rejection and aborts focus", async () => {
    let focusSignal: AbortSignal | undefined;
    (globalThis.fetch as jest.Mock).mockImplementation((url: string, options: RequestInit) => {
      if (url.endsWith("learning-focus")) {
        focusSignal = options.signal ?? undefined;
        return Promise.reject(new Error("focus failed"));
      }
      return Promise.resolve(response(403));
    });
    await expect(getServerPublicProfile("private")).resolves.toEqual({ status: "private" });
    await Promise.resolve();
    expect(focusSignal?.aborted).toBe(true);
  });

  it("normalizes creator username case and trims and encodes request paths", async () => {
    await getServerPublicCreatorProfile(" Alice ");
    await getServerPublicCreatorProfile("ALICE");
    await getServerPublicCreatorProfile("alice");
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    await getServerPublicCreatorProfile(" a/b ? ");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/public/creator/a%2Fb%20%3F",
      expect.any(Object),
    );
  });

  it("returns not_found for empty input without a request", async () => {
    await expect(getServerPublicCreatorProfile("  ")).resolves.toEqual({ status: "not_found" });
    await expect(getServerPublicProfile(" ")).resolves.toEqual({ status: "not_found" });
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("keeps creator and id lookups in different keys", async () => {
    await getServerPublicProfile("same");
    await getServerPublicCreatorProfile("same");
    expect(globalThis.fetch).toHaveBeenCalledTimes(4);
  });

  it("does not let junk 404 usernames fill the profile cache", async () => {
    (globalThis.fetch as jest.Mock).mockResolvedValue(response(200, { name: "real" }));
    await getServerPublicCreatorProfile("real");
    (globalThis.fetch as jest.Mock).mockImplementation((url: string) => Promise.resolve(response(url.endsWith("learning-focus") ? 200 : 404)));
    for (let index = 0; index < 563; index++) await getServerPublicCreatorProfile(`junk${index}`);
    await expect(getServerPublicCreatorProfile("real")).resolves.toMatchObject({ status: "ok" });
    expect(globalThis.fetch).toHaveBeenCalledTimes(563 * 2 + 2);
  });

  it("fails closed when only the Learning Focus request fails: throws, caches nothing, backs off", async () => {
    (globalThis.fetch as jest.Mock).mockImplementation((url: string) => Promise.resolve(
      url.endsWith("learning-focus") ? response(500) : response(200, { bio: "profile loaded fine" }),
    ));
    await expect(getServerPublicCreatorProfile("alice")).rejects.toThrow("Could not load public profile Learning Focus.");
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    await expect(getServerPublicCreatorProfile("alice")).rejects.toBeInstanceOf(CatalogBackoffError);
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
  });

  it("gives the Learning Focus request its own 10 s timeout even when the profile request answers", async () => {
    (globalThis.fetch as jest.Mock).mockImplementation((url: string, options: RequestInit) => {
      if (!url.endsWith("learning-focus")) return Promise.resolve(response(200, { bio: "answered at once" }));
      return new Promise((_resolve, reject) => {
        options.signal?.addEventListener("abort", () => reject(new Error("focus aborted")), { once: true });
      });
    });
    let settled = false;
    const pending = getServerPublicProfile("hung-focus");
    const assertion = expect(pending).rejects.toThrow("focus aborted");
    pending.catch(() => { settled = true; });
    await jest.advanceTimersByTimeAsync(9_999);
    expect(settled).toBe(false);
    await jest.advanceTimersByTimeAsync(1);
    expect(settled).toBe(true);
    await assertion;
    await expect(getServerPublicProfile("hung-focus")).rejects.toBeInstanceOf(CatalogBackoffError);
  });

  it("treats the dot segments . and .. as not_found without a request, because URL normalization would rewrite the backend path", async () => {
    for (const value of [".", "..", " .. "]) {
      await expect(getServerPublicCreatorProfile(value)).resolves.toEqual({ status: "not_found" });
      await expect(getServerPublicProfile(value)).resolves.toEqual({ status: "not_found" });
    }
    expect(globalThis.fetch).not.toHaveBeenCalled();
    // Dots inside a longer value are ordinary characters: not a dot segment, still sent (encoded) and still looked up.
    await getServerPublicCreatorProfile("a..b");
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    expect((globalThis.fetch as jest.Mock).mock.calls[0][0]).toBe("http://localhost:8080/api/public/creator/a..b");
  });
});
