import { NoteEditorPageClient } from "@/components/notes/note-editor-page-client";

type NewNotePageProps = {
  searchParams?: Promise<{
    focus?: string | string[];
  }>;
};

export default async function NewNotePage({ searchParams }: Readonly<NewNotePageProps>) {
  const resolvedSearchParams = searchParams ? await searchParams : {};
  const rawFocus = resolvedSearchParams.focus;
  const initialFocus = Array.isArray(rawFocus) ? rawFocus[0] ?? null : rawFocus ?? null;

  return <NoteEditorPageClient initialFocus={initialFocus} />;
}
