import {
  getServerPublicNoteCount,
  getServerPublicNotes,
  getServerPublicNotesByCoursePrograms,
  getServerPublicNotesBySubjectSlug,
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
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/notes/public?size=1",
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

  it("returns an empty list for a blank slug without calling the backend", async () => {
    global.fetch = jest.fn();

    await expect(getServerPublicNotesBySubjectSlug("   ")).resolves.toEqual([]);

    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("pages the full catalog so no single response can cross the limit", async () => {
    const first = Array.from({ length: 250 }, (_, index) => ({ id: `note-${index}` }));
    global.fetch = jest.fn()
      .mockResolvedValueOnce(page(first, true))
      .mockResolvedValueOnce(page([{ id: "note-250" }], false));

    const notes = await getServerPublicNotes();

    expect(notes).toHaveLength(251);
    const requestedUrls = (global.fetch as jest.Mock).mock.calls.map(([url]) => String(url));
    expect(requestedUrls).toHaveLength(2);
    expect(requestedUrls[0]).toContain("page=0&pageSize=250");
    expect(requestedUrls[1]).toContain("page=1&pageSize=250");
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
});
