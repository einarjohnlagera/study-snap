"use client";

import { MessageSquare, MessageSquarePlus, X } from "lucide-react";
import { type ReactNode, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { submitFeedback } from "@/lib/api";
import { cn } from "@/lib/utils";

const MAX_FEEDBACK_MESSAGE_LENGTH = 4000;
const FEEDBACK_COUNTER_WARNING_THRESHOLD = 3800;
const FEEDBACK_COUNTER_DANGER_THRESHOLD = 3950;

export type FeedbackQuickAction = {
  label: string;
  template?: string;
  onClick?: () => void;
  icon?: ReactNode;
  variant?: "default" | "outline";
};

type SendFeedbackWidgetProps = {
  hidden?: boolean;
  mobileHidden?: boolean;
  variant?: "floating" | "inline" | "icon";
  title?: string;
  description?: string;
  triggerLabel?: string;
  quickActions?: FeedbackQuickAction[];
  iconButtonClassName?: string;
  showTriggerButton?: boolean;
  onDismiss?: () => void;
};

export function SendFeedbackWidget({
  hidden = false,
  mobileHidden = false,
  variant = "floating",
  title = "Send Feedback",
  description = "Found a bug? Something confusing? Have a feature idea? Tell us what happened or what you'd like to see in NoteLib. We read every message and use it to improve the app.",
  triggerLabel = "Send Feedback",
  quickActions = [],
  iconButtonClassName,
  showTriggerButton = true,
  onDismiss,
}: Readonly<SendFeedbackWidgetProps>) {
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [activeQuickActionLabel, setActiveQuickActionLabel] = useState<string | null>(null);

  const resetState = () => {
    setMessage("");
    setError(null);
    setSubmitting(false);
    setSuccessMessage(null);
    setActiveQuickActionLabel(null);
  };

  const closeModal = () => {
    setOpen(false);
    resetState();
  };

  const handleOpen = (prefill?: string, quickActionLabel?: string) => {
    setOpen(true);
    setError(null);
    setSuccessMessage(null);
    setActiveQuickActionLabel(typeof prefill === "string" ? quickActionLabel ?? null : null);
    if (typeof prefill === "string") {
      setMessage(prefill);
    }
  };

  const handleSubmit = async () => {
    const trimmed = message.trim();
    if (trimmed.length === 0) {
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const pageUrl = globalThis.window === undefined ? null : globalThis.window.location.href;
      const response = await submitFeedback({ message: trimmed }, pageUrl);
      setSuccessMessage(response.message);
      setMessage("");
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not send feedback. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  if (hidden) {
    return null;
  }

  return (
    <>
      {variant === "inline" ? (
        <div className="space-y-3 rounded-xl border border-border bg-background p-4">
          <div className="flex items-start justify-between gap-3">
            <div className="space-y-1">
              <p className="text-sm font-semibold text-foreground">{title}</p>
              <p className="text-sm text-foreground/75">{description}</p>
            </div>
            {onDismiss ? (
              <button
                type="button"
                className="rounded-md p-1 text-foreground/55 transition-colors hover:bg-highlight hover:text-foreground"
                onClick={onDismiss}
                aria-label="Dismiss feedback prompt"
              >
                <X className="h-4 w-4" />
              </button>
            ) : null}
          </div>
          <div className="space-y-2">
            {quickActions.length > 0 ? (
              <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-foreground/50">
                Quick start
              </p>
            ) : null}
            <div className="flex flex-wrap gap-2">
              {quickActions.map((action) => (
                <Button
                  key={action.label}
                  type="button"
                  size="sm"
                  variant={action.variant ?? "outline"}
                  className="h-8 gap-1.5 rounded-full px-3 text-xs"
                  onClick={() => {
                    if (action.onClick) {
                      action.onClick();
                      return;
                    }
                    handleOpen(action.template, action.label);
                  }}
                >
                  {action.icon ? <span className="inline-flex items-center">{action.icon}</span> : null}
                  {action.label}
                </Button>
              ))}
            </div>
            {showTriggerButton ? (
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={() => handleOpen()}
              >
                {triggerLabel}
              </Button>
            ) : null}
          </div>
        </div>
      ) : variant === "icon" ? (
        <button
          type="button"
          className={cn(
            "inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border bg-background/85 text-foreground/70 transition-colors hover:bg-highlight hover:text-foreground",
            iconButtonClassName,
          )}
          onClick={() => handleOpen()}
          aria-label={triggerLabel}
          title={triggerLabel}
        >
          <MessageSquare className="h-4 w-4" />
        </button>
      ) : (
        <Button
          type="button"
          className={`fixed bottom-5 right-5 z-40 gap-2 rounded-full px-4 shadow-lg sm:bottom-6 sm:right-6 ${mobileHidden ? "hidden sm:inline-flex" : ""}`}
          onClick={() => handleOpen()}
        >
          <MessageSquarePlus className="h-4 w-4" />
          {triggerLabel}
        </Button>
      )}

      <AppModal
        isOpen={open}
        onClose={closeModal}
        title={title}
        description={description}
        titleIcon={<MessageSquare className="h-4 w-4 text-primary" aria-hidden="true" />}
        descriptionClassName="whitespace-pre-line !text-foreground/65"
        contentClassName="pb-1"
        actions={successMessage ? (
          <div className="flex justify-end">
            <Button type="button" onClick={closeModal}>
              Done
            </Button>
          </div>
        ) : (
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={closeModal} disabled={submitting}>
              Cancel
            </Button>
            <Button
              type="button"
              onClick={() => void handleSubmit()}
              disabled={submitting || message.trim().length === 0}
            >
              {submitting ? "Sending..." : triggerLabel}
            </Button>
          </div>
        )}
        panelClassName="w-full max-w-[520px] self-end rounded-t-2xl pb-[env(safe-area-inset-bottom)] sm:self-center sm:rounded-xl"
        enableSwipeToClose
      >
        {successMessage ? (
          <div className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-800 dark:text-emerald-200">
            {successMessage}
          </div>
        ) : (
          <div className="space-y-3">
            {activeQuickActionLabel ? (
              <div className="inline-flex items-center gap-1.5 rounded-full border border-primary/20 bg-primary/10 py-1 pl-2.5 pr-1.5 text-xs font-medium text-primary">
                <span>Reporting: {activeQuickActionLabel}</span>
                <button
                  type="button"
                  className="rounded-full p-0.5 transition-colors hover:bg-primary/15"
                  onClick={() => {
                    setMessage("");
                    setActiveQuickActionLabel(null);
                  }}
                  aria-label={`Clear ${activeQuickActionLabel} feedback context`}
                >
                  <X className="h-3.5 w-3.5" aria-hidden="true" />
                </button>
              </div>
            ) : null}
            <label htmlFor="feedback-message" className="block text-sm font-medium text-foreground">
              Message
            </label>
            <textarea
              id="feedback-message"
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              rows={6}
              maxLength={MAX_FEEDBACK_MESSAGE_LENGTH}
              placeholder="What's going on? Describe the bug, confusing moment, or idea…"
              className="min-h-[180px] w-full rounded-xl border border-border bg-background px-3 py-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600 sm:min-h-[220px]"
            />
            <div className="flex items-start justify-between gap-3">
              {error ? (
                <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
              ) : (
                <p className="text-xs text-foreground/60">
                  You can report bugs, confusing parts, or request features. This goes directly to our improvement list.
                </p>
              )}
              <p
                className={cn(
                  "shrink-0 text-xs tabular-nums",
                  message.length > FEEDBACK_COUNTER_DANGER_THRESHOLD
                    ? "text-red-600 dark:text-red-400"
                    : message.length > FEEDBACK_COUNTER_WARNING_THRESHOLD
                      ? "text-amber-600 dark:text-amber-400"
                      : "text-foreground/50",
                )}
                aria-live="polite"
              >
                {message.length} / {MAX_FEEDBACK_MESSAGE_LENGTH}
              </p>
            </div>
          </div>
        )}
      </AppModal>
    </>
  );
}
