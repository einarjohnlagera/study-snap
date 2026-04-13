import type { NoteListItemResponse } from "@/lib/api";
import { normalizeSubject } from "@/lib/subjects";

export const DISCOVERY_SECTION_LIMIT = 6;
export const BROWSE_SUBJECTS_LIMIT = 8;
export const PUBLIC_LIBRARY_RANKING = {
  FEATURED_READY_STATUS: "STUDY_PACK_READY",
  FEATURED_COPY_WEIGHT: 3,
  POPULAR_MIN_COPIES: 3,
  POPULAR_MIN_VIEWS: 20,
} as const;

function hasMeaningfulText(value: string | null | undefined): boolean {
  return typeof value === "string" && value.trim().length > 0;
}

function compareCreatedAtDesc(
  left: Pick<NoteListItemResponse, "createdAt">,
  right: Pick<NoteListItemResponse, "createdAt">,
): number {
  return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
}

export function isFeaturedEligible(
  note: Pick<
    NoteListItemResponse,
    "visibility" | "studyPackStatus" | "summaryPreview" | "quizCount" | "contentPreview"
  >,
): boolean {
  return (
    note.visibility === "PUBLIC" &&
    note.studyPackStatus === PUBLIC_LIBRARY_RANKING.FEATURED_READY_STATUS &&
    hasMeaningfulText(note.summaryPreview) &&
    (note.quizCount ?? 0) > 0 &&
    hasMeaningfulText(note.contentPreview)
  );
}

export function isPopularNote(
  note: Pick<NoteListItemResponse, "copyCount" | "viewCount">,
): boolean {
  return (
    (note.copyCount ?? 0) >= PUBLIC_LIBRARY_RANKING.POPULAR_MIN_COPIES ||
    (note.viewCount ?? 0) >= PUBLIC_LIBRARY_RANKING.POPULAR_MIN_VIEWS
  );
}

/**
 * Compute the v1 Featured score from engagement signals.
 * Formula: views + (copies × 3)
 */
export function computeDiscoveryScore(
  note: Pick<NoteListItemResponse, "viewCount" | "copyCount">,
): number {
  const views = note.viewCount ?? 0;
  const copies = note.copyCount ?? 0;
  return views + copies * PUBLIC_LIBRARY_RANKING.FEATURED_COPY_WEIGHT;
}

/**
 * Return top N Featured notes ranked by v1 score after quality gating.
 * Tiebreaks: copies desc, views desc, createdAt desc.
 */
export function getFeaturedNotes(
  notes: NoteListItemResponse[],
  limit = DISCOVERY_SECTION_LIMIT,
): NoteListItemResponse[] {
  return [...notes]
    .filter(isFeaturedEligible)
    .sort((a, b) => {
      const scoreDiff = computeDiscoveryScore(b) - computeDiscoveryScore(a);
      if (scoreDiff !== 0) {
        return scoreDiff;
      }
      const copyDiff = (b.copyCount ?? 0) - (a.copyCount ?? 0);
      if (copyDiff !== 0) {
        return copyDiff;
      }
      const viewDiff = (b.viewCount ?? 0) - (a.viewCount ?? 0);
      if (viewDiff !== 0) {
        return viewDiff;
      }
      return compareCreatedAtDesc(a, b);
    })
    .slice(0, limit);
}

/**
 * Return top N Popular notes that meet the social-proof threshold.
 * Ordering: copies desc, views desc, createdAt desc.
 */
export function getPopularNotes(
  notes: NoteListItemResponse[],
  limit = DISCOVERY_SECTION_LIMIT,
): NoteListItemResponse[] {
  return [...notes]
    .filter(isPopularNote)
    .sort((a, b) => {
      const copyDiff = (b.copyCount ?? 0) - (a.copyCount ?? 0);
      if (copyDiff !== 0) return copyDiff;
      const viewDiff = (b.viewCount ?? 0) - (a.viewCount ?? 0);
      if (viewDiff !== 0) return viewDiff;
      return compareCreatedAtDesc(a, b);
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
