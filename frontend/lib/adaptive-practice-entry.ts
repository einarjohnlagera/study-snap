export const ADAPTIVE_PRACTICE_ENTRY_QUERY_PARAM = "entry";
export const ADAPTIVE_PRACTICE_DASHBOARD_TODAY_FOCUS_ENTRY = "dashboard-today-focus";
export const ADAPTIVE_PRACTICE_DASHBOARD_FOCUS_AREAS_ENTRY = "dashboard-focus-areas";
export const ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY = "challenge-quiz-result";
export const ADAPTIVE_PRACTICE_INTERVIEW_PRACTICE_GAP_ENTRY = "interview-practice-gap";
export const ADAPTIVE_PRACTICE_DASHBOARD_CONTINUE_ENTRY = "dashboard-continue";
export const ADAPTIVE_PRACTICE_NOTE_DETAIL_ENTRY = "note-detail";
export const ADAPTIVE_PRACTICE_NOTE_DETAIL_DUE_CONCEPTS_ENTRY = "note-detail-due-concepts";
export const ADAPTIVE_PRACTICE_COLLECTION_DETAIL_ENTRY = "collection-detail";
export const ADAPTIVE_PRACTICE_DASHBOARD_PLAN_ENTRY = "dashboard-plan";

export type AdaptivePracticeEntry =
  | typeof ADAPTIVE_PRACTICE_DASHBOARD_TODAY_FOCUS_ENTRY
  | typeof ADAPTIVE_PRACTICE_DASHBOARD_FOCUS_AREAS_ENTRY
  | typeof ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY
  | typeof ADAPTIVE_PRACTICE_INTERVIEW_PRACTICE_GAP_ENTRY
  | typeof ADAPTIVE_PRACTICE_DASHBOARD_CONTINUE_ENTRY
  | typeof ADAPTIVE_PRACTICE_NOTE_DETAIL_ENTRY
  | typeof ADAPTIVE_PRACTICE_NOTE_DETAIL_DUE_CONCEPTS_ENTRY
  | typeof ADAPTIVE_PRACTICE_COLLECTION_DETAIL_ENTRY
  | typeof ADAPTIVE_PRACTICE_DASHBOARD_PLAN_ENTRY;

const ADAPTIVE_PRACTICE_ENTRIES: ReadonlySet<string> = new Set([
  ADAPTIVE_PRACTICE_DASHBOARD_TODAY_FOCUS_ENTRY,
  ADAPTIVE_PRACTICE_DASHBOARD_FOCUS_AREAS_ENTRY,
  ADAPTIVE_PRACTICE_CHALLENGE_QUIZ_RESULT_ENTRY,
  ADAPTIVE_PRACTICE_INTERVIEW_PRACTICE_GAP_ENTRY,
  ADAPTIVE_PRACTICE_DASHBOARD_CONTINUE_ENTRY,
  ADAPTIVE_PRACTICE_NOTE_DETAIL_ENTRY,
  ADAPTIVE_PRACTICE_NOTE_DETAIL_DUE_CONCEPTS_ENTRY,
  ADAPTIVE_PRACTICE_COLLECTION_DETAIL_ENTRY,
  ADAPTIVE_PRACTICE_DASHBOARD_PLAN_ENTRY,
]);

export function buildAdaptivePracticeHref(
  noteId: string,
  options: { entry?: AdaptivePracticeEntry } = {},
): string {
  const basePath = `/notes/${noteId}/adaptive-practice`;
  if (!options.entry) {
    return basePath;
  }
  const searchParams = new URLSearchParams({
    [ADAPTIVE_PRACTICE_ENTRY_QUERY_PARAM]: options.entry,
  });
  return `${basePath}?${searchParams.toString()}`;
}

export function normalizeAdaptivePracticeEntry(
  value: string | null | undefined,
): AdaptivePracticeEntry | null {
  if (!value || !ADAPTIVE_PRACTICE_ENTRIES.has(value)) {
    return null;
  }
  return value as AdaptivePracticeEntry;
}
