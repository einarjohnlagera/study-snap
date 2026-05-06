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
