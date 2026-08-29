const REDEMPTION_COMPLETION_COOKIE = "notelib-connection-redemption";
const REDEMPTION_COMPLETION_MAX_AGE_SECONDS = 1800;
const TOKEN_PATTERN = /^[0-9A-Za-z]{22}$/;
const MAX_USER_ID_LENGTH = 128;

type LinkedLearnerRedemptionCompletion = {
  token: string;
  userId: string;
};

function validUserId(userId: string): boolean {
  return userId.length > 0 && userId.length <= MAX_USER_ID_LENGTH;
}

export function setLinkedLearnerRedemptionCompletion(
  token: string,
  userId: string,
): void {
  if (globalThis.document === undefined || !TOKEN_PATTERN.test(token) || !validUserId(userId)) return;
  const value = encodeURIComponent(JSON.stringify({ token, userId }));
  globalThis.document.cookie = `${REDEMPTION_COMPLETION_COOKIE}=${value}; path=/; max-age=${REDEMPTION_COMPLETION_MAX_AGE_SECONDS}; SameSite=Strict`;
}

export function getLinkedLearnerRedemptionCompletion(): LinkedLearnerRedemptionCompletion | null {
  if (globalThis.document === undefined) return null;
  const item = globalThis.document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith(`${REDEMPTION_COMPLETION_COOKIE}=`));
  if (!item) return null;
  try {
    const parsed = JSON.parse(decodeURIComponent(
      item.slice(REDEMPTION_COMPLETION_COOKIE.length + 1),
    )) as Partial<LinkedLearnerRedemptionCompletion>;
    if (typeof parsed.token !== "string" || !TOKEN_PATTERN.test(parsed.token)
        || typeof parsed.userId !== "string" || !validUserId(parsed.userId)) {
      return null;
    }
    return { token: parsed.token, userId: parsed.userId };
  } catch {
    return null;
  }
}

export function clearLinkedLearnerRedemptionCompletion(): void {
  if (globalThis.document === undefined) return;
  globalThis.document.cookie = `${REDEMPTION_COMPLETION_COOKIE}=; path=/; max-age=0; SameSite=Strict`;
}
