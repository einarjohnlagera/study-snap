import Link from "next/link";
import { ArrowRight, Gauge, Library, RefreshCw, Sparkles } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";

export function BulkGenerateGuide() {
  return (
    <div className="space-y-6">
      <section className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          What Bulk Generation does
        </p>
        <p className="text-xs leading-relaxed text-foreground/70">
          Bulk Generation turns a list of topics into separate notes. Each topic gets note content first, then NoteLib
          starts a Study Pack for that note in the background.
        </p>
        <p className="text-xs leading-relaxed text-foreground/55">
          Created notes appear in your Library as they finish. If a topic cannot become a note, NoteLib reports the
          exact topic so you know what did not materialize.
        </p>
      </section>

      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          What to expect
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Sparkles className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>One topic becomes one saved note, with its own generated Study Pack started after the note exists.</span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Library className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>You can leave the page after queueing. The Library refreshes while notes and Study Packs arrive.</span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Gauge className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>Monthly topic note limits still apply. Free users should check the remaining count before queueing a long list.</span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <RefreshCw className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>Retry is for topics that failed to generate. Quota-blocked topics need more monthly credits or an upgrade first.</span>
          </li>
        </ul>
      </section>

      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          How to start
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <ArrowRight className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              Open the <Link href="/library" className="font-medium text-blue-600 hover:underline dark:text-blue-400">Library</Link>,
              then choose <span className="font-medium text-foreground/80">New</span> &rarr;{" "}
              <span className="font-medium text-foreground/80">Bulk generate</span>.
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <ArrowRight className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>Add a subject, paste one topic per line, and queue the batch.</span>
          </li>
        </ul>
      </section>

      <section className="flex flex-col gap-2 border-t border-border pt-4 sm:flex-row">
        <Link
          href="/library/bulk-generate"
          className={buttonVariants({ variant: "default", size: "sm" }) + " inline-flex w-full items-center gap-1.5 sm:w-auto"}
        >
          <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
          Open Bulk Generation
        </Link>
      </section>
    </div>
  );
}
