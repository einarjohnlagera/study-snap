import {
  getServerPublicNoteCount,
  getServerPublicNotesByCoursePrograms,
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
});
