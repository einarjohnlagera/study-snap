import {
  buildCatalogFirstCourseProgramSuggestions,
  getCourseProgramHelperText,
  getGroupedLearnerLevels,
  isCatalogCourseProgram,
  mergeCourseProgramSuggestions,
  normalizeCourseProgram,
} from "./learning-profile";

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

describe("buildCatalogFirstCourseProgramSuggestions", () => {
  it("keeps catalog names first and appends de-duplicated fallback and saved values", () => {
    const suggestions = buildCatalogFirstCourseProgramSuggestions(
      ["Nursing", "Pharmacy"],
      ["Professional / Board Exam Review", " nursing "],
    );

    expect(suggestions.slice(0, 2)).toEqual(["Nursing", "Pharmacy"]);
    expect(suggestions.filter((value) => value.toLowerCase() === "nursing")).toHaveLength(1);
    expect(suggestions).toContain("Professional / Board Exam Review");
    expect(suggestions.indexOf("Software Engineering")).toBeGreaterThan(1);
  });

  it("uses the hardcoded list immediately when the catalog is unavailable or empty", () => {
    expect(buildCatalogFirstCourseProgramSuggestions(null).slice(0, 3)).toEqual([
      "Grade School",
      "High School",
      "Senior High – STEM",
    ]);
    expect(buildCatalogFirstCourseProgramSuggestions([])).toContain("Nursing");
  });
});

describe("isCatalogCourseProgram", () => {
  it("matches case-insensitively after trimming and normalizing dash spacing", () => {
    expect(isCatalogCourseProgram("  senior high-stem ", ["Senior High – STEM"])).toBe(true);
    expect(isCatalogCourseProgram("Professional / Board Exam Review", ["Nursing"])).toBe(false);
  });
});

describe("getCourseProgramHelperText", () => {
  it("returns learner-level-specific helper text for profile flows", () => {
    expect(getCourseProgramHelperText("GRADE_SCHOOL")).toBe(
      "Enter something like General Education, Elementary Math, Reading, etc.",
    );
    expect(getCourseProgramHelperText("SENIOR_HIGH")).toBe(
      "Enter your strand like STEM, ABM, HUMSS, GAS, etc.",
    );
    expect(getCourseProgramHelperText("COLLEGE")).toBe(
      "Enter your degree like Engineering, Nursing, Accountancy, etc.",
    );
    expect(getCourseProgramHelperText("PROFESSIONAL")).toBe(
      "Enter your field like Law, Medicine, IT, Education, etc.",
    );
    expect(getCourseProgramHelperText("PERSONAL_LEARNING")).toBe(
      "Enter the topic you're focusing on like Programming, Finance, History, etc.",
    );
  });

  it("adds note-specific guidance for note metadata forms", () => {
    expect(getCourseProgramHelperText("BOARD_EXAM_REVIEW", "note")).toBe(
      "Enter the program or board exam track like Nursing, Pharmacy, Civil Engineering, etc. This note can use a different value from your profile.",
    );
  });
});

describe("getGroupedLearnerLevels", () => {
  it("keeps Student recommendations unchanged", () => {
    const grouped = getGroupedLearnerLevels("STUDENT");

    expect(grouped.recommendedGroupLabel).toBe("Recommended for Students");
    expect(grouped.recommended.map((option) => option.value)).toEqual([
      "GRADE_SCHOOL",
      "JUNIOR_HIGH",
      "SENIOR_HIGH",
      "COLLEGE",
    ]);
  });

  it("returns Professional and Personal Learning for Professional users", () => {
    const grouped = getGroupedLearnerLevels("PROFESSIONAL");

    expect(grouped.recommendedGroupLabel).toBe("Recommended for Professionals");
    expect(grouped.recommended.map((option) => option.value)).toEqual([
      "PROFESSIONAL",
      "PERSONAL_LEARNING",
    ]);
    expect(grouped.other.map((option) => option.value)).not.toContain("PROFESSIONAL");
    expect(grouped.other.map((option) => option.value)).not.toContain("PERSONAL_LEARNING");
  });
});
