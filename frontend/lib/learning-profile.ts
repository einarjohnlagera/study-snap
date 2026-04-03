import type { LearnerLevel } from "@/lib/api";

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
  "Nursing",
  "MedTech",
  "Engineering",
  "Education",
  "Criminology",
  "Accountancy",
  "Psychology",
  "Pharmacy",
  "Computer Science",
  "Information Technology",
  "Software Engineering",
  "Data Science",
  "Programming",
  "Business",
];

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
