"use client";

import { MessageSquarePlus } from "lucide-react";
import { useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { submitFeedback } from "@/lib/api";

type SendFeedbackWidgetProps = {
  mobileHidden?: boolean;
};

export function SendFeedbackWidget({ mobileHidden = false }: Readonly<SendFeedbackWidgetProps>) {
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const resetState = () => {
    setMessage("");
    setError(null);
    setSubmitting(false);
    setSuccessMessage(null);
  };

  const closeModal = () => {
    setOpen(false);
    resetState();
  };

  const handleOpen = () => {
    setOpen(true);
    setError(null);
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

  return (
    <>
      <Button
        type="button"
        className={`fixed bottom-5 right-5 z-40 gap-2 rounded-full px-4 shadow-lg sm:bottom-6 sm:right-6 ${mobileHidden ? "hidden sm:inline-flex" : ""}`}
        onClick={handleOpen}
      >
        <MessageSquarePlus className="h-4 w-4" />
        Send Feedback
      </Button>

      <AppModal
        isOpen={open}
        onClose={closeModal}
        title="Send Feedback"
        description="Found a bug? Something confusing? Have a feature idea? Tell us what happened or what you'd like to see in NoteLib. We read every message and use it to improve the app."
        descriptionClassName="whitespace-pre-line"
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
              {submitting ? "Sending..." : "Send Feedback"}
            </Button>
          </div>
        )}
        panelClassName="max-w-[520px]"
      >
        {successMessage ? (
          <div className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-800 dark:text-emerald-200">
            {successMessage}
          </div>
        ) : (
          <div className="space-y-3">
            <label htmlFor="feedback-message" className="block text-sm font-medium text-foreground">
              Message
            </label>
            <textarea
              id="feedback-message"
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              rows={6}
              maxLength={4000}
              placeholder="Describe the bug, issue, or feature you'd like to suggest..."
              className="w-full rounded-xl border border-border bg-background px-3 py-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
            />
            {error ? (
              <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
            ) : (
              <p className="text-xs text-foreground/60">
                You can report bugs, confusing parts, or request features. This goes directly to our improvement list.
              </p>
            )}
          </div>
        )}
      </AppModal>
    </>
  );
}
