import Link from "next/link";
import { ArrowRight, BookOpen, LineChart, Link2, Lock, Send } from "lucide-react";

/**
 * ⚠️ Every claim here must be live. This guide covers what shipped in v0.89.0–v0.91.0 only:
 * quiz links, connections, the supporter progress view, and shared notes.
 *
 * <p>Activity sharing and per-scope progress permissions are Phases 2 and 3 and are NOT built —
 * do not describe them here, and do not imply a guardian mode, a supporter profile type, or any
 * setting that decides what a connection can see beyond choosing who to share a note with.
 */
type Section = {
  icon: React.ElementType;
  title: string;
  description: string;
  bullets?: string[];
  cta?: { label: string; href: string };
};

const SECTIONS: Section[] = [
  {
    icon: Send,
    title: "Send someone a quiz — they need no account",
    description:
      "From a note's actions menu, choose Quiz for someone. NoteLib builds a quiz from that note and gives you a link. Whoever opens it answers in their browser and sees their score, without signing up or being connected to you.",
    bullets: [
      "Best for one-off help — a parent quizzing a child before a test, a friend checking a classmate",
      "Making the quiz spends a quiz generation from your monthly allowance",
      "Free plans can have 3 share links a month, Plus 10, Pro unlimited",
    ],
    cta: { label: "Open Library", href: "/library" },
  },
  {
    icon: Link2,
    title: "Connect with someone you help regularly",
    description:
      "Invite them by email address from Learning connections. They accept from their own account, and either of you can end the connection at any time.",
    bullets: [
      "Invitations are one at a time and expire after 30 days",
      "You can invite someone who has not signed up yet — the invitation waits for them",
      "Nothing is shared just because you are connected",
    ],
    cta: { label: "Learning connections", href: "/linked-learners" },
  },
  {
    icon: BookOpen,
    title: "Share a note and its Study Pack",
    description:
      "On any note, open Who can access this note? and choose Share with connections, then pick the people. The note stays private — it does not appear in Explore.",
    bullets: [
      "They open your Study Pack and study it normally: summary, key ideas, then practice",
      "Their practice counts toward their own progress, never yours",
      "They can keep their own copy with Copy to my Library",
      "Switching the note back to Private removes everyone's access",
    ],
  },
  {
    icon: LineChart,
    title: "Follow how they are doing",
    description:
      "Once a connection is accepted, People you support on your Dashboard leads to their progress: how ready they are, how often they study, and how their quizzes are going.",
    bullets: [
      "Ending the connection cuts your access to it immediately",
      "Looking at their progress never changes it",
    ],
    cta: { label: "Open Dashboard", href: "/dashboard" },
  },
  {
    icon: Lock,
    title: "What they never see, and what you never see",
    description:
      "Sharing a note shares the material and nothing else. Your mastery, quiz scores and practice history stay yours.",
    bullets: [
      "A note is shared only with the people you pick, one by one — never with everyone you are connected to",
      "You never see the notes someone writes, only how their preparation is moving",
      "If someone is under the age NoteLib treats as a minor, a guardian has to confirm the connection before it becomes active",
    ],
  },
];

export function LearningConnectionsGuide() {
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
