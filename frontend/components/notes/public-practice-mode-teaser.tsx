import { Clock, Target, Trophy } from "lucide-react";
import { Card } from "@/components/ui/card";

const PRACTICE_MODES = [
  {
    icon: Trophy,
    name: "Challenge Quiz",
    description: "Answer all questions first, then see feedback at the end — the way real exams work.",
    pro: false,
  },
  {
    icon: Target,
    name: "Adaptive Practice",
    description: "Focuses each session on what you got wrong, not what you already know.",
    pro: false,
  },
  {
    icon: Clock,
    name: "Board Exam Mode",
    description: "Full timed exam with all questions, no pausing, and results at the end. Built for board exam prep.",
    pro: true,
  },
] as const;

export function PublicPracticeModeTeaser() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <h2 className="text-base font-semibold sm:text-lg">Practice modes available when you copy this note</h2>
        <p className="text-sm text-foreground/65">
          Copy this note into your library to unlock focused, exam-style practice sessions.
        </p>
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        {PRACTICE_MODES.map((mode) => (
          <div key={mode.name} className="space-y-2 rounded-xl border border-border bg-background p-4">
            <div className="flex flex-wrap items-center gap-2">
              <mode.icon className="h-4 w-4 shrink-0 text-foreground/55" aria-hidden="true" />
              <span className="text-sm font-medium text-foreground">{mode.name}</span>
              {mode.pro ? (
                <span className="inline-flex items-center rounded-full border border-amber-500/20 bg-amber-500/10 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
                  Pro
                </span>
              ) : null}
            </div>
            <p className="text-xs leading-relaxed text-foreground/65">{mode.description}</p>
          </div>
        ))}
      </div>
    </Card>
  );
}
