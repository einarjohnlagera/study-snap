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
    description: "Strength of Materials, Engineering Mechanics, Hydraulics or Fluid Mechanics, Thermodynamics, and Engineering Materials.",
  },
  {
    value: "CIVIL_ENGINEERING",
    label: "Civil Engineering",
    description: "Structural Analysis & Design, Steel or RC Design, Geotechnical, Foundation, Soil Mechanics, Surveying, Transportation, Hydrology, Water Resources, and Construction Management.",
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
