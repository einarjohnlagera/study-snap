const STORAGE_PREFIX = "notelib-dashboard-personalization-prompt";

function buildStorageKey(userId: string): string {
  return `${STORAGE_PREFIX}:${userId}`;
}

export function hasDismissedDashboardPersonalizationPrompt(userId: string): boolean {
  if (globalThis.window === undefined) {
    return false;
  }
  try {
    return globalThis.localStorage.getItem(buildStorageKey(userId)) === "1";
  } catch {
    return false;
  }
}

export function dismissDashboardPersonalizationPrompt(userId: string): void {
  if (globalThis.window === undefined) {
    return;
  }
  try {
    globalThis.localStorage.setItem(buildStorageKey(userId), "1");
  } catch {
    // ignore local storage failures and keep the prompt visible next time
  }
}
