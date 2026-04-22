import type { ExamBalanceSectionIntent, ExamBuilderSection } from "../exam-builder-order";
import { buildWholeNoteEntries, createExamSectionId } from "../exam-builder-order";

export type ExamTemplateId =
  | "SCRATCH"
  | "PRELIM"
  | "MIDTERM"
  | "FINAL"
  | "QUARTERLY"
  | "TRITERM";

export type ExamTemplateDefinition = {
  id: ExamTemplateId;
  title: string;
  description: string;
  sectionTitles: string[];
  sectionIntents: ExamBalanceSectionIntent[];
};

export const EXAM_TEMPLATES: ExamTemplateDefinition[] = [
  {
    id: "SCRATCH",
    title: "Start from Scratch",
    description: "Begin with one flexible section and build the rest your way.",
    sectionTitles: ["Section A"],
    sectionIntents: ["FLEXIBLE"],
  },
  {
    id: "PRELIM",
    title: "Prelim Exam",
    description: "Basic Concepts -> Problem Solving -> Application",
    sectionTitles: [
      "Section A - Basic Concepts",
      "Section B - Problem Solving",
      "Section C - Application",
    ],
    sectionIntents: ["FOUNDATIONAL", "PROBLEM_SOLVING", "APPLICATION"],
  },
  {
    id: "MIDTERM",
    title: "Midterm Exam",
    description: "Fundamentals Review -> Intermediate Problems -> Mixed Application",
    sectionTitles: [
      "Section A - Fundamentals Review",
      "Section B - Intermediate Problems",
      "Section C - Mixed Application",
    ],
    sectionIntents: ["REVIEW", "PROBLEM_SOLVING", "APPLICATION"],
  },
  {
    id: "FINAL",
    title: "Final Exam",
    description: "Comprehensive Review -> Advanced Problems -> Case-Based Questions",
    sectionTitles: [
      "Section A - Comprehensive Review",
      "Section B - Advanced Problems",
      "Section C - Case-Based Questions",
    ],
    sectionIntents: ["REVIEW", "ADVANCED", "CASE_BASED"],
  },
  {
    id: "QUARTERLY",
    title: "Quarterly Exam",
    description: "Knowledge Check -> Understanding -> Application -> Critical Thinking",
    sectionTitles: [
      "Section A - Knowledge Check",
      "Section B - Understanding",
      "Section C - Application",
      "Section D - Critical Thinking",
    ],
    sectionIntents: ["FOUNDATIONAL", "UNDERSTANDING", "APPLICATION", "CRITICAL_THINKING"],
  },
  {
    id: "TRITERM",
    title: "Triterm Exam",
    description: "Core Concepts -> Application -> Integration",
    sectionTitles: [
      "Section A - Core Concepts",
      "Section B - Application",
      "Section C - Integration",
    ],
    sectionIntents: ["FOUNDATIONAL", "APPLICATION", "INTEGRATION"],
  },
];

export function getExamTemplateById(templateId: ExamTemplateId): ExamTemplateDefinition | null {
  return EXAM_TEMPLATES.find((template) => template.id === templateId) ?? null;
}

export function buildTemplateSections(
  template: ExamTemplateDefinition,
  selectedNoteIds: string[],
  questionCountsByNoteId: Record<string, number>,
): ExamBuilderSection[] {
  return template.sectionTitles.map((title, index) => ({
    id: createExamSectionId(),
    title,
    entries: index === 0 ? buildWholeNoteEntries(selectedNoteIds, questionCountsByNoteId) : [],
  }));
}
