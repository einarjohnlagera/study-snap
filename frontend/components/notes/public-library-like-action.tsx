"use client";

import type { MouseEvent } from "react";
import { useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Heart } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { buildLoginPath, getAuthUser } from "@/lib/auth";
import { togglePublicNoteLike } from "@/lib/api";
import { cn } from "@/lib/utils";

const AUTH_MODAL_TITLE = "Like this note";
const AUTH_MODAL_BODY = "Create an account or log in to like helpful notes in Public Library.";
const AUTH_LOGIN_LABEL = "Log In";
const AUTH_SIGNUP_LABEL = "Sign Up";
const LIKE_ERROR_MESSAGE = "Could not update note like.";

type PublicLibraryLikeActionProps = {
  noteId: string;
  likeCount: number;
  liked: boolean;
  onLikeSuccess: (payload: { liked: boolean; likeCount: number }) => void;
};

export function PublicLibraryLikeAction({
  noteId,
  likeCount,
  liked,
  onLikeSuccess,
}: Readonly<PublicLibraryLikeActionProps>) {
  const router = useRouter();
  const pathname = usePathname();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [authModalOpen, setAuthModalOpen] = useState(false);

  const redirectTo = useMemo(() => {
    if (globalThis.window === undefined) {
      return pathname;
    }
    return `${pathname}${globalThis.location.search}`;
  }, [pathname]);

  const handleLike = async (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();

    if (pending) {
      return;
    }

    if (!getAuthUser()) {
      setAuthModalOpen(true);
      return;
    }

    setPending(true);
    setError(null);
    try {
      const response = await togglePublicNoteLike(noteId);
      onLikeSuccess(response);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : LIKE_ERROR_MESSAGE);
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="flex flex-col items-start gap-1">
      <button
        type="button"
        aria-label={liked ? "Unlike note" : "Like note"}
        aria-pressed={liked}
        onClick={(event) => void handleLike(event)}
        disabled={pending}
        className={cn(
          "motion-pressable motion-lift inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors",
          liked
            ? "border-red-500/35 bg-red-500/10 text-red-700 dark:text-red-300"
            : "border-border bg-background text-foreground/65 hover:bg-muted/60 active:bg-muted/70",
          pending && "opacity-70",
        )}
      >
        <Heart className={cn("h-3.5 w-3.5", liked && "fill-current")} aria-hidden="true" />
        <span>{likeCount.toLocaleString()}</span>
      </button>
      {error ? (
        <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
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
                router.push(buildLoginPath({ redirectTo }));
              }}
            >
              {AUTH_LOGIN_LABEL}
            </Button>
            <Button
              type="button"
              onClick={() => {
                setAuthModalOpen(false);
                router.push(`/signup?redirect=${encodeURIComponent(redirectTo)}`);
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
