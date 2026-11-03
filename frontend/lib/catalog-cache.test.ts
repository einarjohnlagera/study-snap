import {
  CATALOG_CACHE_MAX_ITEMS,
  CATALOG_CACHE_MAX_KEYS,
  CATALOG_PAGE_TTL_CAP_MS,
  CATALOG_PAGE_WALK_DEADLINE_MS,
  CATALOG_SITEMAP_WALK_DEADLINE_MS,
  CATALOG_WALK_PAGE_REQUEST_TIMEOUT_MS,
  CATALOG_RAIL_REQUEST_TIMEOUT_MS,
  CATALOG_PLAN_REQUEST_TIMEOUT_MS,
  CATALOG_WALK_MAX_PAGES,
  CATALOG_SITEMAP_TTL_CAP_MS,
  catalogTtlMs,
  CatalogDeadlineError,
  createRequestSignal,
  getCatalogCached,
  resetCatalogCacheForTests,
} from "./catalog-cache";

const deferred = <T>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
};

describe("hard-TTL catalog cache", () => {
  let time = 0;
  const now = () => time;
  const read = <T>(key: string, load: () => Promise<T>, ttlMs = 100) =>
    getCatalogCached(key, ttlMs, load, { now });

  beforeEach(() => { resetCatalogCacheForTests(); time = 0; });

  it("has room for rails alongside catalog walks while keeping the item cap", () => {
    expect(CATALOG_CACHE_MAX_KEYS).toBe(1024);
    expect(CATALOG_CACHE_MAX_ITEMS).toBe(15_000);
    expect([CATALOG_PAGE_WALK_DEADLINE_MS, CATALOG_SITEMAP_WALK_DEADLINE_MS]).toEqual([90_000, 240_000]);
    expect([CATALOG_WALK_PAGE_REQUEST_TIMEOUT_MS, CATALOG_RAIL_REQUEST_TIMEOUT_MS, CATALOG_PLAN_REQUEST_TIMEOUT_MS]).toEqual([15_000, 10_000, 10_000]);
    expect(CATALOG_WALK_MAX_PAGES).toBe(400);
  });

  it("serves inside the TTL but refreshes at its exact boundary without serving expired data (a)", async () => {
    const load = jest.fn().mockResolvedValueOnce("old");
    expect(await read("a", load)).toBe("old");
    time = 99;
    expect(await read("a", load)).toBe("old");
    time = 100;
    const pending = deferred<string>();
    load.mockReturnValueOnce(pending.promise);
    const refresh = read("a", load);
    let settled = false;
    void refresh.then(() => { settled = true; });
    await Promise.resolve();
    expect(settled).toBe(false);
    pending.resolve("new");
    expect(await refresh).toBe("new");
  });

  it("measures from load START, so a slow completed load fails at the boundary (b)", async () => {
    const first = deferred<string>();
    const load = jest.fn().mockReturnValueOnce(first.promise).mockResolvedValueOnce("fresh");
    const initial = read("slow", load);
    await Promise.resolve();
    time = 100;
    first.resolve("old");
    await expect(initial).rejects.toBeInstanceOf(CatalogDeadlineError);
    expect(await read("slow", load)).toBe("fresh");
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("single-flights 10 cold and 10 expired readers, then clears in-flight success (c)", async () => {
    const cold = deferred<string>();
    const hot = deferred<string>();
    const load = jest.fn().mockReturnValueOnce(cold.promise).mockReturnValueOnce(hot.promise);
    const first = Array.from({ length: 10 }, () => read("same", load));
    await Promise.resolve();
    expect(load).toHaveBeenCalledTimes(1);
    cold.resolve("old");
    expect(await Promise.all(first)).toEqual(Array(10).fill("old"));
    time = 100;
    const second = Array.from({ length: 10 }, () => read("same", load));
    await Promise.resolve();
    expect(load).toHaveBeenCalledTimes(2);
    hot.resolve("new");
    expect(await Promise.all(second)).toEqual(Array(10).fill("new"));
    time = 200;
    load.mockResolvedValueOnce("third");
    expect(await read("same", load)).toBe("third");
    expect(load).toHaveBeenCalledTimes(3);
  });

  it("rejects all failed refresh waiters, drops expired data and retries later (d)", async () => {
    const failed = deferred<string>();
    const load = jest.fn().mockResolvedValueOnce("old").mockReturnValueOnce(failed.promise).mockResolvedValueOnce("new");
    expect(await read("a", load)).toBe("old");
    time = 100;
    const waiters = Array.from({ length: 10 }, () => read("a", load));
    await Promise.resolve();
    expect(load).toHaveBeenCalledTimes(2);
    failed.reject(new Error("upstream failed"));
    expect((await Promise.allSettled(waiters)).every((result) => result.status === "rejected" && result.reason.message === "upstream failed")).toBe(true);
    expect(await read("a", load)).toBe("new");
    expect(load).toHaveBeenCalledTimes(3);
  });

  it("evicts the least recently used key at the key cap", async () => {
    for (let index = 0; index < CATALOG_CACHE_MAX_KEYS; index++) {
      await read(`key-${index}`, async () => index);
    }
    expect(await read("key-0", async () => -1)).toBe(0); // touch oldest
    await read("extra", async () => 999);
    expect(await read("key-1", async () => -1)).toBe(-1);
    expect(await read("key-0", async () => -1)).toBe(0);
  });

  it("evicts by total item count and never stores an oversized value (k)", async () => {
    const half = Array(CATALOG_CACHE_MAX_ITEMS / 2).fill("note");
    await read("first", async () => half);
    await read("second", async () => half);
    await read("third", async () => ["extra"]);
    expect(await read("first", async () => ["reloaded"])).toEqual(["reloaded"]);
    const oversized = Array(CATALOG_CACHE_MAX_ITEMS + 1).fill("note");
    const loader = jest.fn().mockResolvedValue(oversized);
    await read("oversized", loader);
    await read("oversized", loader);
    expect(loader).toHaveBeenCalledTimes(2);
  });

  it("clamps overrides to their class caps and ignores invalid values (f)", () => {
    for (const cap of [CATALOG_PAGE_TTL_CAP_MS, CATALOG_SITEMAP_TTL_CAP_MS]) {
      expect(catalogTtlMs(cap, "1")).toBe(1);
      expect(catalogTtlMs(cap, String(cap + 1))).toBe(cap);
      for (const value of [undefined, "", "0", "-1", "NaN", "1.2", "abc", "Infinity"]) {
        expect(catalogTtlMs(cap, value)).toBe(cap);
      }
    }
  });

  describe("load deadlines", () => {
    beforeEach(() => jest.useFakeTimers());
    afterEach(() => { jest.useRealTimers(); jest.restoreAllMocks(); });

    it("cuts a hung signal-ignoring load, aborts it, rejects ten waiters, and permits a fresh load", async () => {
      const warn = jest.spyOn(console, "warn").mockImplementation();
      let signal!: AbortSignal;
      const load = jest.fn().mockImplementationOnce((received: AbortSignal) => {
        signal = received;
        return new Promise<string>(() => undefined);
      }).mockResolvedValueOnce("fresh");
      const callers = Array.from({ length: 10 }, () => getCatalogCached("walk:page:secret-slug", 100, load));
      const settled = Promise.allSettled(callers);
      await Promise.resolve();
      expect(load).toHaveBeenCalledTimes(1);
      await jest.advanceTimersByTimeAsync(100);
      const results = await settled;
      expect(results.every((result) => result.status === "rejected" && result.reason instanceof CatalogDeadlineError)).toBe(true);
      expect(signal.aborted).toBe(true);
      expect(warn).toHaveBeenCalledWith("CATALOG_CACHE_DEADLINE class=walk:page deadlineMs=100");
      expect(jest.getTimerCount()).toBe(0);
      await expect(getCatalogCached("walk:page:secret-slug", 100, load)).resolves.toBe("fresh");
      expect(load).toHaveBeenCalledTimes(2);
      expect(jest.getTimerCount()).toBe(0);
    });

    it("ignores a late completion after the deadline and never caches it", async () => {
      jest.spyOn(console, "warn").mockImplementation();
      const late = deferred<string>();
      const load = jest.fn().mockReturnValueOnce(late.promise).mockResolvedValueOnce("fresh");
      const readers = Array.from({ length: 2 }, () => getCatalogCached("walk:sitemap:private", 200, load, { deadlineMs: 100 }));
      const settled = Promise.allSettled(readers);
      await jest.advanceTimersByTimeAsync(100);
      expect((await settled).every((result) => result.status === "rejected" && result.reason instanceof CatalogDeadlineError)).toBe(true);
      late.resolve("withdrawn");
      await jest.advanceTimersByTimeAsync(0); // flush the abandoned loader's continuation
      await expect(getCatalogCached("walk:sitemap:private", 200, load, { deadlineMs: 100 })).resolves.toBe("fresh");
      expect(load).toHaveBeenCalledTimes(2);
      expect(jest.getTimerCount()).toBe(0);
    });

    it("rejects completion exactly at the deadline and clamps a longer deadline to the TTL", async () => {
      jest.spyOn(console, "warn").mockImplementation();
      let clock = 0;
      const clockNow = () => clock;
      await expect(getCatalogCached("rail:exact", 100, async () => { clock = 100; return "old"; }, { now: clockNow })).rejects.toBeInstanceOf(CatalogDeadlineError);
      clock = 0;
      const pending = getCatalogCached("plans:clamped", 60_000, () => new Promise<string>(() => undefined), { deadlineMs: 90_000 });
      const rejected = expect(pending).rejects.toBeInstanceOf(CatalogDeadlineError);
      await jest.advanceTimersByTimeAsync(60_000);
      await rejected;
      expect(jest.getTimerCount()).toBe(0);
    });

    it("clears timers on success and failure, and does not cache when shouldCache rejects the value", async () => {
      const load = jest.fn().mockResolvedValue("value");
      await getCatalogCached("rail:no-cache", 100, load, { shouldCache: () => false });
      await getCatalogCached("rail:no-cache", 100, load, { shouldCache: () => false });
      expect(load).toHaveBeenCalledTimes(2);
      await getCatalogCached("rail:cached", 100, load, { shouldCache: () => true });
      await getCatalogCached("rail:cached", 100, load);
      expect(load).toHaveBeenCalledTimes(3);
      await expect(getCatalogCached("plans:fail", 100, async () => { throw new Error("failed"); })).rejects.toThrow("failed");
      expect(jest.getTimerCount()).toBe(0);
    });

    it("evicts an entry when the clock moves backwards", async () => {
      let clock = 10;
      const load = jest.fn().mockResolvedValueOnce("old").mockResolvedValueOnce("new");
      await getCatalogCached("rail:clock", 100, load, { now: () => clock });
      clock = 9;
      await expect(getCatalogCached("rail:clock", 100, load, { now: () => clock })).resolves.toBe("new");
      expect(load).toHaveBeenCalledTimes(2);
    });

    it("request signal aborts on timeout or parent abort, and clear removes the timer", async () => {
      const parent = new AbortController();
      const timed = createRequestSignal(parent.signal, 10);
      await jest.advanceTimersByTimeAsync(10);
      expect(timed.signal.aborted).toBe(true);
      timed.clear();
      const inherited = createRequestSignal(parent.signal, 10);
      parent.abort();
      expect(inherited.signal.aborted).toBe(true);
      inherited.clear();
      expect(jest.getTimerCount()).toBe(0);
    });
  });
});
