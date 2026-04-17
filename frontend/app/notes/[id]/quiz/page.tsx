import { GeneratedQuizPreviewPageClient } from "@/components/notes/generated-quiz-preview-page-client";

type GeneratedQuizPreviewPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function GeneratedQuizPreviewPage({ params }: GeneratedQuizPreviewPageProps) {
  const { id } = await params;
  return <GeneratedQuizPreviewPageClient noteId={id} />;
}
