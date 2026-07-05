import Link from "next/link";
import { Card } from "@/components/ui/card";
import { ResponsiveActionLink } from "@/components/ui/action-button";
import { getCollectionLabels } from "@/lib/collection-labels";

export type FirstRunProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PROFESSIONAL";

type FirstRunContent = {
  headline: string;
  intro: string;
  steps: string[];
};

const TEACHER_COLLECTION = getCollectionLabels("TEACHER").singular;
const BOARD_COLLECTION = getCollectionLabels("BOARD_EXAM").singular;

const FIRST_RUN_CONTENT: Record<FirstRunProfileType, FirstRunContent> = {
  TEACHER: {
    headline: "Build your first lesson set",
    intro: "Bring your existing teaching material into NoteLib and turn it into ready-to-share quizzes.",
    steps: [
      "Import your lecture notes, handouts, or reviewers",
      "Generate a quiz from each note",
      `Group them into a ${TEACHER_COLLECTION} to export and share`,
    ],
  },
  STUDENT: {
    headline: "Start studying smarter",
    intro: "Bring your notes into NoteLib and turn them into Study Packs and quizzes in minutes.",
    steps: [
      "Import or create your notes",
      "Generate a Study Pack",
      "Quiz yourself and track weak topics",
    ],
  },
  BOARD_EXAM: {
    headline: "Start your exam prep",
    intro: "Bring your reviewers into NoteLib and drill toward exam day.",
    steps: [
      "Import your reviewers and chapter notes",
      "Generate Study Packs",
      `Practice across a ${BOARD_COLLECTION} with quizzes`,
    ],
  },
  PROFESSIONAL: {
    headline: "Start your certification review",
    intro: "Bring your study materials into NoteLib and turn them into scenario-based practice.",
    steps: [
      "Import or create your materials",
      "Generate a Study Pack",
      "Run scenario-based practice quizzes",
    ],
  },
};

const PLAN_ADOPTION_PROFILE_TYPES: ReadonlySet<FirstRunProfileType> = new Set(["STUDENT", "BOARD_EXAM", "PROFESSIONAL"]);

export function DashboardEmpty({ profileType }: Readonly<{ profileType: FirstRunProfileType }>) {
  const content = FIRST_RUN_CONTENT[profileType];
  const labels = getCollectionLabels(profileType);
  const showPlanAdoptionLink = PLAN_ADOPTION_PROFILE_TYPES.has(profileType);

  return (
    <Card className="space-y-5 p-4 sm:p-6">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold sm:text-xl">{content.headline}</h2>
        <p className="max-w-2xl text-sm text-foreground/75">{content.intro}</p>
      </div>

      <ol className="space-y-2">
        {content.steps.map((step, index) => (
          <li key={step} className="flex items-start gap-3 text-sm text-foreground/80">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-600/10 text-xs font-semibold text-blue-700 dark:bg-blue-400/10 dark:text-blue-300">
              {index + 1}
            </span>
            <span className="pt-0.5">{step}</span>
          </li>
        ))}
      </ol>

      <div className="flex flex-col gap-2 sm:flex-row">
        <ResponsiveActionLink href="/notes/import" action="import" label="Import files" className="w-full sm:w-auto" />
        <ResponsiveActionLink href="/notes/new" action="create" label="Create a note" variant="outline" className="w-full sm:w-auto" />
      </div>

      {showPlanAdoptionLink ? (
        <Link
          href="/collections/published"
          className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
        >
          Or start from a ready-made {labels.singular.toLowerCase()} instead
        </Link>
      ) : null}
    </Card>
  );
}
