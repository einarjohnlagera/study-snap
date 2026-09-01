import Link from "next/link";
import { AlignLeft, ArrowRight, Layers, Sparkles } from "lucide-react";

type Section = {
  icon: React.ElementType;
  title: string;
  description: string;
  bullets?: string[];
  cta?: { label: string; href: string };
};

const SECTIONS: Section[] = [
  {
    icon: Sparkles,
    title: "Generating a Study Pack",
    description:
      "Open any note and click Generate Study Pack. NoteLib reads your note and builds the pack in the background — you'll be notified when it's ready.",
    bullets: [
      "Generation usually takes under a minute",
      "Note content is locked after generation to keep the study material consistent",
      "To revise the content, use Make a Copy — the copy starts fresh and can be edited freely",
    ],
    cta: { label: "Create Note", href: "/notes/new" },
  },
  {
    icon: AlignLeft,
    title: "Summary",
    description: "A condensed overview of your note, distilled into the key points.",
    bullets: [
      "Covers the main topic, core arguments, and key terms from your note",
      "Written at your learner level for clarity",
      "Good to read before starting any quiz session",
    ],
  },
  {
    icon: Layers,
    title: "Key Concepts",
    description: "The most important ideas extracted from your note, organized for reference.",
    bullets: [
      "Auto-extracted from your note content",
      "Used as the basis for quiz and exam questions across all modes",
      "Shown on your note detail page and Dashboard for quick reference",
    ],
  },
];

export function StudyPacksGuide() {
  return (
    <div className="space-y-4">
      {SECTIONS.map((section) => {
        const Icon = section.icon;
        return (
          <div key={section.title} className="flex items-start gap-3">
            <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-border bg-muted/40">
              <Icon className="h-4 w-4 text-foreground/60" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-foreground">{section.title}</p>
              <p className="mt-1 text-xs leading-relaxed text-foreground/65">{section.description}</p>
              {section.bullets ? (
                <ul className="mt-2 space-y-1">
                  {section.bullets.map((bullet) => (
                    <li key={bullet} className="flex gap-2 text-xs text-foreground/60">
                      <span className="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" aria-hidden="true" />
                      {bullet}
                    </li>
                  ))}
                </ul>
              ) : null}
              {section.cta ? (
                <Link
                  href={section.cta.href}
                  className="mt-2.5 inline-flex items-center gap-1 text-xs font-medium text-blue-600 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
                >
                  {section.cta.label}
                  <ArrowRight className="h-3 w-3" aria-hidden="true" />
                </Link>
              ) : null}
            </div>
          </div>
        );
      })}

      <p className="text-xs text-foreground/45">
        Tip: the more complete and structured your note, the better the summary and quiz questions will be.
      </p>
    </div>
  );
}
