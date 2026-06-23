import type { NoteCollectionDetail, NoteCollectionItem, NoteListItemResponse } from "@/lib/api";

type CollectionExamCandidate = Pick<NoteCollectionItem, "noteId" | "position" | "studyPackStatus" | "generatedQuizId">;

export function sortCollectionItemsByPosition<T extends Pick<NoteCollectionItem, "position">>(items: T[]): T[] {
  return [...items].sort((left, right) => left.position - right.position);
}

export function canIncludeCollectionItemInExam(item: Pick<NoteCollectionItem, "studyPackStatus" | "generatedQuizId">): boolean {
  return item.studyPackStatus === "STUDY_PACK_READY" && Boolean(item.generatedQuizId);
}

export function getCollectionQuizReadyNoteIds(items: CollectionExamCandidate[]): string[] {
  return sortCollectionItemsByPosition(items)
    .filter(canIncludeCollectionItemInExam)
    .map((item) => item.noteId);
}

export function getCollectionPrimaryExamItem(items: CollectionExamCandidate[]): CollectionExamCandidate | null {
  return sortCollectionItemsByPosition(items).find(canIncludeCollectionItemInExam) ?? null;
}

export function resolveCollectionScopedSourceNotes(
  collection: NoteCollectionDetail,
  notes: NoteListItemResponse[],
  primaryNoteId: string,
  options: { requireStudyPackId: boolean },
): NoteListItemResponse[] {
  const eligibleNoteIds = new Set(getCollectionQuizReadyNoteIds(collection.items));
  const noteById = new Map(notes.map((note) => [note.id, note]));

  return sortCollectionItemsByPosition(collection.items)
    .filter((item) => eligibleNoteIds.has(item.noteId))
    .filter((item) => item.noteId !== primaryNoteId)
    .map((item) => noteById.get(item.noteId))
    .filter((note): note is NoteListItemResponse => Boolean(note))
    .filter((note) => note.studyPackStatus === "STUDY_PACK_READY")
    .filter((note) => !options.requireStudyPackId || Boolean(note.studyPackId));
}
