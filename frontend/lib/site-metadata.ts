import type { Metadata } from "next";

export const SITE_NAME = "NoteLib";
export const SITE_URL = "https://notelib.app";
// Versioned filename on purpose. Facebook, LinkedIn and X cache Open Graph images by URL and can
// serve a stale copy for weeks; X has no public re-scrape tool. Overwriting og-image.png in place
// would leave every previously-scraped link showing the old card. Bump the version to force a
// refetch. The previous /og-image.png is deliberately left in place so already-scraped links that
// re-fetch do not 404. Source: public/og-image-source.svg — render at exactly 1200x630.
export const DEFAULT_OG_IMAGE_URL = `${SITE_URL}/og-image-v2.png`;
export const DEFAULT_OG_IMAGE_ALT = "NoteLib — your notes become your study system. Turn notes into Study Packs with summaries, key concepts, quizzes, and flashcards.";

type PageMetadataInput = {
  title: string;
  description: string;
  path: string;
  type?: "website" | "article";
  noIndex?: boolean;
};

export function absoluteUrl(path: string) {
  return new URL(path, SITE_URL).toString();
}

export function truncateDescription(value: string, maxLength = 160) {
  const normalized = value.replaceAll(/\s+/g, " ").trim();
  if (normalized.length <= maxLength) {
    return normalized;
  }

  return `${normalized.slice(0, maxLength - 1).trimEnd()}…`;
}

export function buildPageMetadata({
  title,
  description,
  path,
  type = "website",
  noIndex = false,
}: PageMetadataInput): Metadata {
  const url = absoluteUrl(path);

  return {
    title,
    description,
    alternates: {
      canonical: url,
    },
    ...(noIndex ? { robots: { index: false, follow: true } } : {}),
    openGraph: {
      title,
      description,
      type,
      url,
      siteName: SITE_NAME,
      images: [
        {
          url: DEFAULT_OG_IMAGE_URL,
          width: 1200,
          height: 630,
          alt: DEFAULT_OG_IMAGE_ALT,
        },
      ],
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
      images: [DEFAULT_OG_IMAGE_URL],
    },
  };
}
