import {
  getServerExamGoalCoursePrograms,
  getServerExamSlugForCourseProgram,
} from "./server-exam-goal-course-programs";

const originalFetch = globalThis.fetch;

describe("server exam-goal course programs", () => {
  afterAll(() => {
    globalThis.fetch = originalFetch;
  });

  beforeEach(() => {
    globalThis.fetch = jest.fn();
  });

  it("returns the catalog-derived program list", async () => {
    (globalThis.fetch as jest.Mock).mockResolvedValue({
      ok: true,
      json: async () => ({
        ale: ["Architecture"],
        pnle: ["Nursing"],
        let: ["Education"],
        cpale: ["Accountancy"],
      }),
    });

    await expect(getServerExamGoalCoursePrograms("pnle")).resolves.toEqual(["Nursing"]);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/public/exam-goals/course-programs",
      expect.objectContaining({ method: "GET", next: { revalidate: 300 } }),
    );
  });

  it("falls back per slug when the catalog response is empty or incomplete", async () => {
    (globalThis.fetch as jest.Mock).mockResolvedValue({
      ok: true,
      json: async () => ({ ale: [], pnle: ["Nursing"] }),
    });

    await expect(getServerExamGoalCoursePrograms("ale")).resolves.toEqual(["Architecture"]);
  });

  it("falls back when the catalog lookup fails", async () => {
    (globalThis.fetch as jest.Mock).mockRejectedValue(new Error("backend unavailable"));

    await expect(getServerExamGoalCoursePrograms("cpale")).resolves.toEqual(["Accountancy"]);
  });

  it("uses catalog-derived values for the reverse mapping", async () => {
    (globalThis.fetch as jest.Mock).mockResolvedValue({
      ok: true,
      json: async () => ({
        ale: ["Architecture"],
        pnle: ["Nursing"],
        let: ["Education"],
        cpale: ["Accountancy"],
      }),
    });

    await expect(getServerExamSlugForCourseProgram(" nursing ")).resolves.toBe("pnle");
    await expect(getServerExamSlugForCourseProgram("Medical – Surgical Nursing")).resolves.toBeNull();
  });
});
