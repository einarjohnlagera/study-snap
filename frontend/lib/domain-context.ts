import type { DomainContext } from "@/lib/api";

export const DOMAIN_CONTEXT_OPTIONS: Array<{ value: DomainContext; label: string }> = [
  { value: "ENGINEERING_MATHEMATICS", label: "Engineering Mathematics" },
  { value: "ENGINEERING_SCIENCES", label: "Engineering Sciences" },
  { value: "CIVIL_ENGINEERING", label: "Civil Engineering" },
  { value: "PROFESSIONAL_PRACTICE_AND_REGULATION", label: "Professional Practice & Regulation" },
  { value: "GENERAL_EDUCATION", label: "General Education" },
  { value: "PROFESSIONAL_EDUCATION", label: "Professional Education" },
  { value: "NURSING", label: "Nursing" },
  { value: "ACCOUNTANCY", label: "Accountancy" },
];

export function getDomainContextLabel(value: DomainContext): string {
  return DOMAIN_CONTEXT_OPTIONS.find((option) => option.value === value)?.label ?? value;
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
