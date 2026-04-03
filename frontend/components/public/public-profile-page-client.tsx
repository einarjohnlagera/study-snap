"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown } from "lucide-react";
import { OwnedNoteCardMenu } from "@/components/notes/owned-note-card-menu";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { SubjectBadge } from "@/components/notes/subject-badge";
import { Card } from "@/components/ui/card";
import { ResponsiveActionButton, ResponsiveActionContent, ResponsiveActionLink } from "@/components/ui/action-button";
import { getAuthUser } from "@/lib/auth";
import {
  ApiRequestError,
  getPublicProfile,
  type ProfileType,
  type PublicProfileResponse,
  updatePublicProfileVisibility,
} from "@/lib/api";
import {
  buildPublicLibraryNotePathFromSlug,
  buildPublicProfilePath,
} from "@/lib/public-note-path";
import type { ServerPublicProfileResult } from "@/lib/server-public-profiles";

type PublicProfilePageClientProps = {
  userId: string;
  initialResult: ServerPublicProfileResult;
};

const PROFILE_TYPE_LABELS: Record<ProfileType, string> = {
  STUDENT: "Student",
  BOARD_EXAM: "Board Exam",
  TEACHER: "Teacher",
  PARENT: "Parent",
  PROFESSIONAL: "Professional",
};

function formatProfileType(profileType: string | null) {
  if (!profileType) {
    return "Community member";
  }
  return PROFILE_TYPE_LABELS[profileType as ProfileType] ?? profileType.replaceAll("_", " ");
}

function visibilityChip(publicProfileVisible: boolean) {
  if (publicProfileVisible) {
    return "border-blue-500/40 bg-blue-500/10 text-blue-700 dark:text-blue-300";
  }
  return "border-border bg-muted/50 text-foreground/70";
}

export function PublicProfilePageClient({
  userId,
  initialResult,
}: Readonly<PublicProfilePageClientProps>) {
  const router = useRouter();
  const visibilityMenuRef = useRef<HTMLDivElement | null>(null);
  const [currentUserId, setCurrentUserId] = useState<string | null>(() => getAuthUser()?.id ?? null);
  const [profile, setProfile] = useState<PublicProfileResponse | null>(
    initialResult.status === "ok" ? initialResult.profile : null,
  );
  const [pageState, setPageState] = useState<"loading" | "ready" | "private" | "error">(
    initialResult.status === "ok"
      ? "ready"
      : initialResult.status === "private"
        ? "private"
        : "loading",
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [shareMessage, setShareMessage] = useState<string | null>(null);
  const [visibilityMessage, setVisibilityMessage] = useState<string | null>(null);
  const [noteActionMessage, setNoteActionMessage] = useState<string | null>(null);
  const [updatingVisibility, setUpdatingVisibility] = useState(false);
  const [visibilityMenuOpen, setVisibilityMenuOpen] = useState(false);

  const isOwner = currentUserId === userId;

  useEffect(() => {
    const syncAuthUser = () => {
      setCurrentUserId(getAuthUser()?.id ?? null);
    };

    syncAuthUser();
    globalThis.addEventListener("studysnap-auth-change", syncAuthUser);
    return () => globalThis.removeEventListener("studysnap-auth-change", syncAuthUser);
  }, []);

  useEffect(() => {
    if (!isOwner) {
      return;
    }

    let cancelled = false;
    setPageState((current) => (current === "private" ? "loading" : current));
    setErrorMessage(null);

    void getPublicProfile(userId)
      .then((nextProfile) => {
        if (cancelled) {
          return;
        }
        setProfile(nextProfile);
        setPageState("ready");
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        if (error instanceof ApiRequestError && error.code === "PUBLIC_PROFILE_PRIVATE") {
          setProfile(null);
          setPageState("private");
          return;
        }
        setErrorMessage(error instanceof Error ? error.message : "Could not load public profile.");
        setPageState("error");
      });

    return () => {
      cancelled = true;
    };
  }, [isOwner, userId]);

  useEffect(() => {
    if (!visibilityMenuOpen) {
      return;
    }

    const handleOutsideClick = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Node)) {
        return;
      }
      if (visibilityMenuRef.current && !visibilityMenuRef.current.contains(target)) {
        setVisibilityMenuOpen(false);
      }
    };

    globalThis.addEventListener("mousedown", handleOutsideClick);
    return () => globalThis.removeEventListener("mousedown", handleOutsideClick);
  }, [visibilityMenuOpen]);

  const notesCountLabel = useMemo(() => {
    if (!profile) {
      return "0 notes";
    }
    return `${profile.publicNotesCount} ${profile.publicNotesCount === 1 ? "note" : "notes"}`;
  }, [profile]);

  const avatarLetter = useMemo(() => {
    const fallback = profile?.displayName?.trim() || "U";
    return fallback.charAt(0).toUpperCase();
  }, [profile?.displayName]);

  const bioText = useMemo(() => {
    const normalized = profile?.bio?.trim();
    return normalized && normalized.length > 0
      ? normalized
      : "This user hasn't added a bio yet.";
  }, [profile?.bio]);

  const subjects = useMemo(() => {
    const values = new Set<string>();
    for (const note of profile?.publicNotes ?? []) {
      const subject = note.subject?.trim();
      if (subject) {
        values.add(subject);
      }
    }
    return Array.from(values).sort((left, right) => left.localeCompare(right));
  }, [profile?.publicNotes]);

  const handleShareProfile = async () => {
    try {
      const shareUrl = new URL(buildPublicProfilePath(userId), globalThis.location.origin).toString();
      await navigator.clipboard.writeText(shareUrl);
      setShareMessage("Public profile link copied.");
      setVisibilityMessage(null);
    } catch {
      setShareMessage("Could not copy public profile link.");
    }
  };

  const handleToggleVisibility = async () => {
    if (!profile || updatingVisibility) {
      return;
    }

    setUpdatingVisibility(true);
    setVisibilityMenuOpen(false);
    setShareMessage(null);
    setVisibilityMessage(null);
    try {
      const updated = await updatePublicProfileVisibility({
        publicProfileVisible: !profile.publicProfileVisible,
      });
      setProfile((current) => (current ? { ...current, publicProfileVisible: updated.publicProfileVisible } : current));
      setVisibilityMessage(
        updated.publicProfileVisible
          ? "Public profile is now visible."
          : "Public profile is now private.",
      );
    } catch (error) {
      setVisibilityMessage(error instanceof Error ? error.message : "Could not update public profile visibility.");
    } finally {
      setUpdatingVisibility(false);
    }
  };

  if (pageState === "loading") {
    return (
      <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
        <Card className="p-6">Loading public profile...</Card>
      </main>
    );
  }

  if (pageState === "error") {
    return (
      <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
        <Card className="space-y-3 p-6">
          <h1 className="text-xl font-semibold">Could not load public profile</h1>
          <p className="text-sm text-foreground/75">{errorMessage ?? "Could not load public profile."}</p>
        </Card>
      </main>
    );
  }

  if (pageState === "private" || !profile) {
    return (
      <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
        <header className="space-y-3 rounded-3xl border border-blue-500/20 bg-gradient-to-br from-blue-500/10 via-background to-emerald-500/10 p-6 shadow-sm sm:p-8">
          <ResponsiveActionButton type="button" variant="outline" onClick={() => router.back()} action="back" label="Back" className="w-full sm:w-auto" />
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full border border-blue-500/25 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              Public Profile
            </span>
          </div>
          <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">This profile is private.</h1>
        </header>
        <Card className="space-y-3 p-4 sm:p-6">
          <p className="text-sm text-foreground/75">This profile is private.</p>
        </Card>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <header className="space-y-4 rounded-3xl border border-blue-500/20 bg-gradient-to-br from-blue-500/10 via-background to-emerald-500/10 p-6 shadow-sm sm:p-8">
        <ResponsiveActionButton type="button" variant="outline" onClick={() => router.back()} action="back" label="Back" className="w-full sm:w-auto" />

        <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div className="flex min-w-0 gap-4">
            <div className="inline-flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xl font-semibold text-white dark:bg-blue-500">
              {avatarLetter}
            </div>
            <div className="min-w-0 space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-full border border-blue-500/25 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                  Public Profile
                </span>
                {profile.isOfficial ? (
                  <span className="rounded-full border border-blue-500/35 bg-blue-500/10 px-3 py-1 text-xs font-medium text-blue-700 dark:text-blue-300">
                    Official
                  </span>
                ) : null}
                <span className="rounded-full border border-border bg-background/70 px-3 py-1 text-xs font-medium text-foreground/75">
                  {formatProfileType(profile.profileType)}
                </span>
                {isOwner ? (
                  <div className="relative" ref={visibilityMenuRef}>
                    <button
                      type="button"
                      className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium ${visibilityChip(profile.publicProfileVisible)}`}
                      onClick={() => setVisibilityMenuOpen((open) => !open)}
                      aria-haspopup="menu"
                      aria-expanded={visibilityMenuOpen}
                      disabled={updatingVisibility}
                    >
                      <ResponsiveActionContent
                        action={profile.publicProfileVisible ? "public" : "private"}
                        label={profile.publicProfileVisible ? "Public" : "Private"}
                        showTextOnMobile
                        iconClassName="h-3.5 w-3.5"
                      />
                      <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                    {visibilityMenuOpen ? (
                      <div className="absolute left-0 top-9 z-20 w-64 rounded-md border border-border bg-background p-1 shadow-sm">
                        <button
                          type="button"
                          className="w-full rounded px-3 py-2 text-left hover:bg-muted/60"
                          onClick={() => void handleToggleVisibility()}
                          disabled={!profile.publicProfileVisible}
                        >
                          <p className="inline-flex items-center gap-2 text-sm font-medium">
                            <ResponsiveActionContent action="private" label="Private" showTextOnMobile iconClassName="h-4 w-4" />
                          </p>
                          <p className="text-xs text-foreground/70">Hide this profile from other users.</p>
                        </button>
                        <button
                          type="button"
                          className="w-full rounded px-3 py-2 text-left hover:bg-muted/60"
                          onClick={() => void handleToggleVisibility()}
                          disabled={profile.publicProfileVisible}
                        >
                          <p className="inline-flex items-center gap-2 text-sm font-medium">
                            <ResponsiveActionContent action="public" label="Public" showTextOnMobile iconClassName="h-4 w-4" />
                          </p>
                          <p className="text-xs text-foreground/70">Allow other users to view and share this profile.</p>
                        </button>
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </div>

              <div className="space-y-2">
                <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{profile.displayName}</h1>
                <p className="max-w-2xl text-sm leading-relaxed text-foreground/75 sm:text-base">{bioText}</p>
              </div>

              <div className="flex flex-wrap gap-3 text-sm text-foreground/70">
                <span className="rounded-full border border-border bg-background/70 px-3 py-1">
                  {profile.publicNotesCount} {profile.publicNotesCount === 1 ? "public note" : "public notes"}
                </span>
                <span className="rounded-full border border-border bg-background/70 px-3 py-1">
                  {profile.totalCopies} {profile.totalCopies === 1 ? "copy" : "copies"}
                </span>
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-2 sm:flex-row lg:flex-col">
            {isOwner ? (
              <ResponsiveActionLink href="/profile" action="edit" label="Edit Profile" variant="outline" className="w-full sm:w-auto lg:w-full" />
            ) : null}
            <ResponsiveActionButton
              type="button"
              variant="outline"
              className="w-full sm:w-auto lg:w-full"
              onClick={() => void handleShareProfile()}
              action="share"
              label="Share Profile"
            />
          </div>
        </div>

        {shareMessage ? (
          <p className="text-xs text-foreground/60">{shareMessage}</p>
        ) : null}
        {visibilityMessage ? (
          <p className="text-xs text-foreground/60">{visibilityMessage}</p>
        ) : null}
        {noteActionMessage ? (
          <p className="text-xs text-foreground/60">{noteActionMessage}</p>
        ) : null}

        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Subjects</p>
          {subjects.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {subjects.map((subject) => (
                <SubjectBadge key={subject} subject={subject} />
              ))}
            </div>
          ) : (
            <p className="text-sm text-foreground/70">No public subjects yet.</p>
          )}
        </div>
      </header>

      <section aria-labelledby="public-profile-notes" className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h2 id="public-profile-notes" className="text-xl font-semibold">
            Public notes
          </h2>
          <p className="text-sm text-foreground/65">{notesCountLabel}</p>
        </div>

        {profile.publicNotes.length === 0 ? (
          <Card className="space-y-3 p-4 sm:p-6">
            <h3 className="text-lg font-semibold">No public notes yet</h3>
            <p className="text-sm text-foreground/75">This user has no public notes yet.</p>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            {profile.publicNotes.map((note) => (
              <Card
                key={note.noteId}
                role="link"
                tabIndex={0}
                onClick={() => router.push(buildPublicLibraryNotePathFromSlug({ subject: note.subject, slug: note.slug }))}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    router.push(buildPublicLibraryNotePathFromSlug({ subject: note.subject, slug: note.slug }));
                  }
                }}
                className="flex h-full cursor-pointer flex-col justify-between space-y-4 p-4 transition-colors hover:bg-muted/40 hover:shadow-md sm:p-6"
              >
                <SharedNoteCard
                  title={note.title}
                  subject={note.subject}
                  tags={note.tags}
                  contentPreview={note.contentPreview}
                  summaryPreview={note.summaryPreview}
                  copyCount={note.copyCount}
                  actionSlot={isOwner ? (
                    <OwnedNoteCardMenu
                      noteId={note.noteId}
                      title={note.title}
                      subject={note.subject}
                      visibility="PUBLIC"
                      studyPackStatus="STUDY_PACK_READY"
                      surface="publicProfile"
                      onRemoved={() => {
                        setProfile((current) => {
                          if (!current) {
                            return current;
                          }
                          return {
                            ...current,
                            publicNotes: current.publicNotes.filter((candidate) => candidate.noteId !== note.noteId),
                            publicNotesCount: Math.max(0, current.publicNotesCount - 1),
                            totalCopies: Math.max(0, current.totalCopies - note.copyCount),
                          };
                        });
                      }}
                      onMessage={(message) => {
                        setShareMessage(null);
                        setVisibilityMessage(null);
                        setNoteActionMessage(message);
                      }}
                      onError={(message) => {
                        setShareMessage(null);
                        setVisibilityMessage(null);
                        setNoteActionMessage(message);
                      }}
                    />
                  ) : undefined}
                />
              </Card>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
