const COLLECTION_ACTION_NOTICE_KEY = "notelib-collection-action-notice";

/**
 * Records a one-time success message to show after navigating away (e.g. Delete
 * returning to the list page). Best-effort: storage failures simply skip it.
 */
export function setCollectionActionNotice(message: string): void {
  try {
    globalThis.sessionStorage?.setItem(COLLECTION_ACTION_NOTICE_KEY, message);
  } catch {
    // sessionStorage may be unavailable; the notice is best-effort.
  }
}

/**
 * Reads and clears the pending action notice, if any.
 */
export function getCollectionActionNotice(): string | null {
  try {
    const raw = globalThis.sessionStorage?.getItem(COLLECTION_ACTION_NOTICE_KEY);
    if (!raw) {
      return null;
    }
    globalThis.sessionStorage?.removeItem(COLLECTION_ACTION_NOTICE_KEY);
    return raw;
  } catch {
    return null;
  }
}
