import type { PublicNoteDetailResponse } from "@/lib/api";

const DEFAULT_PUBLIC_SUBJECT_SLUG = "general";
const DEFAULT_PUBLIC_TITLE_SLUG = "untitled-note";

function slugify(value: string | null | undefined, fallback: string) {
  const normalized = (value ?? "")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, "-")
    .replaceAll(/^-+|-+$/g, "");

  return normalized.length > 0 ? normalized : fallback;
}

export function getPublicSubjectSlug(subject: string | null | undefined) {
  return slugify(subject, DEFAULT_PUBLIC_SUBJECT_SLUG);
}

export function getPublicTitleSlug(title: string | null | undefined) {
  return slugify(title, DEFAULT_PUBLIC_TITLE_SLUG);
}

/**
 * ⚠️ DEVELOPMENT RULE: a `<Link>` to a subject listing page MUST set `prefetch={false}`.
 * Next.js 16.1.6 runs a route's `generateMetadata` when a link to it is prefetched (verified with a real browser, with or without
 * a `loading.tsx`), and the subject listing's metadata walks the whole subject catalog. So a link merely scrolling into view would
 * start catalog walks for pages nobody opens. Do not add a link without `prefetch={false}` unless engineering has first verified,
 * in a real browser, that prefetching no longer starts a walk. `lib/subject-link-prefetch.static.test.ts` guards the known
 * patterns; it is a safeguard, not proof that every possible construction is covered (a `router.prefetch()` call or an href
 * assembled another way would escape it). Re-verify on every Next.js upgrade.
 */
export function buildPublicLibrarySubjectPath(subject: string | null | undefined) {
  return `/public/library/${getPublicSubjectSlug(subject)}`;
}

export function buildPublicLibraryNotePath(input: {
  subject: string | null | undefined;
  title: string | null | undefined;
}) {
  return `${buildPublicLibrarySubjectPath(input.subject)}/${getPublicTitleSlug(input.title)}`;
}

export function buildPublicLibraryNotePathFromSlug(input: {
  subject: string | null | undefined;
  slug: string;
}) {
  return `${buildPublicLibrarySubjectPath(input.subject)}/${input.slug}`;
}

export function buildPublicLibraryNotePathFromDetail(note: PublicNoteDetailResponse) {
  return buildPublicLibraryNotePath({
    subject: note.subject,
    title: note.title,
  });
}

export function buildPublicProfilePath(userId: string | null | undefined) {
  return `/public/profile/${userId ?? ""}`;
}

export function buildPublicCreatorPath(username: string | null | undefined) {
  return `/public/creator/${username ?? ""}`;
}

export function buildPublicCreatorOrProfilePath(input: {
  userId: string | null | undefined;
  username?: string | null | undefined;
}) {
  const username = input.username?.trim();
  if (username) {
    return buildPublicCreatorPath(username);
  }
  return buildPublicProfilePath(input.userId);
}
