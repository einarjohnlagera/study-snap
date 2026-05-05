"use client";

type NoteMetadataLike = {
  title?: string | null;
  subject?: string | null;
  tags?: string[] | null;
};

export type AiSuggestionSelection = {
  titleChoice: "keep" | "use-ai";
  subjectChoice: "keep" | "use-ai";
  tagsChoice: "keep" | "merge" | "use-ai";
};

function normalizeOptionalText(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function normalizeTags(tags: string[] | null | undefined): string[] {
  if (!tags) {
    return [];
  }

  const normalizedByKey = new Map<string, string>();
  for (const rawTag of tags) {
    const normalizedTag = rawTag.trim();
    if (normalizedTag.length === 0) {
      continue;
    }
    const duplicateKey = normalizedTag.toLowerCase();
    if (!normalizedByKey.has(duplicateKey)) {
      normalizedByKey.set(duplicateKey, normalizedTag);
    }
  }
  return [...normalizedByKey.values()];
}

export function partitionAiSuggestedTags(
  currentTags: string[] | null | undefined,
  suggestedTags: string[] | null | undefined,
): {
  newTags: string[];
  overlappingTags: string[];
} {
  const normalizedCurrentTags = normalizeTags(currentTags);
  const normalizedSuggestedTags = normalizeTags(suggestedTags);
  const existingKeys = new Set(normalizedCurrentTags.map((tag) => tag.toLowerCase()));
  const newTags: string[] = [];
  const overlappingTags: string[] = [];

  for (const suggestedTag of normalizedSuggestedTags) {
    if (existingKeys.has(suggestedTag.toLowerCase())) {
      overlappingTags.push(suggestedTag);
      continue;
    }
    newTags.push(suggestedTag);
  }

  return {
    newTags,
    overlappingTags,
  };
}

export function hasExistingNoteMetadata(note: NoteMetadataLike): boolean {
  return Boolean(
    (note.title && note.title.trim().length > 0)
    || (note.subject && note.subject.trim().length > 0)
    || (note.tags && note.tags.length > 0),
  );
}

export function mergeNoteTags(currentTags: string[] | null | undefined, suggestedTags: string[] | null | undefined): string[] {
  return normalizeTags([...(currentTags ?? []), ...(suggestedTags ?? [])]);
}

export function resolveAiSuggestionSelectionDefaults(
  current: NoteMetadataLike,
  suggested: NoteMetadataLike,
): AiSuggestionSelection {
  const currentTitle = normalizeOptionalText(current.title);
  const currentSubject = normalizeOptionalText(current.subject);
  const currentTags = normalizeTags(current.tags);
  const suggestedTitle = normalizeOptionalText(suggested.title);
  const suggestedSubject = normalizeOptionalText(suggested.subject);
  const suggestedTags = normalizeTags(suggested.tags);
  const suggestedTagPartition = partitionAiSuggestedTags(currentTags, suggestedTags);

  return {
    titleChoice: currentTitle ? "keep" : suggestedTitle ? "use-ai" : "keep",
    subjectChoice: currentSubject ? "keep" : suggestedSubject ? "use-ai" : "keep",
    tagsChoice: currentTags.length > 0
      ? suggestedTagPartition.newTags.length > 0 ? "merge" : "keep"
      : suggestedTags.length > 0
        ? "use-ai"
        : "keep",
  };
}

export function applyAiSuggestionSelection(
  current: NoteMetadataLike,
  suggested: NoteMetadataLike,
  selection: AiSuggestionSelection,
): {
  title: string | null;
  subject: string | null;
  tags: string[];
} {
  const currentTitle = normalizeOptionalText(current.title);
  const currentSubject = normalizeOptionalText(current.subject);
  const currentTags = normalizeTags(current.tags);
  const suggestedTitle = normalizeOptionalText(suggested.title);
  const suggestedSubject = normalizeOptionalText(suggested.subject);
  const suggestedTags = normalizeTags(suggested.tags);

  return {
    title: selection.titleChoice === "use-ai" ? suggestedTitle : currentTitle,
    subject: selection.subjectChoice === "use-ai" ? suggestedSubject : currentSubject,
    tags: selection.tagsChoice === "use-ai"
      ? suggestedTags
      : selection.tagsChoice === "merge"
        ? mergeNoteTags(currentTags, suggestedTags)
        : currentTags,
  };
}
