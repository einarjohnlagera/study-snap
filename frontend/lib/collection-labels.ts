import type { ProfileType } from "@/lib/api";

export type CollectionLabels = {
  singular: string;
  plural: string;
  navLabel: string;
  newCtaLabel: string;
  emptyTitle: string;
  emptyBody: string;
  listDescription: string;
};

const DEFAULT_LABELS: CollectionLabels = {
  singular: "Collection",
  plural: "Collections",
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
    navLabel: "Lesson Plans",
    newCtaLabel: "New Lesson Plan",
    emptyTitle: "No lesson plans yet",
    emptyBody: "Group notes, handouts, and reviewers into an ordered lesson plan you can reuse.",
    listDescription: "Organize related teaching notes into reusable lesson plans.",
  },
  STUDENT: {
    singular: "Study Plan",
    plural: "Study Plans",
    navLabel: "Study Plans",
    newCtaLabel: "New Study Plan",
    emptyTitle: "No study plans yet",
    emptyBody: "Group notes for a unit, exam, or weekly review into one ordered study plan.",
    listDescription: "Organize related notes into study plans you can revisit and reorder.",
  },
  BOARD_EXAM: {
    singular: "Review Set",
    plural: "Review Sets",
    navLabel: "Review Sets",
    newCtaLabel: "New Review Set",
    emptyTitle: "No review sets yet",
    emptyBody: "Group reviewers and practice notes into ordered sets for focused board exam prep.",
    listDescription: "Organize board exam material into focused review sets.",
  },
  PROFESSIONAL: DEFAULT_LABELS,
};

export function getCollectionLabels(profileType: ProfileType | null | undefined): CollectionLabels {
  return LABELS_BY_PROFILE[profileType ?? "PROFESSIONAL"] ?? DEFAULT_LABELS;
}
