import type { ProfileType } from "@/lib/api";

export type NoteEntryMode = "quiz" | null;
export type NoteEntrySource = "paste" | "upload" | null;
export type NoteDetailTab = "summary" | "quiz";

function normalizeSingleValue(value: string | string[] | null | undefined): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return value ?? null;
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

export function resolveGeneratedNoteTab(
  profileType: ProfileType | null | undefined,
  entryMode: NoteEntryMode,
  entrySource: NoteEntrySource,
): NoteDetailTab {
  if (entryMode === "quiz") {
    return "quiz";
  }
  if (entrySource === "paste" || entrySource === "upload") {
    return "quiz";
  }
  if (profileType === "BOARD_EXAM") {
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

export function buildNoteDetailPathWithTab(
  noteId: string,
  tab: NoteDetailTab,
  extraParams?: URLSearchParams,
): string {
  const searchParams = new URLSearchParams(extraParams?.toString() ?? "");
  searchParams.set("tab", tab);
  return `/notes/${noteId}?${searchParams.toString()}`;
}
