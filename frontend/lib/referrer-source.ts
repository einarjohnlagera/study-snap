export type ReferrerSource = "google" | "other-search" | "social" | "direct";

const DIRECT_REFERRER_SOURCE: ReferrerSource = "direct";
const GOOGLE_HOSTNAME_PATTERN = /(^|\.)google\.[a-z.]+$/;
const OTHER_SEARCH_HOSTNAMES = [
  "bing.com",
  "yahoo.com",
  "search.yahoo.com",
  "duckduckgo.com",
  "search.brave.com",
  "ecosia.org",
  "startpage.com",
  "search.aol.com",
  "ask.com",
  "yandex.com",
  "yandex.ru",
  "baidu.com",
];
const SOCIAL_HOSTNAMES = [
  "facebook.com",
  "instagram.com",
  "tiktok.com",
  "twitter.com",
  "x.com",
  "linkedin.com",
  "reddit.com",
  "pinterest.com",
];

function matchesHostname(hostname: string, knownHostnames: readonly string[]): boolean {
  return knownHostnames.some((knownHostname) => (
    hostname === knownHostname || hostname.endsWith(`.${knownHostname}`)
  ));
}

export function bucketReferrerSource(referrer: string): ReferrerSource {
  if (!referrer.trim()) {
    return DIRECT_REFERRER_SOURCE;
  }

  try {
    const referrerUrl = new URL(referrer);
    if (globalThis.window !== undefined && referrerUrl.origin === globalThis.location.origin) {
      return DIRECT_REFERRER_SOURCE;
    }

    const hostname = referrerUrl.hostname.toLowerCase().replace(/^www\./, "");
    if (GOOGLE_HOSTNAME_PATTERN.test(hostname)) {
      return "google";
    }
    if (matchesHostname(hostname, OTHER_SEARCH_HOSTNAMES)) {
      return "other-search";
    }
    if (matchesHostname(hostname, SOCIAL_HOSTNAMES)) {
      return "social";
    }
  } catch {
    return DIRECT_REFERRER_SOURCE;
  }

  // Unknown external sources are intentionally grouped as direct so this remains a coarse,
  // privacy-preserving attribution signal rather than a new source inventory.
  return DIRECT_REFERRER_SOURCE;
}
