import type { ProfileType } from "@/lib/api";
import { resolvePlanPremiumExamMode, type PlanPremiumExamMode } from "@/lib/exam-mode-visibility";

export type CollectionLabels = {
  singular: string;
  plural: string;
  goalSingular: string;
  subjectSingular: string;
  sectionSingular: string;
  primarySingular: string;
  companionSingular: string;
  navLabel: string;
  newCtaLabel: string;
  emptyTitle: string;
  emptyBody: string;
  listDescription: string;
};

export type CollectionTerminalAction =
  | {
    kind: "exam-builder";
    label: string;
  }
  | {
    kind: "premium-exam";
    mode: PlanPremiumExamMode;
    label: string;
  };

const DEFAULT_LABELS: CollectionLabels = {
  singular: "Collection",
  plural: "Collections",
  goalSingular: "Goal",
  subjectSingular: "Collection",
  sectionSingular: "Section",
  primarySingular: "Primary Collection",
  companionSingular: "Companion",
  navLabel: "Collections",
  newCtaLabel: "New Collection",
  emptyTitle: "No collections yet",
  emptyBody: "Group related notes into an ordered set you can return to later.",
  listDescription: "Group related notes into ordered sets you can revisit, edit, and study from.",
};

const LABELS_BY_PROFILE: Partial<Record<ProfileType, CollectionLabels>> = {
  TEACHER: {
    singular: "Lesson Plan",
    plural: "Lesson Plans",
    goalSingular: "Course",
    subjectSingular: "Unit",
    sectionSingular: "Part",
    primarySingular: "Primary Lesson Plan",
    companionSingular: "Companion",
    navLabel: "Lesson Plans",
    newCtaLabel: "New Lesson Plan",
    emptyTitle: "No lesson plans yet",
    emptyBody: "Group notes, handouts, and reviewers into an ordered lesson plan you can reuse.",
    listDescription: "Organize related teaching notes into reusable lesson plans.",
  },
  STUDENT: {
    singular: "Study Plan",
    plural: "Study Plans",
    goalSingular: "Goal",
    subjectSingular: "Subject Plan",
    sectionSingular: "Section",
    primarySingular: "Primary Study Plan",
    companionSingular: "Companion",
    navLabel: "Study Plans",
    newCtaLabel: "New Study Plan",
    emptyTitle: "No study plans yet",
    emptyBody: "Group notes for a unit, exam, or weekly review into one ordered study plan.",
    listDescription: "Organize related notes into study plans you can revisit and reorder.",
  },
  BOARD_EXAM: {
    singular: "Review Set",
    plural: "Review Sets",
    goalSingular: "Goal",
    subjectSingular: "Subject Plan",
    sectionSingular: "Section",
    primarySingular: "Primary Review Set",
    companionSingular: "Companion",
    navLabel: "Review Sets",
    newCtaLabel: "New Review Set",
    emptyTitle: "No review sets yet",
    emptyBody: "Group reviewers and practice notes into ordered sets for focused board exam prep.",
    listDescription: "Organize board exam material into focused review sets.",
  },
  PROFESSIONAL: DEFAULT_LABELS,
};

const PREMIUM_EXAM_LABELS: Record<PlanPremiumExamMode, string> = {
  long_exam: "Take the Long Exam",
  board_exam: "Take the Board Exam",
  interview: "Start Interview Practice",
};

export function getCollectionLabels(profileType: ProfileType | null | undefined): CollectionLabels {
  return LABELS_BY_PROFILE[profileType ?? "PROFESSIONAL"] ?? DEFAULT_LABELS;
}

export function getCollectionTerminalAction(profileType: ProfileType | null | undefined): CollectionTerminalAction | null {
  if (profileType === "TEACHER") {
    return {
      kind: "exam-builder",
      label: "Build Exam",
    };
  }

  const premiumMode = resolvePlanPremiumExamMode(profileType);
  if (!premiumMode) {
    return null;
  }

  return {
    kind: "premium-exam",
    mode: premiumMode,
    label: PREMIUM_EXAM_LABELS[premiumMode],
  };
}
