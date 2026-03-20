import { NoteEditorPageClient } from "@/components/notes/note-editor-page-client";

type NoteDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function NoteDetailPage({ params }: NoteDetailPageProps) {
  const { id } = await params;
  return <NoteEditorPageClient noteId={id} />;
}
