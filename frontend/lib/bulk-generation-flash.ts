const BULK_QUEUED_FLASH_KEY = "notelib.bulk.queuedFlash";

// One-shot flash passed from the bulk-generate page to the Library after redirect.
// sessionStorage (not a query param) because the Library rewrites its own URL from
// filter state, which would strip a param before it could be read.

export function setBulkQueuedFlash(count: number): void {
  try {
    globalThis.sessionStorage?.setItem(BULK_QUEUED_FLASH_KEY, String(count));
  } catch {
    // sessionStorage may be unavailable (private mode, SSR) — the toast is non-critical.
  }
}

export function consumeBulkQueuedFlash(): number | null {
  try {
    const raw = globalThis.sessionStorage?.getItem(BULK_QUEUED_FLASH_KEY);
    if (!raw) {
      return null;
    }
    globalThis.sessionStorage.removeItem(BULK_QUEUED_FLASH_KEY);
    const count = Number(raw);
    return Number.isFinite(count) && count > 0 ? count : null;
  } catch {
    return null;
  }
}
