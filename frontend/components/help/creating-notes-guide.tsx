import Link from "next/link";
import { ArrowRight, Copy, FileText, Lock, Tag } from "lucide-react";

type Section = {
  icon: React.ElementType;
  title: string;
  description: string;
  bullets?: string[];
  cta?: { label: string; href: string };
};

const SECTIONS: Section[] = [
  {
    icon: FileText,
    title: "What to put in a note",
    description: "Paste or type the material you want to study from. The more complete the content, the better the generated summary and quiz.",
    bullets: [
      "Lecture notes and class transcripts",
      "Textbook excerpts and chapter summaries",
      "Reviewer material and topic outlines",
    ],
    cta: { label: "Create Note", href: "/notes/new" },
  },
  {
    icon: Tag,
    title: "Subject and Course / Program",
    description: "These two fields help NoteLib work better for you.",
    bullets: [
      "Subject — organizes your Library and lets you filter notes by topic",
      "Course / Program — tailors generated content and recommendations to your field of study",
    ],
  },
  {
    icon: Lock,
    title: "Editing after a Study Pack is generated",
    description: "Note content is locked once a Study Pack is generated to keep the study material consistent. You can still update the title, subject, course/program, and tags.",
    bullets: [
      "To improve the content, use Make a Copy",
      "The copy is a new draft — edit freely and generate a fresh Study Pack",
    ],
    cta: { label: "Open Library", href: "/library" },
  },
  {
    icon: Copy,
    title: "Make a Copy",
    description: "Copies carry over the title, subject, course/program, tags, and note content. They do not include the Study Pack, session history, or performance data — the copy starts clean.",
  },
];

export function CreatingNotesGuide() {
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
    </div>
  );
}
