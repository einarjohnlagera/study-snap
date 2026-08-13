import type { ProfileType } from "@/lib/api";

export type NoteEntryMode = "quiz" | null;
export type NoteEntrySource = "paste" | "upload" | null;
export type NoteDetailTab = "summary" | "key-concepts" | "quiz" | "full-notes";

function normalizeSingleValue(value: string | string[] | null | undefined): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return value ?? null;
}

export function normalizeNoteDetailTab(value: string | string[] | null | undefined): NoteDetailTab {
  const normalized = normalizeSingleValue(value);
  if (
    normalized === "summary"
    || normalized === "key-concepts"
    || normalized === "quiz"
    || normalized === "full-notes"
  ) {
    return normalized;
  }
  return "summary";
}

export function normalizeNoteEntryMode(value: string | string[] | null | undefined): NoteEntryMode {
  const normalized = normalizeSingleValue(value);
  return normalized === "quiz" ? normalized : null;
}

export function normalizeNoteEntrySource(value: string | string[] | null | undefined): NoteEntrySource {
  const normalized = normalizeSingleValue(value);
  if (normalized === "paste" || normalized === "upload") {
    return normalized;
  }
  return null;
}

/**
 * Which tab to open after a Study Pack finishes generating.
 *
 * `canOpenQuizTab` exists because of the `v0.74.0` Quiz-tab lock: a freshly generated pack has no
 * completed Quick Review, so it is never mastered, so the Quiz tab is locked for every non-curator.
 * Without this guard a BOARD_EXAM learner — the majority of profile-typed accounts — lands on a
 * lock card as the payoff for generating their Study Pack. Curators bypass the lock and still get
 * the quiz. Pass `true` only when the viewer can actually open the tab.
 */
export function resolveGeneratedNoteTab(
  profileType: ProfileType | null | undefined,
  entryMode: NoteEntryMode,
  entrySource: NoteEntrySource,
  canOpenQuizTab: boolean = false,
): NoteDetailTab {
  const quizPreferred = entryMode === "quiz"
    || entrySource === "paste"
    || entrySource === "upload"
    || profileType === "BOARD_EXAM";
  if (quizPreferred && canOpenQuizTab) {
    return "quiz";
  }
  return "summary";
}

export function buildGeneratedNoteDetailPath(noteId: string, tab: NoteDetailTab): string {
  const searchParams = new URLSearchParams({
    from: "notes",
    created: "1",
    tab,
  });
  return `/notes/${noteId}?${searchParams.toString()}`;
}

export function buildGeneratingNoteDetailPath(noteId: string, tab: NoteDetailTab): string {
  const searchParams = new URLSearchParams({
    from: "notes",
    generating: "1",
    tab,
  });
  return `/notes/${noteId}?${searchParams.toString()}`;
}

export function buildNoteDetailPathWithTab(
  noteId: string,
  tab: NoteDetailTab,
  extraParams?: URLSearchParams,
): string {
  const searchParams = new URLSearchParams(extraParams?.toString() ?? "");
  searchParams.set("tab", tab);
  return `/notes/${noteId}?${searchParams.toString()}`;
}
