"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Card } from "@/components/ui/card";
import { BackLink } from "@/components/ui/back-link";
import { PageHeader } from "@/components/page-header";
import { getProgressReport, type GoalSummaryResponse, type ProgressReportResponse, type SubjectProgressEntry } from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { getExamSlugForCourseProgram } from "@/lib/exam-hub-config";

type LoadState = "loading" | "ready" | "error";

const MASTERY_QUARTER_THRESHOLD = 25;
const MASTERY_HALF_THRESHOLD = 50;
const MASTERY_STRONG_THRESHOLD = 70;
const MASTERY_COMPLETE_THRESHOLD = 100;

type GoalMilestone = {
  label: string;
  reached: (goalSummary: GoalSummaryResponse) => boolean;
};

export const MILESTONES: GoalMilestone[] = [
  {
    label: "First concept mastered",
    reached: (goalSummary) => goalSummary.masteredConcepts >= 1,
  },
  {
    label: "25% mastered",
    reached: (goalSummary) => goalSummary.masteryPercentage >= MASTERY_QUARTER_THRESHOLD,
  },
  {
    label: "All concepts reviewed",
    reached: (goalSummary) => (goalSummary.notPracticedConcepts ?? 1) === 0 && goalSummary.totalConcepts > 0,
  },
  {
    label: "50% mastered",
    reached: (goalSummary) => goalSummary.masteryPercentage >= MASTERY_HALF_THRESHOLD,
  },
  {
    label: "70% mastered",
    reached: (goalSummary) => goalSummary.masteryPercentage >= MASTERY_STRONG_THRESHOLD,
  },
  {
    label: "All concepts mastered",
    reached: (goalSummary) => goalSummary.masteryPercentage >= MASTERY_COMPLETE_THRESHOLD,
  },
];

function isNotStarted(entry: SubjectProgressEntry): boolean {
  return entry.masteryPercentage === 0 && entry.notPracticedConcepts === entry.totalConcepts;
}

function getProgressBarTone(entry: SubjectProgressEntry): string {
  if (isNotStarted(entry)) return "bg-foreground/20";
  if (entry.masteryPercentage < 40) return "bg-rose-500";
  if (entry.masteryPercentage < 60) return "bg-amber-500";
  return "bg-blue-600 dark:bg-blue-400";
}

function getCardAccentBorder(entry: SubjectProgressEntry): string {
  if (isNotStarted(entry)) return "border-l-4 border-l-foreground/20";
  if (entry.masteryPercentage < 40) return "border-l-4 border-l-rose-500";
  if (entry.masteryPercentage < 60) return "border-l-4 border-l-amber-500";
  return "border-l-4 border-l-blue-500 dark:border-l-blue-400";
}

function getMasteryTextColor(entry: SubjectProgressEntry): string {
  if (isNotStarted(entry)) return "text-foreground/50";
  if (entry.masteryPercentage < 40) return "text-rose-600 dark:text-rose-400";
  if (entry.masteryPercentage < 60) return "text-amber-600 dark:text-amber-400";
  return "text-blue-600 dark:text-blue-400";
}

function ProgressHeader() {
  return (
    <div className="space-y-4">
      <BackLink href="/dashboard" label="Dashboard" />
      <PageHeader
        eyebrow="MY PROGRESS"
        title="My Progress"
        description="Concept mastery across your subjects, based on your recent practice."
      />
    </div>
  );
}

function SubjectProgressCard({ entry }: Readonly<{ entry: SubjectProgressEntry }>) {
  const notStarted = isNotStarted(entry);
  const fillWidth = notStarted ? 0 : entry.masteryPercentage;

  return (
    <Card className={`space-y-4 p-4 sm:p-6 ${getCardAccentBorder(entry)}`}>
      <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold text-foreground">{entry.subject}</h2>
          <p className="text-sm text-foreground/60">{entry.totalConcepts} concepts tracked</p>
        </div>
        <p className={`text-2xl font-semibold ${getMasteryTextColor(entry)}`}>{entry.masteryPercentage}%</p>
      </div>

      <div className="space-y-2">
        <div
          role="progressbar"
          aria-label={`${entry.subject} mastery`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={entry.masteryPercentage}
          data-state={notStarted ? "not-started" : "in-progress"}
          className="h-3 overflow-hidden rounded-full bg-muted"
        >
          <div
            className={`h-full rounded-full transition-all ${getProgressBarTone(entry)}`}
            style={{ width: `${fillWidth}%` }}
          />
        </div>
        <p className="text-sm text-foreground/70">
          {entry.masteredConcepts} mastered &middot; {entry.dueConcepts} due for review &middot; {entry.notPracticedConcepts} not started
        </p>
      </div>
    </Card>
  );
}

function GoalSummaryHeader({ goalSummary }: Readonly<{ goalSummary: GoalSummaryResponse }>) {
  const eyebrow = goalSummary.goalType === "SUBJECT_FOCUS" ? "FOCUS" : goalSummary.goalName;

  return (
    <Card className="overflow-hidden border-blue-500/25 bg-linear-to-br from-blue-500/10 via-background to-emerald-500/10 p-4 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            {eyebrow} Goal
          </p>
          <div className="space-y-1">
            <h2 className="text-xl font-semibold tracking-tight sm:text-2xl">{goalSummary.goalLabel}</h2>
            <p className="text-sm text-foreground/70">
              {goalSummary.masteredConcepts} of {goalSummary.totalConcepts} goal concepts mastered
            </p>
          </div>
        </div>
        <div className="flex flex-col items-start gap-2 sm:items-end">
          <div className="text-left sm:text-right">
            <p className="text-4xl font-semibold tracking-tight text-foreground">{goalSummary.masteryPercentage}%</p>
            <p className="text-xs font-medium uppercase tracking-wide text-foreground/55">mastered</p>
          </div>
          <Link
            href="/profile#study-focus"
            className="text-xs font-medium text-blue-700 hover:underline dark:text-blue-300"
          >
            Change goal
          </Link>
        </div>
      </div>
    </Card>
  );
}

function getMilestoneMarkerClasses(reached: boolean, isNext: boolean): string {
  if (reached) {
    return "border-blue-600 bg-blue-600";
  }
  if (isNext) {
    return "border-blue-600 bg-background ring-2 ring-blue-500 ring-offset-2 ring-offset-background";
  }
  return "border-muted bg-muted";
}

function GoalMilestonesCard({ goalSummary }: Readonly<{ goalSummary: GoalSummaryResponse }>) {
  const milestoneStates = MILESTONES.map((milestone) => ({
    ...milestone,
    reached: milestone.reached(goalSummary),
  }));
  const nextMilestoneIndex = milestoneStates.findIndex((milestone) => !milestone.reached);
  const reachedCount = milestoneStates.filter((milestone) => milestone.reached).length;
  const progressWidth = (reachedCount / MILESTONES.length) * 100;

  return (
    <Card className="space-y-5 p-4 sm:p-6">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold tracking-tight">Goal Milestones</h2>
        <p className="text-sm text-foreground/65">
          Track the next checkpoint on your path to goal mastery.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {milestoneStates.map((milestone, index) => {
          const isNext = index === nextMilestoneIndex;
          return (
            <div key={milestone.label} className="flex items-center gap-3">
              <span
                aria-hidden="true"
                className={`size-3.5 shrink-0 rounded-full border ${getMilestoneMarkerClasses(milestone.reached, isNext)}`}
              />
              <span className={`text-sm ${milestone.reached || isNext ? "text-foreground" : "text-foreground/50"}`}>
                {milestone.label}
              </span>
            </div>
          );
        })}
      </div>

      <div
        role="progressbar"
        aria-label="Goal milestone progress"
        aria-valuemin={0}
        aria-valuemax={MILESTONES.length}
        aria-valuenow={reachedCount}
        className="h-3 overflow-hidden rounded-full bg-muted"
      >
        <div
          className="h-full rounded-full bg-blue-600 transition-all dark:bg-blue-400"
          style={{ width: `${progressWidth}%` }}
        />
      </div>
    </Card>
  );
}

function NextStudyCard({ goalSummary }: Readonly<{ goalSummary: GoalSummaryResponse }>) {
  if (goalSummary.goalType === "SUBJECT_FOCUS") {
    const weakestGoalSubject = goalSummary.weakestGoalSubject;
    const href = weakestGoalSubject
      ? `/public/library?subject=${encodeURIComponent(weakestGoalSubject)}`
      : "/public/library";
    const message = weakestGoalSubject
      ? `Focus on ${weakestGoalSubject} — you have concepts left to practice.`
      : "Browse community notes to build your knowledge.";
    const linkLabel = weakestGoalSubject
      ? `Browse ${weakestGoalSubject} notes in the community`
      : "Browse notes in the community";

    return (
      <Card className="space-y-3 p-4 sm:p-6">
        <div className="space-y-1">
          <h2 className="text-lg font-semibold tracking-tight">What to study next</h2>
          <p className="text-sm leading-relaxed text-foreground/75">{message}</p>
        </div>
        <Link href={href} className="inline-flex text-sm font-medium text-blue-700 hover:underline dark:text-blue-300">
          {linkLabel} &rarr;
        </Link>
      </Card>
    );
  }

  const examSlug = goalSummary.goalType === "EXAM"
    ? goalSummary.studyGoal
    : getExamSlugForCourseProgram(goalSummary.studyGoal);
  const href = examSlug
    ? `/exam/${examSlug}`
    : `/public/library?courseProgram=${encodeURIComponent(goalSummary.studyGoal)}`;
  const message = goalSummary.weakestGoalSubject
    ? `Focus on ${goalSummary.weakestGoalSubject} — you have concepts left to practice.`
    : `Browse community ${goalSummary.goalName} notes to build your knowledge.`;
  const linkLabel = examSlug
    ? `Browse ${goalSummary.goalName} notes`
    : `Browse ${goalSummary.goalName} notes in the community`;

  return (
    <Card className="space-y-3 p-4 sm:p-6">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold tracking-tight">What to study next</h2>
        <p className="text-sm leading-relaxed text-foreground/75">{message}</p>
      </div>
      <Link href={href} className="inline-flex text-sm font-medium text-blue-700 hover:underline dark:text-blue-300">
        {linkLabel} &rarr;
      </Link>
    </Card>
  );
}

function SetStudyFocusCard({ subjects }: Readonly<{ subjects: SubjectProgressEntry[] }>) {
  const focusSubjects = subjects.slice(0, 5);

  return (
    <Card className="space-y-4 border-dashed p-4 sm:p-5">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold tracking-tight">Set your study focus</h2>
        <p className="text-sm text-foreground/70">
          Pick subjects to track mastery toward. Your current subjects:
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        {focusSubjects.map((subject) => (
          <Link
            key={subject.subject}
            href="/profile#study-focus"
            className="rounded-full border border-blue-500/30 bg-blue-600/10 px-3 py-1 text-xs font-medium text-blue-700 hover:bg-blue-600/15 dark:text-blue-300"
          >
            {subject.subject}
          </Link>
        ))}
      </div>
      <Link href="/profile#study-focus" className="inline-flex text-sm font-medium text-blue-700 hover:underline dark:text-blue-300">
        Or set from Profile &rarr;
      </Link>
    </Card>
  );
}

function ProgressContent({
  report,
  state,
}: Readonly<{
  report: ProgressReportResponse | null;
  state: LoadState;
}>) {
  if (state === "error") {
    return (
      <Card className="p-4 text-sm text-foreground/75 sm:p-6">
        Could not load your progress report. Try refreshing.
      </Card>
    );
  }

  if (state === "loading") {
    return (
      <Card className="p-4 text-sm text-foreground/70 sm:p-6">
        Loading your progress report...
      </Card>
    );
  }

  const subjects = [...(report?.subjects ?? [])].sort(
    (a, b) => a.masteryPercentage - b.masteryPercentage,
  );
  const goalSummary = report?.goalSummary ?? null;

  return (
    <div className="space-y-6">
      {goalSummary ? <GoalSummaryHeader goalSummary={goalSummary} /> : null}
      {!goalSummary && subjects.length > 0 ? <SetStudyFocusCard subjects={subjects} /> : null}

      {goalSummary && goalSummary.totalConcepts > 0 ? <GoalMilestonesCard goalSummary={goalSummary} /> : null}
      {goalSummary ? <NextStudyCard goalSummary={goalSummary} /> : null}

      {subjects.length === 0 ? (
        <Card className="p-4 text-sm leading-relaxed text-foreground/75 sm:p-6">
          No study packs with concepts yet. Generate a Study Pack to start tracking your progress.
        </Card>
      ) : (
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/60">
              Concept Mastery
            </h2>
            <span className="text-xs text-foreground/50">{subjects.length} {subjects.length === 1 ? "subject" : "subjects"}</span>
          </div>
          <div className="grid gap-4">
            {subjects.map((entry) => (
              <SubjectProgressCard key={entry.subject} entry={entry} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

export function ProgressReportClient() {
  const router = useRouter();
  const [report, setReport] = useState<ProgressReportResponse | null>(null);
  const [state, setState] = useState<LoadState>("loading");

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    let cancelled = false;
    getProgressReport()
      .then((payload) => {
        if (cancelled) {
          return;
        }
        setReport(payload);
        setState("ready");
      })
      .catch(() => {
        if (cancelled) {
          return;
        }
        setState("error");
      });

    return () => {
      cancelled = true;
    };
  }, [router]);

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <ProgressHeader />
      <ProgressContent report={report} state={state} />
    </main>
  );
}
