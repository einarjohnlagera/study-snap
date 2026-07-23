import { buildConceptAnchorId, normalizeConceptKey } from "./concepts";

describe("concept anchors", () => {
  it("uses the same trim and case normalization as concept matching", () => {
    expect(normalizeConceptKey("  Cell Membrane  ")).toBe("cell membrane");
    expect(buildConceptAnchorId("  Cell Membrane  ")).toBe("concept-cell-membrane");
  });

  it("creates URL-safe anchors for accented and punctuation-heavy concepts", () => {
    expect(buildConceptAnchorId("Naïve T-cell: CD4+/CD8+")).toBe("concept-naive-t-cell-cd4-cd8");
  });
});
