const DISCOVERY_INTENT_COOKIE = "notelib-discovery-intent";
const DISCOVERY_INTENT_COOKIE_MAX_AGE_SECONDS = 1800;

export const DISCOVERY_AUTH_INTENT = "discovery-adopt";
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

export function setDiscoveryIntentCookie(intent: DiscoveryIntent): void {
  if (globalThis.document === undefined) {
    return;
  }
  try {
    const value = encodeURIComponent(JSON.stringify(intent));
    globalThis.document.cookie = `${DISCOVERY_INTENT_COOKIE}=${value}; path=/; max-age=${DISCOVERY_INTENT_COOKIE_MAX_AGE_SECONDS}; SameSite=Strict`;
  } catch {
    // Cookie-blocking privacy settings must not prevent the visitor from signing up.
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
