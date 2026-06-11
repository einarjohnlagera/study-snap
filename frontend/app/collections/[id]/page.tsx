import { CollectionDetailPageClient } from "./collection-detail-page-client";

export const metadata = {
  title: "Collection | NoteLib",
};

export default async function CollectionDetailPage({ params }: Readonly<{ params: Promise<{ id: string }> }>) {
  const { id } = await params;
  return <CollectionDetailPageClient collectionId={id} />;
}
