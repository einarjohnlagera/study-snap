"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Card } from "@/components/ui/card";
import { BackLink } from "@/components/ui/back-link";
import { PageHeader } from "@/components/page-header";
import { getProgressReport, type GoalSummaryResponse, type ProgressReportResponse, type SubjectProgressEntry } from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

type LoadState = "loading" | "ready" | "error";

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
  return (
    <Card className="overflow-hidden border-blue-500/25 bg-linear-to-br from-blue-500/10 via-background to-emerald-500/10 p-4 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            {goalSummary.goalName} Goal
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

function NextStudyCard({ goalSummary }: Readonly<{ goalSummary: GoalSummaryResponse }>) {
  const isExamGoal = goalSummary.goalType === "EXAM";
  const href = isExamGoal
    ? `/exam/${goalSummary.studyGoal}`
    : `/public/library?courseProgram=${encodeURIComponent(goalSummary.studyGoal)}`;
  const message = goalSummary.weakestGoalSubject
    ? `Focus on ${goalSummary.weakestGoalSubject} — you have concepts left to practice.`
    : `Browse community ${goalSummary.goalName} notes to build your knowledge.`;
  const linkLabel = isExamGoal
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

  const subjects = report?.subjects ?? [];
  const goalSummary = report?.goalSummary ?? null;

  return (
    <div className="space-y-6">
      {goalSummary ? <GoalSummaryHeader goalSummary={goalSummary} /> : null}
      {!goalSummary ? (
        <Card className="border-dashed p-4 text-sm text-foreground/75 sm:p-5">
          No study focus set.{" "}
          <Link href="/profile#study-focus" className="font-medium text-blue-700 hover:underline dark:text-blue-300">
            Set one in Profile settings →
          </Link>
        </Card>
      ) : null}

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

      {goalSummary ? <NextStudyCard goalSummary={goalSummary} /> : null}
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
