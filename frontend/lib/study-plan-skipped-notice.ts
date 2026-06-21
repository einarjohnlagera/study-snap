const SKIPPED_NOTICE_PREFIX = "notelib-study-plan-skipped-";

function getSkippedNoticeKey(collectionId: string): string {
  return `${SKIPPED_NOTICE_PREFIX}${collectionId}`;
}

/**
 * Records how many source notes were skipped while adopting a study plan so the
 * destination collection page can surface a one-time notice. Best-effort: a
 * missing or throwing `sessionStorage` simply means no notice is shown.
 */
export function setStudyPlanSkippedNotice(collectionId: string, skippedCount: number): void {
  if (skippedCount <= 0) {
    return;
  }
  try {
    globalThis.sessionStorage?.setItem(getSkippedNoticeKey(collectionId), String(skippedCount));
  } catch {
    // sessionStorage may be unavailable; the skipped notice is best-effort.
  }
}

/**
 * Reads and clears the skipped-note count for a collection. Returns null when
 * there is nothing to show.
 */
export function getStudyPlanSkippedNotice(collectionId: string): number | null {
  try {
    const key = getSkippedNoticeKey(collectionId);
    const raw = globalThis.sessionStorage?.getItem(key);
    if (!raw) {
      return null;
    }
    globalThis.sessionStorage?.removeItem(key);
    const parsed = Number.parseInt(raw, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  } catch {
    return null;
  }
}
