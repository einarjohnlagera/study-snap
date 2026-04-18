export const CHALLENGE_QUIZ_ENTRY_QUERY_PARAM = "entry";
export const CHALLENGE_QUIZ_MODE_SELECTION_ENTRY = "mode-selection";

export function buildChallengeQuizHref(
  noteId: string,
  options: { entry?: typeof CHALLENGE_QUIZ_MODE_SELECTION_ENTRY } = {},
): string {
  const basePath = `/notes/${noteId}/challenge-quiz`;
  if (!options.entry) {
    return basePath;
  }
  const searchParams = new URLSearchParams({
    [CHALLENGE_QUIZ_ENTRY_QUERY_PARAM]: options.entry,
  });
  return `${basePath}?${searchParams.toString()}`;
}

export function isModeSelectionChallengeQuizEntry(value: string | null | undefined): boolean {
  return value === CHALLENGE_QUIZ_MODE_SELECTION_ENTRY;
}
