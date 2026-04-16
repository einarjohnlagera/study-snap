import Link from "next/link";
import { ArrowRight, BookOpen, Brain, FileText, Globe, RotateCcw, Target, Zap } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";

type Step = {
  number: number;
  icon: LucideIcon;
  title: string;
  description: string;
  cta: { label: string; href: string };
};

const STEPS: Step[] = [
  {
    number: 1,
    icon: FileText,
    title: "Write or Collect Notes",
    description: "Paste lecture notes, textbook content, or reviewer material. The more complete, the better.",
    cta: { label: "Create Note", href: "/notes/new" },
  },
  {
    number: 2,
    icon: BookOpen,
    title: "Generate a Study Pack",
    description: "Turn your note into a summary, key concepts, and quiz questions in one click.",
    cta: { label: "Open Library", href: "/library" },
  },
  {
    number: 3,
    icon: Zap,
    title: "Practice with Quizzes",
    description: "Start with Quick Review to reinforce recall, then take a Challenge Quiz to test yourself.",
    cta: { label: "Start Reviewing", href: "/library" },
  },
  {
    number: 4,
    icon: Target,
    title: "Focus on Weak Areas",
    description: "Review your mistakes and run Adaptive Practice on concepts you keep getting wrong.",
    cta: { label: "See Weak Concepts", href: "/dashboard" },
  },
];

const ROUTINE_STEPS = [
  { label: "Read Summary", icon: BookOpen },
  { label: "Take Quiz", icon: Brain },
  { label: "Review Mistakes", icon: RotateCcw },
  { label: "Repeat", icon: ArrowRight },
];

const TIPS = [
  "Take a quiz the same day you study — don't wait until review night.",
  "Wrong answers are more useful than correct ones. Always check explanations.",
  "Weak Concepts on your Dashboard show exactly what needs more practice.",
  "Challenge Quiz once a week keeps recall sharp across older notes.",
];

export function StudentGuide() {
  return (
    <div className="space-y-6">
      {/* Study System */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Study System
        </p>
        <ol className="space-y-3">
          {STEPS.map((step, index) => {
            const Icon = step.icon;
            const isLast = index === STEPS.length - 1;
            return (
              <li key={step.number} className="flex gap-3">
                <div className="flex flex-col items-center">
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xs font-semibold text-white dark:bg-blue-500">
                    {step.number}
                  </span>
                  {!isLast ? <span className="mt-1.5 flex-1 w-px bg-border" /> : null}
                </div>
                <div className="mb-1 min-w-0 flex-1 rounded-xl border border-border bg-muted/20 p-3">
                  <div className="mb-1.5 flex items-center gap-2">
                    <Icon className="h-4 w-4 shrink-0 text-foreground/60" aria-hidden="true" />
                    <p className="text-sm font-semibold text-foreground">{step.title}</p>
                  </div>
                  <p className="mb-2.5 text-xs leading-relaxed text-foreground/65">{step.description}</p>
                  <Link
                    href={step.cta.href}
                    className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
                  >
                    {step.cta.label}
                    <ArrowRight className="h-3 w-3" aria-hidden="true" />
                  </Link>
                </div>
              </li>
            );
          })}
        </ol>
      </section>

      {/* Study Routine */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Daily Study Routine
        </p>
        <div className="flex flex-wrap items-center gap-2">
          {ROUTINE_STEPS.map((item, index) => {
            const Icon = item.icon;
            const isLast = index === ROUTINE_STEPS.length - 1;
            return (
              <div key={item.label} className="flex items-center gap-2">
                <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted/30 px-2.5 py-1 text-xs font-medium text-foreground">
                  <Icon className="h-3 w-3 shrink-0 text-foreground/60" aria-hidden="true" />
                  {item.label}
                </span>
                {!isLast ? (
                  <ArrowRight className="h-3 w-3 shrink-0 text-foreground/30" aria-hidden="true" />
                ) : null}
              </div>
            );
          })}
        </div>
      </section>

      {/* Study Tips */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Study Tips
        </p>
        <ul className="space-y-2">
          {TIPS.map((tip) => (
            <li key={tip} className="flex gap-2 text-xs text-foreground/70">
              <span className="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" aria-hidden="true" />
              {tip}
            </li>
          ))}
        </ul>
      </section>

      {/* CTA */}
      <section className="flex flex-col gap-2 border-t border-border pt-4 sm:flex-row">
        <Link
          href="/notes/new"
          className={buttonVariants({ variant: "default", size: "sm" }) + " w-full sm:w-auto"}
        >
          Create Note
        </Link>
        <Link
          href="/public/library"
          className={buttonVariants({ variant: "outline", size: "sm" }) + " inline-flex w-full items-center gap-1.5 sm:w-auto"}
        >
          <Globe className="h-3.5 w-3.5" aria-hidden="true" />
          Browse Public Library
        </Link>
      </section>
    </div>
  );
}
