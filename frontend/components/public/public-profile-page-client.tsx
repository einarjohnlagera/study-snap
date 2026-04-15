"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown } from "lucide-react";
import { NoteQualityBadges } from "@/components/notes/note-quality-badge";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { SubjectBadge } from "@/components/notes/subject-badge";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ResponsiveActionButton, ResponsiveActionContent, ResponsiveActionLink } from "@/components/ui/action-button";
import { getAuthUser } from "@/lib/auth";
import { formatLearnerLevel, normalizeCourseProgram } from "@/lib/learning-profile";
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

function truncateShareUrl(url: string, maxLength = 58) {
  if (url.length <= maxLength) {
    return url;
  }
  return `${url.slice(0, maxLength - 3)}...`;
}

function formatLabelList(values: string[]): string {
  if (values.length === 0) {
    return "";
  }
  if (values.length === 1) {
    return values[0];
  }
  if (values.length === 2) {
    return `${values[0]} and ${values[1]}`;
  }
  return `${values.slice(0, -1).join(", ")}, and ${values.at(-1)}`;
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
  const [visibilityMessage, setVisibilityMessage] = useState<string | null>(null);
  const [updatingVisibility, setUpdatingVisibility] = useState(false);
  const [visibilityMenuOpen, setVisibilityMenuOpen] = useState(false);
  const [showShareModal, setShowShareModal] = useState(false);
  const [shareModalUrl, setShareModalUrl] = useState("");
  const [shareModalCopied, setShareModalCopied] = useState(false);
  const [showSharePrivateConfirm, setShowSharePrivateConfirm] = useState(false);

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

  useEffect(() => {
    if (!shareModalCopied) {
      return;
    }
    const timeout = globalThis.setTimeout(() => setShareModalCopied(false), 2000);
    return () => globalThis.clearTimeout(timeout);
  }, [shareModalCopied]);

  const notesCountLabel = useMemo(() => {
    if (!profile) {
      return "0 notes";
    }
    return `${profile.publicNotesCount} ${profile.publicNotesCount === 1 ? "note" : "notes"}`;
  }, [profile]);

  const metricItems = useMemo(() => {
    if (!profile) {
      return [];
    }
    const items = [
      {
        label: "Public Notes",
        value: profile.publicNotesCount,
      },
    ];
    if (profile.totalCopies > 0) {
      items.push({ label: "Total Copies", value: profile.totalCopies });
    }
    if (profile.totalShares > 0) {
      items.push({ label: "Total Shares", value: profile.totalShares });
    }
    if (profile.totalViews > 0) {
      items.push({ label: "Total Views", value: profile.totalViews });
    }
    return items;
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

  const learnerLevelLabel = useMemo(
    () => formatLearnerLevel(profile?.learnerLevel),
    [profile?.learnerLevel],
  );

  const courseProgramLabel = useMemo(() => {
    return normalizeCourseProgram(profile?.courseProgram);
  }, [profile?.courseProgram]);

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

  const topCoursePrograms = useMemo(() => {
    const counts = new Map<string, number>();
    for (const note of profile?.publicNotes ?? []) {
      const courseProgram = normalizeCourseProgram(note.courseProgram);
      if (courseProgram) {
        counts.set(courseProgram, (counts.get(courseProgram) ?? 0) + 1);
      }
    }
    return Array.from(counts.entries())
      .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
      .slice(0, 2)
      .map(([value]) => value);
  }, [profile?.publicNotes]);

  const topSubjects = useMemo(() => {
    const counts = new Map<string, number>();
    for (const note of profile?.publicNotes ?? []) {
      const subject = note.subject?.trim();
      if (subject) {
        counts.set(subject, (counts.get(subject) ?? 0) + 1);
      }
    }
    return Array.from(counts.entries())
      .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
      .slice(0, 3)
      .map(([value]) => value);
  }, [profile?.publicNotes]);

  const learningFocusSummary = useMemo(() => {
    const overlappingLabels = topCoursePrograms.filter((value) => topSubjects.includes(value));
    if (overlappingLabels.length > 0) {
      return `Mostly shares notes in ${formatLabelList(overlappingLabels)}.`;
    }
    if (topCoursePrograms.length > 0 && topSubjects.length > 0) {
      return `Mostly shares notes for ${formatLabelList(topCoursePrograms)}, especially ${formatLabelList(topSubjects)}.`;
    }
    if (topCoursePrograms.length > 0) {
      return `Mostly shares notes for ${formatLabelList(topCoursePrograms)}.`;
    }
    if (topSubjects.length > 0) {
      return `Mostly shares notes in ${formatLabelList(topSubjects)}.`;
    }
    return null;
  }, [topCoursePrograms, topSubjects]);

  const featuredNote = useMemo(() => {
    const notes = profile?.publicNotes ?? [];
    const bestByMetric = (
      key: "copyCount" | "viewCount" | "shareCount",
      label: string,
      noun: string,
    ) => {
      const sorted = [...notes].sort((left, right) => right[key] - left[key] || (left.title ?? "").localeCompare(right.title ?? ""));
      const top = sorted[0];
      if (!top || top[key] <= 0) {
        return null;
      }
      return {
        note: top,
        label,
        metric: `${top[key]} ${top[key] === 1 ? noun : `${noun}s`}`,
      };
    };

    return bestByMetric("copyCount", "Featured Note", "copy")
      ?? bestByMetric("viewCount", "Most Viewed Note", "view")
      ?? bestByMetric("shareCount", "Most Shared Note", "share");
  }, [profile?.publicNotes]);

  const handleShareProfile = () => {
    if (isOwner && profile && !profile.publicProfileVisible) {
      setShowSharePrivateConfirm(true);
      return;
    }
    const shareUrl = new URL(buildPublicProfilePath(userId), globalThis.location.origin).toString();
    setShareModalUrl(shareUrl);
    setShareModalCopied(false);
    setShowShareModal(true);
  };

  const handleMakePublicAndShare = async () => {
    if (!profile || updatingVisibility) {
      return;
    }
    setUpdatingVisibility(true);
    setShowSharePrivateConfirm(false);
    setVisibilityMessage(null);
    try {
      const updated = await updatePublicProfileVisibility({ publicProfileVisible: true });
      setProfile((current) => (current ? { ...current, publicProfileVisible: updated.publicProfileVisible } : current));
      const shareUrl = new URL(buildPublicProfilePath(userId), globalThis.location.origin).toString();
      setShareModalUrl(shareUrl);
      setShareModalCopied(false);
      setShowShareModal(true);
    } catch (error) {
      setVisibilityMessage(error instanceof Error ? error.message : "Could not update public profile visibility.");
    } finally {
      setUpdatingVisibility(false);
    }
  };

  const handleCopyShareLinkFromModal = async () => {
    if (!shareModalUrl) {
      return;
    }
    try {
      await navigator.clipboard.writeText(shareModalUrl);
      setShareModalCopied(true);
    } catch {
      // Silently fail — the URL is still visible for manual copy.
    }
  };

  const handleToggleVisibility = async () => {
    if (!profile || updatingVisibility) {
      return;
    }

    setUpdatingVisibility(true);
    setVisibilityMenuOpen(false);
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
        {!isOwner ? <BackLink href="/library/public" label="Public Library" /> : null}
        <header className="space-y-3 rounded-3xl border border-blue-500/20 bg-gradient-to-br from-blue-500/10 via-background to-emerald-500/10 p-6 shadow-sm sm:p-8">
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
      {!isOwner ? <BackLink href="/library/public" label="Public Library" /> : null}
      <header className="space-y-4 rounded-3xl border border-blue-500/20 bg-gradient-to-br from-blue-500/10 via-background to-emerald-500/10 p-6 shadow-sm sm:p-8">
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
                          className="w-full rounded px-3 py-2 text-left transition-colors hover:bg-muted/60 active:bg-muted/70"
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
                          className="w-full rounded px-3 py-2 text-left transition-colors hover:bg-muted/60 active:bg-muted/70"
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
                {learnerLevelLabel || courseProgramLabel ? (
                  <div className="flex flex-wrap gap-2">
                    {learnerLevelLabel ? (
                      <span className="rounded-full border border-border bg-background/70 px-3 py-1 text-xs font-medium text-foreground/75">
                        {learnerLevelLabel}
                      </span>
                    ) : null}
                    {courseProgramLabel ? (
                      <span className="rounded-full border border-border bg-background/70 px-3 py-1 text-xs font-medium text-foreground/75">
                        {courseProgramLabel}
                      </span>
                    ) : null}
                  </div>
                ) : null}
              </div>

              {metricItems.length > 0 ? (
                <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                  {metricItems.map((item) => (
                    <div key={item.label} className="rounded-2xl border border-border bg-background/75 px-4 py-3">
                      <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">{item.label}</p>
                      <p className="mt-1 text-lg font-semibold text-foreground">{item.value.toLocaleString()}</p>
                    </div>
                  ))}
                </div>
              ) : null}
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
              onClick={handleShareProfile}
              action="share"
              label="Share Profile"
            />
          </div>
        </div>

        {visibilityMessage ? (
          <p className="text-xs text-foreground/60">{visibilityMessage}</p>
        ) : null}
        {learningFocusSummary || subjects.length > 0 ? (
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Learning Focus</p>
            {learningFocusSummary ? (
              <p className="text-sm text-foreground/75">{learningFocusSummary}</p>
            ) : null}
            {subjects.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {subjects.map((subject) => (
                  <SubjectBadge key={subject} subject={subject} />
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
      </header>

      {featuredNote ? (
        <section aria-labelledby="featured-note" className="space-y-3">
          <h2 id="featured-note" className="text-xl font-semibold">Featured note</h2>
          <Card
            role="link"
            tabIndex={0}
            onClick={() => router.push(buildPublicLibraryNotePathFromSlug({ subject: featuredNote.note.subject, slug: featuredNote.note.slug }))}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                router.push(buildPublicLibraryNotePathFromSlug({ subject: featuredNote.note.subject, slug: featuredNote.note.slug }));
              }
            }}
            className="cursor-pointer space-y-3 border-blue-500/20 bg-blue-500/5 p-4 transition-colors hover:bg-blue-500/10 sm:p-5"
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full border border-blue-500/25 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                {featuredNote.label}
              </span>
              <span className="rounded-full border border-border bg-background/75 px-3 py-1 text-xs font-medium text-foreground/75">
                {featuredNote.metric}
              </span>
            </div>
            <div className="space-y-2">
              <h3 className="text-lg font-semibold">{featuredNote.note.title?.trim() || "Untitled note"}</h3>
              <p className="line-clamp-2 text-sm leading-relaxed text-foreground/75">{featuredNote.note.contentPreview}</p>
            </div>
          </Card>
        </section>
      ) : null}

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
                  courseProgram={normalizeCourseProgram(note.courseProgram)}
                  subject={note.subject}
                  tags={note.tags}
                  contentPreview={note.contentPreview}
                  summaryPreview={note.summaryPreview}
                  copyCount={note.copyCount > 0 ? note.copyCount : null}
                  viewCount={note.viewCount > 0 ? note.viewCount : null}
                  metadataBadges={(
                    <NoteQualityBadges
                      copyCount={note.copyCount}
                      viewCount={note.viewCount}
                    />
                  )}
                />
              </Card>
            ))}
          </div>
        )}
      </section>

      <AppModal
        isOpen={showSharePrivateConfirm}
        title="This profile is private"
        description="You need to make this profile public before sharing. Anyone with the link will be able to view your public profile and notes."
        onClose={() => {
          if (!updatingVisibility) {
            setShowSharePrivateConfirm(false);
          }
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowSharePrivateConfirm(false)}
              disabled={updatingVisibility}
            >
              Cancel
            </Button>
            <Button type="button" onClick={() => void handleMakePublicAndShare()} disabled={updatingVisibility}>
              {updatingVisibility ? "Updating..." : "Make Public & Share"}
            </Button>
          </div>
        )}
      />

      <AppModal
        isOpen={showShareModal}
        title="Share this profile"
        onClose={() => {
          setShowShareModal(false);
          setShareModalCopied(false);
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setShowShareModal(false);
                setShareModalCopied(false);
              }}
            >
              Close
            </Button>
            <Button type="button" onClick={() => void handleCopyShareLinkFromModal()}>
              {shareModalCopied ? "Copied" : "Copy Link"}
            </Button>
          </div>
        )}
      >
        <div className="space-y-2">
          <p className="text-xs uppercase tracking-wide text-foreground/60">Shareable URL</p>
          <p className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground/85">
            {truncateShareUrl(shareModalUrl)}
          </p>
          {shareModalCopied ? (
            <p className="text-xs text-emerald-700 dark:text-emerald-300">Link copied</p>
          ) : null}
        </div>
      </AppModal>
    </main>
  );
}
