import {
  clearAuthUser,
  getAccessToken,
  getRefreshToken,
  handleUnauthorizedSession,
  setAuthUser,
} from "./auth";
import { trackAnalyticsEvent } from "./api";

jest.mock("./auth", () => ({
  beginManualLogoutRedirect: jest.fn(),
  clearAuthUser: jest.fn(),
  getAccessToken: jest.fn(),
  getAuthUser: jest.fn(),
  getRefreshToken: jest.fn(),
  handleUnauthorizedSession: jest.fn(),
  patchAuthUser: jest.fn(),
  setAuthUser: jest.fn(),
}));

const analyticsRequest = {
  eventType: "COURSE_PROGRAM_VALUE_SELECTED" as const,
  metadata: { surface: "profile", matchedCatalog: true },
};

const refreshedAuthResponse = {
  userId: "user-1",
  email: "learner@example.com",
  displayName: "Learner",
  profileType: "STUDENT",
  emailVerifiedAt: "2026-08-15T00:00:00Z",
  onboardingCompletedAt: "2026-08-15T00:00:00Z",
  productOnboardingCompletedAt: null,
  themePreference: "SYSTEM",
  role: "USER",
  planType: "FREE",
  token: "fresh-access-token",
  refreshToken: "fresh-refresh-token",
  accessTokenExpiresAt: "2026-08-15T00:15:00Z",
  refreshTokenExpiresAt: "2026-09-14T00:00:00Z",
};

describe("trackAnalyticsEvent", () => {
  const originalFetch = globalThis.fetch;
  let visibilityState: DocumentVisibilityState = "visible";

  beforeEach(() => {
    jest.clearAllMocks();
    visibilityState = "visible";
    jest.spyOn(globalThis.document, "visibilityState", "get").mockImplementation(() => visibilityState);
    (getAccessToken as jest.Mock).mockReturnValue("expired-access-token");
    (getRefreshToken as jest.Mock).mockReturnValue("refresh-token");
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    jest.restoreAllMocks();
  });

  it("refreshes after a 401 and retries the event once with the new access token", async () => {
    const fetchMock = jest.fn()
      .mockResolvedValueOnce({ status: 401 } as Response)
      .mockResolvedValueOnce({ ok: true, json: async () => refreshedAuthResponse } as Response)
      .mockResolvedValueOnce({ status: 204 } as Response);
    globalThis.fetch = fetchMock;
    (getAccessToken as jest.Mock)
      .mockReturnValueOnce("expired-access-token")
      .mockReturnValue("fresh-access-token");

    await expect(trackAnalyticsEvent(analyticsRequest)).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toContain("/auth/refresh");
    expect(fetchMock.mock.calls[2][0]).toContain("/analytics/events");
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get("Authorization"))
      .toBe("Bearer fresh-access-token");
    expect(setAuthUser).toHaveBeenCalled();
    expect(handleUnauthorizedSession).not.toHaveBeenCalled();
  });

  it("silently gives up when refresh fails without expiring or redirecting the session", async () => {
    const initialLocation = globalThis.location.href;
    globalThis.fetch = jest.fn()
      .mockResolvedValueOnce({ status: 401 } as Response)
      .mockResolvedValueOnce({ ok: false, status: 401 } as Response);

    await expect(trackAnalyticsEvent(analyticsRequest)).resolves.toBeUndefined();

    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    expect(clearAuthUser).not.toHaveBeenCalled();
    expect(handleUnauthorizedSession).not.toHaveBeenCalled();
    expect(globalThis.location.href).toBe(initialLocation);
  });

  it("swallows a rejected analytics fetch", async () => {
    globalThis.fetch = jest.fn().mockRejectedValue(new Error("offline"));

    await expect(trackAnalyticsEvent(analyticsRequest)).resolves.toBeUndefined();

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(handleUnauthorizedSession).not.toHaveBeenCalled();
  });

  it("keeps unload delivery fire-and-forget without attempting refresh", async () => {
    visibilityState = "hidden";
    globalThis.fetch = jest.fn().mockResolvedValue({ status: 401 } as Response);

    await expect(trackAnalyticsEvent(analyticsRequest)).resolves.toBeUndefined();

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/analytics/events"),
      expect.objectContaining({ keepalive: true }),
    );
    expect(getRefreshToken).not.toHaveBeenCalled();
    expect(handleUnauthorizedSession).not.toHaveBeenCalled();
  });
});
