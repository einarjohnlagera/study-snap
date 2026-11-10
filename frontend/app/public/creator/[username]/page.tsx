import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PublicProfilePageClient } from "@/components/public/public-profile-page-client";
import { buildPublicCreatorPath } from "@/lib/public-note-path";
import { getServerPublicCreatorProfile } from "@/lib/server-public-profiles";
import { PUBLIC_PROFILE_UNAVAILABLE_METADATA } from "@/lib/public-profile-metadata";
import { buildPageMetadata } from "@/lib/site-metadata";

// Explicit, like the subject listing and exam hub: the 120 s hard-TTL profile cache is the only freshness layer for this page.
export const dynamic = "force-dynamic";

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
  let result: Awaited<ReturnType<typeof getServerPublicCreatorProfile>>;
  try {
    result = await getServerPublicCreatorProfile(username);
  } catch {
    // The page body reads the same value and fails the same way, so error.tsx renders. Only the head is handled here.
    return PUBLIC_PROFILE_UNAVAILABLE_METADATA;
  }

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
