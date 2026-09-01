import type { NoteListItemResponse, PublicNoteDetailResponse } from "@/lib/api";
import { getPublicSubjectSlug } from "@/lib/public-note-path";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

type ServerPublicNoteListResponse = {
  items: NoteListItemResponse[];
  total: number;
  hasMore?: boolean;
};

// Next.js refuses to put anything over 2MB in its data cache, and that is a hard constant rather than a
// tunable. Once a response crosses it the cache silently stops deduping, so every static page issues its
// own copy of the request -- which is what took the production build down on 2026-08-31: ~250 pages each
// re-fetching a 2.5MB catalog until the backend saturated.
//
// ⚠️ 50 is the SERVER's ceiling, not our choice: NoteController.PUBLIC_NOTES_MAX_SIZE clamps pageSize to
// it, so asking for more silently yields 50. A previous version of this file asked for 250 and treated the
// clamped 50-item page as "end of data", which truncated the whole catalog to 50 notes -- see the loop
// below for why that break condition is gone.
// Headroom: the failing build logged 2,579,045 bytes over ~950 public notes, so a list item averages
// ~2.7KB and 50 items is ~135KB, comfortably inside the 2MB cache limit.
const PUBLIC_NOTES_PAGE_SIZE = 50;

/** What getPublicSubjectSlug substitutes for a subject that slugifies to empty. */
const PUBLIC_SUBJECT_FALLBACK_SLUG = "general";

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

/**
 * Fetches every page of a public-note query so no single response can cross the 2MB cache limit.
 * `query` is appended to /notes/public and must already be encoded.
 *
 * Failure semantics deliberately match the previous single-shot behaviour: fetchPublicNoteList returns
 * null rather than throwing when the backend is unreachable, and a null on the FIRST page yields an empty
 * list exactly as before. A null on a later page returns what was collected instead of discarding it --
 * a partial catalog degrades the sitemap, whereas throwing would fail the build, which is the outcome
 * this whole change exists to prevent.
 */
async function fetchAllPublicNotePages(query: string): Promise<NoteListItemResponse[]> {
  const collected: NoteListItemResponse[] = [];
  const separator = query.length > 0 ? "&" : "";
  for (let page = 0; ; page += 1) {
    const payload = await fetchPublicNoteList(
      `/notes/public?${query}${separator}page=${page}&pageSize=${PUBLIC_NOTES_PAGE_SIZE}`,
    );
    const items = payload?.items ?? [];
    collected.push(...items);
    // ⚠️ Stop ONLY on an authoritative signal: a failed page, hasMore=false, or an empty page. Together
    // these terminate; an empty page cannot repeat forever because it always breaks.
    //
    // ⚠️ There used to be a fourth condition here -- "a page shorter than we asked for means the end" --
    // and it was a REAL DEFECT, not a redundant guard. The server clamps pageSize to its own maximum, so
    // a request for more than that always comes back short WITH hasMore=true. The loop exited after page
    // zero and the whole catalog silently became one page. Do not reintroduce a length-based stop: the
    // page size we receive is the server's to decide, not ours to assume.
    if (payload === null || payload.hasMore !== true || items.length === 0) {
      break;
    }
  }
  return collected;
}

export async function getServerPublicNoteById(noteId: string) {
  return fetchPublicNote(`/notes/public/${noteId}`);
}

export async function getServerPublicNoteBySeoPath(subject: string, slug: string) {
  return fetchPublicNote(`/notes/public/seo/${subject}/${slug}`);
}

/**
 * The whole public catalog. ⚠️ Only sitemap.ts legitimately needs this -- every other caller should filter
 * server-side. It is paginated so each response stays cacheable; an unbounded single fetch is the defect
 * that broke the production build.
 */
export async function getServerPublicNotes() {
  return fetchAllPublicNotePages("");
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

/**
 * Subject slugs only, for generateStaticParams.
 *
 * ⚠️ This exists because generateStaticParams needs SLUGS and nothing else, yet reached them by pulling
 * the entire note catalog through getServerPublicSubjects just to derive them -- one of the fetches that
 * broke the build. GET /subjects?scope=public returns a small List<String> of public subject labels and
 * has been anonymous since v0.83.2.
 *
 * ⚠️ Slugs are de-duplicated: getPublicSubjectSlug is lossy, so two labels can map to one slug, and
 * generateStaticParams must not emit the same route twice.
 */
export async function getServerPublicSubjectSlugs(): Promise<string[]> {
  let response: Response;
  try {
    response = await fetch(buildApiUrl("/subjects?scope=public"), {
      method: "GET",
      next: { revalidate: 300 },
    });
  } catch {
    return [];
  }
  if (!response.ok) {
    return [];
  }
  let labels: unknown;
  try {
    labels = await response.json();
  } catch {
    return [];
  }
  if (!Array.isArray(labels)) {
    return [];
  }
  const slugs = new Set<string>();
  labels.forEach((label) => {
    if (typeof label !== "string" || label.trim().length === 0) {
      return;
    }
    slugs.add(getPublicSubjectSlug(label));
  });
  return Array.from(slugs);
}

/**
 * ⚠️ Passes the SLUG straight through, and that is correct rather than sloppy.
 * PublicLibraryRepositoryImpl matches `normalizedSlugSql(n.subject) = :subjectSlug`, and the incoming
 * `subject` param goes through NoteService.normalizePublicLibraryFilterSlug, which is
 * operation-for-operation identical to getPublicSubjectSlug: trim, lowercase, [^a-z0-9]+ -> '-', strip
 * leading/trailing dashes. So the server returns exactly the set the old in-JS filter produced, INCLUDING
 * every label variant that slugs the same -- and notes.subject is free text with no catalog, so those
 * variants really do exist.
 * ⚠️ Do NOT "resolve" the slug to a label first. Inverting a lossy slug would silently drop the variants
 * this preserves, on SEO-indexed pages.
 */
export async function getServerPublicNotesBySubjectSlug(subjectSlug: string) {
  const normalizedSubjectSlug = subjectSlug.trim();
  if (!normalizedSubjectSlug) {
    return [];
  }
  // ⚠️ The one slug the server filter cannot express. getPublicSubjectSlug substitutes "general" when a
  // subject slugifies to empty, but the SQL side coalesces a null subject to '' and the request param is
  // normalized with no fallback at all -- so '' never equals 'general' and a blank-subject public note
  // would silently vanish from a route getPublicSubjectEntries manufactures for it. Filtering these in
  // JS is what the old code did for every subject; it is bounded now because the fetch is paginated.
  if (normalizedSubjectSlug === PUBLIC_SUBJECT_FALLBACK_SLUG) {
    const notes = await getServerPublicNotes();
    return notes.filter((note) => getPublicSubjectSlug(note.subject) === PUBLIC_SUBJECT_FALLBACK_SLUG);
  }
  return fetchAllPublicNotePages(`subject=${encodeURIComponent(normalizedSubjectSlug)}`);
}

export async function getServerPublicNotesBySubject(subject: string) {
  const normalizedSubject = subject.trim();
  if (!normalizedSubject) {
    return [];
  }

  return fetchPublicNotes(`/notes/public?subject=${encodeURIComponent(normalizedSubject)}&size=4`);
}

export async function getServerPublicNotesByCourseProgram(courseProgram: string) {
  const normalizedCourseProgram = courseProgram.trim();
  if (!normalizedCourseProgram) {
    return [];
  }
  return fetchAllPublicNotePages(`courseProgram=${encodeURIComponent(normalizedCourseProgram)}`);
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

  // ⚠️ One request per program rather than one unbounded fetch: the endpoint filters a single program,
  // exam hub pages pass a handful, and each of those responses is small and independently cacheable.
  // Requests run in parallel; the merge below restores a stable order and drops duplicates, since a note
  // joined to several of the requested programs comes back once per program.
  const pages = await Promise.all(
    Array.from(normalizedCoursePrograms).map((courseProgram) => getServerPublicNotesByCourseProgram(courseProgram)),
  );
  const seenNoteIds = new Set<string>();
  const merged: NoteListItemResponse[] = [];
  pages.flat().forEach((note) => {
    if (seenNoteIds.has(note.id)) {
      return;
    }
    seenNoteIds.add(note.id);
    merged.push(note);
  });
  return merged;
}

