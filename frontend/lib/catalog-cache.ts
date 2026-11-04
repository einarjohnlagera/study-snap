/** In-process catalog cache. Entries expire from the start of their load, including rebuild time. */
export const CATALOG_PAGE_TTL_CAP_MS = 120_000;
export const CATALOG_SITEMAP_TTL_CAP_MS = 3_600_000;
export const CATALOG_CACHE_MAX_KEYS = 256;
export const CATALOG_CACHE_MAX_ITEMS = 15_000;

type Entry = { value: unknown; startedAt: number; itemCount: number };
const entries = new Map<string, Entry>();
const inFlight = new Map<string, Promise<unknown>>();
let totalItems = 0;

export function catalogTtlMs(capMs: number, override: string | undefined): number {
  if (!override || !/^\d+$/.test(override.trim())) return capMs;
  const parsed = Number(override);
  return Number.isSafeInteger(parsed) && parsed > 0 ? Math.min(parsed, capMs) : capMs;
}

export function resetCatalogCacheForTests(): void {
  entries.clear();
  inFlight.clear();
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
  load: () => Promise<T>,
  options: { now?: () => number; hardCapMs?: number } = {},
): Promise<T> {
  const now = options.now ?? Date.now;
  const effectiveTtl = Math.min(ttlMs, options.hardCapMs ?? ttlMs);
  const entry = entries.get(key);
  if (entry && now() - entry.startedAt < effectiveTtl && now() >= entry.startedAt) {
    entries.delete(key);
    entries.set(key, entry); // LRU touch
    return entry.value as T;
  }
  if (entry) remove(key);

  const pending = inFlight.get(key);
  if (pending) return pending as Promise<T>;

  let startedAt = 0;
  const refresh = (async () => {
    try {
      const value = await Promise.resolve().then(() => {
        startedAt = now();
        return load();
      });
      const itemCount = Array.isArray(value) ? value.length : 0;
      if (now() - startedAt < effectiveTtl && itemCount <= CATALOG_CACHE_MAX_ITEMS) {
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
      throw error;
    } finally {
      inFlight.delete(key);
    }
  })();
  inFlight.set(key, refresh);
  return refresh;
}
