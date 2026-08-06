import type { QuizItem } from "@/lib/api";
import { resolveQuizCorrectIndex, sanitizeQuizChoiceText } from "@/lib/quiz";
import { cn } from "@/lib/utils";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

const CHOICE_LABELS = ["A", "B", "C", "D"] as const;

type QuizMatchingGroupProps = {
  items: QuizItem[];
  groupStartIndex: number;
  selectedChoices: Record<number, number>;
  onSelectChoice: (questionIndex: number, choiceIndex: number) => void;
  revealAnswer: boolean;
  selectionStyle?: "default" | "exam" | "board-exam";
  disabled?: boolean;
};

export function QuizMatchingGroup({
  items,
  groupStartIndex,
  selectedChoices,
  onSelectChoice,
  revealAnswer,
  selectionStyle = "default",
  disabled = false,
}: QuizMatchingGroupProps) {
  if (items.length === 0) {
    return null;
  }

  const choices = items[0].choices ?? [];

  return (
    <div className="space-y-4">
      <div className="space-y-2 rounded-md border border-border bg-muted/20 p-3">
        <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Match each item to one option</p>
        <ul className="grid gap-2 text-sm sm:grid-cols-2">
          {choices.map((choice, index) => (
            <li key={`${index}-${choice}`} className="flex gap-2 rounded-md border border-border bg-background px-3 py-2">
              <span className="font-semibold text-foreground">{choiceLabel(index)}.</span>
              <span className="text-foreground/75">{renderMathText(sanitizeQuizChoiceText(choice))}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="space-y-3">
        {items.map((item, itemOffset) => {
          const questionIndex = groupStartIndex + itemOffset;
          const selectedChoiceIndex = selectedChoices[questionIndex] ?? null;
          const correctIndex = resolveQuizCorrectIndex(item);

          return (
            <div key={`${questionIndex}-${item.question}`} className="space-y-2 rounded-md border border-border p-3">
              <p className="text-sm font-medium leading-relaxed text-foreground">
                {itemOffset + 1}. {renderMathText(item.question)}
              </p>
              <div className="grid grid-cols-4 gap-2" role="group" aria-label={`Matching answer for item ${itemOffset + 1}`}>
                {choices.map((choice, choiceIndex) => {
                  const isSelected = selectedChoiceIndex === choiceIndex;
                  const isCorrect = correctIndex === choiceIndex;
                  const isIncorrectSelection = revealAnswer && isSelected && !isCorrect;
                  const isInteractive = !revealAnswer && !disabled;
                  const statusLabel = revealAnswer && isCorrect
                    ? " Correct"
                    : isIncorrectSelection ? " Incorrect" : "";

                  return (
                    <button
                      key={`${questionIndex}-${choiceIndex}`}
                      type="button"
                      aria-label={`Item ${itemOffset + 1} choice ${choiceLabel(choiceIndex)}: ${sanitizeQuizChoiceText(choice)}${statusLabel}`}
                      aria-pressed={isSelected}
                      disabled={!isInteractive}
                      onClick={() => onSelectChoice(questionIndex, choiceIndex)}
                      className={cn(
                        "min-h-10 rounded-md border px-2 text-sm font-semibold transition-colors",
                        revealAnswer && isCorrect
                          ? "border-emerald-500/50 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
                          : isSelected
                            ? isIncorrectSelection
                              ? "border-red-500/50 bg-red-500/10 text-red-700 dark:text-red-300"
                              : selectionStyle === "board-exam"
                                ? "border-foreground/45 bg-foreground/[0.05] text-foreground ring-1 ring-foreground/15"
                                : selectionStyle === "exam"
                                  ? "border-blue-600/70 bg-blue-500/15 text-foreground ring-1 ring-blue-500/35"
                                  : "border-foreground/30 bg-muted/60 text-foreground"
                            : "border-border text-foreground/75",
                        isInteractive ? "cursor-pointer" : "cursor-default",
                      )}
                    >
                      {choiceLabel(choiceIndex)}
                      {revealAnswer && isCorrect ? <span className="sr-only"> Correct</span> : null}
                      {isIncorrectSelection ? <span className="sr-only"> Incorrect</span> : null}
                    </button>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function choiceLabel(index: number): string {
  return CHOICE_LABELS[index] ?? String.fromCharCode(65 + index);
}
