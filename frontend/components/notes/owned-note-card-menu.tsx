"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { MoreHorizontal } from "lucide-react";
import { DeleteConfirmationModal } from "@/components/notes/delete-confirmation-modal";
import { ResponsiveActionButton, ResponsiveActionContent } from "@/components/ui/action-button";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { getAuthUser } from "@/lib/auth";
import {
  copyNote,
  deleteNote,
  updateNoteVisibility,
  type NoteStudyPackStatus,
  type NoteVisibility,
} from "@/lib/api";
import { buildPublicLibraryNotePath } from "@/lib/public-note-path";

type OwnedNoteCardMenuSurface = "library" | "publicProfile";

type OwnedNoteCardMenuProps = {
  noteId: string;
  title: string | null;
  subject: string | null;
  visibility: NoteVisibility;
  studyPackStatus: NoteStudyPackStatus;
  surface: OwnedNoteCardMenuSurface;
  onRemoved?: () => void;
  onMessage?: (message: string) => void;
  onError?: (message: string) => void;
};

const DELETE_MENU_ITEM_CLASS = "font-medium text-red-700 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/40";

function buildShareUrl(subject: string | null, title: string | null) {
  const path = buildPublicLibraryNotePath({ subject, title });
  if (globalThis.window === undefined) {
    return path;
  }
  return new URL(path, globalThis.location.origin).toString();
}

export function OwnedNoteCardMenu({
  noteId,
  title,
  subject,
  visibility,
  studyPackStatus,
  surface,
  onRemoved,
  onMessage,
  onError,
}: Readonly<OwnedNoteCardMenuProps>) {
  const router = useRouter();
  const menuRef = useRef<HTMLDivElement | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [currentVisibility, setCurrentVisibility] = useState<NoteVisibility>(visibility);
  const [copying, setCopying] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [updatingVisibility, setUpdatingVisibility] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [showMakePrivateConfirm, setShowMakePrivateConfirm] = useState(false);
  const [showMakePublicShareConfirm, setShowMakePublicShareConfirm] = useState(false);

  useEffect(() => {
    setCurrentVisibility(visibility);
  }, [visibility]);

  useEffect(() => {
    if (!menuOpen) {
      return;
    }

    const handleOutsideClick = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Node)) {
        return;
      }
      if (menuRef.current && !menuRef.current.contains(target)) {
        setMenuOpen(false);
      }
    };

    globalThis.addEventListener("mousedown", handleOutsideClick);
    return () => globalThis.removeEventListener("mousedown", handleOutsideClick);
  }, [menuOpen]);

  const shareUrl = useMemo(() => buildShareUrl(subject, title), [subject, title]);

  const handleEdit = () => {
    setMenuOpen(false);
    if (studyPackStatus === "DRAFT") {
      router.push(`/notes/${noteId}/edit`);
      return;
    }
    router.push(`/notes/${noteId}?edit=1`);
  };

  const handleMakeCopy = async () => {
    if (copying || deleting || updatingVisibility) {
      return;
    }
    setCopying(true);
    setMenuOpen(false);
    try {
      const copied = await copyNote(noteId);
      router.push(`/notes/${copied.id}?copied=1`);
    } catch (error) {
      onError?.(error instanceof Error ? error.message : "Could not copy note.");
    } finally {
      setCopying(false);
    }
  };

  const handleDelete = async () => {
    if (copying || deleting || updatingVisibility) {
      return;
    }
    setDeleting(true);
    try {
      await deleteNote(noteId);
      onRemoved?.();
      onMessage?.("Note deleted.");
    } catch (error) {
      onError?.(error instanceof Error ? error.message : "Could not delete note.");
    } finally {
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
  };

  const copyShareUrl = async (successMessage: string) => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      onMessage?.(successMessage);
    } catch {
      onError?.("Could not copy public note link.");
    }
  };

  const handleShare = async () => {
    if (copying || deleting || updatingVisibility) {
      return;
    }
    setMenuOpen(false);
    if (currentVisibility === "PUBLIC") {
      await copyShareUrl("Public note link copied.");
      return;
    }
    if (!getAuthUser()?.emailVerifiedAt) {
      onMessage?.("Verify your email before publishing notes to the Public Library.");
      return;
    }
    setShowMakePublicShareConfirm(true);
  };

  const handleMakePublicAndShare = async () => {
    if (copying || deleting || updatingVisibility) {
      return;
    }
    setUpdatingVisibility(true);
    try {
      await updateNoteVisibility(noteId, "PUBLIC");
      setCurrentVisibility("PUBLIC");
      try {
        await navigator.clipboard.writeText(shareUrl);
        onMessage?.("Note is now public and share link copied.");
      } catch {
        onError?.("Note is now public, but the share link could not be copied.");
      }
    } catch (error) {
      onError?.(error instanceof Error ? error.message : "Could not update note visibility.");
    } finally {
      setUpdatingVisibility(false);
      setShowMakePublicShareConfirm(false);
    }
  };

  const handleMakePrivate = async () => {
    if (copying || deleting || updatingVisibility) {
      return;
    }
    setUpdatingVisibility(true);
    try {
      await updateNoteVisibility(noteId, "PRIVATE");
      setCurrentVisibility("PRIVATE");
      onRemoved?.();
      onMessage?.("Note is now private.");
    } catch (error) {
      onError?.(error instanceof Error ? error.message : "Could not update note visibility.");
    } finally {
      setUpdatingVisibility(false);
      setShowMakePrivateConfirm(false);
    }
  };

  return (
    <div ref={menuRef} className="relative" data-card-menu="true">
      <button
        type="button"
        aria-label="Open note actions"
        className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-border bg-background text-sm text-foreground/70 hover:bg-muted/60 hover:text-foreground"
        onClick={(event) => {
          event.stopPropagation();
          setMenuOpen((open) => !open);
        }}
        onKeyDown={(event) => event.stopPropagation()}
      >
        <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
      </button>

      {menuOpen ? (
        <div className="absolute right-0 top-9 z-20 w-44 rounded-md border border-border bg-background p-1 shadow-sm">
          {surface === "library" ? (
            <button
              type="button"
              className="w-full rounded px-3 py-2 text-left text-sm hover:bg-muted/60"
              onClick={(event) => {
                event.stopPropagation();
                handleEdit();
              }}
            >
              <span className="inline-flex items-center gap-2">
                <ResponsiveActionContent action="edit" label="Edit" showTextOnMobile />
              </span>
            </button>
          ) : null}

          <button
            type="button"
            className={`w-full rounded px-3 py-2 text-left text-sm ${DELETE_MENU_ITEM_CLASS}`}
            onClick={(event) => {
              event.stopPropagation();
              setMenuOpen(false);
              setShowDeleteConfirm(true);
            }}
            disabled={deleting}
          >
            <span className="inline-flex items-center gap-2">
              <ResponsiveActionContent action="delete" label={deleting ? "Deleting..." : "Delete"} showTextOnMobile />
            </span>
          </button>

          {surface === "publicProfile" && currentVisibility === "PUBLIC" ? (
            <button
              type="button"
              className="w-full rounded px-3 py-2 text-left text-sm hover:bg-muted/60"
              onClick={(event) => {
                event.stopPropagation();
                setMenuOpen(false);
                setShowMakePrivateConfirm(true);
              }}
              disabled={updatingVisibility}
            >
              <span className="inline-flex items-center gap-2">
                <ResponsiveActionContent action="private" label={updatingVisibility ? "Updating..." : "Make Private"} showTextOnMobile />
              </span>
            </button>
          ) : null}

          <button
            type="button"
            className="w-full rounded px-3 py-2 text-left text-sm hover:bg-muted/60"
            onClick={(event) => {
              event.stopPropagation();
              void handleMakeCopy();
            }}
            disabled={copying}
          >
            <span className="inline-flex items-center gap-2">
              <ResponsiveActionContent action="copy" label={copying ? "Copying..." : "Make a Copy"} showTextOnMobile />
            </span>
          </button>

          {surface === "library" ? (
            <button
              type="button"
              className="w-full rounded px-3 py-2 text-left text-sm hover:bg-muted/60"
              onClick={(event) => {
                event.stopPropagation();
                void handleShare();
              }}
              disabled={updatingVisibility}
            >
              <span className="inline-flex items-center gap-2">
                <ResponsiveActionContent action="share" label={updatingVisibility ? "Updating..." : "Share"} showTextOnMobile />
              </span>
            </button>
          ) : null}
        </div>
      ) : null}

      <DeleteConfirmationModal
        isOpen={showDeleteConfirm}
        title="Delete this note?"
        message="This will permanently delete this note and all generated Study Pack content. This action cannot be undone."
        confirmText={deleting ? "Deleting..." : "Delete note"}
        onCancel={() => {
          if (!deleting) {
            setShowDeleteConfirm(false);
          }
        }}
        onConfirm={() => {
          if (!deleting) {
            void handleDelete();
          }
        }}
      />

      <AppModal
        isOpen={showMakePrivateConfirm}
        title="Make this note private?"
        description="This will remove the note from the Public Library and your Public Profile."
        onClose={() => {
          if (!updatingVisibility) {
            setShowMakePrivateConfirm(false);
          }
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowMakePrivateConfirm(false)}
              disabled={updatingVisibility}
            >
              Cancel
            </Button>
            <Button type="button" onClick={() => void handleMakePrivate()} disabled={updatingVisibility}>
              {updatingVisibility ? "Updating..." : "Make Private"}
            </Button>
          </div>
        )}
      />

      <AppModal
        isOpen={showMakePublicShareConfirm}
        title="This note is private"
        description="You need to make this note public before sharing. Anyone with the link will be able to view and copy this note."
        onClose={() => {
          if (!updatingVisibility) {
            setShowMakePublicShareConfirm(false);
          }
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <ResponsiveActionButton
              type="button"
              variant="outline"
              onClick={() => setShowMakePublicShareConfirm(false)}
              action="back"
              label="Cancel"
              disabled={updatingVisibility}
            />
            <ResponsiveActionButton
              type="button"
              onClick={() => void handleMakePublicAndShare()}
              action="share"
              label={updatingVisibility ? "Updating..." : "Make Public & Share"}
              disabled={updatingVisibility}
            />
          </div>
        )}
      />
    </div>
  );
}
