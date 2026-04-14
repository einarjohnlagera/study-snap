import type { QuizSessionMode } from "@/lib/api";
import {
  buildNoteDetailPathWithTab,
  normalizeNoteDetailTab,
  type NoteDetailTab,
} from "@/lib/note-entry";

export const MOBILE_SESSION_REVIEW_MEDIA_QUERY = "(max-width: 767px)";

export const NOTE_SESSION_REVIEW_QUERY_PARAMS = {
  sessionId: "sessionId",
  sessionMode: "sessionMode",
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

export function buildNoteSessionReviewPath(
  noteId: string,
  sessionId: string,
  sessionMode: NoteSessionReviewMode,
  tab: NoteDetailTab,
): string {
  const searchParams = new URLSearchParams({
    [NOTE_SESSION_REVIEW_QUERY_PARAMS.routeMode]: toNoteSessionReviewRouteMode(sessionMode),
    tab,
  });
  return `/notes/${noteId}/sessions/${sessionId}?${searchParams.toString()}`;
}

export function buildNoteDetailPathWithSessionReview(
  noteId: string,
  tab: NoteDetailTab,
  sessionId: string,
  sessionMode: NoteSessionReviewMode,
): string {
  const searchParams = new URLSearchParams({
    [NOTE_SESSION_REVIEW_QUERY_PARAMS.sessionId]: sessionId,
    [NOTE_SESSION_REVIEW_QUERY_PARAMS.sessionMode]: sessionMode,
  });
  return buildNoteDetailPathWithTab(noteId, tab, searchParams);
}

export function resolveRequestedNoteSessionReview(
  sessionIdValue: string | string[] | null | undefined,
  sessionModeValue: string | string[] | null | undefined,
): { sessionId: string; sessionMode: NoteSessionReviewMode } | null {
  const sessionId = normalizeSingleValue(sessionIdValue)?.trim() ?? "";
  const sessionMode = normalizeNoteSessionReviewMode(sessionModeValue);
  if (!sessionId || !sessionMode) {
    return null;
  }
  return { sessionId, sessionMode };
}

export function normalizeNoteSessionReviewTab(
  value: string | string[] | null | undefined,
): NoteDetailTab {
  return normalizeNoteDetailTab(value);
}
