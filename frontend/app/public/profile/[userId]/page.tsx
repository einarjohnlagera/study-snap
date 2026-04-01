import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PublicProfilePageClient } from "@/components/public/public-profile-page-client";
import { buildPublicProfilePath } from "@/lib/public-note-path";
import { getServerPublicProfile } from "@/lib/server-public-profiles";
import { buildPageMetadata } from "@/lib/site-metadata";

type PublicProfilePageProps = {
  params: Promise<{
    userId: string;
  }>;
};

function buildDescription(displayName: string, publicNotesCount: number, totalCopies: number) {
  return `${displayName} has shared ${publicNotesCount} public ${publicNotesCount === 1 ? "note" : "notes"} on NoteLib with ${totalCopies} total ${totalCopies === 1 ? "copy" : "copies"}.`;
}

export async function generateMetadata({ params }: PublicProfilePageProps): Promise<Metadata> {
  const { userId } = await params;
  const result = await getServerPublicProfile(userId);

  if (result.status === "not_found") {
    return {
      title: "Public Profile Not Found | NoteLib",
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
      title: `${result.profile.displayName} | NoteLib Public Profile`,
      description: buildDescription(result.profile.displayName, result.profile.publicNotesCount, result.profile.totalCopies),
      path: buildPublicProfilePath(userId),
    }),
    robots: { index: false, follow: true },
  };
}

export default async function PublicProfilePage({ params }: Readonly<PublicProfilePageProps>) {
  const { userId } = await params;
  const initialResult = await getServerPublicProfile(userId);

  if (initialResult.status === "not_found") {
    notFound();
  }

  return <PublicProfilePageClient userId={userId} initialResult={initialResult} />;
}
