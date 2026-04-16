import Link from "next/link";
import { AlignLeft, ArrowRight, Brain, Layers, Target, Timer, Zap } from "lucide-react";

type Section = {
  icon: React.ElementType;
  title: string;
  description: string;
  bullets?: string[];
  badge?: string;
  cta?: { label: string; href: string };
};

const SECTIONS: Section[] = [
  {
    icon: AlignLeft,
    title: "Summary",
    description: "An AI-generated overview of your note condensed into the key points — useful for a quick refresh before a quiz.",
  },
  {
    icon: Layers,
    title: "Key Concepts",
    description: "The most important ideas extracted from your note, organized for reference and fast review.",
  },
  {
    icon: Zap,
    title: "Quick Review",
    description: "A low-pressure session using your saved practice questions.",
    bullets: [
      "Questions you miss are shown again in a retry round",
      "Good for warming up or reinforcing recent material",
    ],
    cta: { label: "Open Library", href: "/library" },
  },
  {
    icon: Timer,
    title: "Challenge Quiz",
    description: "Generates new timed questions to simulate test conditions.",
    bullets: [
      "Three difficulty levels: Easy, Medium, Hard",
      "Tracks your best score per note",
      "Identifies Weak Concepts for follow-up",
    ],
    cta: { label: "Open Library", href: "/library" },
  },
  {
    icon: Target,
    title: "Adaptive Practice",
    description: "Focuses on concepts you missed in Challenge Quiz, targeting weak areas with questions built from recent performance.",
    bullets: [
      "Available on the Premium plan",
      "Works best after completing at least one Challenge Quiz",
    ],
    badge: "Premium",
  },
  {
    icon: Brain,
    title: "Weak Concepts",
    description: "Topics where your Challenge Quiz accuracy fell below 60%. They appear on your Dashboard and note detail page to guide your next study session.",
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
              <div className="flex items-center gap-2">
                <p className="text-sm font-semibold text-foreground">{section.title}</p>
                {section.badge ? (
                  <span className="inline-flex items-center rounded-full border border-border bg-muted/40 px-2 py-0.5 text-[10px] font-medium text-foreground/60">
                    {section.badge}
                  </span>
                ) : null}
              </div>
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
    </div>
  );
}
