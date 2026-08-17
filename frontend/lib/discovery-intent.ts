const DISCOVERY_INTENT_COOKIE = "notelib-discovery-intent";
const DISCOVERY_INTENT_COOKIE_MAX_AGE_SECONDS = 1800;

export const DISCOVERY_AUTH_INTENT = "discovery-adopt";
// Used when the intent could not be stored, so /auth can promise resumption only when it is real.
export const DISCOVERY_AUTH_INTENT_UNSAVED = "discovery-adopt-unsaved";
export const DISCOVERY_NOTICE_QUERY_PARAM = "discoveryNotice";
export const DISCOVERY_ADOPT_UNAVAILABLE_NOTICE = "adopt-unavailable";

export type DiscoveryIntent = {
  planId: string;
  planType: "goal" | "study-plan";
  returnPath: string;
};

function isExplorePath(value: unknown): value is string {
  return typeof value === "string" && (value === "/explore" || value.startsWith("/explore?"));
}

function isDiscoveryIntent(value: unknown): value is DiscoveryIntent {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Partial<DiscoveryIntent>;
  return typeof candidate.planId === "string"
    && candidate.planId.trim().length > 0
    && (candidate.planType === "goal" || candidate.planType === "study-plan")
    && isExplorePath(candidate.returnPath);
}

/**
 * Returns whether the intent was actually stored. Callers need to know: the cookie is the only
 * carrier that survives verify-email and onboarding (separate page loads, so nothing in memory
 * lasts), and `redirect` is dropped on the signup path. If the write fails, the adoption cannot be
 * resumed — and the visitor should be told that rather than completing signup expecting a plan that
 * will never arrive. Swallowing the failure silently is what this return value exists to prevent.
 */
export function setDiscoveryIntentCookie(intent: DiscoveryIntent): boolean {
  if (globalThis.document === undefined) {
    return false;
  }
  try {
    const value = encodeURIComponent(JSON.stringify(intent));
    globalThis.document.cookie = `${DISCOVERY_INTENT_COOKIE}=${value}; path=/; max-age=${DISCOVERY_INTENT_COOKIE_MAX_AGE_SECONDS}; SameSite=Strict`;
    // Read back rather than trusting the assignment: a blocked cookie jar accepts the write
    // silently and stores nothing, so the assignment itself is not evidence.
    return getDiscoveryIntentCookie() !== null;
  } catch {
    // Cookie-blocking privacy settings must not prevent the visitor from signing up.
    return false;
  }
}

export function getDiscoveryIntentCookie(): DiscoveryIntent | null {
  if (globalThis.document === undefined) {
    return null;
  }
  try {
    const cookie = globalThis.document.cookie
      .split("; ")
      .find((item) => item.startsWith(`${DISCOVERY_INTENT_COOKIE}=`));
    if (!cookie) {
      return null;
    }
    const value = cookie.slice(DISCOVERY_INTENT_COOKIE.length + 1);
    const parsed: unknown = JSON.parse(decodeURIComponent(value));
    if (isDiscoveryIntent(parsed)) {
      return parsed;
    }
  } catch {
    // Malformed and partially written values are treated as expired intent.
  }
  clearDiscoveryIntentCookie();
  return null;
}

export function clearDiscoveryIntentCookie(): void {
  if (globalThis.document === undefined) {
    return;
  }
  try {
    globalThis.document.cookie = `${DISCOVERY_INTENT_COOKIE}=; path=/; max-age=0; SameSite=Strict`;
  } catch {
    // Cookie access can be blocked; there is nothing else to clear client-side.
  }
}

export function buildDiscoveryIntentFallbackPath(returnPath: string): string {
  const safeReturnPath = isExplorePath(returnPath) ? returnPath : "/explore";
  const [pathname, query = ""] = safeReturnPath.split("?", 2);
  const params = new URLSearchParams(query);
  params.set(DISCOVERY_NOTICE_QUERY_PARAM, DISCOVERY_ADOPT_UNAVAILABLE_NOTICE);
  return `${pathname}?${params.toString()}`;
}
