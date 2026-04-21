import { arrayMove } from "@dnd-kit/sortable";

export type ExamBuilderSection = {
  id: string;
  title: string;
  noteIds: string[];
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

export function createExamSection(index: number, noteIds: string[] = []): ExamBuilderSection {
  return {
    id: createExamSectionId(),
    title: buildDefaultSectionTitle(index),
    noteIds: [...noteIds],
  };
}

export function createDefaultExamSections(noteIds: string[]): ExamBuilderSection[] {
  return [createExamSection(0, noteIds)];
}

export function flattenExamSectionNoteIds(sections: ExamBuilderSection[]): string[] {
  return sections.flatMap((section) => section.noteIds);
}

export function moveSelection(ids: string[], noteId: string, direction: "up" | "down") {
  const currentIndex = ids.indexOf(noteId);
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

export function addExamSection(sections: ExamBuilderSection[]): ExamBuilderSection[] {
  return [...sections, createExamSection(sections.length)];
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

export function moveNoteWithinSection(
  sections: ExamBuilderSection[],
  sectionId: string,
  noteId: string,
  direction: "up" | "down",
): ExamBuilderSection[] {
  return sections.map((section) => (
    section.id === sectionId
      ? { ...section, noteIds: moveSelection(section.noteIds, noteId, direction) }
      : section
  ));
}

export function removeNoteFromExamSections(
  sections: ExamBuilderSection[],
  noteId: string,
): ExamBuilderSection[] {
  return sections.map((section) => ({
    ...section,
    noteIds: section.noteIds.filter((candidateId) => candidateId !== noteId),
  }));
}

export function moveNoteToSection(
  sections: ExamBuilderSection[],
  noteId: string,
  targetSectionId: string,
  targetIndex: number,
): ExamBuilderSection[] {
  const noteExists = sections.some((section) => section.noteIds.includes(noteId));
  if (!noteExists) {
    return sections;
  }

  return sections.map((section) => {
    const filteredIds = section.noteIds.filter((candidateId) => candidateId !== noteId);
    if (section.id !== targetSectionId) {
      return { ...section, noteIds: filteredIds };
    }
    const nextIds = [...filteredIds];
    const safeIndex = Math.max(0, Math.min(targetIndex, nextIds.length));
    nextIds.splice(safeIndex, 0, noteId);
    return { ...section, noteIds: nextIds };
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
          noteIds: [...section.noteIds, ...sections[sectionIndex]!.noteIds],
        }];
      }
      return [section];
    }
    if (section.noteIds.length === 0) {
      return [];
    }
    if (strategy === "move_notes" && targetSectionIndex >= 0) {
      return [];
    }
    return [];
  });
}
