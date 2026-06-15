import { parseBulkGenerationText } from "./bulk-generation";

describe("parseBulkGenerationText", () => {
  it("parses subject groups, skips blank lines, and reports leading ignored lines", () => {
    const result = parseBulkGenerationText(`
ALE review list

Subject: Mathematics
Algebraic Expressions
Plane Geometry

subject: Structural Theory
Loads and Reactions

Subject: Empty Group
    `);

    expect(result).toEqual({
      groups: [
        {
          subject: "Mathematics",
          titles: ["Algebraic Expressions", "Plane Geometry"],
        },
        {
          subject: "Structural Theory",
          titles: ["Loads and Reactions"],
        },
      ],
      totalTitles: 3,
      ignoredLineCount: 1,
    });
  });
});
