import type { NoteCollectionDetail, NoteCollectionItem, NoteListItemResponse } from "@/lib/api";

type CollectionExamCandidate = Pick<NoteCollectionItem, "noteId" | "position" | "studyPackStatus" | "generatedQuizId">;

export function sortCollectionItemsByPosition<T extends Pick<NoteCollectionItem, "position">>(items: T[]): T[] {
  return [...items].sort((left, right) => left.position - right.position);
}

// Teacher Exam Builder eligibility: the DOCX / quiz-link builder exports an
// already-generated quiz, so a note must have one.
export function canIncludeCollectionItemInExam(item: Pick<NoteCollectionItem, "generatedQuizId">): boolean {
  return Boolean(item.generatedQuizId);
}

// Premium-exam (Long / Board / Interview) eligibility: these modes generate
// their own question set from the Study Pack at start, so a ready Study Pack is
// the only requirement — no pre-generated quiz needed.
export function canIncludeCollectionItemInPremiumExam(item: Pick<NoteCollectionItem, "studyPackStatus">): boolean {
  return item.studyPackStatus === "STUDY_PACK_READY";
}

export function getCollectionQuizReadyNoteIds(items: CollectionExamCandidate[]): string[] {
  return sortCollectionItemsByPosition(items)
    .filter(canIncludeCollectionItemInExam)
    .map((item) => item.noteId);
}

export function getCollectionPremiumExamReadyNoteIds(items: CollectionExamCandidate[]): string[] {
  return sortCollectionItemsByPosition(items)
    .filter(canIncludeCollectionItemInPremiumExam)
    .map((item) => item.noteId);
}

export function getCollectionPrimaryPremiumExamItem(items: CollectionExamCandidate[]): CollectionExamCandidate | null {
  return sortCollectionItemsByPosition(items).find(canIncludeCollectionItemInPremiumExam) ?? null;
}

export function resolveCollectionScopedSourceNotes(
  collection: NoteCollectionDetail,
  notes: NoteListItemResponse[],
  primaryNoteId: string,
  options: { requireStudyPackId: boolean },
): NoteListItemResponse[] {
  const eligibleNoteIds = new Set(getCollectionPremiumExamReadyNoteIds(collection.items));
  const noteById = new Map(notes.map((note) => [note.id, note]));

  return sortCollectionItemsByPosition(collection.items)
    .filter((item) => eligibleNoteIds.has(item.noteId))
    .filter((item) => item.noteId !== primaryNoteId)
    .map((item) => noteById.get(item.noteId))
    .filter((note): note is NoteListItemResponse => Boolean(note))
    .filter((note) => note.studyPackStatus === "STUDY_PACK_READY")
    .filter((note) => !options.requireStudyPackId || Boolean(note.studyPackId));
}
