"use client";

import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";
import type { SubjectProgressEntry } from "@/lib/api";
import { cn } from "@/lib/utils";

const LOW_READINESS_THRESHOLD = 40;
const MEDIUM_READINESS_THRESHOLD = 60;
const RING_SIZE = 96;
const RING_STROKE = 8;
const RING_RADIUS = (RING_SIZE - RING_STROKE) / 2;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

function clampPercentage(value: number): number {
  return Math.min(100, Math.max(0, value));
}

export function isReadinessNotStarted(entry: Pick<SubjectProgressEntry, "masteryPercentage" | "notPracticedConcepts" | "totalConcepts">): boolean {
  return entry.masteryPercentage === 0 && entry.notPracticedConcepts === entry.totalConcepts;
}

export function getReadinessBarTone(entry: Pick<SubjectProgressEntry, "masteryPercentage" | "notPracticedConcepts" | "totalConcepts">): string {
  if (isReadinessNotStarted(entry)) return "bg-foreground/20";
  if (entry.masteryPercentage < LOW_READINESS_THRESHOLD) return "bg-rose-500";
  if (entry.masteryPercentage < MEDIUM_READINESS_THRESHOLD) return "bg-amber-500";
  return "bg-blue-600 dark:bg-blue-400";
}

export function getReadinessAccentBorder(entry: Pick<SubjectProgressEntry, "masteryPercentage" | "notPracticedConcepts" | "totalConcepts">): string {
  if (isReadinessNotStarted(entry)) return "border-l-4 border-l-foreground/20";
  if (entry.masteryPercentage < LOW_READINESS_THRESHOLD) return "border-l-4 border-l-rose-500";
  if (entry.masteryPercentage < MEDIUM_READINESS_THRESHOLD) return "border-l-4 border-l-amber-500";
  return "border-l-4 border-l-blue-500 dark:border-l-blue-400";
}

export function getReadinessTextColor(entry: Pick<SubjectProgressEntry, "masteryPercentage" | "notPracticedConcepts" | "totalConcepts">): string {
  if (isReadinessNotStarted(entry)) return "text-foreground/50";
  if (entry.masteryPercentage < LOW_READINESS_THRESHOLD) return "text-rose-600 dark:text-rose-400";
  if (entry.masteryPercentage < MEDIUM_READINESS_THRESHOLD) return "text-amber-600 dark:text-amber-400";
  return "text-blue-600 dark:text-blue-400";
}

export function getReadinessStatusLabel(entry: Pick<SubjectProgressEntry, "masteryPercentage" | "notPracticedConcepts" | "totalConcepts">): string {
  return isReadinessNotStarted(entry) ? "Not started" : `${entry.masteryPercentage}% ready`;
}

function ReadinessRing({ percentage }: Readonly<{ percentage: number }>) {
  const clampedPercentage = clampPercentage(percentage);
  const dashOffset = RING_CIRCUMFERENCE - (clampedPercentage / 100) * RING_CIRCUMFERENCE;

  return (
    <div className="relative size-24 shrink-0" aria-hidden="true">
      <svg viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`} className="size-24 -rotate-90">
        <circle
          cx={RING_SIZE / 2}
          cy={RING_SIZE / 2}
          r={RING_RADIUS}
          fill="none"
          stroke="currentColor"
          strokeWidth={RING_STROKE}
          className="text-muted"
        />
        <circle
          cx={RING_SIZE / 2}
          cy={RING_SIZE / 2}
          r={RING_RADIUS}
          fill="none"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth={RING_STROKE}
          strokeDasharray={RING_CIRCUMFERENCE}
          strokeDashoffset={dashOffset}
          className="text-blue-600 transition-all dark:text-blue-400"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-2xl font-semibold tracking-tight">{clampedPercentage}%</span>
        <span className="text-[0.65rem] font-semibold uppercase tracking-wide text-foreground/55">ready</span>
      </div>
    </div>
  );
}

export function ReadinessBar({ entry }: Readonly<{ entry: SubjectProgressEntry }>) {
  const fillWidth = isReadinessNotStarted(entry) ? 0 : entry.masteryPercentage;

  return (
    <Card className={cn("space-y-4 p-4", getReadinessAccentBorder(entry))}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-base font-semibold text-foreground">{entry.subject}</h3>
          <p className="text-sm text-foreground/60">{entry.totalConcepts} concepts tracked</p>
        </div>
        <p className={cn("shrink-0 text-xl font-semibold", getReadinessTextColor(entry))}>
          {getReadinessStatusLabel(entry)}
        </p>
      </div>
      <div className="space-y-2">
        <div
          role="progressbar"
          aria-label={`${entry.subject} readiness`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={entry.masteryPercentage}
          data-state={isReadinessNotStarted(entry) ? "not-started" : "ready"}
          className="h-3 overflow-hidden rounded-full bg-muted"
        >
          <div
            className={cn("h-full rounded-full transition-all", getReadinessBarTone(entry))}
            style={{ width: `${fillWidth}%` }}
          />
        </div>
        <p className="text-sm text-foreground/70">
          {entry.masteredConcepts} mastered &middot; {entry.dueConcepts} due &middot; {entry.notPracticedConcepts} not started
        </p>
      </div>
    </Card>
  );
}

type ReadinessSummaryProps = {
  overallReadinessPercentage: number;
  totalConcepts: number;
  masteredConcepts: number;
  dueConcepts: number;
  notPracticedConcepts: number;
  subjects: SubjectProgressEntry[];
  variant?: "full" | "compact";
  title?: string;
  eyebrow?: string;
  description?: string;
  unavailable?: boolean;
  unavailableDescription?: string;
  emptyTitle?: string;
  emptyDescription?: string;
  footer?: ReactNode;
};

export function ReadinessSummary({
  overallReadinessPercentage,
  totalConcepts,
  masteredConcepts,
  dueConcepts,
  notPracticedConcepts,
  subjects,
  variant = "full",
  title = "How ready this set is right now",
  eyebrow = "Overall readiness",
  description,
  unavailable = false,
  unavailableDescription = "Readiness is unavailable right now.",
  emptyTitle = "No readiness yet",
  emptyDescription = "Generate Study Packs and practice to see readiness.",
  footer,
}: Readonly<ReadinessSummaryProps>) {
  if (variant === "compact") {
    return (
      <Card className="p-4 sm:p-5">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              {eyebrow}
            </p>
            <div className="space-y-1">
              <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
              {unavailable ? (
                <p className="text-sm text-foreground/70">{unavailableDescription}</p>
              ) : totalConcepts > 0 ? (
                <>
                  <p className="text-sm font-medium text-foreground/80">
                    {overallReadinessPercentage}% ready &middot; {masteredConcepts}/{totalConcepts} mastered &middot; {dueConcepts} due
                  </p>
                  <p className="text-sm text-foreground/65">
                    {notPracticedConcepts} not started
                    {description ? <> &middot; {description}</> : null}
                  </p>
                </>
              ) : (
                <p className="text-sm text-foreground/70">{emptyDescription}</p>
              )}
            </div>
          </div>
          <ReadinessRing percentage={unavailable ? 0 : overallReadinessPercentage} />
        </div>
        {footer ? <div className="mt-4 border-t border-border pt-4">{footer}</div> : null}
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <Card className="p-4 sm:p-6">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              {eyebrow}
            </p>
            <div className="space-y-1">
              <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
              {totalConcepts > 0 ? (
                <p className="text-sm text-foreground/70">
                  {masteredConcepts} mastered &middot; {dueConcepts} due &middot; {notPracticedConcepts} not started
                </p>
              ) : (
                <p className="text-sm text-foreground/70">{emptyDescription}</p>
              )}
            </div>
          </div>
          <ReadinessRing percentage={overallReadinessPercentage} />
        </div>
      </Card>

      {subjects.length === 0 ? (
        <Card className="space-y-1 border-dashed p-4 sm:p-6">
          <h2 className="text-lg font-semibold tracking-tight">{emptyTitle}</h2>
          <p className="text-sm text-foreground/70">{emptyDescription}</p>
        </Card>
      ) : (
        <section className="space-y-3">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/60">
              Subject readiness
            </h2>
            <span className="text-xs text-foreground/50">
              {subjects.length} {subjects.length === 1 ? "subject" : "subjects"}
            </span>
          </div>
          <div className="grid gap-4">
            {subjects.map((entry) => (
              <ReadinessBar key={entry.subject} entry={entry} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
