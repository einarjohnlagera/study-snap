import { NoteEditorPageClient } from "@/components/notes/note-editor-page-client";

type EditNotePageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function EditNotePage({ params }: EditNotePageProps) {
  const { id } = await params;
  return <NoteEditorPageClient noteId={id} />;
}
