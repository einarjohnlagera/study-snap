import { CollectionReadinessPageClient } from "./readiness-page-client";

export const metadata = {
  title: "Readiness | NoteLib",
};

export default async function CollectionReadinessPage({ params }: Readonly<{ params: Promise<{ id: string }> }>) {
  const { id } = await params;
  return <CollectionReadinessPageClient collectionId={id} />;
}
