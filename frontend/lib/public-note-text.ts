const PUBLIC_NOTE_HOOK_FALLBACK = "Review the key ideas, then test your understanding with a quick question.";
const MAX_HOOK_LENGTH = 180;

const MOJIBAKE_REPLACEMENTS: ReadonlyArray<readonly [string, string]> = [
  ["â€™", "'"],
  ["â€˜", "'"],
  ["â€œ", "\""],
  ["â€�", "\""],
  ["â€“", "-"],
  ["â€”", "-"],
  ["â€¦", "..."],
  ["Â ", " "],
  ["Â", ""],
];

function applyMojibakeReplacements(value: string): string {
  return MOJIBAKE_REPLACEMENTS.reduce(
    (current, [needle, replacement]) => current.replaceAll(needle, replacement),
    value,
  );
}

export function normalizePublicNoteText(value: string | null | undefined): string {
  if (!value) {
    return "";
  }

  return applyMojibakeReplacements(value)
    .replaceAll(/\r\n?/g, "\n")
    .replaceAll(/[\u2018\u2019]/g, "'")
    .replaceAll(/[\u201C\u201D]/g, "\"")
    .replaceAll(/[\u2013\u2014]/g, "-")
    .replaceAll("\u2026", "...")
    .replaceAll("\u00A0", " ")
    .replaceAll(/\t+/g, " ")
    .replaceAll(/[ \u200B]+/g, " ")
    .replaceAll(/\n{3,}/g, "\n\n")
    .trim();
}

function normalizeSentenceParts(value: string): string[] {
  return normalizePublicNoteText(value)
    .split(/(?<=[.!?])\s+/)
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
}

function isUsefulHookSentence(sentence: string, title: string): boolean {
  const normalizedSentence = sentence.trim().toLowerCase();
  const normalizedTitle = title.trim().toLowerCase();

  if (!normalizedSentence || normalizedSentence === normalizedTitle) {
    return false;
  }

  return normalizedSentence.length >= 24;
}

export function buildPublicNoteHook(params: {
  title: string;
  summary?: string | null;
  content?: string | null;
}): string {
  const title = normalizePublicNoteText(params.title);
  const candidates = [
    ...normalizeSentenceParts(params.summary ?? ""),
    ...normalizeSentenceParts(params.content ?? ""),
  ].filter((sentence) => isUsefulHookSentence(sentence, title));

  if (candidates.length === 0) {
    return PUBLIC_NOTE_HOOK_FALLBACK;
  }

  let hook = candidates[0] ?? PUBLIC_NOTE_HOOK_FALLBACK;
  const second = candidates[1];
  if (second) {
    const combined = `${hook} ${second}`.trim();
    if (combined.length <= MAX_HOOK_LENGTH) {
      hook = combined;
    }
  }

  return hook.length <= MAX_HOOK_LENGTH
    ? hook
    : `${hook.slice(0, MAX_HOOK_LENGTH - 1).trimEnd()}…`;
}

export function splitPublicNoteBlocks(value: string | null | undefined): string[] {
  const normalized = normalizePublicNoteText(value);
  if (!normalized) {
    return [];
  }

  return normalized
    .split(/\n{2,}/)
    .map((block) => block.trim())
    .filter((block) => block.length > 0);
}

export function stripMarkdownForPreview(text: string | null | undefined): string {
  if (!text) return "";
  // Find the first prose paragraph (skip table rows and separator lines).
  const paragraphs = text.split(/\n{2,}/);
  for (const para of paragraphs) {
    const trimmed = para.trim();
    if (!trimmed.startsWith("|") && !trimmed.startsWith("-")) {
      return trimmed
        .replaceAll(/\*\*/g, "")
        .replaceAll(/\s+/g, " ")
        .trim();
    }
  }
  return text
    .replaceAll(/\*\*/g, "")
    .replaceAll(/[|]/g, "")
    .replaceAll(/\s+/g, " ")
    .trim();
}

export { PUBLIC_NOTE_HOOK_FALLBACK };
