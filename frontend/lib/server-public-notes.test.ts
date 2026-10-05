import {
  getServerPublicNoteCount,
  getServerPublicNotes,
  getServerPublicNotesByCourseProgramRecent,
  getServerPublicNotesByCoursePrograms,
  getServerPublicNotesBySubject,
  getServerPublicNotesBySubjectSlug,
  getServerPublicNotesBySubjectSlugRecent,
} from "./server-public-notes";

const originalFetch = global.fetch;

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
      json: async () => ({ items: [nursingNote], total: 1 }),
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
  it("stops on an empty page even when the backend keeps claiming there is more", async () => {
    global.fetch = jest.fn().mockResolvedValue(page([], true));

    await expect(getServerPublicNotes()).resolves.toEqual([]);

    expect((global.fetch as jest.Mock).mock.calls).toHaveLength(1);
  });

  it("keeps what it has collected when a later page fails, rather than failing the build", async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce(page(Array.from({ length: 250 }, (_, i) => ({ id: `note-${i}` })), true))
      .mockResolvedValueOnce({ ok: false });

    const notes = await getServerPublicNotes();

    expect(notes).toHaveLength(250);
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
