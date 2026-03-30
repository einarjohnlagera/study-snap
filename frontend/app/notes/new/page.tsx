import { NoteEditorPageClient } from "@/components/notes/note-editor-page-client";
import { normalizeNoteEntryMode, normalizeNoteEntrySource } from "@/lib/note-entry";

type NewNotePageProps = {
  searchParams?: Promise<{
    mode?: string | string[];
    source?: string | string[];
  }>;
};

export default async function NewNotePage({ searchParams }: Readonly<NewNotePageProps>) {
  const resolvedSearchParams = searchParams ? await searchParams : {};
  const initialMode = normalizeNoteEntryMode(resolvedSearchParams.mode);
  const initialSource = normalizeNoteEntrySource(resolvedSearchParams.source);

  return <NoteEditorPageClient initialMode={initialMode} initialSource={initialSource} />;
}
