/**
 * Pure result computation utilities for Challenge Quiz.
 *
 * All calculations are derived solely from quiz session data (quiz items +
 * selected canonical choice indexes) — no LLM calls involved.
 *
 * These functions mirror the backend result computation so that the logic is
 * independently testable on the frontend.
 */

import type { QuizItem } from "@/lib/api";
import { isQuizSelectionCorrect } from "@/lib/quiz";

export type PerformanceLevel = "Excellent" | "Good" | "Fair" | "Needs Improvement";

export type ConceptStat = {
  concept: string;
  correctAnswers: number;
  totalQuestions: number;
  accuracyPercentage: number;
};

export type QuizScore = {
  correctAnswers: number;
  totalQuestions: number;
  scorePercentage: number;
};

/**
 * Accuracy threshold below which a concept is considered weak.
 * A concept with accuracy < WEAK_CONCEPT_THRESHOLD is flagged for follow-up.
 */
export const WEAK_CONCEPT_THRESHOLD = 60;

/**
 * Map a raw score percentage to a performance level label.
 *
 * Thresholds:
 * - 90–100 → Excellent
 * - 75–89  → Good
 * - 50–74  → Fair
 * - 0–49   → Needs Improvement
 */
export function mapPerformanceLevel(scorePercentage: number): PerformanceLevel {
  if (scorePercentage >= 90) return "Excellent";
  if (scorePercentage >= 75) return "Good";
  if (scorePercentage >= 50) return "Fair";
  return "Needs Improvement";
}

/**
 * Compute score totals and percentage from a quiz and the user's selections.
 *
 * @param quiz      Array of QuizItem objects (must include correctIndex and concept).
 * @param selectedChoices  Map of questionIndex → canonical choice index selected by the user.
 */
export function computeScore(
  quiz: QuizItem[],
  selectedChoices: Record<number, number>,
  selectedMultiChoices: Record<number, number[]> = {},
): QuizScore {
  const totalQuestions = quiz.length;
  const correctAnswers = quiz.reduce((count, item, index) => {
    const selected = item.questionFormat === "MULTI_SELECT"
      ? selectedMultiChoices[index]
      : selectedChoices[index];
    return isQuizSelectionCorrect(item, selected) ? count + 1 : count;
  }, 0);
  const scorePercentage =
    totalQuestions > 0 ? Math.round((correctAnswers / totalQuestions) * 100) : 0;

  return { correctAnswers, totalQuestions, scorePercentage };
}

/**
 * Compute per-concept accuracy from a quiz and the user's selections.
 *
 * Questions without a concept label are grouped under "Unknown".
 * Results are sorted by concept name for stable ordering.
 */
export function computeConceptBreakdown(
  quiz: QuizItem[],
  selectedChoices: Record<number, number>,
  selectedMultiChoices: Record<number, number[]> = {},
): ConceptStat[] {
  const accumulator = new Map<string, { correct: number; total: number }>();

  for (let index = 0; index < quiz.length; index++) {
    const item = quiz[index];
    const concept = item.concept?.trim() || "Unknown";
    const selected = item.questionFormat === "MULTI_SELECT"
      ? selectedMultiChoices[index]
      : selectedChoices[index];
    const isCorrect = isQuizSelectionCorrect(item, selected);

    const existing = accumulator.get(concept) ?? { correct: 0, total: 0 };
    accumulator.set(concept, {
      correct: existing.correct + (isCorrect ? 1 : 0),
      total: existing.total + 1,
    });
  }

  return Array.from(accumulator.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([concept, { correct, total }]) => ({
      concept,
      correctAnswers: correct,
      totalQuestions: total,
      accuracyPercentage: total > 0 ? Math.round((correct / total) * 100) : 0,
    }));
}

/**
 * Identify weak concepts from a breakdown.
 *
 * A concept is weak when its accuracy is strictly below WEAK_CONCEPT_THRESHOLD.
 * Returns concept label strings only (suitable for display and for passing to
 * the Adaptive Practice generation API).
 */
export function computeWeakConcepts(
  breakdown: ConceptStat[],
  threshold = WEAK_CONCEPT_THRESHOLD,
): string[] {
  return breakdown
    .filter((stat) => stat.accuracyPercentage < threshold)
    .map((stat) => stat.concept);
}
