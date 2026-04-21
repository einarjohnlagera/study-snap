import Link from "next/link";
import { ArrowRight, BookOpen, Download, FileText, Search } from "lucide-react";
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
    title: "Add Lesson Material",
    description:
      "Paste your lesson notes, reviewer content, or teaching material. Save it as a note — this becomes the source for everything else.",
    cta: { label: "Create Note", href: "/notes/new" },
  },
  {
    number: 2,
    icon: BookOpen,
    title: "Generate a Quiz",
    description:
      "Turn the note into a teacher-ready quiz with questions, answers, and explanations in one click.",
    cta: { label: "Open Library", href: "/library" },
  },
  {
    number: 3,
    icon: Search,
    title: "Review the Output",
    description:
      "Open Quiz Preview to review every question with the correct answer and explanation already visible.",
    cta: { label: "Open Library", href: "/library" },
  },
  {
    number: 4,
    icon: Download,
    title: "Export for Reuse",
    description:
      "Export a classroom-ready DOCX from Quiz Preview as either Quiz Only (Student Version) or Quiz + Answers (Teacher Version).",
    cta: { label: "Open Library", href: "/library" },
  },
];

const TIPS = [
  "Start with one lesson topic at a time — focused notes produce tighter quiz questions.",
  "Clearer, well-structured notes lead to more accurate summaries and quiz outputs.",
  "Review the generated answer key before printing or sharing with students.",
  "Use Make a Copy on any note to refine material without losing the original Study Pack.",
];

export function TeacherGuide() {
  return (
    <div className="space-y-6">
      {/* Study System */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Workflow
        </p>
        <ol className="space-y-3">
          {STEPS.map((step, index) => {
            const Icon = step.icon;
            const isLast = index === STEPS.length - 1;
            return (
              <li key={step.number} className="flex gap-3">
                <div className="flex flex-col items-center">
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-semibold text-white">
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

      {/* Current limitations note */}
      <section className="rounded-xl border border-dashed border-border bg-muted/10 px-4 py-3">
        <p className="mb-1 text-sm font-medium text-foreground">Where NoteLib fits today</p>
        <p className="text-xs leading-relaxed text-foreground/60">
          Teacher-focused workflows are still improving. Right now, NoteLib works best for turning your lesson
          notes into quiz material, answer keys, and exportable review content — a practical shortcut for
          building reviewer resources faster.
        </p>
      </section>

      {/* Tips */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Tips
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
          className={
            buttonVariants({ variant: "outline", size: "sm" }) +
            " inline-flex w-full items-center gap-1.5 sm:w-auto"
          }
        >
          Browse Public Library
        </Link>
      </section>
    </div>
  );
}
