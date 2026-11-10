import {
  CATALOG_CACHE_MAX_ITEMS,
  CATALOG_CACHE_MAX_KEYS,
  CATALOG_PROFILE_CACHE_MAX_KEYS,
  CatalogBackoffError,
  CatalogDeadlineError,
  catalogKeyClass,
  getCatalogCached,
  resetCatalogCacheForTests,
} from "./catalog-cache";

const now = () => 1_000;
const read = <T>(key: string, load: () => Promise<T>, options: { failureBackoffMs?: number; deadlineMs?: number } = {}) =>
  getCatalogCached(key, 120_000, load, { now, ...options });

describe("catalog and profile cache partitions", () => {
  beforeEach(() => resetCatalogCacheForTests());
  afterEach(() => jest.restoreAllMocks());

  it("retains the catalog's 1,024-key and 15,000-item limits", async () => {
    expect(CATALOG_CACHE_MAX_KEYS).toBe(1_024);
    expect(CATALOG_CACHE_MAX_ITEMS).toBe(15_000);
    for (let index = 0; index < CATALOG_CACHE_MAX_KEYS; index++) {
      await read(`catalog:${index}`, async () => index);
    }
    const last = jest.fn(async () => -1);
    expect(await read("catalog:1023", last)).toBe(1023);
    expect(last).not.toHaveBeenCalled();
    await read("catalog:extra", async () => "extra");
    expect(await read("catalog:0", async () => "evicted")).toBe("evicted");
    resetCatalogCacheForTests();
    const half = Array(CATALOG_CACHE_MAX_ITEMS / 2).fill("note");
    await read("catalog:first", async () => half);
    await read("catalog:second", async () => half);
    await read("catalog:third", async () => ["extra"]);
    expect(await read("catalog:first", async () => ["reloaded"])).toEqual(["reloaded"]);
  });

  it("profile entry churn cannot evict catalog or walk entries", async () => {
    const catalog = jest.fn(async () => "catalog");
    const walk = jest.fn(async () => "walk");
    await read("rail:stable", catalog);
    await read("walk:page:subject=stable", walk);
    for (let index = 0; index < CATALOG_PROFILE_CACHE_MAX_KEYS + 50; index++) {
      await read(`profile:id:${index}`, async () => index);
    }
    expect(await read("rail:stable", catalog)).toBe("catalog");
    expect(await read("walk:page:subject=stable", walk)).toBe("walk");
    expect(catalog).toHaveBeenCalledTimes(1);
    expect(walk).toHaveBeenCalledTimes(1);
    expect(await read("profile:id:0", async () => "evicted")).toBe("evicted");
    const newest = jest.fn(async () => -1);
    expect(await read(`profile:id:${CATALOG_PROFILE_CACHE_MAX_KEYS + 49}`, newest)).toBe(CATALOG_PROFILE_CACHE_MAX_KEYS + 49);
    expect(newest).not.toHaveBeenCalled();
  });

  it("profile failure churn cannot evict a catalog backoff record and is bounded", async () => {
    const fail = jest.fn(async () => { throw new Error("failed"); });
    await expect(read("plans:stable", fail, { failureBackoffMs: 15_000 })).rejects.toThrow("failed");
    for (let index = 0; index < CATALOG_PROFILE_CACHE_MAX_KEYS + 50; index++) {
      await expect(read(`profile:username:${index}`, async () => { throw new Error("failed"); }, { failureBackoffMs: 15_000 })).rejects.toThrow("failed");
    }
    await expect(read("plans:stable", fail, { failureBackoffMs: 15_000 })).rejects.toBeInstanceOf(CatalogBackoffError);
    expect(fail).toHaveBeenCalledTimes(1);
    const oldest = jest.fn(async () => "retry");
    expect(await read("profile:username:0", oldest, { failureBackoffMs: 15_000 })).toBe("retry");
    expect(oldest).toHaveBeenCalledTimes(1);
    const newest = jest.fn(async () => "unexpected");
    await expect(read(`profile:username:${CATALOG_PROFILE_CACHE_MAX_KEYS + 49}`, newest, { failureBackoffMs: 15_000 })).rejects.toBeInstanceOf(CatalogBackoffError);
    expect(newest).not.toHaveBeenCalled();
  });

  it("catalog entry churn cannot evict a profile entry", async () => {
    const profile = jest.fn(async () => "profile");
    await read("profile:id:stable", profile);
    for (let index = 0; index < CATALOG_CACHE_MAX_KEYS + 50; index++) {
      await read(`catalog:${index}`, async () => index);
    }
    expect(await read("profile:id:stable", profile)).toBe("profile");
    expect(profile).toHaveBeenCalledTimes(1);
  });

  it("classifies profile stats and logs its real deadline class", async () => {
    expect(catalogKeyClass("profile:id:alice")).toBe("profile");
    const warn = jest.spyOn(console, "warn").mockImplementation();
    jest.useFakeTimers();
    try {
      const pending = read("profile:id:slow", async () => new Promise<string>(() => {}), { deadlineMs: 15_000 });
      const assertion = expect(pending).rejects.toBeInstanceOf(CatalogDeadlineError);
      await jest.advanceTimersByTimeAsync(15_000);
      await assertion;
      expect(warn).toHaveBeenCalledWith("CATALOG_CACHE_DEADLINE class=profile deadlineMs=15000");
    } finally {
      jest.useRealTimers();
    }
  });

  it("reset clears both partitions and failure records", async () => {
    await read("rail:one", async () => "old catalog");
    await read("profile:id:one", async () => "old profile");
    await expect(read("profile:id:failure", async () => { throw new Error("failed"); }, { failureBackoffMs: 15_000 })).rejects.toThrow();
    resetCatalogCacheForTests();
    expect(await read("rail:one", async () => "new catalog")).toBe("new catalog");
    expect(await read("profile:id:one", async () => "new profile")).toBe("new profile");
    expect(await read("profile:id:failure", async () => "recovered", { failureBackoffMs: 15_000 })).toBe("recovered");
  });
});
