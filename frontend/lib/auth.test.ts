import {
  clearLastVisitedPath,
  rememberLastVisitedPath,
  resolvePostLoginDestination,
  type AuthUser,
} from "./auth";

const verifiedUser: AuthUser = {
  id: "user-1",
  email: "note@example.com",
  displayName: "Note",
  profileType: "STUDENT",
  emailVerifiedAt: "2026-03-31T00:00:00Z",
  onboardingCompletedAt: "2026-03-31T00:05:00Z",
  productOnboardingCompletedAt: null,
  role: "USER",
  planType: "FREE",
  accessToken: "access-token",
  refreshToken: "refresh-token",
  accessTokenExpiresAt: "2026-03-31T01:00:00Z",
  refreshTokenExpiresAt: "2026-04-30T01:00:00Z",
};

describe("auth redirect helpers", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.history.replaceState({}, "", "/login");
  });

  it("prefers an explicit redirect query after login", () => {
    const destination = resolvePostLoginDestination(
      verifiedUser,
      { search: "?redirect=%2Fnotes%2F123%3Ftab%3Dquiz" },
    );

    expect(destination).toBe("/notes/123?tab=quiz");
  });

  it("falls back to the last visited page when no redirect query exists", () => {
    rememberLastVisitedPath("/public/library");

    expect(resolvePostLoginDestination(verifiedUser)).toBe("/public/library");
  });

  it("ignores auth pages as last visited destinations", () => {
    rememberLastVisitedPath("/login?redirect=%2Fprofile");

    expect(resolvePostLoginDestination(verifiedUser)).toBe("/dashboard");
  });

  it("keeps verification and onboarding gating ahead of redirect restoration", () => {
    const unverifiedUser: AuthUser = {
      ...verifiedUser,
      emailVerifiedAt: null,
    };

    rememberLastVisitedPath("/notes/123");

    expect(resolvePostLoginDestination(unverifiedUser)).toBe("/verify-email");
  });

  it("can clear the remembered last visited path", () => {
    rememberLastVisitedPath("/profile");
    clearLastVisitedPath();

    expect(resolvePostLoginDestination(verifiedUser)).toBe("/dashboard");
  });
});
