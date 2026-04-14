import { NoteSessionReviewPageClient } from "@/components/notes/note-session-review-page-client";

type NoteSessionReviewPageProps = {
  params: Promise<{
    id: string;
    sessionId: string;
  }>;
};

export default async function NoteSessionReviewPage({ params }: NoteSessionReviewPageProps) {
  const { id, sessionId } = await params;
  return <NoteSessionReviewPageClient noteId={id} sessionId={sessionId} />;
}
