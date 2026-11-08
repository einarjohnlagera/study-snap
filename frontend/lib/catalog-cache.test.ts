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
  catalogKeyClass,
  CATALOG_FAILURE_BACKOFF_MS,
  CatalogBackoffError,
  CatalogDeadlineError,
  createRequestSignal,
  getCatalogCached,
  resetCatalogCacheForTests,
} from "./catalog-cache";
import { readFileSync } from "node:fs";

describe("catalog cache read statistics", () => {
  let clock = 0;
  const now = () => clock;
  let info: jest.SpyInstance;
  const read = <T>(key: string, load: () => Promise<T>, ttlMs = 100_000, failureBackoffMs = 0) =>
    getCatalogCached(key, ttlMs, load, { now, failureBackoffMs });
  const lines = () => info.mock.calls.map(([line]) => line as string).filter((line) => line.startsWith("CATALOG_CACHE_STATS "));
  const flush = async () => { clock = 60_000; await read("other:trigger", async () => "ok"); };

  beforeEach(() => {
    resetCatalogCacheForTests();
    clock = 0;
    info = jest.spyOn(console, "info").mockImplementation();
    jest.useFakeTimers();
  });
  afterEach(() => { jest.useRealTimers(); jest.restoreAllMocks(); });

  it.each([
    ["rail:x", "rail"], ["plans:x", "plans"], ["walk:sitemap:x", "walk:sitemap"],
    ["walk:page:", "walk:page:all"], ["walk:page:subject=x", "walk:page:subject"],
    ["walk:page:courseProgram=y", "walk:page:program"], ["walk:page:other=z", "walk:page:other"],
    ["unknown", "other"],
  ])("classifies %s as %s without exposing its value", (key, expected) => {
    expect(catalogKeyClass(key)).toBe(expected);
  });

  it("counts miss, join, hit, error and backoff by read, including failed waiters", async () => {
    const cold = deferred<string>();
    const miss = read("walk:page:subject=secret-slug", () => cold.promise);
    clock = 20;
    const join = read("walk:page:subject=secret-slug", () => cold.promise);
    clock = 1020;
    cold.resolve("ready");
    await expect(Promise.all([miss, join])).resolves.toEqual(["ready", "ready"]);
    await expect(read("walk:page:subject=secret-slug", async () => "wrong")).resolves.toBe("ready");
    const fail = deferred<string>();
    const failed = read("plans:secret", () => fail.promise, 100_000, 15_000);
    const failedJoin = read("plans:secret", () => fail.promise, 100_000, 15_000);
    clock = 2020;
    fail.reject(new Error("upstream"));
    expect((await Promise.allSettled([failed, failedJoin])).every((result) => result.status === "rejected")).toBe(true);
    clock = 2021;
    await expect(read("plans:secret", async () => "wrong", 100_000, 15_000)).rejects.toBeInstanceOf(CatalogBackoffError);
    expect(lines()).toHaveLength(0);
    await flush();
    expect(lines()).toHaveLength(1);
    expect(lines()[0]).toContain("walk:page:subject=[hit:1,miss:1,join:1,backoff:0,w1:0,w10:2,w10p:0,err:0]");
    expect(lines()[0]).toContain("plans=[hit:0,miss:1,join:1,backoff:1,w1:0,w10:2,w10p:0,err:2]");
    expect(jest.getTimerCount()).toBe(0);
  });

  it.each([
    [999, "w1"], [1_000, "w10"], [9_999, "w10"], [10_000, "w10p"],
  ])("buckets a miss settling at %i ms in %s", async (delay, bucket) => {
    const pending = deferred<string>();
    const result = read("rail:boundary", () => pending.promise);
    clock = delay;
    pending.resolve("ok");
    await result;
    await flush();
    expect(lines()[0]).toContain(`rail=[hit:0,miss:1,join:0,backoff:0,w1:${bucket === "w1" ? 1 : 0},w10:${bucket === "w10" ? 1 : 0},w10p:${bucket === "w10p" ? 1 : 0},err:0]`);
  });

  it.each([
    [999, 1, 1, 0], [1_000, 0, 2, 0], [9_999, 0, 1, 1], [10_000, 0, 0, 2],
  ])("buckets a join waiting %i ms at its exact boundary", async (delay, w1, w10, w10p) => {
    const pending = deferred<string>();
    const miss = read("rail:joined", () => pending.promise);
    clock = 100;
    const join = read("rail:joined", () => pending.promise);
    clock = 100 + delay;
    pending.resolve("ok");
    await Promise.all([miss, join]);
    await flush();
    expect(lines()[0]).toContain(`rail=[hit:0,miss:1,join:1,backoff:0,w1:${w1},w10:${w10},w10p:${w10p},err:0]`);
  });

  it("flushes once at 60 seconds before recording the new read, then starts a fresh window", async () => {
    await read("rail:first", async () => "ok");
    clock = 59_999;
    await read("rail:first", async () => "ok");
    expect(lines()).toHaveLength(0);
    await flush();
    expect(lines()).toHaveLength(1);
    expect(lines()[0]).toMatch(/^CATALOG_CACHE_STATS inst=[0-9a-f]{6} windowMs=60000 rail=\[hit:1,miss:1,join:0,backoff:0,w1:1,w10:0,w10p:0,err:0\]$/);
    clock = 120_000;
    await read("other:trigger", async () => "ok");
    expect(lines()).toHaveLength(2);
    expect(lines()[1]).toContain("other=[hit:0,miss:1,join:0");
    expect(lines()[1]).not.toContain("rail=");
  });

  it("does not emit again inside the new window that a flush starts", async () => {
    await read("rail:a", async () => "ok");
    clock = 60_000;
    await read("rail:a", async () => "ok");
    expect(lines()).toHaveLength(1);
    clock = 61_000;
    await read("rail:a", async () => "ok");
    clock = 119_999;
    await read("rail:a", async () => "ok");
    expect(lines()).toHaveLength(1);
    clock = 120_000;
    await read("rail:a", async () => "ok");
    expect(lines()).toHaveLength(2);
    expect(lines()[1]).toMatch(/windowMs=60000 /);
  });

  it("resets without emitting when the injected clock moves backwards", async () => {
    clock = 10_000;
    await read("rail:old", async () => "old");
    clock = 9_999;
    await read("plans:new", async () => "new");
    expect(lines()).toHaveLength(0);
    clock = 70_000;
    await read("other:trigger", async () => "ok");
    expect(lines()).toHaveLength(1);
    expect(lines()[0]).toContain("plans=[");
    expect(lines()[0]).not.toContain("rail=");
  });

  it("keeps a late settlement bucket after the decision window has flushed", async () => {
    const pending = deferred<string>();
    const cold = read("rail:late", () => pending.promise);
    clock = 60_000;
    await read("other:trigger", async () => "ok");
    expect(lines()).toHaveLength(1);
    clock = 60_001;
    pending.resolve("late");
    await cold;
    clock = 120_000;
    await read("other:trigger", async () => "ok");
    expect(lines()[1]).toContain("rail=[hit:0,miss:0,join:0,backoff:0,w1:0,w10:0,w10p:1,err:0]");
  });

  it("guards an empty statistics window from emitting a zero line", () => {
    // An external read always increments a counter; this guard's zero state is internal only.
    const source = readFileSync(`${process.cwd()}/lib/catalog-cache.ts`, "utf8");
    expect(source).toMatch(/function flushCacheStats\(elapsed: number\): void \{\s*if \(stats\.size === 0\) return;/);
  });

  it("logs only fixed fields in fixed class order and never a key, slug or query", async () => {
    await read("walk:page:subject=secret-slug", async () => "ok");
    await read("rail:secret-rail", async () => "ok");
    await flush();
    expect(lines()).toHaveLength(1);
    expect(lines()[0]).toMatch(/^CATALOG_CACHE_STATS inst=[0-9a-f]{6} windowMs=60000 rail=\[hit:\d+,miss:\d+,join:\d+,backoff:\d+,w1:\d+,w10:\d+,w10p:\d+,err:\d+\] walk:page:subject=\[hit:\d+,miss:\d+,join:\d+,backoff:\d+,w1:\d+,w10:\d+,w10p:\d+,err:\d+\]$/);
    expect(lines()[0]).not.toContain("secret-slug");
    expect(lines()[0]).not.toContain("secret-rail");
    expect(jest.getTimerCount()).toBe(0);
  });

  it("orders rail before plans even when plans is read first", async () => {
    await read("plans:first", async () => "ok");
    await read("rail:second", async () => "ok");
    await flush();
    expect(lines()[0]).toMatch(/windowMs=60000 rail=\[[^\]]+\] plans=\[[^\]]+\]$/);
  });

  it("keeps reads successful when console.info throws during a flush", async () => {
    await read("rail:x", async () => "first");
    info.mockImplementation(() => { throw new Error("logger unavailable"); });
    clock = 60_000;
    await expect(read("rail:x", async () => "second")).resolves.toBe("first");
    expect(jest.getTimerCount()).toBe(0);
  });
});

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
  describe("failure backoff", () => {
    let clock = 0;
    const opts = { now: () => clock, failureBackoffMs: 1_000 };
    beforeEach(() => { clock = 0; });

    it("rejects without calling the loader inside the backoff and loads again exactly at its end", async () => {
      const load = jest.fn().mockRejectedValueOnce(new Error("upstream")).mockResolvedValueOnce("recovered");
      await expect(getCatalogCached("walk:page:a", 100_000, load, opts)).rejects.toThrow("upstream");
      clock = 999;
      await expect(getCatalogCached("walk:page:a", 100_000, load, opts)).rejects.toBeInstanceOf(CatalogBackoffError);
      expect(load).toHaveBeenCalledTimes(1);
      clock = 1_000;
      await expect(getCatalogCached("walk:page:a", 100_000, load, opts)).resolves.toBe("recovered");
      expect(load).toHaveBeenCalledTimes(2);
    });

    it("backs off after a deadline too, and never serves the expired entry during the backoff", async () => {
      jest.useFakeTimers();
      jest.spyOn(console, "warn").mockImplementation();
      try {
        const fresh = jest.fn().mockResolvedValueOnce("old");
        await getCatalogCached("rail:b", 100, fresh, { now: () => Date.now(), failureBackoffMs: 1_000 });
        await jest.advanceTimersByTimeAsync(100);
        const hung = jest.fn().mockImplementation(() => new Promise<string>(() => undefined));
        const pending = expect(getCatalogCached("rail:b", 100, hung, { now: () => Date.now(), failureBackoffMs: 1_000 })).rejects.toBeInstanceOf(CatalogDeadlineError);
        await jest.advanceTimersByTimeAsync(100);
        await pending;
        await expect(getCatalogCached("rail:b", 100, hung, { now: () => Date.now(), failureBackoffMs: 1_000 })).rejects.toBeInstanceOf(CatalogBackoffError);
        expect(hung).toHaveBeenCalledTimes(1);
        expect(jest.getTimerCount()).toBe(0);
      } finally {
        jest.useRealTimers();
        jest.restoreAllMocks();
      }
    });

    it("rejects ten concurrent callers during the backoff with a single earlier load", async () => {
      const load = jest.fn().mockRejectedValue(new Error("upstream"));
      await expect(getCatalogCached("plans:c", 100_000, load, opts)).rejects.toThrow("upstream");
      clock = 10;
      const results = await Promise.allSettled(Array.from({ length: 10 }, () => getCatalogCached("plans:c", 100_000, load, opts)));
      expect(results.every((result) => result.status === "rejected" && result.reason instanceof CatalogBackoffError)).toBe(true);
      expect(load).toHaveBeenCalledTimes(1);
    });

    it("keeps today's immediate retry when no backoff is configured", async () => {
      const load = jest.fn().mockRejectedValueOnce(new Error("upstream")).mockResolvedValueOnce("ok");
      await expect(getCatalogCached("walk:page:d", 100_000, load, { now: () => clock })).rejects.toThrow("upstream");
      await expect(getCatalogCached("walk:page:d", 100_000, load, { now: () => clock })).resolves.toBe("ok");
    });

    it("ends the backoff when the clock moves backwards instead of extending it", async () => {
      clock = 5_000;
      const load = jest.fn().mockRejectedValueOnce(new Error("upstream")).mockResolvedValueOnce("ok");
      await expect(getCatalogCached("walk:page:e", 100_000, load, opts)).rejects.toThrow("upstream");
      clock = 4_000;
      await expect(getCatalogCached("walk:page:e", 100_000, load, opts)).resolves.toBe("ok");
    });

    it("bounds the failure records: after 1,100 distinct failing keys the oldest is no longer blocked", async () => {
      const load = jest.fn().mockRejectedValue(new Error("upstream"));
      for (let index = 0; index < CATALOG_CACHE_MAX_KEYS + 76; index += 1) {
        await expect(getCatalogCached(`walk:page:f${index}`, 100_000, load, opts)).rejects.toThrow("upstream");
      }
      const calls = load.mock.calls.length;
      await expect(getCatalogCached("walk:page:f0", 100_000, load, opts)).rejects.toThrow("upstream");
      expect(load.mock.calls.length).toBe(calls + 1);
      await expect(getCatalogCached(`walk:page:f${CATALOG_CACHE_MAX_KEYS + 75}`, 100_000, load, opts)).rejects.toBeInstanceOf(CatalogBackoffError);
      expect(load.mock.calls.length).toBe(calls + 1);
    });

    it("a reset clears the failure records", async () => {
      const load = jest.fn().mockRejectedValueOnce(new Error("upstream")).mockResolvedValueOnce("ok");
      await expect(getCatalogCached("walk:page:g", 100_000, load, opts)).rejects.toThrow("upstream");
      resetCatalogCacheForTests();
      await expect(getCatalogCached("walk:page:g", 100_000, load, opts)).resolves.toBe("ok");
    });

    it("exports a 15 s production backoff", () => {
      expect(CATALOG_FAILURE_BACKOFF_MS).toBe(15_000);
    });
  });
});
