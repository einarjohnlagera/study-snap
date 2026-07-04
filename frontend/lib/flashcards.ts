import type { QuizItem } from "@/lib/api";
import { normalizeConceptKey } from "@/lib/concepts";

export type Flashcard = {
  concept: string;
  explanation: string | null;
};

export type MatchedFlashcard = {
  concept: string;
  explanation: string;
};

const MIN_FUZZY_MATCH_LENGTH = 4;

export function isFuzzyConceptMatch(a: string, b: string): boolean {
  if (a === b) {
    return true;
  }
  const shorter = a.length <= b.length ? a : b;
  if (shorter.length < MIN_FUZZY_MATCH_LENGTH) {
    return false;
  }
  return a.includes(b) || b.includes(a);
}

export function buildFlashcardDeck(keyConcepts: string[], quiz: QuizItem[]): Flashcard[] {
  const explanations: Array<{ key: string; explanation: string }> = [];
  for (const item of quiz) {
    const concept = item.concept?.trim();
    const explanation = item.explanation?.trim();
    if (!concept || !explanation) {
      continue;
    }
    explanations.push({ key: normalizeConceptKey(concept), explanation });
  }

  function findExplanation(concept: string): string | null {
    const key = normalizeConceptKey(concept);
    const match = explanations.find((entry) => isFuzzyConceptMatch(entry.key, key));
    return match?.explanation ?? null;
  }

  return keyConcepts
    .map((concept) => concept.trim())
    .filter(Boolean)
    .map((concept) => ({
      concept,
      explanation: findExplanation(concept),
    }));
}

export function buildMatchedFlashcards(keyConcepts: string[], quiz: QuizItem[]): MatchedFlashcard[] {
  return buildFlashcardDeck(keyConcepts, quiz).filter((card): card is MatchedFlashcard => card.explanation !== null);
}
