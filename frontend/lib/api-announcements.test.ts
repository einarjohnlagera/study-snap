import {
  createAnnouncement,
  endAnnouncement,
  listAnnouncements,
  publishAnnouncement,
  updateAnnouncement,
  type UpsertAnnouncementRequest,
} from "./api";

/**
 * Pins the REQUEST SHAPE for the admin announcement endpoints.
 *
 * ⚠️ THESE ARE THE HIGHEST-RISK CALLS IN THE RELEASE. `v0.119.0` shipped a feature whose two JSON
 * POSTs carried no `Content-Type`, so Spring rejected every request with
 * `HttpMediaTypeNotSupportedException` BEFORE the controller was entered — and the feature could not
 * make one successful call while 2,182 frontend tests passed, because every component test mocked
 * `lib/api` wholesale and nothing executed header construction. Three POSTs and a PUT here are exactly
 * that shape, so the header is asserted directly rather than inferred.
 */
describe("Admin announcements API", () => {
  const originalFetch = globalThis.fetch;

  const request: UpsertAnnouncementRequest = {
    title: "Board Exam Mode is here",
    body: "Practice with a full timed board exam.",
    ctaLabel: "Try it",
    ctaPath: "/dashboard?tab=exams",
    audience: "EVERYONE",
    audienceValue: null,
    expiresAt: null,
  };

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

  function lastRequestInit(): RequestInit {
    const mock = globalThis.fetch as unknown as jest.Mock;
    return mock.mock.calls[mock.mock.calls.length - 1][1] as RequestInit;
  }

  function lastContentType(): string | undefined {
    return (lastRequestInit().headers as Record<string, string> | undefined)?.["Content-Type"];
  }

  it("lists announcements with a GET", async () => {
    await listAnnouncements();

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/admin/announcements",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("creates a draft with a JSON Content-Type and the full body", async () => {
    await createAnnouncement(request);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/admin/announcements",
      expect.objectContaining({ method: "POST" }),
    );
    expect(lastContentType()).toBe("application/json");
    expect(JSON.parse(lastRequestInit().body as string)).toEqual(request);
  });

  it("updates a draft with a PUT carrying a JSON Content-Type", async () => {
    await updateAnnouncement("announcement-1", request);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/admin/announcements/announcement-1",
      expect.objectContaining({ method: "PUT" }),
    );
    expect(lastContentType()).toBe("application/json");
    expect(JSON.parse(lastRequestInit().body as string)).toEqual(request);
  });

  it("publishes with a POST that still declares a JSON Content-Type and a body", async () => {
    await publishAnnouncement("announcement-1");

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/admin/announcements/announcement-1/publish",
      expect.objectContaining({ method: "POST" }),
    );
    expect(lastContentType()).toBe("application/json");
    expect(lastRequestInit().body).toBe("{}");
  });

  it("ends with a POST that still declares a JSON Content-Type and a body", async () => {
    await endAnnouncement("announcement-1");

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/admin/announcements/announcement-1/end",
      expect.objectContaining({ method: "POST" }),
    );
    expect(lastContentType()).toBe("application/json");
    expect(lastRequestInit().body).toBe("{}");
  });
});
