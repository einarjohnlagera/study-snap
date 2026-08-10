import type { NoteListItemResponse, PublicNoteDetailResponse } from "@/lib/api";
import { getPublicSubjectSlug } from "@/lib/public-note-path";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

type ServerPublicNoteListResponse = {
  items: NoteListItemResponse[];
  total: number;
};

function buildApiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

async function fetchPublicNote(path: string): Promise<PublicNoteDetailResponse | null> {
  const response = await fetch(buildApiUrl(path), {
    method: "GET",
    cache: "no-store",
  });

  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error("Could not load public note.");
  }
  return (await response.json()) as PublicNoteDetailResponse;
}

async function fetchPublicNotes(path: string): Promise<NoteListItemResponse[]> {
  const payload = await fetchPublicNoteList(path);
  return payload?.items ?? [];
}

async function fetchPublicNoteList(path: string): Promise<ServerPublicNoteListResponse | null> {
  let response: Response;
  try {
    response = await fetch(buildApiUrl(path), {
      method: "GET",
      next: { revalidate: 300 },
    });
  } catch {
    // Backend unreachable at build time — ISR will populate on first live request
    return null;
  }

  if (!response.ok) {
    return null;
  }
  try {
    return (await response.json()) as ServerPublicNoteListResponse;
  } catch {
    return null;
  }
}

export async function getServerPublicNoteById(noteId: string) {
  return fetchPublicNote(`/notes/public/${noteId}`);
}

export async function getServerPublicNoteBySeoPath(subject: string, slug: string) {
  return fetchPublicNote(`/notes/public/seo/${subject}/${slug}`);
}

export async function getServerPublicNotes() {
  return fetchPublicNotes("/notes/public");
}

export async function getServerPublicNoteCount(): Promise<number | null> {
  const payload = await fetchPublicNoteList("/notes/public?size=1");
  const total = payload?.total;
  return typeof total === "number" && Number.isInteger(total) && total >= 0 ? total : null;
}

export type PublicSubjectEntry = {
  slug: string;
  label: string;
  lastModified: string | null;
  noteCount: number;
};

// A subject page below this note count reads as thin to both visitors (its Featured section, capped at
// DISCOVERY_SECTION_LIMIT, can't even fill) and search engines. Below it, the page stays reachable and
// visible but is excluded from the sitemap and marked noindex — see SUBJECT_PAGE_INDEX_THRESHOLD usage
// in app/public/library/[subject]/page.tsx and app/sitemap.ts. Set from a 2026-07-17 production depth
// inventory (docs/claude-prompt/public-library-seo-expansion-out/02-subject-depth-inventory.sql): of 130
// distinct subject pages, only 38 had >= 6 notes.
export const SUBJECT_PAGE_INDEX_THRESHOLD = 6;

export function getPublicSubjectEntries(notes: NoteListItemResponse[]): PublicSubjectEntry[] {
  const subjects = new Map<string, PublicSubjectEntry>();

  notes.forEach((note) => {
    const label = note.subject?.trim() || "General";
    const slug = getPublicSubjectSlug(label);
    const existing = subjects.get(slug);

    if (!existing) {
      subjects.set(slug, {
        slug,
        label,
        lastModified: note.updatedAt ?? null,
        noteCount: 1,
      });
      return;
    }

    existing.noteCount += 1;
    if (note.updatedAt && (!existing.lastModified || note.updatedAt > existing.lastModified)) {
      existing.lastModified = note.updatedAt;
    }
  });

  return Array.from(subjects.values()).sort((left, right) => left.label.localeCompare(right.label));
}

export async function getServerPublicSubjects() {
  return getPublicSubjectEntries(await getServerPublicNotes());
}

export async function getServerPublicNotesBySubjectSlug(subjectSlug: string) {
  const notes = await getServerPublicNotes();
  return notes.filter((note) => getPublicSubjectSlug(note.subject) === subjectSlug);
}

export async function getServerPublicNotesBySubject(subject: string) {
  const normalizedSubject = subject.trim();
  if (!normalizedSubject) {
    return [];
  }

  return fetchPublicNotes(`/notes/public?subject=${encodeURIComponent(normalizedSubject)}&size=4`);
}

export async function getServerPublicNotesByCourseProgram(courseProgram: string) {
  const notes = await getServerPublicNotes();
  const normalizedCourseProgram = courseProgram.trim().toLowerCase();
  return notes.filter((note) => getNormalizedNotePrograms(note).includes(normalizedCourseProgram));
}

export async function getServerPublicNotesByCoursePrograms(coursePrograms: readonly string[]) {
  const normalizedCoursePrograms = new Set(
    coursePrograms
      .map((courseProgram) => courseProgram.trim().toLowerCase())
      .filter((courseProgram) => courseProgram.length > 0),
  );
  if (normalizedCoursePrograms.size === 0) {
    return [];
  }

  const notes = await getServerPublicNotes();
  return notes.filter((note) =>
    getNormalizedNotePrograms(note).some((courseProgram) => normalizedCoursePrograms.has(courseProgram)),
  );
}

function getNormalizedNotePrograms(note: NoteListItemResponse) {
  const joinedPrograms = note.applicablePrograms
    ?.map((courseProgram) => courseProgram.trim().toLowerCase())
    .filter((courseProgram) => courseProgram.length > 0) ?? [];
  if (joinedPrograms.length > 0) {
    return joinedPrograms;
  }

  const personalNoteProgram = note.courseProgram?.trim().toLowerCase();
  return personalNoteProgram ? [personalNoteProgram] : [];
}
