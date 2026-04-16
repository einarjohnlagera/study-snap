"use client";

import type { MePlanResponse } from "./me-plan";
import type { ThemePreference } from "./theme-preferences";

export type AuthUser = {
  id: string;
  email: string;
  displayName: string | null;
  profileType: "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PARENT" | "PROFESSIONAL" | null;
  emailVerifiedAt: string | null;
  onboardingCompletedAt?: string | null;
  productOnboardingCompletedAt?: string | null;
  role: "USER" | "ADMIN";
  planType: "FREE" | "PREMIUM";
  themePreference?: ThemePreference | null;
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  planSummary?: MePlanResponse | null;
};

const AUTH_USER_KEY = "study_snap_auth_user";
export const LOGIN_REDIRECT_QUERY_KEY = "redirect";
export const LOGIN_REASON_QUERY_KEY = "reason";
export const LOGIN_REASON_SESSION_EXPIRED = "session_expired";
export const LOGIN_REASON_LOGGED_OUT = "logged_out";
export const LOGIN_REASON_AUTH_REQUIRED = "auth_required";

export type LoginReason =
  | typeof LOGIN_REASON_SESSION_EXPIRED
  | typeof LOGIN_REASON_LOGGED_OUT
  | typeof LOGIN_REASON_AUTH_REQUIRED;

const SESSION_EXPIRED_USER_KEY = "notelib-session-expired-user-id";

let hasTriggeredSessionExpiryRedirect = false;
let hasManualLogoutRedirectIntent = false;

function getSessionExpiredUserId(): string | null {
  try {
    return globalThis.sessionStorage?.getItem(SESSION_EXPIRED_USER_KEY) ?? null;
  } catch {
    return null;
  }
}

function clearSessionExpiredUserId(): void {
  try {
    globalThis.sessionStorage?.removeItem(SESSION_EXPIRED_USER_KEY);
  } catch {
    // ignore
  }
}

function emitAuthChangeEvent(): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.dispatchEvent(new Event("studysnap-auth-change"));
}

function getSafeRedirectPath(path: string | null | undefined): string | null {
  if (!path) {
    return null;
  }
  if (!path.startsWith("/") || path.startsWith("//")) {
    return null;
  }
  return path;
}

function isAuthRoutePath(path: string): boolean {
  const pathname = path.split("?")[0]?.split("#")[0] ?? path;
  return pathname === "/auth" || pathname === "/login" || pathname === "/signup";
}

export function getCurrentPathWithQuery(): string {
  if (globalThis.window === undefined) {
    return "/dashboard";
  }
  return `${globalThis.location.pathname}${globalThis.location.search}`;
}

export function buildLoginPath(options?: {
  redirectTo?: string | null;
  reason?: LoginReason | null;
}): string {
  const params = new URLSearchParams();
  const redirectTo = getSafeRedirectPath(options?.redirectTo ?? null);
  if (redirectTo) {
    params.set(LOGIN_REDIRECT_QUERY_KEY, redirectTo);
  }
  if (options?.reason) {
    params.set(LOGIN_REASON_QUERY_KEY, options.reason);
  }
  const queryString = params.toString();
  return queryString.length > 0 ? `/login?${queryString}` : "/login";
}

export function getAuthUser(): AuthUser | null {
  if (globalThis.window === undefined) {
    return null;
  }
  const raw = globalThis.localStorage.getItem(AUTH_USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

export function setAuthUser(user: AuthUser): void {
  if (globalThis.window === undefined) {
    return;
  }
  hasTriggeredSessionExpiryRedirect = false;
  hasManualLogoutRedirectIntent = false;
  clearSessionExpiredUserId();
  globalThis.localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
  emitAuthChangeEvent();
}

export function patchAuthUser(patch: Partial<AuthUser>): void {
  const authUser = getAuthUser();
  if (!authUser) {
    return;
  }
  setAuthUser({
    ...authUser,
    ...patch,
  });
}

export function clearAuthUser(options?: { preserveSessionExpiryGuard?: boolean }): void {
  if (globalThis.window === undefined) {
    return;
  }
  if (!options?.preserveSessionExpiryGuard) {
    hasTriggeredSessionExpiryRedirect = false;
  }
  globalThis.localStorage.removeItem(AUTH_USER_KEY);
  emitAuthChangeEvent();
}

export function beginManualLogoutRedirect(): void {
  if (globalThis.window === undefined) {
    return;
  }
  hasTriggeredSessionExpiryRedirect = false;
  hasManualLogoutRedirectIntent = true;
}

export function isManualLogoutInProgress(): boolean {
  return hasManualLogoutRedirectIntent;
}

export function getCurrentUserId(): string | null {
  return getAuthUser()?.id ?? null;
}
export function needsOnboarding(authUser: AuthUser | null): boolean {
  if (!authUser?.emailVerifiedAt) {
    return false;
  }
  if (authUser.onboardingCompletedAt === undefined) {
    return false;
  }
  return authUser.onboardingCompletedAt === null;
}

export function resolveAuthenticatedHome(authUser: AuthUser | null): string {
  if (!authUser?.emailVerifiedAt) {
    return "/verify-email";
  }
  if (needsOnboarding(authUser)) {
    return "/onboarding";
  }
  return "/dashboard";
}

export function resolvePostLoginDestination(
  authUser: AuthUser | null,
  options?: { search?: string | URLSearchParams | null },
): string {
  const authenticatedHome = resolveAuthenticatedHome(authUser);
  if (authenticatedHome !== "/dashboard") {
    return authenticatedHome;
  }

  const params = options?.search instanceof URLSearchParams
    ? options.search
    : new URLSearchParams(options?.search ?? globalThis.location?.search ?? "");
  const loginReason = params.get(LOGIN_REASON_QUERY_KEY);
  if (loginReason === LOGIN_REASON_LOGGED_OUT) {
    return "/dashboard";
  }

  const redirectTarget = getSafeRedirectPath(params.get(LOGIN_REDIRECT_QUERY_KEY));

  if (loginReason === LOGIN_REASON_SESSION_EXPIRED) {
    // Only restore the session-expiry redirect for the exact user whose session expired.
    // If a different user logs in on the same device they land on the dashboard rather than
    // a resource they may not own.
    const expiredUserId = getSessionExpiredUserId();
    if (expiredUserId && authUser?.id === expiredUserId && redirectTarget && !isAuthRoutePath(redirectTarget)) {
      return redirectTarget;
    }
    return "/dashboard";
  }

  if (redirectTarget && !isAuthRoutePath(redirectTarget)) {
    return redirectTarget;
  }

  return "/dashboard";
}

export function getAccessToken(): string | null {
  return getAuthUser()?.accessToken ?? null;
}

export function getRefreshToken(): string | null {
  return getAuthUser()?.refreshToken ?? null;
}
export function handleUnauthorizedSession(): void {
  if (globalThis.window === undefined) {
    return;
  }
  if (hasManualLogoutRedirectIntent) {
    return;
  }
  if (hasTriggeredSessionExpiryRedirect) {
    return;
  }

  hasTriggeredSessionExpiryRedirect = true;
  const expiredUserId = getCurrentUserId();
  const redirectTo = getCurrentPathWithQuery();
  clearAuthUser({ preserveSessionExpiryGuard: true });
  if (expiredUserId) {
    try {
      globalThis.sessionStorage?.setItem(SESSION_EXPIRED_USER_KEY, expiredUserId);
    } catch {
      // ignore — if sessionStorage is unavailable the redirect will fall back to dashboard
    }
  }
  globalThis.location.replace(
    buildLoginPath({
      redirectTo,
      reason: LOGIN_REASON_SESSION_EXPIRED,
    }),
  );
}
