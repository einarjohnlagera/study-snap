// Lightweight signup email-domain typo suggestion. Catches mistyped popular
// providers (e.g. "0gmail.com" / "gmial.com" / "gmail.con") that pass HTML
// email validation but bounce — wasting send budget and sender reputation.
// Soft nudge only ("did you mean…"), never a block.

const POPULAR_DOMAINS = [
  "gmail.com",
  "googlemail.com",
  "yahoo.com",
  "ymail.com",
  "outlook.com",
  "hotmail.com",
  "live.com",
  "msn.com",
  "icloud.com",
  "me.com",
  "aol.com",
  "proton.me",
  "protonmail.com",
];

function levenshtein(a: string, b: string): number {
  const rows = a.length + 1;
  const cols = b.length + 1;
  const dist = Array.from({ length: rows }, () => new Array<number>(cols).fill(0));
  for (let i = 0; i < rows; i += 1) dist[i][0] = i;
  for (let j = 0; j < cols; j += 1) dist[0][j] = j;
  for (let i = 1; i < rows; i += 1) {
    for (let j = 1; j < cols; j += 1) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      dist[i][j] = Math.min(dist[i - 1][j] + 1, dist[i][j - 1] + 1, dist[i - 1][j - 1] + cost);
    }
  }
  return dist[a.length][b.length];
}

// Returns a corrected email (preserving the original local part) when the
// domain looks like a near-miss of a popular provider, else null.
export function suggestEmailCorrection(email: string): string | null {
  const trimmed = email.trim();
  const at = trimmed.lastIndexOf("@");
  if (at <= 0 || at === trimmed.length - 1) {
    return null;
  }
  const local = trimmed.slice(0, at);
  const domain = trimmed.slice(at + 1).toLowerCase();
  if (!domain.includes(".") || POPULAR_DOMAINS.includes(domain)) {
    return null;
  }

  let best: { domain: string; dist: number } | null = null;
  for (const candidate of POPULAR_DOMAINS) {
    const dist = levenshtein(domain, candidate);
    if (dist > 0 && dist <= 2 && (best === null || dist < best.dist)) {
      best = { domain: candidate, dist };
    }
  }
  return best ? `${local}@${best.domain}` : null;
}
