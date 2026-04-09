import { cn } from "@/lib/utils";
import { getDisplayedQuizChoices } from "@/lib/quiz";

type QuizChoiceListProps = {
  questionKey: string;
  choices: string[];
  correctIndex: number;
  selectedChoiceIndex?: number | null;
  revealAnswer: boolean;
  onSelectChoice?: (choiceIndex: number) => void;
  selectionStyle?: "default" | "exam" | "board-exam";
};

export function QuizChoiceList({
  questionKey,
  choices,
  correctIndex,
  selectedChoiceIndex = null,
  revealAnswer,
  onSelectChoice,
  selectionStyle = "default",
}: QuizChoiceListProps) {
  if (choices.length === 0) {
    return null;
  }

  const displayedChoices = getDisplayedQuizChoices({
    question: questionKey,
    choices,
    correctIndex,
    explanation: "",
  });

  return (
    <ul className="space-y-2 text-sm">
      {displayedChoices.map((choice) => {
        const isCorrect = choice.canonicalIndex === correctIndex;
        const isSelected = choice.canonicalIndex === selectedChoiceIndex;
        const isIncorrectSelection = revealAnswer && isSelected && !isCorrect;
        const isInteractive = Boolean(onSelectChoice) && !revealAnswer;

        return (
          <li key={`${choice.label}-${choice.canonicalIndex}-${choice.text}`}>
            <button
              type="button"
              disabled={!isInteractive}
              onClick={() => onSelectChoice?.(choice.canonicalIndex)}
              className={cn(
                "min-h-11 w-full rounded-md border px-3 py-2 text-left text-sm leading-relaxed whitespace-normal break-words transition-colors",
                revealAnswer && isCorrect
                  ? "border-emerald-500/50 bg-emerald-500/10 text-foreground"
                  : isSelected
                    ? isIncorrectSelection
                      ? "border-red-500/50 bg-red-500/10 text-foreground"
                      : selectionStyle === "board-exam"
                        ? "border-foreground/45 bg-foreground/[0.05] text-foreground ring-1 ring-foreground/15"
                        : selectionStyle === "exam"
                          ? "border-blue-600/70 bg-blue-500/15 text-foreground ring-1 ring-blue-500/35"
                          : "border-foreground/30 bg-muted/60 text-foreground"
                    : "border-border text-foreground/75",
                isInteractive ? "cursor-pointer" : "cursor-default",
              )}
            >
              <span className="mr-2 font-semibold text-foreground">{choice.label}.</span>
              <span>{choice.text}</span>
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
