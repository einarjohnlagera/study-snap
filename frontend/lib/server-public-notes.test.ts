import {
  getServerPublicNoteCount,
  getServerPublicNoteById,
  getServerPublicNotes,
  getServerPublicNotesByCourseProgramRecent,
  getServerPublicNotesByCoursePrograms,
  getServerPublicNotesBySubject,
  getServerPublicNotesBySubjectSlug,
  getServerPublicNotesBySubjectSlugRecent,
} from "./server-public-notes";
import { CATALOG_FAILURE_BACKOFF_MS, CatalogBackoffError, CatalogDeadlineError, resetCatalogCacheForTests } from "./catalog-cache";

const originalFetch = global.fetch;
beforeEach(resetCatalogCacheForTests);
afterEach(() => jest.restoreAllMocks());
// Moves the wall clock past a failed key's backoff (cumulative: each call adds one more backoff).
const advancePastBackoff = () => {
  jest.spyOn(Date, "now").mockReturnValue(Date.now() + CATALOG_FAILURE_BACKOFF_MS);
};

describe("getServerPublicNoteCount", () => {
  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("uses the existing unfiltered public-notes endpoint total with a one-item response", async () => {
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ items: [], total: 128 }),
    });
    global.fetch = fetchMock;

    await expect(getServerPublicNoteCount()).resolves.toBe(128);
    // ⚠️ CORRECTED, NOT EXTENDED. This previously pinned `?size=1` — the exact shape that caused the
    // 2026-09-05 outage — so the suite was actively protecting the defect. `size` is not a pagination
    // parameter, and without `sort` the paginated branch still ranks in Java over every candidate.
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes/public?page=0&pageSize=1&sort=recent",
      expect.objectContaining({
        method: "GET",
        next: { revalidate: 300 },
      }),
    );
  });

  it("fails closed when the public endpoint does not return a valid total", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ items: [], total: null }),
    });

    await expect(getServerPublicNoteCount()).resolves.toBeNull();
  });

  it("returns the same PNLE notes after removing the zero-match subject-area alias", async () => {
    const nursingNote = { id: "nursing-note", courseProgram: "Nursing" };
    const response = {
      ok: true,
      json: async () => ({ items: [nursingNote], total: 1, hasMore: false }),
    };
    global.fetch = jest.fn().mockResolvedValue(response);

    const before = await getServerPublicNotesByCoursePrograms([
      "Nursing",
      "Medical – Surgical Nursing",
    ]);
    const after = await getServerPublicNotesByCoursePrograms(["Nursing"]);

    expect(after).toEqual(before);
    expect(after).toEqual([nursingNote]);
  });

  // ⚠️ This test used to pin the JS matching rules -- joined programs win, the personal scalar is used only
  // when there are no joined rows. Those rules did not disappear; they moved to the server, where
  // PublicLibraryRepositoryImpl expresses the identical predicate (an exists() on note_course_program, OR a
  // not-exists plus a match on n.course_program) with both sides slug-normalized. What is testable HERE is
  // now the request shape and the merge, so that is what this asserts.
  it("asks the server for each program instead of filtering the whole catalog in JavaScript", async () => {
    const nursingNote = { id: "nursing-note", courseProgram: null, applicablePrograms: ["Nursing"] };
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ items: [nursingNote], total: 1, hasMore: false }),
    });

    await expect(getServerPublicNotesByCoursePrograms(["NURSING"])).resolves.toEqual([nursingNote]);

    const requestedUrls = (global.fetch as jest.Mock).mock.calls.map(([url]) => String(url));
    expect(requestedUrls).toHaveLength(1);
    // Lower-cased by the existing de-duplication Set, which is why ["Nursing", "NURSING"] costs one
    // request rather than two. Case is irrelevant to the server, which slug-normalizes both sides.
    expect(requestedUrls[0]).toContain("courseProgram=nursing");
    // The defect this whole change fixes: no request may ask for the catalog unfiltered.
    expect(requestedUrls[0]).toMatch(/[?&](courseProgram|subject)=/);
  });

  it("issues one request per program and de-duplicates a note joined to several", async () => {
    const sharedNote = { id: "shared", courseProgram: null, applicablePrograms: ["Nursing", "Accountancy"] };
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ items: [sharedNote], total: 1, hasMore: false }),
    });

    const notes = await getServerPublicNotesByCoursePrograms(["Nursing", "Accountancy"]);

    expect((global.fetch as jest.Mock).mock.calls).toHaveLength(2);
    // Returned once, not once per matching program.
    expect(notes).toEqual([sharedNote]);
  });
});

describe("public note fetches stay inside the 2MB data-cache limit", () => {
  afterEach(() => {
    global.fetch = originalFetch;
  });

  const page = (items: unknown[], hasMore: boolean) => ({
    ok: true,
    json: async () => ({ items, total: items.length, hasMore }),
  });

  it("filters a subject on the server and never asks for the unfiltered catalog", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([{ id: "note-1" }], false));

    await getServerPublicNotesBySubjectSlug("foundation-engineering");

    const requestedUrls = (global.fetch as jest.Mock).mock.calls.map(([url]) => String(url));
    expect(requestedUrls).toHaveLength(1);
    expect(requestedUrls[0]).toContain("subject=foundation-engineering");
  });

  // ⚠️ The slug is passed through UNCHANGED. The backend matches normalizedSlugSql(n.subject) against a
  // param put through the identical normalization, so every label variant sharing this slug still matches
  // -- which is what the old in-JS filter did. Resolving the slug back to a label here would silently drop
  // those variants from an SEO-indexed page.
  it("passes the slug straight through rather than resolving it to a subject label", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([], false));

    await getServerPublicNotesBySubjectSlug("engineering-mathematics");

    const requestedUrls = (global.fetch as jest.Mock).mock.calls.map(([url]) => String(url));
    expect(requestedUrls).toHaveLength(1);
    expect(requestedUrls[0]).toContain("subject=engineering-mathematics");
    // No lookup against a subjects/labels endpoint.
    expect(requestedUrls.some((url) => url.includes("/subjects"))).toBe(false);
  });

  // ⚠️ "general" is the slug getPublicSubjectSlug invents for a blank subject, and the server filter
  // cannot express it: SQL coalesces a null subject to '', the request param normalizes with no fallback,
  // and '' never equals 'general'. Server-filtering this slug drops those notes from a route the app
  // manufactures for them. Found by the signoff cold agent.
  it("filters the blank-subject general slug in JS because the server filter cannot express it", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        items: [{ id: "blank", subject: null }, { id: "biology", subject: "Biology" }],
        total: 2,
        hasMore: false,
      }),
    });

    const notes = await getServerPublicNotesBySubjectSlug("general");

    expect(notes.map((note) => note.id)).toEqual(["blank"]);
    const requestedUrls = (global.fetch as jest.Mock).mock.calls.map(([url]) => String(url));
    expect(requestedUrls.every((url) => !url.includes("subject="))).toBe(true);
  });

  it("returns an empty list for a blank slug without calling the backend", async () => {
    global.fetch = jest.fn();

    await expect(getServerPublicNotesBySubjectSlug("   ")).resolves.toEqual([]);

    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("pages the full catalog so no single response can cross the limit", async () => {
    const first = Array.from({ length: 50 }, (_, index) => ({ id: `note-${index}` }));
    global.fetch = jest.fn()
      .mockResolvedValueOnce(page(first, true))
      .mockResolvedValueOnce(page([{ id: "note-50" }], false));

    const notes = await getServerPublicNotes();

    expect(notes).toHaveLength(51);
    const requestedUrls = (global.fetch as jest.Mock).mock.calls.map(([url]) => String(url));
    expect(requestedUrls).toHaveLength(2);
    expect(requestedUrls[0]).toContain("page=0&pageSize=50");
    expect(requestedUrls[1]).toContain("page=1&pageSize=50");
  });

  // ⚠️ THE TEST THAT WOULD HAVE CAUGHT THE TRUNCATION, and the reason the first version did not:
  // it mocked a 250-item page, which NoteController can never return -- PUBLIC_NOTES_MAX_SIZE clamps
  // pageSize to 50. A response the backend cannot produce proves nothing about the loop.
  // This mock behaves like the real server: it CLAMPS whatever pageSize is asked for, so a short page
  // arrives with hasMore=true. A length-based stop condition truncates the catalog here and the
  // assertion below fails.
  // ⚠️ SERVER_MAX is deliberately BELOW the page size we request. That is the whole point: the defect
  // only appears when the server hands back fewer items than were asked for, which is what its clamp
  // does. A mock that returns exactly PUBLIC_NOTES_PAGE_SIZE items cannot reproduce it -- the first
  // version of this test did that and a reintroduced length-based stop survived the mutation.
  it("keeps paging when the server clamps to a smaller page than requested", async () => {
    const SERVER_MAX = 20;
    const catalog = Array.from({ length: 120 }, (_, index) => ({ id: `note-${index}` }));
    global.fetch = jest.fn().mockImplementation((url: string) => {
      const pageIndex = Number(new URL(String(url)).searchParams.get("page"));
      const start = pageIndex * SERVER_MAX;
      const items = catalog.slice(start, start + SERVER_MAX);
      return Promise.resolve({
        ok: true,
        json: async () => ({ items, total: items.length, hasMore: start + items.length < catalog.length }),
      });
    });

    const notes = await getServerPublicNotes();

    // 120, not 20. A length-based stop returns the first clamped page and stops.
    expect(notes).toHaveLength(120);
    expect((global.fetch as jest.Mock).mock.calls).toHaveLength(6);
  });

  it("keeps paging a server-clamped SUBJECT query too, not just the full catalog", async () => {
    const SERVER_MAX = 20;
    const subjectNotes = Array.from({ length: 60 }, (_, index) => ({ id: `subject-note-${index}` }));
    global.fetch = jest.fn().mockImplementation((url: string) => {
      const parsed = new URL(String(url));
      expect(parsed.searchParams.get("subject")).toBe("foundation-engineering");
      const start = Number(parsed.searchParams.get("page")) * SERVER_MAX;
      const items = subjectNotes.slice(start, start + SERVER_MAX);
      return Promise.resolve({
        ok: true,
        json: async () => ({ items, total: items.length, hasMore: start + items.length < subjectNotes.length }),
      });
    });

    await expect(getServerPublicNotesBySubjectSlug("foundation-engineering")).resolves.toHaveLength(60);
  });

  it("issues exactly one request when the catalog fits in a page", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([{ id: "note-1" }], false));

    await getServerPublicNotes();

    expect((global.fetch as jest.Mock).mock.calls).toHaveLength(1);
  });

  // Without this the loop would spin forever during a build against a backend that keeps claiming hasMore.
  it("rejects an empty page that claims there is more", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([], true));

    await expect(getServerPublicNotes()).rejects.toThrow("Invalid public catalog response");

    expect((global.fetch as jest.Mock).mock.calls).toHaveLength(1);
  });

  it("requires a boolean hasMore, but accepts an explicitly complete empty catalog", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [], hasMore: "false" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [], hasMore: null }) })
      .mockResolvedValueOnce(page([], false));
    for (let index = 0; index < 3; index++) {
      advancePastBackoff();
      await expect(getServerPublicNotes()).rejects.toThrow("Invalid public catalog response");
    }
    advancePastBackoff();
    await expect(getServerPublicNotes()).resolves.toEqual([]);
    expect(global.fetch).toHaveBeenCalledTimes(4);
  });

  it("completes on page 400 only with hasMore false and logs a guard breach otherwise", async () => {
    const error = jest.spyOn(console, "error").mockImplementation();
    global.fetch = jest.fn().mockImplementation((url: string) => Promise.resolve(page([{ id: new URL(url).searchParams.get("page") }], new URL(url).searchParams.get("page") !== "399")));
    await expect(getServerPublicNotes()).resolves.toHaveLength(400);
    expect(global.fetch).toHaveBeenCalledTimes(400);
    resetCatalogCacheForTests();
    global.fetch = jest.fn().mockResolvedValue(page([{ id: "note" }], true));
    await expect(getServerPublicNotes()).rejects.toThrow("page guard");
    expect(global.fetch).toHaveBeenCalledTimes(400);
    expect(error).toHaveBeenCalledWith("CATALOG_WALK_GUARD_BREACH class=page entry=subject pages=400");
    error.mockRestore();
  });

  it("does not cache empty subject walks or let 1,100 junk slugs evict a real subject", async () => {
    const info = jest.spyOn(console, "info").mockImplementation();
    const fetchMock = jest.fn().mockImplementation((url: string) => Promise.resolve(page(String(url).includes("subject=biology") ? [{ id: "real" }] : [], false)));
    global.fetch = fetchMock;
    await getServerPublicNotesBySubjectSlug("biology");
    await getServerPublicNotesBySubjectSlug("junk-once");
    await getServerPublicNotesBySubjectSlug("junk-once");
    for (let index = 0; index < 1100; index++) await getServerPublicNotesBySubjectSlug(`junk-${index}`);
    const calls = fetchMock.mock.calls.length;
    await expect(getServerPublicNotesBySubjectSlug("biology")).resolves.toEqual([{ id: "real" }]);
    expect(fetchMock).toHaveBeenCalledTimes(calls);
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes("subject=junk-once"))).toHaveLength(2);
    info.mockRestore();
  });

  it("continues caching empty general, exam-hub and sitemap walks", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([], false));
    await getServerPublicNotesBySubjectSlug("general");
    await getServerPublicNotesBySubjectSlug("general");
    await getServerPublicNotesByCoursePrograms(["Nursing"]);
    await getServerPublicNotesByCoursePrograms(["Nursing"]);
    await getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" });
    await getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" });
    expect(global.fetch).toHaveBeenCalledTimes(3);
  });

  it("keeps the first occurrence when a note repeats across a page boundary (a note published mid-walk shifts the offset)", async () => {
    const info = jest.spyOn(console, "info").mockImplementation();
    global.fetch = jest.fn()
      .mockResolvedValueOnce(page([{ id: "a", title: "A first" }, { id: "b", title: "B first" }], true))
      .mockResolvedValueOnce(page([{ id: "b", title: "B repeated" }, { id: "c", title: "C" }], false));
    await expect(getServerPublicNotes()).resolves.toEqual([
      { id: "a", title: "A first" },
      { id: "b", title: "B first" },
      { id: "c", title: "C" },
    ]);
    expect(global.fetch).toHaveBeenCalledTimes(2);
    expect(info).toHaveBeenCalledWith(expect.stringMatching(/CATALOG_WALK outcome=ok class=page entry=subject pages=2 notes=3 /));
  });

  it("de-duplicates the sitemap walk too, and keeps items that have no usable id", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce(page([{ id: "a" }, { title: "no id one" }], true))
      .mockResolvedValueOnce(page([{ id: "a" }, { title: "no id two" }, { id: "z" }], false));
    await expect(getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" })).resolves.toEqual([
      { id: "a" },
      { title: "no id one" },
      { title: "no id two" },
      { id: "z" },
    ]);
  });

  it("aborts a stalled walk page at 15 seconds and logs only bounded fields", async () => {
    jest.useFakeTimers();
    const info = jest.spyOn(console, "info").mockImplementation();
    global.fetch = jest.fn().mockImplementation((_url: string, options: { signal: AbortSignal }) => new Promise((_resolve, reject) => {
      options.signal.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
    }));
    try {
      const pending = getServerPublicNotesBySubjectSlug("private-junk-slug");
      const rejected = expect(pending).rejects.toThrow("aborted");
      await jest.advanceTimersByTimeAsync(15_000);
      await rejected;
      expect(info).toHaveBeenCalledWith(expect.stringMatching(/^CATALOG_WALK outcome=error class=page entry=subject pages=0 notes=0 durationMs=\d+$/));
      expect(info.mock.calls.flat().join(" ")).not.toContain("private-junk-slug");
      expect(jest.getTimerCount()).toBe(0);
    } finally {
      info.mockRestore();
      jest.useRealTimers();
    }
  });

  it("ends a slow multi-page walk at 90 seconds even though each page takes less than 15", async () => {
    jest.useFakeTimers();
    const warn = jest.spyOn(console, "warn").mockImplementation();
    const info = jest.spyOn(console, "info").mockImplementation();
    global.fetch = jest.fn().mockImplementation(() => new Promise((resolve) => {
      globalThis.setTimeout(() => resolve(page([{ id: "note" }], true)), 12_000);
    }));
    try {
      const pending = getServerPublicNotes();
      const rejected = expect(pending).rejects.toBeInstanceOf(CatalogDeadlineError);
      await jest.advanceTimersByTimeAsync(90_000);
      await rejected;
      expect(global.fetch).toHaveBeenCalledTimes(8);
      expect(warn).toHaveBeenCalledWith("CATALOG_CACHE_DEADLINE class=walk:page deadlineMs=90000");
    } finally {
      warn.mockRestore();
      info.mockRestore();
      jest.useRealTimers();
    }
  });

  it("cuts a hung sitemap walk at its provisional 240-second deadline", async () => {
    jest.useFakeTimers();
    const warn = jest.spyOn(console, "warn").mockImplementation();
    const info = jest.spyOn(console, "info").mockImplementation();
    global.fetch = jest.fn().mockImplementation(() => new Promise(() => undefined));
    try {
      const pending = getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" });
      const rejected = expect(pending).rejects.toBeInstanceOf(CatalogDeadlineError);
      await jest.advanceTimersByTimeAsync(240_000);
      await rejected;
      expect(global.fetch).toHaveBeenCalledTimes(1);
      expect(warn).toHaveBeenCalledWith("CATALOG_CACHE_DEADLINE class=walk:sitemap deadlineMs=240000");
      expect(jest.getTimerCount()).toBe(0);
    } finally {
      warn.mockRestore();
      info.mockRestore();
      jest.useRealTimers();
    }
  });

  it("rejects a later failed page and retries the whole walk instead of caching a partial catalog (e, h)", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce(page([{ id: "first" }], true))
      .mockResolvedValueOnce({ ok: false })
      .mockResolvedValueOnce(page([{ id: "new" }], false));
    await expect(getServerPublicNotes()).rejects.toThrow("Could not load public catalog");
    advancePastBackoff();
    await expect(getServerPublicNotes()).resolves.toEqual([{ id: "new" }]);
    expect(global.fetch).toHaveBeenCalledTimes(3);
  });

  it("rejects malformed JSON and network failures without caching them", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => { throw new SyntaxError("bad JSON"); } })
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(page([{ id: "healthy" }], false));
    await expect(getServerPublicNotes()).rejects.toThrow("bad JSON");
    advancePastBackoff();
    await expect(getServerPublicNotes()).rejects.toThrow("network");
    advancePastBackoff();
    await expect(getServerPublicNotes()).resolves.toEqual([{ id: "healthy" }]);
    expect(global.fetch).toHaveBeenCalledTimes(3);
  });

  it("rejects a 200 response with no items array instead of ending the walk with a partial list (n)", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce(page([{ id: "first" }], true))
      .mockResolvedValueOnce({ ok: true, json: async () => ({ error: "unexpected shape" }) })
      .mockResolvedValueOnce(page([{ id: "healthy" }], false));
    await expect(getServerPublicNotes()).rejects.toThrow("Invalid public catalog response");
    advancePastBackoff();
    await expect(getServerPublicNotes()).resolves.toEqual([{ id: "healthy" }]);
    expect(global.fetch).toHaveBeenCalledTimes(3);
  });

  it("does not retry a failed walk for 15 s: it rejects with no fetch, then loads again", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({ ok: false })
      .mockResolvedValueOnce(page([{ id: "sitemap-class" }], false))
      .mockResolvedValueOnce(page([{ id: "page-class" }], false));
    await expect(getServerPublicNotes()).rejects.toThrow("Could not load public catalog");
    await expect(getServerPublicNotes()).rejects.toBeInstanceOf(CatalogBackoffError);
    // A different key (the sitemap class) is not blocked by the page-class failure.
    await expect(getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" })).resolves.toEqual([{ id: "sitemap-class" }]);
    expect(global.fetch).toHaveBeenCalledTimes(2);
    advancePastBackoff();
    await expect(getServerPublicNotes()).resolves.toEqual([{ id: "page-class" }]);
    expect(global.fetch).toHaveBeenCalledTimes(3);
  });

  it("backs off the sitemap class after a failed sitemap walk and still never returns a short list", async () => {
    global.fetch = jest.fn().mockResolvedValueOnce({ ok: false });
    await expect(getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" })).rejects.toThrow("Could not load public catalog");
    await expect(getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" })).rejects.toBeInstanceOf(CatalogBackoffError);
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });

  it("sends allowlisted attribution only on walk misses, without Next fetch caching (g, i)", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([{ id: "note" }], false));
    await getServerPublicNotes({ entry: "subject" });
    await getServerPublicNotes({ entry: "exam-hub" }); // same query, same cache entry
    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect((global.fetch as jest.Mock).mock.calls[0][1]).toMatchObject({
      method: "GET", cache: "no-store", headers: { "X-NoteLib-Catalog-Walk": "subject" },
    });
    expect((global.fetch as jest.Mock).mock.calls[0][1].signal).toBeDefined();
    await getServerPublicNotesByCoursePrograms(["Nursing"]);
    expect((global.fetch as jest.Mock).mock.calls[1][1]).toMatchObject({
      method: "GET", cache: "no-store", headers: { "X-NoteLib-Catalog-Walk": "exam-hub" },
    });
    await getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" });
    expect((global.fetch as jest.Mock).mock.calls[2][1]).toMatchObject({
      method: "GET", cache: "no-store", headers: { "X-NoteLib-Catalog-Walk": "sitemap" },
    });
  });

  it("single-flights the entire paginated walk for concurrent metadata and page readers", async () => {
    let releaseFirst!: () => void;
    const firstPage = new Promise<ReturnType<typeof page>>((resolve) => {
      releaseFirst = () => resolve(page([{ id: "first" }], true));
    });
    global.fetch = jest.fn().mockReturnValueOnce(firstPage).mockResolvedValueOnce(page([{ id: "second" }], false));
    const metadataRead = getServerPublicNotesBySubjectSlug("biology");
    const pageRead = getServerPublicNotesBySubjectSlug("biology");
    await Promise.resolve();
    expect(global.fetch).toHaveBeenCalledTimes(1);
    releaseFirst();
    expect(await Promise.all([metadataRead, pageRead])).toEqual([
      [{ id: "first" }, { id: "second" }],
      [{ id: "first" }, { id: "second" }],
    ]);
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it("keeps sitemap and page TTL classes separate", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([], false));
    await getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" });
    await getServerPublicNotes({ entry: "subject" });
    await getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" });
    await getServerPublicNotes({ entry: "subject" });
    expect((global.fetch as jest.Mock).mock.calls.map(([, options]) => options.headers["X-NoteLib-Catalog-Walk"])).toEqual(["sitemap", "subject"]);
  });

  it("keeps the count on the 300-second fetch while rails use uncached upstream reads without attribution", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([], false));
    await getServerPublicNoteCount();
    await getServerPublicNotesBySubject("Biology");
    await getServerPublicNotesBySubjectSlugRecent("biology", 3);
    await getServerPublicNotesByCourseProgramRecent("Nursing", 3);
    const calls = (global.fetch as jest.Mock).mock.calls;
    expect(calls).toHaveLength(4);
    expect(calls[0][1]).toEqual({ method: "GET", next: { revalidate: 300 } });
    calls.slice(1).forEach(([, options]) => {
      expect(options).toMatchObject({ method: "GET", cache: "no-store" });
      expect(options.signal).toBeDefined();
    });
  });

  /**
   * ⚠️ THE TIGHTENED INVARIANT. The version below it permitted the exact shape that caused the
   * 2026-09-05 outage, in two ways: it accepted `subject=` as an ALTERNATIVE to `pageSize`, so
   * `?subject=X&size=4` passed while loading the entire catalog; and it never called the two helpers
   * that actually send the unbounded shape. `size` is NOT a pagination parameter — the server selects
   * its branch on `page != null || pageSize != null`.
   */
  it("never requests /notes/public without pageSize, on any helper", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([], false));

    await getServerPublicNotes();
    await getServerPublicNotesBySubjectSlug("biology");
    await getServerPublicNotesByCoursePrograms(["Nursing"]);
    // The two the previous guard never exercised, and the only two that were unbounded.
    await getServerPublicNoteCount();
    await getServerPublicNotesBySubject("Biology");

    const requestedUrls = (global.fetch as jest.Mock).mock.calls.map(([url]) => String(url));
    expect(requestedUrls.length).toBeGreaterThan(0);
    requestedUrls.forEach((url) => {
      expect(url).toMatch(/[?&]pageSize=/);
    });
  });

  // The invariant the whole change exists to establish.
  it("never requests /notes/public without either a filter or a pageSize", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([], false));

    await getServerPublicNotes();
    await getServerPublicNotesBySubjectSlug("biology");
    await getServerPublicNotesByCoursePrograms(["Nursing"]);

    const requestedUrls = (global.fetch as jest.Mock).mock.calls.map(([url]) => String(url));
    expect(requestedUrls.length).toBeGreaterThan(0);
    requestedUrls.forEach((url) => {
      expect(url).toMatch(/[?&](pageSize=|subject=|courseProgram=)/);
    });
  });

  // ⚠️ GUARD, 2026-10-01: every URL this walker builds must carry a sort the database can execute as a
  // plain index/ORDER BY -- the exact property whose absence caused the production outage this release
  // closes (no `sort` -> the ranked branch -> a full popularity-ranking query PLUS a count, per page).
  // `PublicLibrarySort.java` names exactly two SQL-orderable values: RECENT and TITLE. Mutation check:
  // removing `&sort=recent` from the walker's request URL (server-public-notes.ts) fails this test.
  it("always sends an SQL-orderable sort from the unbounded walker, regardless of caller", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([{ id: "note-1" }], false));

    await getServerPublicNotes();
    await getServerPublicNotesBySubjectSlug("biology");
    await getServerPublicNotesByCoursePrograms(["Nursing"]);

    const requestedUrls = (global.fetch as jest.Mock).mock.calls.map(([url]) => String(url));
    expect(requestedUrls.length).toBeGreaterThan(0);
    requestedUrls.forEach((url) => {
      const sort = new URL(url).searchParams.get("sort");
      expect(["recent", "title"]).toContain(sort);
    });
  });
});

// ⚠️ GUARDS, 2026-10-01, for the two Public Note page rails whose unbounded walk caused 24 production
// restarts since 2026-09-01 (docs/claude-findings/2026-10-01-prod-restarts-public-note-page-fanout-db-saturation.md).
// Each rail must now issue exactly ONE bounded request -- not a walk -- with an explicit `sort=recent`,
// `readyOnly=true` (preserving the eligibility check the old unbounded JS filter used to apply after
// the fact), and a `pageSize` bounded to the caller's own display count plus one (for current-note
// exclusion), never the full candidate set. Mutation check, named per the plan's own requirement:
// removing `sort=recent` or `readyOnly=true` from either function (server-public-notes.ts), or widening
// `pageSize` into an unbounded walk, fails these tests.
describe("bounded, most-recent-first Public Note page rails (production-outage fix)", () => {
  afterEach(() => {
    global.fetch = originalFetch;
  });

  const page = (items: unknown[]) => ({
    ok: true,
    json: async () => ({ items, total: items.length, hasMore: false }),
  });

  it("getServerPublicNotesBySubjectSlugRecent issues exactly one bounded, sorted, eligibility-checked request", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([{ id: "note-1" }]));

    await getServerPublicNotesBySubjectSlugRecent("civil-engineering", 3);

    expect((global.fetch as jest.Mock).mock.calls).toHaveLength(1);
    const requestedUrl = new URL(String((global.fetch as jest.Mock).mock.calls[0][0]));
    expect(requestedUrl.searchParams.get("subject")).toBe("civil-engineering");
    expect(requestedUrl.searchParams.get("sort")).toBe("recent");
    expect(requestedUrl.searchParams.get("readyOnly")).toBe("true");
    expect(requestedUrl.searchParams.get("page")).toBe("0");
    // limit + 1: enough to exclude the current note locally, never a walk.
    expect(requestedUrl.searchParams.get("pageSize")).toBe("4");
  });

  it("getServerPublicNotesByCourseProgramRecent issues exactly one bounded, sorted, eligibility-checked request", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([{ id: "note-1" }]));

    await getServerPublicNotesByCourseProgramRecent("Civil Engineering", 4);

    expect((global.fetch as jest.Mock).mock.calls).toHaveLength(1);
    const requestedUrl = new URL(String((global.fetch as jest.Mock).mock.calls[0][0]));
    expect(requestedUrl.searchParams.get("courseProgram")).toBe("Civil Engineering");
    expect(requestedUrl.searchParams.get("sort")).toBe("recent");
    expect(requestedUrl.searchParams.get("readyOnly")).toBe("true");
    expect(requestedUrl.searchParams.get("page")).toBe("0");
    expect(requestedUrl.searchParams.get("pageSize")).toBe("5");
  });

  it("neither bounded rail function ever pages past page 0", async () => {
    // A `hasMore: true` response must not trigger a second request -- these are single bounded
    // fetches, not a walk. (fetchAllPublicNotePages is a different function and is covered above.)
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ items: [{ id: "note-1" }], total: 500, hasMore: true }),
    });

    await getServerPublicNotesBySubjectSlugRecent("civil-engineering", 3);
    await getServerPublicNotesByCourseProgramRecent("Civil Engineering", 4);

    expect((global.fetch as jest.Mock).mock.calls).toHaveLength(2);
  });

  it("returns an empty list without calling the backend for a blank filter or a non-positive limit", async () => {
    global.fetch = jest.fn();

    await expect(getServerPublicNotesBySubjectSlugRecent("   ", 3)).resolves.toEqual([]);
    await expect(getServerPublicNotesByCourseProgramRecent("Civil Engineering", 0)).resolves.toEqual([]);

    expect(global.fetch).not.toHaveBeenCalled();
  });
});

describe("public note rails use the page-class hard-TTL cache", () => {
  const originalTtlOverride = process.env.NOTELIB_CATALOG_PAGE_TTL_MS;
  let now = 0;

  const page = (items: unknown[]) => ({
    ok: true,
    json: async () => ({ items, total: items.length }),
  });

  beforeEach(() => {
    now = 0;
    jest.spyOn(Date, "now").mockImplementation(() => now);
    delete process.env.NOTELIB_CATALOG_PAGE_TTL_MS;
  });

  afterEach(() => {
    global.fetch = originalFetch;
    jest.restoreAllMocks();
    if (originalTtlOverride === undefined) delete process.env.NOTELIB_CATALOG_PAGE_TTL_MS;
    else process.env.NOTELIB_CATALOG_PAGE_TTL_MS = originalTtlOverride;
  });

  it("caches each rail inside 120 s and refetches at the boundary without returning the old list", async () => {
    const oldNote = { id: "old", title: "Withdrawn title", excerpt: "Withdrawn excerpt" };
    const newNote = { id: "new", title: "Current title", excerpt: "Current excerpt" };
    global.fetch = jest.fn().mockResolvedValueOnce(page([oldNote])).mockResolvedValueOnce(page([newNote]));

    await expect(getServerPublicNotesBySubjectSlugRecent("biology", 6)).resolves.toEqual([oldNote]);
    now = 119_999;
    await expect(getServerPublicNotesBySubjectSlugRecent("biology", 6)).resolves.toEqual([oldNote]);
    expect(global.fetch).toHaveBeenCalledTimes(1);
    now = 120_000;
    await expect(getServerPublicNotesBySubjectSlugRecent("biology", 6)).resolves.toEqual([newNote]);
    expect(global.fetch).toHaveBeenCalledTimes(2);
    expect((global.fetch as jest.Mock).mock.calls[0][1]).toMatchObject({ method: "GET", cache: "no-store" });
  });

  it("clamps a raised page TTL override for rails to 120 s", async () => {
    process.env.NOTELIB_CATALOG_PAGE_TTL_MS = "999999";
    global.fetch = jest.fn().mockResolvedValueOnce(page([{ id: "old" }])).mockResolvedValueOnce(page([{ id: "new" }]));

    await getServerPublicNotesBySubject("Biology");
    now = 120_000;
    await expect(getServerPublicNotesBySubject("Biology")).resolves.toEqual([{ id: "new" }]);
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it("returns an empty rail after a failed expired refresh and retries instead of caching the failure", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce(page([{ id: "withdrawn", title: "Withdrawn title" }]))
      .mockResolvedValueOnce({ ok: false })
      .mockResolvedValueOnce(page([{ id: "replacement" }]));

    await expect(getServerPublicNotesByCourseProgramRecent("Nursing", 4)).resolves.toEqual([{ id: "withdrawn", title: "Withdrawn title" }]);
    now = 120_000;
    await expect(getServerPublicNotesByCourseProgramRecent("Nursing", 4)).resolves.toEqual([]);
    now = 120_000 + CATALOG_FAILURE_BACKOFF_MS;
    await expect(getServerPublicNotesByCourseProgramRecent("Nursing", 4)).resolves.toEqual([{ id: "replacement" }]);
    expect(global.fetch).toHaveBeenCalledTimes(3);
  });

  it("returns empty, uncached rails for malformed JSON, missing items, non-OK, and network errors", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => { throw new SyntaxError("bad JSON"); } })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ total: 1 }) })
      .mockResolvedValueOnce({ ok: false })
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(page([{ id: "recovered" }]));

    for (let attempt = 0; attempt < 4; attempt += 1) {
      await expect(getServerPublicNotesBySubject("Physics")).resolves.toEqual([]);
      now += CATALOG_FAILURE_BACKOFF_MS;
    }
    await expect(getServerPublicNotesBySubject("Physics")).resolves.toEqual([{ id: "recovered" }]);
    expect(global.fetch).toHaveBeenCalledTimes(5);
  });

  it("returns an empty uncached rail after its 10-second request timeout", async () => {
    jest.useFakeTimers();
    global.fetch = jest.fn().mockImplementationOnce((_url: string, options: { signal: AbortSignal }) => new Promise((_resolve, reject) => {
      options.signal.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
    })).mockResolvedValueOnce(page([{ id: "fresh" }]));
    try {
      const pending = getServerPublicNotesBySubject("Biology");
      const result = expect(pending).resolves.toEqual([]);
      await jest.advanceTimersByTimeAsync(10_000);
      await result;
      await jest.advanceTimersByTimeAsync(CATALOG_FAILURE_BACKOFF_MS);
      await expect(getServerPublicNotesBySubject("Biology")).resolves.toEqual([{ id: "fresh" }]);
      expect(global.fetch).toHaveBeenCalledTimes(2);
      expect(jest.getTimerCount()).toBe(0);
    } finally {
      jest.useRealTimers();
    }
  });

  it("returns an empty rail at the whole-load deadline when fetch ignores abort", async () => {
    jest.useFakeTimers();
    const warn = jest.spyOn(console, "warn").mockImplementation();
    global.fetch = jest.fn().mockImplementationOnce(() => new Promise(() => undefined))
      .mockResolvedValueOnce(page([{ id: "fresh" }]));
    try {
      const pending = getServerPublicNotesBySubject("Biology");
      const result = expect(pending).resolves.toEqual([]);
      await jest.advanceTimersByTimeAsync(90_000);
      await result;
      await jest.advanceTimersByTimeAsync(CATALOG_FAILURE_BACKOFF_MS);
      await expect(getServerPublicNotesBySubject("Biology")).resolves.toEqual([{ id: "fresh" }]);
      expect(warn).toHaveBeenCalledWith("CATALOG_CACHE_DEADLINE class=rail deadlineMs=90000");
      expect(global.fetch).toHaveBeenCalledTimes(2);
    } finally {
      warn.mockRestore();
      jest.useRealTimers();
    }
  });

  it("returns an empty rail without calling the backend for 15 s after a failed rail read", async () => {
    global.fetch = jest.fn().mockResolvedValueOnce({ ok: false }).mockResolvedValueOnce(page([{ id: "later" }]));
    await expect(getServerPublicNotesBySubjectSlugRecent("biology", 6)).resolves.toEqual([]);
    now = CATALOG_FAILURE_BACKOFF_MS - 1;
    await expect(getServerPublicNotesBySubjectSlugRecent("biology", 6)).resolves.toEqual([]);
    expect(global.fetch).toHaveBeenCalledTimes(1);
    now = CATALOG_FAILURE_BACKOFF_MS;
    await expect(getServerPublicNotesBySubjectSlugRecent("biology", 6)).resolves.toEqual([{ id: "later" }]);
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it("single-flights 10 concurrent cold rail reads", async () => {
    let resolveFetch!: (response: ReturnType<typeof page>) => void;
    const pending = new Promise<ReturnType<typeof page>>((resolve) => { resolveFetch = resolve; });
    global.fetch = jest.fn().mockReturnValue(pending);

    const reads = Array.from({ length: 10 }, () => getServerPublicNotesBySubjectSlugRecent("biology", 6));
    await Promise.resolve();
    expect(global.fetch).toHaveBeenCalledTimes(1);
    resolveFetch(page([{ id: "shared" }]));
    expect(await Promise.all(reads)).toEqual(Array(10).fill([{ id: "shared" }]));
  });

  it("single-flights 10 failed expired rail reads, returning empty to every waiter", async () => {
    let rejectFetch!: (error: Error) => void;
    const pending = new Promise<ReturnType<typeof page>>((_, reject) => { rejectFetch = reject; });
    global.fetch = jest.fn()
      .mockResolvedValueOnce(page([{ id: "withdrawn" }]))
      .mockReturnValueOnce(pending)
      .mockResolvedValueOnce(page([{ id: "recovered" }]));

    await getServerPublicNotesBySubjectSlugRecent("biology", 6);
    now = 120_000;
    const reads = Array.from({ length: 10 }, () => getServerPublicNotesBySubjectSlugRecent("biology", 6));
    await Promise.resolve();
    expect(global.fetch).toHaveBeenCalledTimes(2);
    rejectFetch(new Error("network"));
    expect(await Promise.all(reads)).toEqual(Array(10).fill([]));
    now = 120_000 + CATALOG_FAILURE_BACKOFF_MS;
    await expect(getServerPublicNotesBySubjectSlugRecent("biology", 6)).resolves.toEqual([{ id: "recovered" }]);
    expect(global.fetch).toHaveBeenCalledTimes(3);
  });

  it("keeps 600 distinct small rail keys resident within the TTL", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([{ id: "one" }]));

    for (let index = 0; index < 600; index += 1) {
      await getServerPublicNotesBySubjectSlugRecent(`subject-${index}`, 6);
    }
    for (let index = 0; index < 600; index += 1) {
      await getServerPublicNotesBySubjectSlugRecent(`subject-${index}`, 6);
    }
    expect(global.fetch).toHaveBeenCalledTimes(600);
  });

  it("keeps the homepage count on the 300 s fetch cache with null failures and direct notes no-store", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ total: 7 }) })
      .mockRejectedValueOnce(new Error("count unavailable"))
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ id: "first" }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ id: "second" }) });

    await expect(getServerPublicNoteCount()).resolves.toBe(7);
    await expect(getServerPublicNoteCount()).resolves.toBeNull();
    await expect(getServerPublicNoteById("same")).resolves.toEqual({ id: "first" });
    await expect(getServerPublicNoteById("same")).resolves.toEqual({ id: "second" });
    const calls = (global.fetch as jest.Mock).mock.calls;
    expect(calls).toHaveLength(4);
    expect(calls.slice(0, 2).map(([, options]) => options)).toEqual(Array(2).fill({ method: "GET", next: { revalidate: 300 } }));
    expect(calls.slice(2).map(([, options]) => options)).toEqual(Array(2).fill({ method: "GET", cache: "no-store" }));
  });
});
