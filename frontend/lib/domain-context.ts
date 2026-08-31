import type { DomainContext } from "@/lib/api";

export const DOMAIN_CONTEXT_OPTIONS: Array<{ value: DomainContext; label: string; description: string }> = [
  {
    value: "ENGINEERING_MATHEMATICS",
    label: "Engineering Mathematics",
    description: "Algebra, Trigonometry, Analytic Geometry, Calculus, Differential Equations, Probability & Statistics, and Engineering Economics.",
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
    description: "Engineering Laws, Ethics, and Contracts; Architecture Professional Practice; Building Laws; and BP 334.",
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
