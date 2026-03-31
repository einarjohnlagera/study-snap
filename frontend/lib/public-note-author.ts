export type PublicNoteAuthorLabel = "By You" | "By NoteLib" | "By Community";

function normalizeComparableId(value: string | null | undefined): string | null {
  const normalized = value?.trim();
  return normalized ? normalized.toLowerCase() : null;
}

export function isPublicNoteOwner(params: {
  ownerUserId: string | null | undefined;
  currentUserId: string | null | undefined;
}): boolean {
  const ownerUserId = normalizeComparableId(params.ownerUserId);
  const currentUserId = normalizeComparableId(params.currentUserId);
  return ownerUserId !== null && currentUserId !== null && ownerUserId === currentUserId;
}

export function resolvePublicNoteAuthorLabel(params: {
  ownerUserId: string | null | undefined;
  currentUserId: string | null | undefined;
  official: boolean;
}): PublicNoteAuthorLabel {
  const { ownerUserId, currentUserId, official } = params;

  if (isPublicNoteOwner({ ownerUserId, currentUserId })) {
    return "By You";
  }

  if (official) {
    return "By NoteLib";
  }

  return "By Community";
}
