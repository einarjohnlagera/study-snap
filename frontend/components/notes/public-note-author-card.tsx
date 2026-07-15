"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { getPublicCreatorProfile, getPublicProfile, type PublicProfileResponse } from "@/lib/api";
import { buildPublicCreatorOrProfilePath } from "@/lib/public-note-path";

type PublicNoteAuthorCardProps = {
  ownerUserId?: string | null;
  authorDisplayName?: string | null;
  authorUsername?: string | null;
};

function formatPublicNotesCount(count: number) {
  return `${count} public ${count === 1 ? "note" : "notes"}`;
}

export function PublicNoteAuthorCard({
  ownerUserId,
  authorDisplayName,
  authorUsername,
}: Readonly<PublicNoteAuthorCardProps>) {
  const [profile, setProfile] = useState<PublicProfileResponse | null>(null);
  const hasAuthorIdentity = Boolean(ownerUserId || authorUsername);
  const fallbackName = authorDisplayName?.trim() || "NoteLib learner";

  useEffect(() => {
    if (!hasAuthorIdentity) {
      return;
    }

    let cancelled = false;
    const loadProfile = authorUsername ? getPublicCreatorProfile : getPublicProfile;
    const profileKey = authorUsername ?? ownerUserId;
    if (!profileKey) {
      return;
    }

    void loadProfile(profileKey)
      .then((nextProfile) => {
        if (!cancelled) {
          setProfile(nextProfile);
        }
      })
      .catch(() => {
        // Private profiles intentionally fall back to the note's public attribution.
        if (!cancelled) {
          setProfile(null);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [authorUsername, hasAuthorIdentity, ownerUserId]);

  const displayName = profile?.displayName?.trim() || fallbackName;
  const bio = profile?.bio?.trim();
  const avatarLetter = useMemo(() => displayName.charAt(0).toUpperCase(), [displayName]);

  if (!hasAuthorIdentity) {
    return null;
  }

  return (
    <Link
      href={buildPublicCreatorOrProfilePath({ userId: ownerUserId, username: authorUsername })}
      className="group flex items-start gap-3 rounded-2xl border border-border bg-card p-4 transition-colors hover:border-blue-500/45 hover:bg-blue-500/5"
      aria-label={`View ${displayName}'s public profile`}
    >
      <span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-blue-600 text-sm font-semibold text-white dark:bg-blue-500">
        {avatarLetter}
      </span>
      <span className="min-w-0 space-y-1">
        <span className="block text-xs font-semibold uppercase tracking-wide text-foreground/55">About the author</span>
        <span className="block font-medium text-foreground group-hover:text-blue-700 dark:group-hover:text-blue-300">{displayName}</span>
        {bio ? <span className="line-clamp-2 block text-sm leading-relaxed text-foreground/70">{bio}</span> : null}
        {profile ? <span className="block text-xs text-foreground/60">{formatPublicNotesCount(profile.publicNotesCount)}</span> : null}
      </span>
    </Link>
  );
}
