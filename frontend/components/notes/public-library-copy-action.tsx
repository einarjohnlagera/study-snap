"use client";

import type { MouseEvent } from "react";
import { useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { ResponsiveActionButton } from "@/components/ui/action-button";
import { buildLoginPath, getAuthUser } from "@/lib/auth";
import { copyNote, trackAnalyticsEvent } from "@/lib/api";
import { buildCopiedNotePath, buildPublicCopyIntentQuery } from "@/lib/public-note-copy";

const COPY_BUTTON_LABEL = "Copy to My Library";
const COPY_LOADING_LABEL = "Copying to My Library...";
const VIEW_NOTE_BUTTON_LABEL = "View Note";
const ALREADY_IN_LIBRARY_LABEL = "Already in your library";
const COPY_ERROR_MESSAGE = "Could not copy note.";
const CARD_COPY_SURFACE = "public_library_card";

type PublicLibraryCopyActionProps = {
  noteId: string;
  isOwner: boolean;
  existingCopyNoteId?: string | null;
  onCopySuccess: (payload: { copiedNoteId: string }) => void;
};

export function PublicLibraryCopyAction({
  noteId,
  isOwner,
  existingCopyNoteId = null,
  onCopySuccess,
}: Readonly<PublicLibraryCopyActionProps>) {
  const router = useRouter();
  const pathname = usePathname();
  const [copying, setCopying] = useState(false);
  const [copyError, setCopyError] = useState<string | null>(null);

  const redirectTo = useMemo(() => {
    if (globalThis.window === undefined) {
      return pathname;
    }
    return `${pathname}${globalThis.location.search}`;
  }, [pathname]);

  const handleOpenExisting = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    router.push(buildCopiedNotePath(existingCopyNoteId ?? noteId, "library"));
  };

  const handleCopy = async (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();

    if (copying) {
      return;
    }

    void trackAnalyticsEvent({
      eventType: "PUBLIC_NOTE_COPY_CLICKED",
      entityId: noteId,
      metadata: {
        path: redirectTo,
        surface: CARD_COPY_SURFACE,
      },
    });

    if (!getAuthUser()) {
      router.push(buildLoginPath({ redirectTo: `${redirectTo}${redirectTo.includes("?") ? "&" : "?"}${buildPublicCopyIntentQuery("library")}` }));
      return;
    }

    setCopying(true);
    setCopyError(null);
    try {
      const copied = await copyNote(noteId);
      onCopySuccess({ copiedNoteId: copied.id });
    } catch (error) {
      setCopyError(error instanceof Error ? error.message : COPY_ERROR_MESSAGE);
      setCopying(false);
      return;
    }

    setCopying(false);
  };

  if (isOwner) {
    return null;
  }

  if (existingCopyNoteId) {
    return (
      <div className="space-y-2" onClick={(event) => event.stopPropagation()}>
        <div className="inline-flex items-center rounded-full border border-emerald-500/35 bg-emerald-500/10 px-2.5 py-1 text-[11px] font-medium text-emerald-700 dark:text-emerald-300">
          {ALREADY_IN_LIBRARY_LABEL}
        </div>
        <ResponsiveActionButton
          type="button"
          variant="outline"
          className="w-full sm:w-auto"
          action="library"
          label={VIEW_NOTE_BUTTON_LABEL}
          onClick={handleOpenExisting}
          showTextOnMobile
        />
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <ResponsiveActionButton
        type="button"
        className="w-full sm:w-auto"
        action="copy"
        label={copying ? COPY_LOADING_LABEL : COPY_BUTTON_LABEL}
        onClick={(event) => void handleCopy(event)}
        disabled={copying}
        showTextOnMobile
      />
      {copyError ? (
        <p className="text-xs text-red-600 dark:text-red-400">{copyError}</p>
      ) : null}
    </div>
  );
}
