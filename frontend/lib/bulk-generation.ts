import type { BulkGenerateNoteGroup } from "@/lib/api";

export const MAX_BULK_GENERATION_TITLES = 50;

const SUBJECT_PREFIX_PATTERN = /^subject\s*:(.*)$/i;

export type BulkGenerationParseResult = {
  groups: BulkGenerateNoteGroup[];
  totalTitles: number;
  ignoredLineCount: number;
};

export function parseBulkGenerationText(rawText: string): BulkGenerationParseResult {
  const groups: BulkGenerateNoteGroup[] = [];
  let currentGroup: BulkGenerateNoteGroup | null = null;
  let ignoredLineCount = 0;

  for (const rawLine of rawText.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) {
      continue;
    }

    const subjectMatch = SUBJECT_PREFIX_PATTERN.exec(line);
    if (subjectMatch) {
      const subject = subjectMatch[1]?.trim() ?? "";
      if (!subject) {
        currentGroup = null;
        ignoredLineCount += 1;
        continue;
      }
      currentGroup = { subject, titles: [] };
      groups.push(currentGroup);
      continue;
    }

    if (!currentGroup) {
      ignoredLineCount += 1;
      continue;
    }
    currentGroup.titles.push(line);
  }

  const populatedGroups = groups.filter((group) => group.titles.length > 0);
  return {
    groups: populatedGroups,
    totalTitles: populatedGroups.reduce((total, group) => total + group.titles.length, 0),
    ignoredLineCount,
  };
}
