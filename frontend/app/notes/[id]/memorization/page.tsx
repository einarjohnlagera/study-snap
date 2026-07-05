import { MemorizationPageClient } from "./memorization-page-client";

type PageProps = {
  params: Promise<{ id: string }>;
};

export default async function MemorizationPage({ params }: Readonly<PageProps>) {
  const { id } = await params;
  return <MemorizationPageClient noteId={id} />;
}
