"use client";

import { useState } from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";

export type QuestionNavigatorTone = "challenge" | "long-exam" | "board-exam";

type QuestionNavigatorProps = {
  total: number;
  currentIndex: number;
  isAnswered: (index: number) => boolean;
  onSelect: (index: number) => void;
  summary: string;
  disabled?: boolean;
  defaultCollapsed?: boolean;
  tone?: QuestionNavigatorTone;
  testId?: string;
  disclosureTestId?: string;
};

export function QuestionNavigator({
  total,
  currentIndex,
  isAnswered,
  onSelect,
  summary,
  disabled = false,
  defaultCollapsed = true,
  tone = "challenge",
  testId,
  disclosureTestId,
}: QuestionNavigatorProps) {
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  const isExamTone = tone === "board-exam" || tone === "long-exam";
  const wrapperClass = isExamTone
    ? "border-foreground/15 bg-muted/10"
    : "border-border bg-background";
  const currentClass = isExamTone
    ? "border-foreground/40 bg-foreground/6 text-foreground"
    : "border-blue-500 bg-blue-500/10 text-blue-700 dark:text-blue-300";

  return (
    <div
      data-testid={testId}
      data-tone={tone}
      className={cn("rounded-md border p-3", wrapperClass)}
    >
      <button
        type="button"
        className="motion-pressable flex w-full items-center justify-between gap-3 rounded-md text-left"
        onClick={() => setIsCollapsed((current) => !current)}
        aria-expanded={!isCollapsed}
        aria-controls={disclosureTestId ?? "question-navigator-grid"}
      >
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
            Question Navigator
          </p>
          <p key={currentIndex} className="text-sm text-foreground/75">
            {summary}
          </p>
        </div>
        <ChevronDown
          className={cn(
            "h-4 w-4 shrink-0 text-foreground/65 transition-transform",
            !isCollapsed && "rotate-180",
          )}
          aria-hidden="true"
        />
      </button>
      <div
        id={disclosureTestId ?? "question-navigator-grid"}
        data-testid={disclosureTestId}
        className="motion-collapse mt-3"
        data-state={isCollapsed ? "collapsed" : "expanded"}
        aria-hidden={isCollapsed}
      >
        <div className="motion-collapse-inner">
          <div className="grid grid-cols-5 gap-2 sm:grid-cols-8" aria-label="Question navigator">
            {Array.from({ length: total }, (_, index) => {
              const isCurrent = index === currentIndex;
              const answered = isAnswered(index);
              return (
                <button
                  key={index}
                  type="button"
                  tabIndex={isCollapsed ? -1 : 0}
                  aria-label={`Go to question ${index + 1}${answered ? " (answered)" : " (unanswered)"}`}
                  aria-current={isCurrent ? "step" : undefined}
                  className={cn(
                    "motion-pressable rounded-md border px-2 py-1.5 text-sm font-medium",
                    isCurrent
                      ? currentClass
                      : answered
                        ? "border-foreground/30 bg-muted/60 text-foreground"
                        : "border-border bg-background text-foreground/70",
                  )}
                  onClick={() => onSelect(index)}
                  disabled={disabled}
                >
                  {index + 1}
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
