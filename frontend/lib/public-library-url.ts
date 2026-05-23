import type { NoteTargetProfileType } from "@/lib/api";

export const PUBLIC_LIBRARY_PATH = "/public/library";
export const PUBLIC_LIBRARY_VIEW_QUERY_PARAM = "view";
export const PUBLIC_LIBRARY_SEARCH_QUERY_PARAM = "search";
export const PUBLIC_LIBRARY_SUBJECT_QUERY_PARAM = "subject";
export const PUBLIC_LIBRARY_TAG_QUERY_PARAM = "tag";
export const PUBLIC_LIBRARY_AUDIENCE_QUERY_PARAM = "audience";
export const PUBLIC_LIBRARY_COURSE_PROGRAM_QUERY_PARAM = "courseProgram";
export const PUBLIC_LIBRARY_SORT_QUERY_PARAM = "sort";

const PUBLIC_LIBRARY_FILTER_QUERY_PARAMS = [
  PUBLIC_LIBRARY_VIEW_QUERY_PARAM,
  PUBLIC_LIBRARY_SEARCH_QUERY_PARAM,
  PUBLIC_LIBRARY_SUBJECT_QUERY_PARAM,
  PUBLIC_LIBRARY_TAG_QUERY_PARAM,
  PUBLIC_LIBRARY_AUDIENCE_QUERY_PARAM,
  PUBLIC_LIBRARY_COURSE_PROGRAM_QUERY_PARAM,
  PUBLIC_LIBRARY_SORT_QUERY_PARAM,
] as const;

export type PublicLibraryDiscoveryView = "featured" | "popular" | "recent";
export type PublicLibrarySortQuery = "popular" | "recent" | "title" | "views" | "copied";

export type PublicLibraryUrlFilters = {
  audience?: NoteTargetProfileType | null;
  courseProgram?: string | null;
  search?: string | null;
  sort?: PublicLibrarySortQuery | null;
  subject?: string | null;
  tags?: string[];
  view?: PublicLibraryDiscoveryView | null;
};

type SearchParamsInput =
  | URLSearchParams
  | ReadonlyURLSearchParams
  | string
  | Record<string, string | string[] | undefined>;

type ReadonlyURLSearchParams = {
  entries(): IterableIterator<[string, string]>;
  get(name: string): string | null;
  getAll(name: string): string[];
  has(name: string): boolean;
  toString(): string;
};

function normalizeFilterValue(value: string | null | undefined) {
  const normalized = value?.trim();
  return normalized || null;
}

function isReadonlyURLSearchParams(input: SearchParamsInput): input is ReadonlyURLSearchParams {
  return typeof input === "object"
    && input !== null
    && "entries" in input
    && typeof input.entries === "function";
}

function cloneSearchParams(input?: SearchParamsInput) {
  if (!input) {
    return new URLSearchParams();
  }
  if (typeof input === "string") {
    return new URLSearchParams(input.startsWith("?") ? input.slice(1) : input);
  }
  if (input instanceof URLSearchParams) {
    return new URLSearchParams(input);
  }
  if (isReadonlyURLSearchParams(input)) {
    const params = new URLSearchParams();
    for (const [key, value] of input.entries()) {
      params.append(key, value);
    }
    return params;
  }

  const params = new URLSearchParams();
  Object.entries(input).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((entry) => {
        if (entry) {
          params.append(key, entry);
        }
      });
      return;
    }
    if (value) {
      params.set(key, value);
    }
  });
  return params;
}

export function slugifyPublicLibraryFilterValue(value: string | null | undefined) {
  return (value ?? "")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, "-")
    .replaceAll(/^-+|-+$/g, "");
}

export function resolvePublicLibraryValueBySlug(values: string[], slug: string | null | undefined) {
  if (!slug) {
    return null;
  }

  return values.find((value) => slugifyPublicLibraryFilterValue(value) === slug) ?? null;
}

export function resolvePublicLibraryValuesBySlug(values: string[], slugs: string[]) {
  return slugs
    .map((slug) => resolvePublicLibraryValueBySlug(values, slug))
    .filter((value): value is string => value !== null);
}

export function toPublicLibraryAudienceQueryValue(value: NoteTargetProfileType | null | undefined) {
  if (!value) {
    return null;
  }
  return slugifyPublicLibraryFilterValue(value.replaceAll("_", "-"));
}

export function parsePublicLibraryAudienceQueryValue(value: string | null | undefined): NoteTargetProfileType | null {
  const normalized = slugifyPublicLibraryFilterValue(value);
  if (normalized === "student") {
    return "STUDENT";
  }
  if (normalized === "board-taker") {
    return "BOARD_TAKER";
  }
  if (normalized === "professional") {
    return "PROFESSIONAL";
  }
  return null;
}

export function parsePublicLibraryFilters(searchParams?: SearchParamsInput): Required<PublicLibraryUrlFilters> {
  const params = cloneSearchParams(searchParams);
  const sort = normalizeFilterValue(params.get(PUBLIC_LIBRARY_SORT_QUERY_PARAM));
  const view = normalizeFilterValue(params.get(PUBLIC_LIBRARY_VIEW_QUERY_PARAM));

  return {
    audience: parsePublicLibraryAudienceQueryValue(params.get(PUBLIC_LIBRARY_AUDIENCE_QUERY_PARAM)),
    courseProgram: normalizeFilterValue(params.get(PUBLIC_LIBRARY_COURSE_PROGRAM_QUERY_PARAM)),
    search: normalizeFilterValue(params.get(PUBLIC_LIBRARY_SEARCH_QUERY_PARAM)),
    sort: sort === "popular" || sort === "recent" || sort === "title" || sort === "views" || sort === "copied"
      ? sort
      : null,
    subject: normalizeFilterValue(params.get(PUBLIC_LIBRARY_SUBJECT_QUERY_PARAM)),
    tags: params.getAll(PUBLIC_LIBRARY_TAG_QUERY_PARAM)
      .map((value) => normalizeFilterValue(value))
      .filter((value): value is string => value !== null),
    view: view === "featured" || view === "popular" || view === "recent"
      ? view
      : null,
  };
}

export function buildPublicLibraryUrl(
  filters: PublicLibraryUrlFilters = {},
  baseSearchParams?: SearchParamsInput,
) {
  const params = cloneSearchParams(baseSearchParams);

  PUBLIC_LIBRARY_FILTER_QUERY_PARAMS.forEach((key) => params.delete(key));

  const search = normalizeFilterValue(filters.search);
  const subject = normalizeFilterValue(filters.subject);
  const courseProgram = normalizeFilterValue(filters.courseProgram);
  const audience = toPublicLibraryAudienceQueryValue(filters.audience);

  if (filters.view) {
    params.set(PUBLIC_LIBRARY_VIEW_QUERY_PARAM, filters.view);
  }
  if (search) {
    params.set(PUBLIC_LIBRARY_SEARCH_QUERY_PARAM, search);
  }
  if (subject) {
    params.set(PUBLIC_LIBRARY_SUBJECT_QUERY_PARAM, subject);
  }
  if (courseProgram) {
    params.set(PUBLIC_LIBRARY_COURSE_PROGRAM_QUERY_PARAM, courseProgram);
  }
  if (audience) {
    params.set(PUBLIC_LIBRARY_AUDIENCE_QUERY_PARAM, audience);
  }
  if (filters.sort) {
    params.set(PUBLIC_LIBRARY_SORT_QUERY_PARAM, filters.sort);
  }
  (filters.tags ?? [])
    .map((tag) => normalizeFilterValue(tag))
    .filter((tag): tag is string => tag !== null)
    .forEach((tag) => params.append(PUBLIC_LIBRARY_TAG_QUERY_PARAM, tag));

  const query = params.toString();
  return query ? `${PUBLIC_LIBRARY_PATH}?${query}` : PUBLIC_LIBRARY_PATH;
}
