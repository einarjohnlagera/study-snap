/**
 * The render-side half of the announcement CTA security control.
 *
 * ⚠️ THE BACKEND ALREADY VALIDATES `cta_path` ON WRITE. This exists because a value that is already
 * in the database is still untrusted input by the time it reaches an `href`: `next/link` happily
 * renders an absolute URL as an external anchor, so a row written before this rule existed — or by
 * any future path that forgets it — would become a live off-site link inside every user's inbox.
 *
 * ⚠️ THE RULE MIRRORS `AnnouncementCtaPathValidator` EXACTLY, deliberately: accept a same-origin
 * relative path (`^/[A-Za-z0-9\-._~/]*$`) plus an optional query string, and reject anything carrying
 * a scheme, a host, or a protocol-relative `//` prefix. If one side changes, change the other.
 */
const RELATIVE_PATH = /^\/[A-Za-z0-9\-._~/]*$/;
const QUERY_STRING = /^[A-Za-z0-9\-._~/?:@!$&'()*+,;=%]*$/;

export function isSafeRelativePath(value: string | null | undefined): boolean {
  if (!value) {
    return false;
  }
  const path = value.trim();
  if (!path.startsWith("/") || path.startsWith("//")) {
    return false;
  }
  const queryStart = path.indexOf("?");
  const pathPart = queryStart < 0 ? path : path.slice(0, queryStart);
  const queryPart = queryStart < 0 ? null : path.slice(queryStart + 1);
  if (!RELATIVE_PATH.test(pathPart)) {
    return false;
  }
  return queryPart === null || QUERY_STRING.test(queryPart);
}

/**
 * @returns the trimmed path when it is safe to render, otherwise `null` so the caller renders no link
 *          at all rather than a broken or hostile one
 */
export function toSafeRelativePath(value: string | null | undefined): string | null {
  return isSafeRelativePath(value) ? (value as string).trim() : null;
}
