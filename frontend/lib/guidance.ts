const GUIDANCE_STORAGE_PREFIX = "notelib-guidance-dismissed-";

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
