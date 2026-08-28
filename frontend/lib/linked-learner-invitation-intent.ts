const INVITATION_LINK_INTENT_COOKIE = "notelib-connection-invite";
const INVITATION_LINK_INTENT_MAX_AGE_SECONDS = 1800;
const TOKEN_PATTERN = /^[0-9A-Za-z]{22}$/;

export function buildLinkedLearnerInvitationPath(token: string): string {
  return `/linked-learners/invite/${encodeURIComponent(token)}`;
}

export function setLinkedLearnerInvitationIntentCookie(token: string): void {
  if (globalThis.document === undefined || !TOKEN_PATTERN.test(token)) return;
  globalThis.document.cookie = `${INVITATION_LINK_INTENT_COOKIE}=${encodeURIComponent(token)}; path=/; max-age=${INVITATION_LINK_INTENT_MAX_AGE_SECONDS}; SameSite=Strict`;
}

export function getLinkedLearnerInvitationIntentPath(): string | null {
  if (globalThis.document === undefined) return null;
  const item = globalThis.document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith(`${INVITATION_LINK_INTENT_COOKIE}=`));
  if (!item) return null;
  const token = decodeURIComponent(item.slice(INVITATION_LINK_INTENT_COOKIE.length + 1));
  return TOKEN_PATTERN.test(token) ? buildLinkedLearnerInvitationPath(token) : null;
}

export function clearLinkedLearnerInvitationIntentCookie(): void {
  if (globalThis.document === undefined) return;
  globalThis.document.cookie = `${INVITATION_LINK_INTENT_COOKIE}=; path=/; max-age=0; SameSite=Strict`;
}
