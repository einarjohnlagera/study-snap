"use client";

import type { MePlanResponse } from "./me-plan";

export type AuthUser = {
  id: string;
  email: string;
  displayName: string;
  profileType: "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PARENT" | "PROFESSIONAL" | null;
  emailVerifiedAt: string | null;
  onboardingCompletedAt?: string | null;
  productOnboardingCompletedAt?: string | null;
  role: "USER" | "ADMIN";
  planType: "FREE" | "PREMIUM";
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

let hasTriggeredSessionExpiryRedirect = false;

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

export function getCurrentPathWithQuery(): string {
  if (globalThis.window === undefined) {
    return "/dashboard";
  }
  return `${globalThis.location.pathname}${globalThis.location.search}`;
}

export function buildLoginPath(options?: {
  redirectTo?: string | null;
  sessionExpired?: boolean;
}): string {
  const params = new URLSearchParams();
  const redirectTo = getSafeRedirectPath(options?.redirectTo ?? null);
  if (redirectTo) {
    params.set(LOGIN_REDIRECT_QUERY_KEY, redirectTo);
  }
  if (options?.sessionExpired) {
    params.set(LOGIN_REASON_QUERY_KEY, LOGIN_REASON_SESSION_EXPIRED);
  }
  return params.size > 0 ? `/login?${params.toString()}` : "/login";
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
  globalThis.localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
  emitAuthChangeEvent();
}

export function clearAuthUser(): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.removeItem(AUTH_USER_KEY);
  emitAuthChangeEvent();
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
  if (hasTriggeredSessionExpiryRedirect) {
    return;
  }

  hasTriggeredSessionExpiryRedirect = true;
  const redirectTo = getCurrentPathWithQuery();
  clearAuthUser();
  globalThis.location.replace(
    buildLoginPath({
      redirectTo,
      sessionExpired: true,
    }),
  );
}
