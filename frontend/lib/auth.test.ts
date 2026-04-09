import {
  beginManualLogoutRedirect,
  buildLoginPath,
  getAuthUser,
  handleUnauthorizedSession,
  LOGIN_REASON_AUTH_REQUIRED,
  LOGIN_REASON_LOGGED_OUT,
  LOGIN_REASON_SESSION_EXPIRED,
  resolvePostLoginDestination,
  setAuthUser,
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

const secondVerifiedUser: AuthUser = {
  ...verifiedUser,
  id: "user-2",
  email: "second@example.com",
  displayName: "Second",
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

  it("falls back to the dashboard when no redirect query exists", () => {
    expect(resolvePostLoginDestination(verifiedUser)).toBe("/dashboard");
  });

  it("ignores stale redirect queries after manual logout for the same user", () => {
    const destination = resolvePostLoginDestination(
      verifiedUser,
      { search: "?reason=logged_out&redirect=%2Fstudy-packs%2Fnote-1%2Fchallenge-quiz" },
    );

    expect(destination).toBe("/dashboard");
  });

  it("ignores stale redirect queries after manual logout for a different user", () => {
    const destination = resolvePostLoginDestination(
      secondVerifiedUser,
      { search: "?reason=logged_out&redirect=%2Fnotes%2Fnote-1%3Ftab%3Dquiz" },
    );

    expect(destination).toBe("/dashboard");
  });

  it("ignores auth-page redirect targets", () => {
    const destination = resolvePostLoginDestination(
      verifiedUser,
      { search: "?redirect=%2Flogin%3Fredirect%3D%252Fprofile" },
    );

    expect(destination).toBe("/dashboard");
  });

  it("builds a login path for session expiry recovery", () => {
    expect(buildLoginPath({
      redirectTo: "/notes/123?tab=quiz",
      reason: LOGIN_REASON_SESSION_EXPIRED,
    })).toBe("/login?redirect=%2Fnotes%2F123%3Ftab%3Dquiz&reason=session_expired");
  });

  it("builds a neutral login path for protected-route access", () => {
    expect(buildLoginPath({
      redirectTo: "/library",
      reason: LOGIN_REASON_AUTH_REQUIRED,
    })).toBe("/login?redirect=%2Flibrary&reason=auth_required");
  });

  it("builds a logged-out login path without a redirect target", () => {
    expect(buildLoginPath({
      reason: LOGIN_REASON_LOGGED_OUT,
    })).toBe("/login?reason=logged_out");
  });

  it("does not let manual logout intent be overwritten by session-expired handling", () => {
    setAuthUser(verifiedUser);

    beginManualLogoutRedirect();
    handleUnauthorizedSession();

    expect(getAuthUser()).toEqual(verifiedUser);
  });

  it("keeps verification and onboarding gating ahead of redirect restoration", () => {
    const unverifiedUser: AuthUser = {
      ...verifiedUser,
      emailVerifiedAt: null,
    };

    expect(resolvePostLoginDestination(
      unverifiedUser,
      { search: "?redirect=%2Fnotes%2F123" },
    )).toBe("/verify-email");
  });
});
