// Maximum length of a single topic. Mirrors the topic input's maxLength so a
// programmatically-set (pasted) value is clamped the same way typed input is.
export const MAX_TOPIC_LENGTH = 160;

// Known leading list markers to strip on paste: bullets and numbered prefixes,
// each REQUIRING trailing whitespace. The trailing-whitespace guard is what
// keeps real topics intact — e.g. "1.5 inch standards" (no space after the dot)
// and ".NET basics" are not markers and are left untouched.
const LEADING_LIST_MARKER = /^\s*(?:[*\-–—•·‣◦]|\d+[.)]|\(\d+\))\s+/;

function cleanTopicLine(line: string): string {
  // Strip a single leading marker (not looped), then trim and clamp to length.
  const withoutMarker = line.replace(LEADING_LIST_MARKER, "").trim();
  return withoutMarker.slice(0, MAX_TOPIC_LENGTH).trim();
}

/**
 * Parse a pasted block into discrete topics.
 *
 * Splits on newlines only (never commas — topics legitimately contain commas),
 * handles CRLF, strips known leading list markers, trims, clamps each line to
 * {@link MAX_TOPIC_LENGTH}, and drops empties. Pure and side-effect-free so the
 * trimming-safety rules can be unit-tested independently of the React handler.
 */
export function parsePastedTopics(text: string): string[] {
  if (!text) {
    return [];
  }
  return text
    .split(/\r?\n/)
    .map(cleanTopicLine)
    .filter(Boolean);
}
