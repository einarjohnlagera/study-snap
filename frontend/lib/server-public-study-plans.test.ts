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
        json: async () => [plan("shared", "Nursing")],
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [plan("shared", "Nursing"), plan("surgical", "Medical – Surgical Nursing")],
      });

    const result = await getServerPublicStudyPlansByCoursePrograms([
      "Nursing",
      "Medical – Surgical Nursing",
    ]);

    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    expect((globalThis.fetch as jest.Mock).mock.calls[0][0]).toContain("courseProgram=Nursing");
    expect((globalThis.fetch as jest.Mock).mock.calls[1][0]).toContain(
      "courseProgram=Medical+%E2%80%93+Surgical+Nursing",
    );
    expect(result.map((item) => item.id)).toEqual(["shared", "surgical"]);
  });

  it("keeps successful matches when another exact-match lookup fails", async () => {
    (globalThis.fetch as jest.Mock)
      .mockRejectedValueOnce(new Error("timeout"))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [plan("surgical", "Medical – Surgical Nursing")],
      });

    await expect(getServerPublicStudyPlansByCoursePrograms([
      "Nursing",
      "Medical – Surgical Nursing",
    ])).resolves.toEqual([
      plan("surgical", "Medical – Surgical Nursing"),
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
        json: async () => [plan("surgical", "Medical – Surgical Nursing")],
      });

    await expect(getServerPublicStudyPlansByCoursePrograms([
      "Nursing",
      "Medical – Surgical Nursing",
    ])).resolves.toEqual([
      plan("surgical", "Medical – Surgical Nursing"),
    ]);
  });
});
