import { cn } from "@/lib/utils";

type QuizChoiceListProps = {
  choices: string[];
  correctAnswer: string;
  selectedChoice?: string | null;
  revealAnswer: boolean;
  onSelectChoice?: (choice: string) => void;
};

export function QuizChoiceList({
  choices,
  correctAnswer,
  selectedChoice = null,
  revealAnswer,
  onSelectChoice,
}: QuizChoiceListProps) {
  if (choices.length === 0) {
    return null;
  }

  return (
    <ul className="space-y-2 text-sm">
      {choices.map((choice) => {
        const isCorrect = choice === correctAnswer;
        const isSelected = choice === selectedChoice;
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
                    ? "border-blue-500/40 bg-blue-500/10 text-foreground"
                    : "border-border text-foreground/75",
                isInteractive ? "cursor-pointer" : "cursor-default",
              )}
            >
              {choice}
              {revealAnswer && isCorrect ? (
                <span className="ml-2 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                  Correct answer
                </span>
              ) : null}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
