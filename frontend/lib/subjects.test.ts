import { normalizeSubject } from "./subjects";

describe("normalizeSubject", () => {
  it("standardizes dash formatting for display and filtering", () => {
    expect(normalizeSubject("  Biology-Cell Division  ")).toBe("Biology – Cell Division");
    expect(normalizeSubject("biology -  cell division")).toBe("biology – cell division");
    expect(normalizeSubject("Biology–Cell Division")).toBe("Biology – Cell Division");
  });
});
