"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { getAuthUser } from "@/lib/auth";
import { deleteNote } from "@/lib/api";
import { PublicSeoCopyCta } from "./public-seo-copy-cta";

type PublicNoteOwnershipActionsProps = {
  noteId: string;
  ownerUserId: string | null;
  official: boolean;
  studyPackStatus: "DRAFT" | "STUDY_PACK_READY";
};

function resolveAuthorLabel(
  ownerUserId: string | null,
  currentUserId: string | null,
  official: boolean,
): "By You" | "By NoteLib" | "By Community" {
  if (currentUserId && ownerUserId === currentUserId) {
    return "By You";
  }
  if (official) {
    return "By NoteLib";
  }
  return "By Community";
}

export function PublicNoteOwnershipActions({
  noteId,
  ownerUserId,
  official,
  studyPackStatus,
}: Readonly<PublicNoteOwnershipActionsProps>) {
  const router = useRouter();
  const [currentUserId, setCurrentUserId] = useState<string | null>(() => getAuthUser()?.id ?? null);
  const [shareState, setShareState] = useState<"idle" | "copied" | "error">("idle");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

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

  const isOwner = Boolean(currentUserId && ownerUserId === currentUserId);
  const authorLabel = useMemo(
    () => resolveAuthorLabel(ownerUserId, currentUserId, official),
    [currentUserId, official, ownerUserId],
  );
  const editHref = studyPackStatus === "DRAFT" ? `/notes/${noteId}/edit` : `/notes/${noteId}`;

  const handleShare = async () => {
    try {
      await navigator.clipboard.writeText(globalThis.location.href);
      setShareState("copied");
    } catch {
      setShareState("error");
    }
    globalThis.setTimeout(() => {
      setShareState("idle");
    }, 2000);
  };

  const handleDelete = async () => {
    if (deleting) {
      return;
    }
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteNote(noteId);
      router.push("/library");
    } catch (error) {
      setDeleteError(error instanceof Error ? error.message : "Could not delete note.");
      setDeleting(false);
      return;
    }
    setDeleting(false);
    setShowDeleteConfirm(false);
  };

  return (
    <div className="space-y-4">
      <p className="text-sm text-foreground/80">{authorLabel}</p>

      {isOwner ? (
        <div className="space-y-3">
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Link href={editHref} className="w-full sm:w-auto">
              <Button type="button" className="w-full sm:w-auto">
                Edit Note
              </Button>
            </Link>
            {studyPackStatus === "STUDY_PACK_READY" ? (
              <>
                <Link href={`/notes/${noteId}/quick-review`} className="w-full sm:w-auto">
                  <Button type="button" variant="outline" className="w-full sm:w-auto">
                    Start Quick Review
                  </Button>
                </Link>
                <Link href={`/notes/${noteId}/challenge-quiz`} className="w-full sm:w-auto">
                  <Button type="button" variant="outline" className="w-full sm:w-auto">
                    Challenge Quiz
                  </Button>
                </Link>
              </>
            ) : null}
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => void handleShare()}>
              {shareState === "copied" ? "Link Copied" : "Share"}
            </Button>
            <Button
              type="button"
              variant="outline"
              className="w-full border-red-500/40 text-red-600 hover:bg-red-500/10 dark:text-red-400 sm:w-auto"
              onClick={() => setShowDeleteConfirm(true)}
            >
              Delete
            </Button>
          </div>
          {shareState === "error" ? (
            <p className="text-xs text-red-600 dark:text-red-400">Could not copy the note link.</p>
          ) : null}
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <PublicSeoCopyCta noteId={noteId} label="Make a Copy" />
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => void handleShare()}>
              {shareState === "copied" ? "Link Copied" : "Share"}
            </Button>
          </div>
          {shareState === "error" ? (
            <p className="text-xs text-red-600 dark:text-red-400">Could not copy the note link.</p>
          ) : null}
        </div>
      )}

      <AppModal
        isOpen={showDeleteConfirm}
        title="Delete this note?"
        description="This removes the note from your library. This action cannot be undone."
        onClose={() => {
          if (!deleting) {
            setShowDeleteConfirm(false);
          }
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => setShowDeleteConfirm(false)}
              disabled={deleting}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="outline"
              className="w-full border-red-500/40 text-red-600 hover:bg-red-500/10 dark:text-red-400 sm:w-auto"
              onClick={() => void handleDelete()}
              disabled={deleting}
            >
              {deleting ? "Deleting..." : "Delete"}
            </Button>
          </div>
        )}
      >
        {deleteError ? <p className="text-sm text-red-600 dark:text-red-400">{deleteError}</p> : null}
      </AppModal>
    </div>
  );
}
