import { createCourseProgram, findSimilarCoursePrograms, getCourseProgramCatalog, updateCourseProgram } from "./api";

describe("course program catalog API", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("creates and defensively parses a catalog item", async () => {
    const payload = {
      id: "program-new",
      name: "Chemical Engineering",
      programFamilyId: "family-engineering",
      programFamilyName: "Engineering",
      programFamilies: [{ id: "family-engineering", name: "Engineering" }],
      isActive: true,
    };
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);

    await expect(createCourseProgram({ name: payload.name, programFamilyIds: [payload.programFamilyId] }))
      .resolves.toEqual(payload);
  });

  it("treats a missing isActive field as active, for a backend not yet deployed with the column", async () => {
    const payload = {
      id: "program-legacy",
      name: "Chemical Engineering",
      programFamilyId: "family-engineering",
      programFamilyName: "Engineering",
    };
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);

    await expect(createCourseProgram({ name: payload.name, programFamilyId: payload.programFamilyId }))
      .resolves.toEqual({ ...payload, isActive: true, programFamilies: [{ id: "family-engineering", name: "Engineering" }] });
  });

  it("rejects a malformed create response", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({ id: "program-new" }),
    } as unknown as Response);

    await expect(createCourseProgram({ name: "Chemical Engineering" }))
      .rejects.toThrow("Could not add the Course / Program to the catalog.");
  });

  it("returns defensively parsed near matches", async () => {
    const payload = [{
      id: "program-civil",
      name: "Civil Engineering",
      programFamilyId: null,
      programFamilyName: null,
      programFamilies: [],
      isActive: true,
    }];
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);

    await expect(findSimilarCoursePrograms("Civil Engineer")).resolves.toEqual(payload);
  });

  it("round-trips two memberships and sends one authoritative PATCH", async () => {
    const payload = {
      id: "program-overlap", name: "Computer Engineering", isActive: true,
      programFamilies: [
        { id: "family-computing", name: "Computing & Technology" },
        { id: "family-engineering", name: "Engineering" },
      ],
      programFamilyId: "family-computing", programFamilyName: "Computing & Technology",
    };
    globalThis.fetch = jest.fn().mockResolvedValue({ ok: true, status: 200, json: jest.fn().mockResolvedValue(payload) } as unknown as Response);

    await expect(updateCourseProgram(payload.id, { programFamilyIds: payload.programFamilies.map((family) => family.id) }))
      .resolves.toEqual(payload);
    const [, init] = (globalThis.fetch as jest.Mock).mock.calls[0];
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body)).toEqual({ programFamilyIds: ["family-computing", "family-engineering"] });
  });

  it("parses an old catalog list payload that omits programFamilies", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true, status: 200, json: jest.fn().mockResolvedValue([{ id: "legacy", name: "Nursing", programFamilyId: null, programFamilyName: null }]),
    } as unknown as Response);
    await expect(getCourseProgramCatalog()).resolves.toEqual([{
      id: "legacy", name: "Nursing", programFamilies: [], programFamilyId: null, programFamilyName: null, isActive: true,
    }]);
  });
});
