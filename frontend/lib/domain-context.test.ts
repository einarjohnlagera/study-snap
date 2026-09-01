import {
  DOMAIN_CONTEXT_OPTIONS,
  getDomainContextDescription,
  isSubjectSameAsDomainContext,
} from "./domain-context";

describe("Domain Context descriptions", () => {
  it("declares curator guidance for every option", () => {
    expect(DOMAIN_CONTEXT_OPTIONS).toHaveLength(8);
    DOMAIN_CONTEXT_OPTIONS.forEach((option) => {
      expect(getDomainContextDescription(option.value)).toBe(option.description);
      expect(option.description).not.toHaveLength(0);
    });
  });

  it("stays silent while the automatic fallback is selected", () => {
    expect(getDomainContextDescription("")).toBeNull();
  });

  it("limits Engineering Mathematics to notes that teach the computational method", () => {
    const engineeringMathematics = DOMAIN_CONTEXT_OPTIONS.find(
      (option) => option.value === "ENGINEERING_MATHEMATICS",
    );

    expect(engineeringMathematics?.description).toContain("teaches a computational method");
    expect(engineeringMathematics?.description).toContain("not merely when it uses one");
    expect(DOMAIN_CONTEXT_OPTIONS.find((option) => option.value === "GENERAL_EDUCATION")?.description)
      .toBe("General education material, with curriculum depth carried separately by Authored Depth.");
    expect(DOMAIN_CONTEXT_OPTIONS.find((option) => option.value === "PROFESSIONAL_EDUCATION")?.description)
      .toBe("Educational Psychology, Assessment of Learning, Curriculum Development, and Teaching Profession.");
    expect(DOMAIN_CONTEXT_OPTIONS.find((option) => option.value === "NURSING")?.description)
      .toBe("Medical-Surgical, Psychiatric, Pediatric, Maternal & Child, Fundamentals, and nursing-framed Pharmacology.");
    expect(DOMAIN_CONTEXT_OPTIONS.find((option) => option.value === "ACCOUNTANCY")?.description)
      .toBe("FAR, Taxation, Auditing, MAS, RFBT, and Financial Management.");
  });
});

describe("isSubjectSameAsDomainContext", () => {
  it("flags a subject that exactly matches the Domain Context label", () => {
    expect(isSubjectSameAsDomainContext("Nursing", "NURSING")).toBe(true);
  });

  it("ignores case and surrounding whitespace", () => {
    expect(isSubjectSameAsDomainContext("  nursing  ", "NURSING")).toBe(true);
  });

  it("matches labels containing punctuation", () => {
    expect(
      isSubjectSameAsDomainContext("Professional Practice & Regulation", "PROFESSIONAL_PRACTICE_AND_REGULATION"),
    ).toBe(true);
  });

  it("does not flag a specific subject under the same Domain Context", () => {
    expect(isSubjectSameAsDomainContext("Algebra", "ENGINEERING_MATHEMATICS")).toBe(false);
  });

  it("does not flag a subject that merely contains the label", () => {
    expect(isSubjectSameAsDomainContext("Nursing Pharmacology", "NURSING")).toBe(false);
  });

  it("stays silent when no Domain Context is selected", () => {
    expect(isSubjectSameAsDomainContext("Nursing", "")).toBe(false);
  });

  it("stays silent for a blank or whitespace-only subject", () => {
    expect(isSubjectSameAsDomainContext("", "NURSING")).toBe(false);
    expect(isSubjectSameAsDomainContext("   ", "NURSING")).toBe(false);
  });
});
