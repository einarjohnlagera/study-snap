const COPIED_QUERY_PARAM = "copied";
const COPY_INTENT_COOKIE = "notelib-copy-intent";
const COPY_INTENT_COOKIE_MAX_AGE_SECONDS = 1800;
const GENERATE_QUERY_PARAM = "generate";
const START_QUICK_REVIEW_QUERY_PARAM = "startQuickReview";

export type PublicCopyRedirectTarget = "library" | "generate" | "quick-review";

export function buildCopiedNotePath(noteId: string, redirectTarget: PublicCopyRedirectTarget = "library") {
  const next = new URLSearchParams({ [COPIED_QUERY_PARAM]: "1" });

  if (redirectTarget === "generate" || redirectTarget === "quick-review") {
    next.set(GENERATE_QUERY_PARAM, "1");
  }
  if (redirectTarget === "quick-review") {
    next.set(START_QUICK_REVIEW_QUERY_PARAM, "1");
  }

  return `/notes/${noteId}?${next.toString()}`;
}

export function buildPublicCopyIntentQuery(redirectTarget: PublicCopyRedirectTarget = "library") {
  const next = new URLSearchParams({ copy: "1" });
  next.set("intent", redirectTarget);
  return next.toString();
}

export function setCopyIntentCookie(noteId: string): void {
  if (globalThis.document === undefined) {
    return;
  }
  globalThis.document.cookie = `${COPY_INTENT_COOKIE}=${encodeURIComponent(noteId)}; path=/; max-age=${COPY_INTENT_COOKIE_MAX_AGE_SECONDS}; SameSite=Strict`;
}

export function getCopyIntentCookie(): string | null {
  if (globalThis.document === undefined) {
    return null;
  }
  const cookie = globalThis.document.cookie
    .split("; ")
    .find((item) => item.startsWith(`${COPY_INTENT_COOKIE}=`));
  if (!cookie) {
    return null;
  }
  const value = cookie.slice(COPY_INTENT_COOKIE.length + 1);
  return value ? decodeURIComponent(value) : null;
}

export function clearCopyIntentCookie(): void {
  if (globalThis.document === undefined) {
    return;
  }
  globalThis.document.cookie = `${COPY_INTENT_COOKIE}=; path=/; max-age=0; SameSite=Strict`;
}

export const PUBLIC_NOTE_COPY_QUERY_PARAMS = {
  copied: COPIED_QUERY_PARAM,
  generate: GENERATE_QUERY_PARAM,
  startQuickReview: START_QUICK_REVIEW_QUERY_PARAM,
} as const;
