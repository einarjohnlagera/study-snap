import { getReviewSetPublicationStatus, publishReviewSetUpdate } from "./api";

/**
 * ⚠️ THE ONE TEST THAT EXECUTES THE PUBLICATION ENDPOINTS' OWN REQUEST SHAPE.
 *
 * `app/collections/[id]/page.test.tsx` mocks `@/lib/api` wholesale with no `requireActual` spread, so
 * nothing in the component suite ever runs `buildAuthHeaders`, `fetchWithAuth` or these URL strings.
 * That is exactly the `v0.119.0` blind spot: that release shipped a feature whose JSON POSTs carried no
 * `Content-Type`, Spring rejected every request with `HttpMediaTypeNotSupportedException` BEFORE the
 * controller was entered, and 2,182 frontend tests passed anyway.
 *
 * ⚠️ So both halves are asserted LITERALLY here. The paths must stay byte-identical to the backend's
 * `@GetMapping("/{id}/publication-status")` and `@PostMapping("/{id}/publish-update")`, and the POST
 * must keep sending `Content-Type: application/json` with a body — the controller binds a
 * `@RequestBody`, so a bodiless or header-less POST is a 415 the component suite cannot see.
 */
describe("Review Set publication API", () => {
  const originalFetch = globalThis.fetch;
  const status = {
    collectionId: "set-1",
    unpublishedChanges: false,
    topicsAdded: 0,
    subjectPlansAdded: 0,
    lastUpdatePublishedAt: null,
  };

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const stubOk = (payload: unknown) => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);
  };

  it("reads publication status with one GET at the path the backend maps", async () => {
    stubOk(status);

    await expect(getReviewSetPublicationStatus("set-1")).resolves.toEqual(status);

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/collections/set-1/publication-status",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("publishes with a POST that actually carries Content-Type and a body", async () => {
    stubOk(status);

    await expect(publishReviewSetUpdate("set-1")).resolves.toEqual(status);

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    const [url, init] = (globalThis.fetch as jest.Mock).mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/collections/set-1/publish-update");
    expect(init.method).toBe("POST");
    // ⚠️ This is the v0.119.0 assertion. Without the header Spring answers 415 before the controller runs.
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
    expect(init.body).toBe("{}");
  });

  it("surfaces a readable publish failure instead of resolving silently", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);

    await expect(publishReviewSetUpdate("set-1")).rejects.toThrow("Could not publish this Review Set update.");
  });
});
