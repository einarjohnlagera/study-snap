/** In-process catalog cache. Entries expire from the start of their load, including rebuild time. */
export const CATALOG_PAGE_TTL_CAP_MS = 120_000;
export const CATALOG_SITEMAP_TTL_CAP_MS = 3_600_000;
export const CATALOG_CACHE_MAX_KEYS = 1024;
export const CATALOG_CACHE_MAX_ITEMS = 15_000;
export const CATALOG_PAGE_WALK_DEADLINE_MS = 90_000;
export const CATALOG_SITEMAP_WALK_DEADLINE_MS = 240_000;
export const CATALOG_WALK_PAGE_REQUEST_TIMEOUT_MS = 15_000;
export const CATALOG_RAIL_REQUEST_TIMEOUT_MS = 10_000;
export const CATALOG_PLAN_REQUEST_TIMEOUT_MS = 10_000;
export const CATALOG_WALK_MAX_PAGES = 400;
/** After a failed load the key rejects for this long without calling the backend (never serves data). */
export const CATALOG_FAILURE_BACKOFF_MS = 15_000;

export class CatalogBackoffError extends Error {
  constructor() {
    super("Public catalog is temporarily unavailable.");
    this.name = "CatalogBackoffError";
  }
}

export class CatalogDeadlineError extends Error {
  constructor() {
    super("Public catalog load exceeded its deadline.");
    this.name = "CatalogDeadlineError";
  }
}

export function createRequestSignal(parent: AbortSignal, timeoutMs: number): { signal: AbortSignal; clear: () => void } {
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (parent.aborted) abort();
  else parent.addEventListener("abort", abort, { once: true });
  const timer = globalThis.setTimeout(abort, timeoutMs);
  return {
    signal: controller.signal,
    clear: () => {
      globalThis.clearTimeout(timer);
      parent.removeEventListener("abort", abort);
    },
  };
}

type Entry = { value: unknown; startedAt: number; itemCount: number };
const entries = new Map<string, Entry>();
const inFlight = new Map<string, Promise<unknown>>();
// key -> start of the failure, so a backwards clock cannot extend a backoff. Bounded like `entries`.
const failures = new Map<string, number>();
let totalItems = 0;

export function catalogTtlMs(capMs: number, override: string | undefined): number {
  if (!override || !/^\d+$/.test(override.trim())) return capMs;
  const parsed = Number(override);
  return Number.isSafeInteger(parsed) && parsed > 0 ? Math.min(parsed, capMs) : capMs;
}

export function resetCatalogCacheForTests(): void {
  entries.clear();
  inFlight.clear();
  failures.clear();
  totalItems = 0;
}

function remove(key: string): void {
  const entry = entries.get(key);
  if (entry) totalItems -= entry.itemCount;
  entries.delete(key);
}

export async function getCatalogCached<T>(
  key: string,
  ttlMs: number,
  load: (signal: AbortSignal) => Promise<T>,
  options: {
    now?: () => number;
    hardCapMs?: number;
    deadlineMs?: number;
    shouldCache?: (value: T) => boolean;
    failureBackoffMs?: number;
  } = {},
): Promise<T> {
  const now = options.now ?? Date.now;
  const effectiveTtl = Math.min(ttlMs, options.hardCapMs ?? ttlMs);
  const effectiveDeadline = Math.min(options.deadlineMs ?? effectiveTtl, effectiveTtl);
  const entry = entries.get(key);
  if (entry && now() - entry.startedAt < effectiveTtl && now() >= entry.startedAt) {
    entries.delete(key);
    entries.set(key, entry); // LRU touch
    return entry.value as T;
  }
  if (entry) remove(key);

  const pending = inFlight.get(key);
  if (pending) return pending as Promise<T>;

  const backoffMs = options.failureBackoffMs ?? 0;
  const failedAt = failures.get(key);
  if (failedAt !== undefined) {
    const sinceFailure = now() - failedAt;
    // A clock that moved backwards, or a smaller configured backoff, ends the block instead of extending it.
    if (sinceFailure >= 0 && sinceFailure < backoffMs) throw new CatalogBackoffError();
    failures.delete(key);
  }

  const startedAt = now();
  const controller = new AbortController();
  const deadlineError = () => {
    const logClass = key.startsWith("walk:page:") ? "walk:page"
      : key.startsWith("walk:sitemap:") ? "walk:sitemap"
        : key.startsWith("rail:") ? "rail" : "plans";
    console.warn(`CATALOG_CACHE_DEADLINE class=${logClass} deadlineMs=${effectiveDeadline}`);
    return new CatalogDeadlineError();
  };
  let timer!: ReturnType<typeof globalThis.setTimeout>;
  const deadline = new Promise<never>((_, reject) => {
    timer = globalThis.setTimeout(() => {
      controller.abort();
      reject(deadlineError());
    }, effectiveDeadline);
  });
  // The promise is assigned before its async finally runs; the identity check protects a newer load.
  let refresh!: Promise<T>;
  // eslint-disable-next-line prefer-const
  refresh = (async () => {
    try {
      const value = await Promise.race([Promise.resolve().then(() => load(controller.signal)), deadline]);
      if (now() - startedAt >= effectiveDeadline) throw deadlineError();
      const itemCount = Array.isArray(value) ? value.length : 0;
      if (itemCount <= CATALOG_CACHE_MAX_ITEMS && (!options.shouldCache || options.shouldCache(value))) {
        remove(key);
        entries.set(key, { value, startedAt, itemCount });
        totalItems += itemCount;
        while (entries.size > CATALOG_CACHE_MAX_KEYS || totalItems > CATALOG_CACHE_MAX_ITEMS) {
          remove(entries.keys().next().value as string);
        }
      }
      return value;
    } catch (error) {
      remove(key);
      if (backoffMs > 0) {
        failures.delete(key);
        failures.set(key, now());
        while (failures.size > CATALOG_CACHE_MAX_KEYS) failures.delete(failures.keys().next().value as string);
      }
      throw error;
    } finally {
      globalThis.clearTimeout(timer);
      if (inFlight.get(key) === refresh) inFlight.delete(key);
    }
  })();
  inFlight.set(key, refresh);
  return refresh;
}
