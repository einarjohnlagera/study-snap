import Link from "next/link";
import { Activity, ArrowRight, BookOpen, LineChart, Link2, Lock, Send } from "lucide-react";
import { pricingConfig } from "@/lib/pricing-config";

/**
 * ⚠️ Every claim here must be live. This guide covers what shipped in v0.89.0–v0.94.0: quiz links,
 * connections, shared notes, directional activity sharing, learner-controlled progress sharing, and
 * shareable single-use invitation links.
 *
 * <p>⚠️ Activity sharing shipped in v0.92.0 (Phase 2), and learner-granted PROGRESS permission shipped
 * in v0.93.0 (Phase 3). Activity can be shared in either direction; progress runs learner to supporter
 * only. Do not imply a guardian mode or a supporter profile type.
 *
 * <p>⚠️ v0.94.0 moved streaks and study days OUT of the progress view — they now need a separate
 * ACTIVITY grant. Do not describe the progress view as showing how often someone studies. This file
 * was NOT in that release's diff, so its sweep missed it and a cold-agent pressure test caught it;
 * check this guide against code whenever a sharing scope changes, not only when the file is touched.
 *
 * <p>⚠️ Plan numbers are imported from pricing-config, never retyped. They were hardcoded here until
 * v0.92.0 and would have drifted the moment a limit changed.
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
      "On a note with a ready Study Pack, choose Quiz for someone — teachers see the same action as Generate Quiz. NoteLib builds a quiz from that note and gives you a link. Whoever opens it answers in their browser and sees their score, without signing up or being connected to you.",
    bullets: [
      "Best for one-off help — a parent quizzing a child before a test, a friend checking a classmate",
      "Making the quiz spends a quiz generation from your monthly allowance",
      `Free plans can have ${pricingConfig.free.quizShareLinksPerMonth} share links a month, Plus ${pricingConfig.plus.quizShareLinksPerMonth}, Pro unlimited`,
    ],
    cta: { label: "Open Library", href: "/library" },
  },
  {
    icon: Link2,
    title: "Connect with someone you help regularly",
    description:
      "From Learning connections you can invite them by email address, or create a link and send it however you like. Either way they act from their own account, and either of you can end the connection at any time.",
    bullets: [
      "An email invitation names one address and expires after 30 days; you can see its clock and invite again once it lapses",
      "A link is single-use: the first person to open it sends you a request, and you confirm before the connection exists",
      "A connection request that is never confirmed expires after 30 days and its card shows the date — this includes a request still waiting on a guardian's permission, so record consent before then or send a new invitation",
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
      "After the learner turns on Share my study progress, People you support on your Dashboard leads to their progress: how ready they are, how far through their plans they are, and how their quizzes are going.",
    bullets: [
      "An accepted connection alone grants no progress access — the learner chooses whether to share it",
      "The learner can turn progress sharing off at any time; ending or pausing the connection also cuts access immediately",
      "Streaks and study days are NOT part of this view — they are a separate choice, Share my study activity",
      "Looking at their progress never changes it",
      "Supporters never receive the learner's notes or other authored study text",
    ],
    cta: { label: "Open Dashboard", href: "/dashboard" },
  },
  {
    icon: Activity,
    title: "Show someone you are studying",
    description:
      "On an accepted connection, turn on Share my study activity. They can then see your current streak, your longest streak, how many days you studied this week, and your study mode — and nothing else. It is off until you turn it on.",
    bullets: [
      "Connecting on its own shares nothing — this is a separate choice",
      "It works one way at a time: sharing yours does not make theirs visible to you. Whoever turned sharing on can turn it off, and either of you can end the connection",
      "They see streaks and study days, never your scores, your notes or what you studied",
      "Turning it off, or ending the connection, cuts their view immediately",
    ],
    cta: { label: "Learning connections", href: "/linked-learners" },
  },
  {
    icon: LineChart,
    title: "Choose whether a supporter sees your progress",
    description:
      "If you are the learner on a connection, turn on Share my study progress to let that supporter open your aggregate progress view. It is off until you choose to enable it.",
    bullets: [
      "Progress sharing is separate from study activity sharing — either can be on while the other is off",
      "Only the learner can grant progress access; the supporter cannot enable it for themselves",
      "You can turn it off even while a birth-year correction has paused the connection",
    ],
    cta: { label: "Learning connections", href: "/linked-learners" },
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
