import { mergeCourseProgramSuggestions, normalizeCourseProgram } from "./learning-profile";

describe("normalizeCourseProgram", () => {
  it("standardizes dash spacing for display and filtering", () => {
    expect(normalizeCourseProgram("  Senior High-STEM  ")).toBe("Senior High – STEM");
    expect(normalizeCourseProgram("Civil Engineering -  Structural Design")).toBe("Civil Engineering – Structural Design");
    expect(normalizeCourseProgram("Civil Engineering–Structural Design")).toBe("Civil Engineering – Structural Design");
  });
});

describe("mergeCourseProgramSuggestions", () => {
  it("dedupes equivalent values while preserving normalized display labels", () => {
    expect(
      mergeCourseProgramSuggestions(
        ["biology - cell division", "Nursing"],
        ["Biology – Cell Division", "Computer Science"],
        ["  nursing  "],
      ),
    ).toEqual([
      "Biology – Cell Division",
      "Computer Science",
      "Nursing",
    ]);
  });
});
