import {
  bulkRegenerateNotes,
  preflightNoteRegeneration,
  retryBulkRegeneration,
} from "./api";

/**
 * ⚠️ THIS FILE EXISTS BECAUSE THE MODAL TESTS MOCK `lib/api` WHOLESALE.
 *
 * Bulk regeneration shipped with both of its JSON POSTs sending no `Content-Type`, so the browser
 * defaulted to `text/plain;charset=UTF-8` and Spring rejected every request with
 * `HttpMediaTypeNotSupportedException` before the controller was entered — the feature was broken end
 * to end while 2,182 frontend tests passed, because every one of them mocks this layer away.
 *
 * The request SHAPE is what needs pinning here: the component tests cover behaviour, and nothing else
 * executes header construction.
 */
describe("bulk regeneration API request shape", () => {
  const originalFetch = globalThis.fetch;

  function mockFetch(payload: unknown) {
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);
    globalThis.fetch = fetchMock;
    return fetchMock;
  }

  function headerOf(fetchMock: jest.Mock, name: string): string | undefined {
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    return (init.headers as Record<string, string>)[name];
  }

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("sends the preflight body as JSON", async () => {
    const fetchMock = mockFetch({ scope: "STUDY_PACK", items: [] });

    await preflightNoteRegeneration(["note-1", "note-2"], "STUDY_PACK");

    expect(headerOf(fetchMock, "Content-Type")).toBe("application/json");
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({
      noteIds: ["note-1", "note-2"],
      scope: "STUDY_PACK",
    });
  });

  it("sends the batch body as JSON", async () => {
    const fetchMock = mockFetch({ batchId: "batch-1", scope: "NOTE_AND_STUDY_PACK", acceptedCount: 2 });

    await bulkRegenerateNotes(["note-1", "note-2"], "NOTE_AND_STUDY_PACK");

    expect(headerOf(fetchMock, "Content-Type")).toBe("application/json");
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({
      noteIds: ["note-1", "note-2"],
      scope: "NOTE_AND_STUDY_PACK",
    });
  });

  it("addresses retry by batch id and sends no note list", async () => {
    const fetchMock = mockFetch({ batchId: "batch-2", scope: "STUDY_PACK", acceptedCount: 1 });

    await retryBulkRegeneration("batch-1");

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/notes/bulk-regenerate/batch-1/retry");
    // The server derives which items failed. A body here would mean the client was choosing, which is
    // the thing this endpoint's shape exists to prevent on a metered path.
    expect(init.body).toBeUndefined();
  });
});
