import { listNotes } from "./api";

/**
 * ⚠️ THE ONE TEST THAT EXECUTES THIS CLIENT'S OWN REQUEST SHAPE.
 *
 * The builder's component tests mock `@/lib/api` wholesale, so nothing there ever invokes the real
 * `listNotes` — its URL, its parameter names, its trimming. That is the `v0.119.0` blind spot on the
 * client side: a backend MockMvc test pins `q`, a component test pins that the picker calls
 * `listNotes(50, "thermo")`, and BOTH stay green while the two halves disagree about the parameter
 * name and the search silently matches nothing.
 *
 * ⚠️ So `q` and `limit` are asserted LITERALLY. They must stay byte-identical to
 * `NoteController.listMine`'s `@RequestParam` names.
 */
describe("listNotes request shape", () => {
  const originalFetch = globalThis.fetch;

  function stubOk() {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue([]),
    } as unknown as Response);
  }

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("sends no query string at all when neither bound nor search is given", async () => {
    stubOk();

    await expect(listNotes()).resolves.toEqual([]);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("sends the bound and the search together, as limit and q", async () => {
    stubOk();

    await listNotes(50, "thermo");

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes?limit=50&search=thermo",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("omits a blank search rather than sending an empty pattern", async () => {
    // ⚠️ An empty `q` reaching the server would be a `%%` pattern on the backend's own escaping path
    // — harmless there, but this keeps the unsearched request byte-identical to the legacy one, which
    // is what makes the parameter genuinely additive for the six other callers of this endpoint.
    stubOk();

    await listNotes(50, "   ");

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes?limit=50",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("percent-encodes a search containing characters that would break the query string", async () => {
    stubOk();

    await listNotes(25, "a&b c");

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes?limit=25&search=a%26b+c",
      expect.objectContaining({ method: "GET" }),
    );
  });
});
