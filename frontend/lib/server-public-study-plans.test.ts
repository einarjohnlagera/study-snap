import { getServerPublicStudyPlansByCoursePrograms } from "./server-public-study-plans";
import { resetCatalogCacheForTests } from "./catalog-cache";

const originalFetch = globalThis.fetch;

const plan = (id: string, courseProgram: string) => ({
  id,
  title: `${courseProgram} Official Set`,
  description: null,
  visibility: "PUBLIC" as const,
  courseProgram,
  sourcePlanId: null,
  parentCollectionId: null,
  itemCount: 2,
  childCount: 0,
  notesPracticed: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
});

describe("getServerPublicStudyPlansByCoursePrograms", () => {
  afterAll(() => {
    globalThis.fetch = originalFetch;
  });

  beforeEach(() => {
    resetCatalogCacheForTests();
    globalThis.fetch = jest.fn();
  });

  it("looks up every configured course/program and deduplicates matching sets", async () => {
    (globalThis.fetch as jest.Mock)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [plan("shared", "Architecture")],
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [plan("shared", "Architecture"), plan("nursing", "Nursing")],
      });

    const result = await getServerPublicStudyPlansByCoursePrograms([
      "Architecture",
      "Nursing",
    ]);

    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    expect((globalThis.fetch as jest.Mock).mock.calls[0][0]).toContain("courseProgram=Architecture");
    expect((globalThis.fetch as jest.Mock).mock.calls[1][0]).toContain("courseProgram=Nursing");
    expect(result.map((item) => item.id)).toEqual(["shared", "nursing"]);
    for (const [, options] of (globalThis.fetch as jest.Mock).mock.calls) {
      expect(options).toEqual({ method: "GET", cache: "no-store" });
    }
    await getServerPublicStudyPlansByCoursePrograms(["Nursing", "Architecture"]);
    expect(globalThis.fetch).toHaveBeenCalledTimes(2); // sorted key ignores caller order
  });

  it("rejects the whole plan set when one program lookup fails and retries next time", async () => {
    (globalThis.fetch as jest.Mock)
      .mockRejectedValueOnce(new Error("timeout"))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [plan("nursing", "Nursing")],
      });

    await expect(getServerPublicStudyPlansByCoursePrograms([
      "Architecture",
      "Nursing",
    ])).rejects.toThrow("timeout");
    (globalThis.fetch as jest.Mock).mockResolvedValue({ ok: true, json: async () => [plan("nursing", "Nursing")] });
    await expect(getServerPublicStudyPlansByCoursePrograms(["Architecture", "Nursing"])).resolves.toHaveLength(1);
  });

  it("rejects a non-OK response instead of returning no plans for the hub (l)", async () => {
    (globalThis.fetch as jest.Mock).mockResolvedValue({ ok: false, status: 503 });
    await expect(getServerPublicStudyPlansByCoursePrograms(["Nursing"])).rejects.toThrow("Could not load public study plans");
  });

  it("rejects a malformed 200 response rather than rendering a hub without review sets", async () => {
    (globalThis.fetch as jest.Mock)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ error: "unexpected shape" }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [plan("nursing", "Nursing")],
      });

    await expect(getServerPublicStudyPlansByCoursePrograms([
      "Architecture",
      "Nursing",
    ])).rejects.toThrow("Invalid public study plans response");
  });

  it("returns the same PNLE sets after removing the zero-match subject-area alias", async () => {
    (globalThis.fetch as jest.Mock)
      .mockResolvedValueOnce({ ok: true, json: async () => [plan("pnle", "Nursing")] })
      .mockResolvedValueOnce({ ok: true, json: async () => [] })
      .mockResolvedValueOnce({ ok: true, json: async () => [plan("pnle", "Nursing")] });

    const before = await getServerPublicStudyPlansByCoursePrograms([
      "Nursing",
      "Medical – Surgical Nursing",
    ]);
    const after = await getServerPublicStudyPlansByCoursePrograms(["Nursing"]);

    expect(after).toEqual(before);
    expect(after.map((item) => item.id)).toEqual(["pnle"]);
  });
});
