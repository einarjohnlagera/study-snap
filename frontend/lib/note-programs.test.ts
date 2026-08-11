import { resolveEffectivePrograms, sharesAnyProgram } from "@/lib/note-programs";

describe("resolveEffectivePrograms", () => {
  it("prefers joined catalog programs over the legacy string", () => {
    expect(resolveEffectivePrograms(["Nursing", "Pharmacy"], "Ignored")).toEqual(["Nursing", "Pharmacy"]);
  });

  it("falls back to the legacy string when a note carries no join rows", () => {
    expect(resolveEffectivePrograms([], "BS Civil Engineering")).toEqual(["BS Civil Engineering"]);
    expect(resolveEffectivePrograms(null, "BS Civil Engineering")).toEqual(["BS Civil Engineering"]);
  });

  it("resolves a curated note by its join rows alone", () => {
    // ADR-001 defines a curator-authored note's legacy string as null. Reading that string alone is
    // what made curated notes look programme-less to M3's and M4's consumers.
    expect(resolveEffectivePrograms(["Nursing"], null)).toEqual(["Nursing"]);
  });

  it("returns empty when a note has neither representation", () => {
    expect(resolveEffectivePrograms(null, null)).toEqual([]);
    expect(resolveEffectivePrograms([], "   ")).toEqual([]);
  });

  it("trims and de-duplicates joined names", () => {
    expect(resolveEffectivePrograms([" Nursing ", "Nursing"], null)).toEqual(["Nursing"]);
  });
});

describe("sharesAnyProgram", () => {
  it("matches when a multi-program note overlaps on any single program", () => {
    expect(sharesAnyProgram(["Civil Engineering", "Nursing"], ["Nursing"])).toBe(true);
  });

  it("does not match unrelated programs", () => {
    expect(sharesAnyProgram(["Civil Engineering"], ["Nursing"])).toBe(false);
  });

  it("treats an empty side as no match, so callers must handle it explicitly", () => {
    // Interview Practice relies on this: no resolvable program falls back to offering everything
    // rather than presenting an empty picker.
    expect(sharesAnyProgram([], ["Nursing"])).toBe(false);
    expect(sharesAnyProgram(["Nursing"], [])).toBe(false);
  });
});
