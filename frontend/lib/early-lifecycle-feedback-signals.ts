import { hasSeenTipThisSession, markTipSeenThisSession } from "@/lib/guidance";

const SESSION_CAP_ID = "early-lifecycle-feedback-signal-shown-this-session";
const VIEWED_NOTES_STORAGE_KEY = "notelib-public-library-viewed-notes";
const ADOPTED_STORAGE_KEY = "notelib-public-library-adopted-this-session";
const DISCOVERY_FRICTION_VIEW_THRESHOLD = 3;

export function hasShownEarlyLifecycleFeedbackSignalThisSession(): boolean {
  return hasSeenTipThisSession(SESSION_CAP_ID);
}

export function markEarlyLifecycleFeedbackSignalShownThisSession(): void {
  markTipSeenThisSession(SESSION_CAP_ID);
}

function readViewedNoteIds(): string[] {
  try {
    const raw = globalThis.sessionStorage?.getItem(VIEWED_NOTES_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

export function recordPublicNoteViewedWithoutAdopting(noteId: string): void {
  try {
    const viewed = new Set(readViewedNoteIds());
    viewed.add(noteId);
    globalThis.sessionStorage?.setItem(VIEWED_NOTES_STORAGE_KEY, JSON.stringify([...viewed]));
  } catch {
    // sessionStorage unavailable — the discovery-friction signal simply won't fire this session
  }
}

export function markPublicLibraryNoteAdoptedThisSession(): void {
  try {
    globalThis.sessionStorage?.setItem(ADOPTED_STORAGE_KEY, "1");
  } catch {
    // ignore — worst case the discovery prompt fires once despite an adoption
  }
}

export function hasPublicLibraryDiscoveryFriction(): boolean {
  try {
    if (globalThis.sessionStorage?.getItem(ADOPTED_STORAGE_KEY) === "1") {
      return false;
    }
    return readViewedNoteIds().length >= DISCOVERY_FRICTION_VIEW_THRESHOLD;
  } catch {
    return false;
  }
}
