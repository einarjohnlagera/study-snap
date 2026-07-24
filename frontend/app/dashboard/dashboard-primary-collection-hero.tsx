import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Card } from "@/components/ui/card";
import { buttonVariants } from "@/components/ui/button";
import { getCollectionLabels } from "@/lib/collection-labels";
import type { GoalCollectionDetailResponse, ProfileType } from "@/lib/api";

type CurrentStep = {
  title: string;
  description: string;
};

function formatConceptCount(count: number): string {
  return `${count} ${count === 1 ? "concept" : "concepts"}`;
}

function resolveCurrentStep(goal: GoalCollectionDetailResponse, subjectLabel: string): CurrentStep {
  if (goal.dueConcepts > 0) {
    return {
      title: "Review due concepts",
      description: `${formatConceptCount(goal.dueConcepts)} are ready for another review pass.`,
    };
  }

  const firstUnpracticedSubject = goal.children.find((child) => child.notPracticedConcepts > 0);
  if (firstUnpracticedSubject) {
    return {
      title: `Start ${firstUnpracticedSubject.title}`,
      description: `Build momentum with the next ${subjectLabel.toLowerCase()} in this plan.`,
    };
  }

  if (goal.notPracticedConcepts > 0) {
    return {
      title: "Start your next review",
      description: `${formatConceptCount(goal.notPracticedConcepts)} have not been practiced yet.`,
    };
  }

  if (goal.totalConcepts > 0 && goal.masteredConcepts === goal.totalConcepts) {
    return {
      title: "Keep your review steady",
      description: "You are ready—return to the plan to reinforce what you know.",
    };
  }

  return {
    title: "Choose your next note",
    description: "Open this plan to continue your review journey.",
  };
}

export function DashboardPrimaryCollectionHero({
  goal,
  profileType,
}: Readonly<{
  goal: GoalCollectionDetailResponse;
  profileType: ProfileType | null | undefined;
}>) {
  const labels = getCollectionLabels(profileType);
  const currentStep = resolveCurrentStep(goal, labels.subjectSingular);
  const collectionHref = `/collections/${goal.collectionId}`;

  return (
    <section aria-labelledby="dashboard-primary-collection-title">
      <Card className="space-y-5 border-l-4 border-l-indigo-500 bg-linear-to-br from-indigo-500/[0.08] via-background to-emerald-500/[0.08] p-5 dark:border-l-indigo-400 sm:p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0 space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-indigo-700 dark:text-indigo-300">
              {labels.primarySingular}
            </p>
            <Link href={collectionHref} className="block w-fit max-w-full rounded-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2">
              <h2 id="dashboard-primary-collection-title" className="text-xl font-semibold tracking-tight hover:underline sm:text-2xl">
                {goal.title}
              </h2>
            </Link>
            <p className="text-sm text-foreground/70">
              {goal.courseProgram ?? `${goal.childCount} ${labels.subjectSingular.toLowerCase()}${goal.childCount === 1 ? "" : "s"}`}
            </p>
          </div>
          <div className="shrink-0 text-left sm:text-right">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Readiness</p>
            <p className="text-4xl font-semibold tracking-tight text-foreground">{goal.overallReadinessPercentage}%</p>
            <p className="text-xs text-foreground/60">ready</p>
          </div>
        </div>

        <div className="flex flex-col gap-4 rounded-xl border border-indigo-500/15 bg-background/70 p-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Current step</p>
            <h3 className="text-base font-semibold text-foreground">{currentStep.title}</h3>
            <p className="text-sm text-foreground/70">{currentStep.description}</p>
          </div>
          <Link href={collectionHref} className={buttonVariants({ size: "sm" })}>
            Continue Studying
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
      </Card>
    </section>
  );
}

export function DashboardPrimaryCollectionHeroSkeleton() {
  return (
    <Card
      aria-label="Loading primary review set"
      className="space-y-5 border-l-4 border-l-indigo-500 bg-linear-to-br from-indigo-500/[0.08] via-background to-emerald-500/[0.08] p-5 dark:border-l-indigo-400 sm:p-6"
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-3">
          <div className="h-3 w-32 animate-pulse rounded bg-foreground/10" />
          <div className="h-6 w-64 max-w-full animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-40 animate-pulse rounded bg-foreground/10" />
        </div>
        <div className="h-14 w-16 animate-pulse rounded bg-foreground/10" />
      </div>
      <div className="h-24 animate-pulse rounded-xl bg-foreground/10" />
    </Card>
  );
}
