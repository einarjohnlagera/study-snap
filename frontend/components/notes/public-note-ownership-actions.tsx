"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import { getAuthUser } from "@/lib/auth";
import { trackAnalyticsEvent } from "@/lib/api";
import { SubjectBadge } from "@/components/notes/subject-badge";
import { isPublicNoteOwner, resolvePublicNoteAuthorMeta } from "@/lib/public-note-author";
import { buildPublicCreatorOrProfilePath, buildPublicLibraryNotePath } from "@/lib/public-note-path";
import { buildPublicLibraryUrl, slugifyPublicLibraryFilterValue } from "@/lib/public-library-url";
import { PublicSeoCopyCta } from "./public-seo-copy-cta";

type PublicNoteOwnershipActionsProps = {
  noteId: string;
  ownerUserId: string | null;
  isCurrentUser?: boolean;
  subject?: string | null;
  title?: string | null;
};

type PublicNoteAuthorLineProps = {
  ownerUserId: string | null;
  authorDisplayName: string;
  authorUsername?: string | null;
  isOfficialAuthor: boolean;
  isCurrentUser: boolean;
  subject?: string | null;
};

export function PublicNoteAuthorLine({
  ownerUserId,
  authorDisplayName,
  authorUsername,
  isOfficialAuthor,
  isCurrentUser,
  subject,
}: Readonly<PublicNoteAuthorLineProps>) {
  const [currentUserId, setCurrentUserId] = useState<string | null>(() => getAuthUser()?.id ?? null);

  useEffect(() => {
    const syncAuth = () => {
      setCurrentUserId(getAuthUser()?.id ?? null);
    };
    syncAuth();
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuth);
    };
  }, []);

  const authorMeta = useMemo(
    () => resolvePublicNoteAuthorMeta({
      ownerUserId,
      currentUserId,
      authorDisplayName,
      authorUsername,
      isOfficialAuthor,
      isCurrentUser,
    }),
    [authorDisplayName, authorUsername, currentUserId, isCurrentUser, isOfficialAuthor, ownerUserId],
  );

  return (
    <div className="flex flex-wrap items-center gap-2 text-sm text-foreground/80">
      {subject ? (
        <Link
          href={buildPublicLibraryUrl({ subject: slugifyPublicLibraryFilterValue(subject) })}
          className="transition-opacity hover:opacity-80"
        >
          <SubjectBadge subject={subject} />
        </Link>
      ) : (
        <SubjectBadge subject={subject} />
      )}
      <span className="text-foreground/45">•</span>
      {ownerUserId || authorUsername ? (
        <Link
          href={buildPublicCreatorOrProfilePath({ userId: ownerUserId, username: authorUsername })}
          className="font-medium text-blue-700 hover:underline dark:text-blue-300"
        >
          {authorMeta.label}
        </Link>
      ) : (
        <span>{authorMeta.label}</span>
      )}
      {authorMeta.showOfficialBadge ? (
        <span className="inline-flex items-center rounded-full border border-blue-500/35 bg-blue-500/10 px-2 py-0.5 text-xs font-medium text-blue-700 dark:text-blue-300">
          Official
        </span>
      ) : null}
    </div>
  );
}

export function PublicNoteOwnershipActions({
  noteId,
  ownerUserId,
  isCurrentUser = false,
  subject,
  title,
}: Readonly<PublicNoteOwnershipActionsProps>) {
  const [currentUserId, setCurrentUserId] = useState<string | null>(() => getAuthUser()?.id ?? null);
  const [shareState, setShareState] = useState<"idle" | "copied" | "error">("idle");
  const [showShareModal, setShowShareModal] = useState(false);
  const shareUrlInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const syncAuth = () => {
      setCurrentUserId(getAuthUser()?.id ?? null);
    };
    syncAuth();
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuth);
    };
  }, []);

  const isOwner = isCurrentUser || isPublicNoteOwner({ ownerUserId, currentUserId });
  const openNoteHref = `/notes/${noteId}`;

  const resolvedShareUrl = useMemo(() => {
    const path = buildPublicLibraryNotePath({ subject, title });
    if (globalThis.window === undefined) {
      return path;
    }
    return new URL(path, globalThis.location.origin).toString();
  }, [subject, title]);

  const truncatedShareUrl = useMemo(() => {
    if (resolvedShareUrl.length <= 58) {
      return resolvedShareUrl;
    }
    return `${resolvedShareUrl.slice(0, 55)}...`;
  }, [resolvedShareUrl]);

  const handleCopyShareLink = async () => {
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error("Clipboard access is unavailable.");
      }
      await navigator.clipboard.writeText(resolvedShareUrl);
      void trackAnalyticsEvent({
        eventType: "PUBLIC_NOTE_SHARED",
        entityId: noteId,
        metadata: {
          path: buildPublicLibraryNotePath({ subject, title }),
        },
      });
      setShareState("copied");
    } catch {
      setShareState("error");
    }
  };

  useEffect(() => {
    if (!showShareModal) {
      return;
    }
    shareUrlInputRef.current?.select();
    void handleCopyShareLink();
  }, [showShareModal]);

  return (
    <div className="space-y-4">
      {isOwner ? (
        <div className="space-y-3">
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <ResponsiveActionLink href={openNoteHref} action="open" label="Open Note" className="w-full sm:w-auto" />
            <ResponsiveActionButton type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShowShareModal(true)} action="share" label="Share this note" />
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-foreground/75">
            Copy this note to your library and get the full Study Pack instantly — summary, key concepts, and practice quiz included.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <PublicSeoCopyCta
              noteId={noteId}
              label="Copy Study Pack"
              redirectTarget="quick-review"
              action="copy"
              analyticsEvent="PUBLIC_NOTE_COPY_CLICKED"
              authModalTitle="Copy this Study Pack"
              authModalBody="Create a free account or log in to copy this note and its Study Pack to your library."
              includeStudyPack
            />
            <ResponsiveActionButton type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShowShareModal(true)} action="share" label="Share this note" />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs text-foreground/55">Want just the note without the Study Pack?</span>
            <PublicSeoCopyCta
              noteId={noteId}
              label="Copy note only"
              redirectTarget="library"
              action="copy"
              variant="outline"
              includeStudyPack={false}
              authModalTitle="Copy this note"
              authModalBody="Create a free account or log in to copy this note to your library."
            />
          </div>
        </div>
      )}

      <AppModal
        isOpen={showShareModal}
        title="Share this note"
        onClose={() => {
          setShowShareModal(false);
          setShareState("idle");
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <ResponsiveActionButton
              type="button"
              variant="outline"
              onClick={() => {
                setShowShareModal(false);
                setShareState("idle");
              }}
              action="back"
              label="Close"
              showTextOnMobile
            />
            {shareState === "copied" ? (
              <span className="inline-flex min-h-10 items-center justify-center rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 text-sm font-medium text-emerald-700 dark:text-emerald-300">
                Copied ✓
              </span>
            ) : (
              <ResponsiveActionButton type="button" onClick={() => void handleCopyShareLink()} action="share" label="Copy Link" />
            )}
          </div>
        )}
      >
        <div className="space-y-2">
          <label htmlFor="public-share-note-url" className="text-xs uppercase tracking-wide text-foreground/60">
            Shareable URL
          </label>
          <input
            ref={shareUrlInputRef}
            id="public-share-note-url"
            readOnly
            value={resolvedShareUrl}
            onFocus={(event) => event.currentTarget.select()}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground/85"
          />
          <p aria-live="polite" className="sr-only">
            {shareState === "copied" ? "Link copied to clipboard" : ""}
          </p>
          {shareState === "copied" ? (
            <p className="text-xs text-emerald-700 dark:text-emerald-300">Link copied to clipboard</p>
          ) : (
            <p className="text-xs text-foreground/60">{truncatedShareUrl}</p>
          )}
          {shareState === "error" ? (
            <p className="text-xs text-red-600 dark:text-red-400">Could not copy the note link.</p>
          ) : null}
        </div>
      </AppModal>
    </div>
  );
}
