import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PublicProfilePageClient } from "@/components/public/public-profile-page-client";
import { buildPublicCreatorPath } from "@/lib/public-note-path";
import { getServerPublicCreatorProfile } from "@/lib/server-public-profiles";
import { buildPageMetadata } from "@/lib/site-metadata";

type PublicCreatorPageProps = {
  params: Promise<{
    username: string;
  }>;
};

function buildDescription(displayName: string, publicNotesCount: number, totalCopies: number) {
  return `${displayName} has shared ${publicNotesCount} public ${publicNotesCount === 1 ? "note" : "notes"} on NoteLib with ${totalCopies} total ${totalCopies === 1 ? "copy" : "copies"}.`;
}

export async function generateMetadata({ params }: PublicCreatorPageProps): Promise<Metadata> {
  const { username } = await params;
  const result = await getServerPublicCreatorProfile(username);

  if (result.status === "not_found") {
    return {
      title: "Public Creator Not Found | NoteLib",
      robots: { index: false, follow: false },
    };
  }
  if (result.status === "private") {
    return {
      title: "This Profile Is Private | NoteLib",
      robots: { index: false, follow: false },
    };
  }

  return {
    ...buildPageMetadata({
      title: `${result.profile.displayName} (@${result.profile.username}) | NoteLib Public Profile`,
      description: buildDescription(result.profile.displayName, result.profile.publicNotesCount, result.profile.totalCopies),
      path: buildPublicCreatorPath(result.profile.username ?? username),
    }),
    robots: { index: false, follow: true },
  };
}

export default async function PublicCreatorPage({ params }: Readonly<PublicCreatorPageProps>) {
  const { username } = await params;
  const initialResult = await getServerPublicCreatorProfile(username);

  if (initialResult.status === "not_found") {
    notFound();
  }

  return <PublicProfilePageClient userId={username} lookupType="username" initialResult={initialResult} />;
}
