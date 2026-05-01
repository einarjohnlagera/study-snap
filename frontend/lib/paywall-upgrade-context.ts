import type { PaywallContextType, PaywallResumeAction } from "@/lib/paywall-content";

export type PendingPaywallUpgradeContext = {
  type: PaywallContextType;
  lastAction: PaywallResumeAction;
  noteId?: string | null;
  returnPath?: string | null;
  source?: string | null;
  createdAtMs: number;
};

const PAYWALL_UPGRADE_CONTEXT_PREFIX = "notelib.paywall-upgrade-context";
const CONTEXT_MAX_AGE_MS = 1000 * 60 * 60 * 6;
const STUDY_PACK_RESUME_QUERY = "generate=1";

function resolveKey(userId: string) {
  return `${PAYWALL_UPGRADE_CONTEXT_PREFIX}:${userId}`;
}

function isSafeInternalPath(value: string | null | undefined): value is string {
  return typeof value === "string" && value.startsWith("/") && !value.startsWith("//");
}

export function buildStudyPackResumePath(noteId: string) {
  return `/notes/${noteId}?${STUDY_PACK_RESUME_QUERY}`;
}

export function savePendingPaywallUpgradeContext(userId: string, context: PendingPaywallUpgradeContext) {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.setItem(resolveKey(userId), JSON.stringify(context));
}

export function loadPendingPaywallUpgradeContext(userId: string): PendingPaywallUpgradeContext | null {
  if (globalThis.window === undefined) {
    return null;
  }
  const raw = globalThis.localStorage.getItem(resolveKey(userId));
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<PendingPaywallUpgradeContext>;
    if (!parsed || typeof parsed !== "object" || typeof parsed.type !== "string" || typeof parsed.lastAction !== "string") {
      return null;
    }
    const createdAtMs = typeof parsed.createdAtMs === "number" ? parsed.createdAtMs : 0;
    if (createdAtMs > 0 && Date.now() - createdAtMs > CONTEXT_MAX_AGE_MS) {
      return null;
    }
    return {
      type: parsed.type as PaywallContextType,
      lastAction: parsed.lastAction as PaywallResumeAction,
      noteId: typeof parsed.noteId === "string" ? parsed.noteId : null,
      returnPath: isSafeInternalPath(parsed.returnPath) ? parsed.returnPath : null,
      source: typeof parsed.source === "string" ? parsed.source : null,
      createdAtMs: createdAtMs > 0 ? createdAtMs : Date.now(),
    };
  } catch {
    return null;
  }
}

export function clearPendingPaywallUpgradeContext(userId: string) {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.removeItem(resolveKey(userId));
}

export function resolvePaywallSuccessPath(
  context: PendingPaywallUpgradeContext | null,
  fallbackReturnPath: string | null,
) {
  if (context?.lastAction === "GENERATE_STUDY_PACK" && context.noteId) {
    return buildStudyPackResumePath(context.noteId);
  }
  if (isSafeInternalPath(context?.returnPath)) {
    return context.returnPath;
  }
  if (isSafeInternalPath(fallbackReturnPath)) {
    return fallbackReturnPath;
  }
  return "/dashboard";
}
