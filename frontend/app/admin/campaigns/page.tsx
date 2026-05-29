"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ToastMessage } from "@/components/ui/toast-message";
import {
  ApiRequestError,
  getReEngagementCampaignStatus,
  sendReEngagementCampaign,
  type CampaignStatusResponse,
} from "@/lib/api";
import { requireAdminUser } from "@/lib/route-guards";

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(value));
}

type CampaignCardProps = {
  name: string;
  description: string;
  status: CampaignStatusResponse | null;
  loadError: string | null;
  sendError: string | null;
  sending: boolean;
  onSendClick: () => void;
};

function CampaignCard({
  name,
  description,
  status,
  loadError,
  sendError,
  sending,
  onSendClick,
}: Readonly<CampaignCardProps>) {
  const statusLabel = (() => {
    if (loadError) return null;
    if (!status) return "Loading…";
    if (status.totalSent === 0) return "Never sent";
    const date = status.lastSentAt ? formatDate(status.lastSentAt) : "Unknown date";
    return `Last sent ${date} — ${status.totalSent} total recipients`;
  })();

  const eligibleLabel = (() => {
    if (loadError || !status) return null;
    if (status.eligibleCount === 0) return "No eligible users right now";
    return `${status.eligibleCount} user${status.eligibleCount === 1 ? "" : "s"} eligible`;
  })();

  return (
    <Card className="space-y-4 p-5">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <p className="font-semibold text-foreground">{name}</p>
          <p className="text-sm text-foreground/65">{description}</p>
        </div>
        <div className="shrink-0">
          <Button
            size="sm"
            disabled={sending || !status || status.eligibleCount === 0}
            onClick={onSendClick}
          >
            {sending ? "Sending…" : "Send"}
          </Button>
        </div>
      </div>

      {loadError ? (
        <p className="text-sm text-red-600 dark:text-red-400">{loadError}</p>
      ) : (
        <div className="flex flex-wrap gap-4 text-sm">
          {statusLabel ? (
            <span className="text-foreground/65">{statusLabel}</span>
          ) : null}
          {eligibleLabel ? (
            <span
              className={
                status && status.eligibleCount > 0
                  ? "font-medium text-foreground"
                  : "text-foreground/50"
              }
            >
              {eligibleLabel}
            </span>
          ) : null}
        </div>
      )}

      {sendError ? (
        <p className="text-sm text-red-600 dark:text-red-400">{sendError}</p>
      ) : null}
    </Card>
  );
}

export default function AdminCampaignsPage() {
  const router = useRouter();
  const [status, setStatus] = useState<CampaignStatusResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [showConfirm, setShowConfirm] = useState(false);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const loadStatus = useCallback(async () => {
    if (!requireAdminUser(router)) {
      return;
    }
    setLoadError(null);
    try {
      const result = await getReEngagementCampaignStatus();
      setStatus(result);
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 403) {
        router.replace("/dashboard");
        return;
      }
      setLoadError(err instanceof Error ? err.message : "Could not load campaign status.");
    }
  }, [router]);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  useEffect(() => {
    if (!successMessage) {
      return;
    }
    const id = globalThis.setTimeout(() => setSuccessMessage(null), 4000);
    return () => {
      globalThis.clearTimeout(id);
    };
  }, [successMessage]);

  const handleSendClick = () => {
    setSendError(null);
    setShowConfirm(true);
  };

  const handleCloseConfirm = () => {
    if (sending) {
      return;
    }
    setShowConfirm(false);
    setSendError(null);
  };

  const handleConfirmSend = async () => {
    setSending(true);
    setSendError(null);
    try {
      const result = await sendReEngagementCampaign();
      setShowConfirm(false);
      setSuccessMessage(`Campaign sent: ${result.sent} sent, ${result.skipped} failed`);
      void loadStatus();
    } catch (err) {
      setSendError(err instanceof Error ? err.message : "Could not send campaign.");
    } finally {
      setSending(false);
    }
  };

  const eligibleCount = status?.eligibleCount ?? 0;

  return (
    <div className="mx-auto w-full max-w-7xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      {successMessage ? <ToastMessage message={successMessage} tone="success" /> : null}

      <header className="space-y-2">
        <div className="flex items-center gap-3">
          <Link href="/admin" className="text-sm text-foreground/55 hover:text-foreground/80">
            ← Admin
          </Link>
        </div>
        <h1 className="text-3xl font-semibold text-foreground">Campaigns</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-foreground/70">
          One-time email campaigns for targeted user outreach. Sends up to 100 emails per invocation (free Resend tier) — re-run to reach remaining eligible users.
        </p>
      </header>

      <section className="space-y-3">
        <CampaignCard
          name="Re-engagement 2025"
          description="Users who signed up before v0.19.0 and have been inactive for 30+ days. Segmented by profile type."
          status={status}
          loadError={loadError}
          sendError={sendError}
          sending={sending}
          onSendClick={handleSendClick}
        />
      </section>

      <AppModal
        isOpen={showConfirm}
        title="Send Re-engagement Campaign"
        description={
          eligibleCount > 0
            ? `This will send to up to ${Math.min(eligibleCount, 100)} user${Math.min(eligibleCount, 100) === 1 ? "" : "s"} now (100/day cap). ${eligibleCount > 100 ? `${eligibleCount - 100} remaining users can be reached by running again tomorrow.` : ""} Already-sent users are automatically skipped. This cannot be undone.`
            : "No eligible users found."
        }
        onClose={handleCloseConfirm}
        panelClassName="max-w-[520px]"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={handleCloseConfirm}
              disabled={sending}
            >
              Cancel
            </Button>
            <Button
              type="button"
              onClick={handleConfirmSend}
              disabled={sending || eligibleCount === 0}
            >
              {sending ? "Sending…" : "Send Now"}
            </Button>
          </div>
        )}
      >
        {sendError ? (
          <p className="mt-2 text-sm text-red-600 dark:text-red-400">{sendError}</p>
        ) : null}
      </AppModal>
    </div>
  );
}
