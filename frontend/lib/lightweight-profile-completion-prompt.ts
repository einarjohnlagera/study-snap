const DISMISSAL_STORAGE_PREFIX = "notelib-lightweight-profile-completion-dismissed";

function buildStorageKey(userId: string): string {
  return `${DISMISSAL_STORAGE_PREFIX}:${userId}`;
}

function getCurrentDay(): string {
  return new Date().toISOString().slice(0, 10);
}

export function hasDismissedLightweightProfileCompletionPrompt(userId: string): boolean {
  if (globalThis.window === undefined) {
    return false;
  }
  try {
    return globalThis.localStorage.getItem(buildStorageKey(userId)) === getCurrentDay();
  } catch {
    return false;
  }
}

export function dismissLightweightProfileCompletionPrompt(userId: string): void {
  if (globalThis.window === undefined) {
    return;
  }
  try {
    globalThis.localStorage.setItem(buildStorageKey(userId), getCurrentDay());
  } catch {
    // Keep the prompt visible on a later visit when dismissal storage is unavailable.
  }
}
