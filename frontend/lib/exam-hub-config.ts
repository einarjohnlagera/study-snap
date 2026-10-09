import { Calculator, GraduationCap, HardHat, Heart, PenTool } from "lucide-react";
import type { LucideIcon } from "lucide-react";

export type ExamHubSlug = "ale" | "pnle" | "let" | "cpale" | "ce";

export type ExamHubConfig = {
  slug: ExamHubSlug;
  shortName: string;
  fullName: string;
  description: string;
  coursePrograms: readonly string[];
};

export const EXAM_HUBS = {
  ale: {
    slug: "ale",
    shortName: "ALE",
    fullName: "Architect Licensure Examination (ALE)",
    description: "Free ALE reviewer notes and practice quizzes for Philippine architecture board exam takers",
    coursePrograms: ["Architecture"],
  },
  pnle: {
    slug: "pnle",
    shortName: "PNLE",
    fullName: "Philippine Nurse Licensure Examination (PNLE)",
    description: "Free PNLE reviewer notes and practice quizzes for Philippine nursing board exam takers",
    coursePrograms: ["Nursing"],
  },
  let: {
    slug: "let",
    shortName: "LET",
    fullName: "Licensure Examination for Teachers (LET)",
    description: "Free LET reviewer notes and practice quizzes for Philippine teacher licensure exam takers",
    // ⚠️ FALLBACK ONLY — mirrors every course_program carrying exam_goal_slug = 'let'. V142
    // (v0.133.0) took that from ONE row to EIGHT; keep this in step with ExamGoalConfig.java.
    coursePrograms: [
      "Education",
      "Special Needs Education",
      "Elementary Education",
      "Secondary Education",
      "Early Childhood Education",
      "Technical-Vocational Teacher Education",
      "Physical Education",
      "Teacher Certification",
    ],
  },
  cpale: {
    slug: "cpale",
    shortName: "CPALE",
    fullName: "Certified Public Accountant Licensure Examination (CPALE)",
    description: "Free CPALE reviewer notes and practice quizzes for Philippine accountancy board exam takers",
    coursePrograms: ["Accountancy"],
  },
  ce: {
    slug: "ce",
    shortName: "CELE",
    fullName: "Civil Engineering Licensure Examination (CELE)",
    description: "Free CELE reviewer notes and practice quizzes for Philippine civil engineering board exam takers",
    coursePrograms: ["Civil Engineering"],
  },
} as const satisfies Record<ExamHubSlug, ExamHubConfig>;

export const EXAM_HUB_SLUGS = Object.keys(EXAM_HUBS) as ExamHubSlug[];

export const EXAM_HUB_ICONS: Record<ExamHubSlug, LucideIcon> = {
  ale: PenTool,
  pnle: Heart,
  let: GraduationCap,
  cpale: Calculator,
  ce: HardHat,
};

export function getExamHubConfig(slug: string): ExamHubConfig | null {
  return Object.prototype.hasOwnProperty.call(EXAM_HUBS, slug)
    ? EXAM_HUBS[slug as ExamHubSlug]
    : null;
}

export function getExamSlugForCourseProgram(courseProgram: string | null | undefined): ExamHubSlug | null {
  const normalizedCourseProgram = courseProgram?.trim().toLowerCase();
  if (!normalizedCourseProgram) {
    return null;
  }

  return EXAM_HUB_SLUGS.find((slug) => (
    EXAM_HUBS[slug].coursePrograms.some((value) => value.trim().toLowerCase() === normalizedCourseProgram)
  )) ?? null;
}
