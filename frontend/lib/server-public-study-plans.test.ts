import { getServerPublicStudyPlansByCoursePrograms } from "./server-public-study-plans";

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
  });

  it("keeps successful matches when another exact-match lookup fails", async () => {
    (globalThis.fetch as jest.Mock)
      .mockRejectedValueOnce(new Error("timeout"))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [plan("nursing", "Nursing")],
      });

    await expect(getServerPublicStudyPlansByCoursePrograms([
      "Architecture",
      "Nursing",
    ])).resolves.toEqual([
      plan("nursing", "Nursing"),
    ]);
  });

  it("degrades to an empty result instead of crashing on a malformed but 200-status response", async () => {
    // A valid-JSON, non-array 200 response used to throw uncaught downstream (`result.value.forEach`
    // assumed an array), crashing the anonymous, SEO-relevant Exam Hub page's server render.
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
    ])).resolves.toEqual([
      plan("nursing", "Nursing"),
    ]);
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
