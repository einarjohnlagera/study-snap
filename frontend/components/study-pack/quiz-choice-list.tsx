import { cn } from "@/lib/utils";
import { getDisplayedQuizChoices } from "@/lib/quiz";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

type QuizChoiceListProps = {
  questionKey: string;
  choices: string[];
  correctIndex: number;
  correctIndices?: number[] | null;
  questionFormat?: string | null;
  selectedChoiceIndex?: number | null;
  selectedMultiChoiceIndices?: number[] | null;
  revealAnswer: boolean;
  onSelectChoice?: (choiceIndex: number) => void;
  onSelectMultiChoices?: (indices: number[]) => void;
  selectionStyle?: "default" | "exam" | "board-exam";
  disabled?: boolean;
};

export function QuizChoiceList({
  questionKey,
  choices,
  correctIndex,
  correctIndices = null,
  questionFormat = null,
  selectedChoiceIndex = null,
  selectedMultiChoiceIndices = null,
  revealAnswer,
  onSelectChoice,
  onSelectMultiChoices,
  selectionStyle = "default",
  disabled = false,
}: QuizChoiceListProps) {
  if (choices.length === 0) {
    return null;
  }

  const displayedChoices = getDisplayedQuizChoices({
    question: questionKey,
    choices,
    correctIndex,
    correctIndices,
    questionFormat: questionFormat === "MULTI_SELECT" ? "MULTI_SELECT" : questionFormat === "TRUE_FALSE" ? "TRUE_FALSE" : "MCQ",
    explanation: "",
  });
  const isMultiSelect = questionFormat === "MULTI_SELECT";
  const selectedMultiChoiceSet = new Set(selectedMultiChoiceIndices ?? []);
  const correctMultiChoiceSet = new Set(correctIndices ?? []);

  return (
    <div className="space-y-2">
      {isMultiSelect && !revealAnswer ? (
        <p className="text-xs font-medium uppercase tracking-wide text-foreground/60">Select all that apply</p>
      ) : null}
      <ul className="space-y-2 text-sm">
        {displayedChoices.map((choice) => {
        const isCorrect = isMultiSelect ? correctMultiChoiceSet.has(choice.canonicalIndex) : choice.canonicalIndex === correctIndex;
        const isSelected = isMultiSelect ? selectedMultiChoiceSet.has(choice.canonicalIndex) : choice.canonicalIndex === selectedChoiceIndex;
        const isIncorrectSelection = revealAnswer && isSelected && !isCorrect;
        const isInteractive = Boolean(isMultiSelect ? onSelectMultiChoices : onSelectChoice) && !revealAnswer && !disabled;
        const nextMultiSelectChoices = () => {
          const next = new Set(selectedMultiChoiceSet);
          if (next.has(choice.canonicalIndex)) {
            next.delete(choice.canonicalIndex);
          } else {
            next.add(choice.canonicalIndex);
          }
          return Array.from(next).sort((a, b) => a - b);
        };

        return (
          <li key={`${choice.label}-${choice.canonicalIndex}-${choice.text}`}>
            <button
              type="button"
              disabled={!isInteractive}
              aria-pressed={isSelected}
              onClick={() => {
                if (isMultiSelect) {
                  onSelectMultiChoices?.(nextMultiSelectChoices());
                  return;
                }
                onSelectChoice?.(choice.canonicalIndex);
              }}
              className={cn(
                "flex min-h-11 w-full items-start gap-2 rounded-md border px-3 py-2 text-left text-sm leading-relaxed whitespace-normal break-words transition-colors",
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
              {isMultiSelect ? (
                <span
                  className={cn(
                    "mt-1 flex h-4 w-4 shrink-0 items-center justify-center rounded-[3px] border text-[10px] leading-none",
                    isSelected
                      ? "border-blue-600 bg-blue-600 text-white"
                      : "border-foreground/35 bg-background text-transparent",
                    revealAnswer && isCorrect ? "border-emerald-600 bg-emerald-600 text-white" : null,
                    isIncorrectSelection ? "border-red-600 bg-red-600 text-white" : null,
                  )}
                  aria-hidden="true"
                >
                  {isSelected || (revealAnswer && isCorrect) ? "✓" : ""}
                </span>
              ) : null}
              <span className="min-w-0 flex-1">
                <span className="mr-2 font-semibold text-foreground">{choice.label}.</span>
                {/* Options carry LaTeX for the same reason questions do -- see quiz-question-text. */}
                <span>{renderMathText(choice.text)}</span>
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
              </span>
            </button>
          </li>
        );
      })}
      </ul>
    </div>
  );
}
