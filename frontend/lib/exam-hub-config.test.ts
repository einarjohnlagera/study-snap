import { getExamSlugForCourseProgram } from "./exam-hub-config";

describe("exam hub config", () => {
  it.each([
    ["Architecture", "ale"],
    ["Nursing", "pnle"],
    ["Medical – Surgical Nursing", "pnle"],
    ["Education", "let"],
    ["nursing", "pnle"],
    ["Accountancy", "cpale"],
  ])("maps %s to %s", (courseProgram, expectedSlug) => {
    expect(getExamSlugForCourseProgram(courseProgram)).toBe(expectedSlug);
  });

  it("returns null for unknown course programs", () => {
    expect(getExamSlugForCourseProgram("Civil Engineering")).toBeNull();
    expect(getExamSlugForCourseProgram(null)).toBeNull();
  });
});
