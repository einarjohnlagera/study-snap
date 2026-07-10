import { BookOpen, CheckCircle2, Compass, MessageCircle, PencilLine } from "lucide-react";
import { getCollectionLabels } from "@/lib/collection-labels";
import type { ProfileType } from "@/lib/api";

export function LearningCompanionGuide({ profileType }: Readonly<{ profileType: ProfileType | null }>) {
  const labels = getCollectionLabels(profileType);

  return (
    <div className="space-y-6">
      <section className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          What the {labels.companionSingular} is
        </p>
        <p className="text-xs leading-relaxed text-foreground/70">
          The Learning {labels.companionSingular} is authored guidance attached to an Official top-level{" "}
          {labels.singular}. It is static, curator-written advice for using that set, not guidance generated
          per view.
        </p>
      </section>

      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Today&apos;s Focus
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <Compass className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              The coach card appears near the top of the {labels.singular.toLowerCase()} detail page after the
              hero and answers what to do next.
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              It combines the resolved primary action, a Continue Studying CTA, a short coaching sentence, and
              Quick Actions such as Review Due Concepts or the terminal exam/builder action.
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <MessageCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              When nothing is due, it shows a caught-up state and stays useful. It renders even when no authored
              {labels.companionSingular.toLowerCase()} exists.
            </span>
          </li>
        </ul>
      </section>

      <section className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Mentor Tips
        </p>
        <p className="text-xs leading-relaxed text-foreground/70">
          Mentor Tips are small authored notes that can link to an existing action. At most one eligible tip
          appears near Today&apos;s Focus, selected deterministically in authored order.
        </p>
        <p className="text-xs leading-relaxed text-foreground/55">
          Every tip remains reachable in View Full Guide, even when its date or progress condition is not active
          right now.
        </p>
      </section>

      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          The full guide
        </p>
        <p className="text-xs leading-relaxed text-foreground/65">
          The full Learning {labels.companionSingular} is collapsed by default so Today&apos;s Focus and Progress
          stay first. Use View Full Guide when you want the reference material.
        </p>
        <ul className="space-y-2">
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <BookOpen className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>
              The long-form sections use coach-voice headings for Overview, Study Strategy, Common Mistakes,
              FAQ, and Resources.
            </span>
          </li>
          <li className="flex gap-2 text-xs leading-relaxed text-foreground/65">
            <MessageCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/50" aria-hidden="true" />
            <span>Mentor Tips appear as the final section after those five guide sections.</span>
          </li>
        </ul>
      </section>

      <section className="space-y-2 border-t border-border pt-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          For official authors
        </p>
        <p className="text-xs leading-relaxed text-foreground/70">
          ADMIN users can author or edit {labels.companionSingular} content, including Mentor Tips, through Manage
          {labels.companionSingular} on an eligible top-level {labels.singular}. AI-assisted per-section drafts are
          optional, and a human must review and save before anything is published.
        </p>
        <p className="flex gap-2 text-xs leading-relaxed text-foreground/55">
          <PencilLine className="mt-0.5 h-3.5 w-3.5 shrink-0 text-foreground/45" aria-hidden="true" />
          <span>Draft generation never publishes by itself; the saved authored guide remains the source of truth.</span>
        </p>
      </section>
    </div>
  );
}
