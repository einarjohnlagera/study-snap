import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PublicProfilePageClient } from "@/components/public/public-profile-page-client";
import { buildPublicProfilePath } from "@/lib/public-note-path";
import { getServerPublicProfile } from "@/lib/server-public-profiles";
import { PUBLIC_PROFILE_UNAVAILABLE_METADATA } from "@/lib/public-profile-metadata";
import { buildPageMetadata } from "@/lib/site-metadata";

// Explicit, like the subject listing and exam hub: the 120 s hard-TTL profile cache is the only freshness layer for this page.
export const dynamic = "force-dynamic";

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
  let result: Awaited<ReturnType<typeof getServerPublicProfile>>;
  try {
    result = await getServerPublicProfile(userId);
  } catch {
    // The page body reads the same value and fails the same way, so error.tsx renders. Only the head is handled here.
    return PUBLIC_PROFILE_UNAVAILABLE_METADATA;
  }

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
