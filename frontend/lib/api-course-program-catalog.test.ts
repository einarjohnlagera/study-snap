import { createCourseProgram, findSimilarCoursePrograms } from "./api";

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
      isActive: true,
    };
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);

    await expect(createCourseProgram({ name: payload.name, programFamilyId: payload.programFamilyId }))
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
      .resolves.toEqual(payload);
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
      isActive: true,
    }];
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);

    await expect(findSimilarCoursePrograms("Civil Engineer")).resolves.toEqual(payload);
  });
});
