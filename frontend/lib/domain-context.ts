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
