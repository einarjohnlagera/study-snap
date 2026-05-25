"use client";

import type { MouseEvent } from "react";
import { useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Check, Save } from "lucide-react";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { buildLoginPath, getAuthUser } from "@/lib/auth";
import { copyNote, trackAnalyticsEvent } from "@/lib/api";
import { buildPublicCopyIntentQuery, setCopyIntentCookie } from "@/lib/public-note-copy";

const SAVE_BUTTON_LABEL = "Save";
const SAVE_LOADING_LABEL = "Saving...";
const SAVED_BUTTON_LABEL = "Saved";
const COPY_ERROR_MESSAGE = "Could not copy note.";
const CARD_COPY_SURFACE = "public_library_card";
const AUTH_MODAL_TITLE = "Save this note";
const AUTH_MODAL_BODY = "Create an account or log in to save notes to your library.";
const AUTH_LOGIN_LABEL = "Log In";
const AUTH_SIGNUP_LABEL = "Sign Up";

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
  const startRouteProgress = useRouteProgress();
  const [copying, setCopying] = useState(false);
  const [copyError, setCopyError] = useState<string | null>(null);
  const [authModalOpen, setAuthModalOpen] = useState(false);

  const redirectTo = useMemo(() => {
    if (globalThis.window === undefined) {
      return pathname;
    }
    return `${pathname}${globalThis.location.search}`;
  }, [pathname]);

  const authRedirectTarget = useMemo(
    () => `${redirectTo}${redirectTo.includes("?") ? "&" : "?"}${buildPublicCopyIntentQuery("library")}`,
    [redirectTo],
  );

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
      setAuthModalOpen(true);
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
      <span
        className="inline-flex"
        onClick={(event) => event.stopPropagation()}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <Button
          type="button"
          size="sm"
          variant="ghost"
          disabled
          className="h-9 gap-1.5 rounded-full px-3 text-foreground/55"
        >
          <Check className="h-3.5 w-3.5" aria-hidden="true" />
          <span>{SAVED_BUTTON_LABEL}</span>
        </Button>
      </span>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <Button
        type="button"
        size="sm"
        variant="outline"
        className="h-9 gap-1.5 rounded-full px-3 shadow-sm shadow-black/[0.02]"
        onClick={(event) => void handleCopy(event)}
        loading={copying}
        loadingText={SAVE_LOADING_LABEL}
      >
        <Save className="h-3.5 w-3.5" aria-hidden="true" />
        <span>{SAVE_BUTTON_LABEL}</span>
      </Button>
      {copyError ? (
        <p className="text-xs text-red-600 dark:text-red-400">{copyError}</p>
      ) : null}
      <AppModal
        isOpen={authModalOpen}
        title={AUTH_MODAL_TITLE}
        description={AUTH_MODAL_BODY}
        onClose={() => setAuthModalOpen(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setAuthModalOpen(false);
                startRouteProgress();
                router.push(buildLoginPath({ redirectTo: authRedirectTarget }));
              }}
            >
              {AUTH_LOGIN_LABEL}
            </Button>
            <Button
              type="button"
              onClick={() => {
                setAuthModalOpen(false);
                setCopyIntentCookie(noteId);
                startRouteProgress();
                router.push(`/signup?redirect=${encodeURIComponent(authRedirectTarget)}`);
              }}
            >
              {AUTH_SIGNUP_LABEL}
            </Button>
          </div>
        )}
      />
    </div>
  );
}
