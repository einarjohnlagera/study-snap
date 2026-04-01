"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { getAuthUser } from "@/lib/auth";
import { SubjectBadge } from "@/components/notes/subject-badge";
import { isPublicNoteOwner, resolvePublicNoteAuthorMeta } from "@/lib/public-note-author";
import { buildPublicLibraryNotePath, buildPublicProfilePath } from "@/lib/public-note-path";
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
  isOfficialAuthor: boolean;
  isCurrentUser: boolean;
  subject?: string | null;
};

export function PublicNoteAuthorLine({
  ownerUserId,
  authorDisplayName,
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
      isOfficialAuthor,
      isCurrentUser,
    }),
    [authorDisplayName, currentUserId, isCurrentUser, isOfficialAuthor, ownerUserId],
  );

  return (
    <div className="flex flex-wrap items-center gap-2 text-sm text-foreground/80">
      <SubjectBadge subject={subject} />
      <span className="text-foreground/45">•</span>
      {ownerUserId ? (
        <Link
          href={buildPublicProfilePath(ownerUserId)}
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

  useEffect(() => {
    if (shareState !== "copied") {
      return;
    }
    const timeout = globalThis.setTimeout(() => setShareState("idle"), 2000);
    return () => globalThis.clearTimeout(timeout);
  }, [shareState]);

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
      await navigator.clipboard.writeText(resolvedShareUrl);
      setShareState("copied");
    } catch {
      setShareState("error");
    }
  };

  return (
    <div className="space-y-4">
      {isOwner ? (
        <div className="space-y-3">
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Link href={openNoteHref} className="w-full sm:w-auto">
              <Button type="button" className="w-full sm:w-auto">
                Open Note
              </Button>
            </Link>
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShowShareModal(true)}>
              Share
            </Button>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <PublicSeoCopyCta noteId={noteId} label="Make a Copy" />
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShowShareModal(true)}>
              Share
            </Button>
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
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setShowShareModal(false);
                setShareState("idle");
              }}
            >
              Close
            </Button>
            <Button type="button" onClick={() => void handleCopyShareLink()}>
              {shareState === "copied" ? "Copied" : "Copy Link"}
            </Button>
          </div>
        )}
      >
        <div className="space-y-2">
          <p className="text-xs uppercase tracking-wide text-foreground/60">Shareable URL</p>
          <p className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground/85">
            {truncatedShareUrl}
          </p>
          {shareState === "copied" ? (
            <p className="text-xs text-emerald-700 dark:text-emerald-300">Link copied</p>
          ) : null}
          {shareState === "error" ? (
            <p className="text-xs text-red-600 dark:text-red-400">Could not copy the note link.</p>
          ) : null}
        </div>
      </AppModal>
    </div>
  );
}
