import { PublicNoteDetailPageClient } from "@/components/notes/public-note-detail-page-client";

type PublicNoteDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function PublicNoteDetailPage({ params }: PublicNoteDetailPageProps) {
  const { id } = await params;
  return <PublicNoteDetailPageClient noteId={id} />;
}
