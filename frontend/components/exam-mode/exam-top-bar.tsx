"use client";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export type ExamTimerState = "normal" | "warning" | "urgent" | "expired";
export type ExamTopBarTone = "long-exam" | "board-exam";

type ExamTopBarProps = {
  modeLabel: string;
  leaveLabel: string;
  onLeave: () => void;
  leaveDisabled?: boolean;
  remainingSeconds: number;
  timerState: ExamTimerState;
  tone?: ExamTopBarTone;
  testId?: string;
  timerTestId?: string;
};

function formatTimer(seconds: number): string {
  const safeSeconds = Math.max(0, seconds);
  const minutes = Math.floor(safeSeconds / 60);
  const remaining = safeSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(remaining).padStart(2, "0")}`;
}

export function ExamTopBar({
  modeLabel,
  leaveLabel,
  onLeave,
  leaveDisabled,
  remainingSeconds,
  timerState,
  tone = "long-exam",
  testId,
  timerTestId,
}: ExamTopBarProps) {
  return (
    <div
      data-testid={testId}
      data-tone={tone}
      className="sticky top-0 z-20 -mx-4 flex items-center justify-between gap-3 border-b border-border bg-background/95 px-4 py-4 backdrop-blur sm:mx-0 sm:rounded-xl sm:border sm:px-6"
    >
      <div className="flex min-w-0 items-center gap-3 sm:gap-4">
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="shrink-0 px-3"
          onClick={onLeave}
          disabled={leaveDisabled}
        >
          {leaveLabel}
        </Button>
        <span className="hidden truncate text-xs font-medium uppercase tracking-[0.18em] text-foreground/55 sm:inline">
          {modeLabel}
        </span>
      </div>
      <div className="flex flex-col items-end gap-0.5 text-right">
        <span className="text-[10px] font-medium uppercase tracking-[0.18em] text-foreground/50">
          Time remaining
        </span>
        <span
          data-testid={timerTestId}
          data-timer-state={timerState}
          aria-label="Exam timer"
          className={cn(
            "text-xl font-semibold tabular-nums leading-none sm:text-2xl",
            timerState === "normal" && "text-foreground",
            timerState === "warning" && "text-amber-700 dark:text-amber-300",
            (timerState === "urgent" || timerState === "expired") &&
              "text-red-700 dark:text-red-300",
          )}
        >
          {formatTimer(remainingSeconds)}
        </span>
      </div>
    </div>
  );
}
