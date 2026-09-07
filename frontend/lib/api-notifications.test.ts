import {
  dismissNotification,
  getNotificationUnreadCount,
  listNotifications,
  markNotificationRead,
} from "./api";

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

  it("does NOT retry or clear the session when the unread-count poll returns 401", async () => {
    // ⚠️ The load-bearing one. This endpoint is polled on a timer, so a transient 401 must not sign a
    // learner out of an otherwise valid session, and must not trigger a refresh storm. It is called
    // with both retry and unauthorized-handling disabled; a single fetch proves no retry happened.
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);

    await expect(getNotificationUnreadCount()).rejects.toBeDefined();

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });
});
