import { arrayMove } from "@dnd-kit/sortable";

export type ExamQuestionRef = {
  noteId: string;
  questionIndex: number;
};

export type ExamBuilderEntry = {
  id: string;
  noteId: string;
  questionRefs: ExamQuestionRef[];
};

export type ExamBuilderSection = {
  id: string;
  title: string;
  entries: ExamBuilderEntry[];
};

const SECTION_PREFIX = "Section ";

function buildSectionLetters(index: number): string {
  let nextIndex = index;
  let letters = "";
  do {
    letters = String.fromCharCode(65 + (nextIndex % 26)) + letters;
    nextIndex = Math.floor(nextIndex / 26) - 1;
  } while (nextIndex >= 0);
  return letters;
}

export function buildDefaultSectionTitle(index: number): string {
  return `${SECTION_PREFIX}${buildSectionLetters(index)}`;
}

export function createExamSectionId(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return `section-${Math.random().toString(36).slice(2, 10)}`;
}

export function createExamEntryId(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return `entry-${Math.random().toString(36).slice(2, 10)}`;
}

export function createQuestionRefs(noteId: string, questionCount: number): ExamQuestionRef[] {
  return Array.from({ length: Math.max(0, questionCount) }, (_, questionIndex) => ({
    noteId,
    questionIndex,
  }));
}

export function createExamEntry(noteId: string, questionRefs: ExamQuestionRef[]): ExamBuilderEntry {
  return {
    id: createExamEntryId(),
    noteId,
    questionRefs: questionRefs.map((questionRef) => ({ ...questionRef })),
  };
}

export function createWholeNoteEntry(noteId: string, questionCount: number): ExamBuilderEntry {
  return createExamEntry(noteId, createQuestionRefs(noteId, questionCount));
}

export function buildWholeNoteEntries(
  noteIds: string[],
  questionCountsByNoteId: Record<string, number>,
): ExamBuilderEntry[] {
  return noteIds.flatMap((noteId) => {
    const questionCount = questionCountsByNoteId[noteId] ?? 0;
    return questionCount > 0 ? [createWholeNoteEntry(noteId, questionCount)] : [];
  });
}

export function createExamSection(index: number, entries: ExamBuilderEntry[] = []): ExamBuilderSection {
  return {
    id: createExamSectionId(),
    title: buildDefaultSectionTitle(index),
    entries: entries.map((entry) => ({
      ...entry,
      questionRefs: entry.questionRefs.map((questionRef) => ({ ...questionRef })),
    })),
  };
}

export function createDefaultExamSections(
  noteIds: string[],
  questionCountsByNoteId: Record<string, number>,
): ExamBuilderSection[] {
  return [createExamSection(0, buildWholeNoteEntries(noteIds, questionCountsByNoteId))];
}

export function flattenExamSectionQuestionRefs(sections: ExamBuilderSection[]): ExamQuestionRef[] {
  return sections.flatMap((section) => section.entries.flatMap((entry) => entry.questionRefs));
}

export function flattenExamSectionNoteIds(sections: ExamBuilderSection[]): string[] {
  return sections.flatMap((section) => section.entries.map((entry) => entry.noteId));
}

export function countExamSectionQuestions(sections: ExamBuilderSection[]): number {
  return flattenExamSectionQuestionRefs(sections).length;
}

function moveSelection(ids: string[], entryId: string, direction: "up" | "down") {
  const currentIndex = ids.indexOf(entryId);
  if (currentIndex < 0) {
    return ids;
  }
  const targetIndex = direction === "up" ? currentIndex - 1 : currentIndex + 1;
  if (targetIndex < 0 || targetIndex >= ids.length) {
    return ids;
  }
  const next = [...ids];
  [next[currentIndex], next[targetIndex]] = [next[targetIndex], next[currentIndex]];
  return next;
}

export function reorderSelectedNoteIdsByDrag(ids: string[], activeId: string, overId: string | null | undefined) {
  if (!overId || activeId === overId) {
    return ids;
  }
  const activeIndex = ids.indexOf(activeId);
  const overIndex = ids.indexOf(overId);
  if (activeIndex < 0 || overIndex < 0) {
    return ids;
  }
  return arrayMove(ids, activeIndex, overIndex);
}

function mapEntriesByIds(entries: ExamBuilderEntry[], orderedIds: string[]): ExamBuilderEntry[] {
  const entriesById = new Map(entries.map((entry) => [entry.id, entry]));
  return orderedIds
    .map((entryId) => entriesById.get(entryId))
    .filter((entry): entry is ExamBuilderEntry => Boolean(entry));
}

export function renameExamSection(
  sections: ExamBuilderSection[],
  sectionId: string,
  title: string,
): ExamBuilderSection[] {
  return sections.map((section) => (
    section.id === sectionId
      ? { ...section, title }
      : section
  ));
}

export function reorderExamSections(
  sections: ExamBuilderSection[],
  activeSectionId: string,
  overSectionId: string | null | undefined,
): ExamBuilderSection[] {
  if (!overSectionId || activeSectionId === overSectionId) {
    return sections;
  }
  const activeIndex = sections.findIndex((section) => section.id === activeSectionId);
  const overIndex = sections.findIndex((section) => section.id === overSectionId);
  if (activeIndex < 0 || overIndex < 0) {
    return sections;
  }
  return arrayMove(sections, activeIndex, overIndex);
}

export function moveEntryWithinSection(
  sections: ExamBuilderSection[],
  sectionId: string,
  entryId: string,
  direction: "up" | "down",
): ExamBuilderSection[] {
  return sections.map((section) => {
    if (section.id !== sectionId) {
      return section;
    }
    const orderedIds = moveSelection(section.entries.map((entry) => entry.id), entryId, direction);
    return {
      ...section,
      entries: mapEntriesByIds(section.entries, orderedIds),
    };
  });
}

export function removeEntryFromExamSections(
  sections: ExamBuilderSection[],
  entryId: string,
): ExamBuilderSection[] {
  return sections.map((section) => ({
    ...section,
    entries: section.entries.filter((entry) => entry.id !== entryId),
  }));
}

export function moveEntryToSection(
  sections: ExamBuilderSection[],
  entryId: string,
  targetSectionId: string,
  targetIndex: number,
): ExamBuilderSection[] {
  const activeEntry = sections.flatMap((section) => section.entries).find((entry) => entry.id === entryId);
  if (!activeEntry) {
    return sections;
  }

  return sections.map((section) => {
    const filteredEntries = section.entries.filter((entry) => entry.id !== entryId);
    if (section.id !== targetSectionId) {
      return { ...section, entries: filteredEntries };
    }
    const nextEntries = [...filteredEntries];
    const safeIndex = Math.max(0, Math.min(targetIndex, nextEntries.length));
    nextEntries.splice(safeIndex, 0, activeEntry);
    return { ...section, entries: nextEntries };
  });
}

export function deleteExamSection(
  sections: ExamBuilderSection[],
  sectionId: string,
  strategy: "move_notes" | "delete_notes",
): ExamBuilderSection[] {
  const sectionIndex = sections.findIndex((section) => section.id === sectionId);
  if (sectionIndex < 0) {
    return sections;
  }
  const targetSectionIndex = sectionIndex > 0
    ? sectionIndex - 1
    : sections.length > 1
      ? 1
      : -1;

  return sections.flatMap((section, index) => {
    if (section.id !== sectionId) {
      if (strategy === "move_notes" && targetSectionIndex === index) {
        return [{
          ...section,
          entries: [...section.entries, ...sections[sectionIndex]!.entries],
        }];
      }
      return [section];
    }
    return [];
  });
}

function createEntriesFromQuestionRefs(questionRefs: ExamQuestionRef[]): ExamBuilderEntry[] {
  if (questionRefs.length === 0) {
    return [];
  }

  const entries: ExamBuilderEntry[] = [];
  let currentNoteId = questionRefs[0]!.noteId;
  let currentRefs: ExamQuestionRef[] = [];

  for (const questionRef of questionRefs) {
    if (questionRef.noteId !== currentNoteId && currentRefs.length > 0) {
      entries.push(createExamEntry(currentNoteId, currentRefs));
      currentNoteId = questionRef.noteId;
      currentRefs = [];
    }
    currentRefs.push({ ...questionRef });
  }

  if (currentRefs.length > 0) {
    entries.push(createExamEntry(currentNoteId, currentRefs));
  }

  return entries;
}

export function autoBalanceExamSections(sections: ExamBuilderSection[]): ExamBuilderSection[] {
  if (sections.length <= 1) {
    return sections;
  }

  const pooledQuestionRefs = flattenExamSectionQuestionRefs(sections);
  if (pooledQuestionRefs.length === 0) {
    return sections;
  }

  const sectionCount = sections.length;
  const baseCount = Math.floor(pooledQuestionRefs.length / sectionCount);
  const remainder = pooledQuestionRefs.length % sectionCount;
  let nextIndex = 0;

  return sections.map((section, sectionIndex) => {
    const sectionQuestionCount = baseCount + (sectionIndex < remainder ? 1 : 0);
    const questionSlice = pooledQuestionRefs.slice(nextIndex, nextIndex + sectionQuestionCount);
    nextIndex += sectionQuestionCount;
    return {
      ...section,
      entries: createEntriesFromQuestionRefs(questionSlice),
    };
  });
}
