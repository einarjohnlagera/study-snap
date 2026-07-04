import { FlashcardsPageClient } from "./flashcards-page-client";

type FlashcardsPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function FlashcardsPage({ params }: Readonly<FlashcardsPageProps>) {
  const { id } = await params;
  return <FlashcardsPageClient noteId={id} />;
}
