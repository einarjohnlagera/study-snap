const GUIDANCE_STORAGE_PREFIX = "notelib-guidance-dismissed-";
const GUIDANCE_SESSION_STORAGE_PREFIX = "notelib-guidance-seen-this-session-";

export function getUserScopedGuidanceId(tipId: string, userId: string): string {
  return `${tipId}-${userId}`;
}

export function hasSeenTip(tipId: string): boolean {
  try {
    return globalThis.localStorage?.getItem(`${GUIDANCE_STORAGE_PREFIX}${tipId}`) === "1";
  } catch {
    return false;
  }
}

export function markTipSeen(tipId: string): void {
  try {
    globalThis.localStorage?.setItem(`${GUIDANCE_STORAGE_PREFIX}${tipId}`, "1");
  } catch {
    // ignore
  }
}

export function hasSeenTipThisSession(tipId: string): boolean {
  try {
    return globalThis.sessionStorage?.getItem(`${GUIDANCE_SESSION_STORAGE_PREFIX}${tipId}`) === "1";
  } catch {
    return false;
  }
}

export function markTipSeenThisSession(tipId: string): void {
  try {
    globalThis.sessionStorage?.setItem(`${GUIDANCE_SESSION_STORAGE_PREFIX}${tipId}`, "1");
  } catch {
    // ignore
  }
}
