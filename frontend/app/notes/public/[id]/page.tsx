import { redirect } from "next/navigation";

type LegacyPublicNoteDetailRedirectProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function LegacyPublicNoteDetailRedirect({ params }: LegacyPublicNoteDetailRedirectProps) {
  const { id } = await params;
  redirect(`/public/notes/${id}`);
}
