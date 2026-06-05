export type ExamHubSlug = "ale" | "pnle" | "let";

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
    description: "Notes and practice quizzes for Philippine architecture board exam reviewers",
    coursePrograms: ["Architecture"],
  },
  pnle: {
    slug: "pnle",
    shortName: "PNLE",
    fullName: "Philippine Nurse Licensure Examination (PNLE)",
    description: "Notes and practice quizzes for nursing board exam reviewers",
    coursePrograms: ["Nursing", "Medical – Surgical Nursing"],
  },
  let: {
    slug: "let",
    shortName: "LET",
    fullName: "Licensure Examination for Teachers (LET)",
    description: "Notes and practice quizzes for Philippine teacher licensure exam reviewers",
    coursePrograms: ["Education"],
  },
} as const satisfies Record<ExamHubSlug, ExamHubConfig>;

export const EXAM_HUB_SLUGS = Object.keys(EXAM_HUBS) as ExamHubSlug[];

export function getExamHubConfig(slug: string): ExamHubConfig | null {
  return Object.prototype.hasOwnProperty.call(EXAM_HUBS, slug)
    ? EXAM_HUBS[slug as ExamHubSlug]
    : null;
}
