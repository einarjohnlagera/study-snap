import { ProgressReportClient } from "./progress-report-client";

export const metadata = {
  title: "My Progress | NoteLib",
};

type ProgressPageSearchParams = {
  collectionId?: string | string[];
};

function resolveCollectionId(searchParams: ProgressPageSearchParams | undefined): string | null {
  const rawCollectionId = searchParams?.collectionId;
  if (Array.isArray(rawCollectionId)) {
    return rawCollectionId[0] ?? null;
  }
  return rawCollectionId ?? null;
}

export default async function ProgressPage({
  searchParams,
}: Readonly<{
  searchParams?: Promise<ProgressPageSearchParams>;
}>) {
  const resolvedSearchParams = await searchParams;
  return <ProgressReportClient initialCollectionId={resolveCollectionId(resolvedSearchParams)} />;
}
