import type { QuizItem } from "@/lib/api";

const CHOICE_LABELS = ["A", "B", "C", "D"] as const;

export type QuizDisplayChoice = {
  displayIndex: number;
  canonicalIndex: number;
  label: string;
  text: string;
};

export function resolveQuizCorrectIndex(item: QuizItem): number {
  if (Number.isInteger(item.correctIndex) && item.correctIndex >= 0 && item.correctIndex < item.choices.length) {
    return item.correctIndex;
  }
  const legacyAnswerIndex = item.answerIndex;
  if (
    typeof legacyAnswerIndex === "number"
    && Number.isInteger(legacyAnswerIndex)
    && legacyAnswerIndex >= 0
    && legacyAnswerIndex < item.choices.length
  ) {
    return legacyAnswerIndex;
  }
  const legacyCorrectAnswerIndex = item.correctAnswerIndex;
  if (
    typeof legacyCorrectAnswerIndex === "number"
    && Number.isInteger(legacyCorrectAnswerIndex)
    && legacyCorrectAnswerIndex >= 0
    && legacyCorrectAnswerIndex < item.choices.length
  ) {
    return legacyCorrectAnswerIndex;
  }
  if (typeof item.answer === "string") {
    const fallbackIndex = item.choices.findIndex((choice) => choice === item.answer);
    if (fallbackIndex >= 0) {
      return fallbackIndex;
    }
  }
  return -1;
}

export function resolveQuizCorrectAnswer(item: QuizItem): string | null {
  const correctIndex = resolveQuizCorrectIndex(item);
  return correctIndex >= 0 && correctIndex < item.choices.length ? item.choices[correctIndex] : null;
}

export function isQuizSelectionCorrect(item: QuizItem, selectedChoiceIndex: number | null | undefined): boolean {
  return selectedChoiceIndex != null && selectedChoiceIndex === resolveQuizCorrectIndex(item);
}

export function toSelectedChoiceIndexRecord(value: unknown, quiz: QuizItem[]): Record<number, number> {
  if (!value || typeof value !== "object") {
    return {};
  }

  const selectedChoices: Record<number, number> = {};
  for (const [rawKey, rawValue] of Object.entries(value as Record<string, unknown>)) {
    const questionIndex = Number(rawKey);
    if (!Number.isInteger(questionIndex) || questionIndex < 0 || questionIndex >= quiz.length) {
      continue;
    }

    const choiceIndex = resolveSelectedChoiceIndex(rawValue, quiz[questionIndex]);
    if (choiceIndex !== null) {
      selectedChoices[questionIndex] = choiceIndex;
    }
  }

  return selectedChoices;
}

export function serializeSelectedChoiceIndexRecord(value: Record<number, number>): Record<string, number> {
  return Object.fromEntries(
    Object.entries(value)
      .filter(([, selectedChoiceIndex]) => Number.isInteger(selectedChoiceIndex) && selectedChoiceIndex >= 0)
      .map(([questionIndex, selectedChoiceIndex]) => [String(questionIndex), selectedChoiceIndex]),
  );
}

export function getDisplayedQuizChoices(item: QuizItem): QuizDisplayChoice[] {
  const order = buildDeterministicChoiceOrder(item.question, item.choices);
  return order.map((canonicalIndex, displayIndex) => ({
    displayIndex,
    canonicalIndex,
    label: CHOICE_LABELS[displayIndex] ?? String.fromCharCode(65 + displayIndex),
    text: item.choices[canonicalIndex] ?? "",
  }));
}

function resolveSelectedChoiceIndex(rawValue: unknown, item: QuizItem): number | null {
  if (typeof rawValue === "number" && Number.isInteger(rawValue) && rawValue >= 0 && rawValue < item.choices.length) {
    return rawValue;
  }
  if (typeof rawValue === "string") {
    const matchedIndex = item.choices.findIndex((choice) => choice === rawValue);
    return matchedIndex >= 0 ? matchedIndex : null;
  }
  return null;
}

function buildDeterministicChoiceOrder(question: string, choices: string[]): number[] {
  const indexes = choices.map((_, index) => index);
  if (indexes.length < 2) {
    return indexes;
  }

  let state = hashString(`${question}::${choices.join("\u241f")}`) || 1;
  for (let index = indexes.length - 1; index > 0; index -= 1) {
    state = (state * 1664525 + 1013904223) >>> 0;
    const swapIndex = state % (index + 1);
    [indexes[index], indexes[swapIndex]] = [indexes[swapIndex], indexes[index]];
  }

  return indexes;
}

function hashString(value: string): number {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}
