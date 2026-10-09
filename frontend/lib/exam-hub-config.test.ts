import { EXAM_HUBS, getExamHubConfig, getExamSlugForCourseProgram } from "./exam-hub-config";

describe("exam hub config", () => {
  it.each([
    ["Architecture", "ale"],
    ["Nursing", "pnle"],
    ["Education", "let"],
    ["nursing", "pnle"],
    ["Accountancy", "cpale"],
  ])("maps %s to %s", (courseProgram, expectedSlug) => {
    expect(getExamSlugForCourseProgram(courseProgram)).toBe(expectedSlug);
  });

  it("recognizes Civil Engineering as the CELE hub", () => {
    expect(getExamHubConfig("ce")).toEqual(EXAM_HUBS.ce);
    expect(EXAM_HUBS.ce).toMatchObject({
      shortName: "CELE",
      fullName: "Civil Engineering Licensure Examination (CELE)",
      coursePrograms: ["Civil Engineering"],
    });
    expect(getExamSlugForCourseProgram(" Civil Engineering ")).toBe("ce");
  });

  it("returns null for unknown course programs", () => {
    expect(getExamSlugForCourseProgram("Medical – Surgical Nursing")).toBeNull();
    expect(getExamSlugForCourseProgram(null)).toBeNull();
  });
});
