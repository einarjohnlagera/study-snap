import {
  CATALOG_CACHE_MAX_ITEMS,
  CATALOG_CACHE_MAX_KEYS,
  CATALOG_PAGE_TTL_CAP_MS,
  CATALOG_SITEMAP_TTL_CAP_MS,
  catalogTtlMs,
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

  it("measures from load START, so a slow completed load is immediately expired (b)", async () => {
    const first = deferred<string>();
    const load = jest.fn().mockReturnValueOnce(first.promise).mockResolvedValueOnce("fresh");
    const initial = read("slow", load);
    await Promise.resolve();
    time = 100;
    first.resolve("old");
    expect(await initial).toBe("old");
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
});
