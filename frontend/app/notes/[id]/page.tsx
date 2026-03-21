import { PrivateNoteDetailPageClient } from "@/components/notes/private-note-detail-page-client";

type NoteDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function NoteDetailPage({ params }: NoteDetailPageProps) {
  const { id } = await params;
  return <PrivateNoteDetailPageClient routeId={id} />;
}
