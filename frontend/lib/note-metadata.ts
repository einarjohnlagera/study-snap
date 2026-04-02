"use client";

type NoteMetadataLike = {
  title?: string | null;
  subject?: string | null;
  tags?: string[] | null;
};

export function hasExistingNoteMetadata(note: NoteMetadataLike): boolean {
  return Boolean(
    (note.title && note.title.trim().length > 0)
    || (note.subject && note.subject.trim().length > 0)
    || (note.tags && note.tags.length > 0),
  );
}
