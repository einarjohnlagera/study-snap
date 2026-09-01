import type { DomainContext } from "@/lib/api";

export const DOMAIN_CONTEXT_OPTIONS: Array<{ value: DomainContext; label: string; description: string }> = [
  {
    value: "ENGINEERING_MATHEMATICS",
    label: "Engineering Mathematics",
    description: "Choose this when the note teaches a computational method, not merely when it uses one. Algebra, Trigonometry, Analytic Geometry, Calculus, Differential Equations, Probability & Statistics, and Engineering Economics.",
  },
  {
    value: "ENGINEERING_SCIENCES",
    label: "Engineering Sciences",
    // ⚠️ Widened in v0.99.0. This previously listed only the Civil-flavoured half — Strength of
    // Materials, Mechanics, Hydraulics, Thermodynamics, Engineering Materials — which is an
    // enumeration of a NARROWER value than the one it names, and it is why ~41 Building Utilities
    // notes sat unclassified: a curator reading it would never pick this for HVAC or acoustics.
    // Those notes then generated with NO computation guidance, since QUANTITATIVE_KEYWORDS has no
    // "architecture" and their titles trip nothing. Building services belong here on ADR-001's
    // own binary test — plumbing is applied hydraulics, HVAC is applied thermodynamics — and the
    // ratified Water Supply Engineering worked example is the same family.
    description: "Shared engineering knowledge across programs: Strength of Materials, Engineering Mechanics, Hydraulics or Fluid Mechanics, Thermodynamics, and Engineering Materials — and building services such as plumbing, HVAC, electrical distribution, lighting, acoustics, fire protection, and vertical transportation. Also construction materials and testing, and construction or project management.",
  },
  {
    value: "CIVIL_ENGINEERING",
    label: "Civil Engineering",
    // ⚠️ Corrected in v0.99.0: Surveying and Construction Management were removed. Production
    // practice places them in Engineering Sciences — Construction Materials and Construction/
    // Project Management notes authored for Civil Engineering are reused UNCHANGED in
    // Architecture, which is ADR-001's binary test resolving to the shared bundle. Keeping them
    // here taught curators to over-select this value for cross-program material.
    // ⚠️ Surveying specifically is INSUFFICIENT EVIDENCE, not reassigned: no Surveying notes
    // exist in the ALE set, so it is simply not claimed by either value until evidence arrives.
    description: "Civil-specific treatment: Structural Analysis & Design, Steel or RC Design, Geotechnical, Foundation, Soil Mechanics, Transportation, Hydrology, and Water Resources. Knowledge a sibling program would use unchanged belongs in Engineering Sciences instead.",
  },
  {
    value: "PROFESSIONAL_PRACTICE_AND_REGULATION",
    label: "Professional Practice & Regulation",
    // ⚠️ Corrected in v0.99.0 to BP 344, the accessibility law (Batas Pambansa Blg. 344).
    // ⚠️ THE RATIFIED SPEC ITSELF SAYS "BP 334" AND IS ALSO WRONG -- do not "restore" it. A cold
    // agent falsified this comment's first draft, which claimed the spec said 344: it does not.
    // 08-taxonomy-validation-and-final-recommendation.md:41 and :60 both say 334 (attributing 32
    // notes to it), and RELEASES.md silently misquoted that as 344, which the first draft then
    // cited as proof. The evidence is the CURRICULUM sources, not the taxonomy spec:
    // docs/gpt-contexts/archi-comprehensive-set-1.md:995 ("BP 344-related accessibility") and
    // set-2:1056 ("Subject: BP 344"), plus the audit's own reuse table. BP 334 is not a building
    // or accessibility law. The description also read as an
    // Engineering-union-Architecture list and omitted National Building Code and Construction
    // Safety, which the ALE reuse data places here -- 19 of the 47 cross-program reused rows.
    // ⚠️ `quantitative` STAYS FALSE, and that is a decision rather than an oversight. Widening a
    // description changes WHICH notes land here -- that is exactly what item 4 did to
    // ENGINEERING_SCIENCES -- but codes, laws, ethics and accessibility compliance are prose
    // treatment, so it must not change HOW they are written. Per v0.85.0, `false` is a no-op that
    // falls through to the untouched keyword scan, while `true` is a new signal that is PERMANENT
    // per note, since Study Packs never auto-regenerate.
    description: "Codes, laws, ethics and licensure shared across programs: Engineering Laws, Ethics and Contracts; Professional Practice; Building Laws including the National Building Code and BP 344 accessibility; and Construction Safety.",
  },
  {
    value: "GENERAL_EDUCATION",
    label: "General Education",
    description: "General education material, with curriculum depth carried separately by Authored Depth.",
  },
  {
    value: "PROFESSIONAL_EDUCATION",
    label: "Professional Education",
    description: "Educational Psychology, Assessment of Learning, Curriculum Development, and Teaching Profession.",
  },
  {
    value: "NURSING",
    label: "Nursing",
    description: "Medical-Surgical, Psychiatric, Pediatric, Maternal & Child, Fundamentals, and nursing-framed Pharmacology.",
  },
  {
    value: "ACCOUNTANCY",
    label: "Accountancy",
    description: "FAR, Taxation, Auditing, MAS, RFBT, and Financial Management.",
  },
];

export function getDomainContextLabel(value: DomainContext): string {
  return DOMAIN_CONTEXT_OPTIONS.find((option) => option.value === value)?.label ?? value;
}

export function getDomainContextDescription(value: DomainContext | ""): string | null {
  if (!value) {
    return null;
  }
  return DOMAIN_CONTEXT_OPTIONS.find((option) => option.value === value)?.description ?? null;
}

/**
 * Subject and Domain Context are different axes, so a value appearing in both is not an error --
 * a broad survey note about nursing really is `subject = Nursing`. But it usually means the
 * subject was written too broadly, which is what ADR-001 asks admins to be nudged about. This is
 * advisory only: never a validation error, never a save block.
 */
export function isSubjectSameAsDomainContext(
  subject: string,
  domainContext: DomainContext | "",
): boolean {
  if (!domainContext) {
    return false;
  }
  const normalizedSubject = subject.trim().toLowerCase();
  return normalizedSubject.length > 0
    && normalizedSubject === getDomainContextLabel(domainContext).trim().toLowerCase();
}
