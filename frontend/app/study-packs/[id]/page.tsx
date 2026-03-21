import { PrivateNoteDetailPageClient } from "@/components/notes/private-note-detail-page-client";

type StudyPackNoteDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function StudyPackNoteDetailPage({ params }: StudyPackNoteDetailPageProps) {
  const { id } = await params;
  return <PrivateNoteDetailPageClient routeId={id} />;
}
