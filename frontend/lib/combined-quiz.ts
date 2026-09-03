/**
 * Combined-quiz request bounds, in ONE place.
 *
 * ⚠️ These mirror `CombinedQuizService.MAX_SOURCE_NOTES` / `MAX_TOTAL_QUESTIONS`, which the server enforces
 * by REJECTING an over-cap request (it never truncates). The UI checks them first only so the user is told
 * before submitting — the server remains the authority.
 *
 * ⚠️ Do NOT re-declare either number at a call site. Two surfaces read them (the Library picker and the
 * builder page), and `v0.103.0` records a standalone constant beside a derived cap as exactly how the two
 * drift apart. They are plan-agnostic on purpose: differing by plan would make them a pricing decision.
 */
export const COMBINED_QUIZ_MAX_SOURCE_NOTES = 20;
export const COMBINED_QUIZ_MAX_QUESTIONS = 100;

/** True when a selection cannot be assembled because it exceeds either bound. */
export function isCombinedQuizSelectionOverCap(sourceNoteCount: number, questionCount: number): boolean {
  return sourceNoteCount > COMBINED_QUIZ_MAX_SOURCE_NOTES || questionCount > COMBINED_QUIZ_MAX_QUESTIONS;
}
