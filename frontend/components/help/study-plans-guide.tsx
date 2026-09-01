import Link from "next/link";
import { ArrowRight, CalendarClock, Layers, ListOrdered, Copy, GraduationCap, Star, Target } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { getCollectionLabels } from "@/lib/collection-labels";
import type { ProfileType } from "@/lib/api";

export function StudyPlansGuide({ profileType }: Readonly<{ profileType: ProfileType | null }>) {
  const labels = getCollectionLabels(profileType);
  const isTeacher = profileType === "TEACHER";

  return (
    <div className="space-y-6">
      {/* What they are */}
      <section className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          What {labels.plural} are
        </p>
        <p className="text-xs leading-relaxed text-foreground/70">
          A {labels.singular} is a saved, named, ordered set of your own notes — a playlist over notes you already
          have. Use it to gather a unit, an exam topic, or a weekly review into one place you can return to.
        </p>
        <p className="text-xs leading-relaxed text-foreground/55">
          A {labels.singular} isn&apos;t a new kind of content — Study Packs and quizzes stay per note, on your
          existing limits. A {labels.singular} organizes those notes, and a Long Exam can draw its questions from
          the notes in one, so you can be tested across the whole {labels.singular} rather than one note at a time.
        </p>
      </section>

      {/* What you can do */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          What you can do
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Layers className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>Group related notes into one set, and reorder them into the sequence you want to study.</span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <ListOrdered className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>Label each item (week, topic, or section) to give the set structure.</span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Copy className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              A note can belong to several {labels.plural.toLowerCase()} at once, and deleting a {labels.singular.toLowerCase()}{" "}
              never deletes its notes — your notes stay independent.
            </span>
          </li>
          {isTeacher ? (
            <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
              <GraduationCap className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
              <span>
                Build a combined exam from a {labels.singular.toLowerCase()} — a sectioned DOCX plus shareable student
                quiz links — straight from Exam Builder.
              </span>
            </li>
          ) : (
            <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
              <GraduationCap className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
              <span>Study the set and generate a Study Pack or quiz per note, on your existing limits.</span>
            </li>
          )}
        </ul>
      </section>

      {/* How to create */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          How to create one
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <ArrowRight className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              In the <Link href="/library" className="font-medium text-blue-600 hover:underline dark:text-blue-400">Library</Link>,
              open <span className="font-medium text-foreground/80">New</span> →{" "}
              <span className="font-medium text-foreground/80">{labels.newCtaLabel}</span>, then pick the notes to include.
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <ArrowRight className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              Or open <span className="font-medium text-foreground/80">{labels.navLabel}</span> from the navigation to
              create one and add notes from there.
            </span>
          </li>
        </ul>
      </section>

      {/* Primary collection */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Your {labels.primarySingular}
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Star className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              Mark one top-level {labels.singular.toLowerCase()} as your default so it appears first on Dashboard,
              {labels.navLabel}, and Progress.
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <ArrowRight className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              Set or remove it from the {labels.singular.toLowerCase()} detail page&apos;s overflow menu. If you have
              exactly one top-level goal and no primary set yet, NoteLib can set it as primary automatically.
            </span>
          </li>
        </ul>
      </section>

      {/* Target date pacing */}
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Pacing to a target date
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Target className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              On a top-level {labels.singular.toLowerCase()}, use Edit to set an optional target completion date and
              your study intensity in days per week. Child {labels.plural.toLowerCase()} do not have their own target
              dates.
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <CalendarClock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              Once a target date is set, Progress shows weeks remaining and concepts remaining, and Today&apos;s
              Focus includes a daily concept budget in its coaching sentence. Due concepts are always included; new
              concepts are paced across your remaining study days.
            </span>
          </li>
        </ul>
      </section>

      {/* CTA */}
      <section className="flex flex-col gap-2 border-t border-border pt-4 sm:flex-row">
        <Link
          href="/collections"
          className={buttonVariants({ variant: "default", size: "sm" }) + " inline-flex w-full items-center gap-1.5 sm:w-auto"}
        >
          <Layers className="h-3.5 w-3.5" aria-hidden="true" />
          Open {labels.navLabel}
        </Link>
      </section>
    </div>
  );
}
