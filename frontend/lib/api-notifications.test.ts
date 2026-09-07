import {
  dismissNotification,
  getNotificationUnreadCount,
  listNotifications,
  markNotificationRead,
} from "./api";
import { clearAuthUser, getAccessToken, getRefreshToken, handleUnauthorizedSession } from "./auth";

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

const refreshedAuthResponse = {
  userId: "user-1",
  email: "learner@example.com",
  displayName: "Learner",
  profileType: "STUDENT",
  emailVerifiedAt: "2026-09-07T00:00:00Z",
  onboardingCompletedAt: "2026-09-07T00:00:00Z",
  productOnboardingCompletedAt: null,
  themePreference: "SYSTEM",
  role: "USER",
  planType: "FREE",
  token: "fresh-access-token",
  refreshToken: "fresh-refresh-token",
  accessTokenExpiresAt: "2026-09-07T00:15:00Z",
  refreshTokenExpiresAt: "2026-10-07T00:00:00Z",
};

/**
 * Pins the REQUEST SHAPE for the notification endpoints.
 *
 * <p>⚠️ This file exists because a component test that mocks `lib/api` proves nothing about the
 * request actually sent. `v0.119.0` shipped a feature whose JSON POSTs carried no `Content-Type`;
 * Spring rejected every request before the controller was entered, and the feature could not make one
 * successful call while 2,182 frontend tests passed. The component tests all mocked the API layer.
 */
describe("Notifications API", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    jest.clearAllMocks();
    (getAccessToken as jest.Mock).mockReturnValue("access-token");
    (getRefreshToken as jest.Mock).mockReturnValue("refresh-token");
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("requests the inbox with an explicit limit", async () => {
    await listNotifications(25);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notifications?limit=25",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("reads the unread count from its own endpoint", async () => {
    await getNotificationUnreadCount();

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notifications/unread-count",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("marks a notification read with a POST to its own path", async () => {
    await markNotificationRead("notification-1");

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notifications/notification-1/read",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("dismisses a notification with a POST to its own path", async () => {
    await dismissNotification("notification-1");

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notifications/notification-1/dismiss",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("refreshes and retries when the unread-count poll returns 401", async () => {
    // ⚠️ THE LOAD-BEARING ONE, AND ITS ORIGINAL FORM ASSERTED THE DEFECT. This test used to require that
    // the poll NOT retry, on a "refresh storm" rationale that was never real — tryRefreshAccessToken
    // dedupes concurrent refreshes. Access tokens live 15 minutes and this polls every 60 seconds, so
    // "never refresh" meant the badge froze on its last value after a quarter-hour idle and stayed
    // frozen for the rest of the session. trackAnalyticsEvent is the precedent: refresh, retry, and
    // leave the session alone.
    const fetchMock = jest.fn()
      .mockResolvedValueOnce({ ok: false, status: 401, json: jest.fn().mockResolvedValue({}) } as unknown as Response)
      .mockResolvedValueOnce({ ok: true, json: async () => refreshedAuthResponse } as Response)
      .mockResolvedValueOnce({ ok: true, status: 200, json: jest.fn().mockResolvedValue({ count: 4 }) } as unknown as Response);
    globalThis.fetch = fetchMock;
    (getAccessToken as jest.Mock)
      .mockReturnValueOnce("expired-access-token")
      .mockReturnValue("fresh-access-token");

    await expect(getNotificationUnreadCount()).resolves.toEqual({ count: 4 });

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toContain("/auth/refresh");
    expect(fetchMock.mock.calls[2][0]).toContain("/notifications/unread-count");
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get("Authorization"))
      .toBe("Bearer fresh-access-token");
  });

  it("never signs a learner out when the poll's refresh also fails", async () => {
    // ⚠️ The other half, and it is a SEPARATE decision from retrying. A background poll must not expire
    // a session whose foreground still works, so handleUnauthorized stays false.
    globalThis.fetch = jest.fn()
      .mockResolvedValueOnce({ ok: false, status: 401, json: jest.fn().mockResolvedValue({}) } as unknown as Response)
      .mockResolvedValueOnce({ ok: false, status: 401 } as Response);

    await expect(getNotificationUnreadCount()).rejects.toBeDefined();

    expect(clearAuthUser).not.toHaveBeenCalled();
    expect(handleUnauthorizedSession).not.toHaveBeenCalled();
  });
});
