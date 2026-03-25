import type { NoteListItemResponse, PublicNoteDetailResponse } from "@/lib/api";
import { getPublicSubjectSlug } from "@/lib/public-note-path";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

function buildApiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

async function fetchPublicNote(path: string): Promise<PublicNoteDetailResponse | null> {
  const response = await fetch(buildApiUrl(path), {
    method: "GET",
    next: { revalidate: 300 },
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
  const response = await fetch(buildApiUrl(path), {
    method: "GET",
    next: { revalidate: 300 },
  });

  if (!response.ok) {
    throw new Error("Could not load public notes.");
  }
  return (await response.json()) as NoteListItemResponse[];
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

export type PublicSubjectEntry = {
  slug: string;
  label: string;
  lastModified: string | null;
};

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
      });
      return;
    }

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
