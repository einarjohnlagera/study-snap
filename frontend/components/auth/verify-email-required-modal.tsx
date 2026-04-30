"use client";

import { useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { ApiRequestError, requestEmailVerification } from "@/lib/api";

type VerifyEmailRequiredModalProps = {
  isOpen: boolean;
  onClose: () => void;
};

export function VerifyEmailRequiredModal({
  isOpen,
  onClose,
}: Readonly<VerifyEmailRequiredModalProps>) {
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleResendVerification = async () => {
    if (sending) {
      return;
    }
    setSending(true);
    setMessage(null);
    setError(null);
    try {
      const response = await requestEmailVerification();
      setMessage(response.message);
    } catch (requestError) {
      if (
        requestError instanceof ApiRequestError
        && (requestError.code === "VERIFICATION_EMAIL_COOLDOWN" || requestError.status === 429)
      ) {
        setMessage("You can resend again in a moment.");
      } else {
        setError(requestError instanceof Error ? requestError.message : "Could not send verification email.");
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title="Verify your email first"
      onClose={onClose}
      panelClassName="max-w-[460px]"
      actions={(
        <div className="flex justify-end">
          <Button type="button" onClick={() => void handleResendVerification()} disabled={sending}>
            {sending ? "Sending..." : "Resend verification email"}
          </Button>
        </div>
      )}
    >
      <div className="space-y-4 text-sm leading-relaxed text-foreground/85">
        <p>
          Please verify your email before upgrading your plan. This helps us secure your account and send important
          updates.
        </p>
        {message ? (
          <div className="rounded-lg border border-blue-500/20 bg-blue-500/10 p-3 text-sm text-foreground">
            {message}
          </div>
        ) : null}
        {error ? (
          <div className="rounded-lg border border-red-500/30 bg-red-50/70 p-3 text-sm text-red-700 dark:bg-red-950/20 dark:text-red-300">
            {error}
          </div>
        ) : null}
      </div>
    </AppModal>
  );
}
