import Link from "next/link";
import { ArrowRight, FileText, Settings, Shield, Target, Timer } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";

type Step = {
  number: number;
  icon: LucideIcon;
  title: string;
  description: string;
  badge?: string;
  cta: { label: string; href: string };
};

const STEPS: Step[] = [
  {
    number: 1,
    icon: FileText,
    title: "Build Your Notes by Topic",
    description:
      "Organize your reviewer material one topic at a time — one note per topic works best. The more complete the note, the better the generated exam.",
    cta: { label: "Create Note", href: "/notes/new" },
  },
  {
    number: 2,
    icon: Timer,
    title: "Practice with Challenge Quiz",
    description:
      "Use Challenge Quiz for focused board-style practice before you commit to a full simulation.",
    cta: { label: "Open Library", href: "/library" },
  },
  {
    number: 3,
    icon: Target,
    title: "Review Your Mastery Report",
    description:
      "After each quiz or simulation, review weak domains and low-accuracy concepts so your next session targets the right gaps.",
    cta: { label: "See Dashboard", href: "/dashboard" },
  },
  {
    number: 4,
    icon: Shield,
    title: "Simulate with Board Exam Mode",
    description:
      "When you're ready, run Board Exam Mode for a full high-stakes simulation — strict timer, no pausing, score report at the end.",
    badge: "Pro",
    cta: { label: "Open Library", href: "/library" },
  },
];

const TIPS = [
  "Challenge Quiz is focused practice. Board Exam Mode is the simulation.",
  "Board Exam Mode has a strict timer — leaving the session forfeits your score.",
  "The timer keeps running if you close the tab. Stay in the browser during simulations.",
  "Use your weak concepts after each session to decide what to study next.",
  "Preparing for boards? Switch your profile type to unlock Board Exam Mode.",
];

type BoardExamGuideProps = {
  showSwitchProfileCta?: boolean;
};

export function BoardExamGuide({ showSwitchProfileCta = true }: Readonly<BoardExamGuideProps>) {
  return (
    <div className="space-y-6">
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Study Workflow
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
                    {step.badge ? (
                      <span className="inline-flex items-center rounded-full border border-border bg-muted/40 px-2 py-0.5 text-[10px] font-medium text-foreground/60">
                        {step.badge}
                      </span>
                    ) : null}
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
