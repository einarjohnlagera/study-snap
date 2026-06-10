import Link from "next/link";
import { ArrowRight, Compass, GraduationCap, Heart, PenTool, Target } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";

type Hub = {
  icon: LucideIcon;
  shortName: string;
  fullName: string;
  href: string;
};

const HUBS: Hub[] = [
  { icon: PenTool, shortName: "ALE", fullName: "Architect Licensure Examination", href: "/exam/ale" },
  { icon: Heart, shortName: "PNLE", fullName: "Philippine Nurse Licensure Examination", href: "/exam/pnle" },
  { icon: GraduationCap, shortName: "LET", fullName: "Licensure Examination for Teachers", href: "/exam/let" },
];

export function ExamHubsGuide() {
  return (
    <div className="space-y-6">
      {/* What they are */}
      <section className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          What Exam Hubs are
        </p>
        <p className="text-xs leading-relaxed text-foreground/70">
          Exam Hubs are curated landing pages for major Philippine licensure exams. Each hub gathers the most
          useful public notes, summaries, and practice quizzes for that exam in one place — so you don&apos;t have to
          dig through the whole Public Library to find relevant review material.
        </p>
        <p className="text-xs leading-relaxed text-foreground/55">
          A hub isn&apos;t separate content — it&apos;s a curated view over notes the community has already shared,
          organized into featured, popular, and recent sections for that exam.
        </p>
      </section>

      {/* The hubs */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Available hubs
        </p>
        <ul className="space-y-2">
          {HUBS.map((hub) => {
            const Icon = hub.icon;
            return (
              <li key={hub.shortName}>
                <Link
                  href={hub.href}
                  className="flex items-center gap-3 rounded-xl border border-border bg-muted/20 p-3 transition-colors hover:bg-highlight"
                >
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-border bg-muted/40">
                    <Icon className="h-4 w-4 text-foreground/70" aria-hidden="true" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-foreground">{hub.shortName}</span>
                    <span className="block text-xs text-foreground/60">{hub.fullName}</span>
                  </span>
                  <ArrowRight className="h-4 w-4 shrink-0 text-foreground/40" aria-hidden="true" />
                </Link>
              </li>
            );
          })}
        </ul>
      </section>

      {/* How to reach */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          How to find them
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Compass className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              Open <span className="font-medium text-foreground/80">Exam Hubs</span> from the top navigation, or
              visit the <Link href="/exam" className="font-medium text-blue-600 hover:underline dark:text-blue-400">hub index</Link>.
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Compass className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              When you open a public note tagged for one of these exams, a callout links straight to the matching
              hub.
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Target className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              Set an exam as your <span className="font-medium text-foreground/80">study goal</span> on your Profile
              and your Progress report&apos;s next-step card links back to that hub.
            </span>
          </li>
        </ul>
      </section>

      {/* CTA */}
      <section className="flex flex-col gap-2 border-t border-border pt-4 sm:flex-row">
        <Link
          href="/exam"
          className={buttonVariants({ variant: "default", size: "sm" }) + " inline-flex w-full items-center gap-1.5 sm:w-auto"}
        >
          <Compass className="h-3.5 w-3.5" aria-hidden="true" />
          Browse Exam Hubs
        </Link>
      </section>
    </div>
  );
}
