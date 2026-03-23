import { notFound, redirect } from "next/navigation";
import { buildPublicLibraryNotePathFromDetail } from "@/lib/public-note-path";
import { getServerPublicNoteById } from "@/lib/server-public-notes";

type PublicNoteDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function PublicNoteDetailPage({ params }: PublicNoteDetailPageProps) {
  const { id } = await params;
  const note = await getServerPublicNoteById(id);
  if (!note) {
    notFound();
  }
  redirect(buildPublicLibraryNotePathFromDetail(note));
}
