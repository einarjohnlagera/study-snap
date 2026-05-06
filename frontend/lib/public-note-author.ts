export type PublicNoteAuthorMeta = {
  label: string;
  showOfficialBadge: boolean;
};

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

export function resolvePublicNoteAuthorMeta(params: {
  ownerUserId: string | null | undefined;
  currentUserId: string | null | undefined;
  authorDisplayName: string | null | undefined;
  authorUsername?: string | null | undefined;
  isOfficialAuthor: boolean;
  isCurrentUser: boolean;
}): PublicNoteAuthorMeta {
  const { ownerUserId, currentUserId, authorDisplayName, authorUsername, isOfficialAuthor, isCurrentUser } = params;
  const normalizedAuthorDisplayName = authorDisplayName?.trim();
  const normalizedUsername = authorUsername?.trim();
  const labelWithUsername = normalizedAuthorDisplayName && normalizedUsername
    ? `By ${normalizedAuthorDisplayName} · @${normalizedUsername}`
    : normalizedAuthorDisplayName
      ? `By ${normalizedAuthorDisplayName}`
      : normalizedUsername
        ? `@${normalizedUsername}`
        : "By Community";

  if (isCurrentUser || isPublicNoteOwner({ ownerUserId, currentUserId })) {
    return {
      label: "By You",
      showOfficialBadge: false,
    };
  }

  if (isOfficialAuthor) {
    return {
      label: normalizedAuthorDisplayName ? `By ${normalizedAuthorDisplayName}` : "By NoteLib",
      showOfficialBadge: true,
    };
  }

  return {
    label: labelWithUsername,
    showOfficialBadge: false,
  };
}
