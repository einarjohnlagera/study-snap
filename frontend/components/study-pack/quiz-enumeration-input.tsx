import type { QuizItem } from "@/lib/api";
import {
  isEnumerationAnswerCorrect,
  resolveEnumerationAcceptedAnswerGroups,
} from "@/lib/quiz";
import { cn } from "@/lib/utils";

type QuizEnumerationInputProps = {
  item: QuizItem;
  values: string[];
  revealAnswer: boolean;
  onChangeAnswers?: (values: string[]) => void;
  disabled?: boolean;
  selectionStyle?: "default" | "exam" | "board-exam";
};

export function QuizEnumerationInput({
  item,
  values,
  revealAnswer,
  onChangeAnswers,
  disabled = false,
  selectionStyle = "default",
}: QuizEnumerationInputProps) {
  const acceptedAnswerGroups = resolveEnumerationAcceptedAnswerGroups(item);
  const slotCount = acceptedAnswerGroups.length;
  const normalizedValues = Array.from({ length: slotCount }, (_, index) => values[index] ?? "");
  const hasAnyAnswer = normalizedValues.some((value) => value.trim().length > 0);
  const isCorrect = isEnumerationAnswerCorrect(item, normalizedValues);
  const isInteractive = Boolean(onChangeAnswers) && !revealAnswer && !disabled;

  const handleChange = (slotIndex: number, nextValue: string) => {
    const next = [...normalizedValues];
    next[slotIndex] = nextValue;
    onChangeAnswers?.(next);
  };

  return (
    <div className="space-y-2">
      {!revealAnswer ? (
        <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">
          Name all {slotCount} {slotCount === 1 ? "item" : "items"}
        </p>
      ) : null}
      <div
        className={cn(
          "space-y-2 rounded-md border px-3 py-3 transition-colors",
          revealAnswer
            ? isCorrect
              ? "border-emerald-500/50 bg-emerald-500/10"
              : hasAnyAnswer
                ? "border-red-500/50 bg-red-500/10"
                : "border-amber-500/40 bg-amber-500/10"
            : selectionStyle === "board-exam"
              ? "border-foreground/20 bg-background"
              : selectionStyle === "exam"
                ? "border-blue-600/35 bg-background"
                : "border-border bg-background",
        )}
      >
        {normalizedValues.map((value, slotIndex) => (
          <input
            key={slotIndex}
            type="text"
            value={value}
            disabled={!isInteractive}
            onChange={(event) => handleChange(slotIndex, event.target.value)}
            placeholder={`Item ${slotIndex + 1}`}
            className="min-h-10 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 disabled:cursor-default disabled:opacity-100"
            aria-label={`Enumeration answer ${slotIndex + 1} of ${slotCount}`}
          />
        ))}
        {revealAnswer ? (
          <div className="space-y-1 text-sm">
            <p
              className={cn(
                "font-medium",
                isCorrect
                  ? "text-emerald-700 dark:text-emerald-300"
                  : hasAnyAnswer
                    ? "text-red-700 dark:text-red-300"
                    : "text-amber-700 dark:text-amber-300",
              )}
            >
              {isCorrect ? "Correct" : hasAnyAnswer ? "Incorrect" : "No answer entered"}
            </p>
            <p className="break-words text-foreground/75">
              Accepted answers:{" "}
              {acceptedAnswerGroups.length > 0
                ? acceptedAnswerGroups.map((group) => group.join(" / ")).join("; ")
                : "Unavailable"}
            </p>
          </div>
        ) : null}
      </div>
    </div>
  );
}
