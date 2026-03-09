"use client";

export type AuthUser = {
  id: string;
  email: string;
  displayName: string;
  profileType: "STUDENT" | "PARENT" | "PROFESSIONAL";
  planType: "FREE" | "PREMIUM";
  token: string;
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
