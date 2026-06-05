const EXAM_INTENT_COOKIE = "notelib-exam-intent";
const EXAM_INTENT_COOKIE_MAX_AGE_SECONDS = 1800;

export function setExamIntentCookie(examSlug: string): void {
  if (globalThis.document === undefined) {
    return;
  }
  globalThis.document.cookie = `${EXAM_INTENT_COOKIE}=${encodeURIComponent(examSlug)}; path=/; max-age=${EXAM_INTENT_COOKIE_MAX_AGE_SECONDS}; SameSite=Strict`;
}

export function getExamIntentCookie(): string | null {
  if (globalThis.document === undefined) {
    return null;
  }
  const cookie = globalThis.document.cookie
    .split("; ")
    .find((item) => item.startsWith(`${EXAM_INTENT_COOKIE}=`));
  if (!cookie) {
    return null;
  }
  const value = cookie.slice(EXAM_INTENT_COOKIE.length + 1);
  return value ? decodeURIComponent(value) : null;
}

export function clearExamIntentCookie(): void {
  if (globalThis.document === undefined) {
    return;
  }
  globalThis.document.cookie = `${EXAM_INTENT_COOKIE}=; path=/; max-age=0; SameSite=Strict`;
}
