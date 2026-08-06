import type { QuizItem } from "@/lib/api";
import {
  isIdentificationAnswerCorrect,
  resolveIdentificationAcceptedAnswers,
} from "@/lib/quiz";
import { cn } from "@/lib/utils";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

type QuizIdentificationInputProps = {
  item: QuizItem;
  value: string;
  revealAnswer: boolean;
  onChangeAnswer?: (value: string) => void;
  disabled?: boolean;
  selectionStyle?: "default" | "exam" | "board-exam";
};

export function QuizIdentificationInput({
  item,
  value,
  revealAnswer,
  onChangeAnswer,
  disabled = false,
  selectionStyle = "default",
}: QuizIdentificationInputProps) {
  const acceptedAnswers = resolveIdentificationAcceptedAnswers(item);
  const isCorrect = isIdentificationAnswerCorrect(item, value);
  const hasAnswer = value.trim().length > 0;
  const isInteractive = Boolean(onChangeAnswer) && !revealAnswer && !disabled;

  return (
    <div className="space-y-2">
      {!revealAnswer ? (
        <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Type your answer</p>
      ) : null}
      <div
        className={cn(
          "rounded-md border px-3 py-3 transition-colors",
          revealAnswer
            ? isCorrect
              ? "border-emerald-500/50 bg-emerald-500/10"
              : hasAnswer
                ? "border-red-500/50 bg-red-500/10"
                : "border-amber-500/40 bg-amber-500/10"
            : selectionStyle === "board-exam"
              ? "border-foreground/20 bg-background"
              : selectionStyle === "exam"
                ? "border-blue-600/35 bg-background"
                : "border-border bg-background",
        )}
      >
        <input
          type="text"
          value={value}
          disabled={!isInteractive}
          onChange={(event) => onChangeAnswer?.(event.target.value)}
          placeholder="Enter the term or answer"
          className="min-h-10 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 disabled:cursor-default disabled:opacity-100"
          aria-label="Identification answer"
        />
        {revealAnswer ? (
          <div className="mt-3 space-y-1 text-sm">
            <p
              className={cn(
                "font-medium",
                isCorrect
                  ? "text-emerald-700 dark:text-emerald-300"
                  : hasAnswer
                    ? "text-red-700 dark:text-red-300"
                    : "text-amber-700 dark:text-amber-300",
              )}
            >
              {isCorrect ? "Correct" : hasAnswer ? "Incorrect" : "No answer entered"}
            </p>
            <p className="break-words text-foreground/75">
              Accepted answer{acceptedAnswers.length === 1 ? "" : "s"}: {acceptedAnswers.length > 0 ? renderMathText(acceptedAnswers.join("; ")) : "Unavailable"}
            </p>
          </div>
        ) : null}
      </div>
    </div>
  );
}
