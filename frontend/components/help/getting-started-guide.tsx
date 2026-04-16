import Link from "next/link";
import { ArrowRight, BookOpen, Brain, FileText, Globe } from "lucide-react";
import type { LucideIcon } from "lucide-react";

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
    title: "Create a Note",
    description: "Write or paste your study material — lecture notes, textbook excerpts, or reviewer content.",
    cta: { label: "Create Note", href: "/notes/new" },
  },
  {
    number: 2,
    icon: BookOpen,
    title: "Generate a Study Pack",
    description: "Turn your note into a summary, key concepts, and a practice quiz with one click.",
    cta: { label: "Go to Library", href: "/library" },
  },
  {
    number: 3,
    icon: Brain,
    title: "Practice with Quizzes",
    description: "Use Quick Review to reinforce recall, or Challenge Quiz to test yourself under timed conditions.",
    cta: { label: "Open Library", href: "/library" },
  },
];

export function GettingStartedGuide() {
  return (
    <div className="space-y-5">
      {/* Steps */}
      <ol className="space-y-3">
        {STEPS.map((step, index) => {
          const Icon = step.icon;
          const isLast = index === STEPS.length - 1;
          return (
            <li key={step.number} className="flex gap-3">
              {/* Left: step indicator + connector */}
              <div className="flex flex-col items-center">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xs font-semibold text-white dark:bg-blue-500">
                  {step.number}
                </span>
                {!isLast ? (
                  <span className="mt-1 h-full w-px bg-border" />
                ) : null}
              </div>

              {/* Right: content card */}
              <div className="mb-1 min-w-0 flex-1 rounded-xl border border-border bg-muted/20 p-3">
                <div className="mb-2 flex items-center gap-2">
                  <Icon className="h-4 w-4 shrink-0 text-foreground/60" aria-hidden="true" />
                  <p className="text-sm font-semibold text-foreground">{step.title}</p>
                </div>
                <p className="mb-3 text-xs leading-relaxed text-foreground/65">
                  {step.description}
                </p>
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

      {/* Empty state / alternate path */}
      <div className="rounded-xl border border-dashed border-border bg-muted/10 px-4 py-3">
        <p className="mb-0.5 text-sm font-medium text-foreground">Don&apos;t have notes yet?</p>
        <p className="mb-2.5 text-xs text-foreground/60">
          Browse notes shared by other students to see what a Study Pack looks like before creating your own.
        </p>
        <Link
          href="/public/library"
          className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
        >
          <Globe className="h-3 w-3" aria-hidden="true" />
          Browse Public Library
          <ArrowRight className="h-3 w-3" aria-hidden="true" />
        </Link>
      </div>

      {/* Tip */}
      <p className="text-xs text-foreground/45">
        Tip: the more complete your note, the better the generated quiz and summary will be.
      </p>
    </div>
  );
}
