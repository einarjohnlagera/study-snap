import type { QuizSessionMode } from "@/lib/api";
import {
  buildNoteDetailPathWithTab,
  type NoteDetailTab,
} from "@/lib/note-entry";

export const NOTE_SESSION_REVIEW_QUERY_PARAMS = {
  routeMode: "mode",
} as const;

export type NoteSessionReviewMode = Extract<QuizSessionMode, "QUICK_REVIEW" | "CHALLENGE">;

const NOTE_SESSION_REVIEW_ROUTE_MODE_BY_SESSION_MODE: Record<NoteSessionReviewMode, string> = {
  QUICK_REVIEW: "quick-review",
  CHALLENGE: "challenge",
};

function normalizeSingleValue(value: string | string[] | null | undefined): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return value ?? null;
}

export function isNoteSessionReviewMode(value: string | null | undefined): value is NoteSessionReviewMode {
  return value === "QUICK_REVIEW" || value === "CHALLENGE";
}

export function normalizeNoteSessionReviewMode(
  value: string | string[] | null | undefined,
): NoteSessionReviewMode | null {
  const normalized = normalizeSingleValue(value);
  return isNoteSessionReviewMode(normalized) ? normalized : null;
}

export function toNoteSessionReviewRouteMode(sessionMode: NoteSessionReviewMode): string {
  return NOTE_SESSION_REVIEW_ROUTE_MODE_BY_SESSION_MODE[sessionMode];
}

export function fromNoteSessionReviewRouteMode(
  value: string | string[] | null | undefined,
): NoteSessionReviewMode | null {
  const normalized = normalizeSingleValue(value);
  if (normalized === "quick-review") {
    return "QUICK_REVIEW";
  }
  if (normalized === "challenge") {
    return "CHALLENGE";
  }
  return null;
}

export type NoteSessionReviewSource = "profile" | "dashboard";

export function buildNoteSessionReviewPath(
  noteId: string,
  sessionId: string,
  sessionMode: NoteSessionReviewMode,
  tab: NoteDetailTab,
  source?: NoteSessionReviewSource,
): string {
  const params: Record<string, string> = {
    [NOTE_SESSION_REVIEW_QUERY_PARAMS.routeMode]: toNoteSessionReviewRouteMode(sessionMode),
    tab,
  };
  if (source) {
    params.source = source;
  }
  return `/notes/${noteId}/sessions/${sessionId}?${new URLSearchParams(params).toString()}`;
}

export function buildNoteSessionReviewBackPath(
  noteId: string,
  tab: NoteDetailTab,
  source?: NoteSessionReviewSource | null,
): string {
  if (source === "profile") return "/profile";
  if (source === "dashboard") return "/dashboard";
  return buildNoteDetailPathWithTab(noteId, tab);
}

export function buildNoteSessionReviewBackLabel(source?: NoteSessionReviewSource | null): string {
  if (source === "profile") return "Profile";
  if (source === "dashboard") return "Dashboard";
  return "Note";
}
