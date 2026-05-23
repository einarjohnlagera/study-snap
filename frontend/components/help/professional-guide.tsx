import Link from "next/link";
import { ArrowRight, BarChart3, Briefcase, FileText, MessageSquareText, Settings, Target } from "lucide-react";
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
    title: "Start with a Professional Note",
    description:
      "Start with a Professional-profile note about the domain or subject area relevant to the interview.",
    cta: { label: "Create Note", href: "/notes/new" },
  },
  {
    number: 2,
    icon: Briefcase,
    title: "Select Interview Practice",
    description:
      "Select Interview Practice from the Challenge Quiz mode selection screen.",
    cta: { label: "Open Library", href: "/library" },
  },
  {
    number: 3,
    icon: MessageSquareText,
    title: "Answer and Review Critique",
    description:
      "Answer scenario-based multiple choice questions, then see targeted AI critique on each answer.",
    cta: { label: "Start Practice", href: "/library" },
  },
  {
    number: 4,
    icon: BarChart3,
    title: "Read Your Readiness Report",
    description:
      "Complete the session to receive an Interview Readiness Report showing your coverage and weak areas.",
    cta: { label: "Open Dashboard", href: "/dashboard" },
  },
];

const TIPS = [
  "Notes that cover one focused domain or role area produce the most useful practice questions.",
  "Read the AI critique even on correct answers — it adds context that helps in real interviews.",
  "Use the Interview Readiness Report to decide which topics to study before the next session.",
  "Each session is 10 questions with a 2-minute soft pacing timer — treat it like a real interview round.",
];

const REPORT_NOTES = [
  "The report summarizes how well you answered questions across the session.",
  "Weak areas are the topics where your answers were incorrect or partially understood.",
  "Use weak areas to guide your next note and next practice session before the real interview.",
];

type ProfessionalGuideProps = {
  showSwitchProfileCta?: boolean;
};

export function ProfessionalGuide({ showSwitchProfileCta = true }: Readonly<ProfessionalGuideProps>) {
  return (
    <div className="space-y-6">
      {/* Overview */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Interview Practice for Professionals
        </p>
        <p className="text-xs leading-relaxed text-foreground/70">
          Interview Practice helps professionals prepare for job interviews using their own notes. Instead of
          generic prep questions, you practice with scenario-based questions drawn from material you actually
          know — your domain notes, technical topics, and subject areas relevant to your target role.
        </p>
      </section>

      {/* Workflow */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          How Interview Practice works
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

      {/* Plan gate note */}
      <section className="rounded-xl border border-dashed border-border bg-muted/10 px-4 py-3">
        <p className="mb-1 text-sm font-medium text-foreground">Plan access</p>
        <p className="text-xs leading-relaxed text-foreground/60">
          Interview Practice is available on the Pro plan for Professional-profile users.
        </p>
      </section>

      {/* Tips */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Getting the most out of your sessions
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

      {/* Report */}
      <section className="space-y-3">
        <div className="flex items-center gap-2">
          <Target className="h-4 w-4 shrink-0 text-foreground/60" aria-hidden="true" />
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            What the Interview Readiness Report tells you
          </p>
        </div>
        <ul className="space-y-2">
          {REPORT_NOTES.map((note) => (
            <li key={note} className="flex gap-2 text-xs text-foreground/70">
              <span className="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" aria-hidden="true" />
              {note}
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
        {showSwitchProfileCta ? (
          <Link
            href="/profile#profile-type"
            className={
              buttonVariants({ variant: "outline", size: "sm" }) +
              " inline-flex w-full items-center gap-1.5 sm:w-auto"
            }
          >
            <Settings className="h-3.5 w-3.5" aria-hidden="true" />
            Switch Profile
          </Link>
        ) : null}
      </section>
    </div>
  );
}
