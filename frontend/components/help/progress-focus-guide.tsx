import Link from "next/link";
import {
  ArrowRight,
  BarChart2,
  CalendarClock,
  CheckCircle2,
  CircleDashed,
  Flag,
  RefreshCw,
  Target,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { PROFILE_STUDY_FOCUS_SECTION_ID } from "@/lib/profile-sections";

type MasteryState = {
  icon: LucideIcon;
  label: string;
  description: string;
  dotClass: string;
};

const MASTERY_STATES: MasteryState[] = [
  {
    icon: CheckCircle2,
    label: "Mastered",
    description:
      "You answered it correctly recently enough that it's still fresh. Only mastered concepts count toward your mastery %.",
    dotClass: "text-blue-500",
  },
  {
    icon: CalendarClock,
    label: "Due for review",
    description:
      "You got it right before, but enough time has passed that it needs another pass to stay sharp. It no longer counts as mastered until you review it.",
    dotClass: "text-amber-500",
  },
  {
    icon: CircleDashed,
    label: "Not started",
    description: "You haven't answered this concept correctly yet.",
    dotClass: "text-foreground/40",
  },
];

type Milestone = {
  label: string;
  description: string;
};

const MILESTONES: Milestone[] = [
  { label: "First concept mastered", description: "Master any one concept in your focus." },
  { label: "25% mastered", description: "A quarter of your concepts are fresh." },
  {
    label: "All concepts reviewed",
    description: "Every concept has been practiced at least once — nothing left at “not started.”",
  },
  { label: "50% mastered", description: "Halfway there." },
  { label: "70% mastered", description: "Strong coverage — you're exam-ready territory." },
  { label: "All concepts mastered", description: "100% of your concepts are fresh at once." },
];

export function ProgressFocusGuide() {
  return (
    <div className="space-y-6">
      {/* What Progress shows */}
      <section className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          What the Progress page shows
        </p>
        <p className="text-xs leading-relaxed text-foreground/70">
          Your Progress report tracks how well you know the concepts in your study focus — not how many
          quizzes you&apos;ve taken. It rolls up every concept across your notes into one mastery picture, so you
          always know what to study next.
        </p>
      </section>

      {/* Concept mastery */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          How mastery is measured
        </p>
        <p className="text-xs leading-relaxed text-foreground/65">
          Every concept is in one of three states. Your mastery % is simply the share of concepts that are
          currently <span className="font-medium text-foreground/80">mastered</span>.
        </p>
        <ul className="space-y-2.5">
          {MASTERY_STATES.map((state) => {
            const Icon = state.icon;
            return (
              <li key={state.label} className="flex gap-2.5 rounded-xl border border-border bg-muted/20 p-3">
                <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${state.dotClass}`} aria-hidden="true" />
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-foreground">{state.label}</p>
                  <p className="text-xs leading-relaxed text-foreground/65">{state.description}</p>
                </div>
              </li>
            );
          })}
        </ul>
        <div className="flex gap-2 rounded-xl border border-dashed border-border bg-muted/10 px-3 py-2.5">
          <RefreshCw className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
          <p className="text-xs leading-relaxed text-foreground/60">
            Mastery isn&apos;t permanent. A mastered concept slips back to &ldquo;due for review&rdquo; over time, so your %
            can dip if you stop practicing. That&apos;s spaced repetition working as intended — a quick review
            pass brings it back.
          </p>
        </div>
      </section>

      {/* Milestones */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Goal milestones
        </p>
        <p className="text-xs leading-relaxed text-foreground/65">
          Six checkpoints mark your path to a fully mastered goal. They fill in as you practice:
        </p>
        <ol className="space-y-2">
          {MILESTONES.map((milestone, index) => (
            <li key={milestone.label} className="flex gap-3">
              <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary text-[11px] font-semibold text-white">
                {index + 1}
              </span>
              <div className="min-w-0">
                <p className="text-sm font-medium text-foreground">{milestone.label}</p>
                <p className="text-xs leading-relaxed text-foreground/60">{milestone.description}</p>
              </div>
            </li>
          ))}
        </ol>
        <p className="text-xs leading-relaxed text-foreground/50">
          <Flag className="mr-1 inline h-3 w-3" aria-hidden="true" />
          &ldquo;All concepts reviewed&rdquo; tracks coverage (have you touched everything?) while the % milestones track
          depth (how much is fresh?) — so they can light up in a different order.
        </p>
      </section>

      {/* Study Focus */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Setting your study focus
        </p>
        <p className="text-xs leading-relaxed text-foreground/65">
          Your focus decides which concepts the Progress report counts. Set it on your Profile:
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Target className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              <span className="font-medium text-foreground/80">Subjects</span> — pick one or more subjects from your
              own notes. Students see this as <span className="font-medium text-foreground/80">Study Focus</span>;
              board exam takers see it as <span className="font-medium text-foreground/80">Exam Focus</span>.
              (Teacher profiles don&apos;t track a study focus.)
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Target className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              <span className="font-medium text-foreground/80">Exam goal</span> — or aim at a licensure exam (ALE,
              PNLE, LET) and track readiness toward it.
            </span>
          </li>
        </ul>
        <p className="text-xs leading-relaxed text-foreground/55">
          Subjects and an exam goal are mutually exclusive — the most recent choice wins. Either way, Progress
          shows combined mastery across your focus.
        </p>
      </section>

      {/* New term */}
      <section className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Starting a new term or school year
        </p>
        <p className="text-xs leading-relaxed text-foreground/65">
          There&apos;s no &ldquo;reset&rdquo; button — and you don&apos;t need one. Mastery is tracked per concept, not per term.
          When a new term starts, just update your Study Focus to the new subjects:
        </p>
        <ul className="space-y-1.5">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/60">
            <span className="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" aria-hidden="true" />
            New subjects start at 0% — their milestones begin fresh.
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/60">
            <span className="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" aria-hidden="true" />
            Subjects you keep carry their mastery forward — that&apos;s intentional, since you really do still know
            them.
          </li>
        </ul>
      </section>

      {/* CTA */}
      <section className="flex flex-col gap-2 border-t border-border pt-4 sm:flex-row">
        <Link
          href="/progress"
          className={buttonVariants({ variant: "default", size: "sm" }) + " inline-flex w-full items-center gap-1.5 sm:w-auto"}
        >
          <BarChart2 className="h-3.5 w-3.5" aria-hidden="true" />
          View your Progress
        </Link>
        <Link
          href={`/profile#${PROFILE_STUDY_FOCUS_SECTION_ID}`}
          className={buttonVariants({ variant: "outline", size: "sm" }) + " inline-flex w-full items-center gap-1.5 sm:w-auto"}
        >
          Set Study Focus
          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
        </Link>
      </section>
    </div>
  );
}
