"use client";

import { useCallback, useEffect, useRef, useState, type ReactElement } from "react";
import { useRouter } from "next/navigation";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";

const LEAVE_QUIZ_TITLE = "Leave quiz?";
const LEAVE_QUIZ_DESCRIPTION = "You are currently in an active quiz. Leaving will forfeit your progress.";
const LEAVE_QUIZ_ERROR = "Could not leave quiz. Please try again.";
const BEFORE_UNLOAD_MESSAGE = "You are currently in an active quiz. Leaving will forfeit your progress.";

type QuizSessionGuardOptions = {
  active: boolean;
  fallbackHref: string;
  onConfirmLeave: () => Promise<void> | void;
  onBeforeRouteLeave?: () => void;
  blockWithoutConfirmation?: boolean;
  dialogTitle?: string;
  dialogDescription?: string;
  cancelLabel?: string;
  confirmLabel?: string;
  confirmLoadingLabel?: string;
  leaveErrorMessage?: string;
  beforeUnloadMessage?: string;
};

type QuizSessionGuardResult = {
  requestLeave: (targetHref?: string) => void;
  LeaveQuizModal: () => ReactElement;
};

export function useQuizSessionGuard({
  active,
  fallbackHref,
  onConfirmLeave,
  onBeforeRouteLeave,
  blockWithoutConfirmation = false,
  dialogTitle = LEAVE_QUIZ_TITLE,
  dialogDescription = LEAVE_QUIZ_DESCRIPTION,
  cancelLabel = "Stay",
  confirmLabel = "Leave Quiz",
  confirmLoadingLabel = "Leaving...",
  leaveErrorMessage = LEAVE_QUIZ_ERROR,
  beforeUnloadMessage = BEFORE_UNLOAD_MESSAGE,
}: QuizSessionGuardOptions): QuizSessionGuardResult {
  const router = useRouter();
  const [showLeaveDialog, setShowLeaveDialog] = useState(false);
  const [pendingHref, setPendingHref] = useState<string | null>(null);
  const [leaving, setLeaving] = useState(false);
  const [leaveError, setLeaveError] = useState<string | null>(null);
  const showLeaveDialogRef = useRef(false);
  const leaveGuardInsertedRef = useRef(false);

  useEffect(() => {
    showLeaveDialogRef.current = showLeaveDialog;
  }, [showLeaveDialog]);

  const requestLeave = useCallback((targetHref?: string) => {
    onBeforeRouteLeave?.();
    if (blockWithoutConfirmation) {
      return;
    }
    setPendingHref(targetHref ?? fallbackHref);
    setLeaveError(null);
    setShowLeaveDialog(true);
  }, [blockWithoutConfirmation, fallbackHref, onBeforeRouteLeave]);

  useEffect(() => {
    if (!active) {
      leaveGuardInsertedRef.current = false;
      return;
    }

    const openLeaveDialog = (targetHref?: string) => {
      onBeforeRouteLeave?.();
      if (blockWithoutConfirmation) {
        return;
      }
      if (showLeaveDialogRef.current) {
        return;
      }
      setPendingHref(targetHref ?? fallbackHref);
      setLeaveError(null);
      setShowLeaveDialog(true);
    };

    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      onBeforeRouteLeave?.();
      event.preventDefault();
      event.returnValue = beforeUnloadMessage;
      return beforeUnloadMessage;
    };

    const handlePopState = () => {
      globalThis.history.pushState(null, "", globalThis.location.href);
      openLeaveDialog(fallbackHref);
    };

    const handleDocumentClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }
      const target = event.target instanceof Element ? event.target : null;
      const anchor = target?.closest<HTMLAnchorElement>("a[href]");
      if (!anchor || anchor.target === "_blank" || anchor.hasAttribute("download")) {
        return;
      }
      const rawHref = anchor.getAttribute("href");
      if (!rawHref || rawHref.startsWith("#")) {
        return;
      }
      const targetUrl = new URL(anchor.href, globalThis.location.href);
      if (targetUrl.href === globalThis.location.href) {
        return;
      }

      event.preventDefault();
      event.stopPropagation();
      const targetHref = targetUrl.origin === globalThis.location.origin
        ? `${targetUrl.pathname}${targetUrl.search}${targetUrl.hash}`
        : targetUrl.href;
      openLeaveDialog(targetHref);
    };

    if (!leaveGuardInsertedRef.current) {
      globalThis.history.pushState(null, "", globalThis.location.href);
      leaveGuardInsertedRef.current = true;
    }

    globalThis.addEventListener("beforeunload", handleBeforeUnload);
    globalThis.addEventListener("popstate", handlePopState);
    document.addEventListener("click", handleDocumentClick, true);

    return () => {
      globalThis.removeEventListener("beforeunload", handleBeforeUnload);
      globalThis.removeEventListener("popstate", handlePopState);
      document.removeEventListener("click", handleDocumentClick, true);
    };
  }, [active, beforeUnloadMessage, blockWithoutConfirmation, fallbackHref, onBeforeRouteLeave]);

  const handleConfirmLeave = useCallback(async () => {
    if (leaving) {
      return;
    }
    setLeaving(true);
    setLeaveError(null);
    try {
      await onConfirmLeave();
      const targetHref = pendingHref ?? fallbackHref;
      setShowLeaveDialog(false);
      if (/^https?:\/\//i.test(targetHref)) {
        globalThis.location.assign(targetHref);
      } else {
        router.push(targetHref);
      }
    } catch {
      setLeaveError(leaveErrorMessage);
    } finally {
      setLeaving(false);
    }
  }, [fallbackHref, leaveErrorMessage, leaving, onConfirmLeave, pendingHref, router]);

  const LeaveQuizModal = useCallback(() => (
    <AppModal
      isOpen={showLeaveDialog}
      title={dialogTitle}
      description={dialogDescription}
      onClose={() => {
        if (!leaving) {
          setShowLeaveDialog(false);
        }
      }}
      actions={(
        <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={() => setShowLeaveDialog(false)} disabled={leaving}>
            {cancelLabel}
          </Button>
          <Button type="button" onClick={() => void handleConfirmLeave()} disabled={leaving}>
            {leaving ? confirmLoadingLabel : confirmLabel}
          </Button>
        </div>
      )}
    >
      {leaveError ? (
        <p className="text-sm text-red-600 dark:text-red-400">{leaveError}</p>
      ) : null}
    </AppModal>
  ), [
    cancelLabel,
    confirmLabel,
    confirmLoadingLabel,
    dialogDescription,
    dialogTitle,
    handleConfirmLeave,
    leaveError,
    leaving,
    showLeaveDialog,
  ]);

  return {
    requestLeave,
    LeaveQuizModal,
  };
}
