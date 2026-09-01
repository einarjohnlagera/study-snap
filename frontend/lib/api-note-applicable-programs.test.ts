import { getNoteApplicablePrograms } from "./api";

describe("getNoteApplicablePrograms", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("returns the programs, the shadow flag and the resolver-derived writing domain", async () => {
    const payload = {
      programs: [{ id: "program-nursing", name: "Nursing" }],
      courseProgramShadowed: true,
      effectiveWritingDomain: "Nursing",
    };
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);

    await expect(getNoteApplicablePrograms("note-1")).resolves.toEqual(payload);
  });

  // The field reached the response TYPE without reaching the runtime parser, so it type-checked at the
  // call sites, was `undefined` in the browser, and the writing-domain line silently never rendered.
  // Every surface test mocks this function, so only a parser test can catch that class of gap.
  it("carries the writing domain through the parser rather than dropping it", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({
        programs: [{ id: "program-ce", name: "Civil Engineering" }],
        courseProgramShadowed: false,
        effectiveWritingDomain: "Engineering Sciences",
      }),
    } as unknown as Response);

    const response = await getNoteApplicablePrograms("note-1");

    expect(response.effectiveWritingDomain).toBe("Engineering Sciences");
  });

  it("normalizes a missing or non-string writing domain to null, never undefined", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({
        programs: [],
        courseProgramShadowed: false,
      }),
    } as unknown as Response);

    const response = await getNoteApplicablePrograms("note-1");

    // null means "nothing resolved" and drives "Writing domain needs attention"; undefined would make
    // the surface unable to tell that apart from "not loaded".
    expect(response.effectiveWritingDomain).toBeNull();
    expect("effectiveWritingDomain" in response).toBe(true);
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
