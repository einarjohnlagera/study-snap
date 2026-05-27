import type { QuizItem } from "@/lib/api";

const CHOICE_LABELS = ["A", "B", "C", "D"] as const;
const LEADING_CHOICE_LABEL_PATTERN = /^\s*[A-D]\s*[.)]\s*/i;

export type QuizDisplayChoice = {
  displayIndex: number;
  canonicalIndex: number;
  label: string;
  text: string;
};

export type GroupedQuizItem = QuizItem | QuizItem[];
export type QuizItemGroup = {
  items: QuizItem[];
  startIndex: number;
  endIndex: number;
};

export function groupQuizItems(quiz: QuizItem[]): GroupedQuizItem[] {
  const groupedItems: GroupedQuizItem[] = [];
  let index = 0;
  while (index < quiz.length) {
    const item = quiz[index];
    const questionGroup = resolveMatchingQuestionGroup(item);
    if (questionGroup) {
      const groupItems: QuizItem[] = [item];
      let nextIndex = index + 1;
      while (nextIndex < quiz.length && resolveMatchingQuestionGroup(quiz[nextIndex]) === questionGroup) {
        groupItems.push(quiz[nextIndex]);
        nextIndex += 1;
      }
      if (groupItems.length > 1) {
        groupedItems.push(groupItems);
        index = nextIndex;
        continue;
      }
    }
    groupedItems.push(item);
    index += 1;
  }
  return groupedItems;
}

export function resolveQuizItemGroupAt(quiz: QuizItem[], questionIndex: number): QuizItemGroup | null {
  if (!Number.isInteger(questionIndex) || questionIndex < 0 || questionIndex >= quiz.length) {
    return null;
  }
  let startIndex = 0;
  for (const groupedItem of groupQuizItems(quiz)) {
    const length = Array.isArray(groupedItem) ? groupedItem.length : 1;
    const endIndex = startIndex + length - 1;
    if (questionIndex >= startIndex && questionIndex <= endIndex) {
      return Array.isArray(groupedItem) ? { items: groupedItem, startIndex, endIndex } : null;
    }
    startIndex += length;
  }
  return null;
}

export function resolveQuizCorrectIndex(item: QuizItem): number {
  if (Number.isInteger(item.correctIndex) && item.correctIndex >= 0 && item.correctIndex < item.choices.length) {
    return item.correctIndex;
  }
  if (item.questionFormat === "MULTI_SELECT") {
    const firstCorrectIndex = resolveMultiSelectCorrectIndices(item)[0];
    if (Number.isInteger(firstCorrectIndex)) {
      return firstCorrectIndex;
    }
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
    const normalizedAnswer = sanitizeQuizChoiceText(item.answer);
    const fallbackIndex = item.choices.findIndex((choice) => sanitizeQuizChoiceText(choice) === normalizedAnswer);
    if (fallbackIndex >= 0) {
      return fallbackIndex;
    }
    const letterIndex = resolveChoiceLetterIndex(normalizedAnswer || item.answer, item.choices.length);
    if (letterIndex !== null) {
      return letterIndex;
    }
  }
  return -1;
}

export function resolveQuizCorrectAnswer(item: QuizItem): string | null {
  const correctIndex = resolveQuizCorrectIndex(item);
  return correctIndex >= 0 && correctIndex < item.choices.length ? item.choices[correctIndex] : null;
}

export function resolveMultiSelectCorrectIndices(item: QuizItem): number[] {
  if (!Array.isArray(item.correctIndices) || item.correctIndices.length === 0) {
    return [];
  }
  return normalizeChoiceIndexSet(item.correctIndices, item.choices.length);
}

export function isMultiSelectSelectionCorrect(item: QuizItem, selectedIndices: number[]): boolean {
  const correctIndices = resolveMultiSelectCorrectIndices(item);
  const selected = normalizeChoiceIndexSet(selectedIndices, item.choices.length);
  return selected.length > 0
    && selected.length === correctIndices.length
    && selected.every((index, position) => index === correctIndices[position]);
}

export function isQuizSelectionCorrect(
  item: QuizItem,
  selectedChoiceIndex: number | number[] | null | undefined,
): boolean {
  if (item.questionFormat === "MULTI_SELECT") {
    return Array.isArray(selectedChoiceIndex)
      ? isMultiSelectSelectionCorrect(item, selectedChoiceIndex)
      : false;
  }
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

export function toSelectedMultiChoiceIndicesRecord(value: unknown, quiz: QuizItem[]): Record<number, number[]> {
  if (!value || typeof value !== "object") {
    return {};
  }

  const selectedChoices: Record<number, number[]> = {};
  for (const [rawKey, rawValue] of Object.entries(value as Record<string, unknown>)) {
    const questionIndex = Number(rawKey);
    if (!Number.isInteger(questionIndex) || questionIndex < 0 || questionIndex >= quiz.length) {
      continue;
    }

    const selectedIndexes = resolveSelectedChoiceIndexes(rawValue, quiz[questionIndex]);
    if (selectedIndexes.length > 0) {
      selectedChoices[questionIndex] = selectedIndexes;
    }
  }

  return selectedChoices;
}

export function serializeSelectedMultiChoiceIndicesRecord(value: Record<number, number[]>): Record<string, number[]> {
  return Object.fromEntries(
    Object.entries(value)
      .map(([questionIndex, selectedChoiceIndices]) => [
        String(questionIndex),
        normalizeChoiceIndexSet(selectedChoiceIndices, Number.POSITIVE_INFINITY),
      ])
      .filter(([, selectedChoiceIndices]) => Array.isArray(selectedChoiceIndices) && selectedChoiceIndices.length > 0),
  );
}

export function getDisplayedQuizChoices(item: QuizItem): QuizDisplayChoice[] {
  if (item.questionFormat === "TRUE_FALSE") {
    return item.choices.map((choice, index) => ({
      displayIndex: index,
      canonicalIndex: index,
      label: CHOICE_LABELS[index] ?? String.fromCharCode(65 + index),
      text: sanitizeQuizChoiceText(choice),
    }));
  }

  const order = buildDeterministicChoiceOrder(item.question, item.choices);
  return order.map((canonicalIndex, displayIndex) => ({
    displayIndex,
    canonicalIndex,
    label: CHOICE_LABELS[displayIndex] ?? String.fromCharCode(65 + displayIndex),
    text: sanitizeQuizChoiceText(item.choices[canonicalIndex] ?? ""),
  }));
}

export function sanitizeQuizChoiceText(value: string): string {
  return value.replace(LEADING_CHOICE_LABEL_PATTERN, "").replace(/\s+/g, " ").trim();
}

function resolveSelectedChoiceIndex(rawValue: unknown, item: QuizItem): number | null {
  if (typeof rawValue === "number" && Number.isInteger(rawValue) && rawValue >= 0 && rawValue < item.choices.length) {
    return rawValue;
  }
  if (typeof rawValue === "string") {
    const normalizedSelectedChoice = sanitizeQuizChoiceText(rawValue);
    const matchedIndex = item.choices.findIndex((choice) => sanitizeQuizChoiceText(choice) === normalizedSelectedChoice);
    if (matchedIndex >= 0) {
      return matchedIndex;
    }
    return resolveChoiceLetterIndex(normalizedSelectedChoice || rawValue, item.choices.length);
  }
  return null;
}

function resolveSelectedChoiceIndexes(rawValue: unknown, item: QuizItem): number[] {
  if (!Array.isArray(rawValue)) {
    return [];
  }
  const indexes = rawValue
    .map((value) => resolveSelectedChoiceIndex(value, item))
    .filter((value): value is number => value !== null);
  return normalizeChoiceIndexSet(indexes, item.choices.length);
}

function normalizeChoiceIndexSet(values: number[], choiceCount: number): number[] {
  return Array.from(new Set(values))
    .filter((index) => Number.isInteger(index) && index >= 0 && index < choiceCount)
    .sort((a, b) => a - b);
}

function resolveChoiceLetterIndex(value: string, choiceCount: number): number | null {
  const normalizedValue = value.trim().replace(/^([A-D])[.)]$/i, "$1");
  if (!/^[A-D]$/i.test(normalizedValue)) {
    return null;
  }
  const index = normalizedValue.toUpperCase().charCodeAt(0) - 65;
  return index >= 0 && index < choiceCount ? index : null;
}

function resolveMatchingQuestionGroup(item: QuizItem | undefined): string | null {
  if (!item || item.questionFormat !== "MATCHING" || typeof item.questionGroup !== "string") {
    return null;
  }
  const normalized = item.questionGroup.trim();
  return normalized.length > 0 ? normalized : null;
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
