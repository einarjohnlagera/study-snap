import type { NoteListItemResponse } from "@/lib/api";
import { normalizeSubject } from "@/lib/subjects";

export const DISCOVERY_SECTION_LIMIT = 6;
export const BROWSE_SUBJECTS_LIMIT = 8;

/**
 * Compute a weighted discovery score from engagement signals.
 * Formula: (views × 0.4) + (copies × 0.5) + (shares × 0.1)
 * Only uses real data available on NoteListItemResponse.
 */
export function computeDiscoveryScore(
  note: Pick<NoteListItemResponse, "viewCount" | "copyCount" | "shareCount">,
): number {
  const views = note.viewCount ?? 0;
  const copies = note.copyCount ?? 0;
  const shares = note.shareCount ?? 0;
  return views * 0.4 + copies * 0.5 + shares * 0.1;
}

/**
 * Return top N notes ranked by discovery score (views × 0.4 + copies × 0.5 + shares × 0.1).
 * Tiebreak: newer createdAt first.
 */
export function getFeaturedNotes(
  notes: NoteListItemResponse[],
  limit = DISCOVERY_SECTION_LIMIT,
): NoteListItemResponse[] {
  return [...notes]
    .sort((a, b) => {
      const scoreDiff = computeDiscoveryScore(b) - computeDiscoveryScore(a);
      if (scoreDiff !== 0) return scoreDiff;
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    })
    .slice(0, limit);
}

/**
 * Return top N notes sorted by copy count descending, then view count, then newest.
 * Accepts a pre-filtered list (caller excludes notes already in Featured).
 */
export function getPopularNotes(
  notes: NoteListItemResponse[],
  limit = DISCOVERY_SECTION_LIMIT,
): NoteListItemResponse[] {
  return [...notes]
    .sort((a, b) => {
      const copyDiff = (b.copyCount ?? 0) - (a.copyCount ?? 0);
      if (copyDiff !== 0) return copyDiff;
      const viewDiff = (b.viewCount ?? 0) - (a.viewCount ?? 0);
      if (viewDiff !== 0) return viewDiff;
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    })
    .slice(0, limit);
}

/**
 * Return top N notes sorted by createdAt descending.
 * Accepts a pre-filtered list (caller excludes notes already in Featured/Popular).
 */
export function getRecentNotes(
  notes: NoteListItemResponse[],
  limit = DISCOVERY_SECTION_LIMIT,
): NoteListItemResponse[] {
  return [...notes]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, limit);
}

/**
 * Return unique normalized subjects sorted by note count descending, then alphabetically.
 * Limits to BROWSE_SUBJECTS_LIMIT entries.
 */
export function getBrowseSubjects(
  notes: NoteListItemResponse[],
  maxSubjects = BROWSE_SUBJECTS_LIMIT,
): string[] {
  const counts = new Map<string, number>();
  for (const note of notes) {
    const subject = normalizeSubject(note.subject);
    if (subject) {
      counts.set(subject, (counts.get(subject) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .sort(([subjectA, countA], [subjectB, countB]) => countB - countA || subjectA.localeCompare(subjectB))
    .map(([subject]) => subject)
    .slice(0, maxSubjects);
}

/**
 * Filter a list to exclude notes whose ids are in the excludeIds set.
 */
export function excludeById(
  notes: NoteListItemResponse[],
  excludeIds: Set<string>,
): NoteListItemResponse[] {
  return notes.filter((note) => !excludeIds.has(note.id));
}
