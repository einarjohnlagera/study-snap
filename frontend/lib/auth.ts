"use client";

export type AuthUser = {
  id: string;
  email: string;
  displayName: string;
  profileType: "STUDENT" | "PARENT" | "PROFESSIONAL" | null;
  emailVerifiedAt: string | null;
  role: "USER" | "ADMIN";
  planType: "FREE" | "PREMIUM";
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
};

const AUTH_USER_KEY = "study_snap_auth_user";

export function getAuthUser(): AuthUser | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(AUTH_USER_KEY);
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
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
}

export function clearAuthUser(): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem(AUTH_USER_KEY);
}

export function getCurrentUserId(): string | null {
  return getAuthUser()?.id ?? null;
}

export function isEmailVerified(): boolean {
  return Boolean(getAuthUser()?.emailVerifiedAt);
}

export function getAccessToken(): string | null {
  return getAuthUser()?.accessToken ?? null;
}

export function getRefreshToken(): string | null {
  return getAuthUser()?.refreshToken ?? null;
}

export function isAccessTokenExpired(bufferSeconds = 30): boolean {
  const authUser = getAuthUser();
  if (!authUser) {
    return true;
  }
  const expiresAt = new Date(authUser.accessTokenExpiresAt).getTime();
  return Date.now() + bufferSeconds * 1000 >= expiresAt;
}
