import { cn } from "@/lib/utils";

export type ScoreRevealTone = "long-exam" | "board-exam";

type ScoreRevealProps = {
  percentage: number;
  label: string;
  supportingLine?: string;
  performanceLevel?: string | null;
  tone?: ScoreRevealTone;
  className?: string;
};

function resolvePerformancePillClass(performanceLevel: string): string {
  if (performanceLevel === "Excellent") {
    return "border-emerald-600/40 text-emerald-700 dark:text-emerald-300";
  }
  if (performanceLevel === "Good") {
    return "border-foreground/30 text-foreground/80";
  }
  if (performanceLevel === "Fair") {
    return "border-amber-600/40 text-amber-700 dark:text-amber-300";
  }
  return "border-orange-600/40 text-orange-700 dark:text-orange-300";
}

export function ScoreReveal({
  percentage,
  label,
  supportingLine,
  performanceLevel,
  tone = "long-exam",
  className,
}: ScoreRevealProps) {
  const clamped = Math.max(0, Math.min(100, Math.round(percentage)));
  const ariaLabel = `${label}, ${clamped} percent`;

  return (
    <section
      data-testid="exam-score-reveal"
      data-tone={tone}
      className={cn(
        "flex flex-col items-center gap-3 rounded-2xl border border-border bg-card py-10 px-6 text-center sm:py-14 sm:px-8",
        className,
      )}
    >
      <h2
        className="text-6xl font-semibold tracking-tight tabular-nums text-foreground sm:text-7xl"
        aria-label={ariaLabel}
      >
        {clamped}
        <span className="ml-1 text-3xl font-medium text-foreground/55 sm:text-4xl">%</span>
      </h2>
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/55">
        {label}
      </p>
      {supportingLine ? (
        <p className="max-w-md text-sm leading-relaxed text-foreground/70">
          {supportingLine}
        </p>
      ) : null}
      {performanceLevel ? (
        <span
          className={cn(
            "mt-1 inline-flex items-center rounded-full border bg-transparent px-3 py-1 text-xs font-medium",
            resolvePerformancePillClass(performanceLevel),
          )}
        >
          {performanceLevel}
        </span>
      ) : null}
    </section>
  );
}
