const JUST_ADOPTED_NOTICE_PREFIX = "notelib-just-adopted-";

function getJustAdoptedNoticeKey(collectionId: string): string {
  return `${JUST_ADOPTED_NOTICE_PREFIX}${collectionId}`;
}

/**
 * Records that a top-level Goal was just adopted so the destination page can
 * surface a one-time setup tip. Best-effort: storage failures simply skip it.
 */
export function setJustAdoptedNotice(collectionId: string): void {
  try {
    globalThis.sessionStorage?.setItem(getJustAdoptedNoticeKey(collectionId), "1");
  } catch {
    // sessionStorage may be unavailable; the guidance tip is best-effort.
  }
}

/**
 * Reads and clears the just-adopted flag for a collection.
 */
export function getJustAdoptedNotice(collectionId: string): boolean {
  try {
    const key = getJustAdoptedNoticeKey(collectionId);
    const raw = globalThis.sessionStorage?.getItem(key);
    if (!raw) {
      return false;
    }
    globalThis.sessionStorage?.removeItem(key);
    return true;
  } catch {
    return false;
  }
}
