import { isSubjectSameAsDomainContext } from "./domain-context";

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
