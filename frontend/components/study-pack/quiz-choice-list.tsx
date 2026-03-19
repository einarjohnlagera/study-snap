import { cn } from "@/lib/utils";

type QuizChoiceListProps = {
  choices: string[];
  correctAnswer: string;
  selectedChoice?: string | null;
  revealAnswer: boolean;
  onSelectChoice?: (choice: string) => void;
  selectionStyle?: "default" | "exam";
};

export function QuizChoiceList({
  choices,
  correctAnswer,
  selectedChoice = null,
  revealAnswer,
  onSelectChoice,
  selectionStyle = "default",
}: QuizChoiceListProps) {
  if (choices.length === 0) {
    return null;
  }

  return (
    <ul className="space-y-2 text-sm">
      {choices.map((choice) => {
        const isCorrect = choice === correctAnswer;
        const isSelected = choice === selectedChoice;
        const isIncorrectSelection = revealAnswer && isSelected && !isCorrect;
        const isInteractive = Boolean(onSelectChoice) && !revealAnswer;

        return (
          <li key={choice}>
            <button
              type="button"
              disabled={!isInteractive}
              onClick={() => onSelectChoice?.(choice)}
              className={cn(
                "min-h-11 w-full rounded-md border px-3 py-2 text-left text-sm leading-relaxed whitespace-normal break-words transition-colors",
                revealAnswer && isCorrect
                  ? "border-emerald-500/50 bg-emerald-500/10 text-foreground"
                  : isSelected
                    ? isIncorrectSelection
                      ? "border-red-500/50 bg-red-500/10 text-foreground"
                      : selectionStyle === "exam"
                        ? "border-blue-600/70 bg-blue-500/15 text-foreground ring-1 ring-blue-500/35"
                        : "border-foreground/30 bg-muted/60 text-foreground"
                    : "border-border text-foreground/75",
                isInteractive ? "cursor-pointer" : "cursor-default",
              )}
            >
              {choice}
              {revealAnswer && isCorrect ? (
                <span className="ml-2 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                  ✓ Correct
                </span>
              ) : null}
              {isIncorrectSelection ? (
                <span className="ml-2 text-xs font-medium text-red-700 dark:text-red-300">
                  ✗ Incorrect
                </span>
              ) : null}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
