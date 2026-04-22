import { arrayMove } from "@dnd-kit/sortable";

export type ExamQuestionRef = {
  noteId: string;
  questionIndex: number;
};

export type ExamBalanceMode = "EVEN" | "SMART";

export type ExamBalanceSectionIntent =
  | "FLEXIBLE"
  | "FOUNDATIONAL"
  | "REVIEW"
  | "UNDERSTANDING"
  | "PROBLEM_SOLVING"
  | "APPLICATION"
  | "INTEGRATION"
  | "ADVANCED"
  | "CASE_BASED"
  | "CRITICAL_THINKING";

export type ExamQuestionBalanceMetadata = {
  concept?: string | null;
  difficulty?: string | null;
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
const EVEN_BALANCE_MODE: ExamBalanceMode = "EVEN";
const FLEXIBLE_SECTION_INTENT: ExamBalanceSectionIntent = "FLEXIBLE";
const COUNT_BALANCE_WEIGHT = 100;
const SAME_CONCEPT_PENALTY = 12;
const SAME_NOTE_PENALTY = 10;
const SAME_DIFFICULTY_PENALTY = 6;
const TEMPLATE_HINT_WEIGHT = 18;
const STABLE_TIEBREAKER_WEIGHT = 0.001;
const SECTION_INTENT_TARGET_PERCENTILES: Record<ExamBalanceSectionIntent, number | null> = {
  FLEXIBLE: null,
  FOUNDATIONAL: 0.15,
  REVIEW: 0.3,
  UNDERSTANDING: 0.4,
  PROBLEM_SOLVING: 0.55,
  APPLICATION: 0.7,
  INTEGRATION: 0.8,
  ADVANCED: 0.86,
  CASE_BASED: 0.9,
  CRITICAL_THINKING: 0.94,
};

type PreparedExamQuestion = ExamQuestionRef & {
  originalIndex: number;
  concept: string | null;
  difficulty: string | null;
};

type ExamBalanceSectionState = {
  assignedQuestions: PreparedExamQuestion[];
  noteCounts: Record<string, number>;
  conceptCounts: Record<string, number>;
  difficultyCounts: Record<string, number>;
};

export type ExamSectionBalanceOptions = {
  questionMetadataByRefKey?: Record<string, ExamQuestionBalanceMetadata | undefined>;
  sectionIntents?: Array<ExamBalanceSectionIntent | null | undefined>;
};

function buildSectionLetters(index: number): string {
  let nextIndex = index;
  let letters = "";
  do {
    letters = String.fromCodePoint(65 + (nextIndex % 26)) + letters;
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

export function createExamQuestionRefKey(noteId: string, questionIndex: number): string {
  return `${noteId}:${questionIndex}`;
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
          entries: [...section.entries, ...sections[sectionIndex].entries],
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
  let currentNoteId = questionRefs[0].noteId;
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

function normalizeBalanceMetadataValue(value: string | null | undefined): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function buildSectionQuestionTargets(totalQuestionCount: number, sectionCount: number): number[] {
  const baseCount = Math.floor(totalQuestionCount / sectionCount);
  const remainder = totalQuestionCount % sectionCount;
  return Array.from({ length: sectionCount }, (_, sectionIndex) => baseCount + (sectionIndex < remainder ? 1 : 0));
}

function prepareExamQuestionPool(
  sections: ExamBuilderSection[],
  questionMetadataByRefKey: Record<string, ExamQuestionBalanceMetadata | undefined>,
): PreparedExamQuestion[] {
  return flattenExamSectionQuestionRefs(sections).map((questionRef, originalIndex) => {
    const metadata = questionMetadataByRefKey[createExamQuestionRefKey(questionRef.noteId, questionRef.questionIndex)];
    return {
      ...questionRef,
      originalIndex,
      concept: normalizeBalanceMetadataValue(metadata?.concept),
      difficulty: normalizeBalanceMetadataValue(metadata?.difficulty),
    };
  });
}

function createEmptySectionBalanceState(): ExamBalanceSectionState {
  return {
    assignedQuestions: [],
    noteCounts: {},
    conceptCounts: {},
    difficultyCounts: {},
  };
}

function incrementCount(counts: Record<string, number>, key: string | null) {
  if (!key) {
    return;
  }
  counts[key] = (counts[key] ?? 0) + 1;
}

function countForKey(counts: Record<string, number>, key: string | null): number {
  if (!key) {
    return 0;
  }
  return counts[key] ?? 0;
}

function appendQuestionToSectionState(
  sectionState: ExamBalanceSectionState,
  question: PreparedExamQuestion,
): ExamBalanceSectionState {
  const nextState: ExamBalanceSectionState = {
    assignedQuestions: [...sectionState.assignedQuestions, question],
    noteCounts: { ...sectionState.noteCounts },
    conceptCounts: { ...sectionState.conceptCounts },
    difficultyCounts: { ...sectionState.difficultyCounts },
  };
  incrementCount(nextState.noteCounts, question.noteId);
  incrementCount(nextState.conceptCounts, question.concept);
  incrementCount(nextState.difficultyCounts, question.difficulty);
  return nextState;
}

function getSectionIntentTargetPercentile(
  sectionIntent: ExamBalanceSectionIntent | null | undefined,
): number | null {
  if (!sectionIntent) {
    return null;
  }
  return SECTION_INTENT_TARGET_PERCENTILES[sectionIntent] ?? null;
}

function resolveTemplateHintPenalty(
  question: PreparedExamQuestion,
  totalQuestionCount: number,
  sectionIntent: ExamBalanceSectionIntent | null | undefined,
): number {
  const targetPercentile = getSectionIntentTargetPercentile(sectionIntent);
  if (targetPercentile === null || totalQuestionCount <= 1) {
    return 0;
  }
  const questionPercentile = question.originalIndex / (totalQuestionCount - 1);
  return Math.abs(questionPercentile - targetPercentile) * TEMPLATE_HINT_WEIGHT;
}

function scoreSectionForSmartBalance(
  question: PreparedExamQuestion,
  sectionState: ExamBalanceSectionState,
  targetCount: number,
  sectionIndex: number,
  totalQuestionCount: number,
  sectionIntent: ExamBalanceSectionIntent | null | undefined,
): number {
  const fillRatio = sectionState.assignedQuestions.length / Math.max(targetCount, 1);
  const conceptPenalty = countForKey(sectionState.conceptCounts, question.concept) * SAME_CONCEPT_PENALTY;
  const notePenalty = countForKey(sectionState.noteCounts, question.noteId) * SAME_NOTE_PENALTY;
  const difficultyPenalty = countForKey(sectionState.difficultyCounts, question.difficulty) * SAME_DIFFICULTY_PENALTY;
  const templatePenalty = resolveTemplateHintPenalty(question, totalQuestionCount, sectionIntent);

  return (fillRatio * COUNT_BALANCE_WEIGHT)
    + conceptPenalty
    + notePenalty
    + difficultyPenalty
    + templatePenalty
    + (sectionIndex * STABLE_TIEBREAKER_WEIGHT);
}

function buildBalancedSections(
  sections: ExamBuilderSection[],
  questionPool: PreparedExamQuestion[],
  scoreSection: (
    question: PreparedExamQuestion,
    sectionState: ExamBalanceSectionState,
    targetCount: number,
    sectionIndex: number,
  ) => number,
): ExamBuilderSection[] {
  if (sections.length <= 1 || questionPool.length === 0) {
    return sections;
  }

  const sectionCount = sections.length;
  const targetCounts = buildSectionQuestionTargets(questionPool.length, sectionCount);
  const sectionStates = sections.map(() => createEmptySectionBalanceState());

  for (const question of questionPool) {
    let bestSectionIndex = -1;
    let bestScore = Number.POSITIVE_INFINITY;

    for (let sectionIndex = 0; sectionIndex < sectionCount; sectionIndex += 1) {
      const targetCount = targetCounts[sectionIndex] ?? 0;
      const sectionState = sectionStates[sectionIndex];
      if (!sectionState || targetCount <= sectionState.assignedQuestions.length) {
        continue;
      }
      const score = scoreSection(question, sectionState, targetCount, sectionIndex);
      if (score < bestScore) {
        bestScore = score;
        bestSectionIndex = sectionIndex;
      }
    }

    if (bestSectionIndex >= 0) {
      sectionStates[bestSectionIndex] = appendQuestionToSectionState(sectionStates[bestSectionIndex], question);
    }
  }

  return sections.map((section, sectionIndex) => {
    return {
      ...section,
      entries: createEntriesFromQuestionRefs(
        (sectionStates[sectionIndex]?.assignedQuestions ?? []).map(({ noteId, questionIndex }) => ({
          noteId,
          questionIndex,
        })),
      ),
    };
  });
}

export function evenBalanceExamSections(
  sections: ExamBuilderSection[],
  options: ExamSectionBalanceOptions = {},
): ExamBuilderSection[] {
  const questionPool = prepareExamQuestionPool(sections, options.questionMetadataByRefKey ?? {});
  if (sections.length <= 1 || questionPool.length === 0) {
    return sections;
  }

  const targetCounts = buildSectionQuestionTargets(questionPool.length, sections.length);
  let nextQuestionIndex = 0;

  return sections.map((section, sectionIndex) => {
    const sectionQuestionCount = targetCounts[sectionIndex] ?? 0;
    const questionSlice = questionPool.slice(nextQuestionIndex, nextQuestionIndex + sectionQuestionCount);
    nextQuestionIndex += sectionQuestionCount;
    return {
      ...section,
      entries: createEntriesFromQuestionRefs(
        questionSlice.map(({ noteId, questionIndex }) => ({ noteId, questionIndex })),
      ),
    };
  });
}

export function smartBalanceExamSections(
  sections: ExamBuilderSection[],
  options: ExamSectionBalanceOptions = {},
): ExamBuilderSection[] {
  const questionPool = prepareExamQuestionPool(sections, options.questionMetadataByRefKey ?? {});
  const sectionIntents = options.sectionIntents ?? [];
  return buildBalancedSections(
    sections,
    questionPool,
    (question, sectionState, targetCount, sectionIndex) => scoreSectionForSmartBalance(
      question,
      sectionState,
      targetCount,
      sectionIndex,
      questionPool.length,
      sectionIntents[sectionIndex] ?? FLEXIBLE_SECTION_INTENT,
    ),
  );
}

export function autoBalanceExamSections(
  sections: ExamBuilderSection[],
  options: ExamSectionBalanceOptions = {},
): ExamBuilderSection[] {
  return evenBalanceExamSections(sections, options);
}
