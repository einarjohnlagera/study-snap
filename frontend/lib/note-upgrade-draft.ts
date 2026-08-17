import type { NoteEditorDraft, NoteEditorEntryOption } from "@/components/notes/note-editor-form";

export type PendingNoteUpgradeDraft = {
  draft: NoteEditorDraft;
  entryOption: NoteEditorEntryOption;
  generateTopic: string;
  savedAtMs: number;
};

const NOTE_UPGRADE_DRAFT_PREFIX = "notelib.note-upgrade-draft";
const RESTORE_DRAFT_QUERY_KEY = "restoreDraft";

function resolveKey(userId: string): string {
  return `${NOTE_UPGRADE_DRAFT_PREFIX}:${userId}`;
}

export function resolveNoteUpgradeDraftReturnPath(): string {
  return `/notes/new?${RESTORE_DRAFT_QUERY_KEY}=1`;
}

export function shouldRestoreNoteUpgradeDraft(): boolean {
  if (globalThis.window === undefined) {
    return false;
  }
  const params = new URLSearchParams(globalThis.location.search);
  return params.get(RESTORE_DRAFT_QUERY_KEY) === "1";
}

export function saveNoteUpgradeDraft(userId: string, snapshot: PendingNoteUpgradeDraft): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.setItem(resolveKey(userId), JSON.stringify(snapshot));
}

export function loadNoteUpgradeDraft(userId: string): PendingNoteUpgradeDraft | null {
  if (globalThis.window === undefined) {
    return null;
  }
  const raw = globalThis.localStorage.getItem(resolveKey(userId));
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<PendingNoteUpgradeDraft>;
    if (!parsed || typeof parsed !== "object") {
      return null;
    }
    return {
      draft: {
        title: parsed.draft?.title ?? "",
        subject: parsed.draft?.subject ?? "",
        courseProgram: parsed.draft?.courseProgram ?? "",
        domainContext: parsed.draft?.domainContext ?? "",
        learnerLevel: parsed.draft?.learnerLevel ?? "",
        content: parsed.draft?.content ?? "",
        tags: Array.isArray(parsed.draft?.tags) ? parsed.draft?.tags : [],
      },
      entryOption: parsed.entryOption === "generate" || parsed.entryOption === "import" ? parsed.entryOption : "write",
      generateTopic: typeof parsed.generateTopic === "string" ? parsed.generateTopic : "",
      savedAtMs: typeof parsed.savedAtMs === "number" ? parsed.savedAtMs : Date.now(),
    };
  } catch {
    return null;
  }
}

export function clearNoteUpgradeDraft(userId: string): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.removeItem(resolveKey(userId));
}
