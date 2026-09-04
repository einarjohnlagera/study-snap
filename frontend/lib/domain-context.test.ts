import {
  DOMAIN_CONTEXT_OPTIONS,
  getDomainContextDescription,
  isSubjectSameAsDomainContext,
} from "./domain-context";

describe("Domain Context descriptions", () => {
  it("declares curator guidance for every option", () => {
    expect(DOMAIN_CONTEXT_OPTIONS).toHaveLength(11);
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

  // ⚠️ These pin the ROUTING clause, not merely that a description exists. The defect that produced
  // 215 unset ALE rows was a description enumerating a narrower value than the one it named, so a
  // description that says what belongs but not what does NOT belong is the failure mode to guard.
  it("routes adjacent material away from each v0.111.0 architecture value", () => {
    const descriptionFor = (value: string) =>
      DOMAIN_CONTEXT_OPTIONS.find((option) => option.value === value)?.description ?? "";

    expect(descriptionFor("ARCHITECTURAL_DESIGN")).toContain("architectural programming");
    expect(descriptionFor("ARCHITECTURAL_DESIGN")).toContain("belongs in Engineering Sciences instead");

    expect(descriptionFor("ARCHITECTURAL_HISTORY_AND_THEORY")).toContain("heritage conservation");
    expect(descriptionFor("ARCHITECTURAL_HISTORY_AND_THEORY")).toContain("not General Education");

    expect(descriptionFor("PLANNING_AND_SITE_DEVELOPMENT")).toContain("land use and zoning");
    expect(descriptionFor("PLANNING_AND_SITE_DEVELOPMENT")).toContain("belongs in Engineering Sciences");
  });

  // ⚠️ The label is the PROMPT PAYLOAD -- effectiveAuthoringDomain returns getLabel(), so changing
  // one rewrites what future generations are told. Pinned here as well as in DomainContextTest.
  it("keeps the three new labels as the curriculum vocabulary they were approved as", () => {
    const label = (value: string) =>
      DOMAIN_CONTEXT_OPTIONS.find((option) => option.value === value)?.label;
    expect(label("ARCHITECTURAL_DESIGN")).toBe("Architectural Design");
    expect(label("ARCHITECTURAL_HISTORY_AND_THEORY")).toBe("History and Theory of Architecture");
    expect(label("PLANNING_AND_SITE_DEVELOPMENT")).toBe("Planning and Site Development");
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

  // ⚠️ v0.111.0 SHIPPED THIS AS A NAMED KNOWN LIMITATION: ~51 ALE notes carry
  // subject = "Architectural Design" under the same-named Domain Context, so the advisory fires on
  // them. It is advisory-only and was deliberately NOT suppressed per-value. This pins the
  // DOCUMENTED behaviour, so a later "fix" that special-cases the new values fails here rather than
  // silently falsifying the release note.
  it("still flags the v0.111.0 architecture values whose label a subject can match", () => {
    expect(isSubjectSameAsDomainContext("Architectural Design", "ARCHITECTURAL_DESIGN")).toBe(true);
    expect(
      isSubjectSameAsDomainContext("History and Theory of Architecture", "ARCHITECTURAL_HISTORY_AND_THEORY"),
    ).toBe(true);
    expect(isSubjectSameAsDomainContext("Site Planning", "PLANNING_AND_SITE_DEVELOPMENT")).toBe(false);
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
