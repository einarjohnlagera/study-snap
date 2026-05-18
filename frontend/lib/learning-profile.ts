import type { LearnerLevel, ProfileType } from "@/lib/api";

export const LEARNER_LEVEL_OPTIONS: Array<{ value: LearnerLevel; label: string }> = [
  { value: "GRADE_SCHOOL", label: "Grade School" },
  { value: "JUNIOR_HIGH", label: "Junior High" },
  { value: "SENIOR_HIGH", label: "Senior High" },
  { value: "COLLEGE", label: "College" },
  { value: "BOARD_EXAM_REVIEW", label: "Board Exam Review" },
  { value: "PROFESSIONAL", label: "Professional" },
  { value: "PERSONAL_LEARNING", label: "Personal Learning" },
];

export const COURSE_PROGRAM_SUGGESTIONS = [
  "Grade School",
  "High School",
  "Senior High – STEM",
  "Senior High – ABM",
  "Senior High – HUMSS",
  "Senior High – GAS",
  "Senior High – TVL",
  "Nursing",
  "Civil Engineering",
  "Mechanical Engineering",
  "Electrical Engineering",
  "Accountancy",
  "Business Administration",
  "Information Technology",
  "Computer Science",
  "Software Engineering",
  "Education",
  "Psychology",
  "Biology",
  "Chemistry",
  "Physics",
  "Law",
  "Medicine",
  "Architecture",
  "Criminology",
  "Tourism",
  "Hospitality Management",
  "Self Study / Personal Learning",
  "Professional / Board Exam Review",
  "AWS Certification",
  "PMP Certification",
];

const COURSE_PROGRAM_HELPER_BY_LEVEL: Record<LearnerLevel, string> = {
  GRADE_SCHOOL: "Enter something like General Education, Elementary Math, Reading, etc.",
  JUNIOR_HIGH: "Enter something like General Education, Math, Science, English, etc.",
  SENIOR_HIGH: "Enter your strand like STEM, ABM, HUMSS, GAS, etc.",
  COLLEGE: "Enter your degree like Engineering, Nursing, Accountancy, etc.",
  BOARD_EXAM_REVIEW: "Enter the program or board exam track like Nursing, Pharmacy, Civil Engineering, etc.",
  PROFESSIONAL: "Enter your field like Law, Medicine, IT, Education, etc.",
  PERSONAL_LEARNING: "Enter the topic you're focusing on like Programming, Finance, History, etc.",
};

export type CourseProgramFieldContext = "profile" | "note" | "onboarding";

function courseProgramReadabilityScore(value: string): number {
  const words = value.split(/[\s/–-]+/).filter((word) => word.length > 0);
  const titleCaseWords = words.filter((word) => /^[A-Z]/.test(word)).length;
  const hasUppercase = /[A-Z]/.test(value) ? 1 : 0;
  return (titleCaseWords * 10) + hasUppercase;
}

function preferReadableCourseProgram(existing: string, candidate: string): string {
  const existingScore = courseProgramReadabilityScore(existing);
  const candidateScore = courseProgramReadabilityScore(candidate);
  if (candidateScore !== existingScore) {
    return candidateScore > existingScore ? candidate : existing;
  }
  return existing.length <= candidate.length ? existing : candidate;
}

export function normalizeCourseProgram(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  if (!trimmed) {
    return null;
  }
  const normalized = trimmed.replaceAll(/\s*[-–—]\s*/g, " – ").replaceAll(/\s+/g, " ").trim();
  return normalized.length > 0 ? normalized : null;
}

export function mergeCourseProgramSuggestions(...sources: Array<Array<string | null | undefined> | null | undefined>): string[] {
  const normalized = new Map<string, string>();

  for (const source of sources) {
    if (!source) {
      continue;
    }
    for (const value of source) {
      const normalizedValue = normalizeCourseProgram(value);
      if (!normalizedValue) {
        continue;
      }
      const lookup = normalizedValue.toLowerCase();
      normalized.set(lookup, normalized.has(lookup)
        ? preferReadableCourseProgram(normalized.get(lookup) ?? normalizedValue, normalizedValue)
        : normalizedValue);
    }
  }

  return Array.from(normalized.values()).sort((left, right) => {
    const caseInsensitive = left.localeCompare(right, undefined, { sensitivity: "accent" });
    return caseInsensitive !== 0 ? caseInsensitive : left.localeCompare(right);
  });
}

export function formatLearnerLevel(learnerLevel: LearnerLevel | string | null | undefined): string | null {
  if (!learnerLevel) {
    return null;
  }
  const match = LEARNER_LEVEL_OPTIONS.find((option) => option.value === learnerLevel);
  if (match) {
    return match.label;
  }
  return learnerLevel.replaceAll("_", " ");
}

export type GroupedLearnerLevels = {
  recommended: Array<{ value: LearnerLevel; label: string }>;
  other: Array<{ value: LearnerLevel; label: string }>;
  recommendedGroupLabel: string;
};

const LEARNER_LEVEL_SET: Record<string, { value: LearnerLevel; label: string }> = Object.fromEntries(
  LEARNER_LEVEL_OPTIONS.map((opt) => [opt.value, opt]),
);

function pick(...values: LearnerLevel[]): Array<{ value: LearnerLevel; label: string }> {
  return values.map((v) => LEARNER_LEVEL_SET[v]).filter(Boolean);
}

export function getGroupedLearnerLevels(
  profileType: ProfileType | null | undefined,
): GroupedLearnerLevels {
  if (profileType === "BOARD_EXAM") {
    const recommended = pick("BOARD_EXAM_REVIEW");
    const other = LEARNER_LEVEL_OPTIONS.filter((opt) => !recommended.some((r) => r.value === opt.value));
    return { recommended, other, recommendedGroupLabel: "Recommended for Exam Reviewers" };
  }

  if (profileType === "TEACHER") {
    const recommended = pick("PERSONAL_LEARNING", "COLLEGE");
    const other = LEARNER_LEVEL_OPTIONS.filter((opt) => !recommended.some((r) => r.value === opt.value));
    return { recommended, other, recommendedGroupLabel: "Recommended for Teachers" };
  }

  if (profileType === "PROFESSIONAL") {
    const recommended = pick("PROFESSIONAL", "PERSONAL_LEARNING");
    const other = LEARNER_LEVEL_OPTIONS.filter((opt) => !recommended.some((r) => r.value === opt.value));
    return { recommended, other, recommendedGroupLabel: "Recommended for Professionals" };
  }

  // STUDENT and fallback
  const recommended = pick("GRADE_SCHOOL", "JUNIOR_HIGH", "SENIOR_HIGH", "COLLEGE");
  const other = LEARNER_LEVEL_OPTIONS.filter((opt) => !recommended.some((r) => r.value === opt.value));
  return { recommended, other, recommendedGroupLabel: "Recommended for Students" };
}

export function getCourseProgramHelperText(
  learnerLevel: LearnerLevel | "" | null | undefined,
  context: CourseProgramFieldContext = "profile",
): string {
  const baseHelperText = learnerLevel
    ? COURSE_PROGRAM_HELPER_BY_LEVEL[learnerLevel]
    : "Choose or type the course, strand, field, or topic that fits best.";

  if (context === "note") {
    return `${baseHelperText} This note can use a different value from your profile.`;
  }

  return baseHelperText;
}
