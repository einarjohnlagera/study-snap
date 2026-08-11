import { getNoteApplicablePrograms } from "./api";

describe("getNoteApplicablePrograms", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("returns the programs and backend-resolved shadow flag", async () => {
    const payload = {
      programs: [{ id: "program-nursing", name: "Nursing" }],
      courseProgramShadowed: true,
    };
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);

    await expect(getNoteApplicablePrograms("note-1")).resolves.toEqual(payload);
  });

  it("rejects a malformed successful response so the surface can use its recoverable error state", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue([]),
    } as unknown as Response);

    await expect(getNoteApplicablePrograms("note-1"))
      .rejects.toThrow("Could not load Course / Program(s).");
  });
});
