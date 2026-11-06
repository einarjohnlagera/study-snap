import type { NoteListItemResponse, PublicNoteDetailResponse } from "@/lib/api";
import { getPublicSubjectSlug } from "@/lib/public-note-path";
import { CATALOG_PAGE_TTL_CAP_MS, CATALOG_SITEMAP_TTL_CAP_MS, CATALOG_PAGE_WALK_DEADLINE_MS, CATALOG_SITEMAP_WALK_DEADLINE_MS, CATALOG_WALK_PAGE_REQUEST_TIMEOUT_MS, CATALOG_RAIL_REQUEST_TIMEOUT_MS, CATALOG_WALK_MAX_PAGES, CATALOG_FAILURE_BACKOFF_MS, catalogTtlMs, createRequestSignal, getCatalogCached } from "@/lib/catalog-cache";

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
  try {
    return await getCatalogCached(
      `rail:${path}`,
      catalogTtlMs(CATALOG_PAGE_TTL_CAP_MS, process.env.NOTELIB_CATALOG_PAGE_TTL_MS),
      async (signal) => {
        const request = createRequestSignal(signal, CATALOG_RAIL_REQUEST_TIMEOUT_MS);
        try {
          const response = await fetch(buildApiUrl(path), { method: "GET", cache: "no-store", signal: request.signal });
          if (!response.ok) throw new Error("Could not load public note rail.");
          const payload = await response.json() as ServerPublicNoteListResponse;
          if (!payload || !Array.isArray(payload.items)) throw new Error("Invalid public note rail response.");
          return payload.items;
        } finally {
          request.clear();
        }
      },
      { hardCapMs: CATALOG_PAGE_TTL_CAP_MS, deadlineMs: CATALOG_PAGE_WALK_DEADLINE_MS, failureBackoffMs: CATALOG_FAILURE_BACKOFF_MS },
    );
  } catch {
    return [];
  }
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
 * Fetches every page of a public-note query so no single response can cross the 2MB limit.
 * `query` is appended to /notes/public and must already be encoded. Any failed page rejects the walk.
 *
 * ⚠️ ALWAYS sends `sort=recent` -- this is REQUIRED, not decorative, same reasoning as
 * `getServerPublicNoteCount` above. With no `sort` the server defaults to RECOMMENDED, which is not
 * SQL-orderable (`PublicLibrarySort.java`), so every page -- not just the first -- takes the ranked
 * branch: a full popularity-ranking query plus a count, repeated once per page. That is the exact
 * mechanism behind the 2026-10-01 production outage (24 backend restarts/30 days): a public Note page's
 * two "More from {Subject}"/"More {Program} notes" rails called this function to walk an entire
 * subject's or program's note list -- 8 pages for Accountancy, 18 for Civil Engineering -- recomputing
 * live popularity on every single page, to fill 7 cards. `RECENT` is SQL-orderable
 * (`order by created_at desc, id asc`), so every page here uses the SQL-orderable listing path instead
 * of the ranking query. Per the owner's reliability decision
 * (`docs/claude-plans/2026-10-01-public-note-discovery-rail-ordering-decision.md`), the two broken rails
 * no longer call this function at all -- see `getServerPublicNotesBySubjectRecent` and
 * `getServerPublicNotesByCourseProgramRecent` below -- but this walker still serves callers that
 * genuinely need the full list (the subject listing page, exam hub pages, the sitemap), so it must stay
 * cheap for all of them, not just the two rails that stopped using it.
 */
export type CatalogWalkEntry = "sitemap" | "subject" | "exam-hub";
type CatalogTtlClass = "page" | "sitemap";

/**
 * Offset paging over a catalog that changes while it is walked: a note published mid-walk shifts the offset and
 * repeats one item across a page boundary. Keep the first occurrence (it keeps the position order). A note
 * UNPUBLISHED mid-walk can still make the walk skip one item; that needs a consistent snapshot and is not fixed here.
 */
function dedupeById(items: NoteListItemResponse[]): NoteListItemResponse[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    if (typeof item?.id !== "string") return true;
    if (seen.has(item.id)) return false;
    seen.add(item.id);
    return true;
  });
}

async function fetchAllPublicNotePages(
  query: string,
  entry: CatalogWalkEntry,
  ttlClass: CatalogTtlClass = "page",
  shouldCache?: (notes: NoteListItemResponse[]) => boolean,
): Promise<NoteListItemResponse[]> {
  const cap = ttlClass === "sitemap" ? CATALOG_SITEMAP_TTL_CAP_MS : CATALOG_PAGE_TTL_CAP_MS;
  const override = ttlClass === "sitemap"
    ? process.env.NOTELIB_CATALOG_SITEMAP_TTL_MS
    : process.env.NOTELIB_CATALOG_PAGE_TTL_MS;
  return getCatalogCached(`walk:${ttlClass}:${query}`, catalogTtlMs(cap, override), async (signal) => {
    const collected: NoteListItemResponse[] = [];
    const separator = query.length > 0 ? "&" : "";
    const startedAt = Date.now();
    let pages = 0;
    let reported = 0;
    let outcome = "error";
    try {
      for (let page = 0; page < CATALOG_WALK_MAX_PAGES; page += 1) {
        const request = createRequestSignal(signal, CATALOG_WALK_PAGE_REQUEST_TIMEOUT_MS);
        try {
          const response = await fetch(buildApiUrl(
            `/notes/public?${query}${separator}page=${page}&pageSize=${PUBLIC_NOTES_PAGE_SIZE}&sort=recent`,
          ), {
            method: "GET",
            cache: "no-store",
            headers: { "X-NoteLib-Catalog-Walk": entry },
            signal: request.signal,
          });
          pages += 1;
          if (!response.ok) throw new Error("Could not load public catalog.");
          const payload = await response.json() as ServerPublicNoteListResponse;
          if (!payload || !Array.isArray(payload.items) || typeof payload.hasMore !== "boolean") {
            throw new Error("Invalid public catalog response.");
          }
          const items = payload.items;
          if (payload.hasMore && items.length === 0) throw new Error("Invalid public catalog response.");
          collected.push(...items);
          reported = collected.length;
          if (payload.hasMore === false) {
            outcome = "ok";
            const unique = dedupeById(collected);
            reported = unique.length;
            return unique;
          }
        } finally {
          request.clear();
        }
      }
      console.error(`CATALOG_WALK_GUARD_BREACH class=${ttlClass} entry=${entry} pages=${CATALOG_WALK_MAX_PAGES}`);
      throw new Error("Public catalog walk exceeded page guard.");
    } finally {
      console.info(`CATALOG_WALK outcome=${outcome} class=${ttlClass} entry=${entry} pages=${pages} notes=${reported} durationMs=${Date.now() - startedAt}`);
    }
  }, {
    hardCapMs: cap,
    deadlineMs: ttlClass === "sitemap" ? CATALOG_SITEMAP_WALK_DEADLINE_MS : CATALOG_PAGE_WALK_DEADLINE_MS,
    shouldCache,
    failureBackoffMs: CATALOG_FAILURE_BACKOFF_MS,
  });
}

export async function getServerPublicNoteById(noteId: string) {
  return fetchPublicNote(`/notes/public/${noteId}`);
}

export async function getServerPublicNoteBySeoPath(subject: string, slug: string) {
  return fetchPublicNote(`/notes/public/seo/${subject}/${slug}`);
}

/**
 * The whole public catalog. The sitemap and the `general` subject page need this; other callers should
 * filter server-side. It is paginated so each response stays below the 2 MB limit; an unbounded single
 * fetch is the defect that broke the production build.
 *
 * ⚠️ THAT PAGINATION BOUNDED THE RESPONSE, NOT THE SERVER'S WORK, AND READING IT OTHERWISE IS WHAT LET
 * THE 2026-09-05 OUTAGE HAPPEN. It correctly fixed the 2 MB Next.js data-cache defect; it did nothing
 * about the server materialising the whole catalog per request. The walker sends `sort=recent` on every
 * page to use the SQL-orderable path instead of the default ranked branch.
 */
export async function getServerPublicNotes(options: { ttlClass?: CatalogTtlClass; entry?: CatalogWalkEntry } = {}) {
  return fetchAllPublicNotePages("", options.entry ?? "subject", options.ttlClass ?? "page");
}

export async function getServerPublicNoteCount(): Promise<number | null> {
  // ⚠️ `page`/`pageSize`, NOT `size`. The server picks its branch on `page != null || pageSize != null`
  // — `size` is not a pagination parameter, so `?size=1` took the LEGACY branch, which loads every
  // public NoteEntity (including `content`) and applies `size` LAST. That is 1,442 notes materialised
  // to return one integer, inside a transaction holding a pooled connection: the 2026-09-05 outage.
  //
  // ⚠️ `sort=recent` is REQUIRED, not decorative. The default sort is RECOMMENDED, which is not
  // SQL-orderable, so a paginated request WITHOUT a sort still loads every candidate to rank in Java.
  //
  // The paginated branch sets `totalMatching` from a real `count(*)`, and `total` is what this function
  // already read — so the field keeps its exact meaning and is simply computed cheaply.
  const payload = await fetchPublicNoteList("/notes/public?page=0&pageSize=1&sort=recent");
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
    const notes = await getServerPublicNotes({ entry: "subject" });
    return notes.filter((note) => getPublicSubjectSlug(note.subject) === PUBLIC_SUBJECT_FALLBACK_SLUG);
  }
  return fetchAllPublicNotePages(`subject=${encodeURIComponent(normalizedSubjectSlug)}`, "subject", "page", (notes) => notes.length > 0);
}

export async function getServerPublicNotesBySubject(subject: string) {
  const normalizedSubject = subject.trim();
  if (!normalizedSubject) {
    return [];
  }

  // ⚠️ SAME BRANCH FIX AS getServerPublicNoteCount, and one PRODUCT CHANGE that was decided, not
  // absorbed (owner, 2026-09-06): these four related notes used to arrive in RECOMMENDED rank order and
  // now arrive most-recent-first. RECOMMENDED is not SQL-orderable, so the ranked order cannot be had
  // without bounding the ranking branch itself. This runs on ~250 public SEO pages, each of which was
  // loading the entire 1,442-note catalog to show four items.
  return fetchPublicNotes(
    `/notes/public?subject=${encodeURIComponent(normalizedSubject)}&page=0&pageSize=4&sort=recent`,
  );
}

/**
 * Bounded, most-recent-first replacement for the Public Note page's "More from {Subject}" rail.
 *
 * ⚠️ Added 2026-10-01, closing the production outage this release exists for. The rail used to call
 * `getServerPublicNotesBySubjectSlug` (the UNBOUNDED walker above) to pull an entire subject's note
 * list, rank it by popularity in JavaScript, and keep the top 3 -- up to 18 pages of ranking queries
 * per render for a popular subject. One bounded page-0 fetch replaces the whole walk.
 *
 * ⚠️ `readyOnly=true` is REQUIRED here, and its absence would be a silent eligibility regression, not a
 * cosmetic difference. The old unbounded path filtered `studyPackDone === true` in JS after fetching
 * everything; a bounded fetch has nothing left to filter afterward, so the server must apply the same
 * filter instead. This deliberately does NOT reuse `getServerPublicNotesBySubject` below (the
 * already-bounded "More in {Subject}" rail), which does not pass `readyOnly` -- that is pre-existing,
 * separate behavior this change does not touch.
 *
 * `limit` is the caller's bounded candidate count, which may include headroom for filtering notes
 * already displayed elsewhere. This requests one extra row so the current note can be excluded
 * (the exact pattern `getServerPublicNotesBySubject` already uses for its own display count).
 */
export async function getServerPublicNotesBySubjectSlugRecent(subjectSlug: string, limit: number) {
  const normalizedSubjectSlug = subjectSlug.trim();
  if (!normalizedSubjectSlug || limit <= 0) {
    return [];
  }
  return fetchPublicNotes(
    `/notes/public?readyOnly=true&subject=${encodeURIComponent(normalizedSubjectSlug)}&page=0&pageSize=${limit + 1}&sort=recent`,
  );
}

/**
 * Bounded, most-recent-first replacement for the Public Note page's "More {Program} notes" rail.
 * Same reasoning and the same `readyOnly=true` requirement as
 * `getServerPublicNotesBySubjectSlugRecent` above -- see its comment for the full mechanism this
 * replaces. This rail used to call `getServerPublicNotesByCourseProgram` (the UNBOUNDED walker),
 * costing up to 18 pages of ranking queries per render for a program the size of Civil Engineering.
 */
export async function getServerPublicNotesByCourseProgramRecent(courseProgram: string, limit: number) {
  const normalizedCourseProgram = courseProgram.trim();
  if (!normalizedCourseProgram || limit <= 0) {
    return [];
  }
  return fetchPublicNotes(
    `/notes/public?readyOnly=true&courseProgram=${encodeURIComponent(normalizedCourseProgram)}&page=0&pageSize=${limit + 1}&sort=recent`,
  );
}

export async function getServerPublicNotesByCourseProgram(courseProgram: string) {
  const normalizedCourseProgram = courseProgram.trim();
  if (!normalizedCourseProgram) {
    return [];
  }
  return fetchAllPublicNotePages(`courseProgram=${encodeURIComponent(normalizedCourseProgram)}`, "exam-hub");
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
