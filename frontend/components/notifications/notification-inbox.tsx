"use client";

import Link from "next/link";
import { Bell, X } from "lucide-react";
import { useEffect, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import {
  dismissNotification,
  listNotifications,
  markNotificationRead,
  type NotificationResponse,
} from "@/lib/api";

type NotificationInboxProps = {
  actionableUnreadCount: number;
  onActionableUnreadDelta: (delta: number) => void;
};

export function NotificationInbox({
  actionableUnreadCount,
  onActionableUnreadDelta,
}: Readonly<NotificationInboxProps>) {
  const [isOpen, setIsOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [hasLoadError, setHasLoadError] = useState(false);

  // ⚠️ matchMedia is accessed defensively, and the reason is blast radius rather than tidiness: this
  // component renders inside the app shell's header, so throwing here takes down EVERY authenticated
  // page, not just the bell. It is genuinely absent in some environments (jsdom without a polyfill,
  // and any non-browser render path), so the desktop popover is the safe fallback.
  useEffect(() => {
    const mediaQuery = globalThis.matchMedia?.("(max-width: 639px)");
    if (!mediaQuery) {
      return;
    }
    const updateIsMobile = () => setIsMobile(mediaQuery.matches);
    updateIsMobile();
    mediaQuery.addEventListener?.("change", updateIsMobile);
    return () => mediaQuery.removeEventListener?.("change", updateIsMobile);
  }, []);

  const loadInbox = async () => {
    setIsLoading(true);
    setHasLoadError(false);
    try {
      setNotifications(await listNotifications());
    } catch {
      setHasLoadError(true);
    } finally {
      setIsLoading(false);
    }
  };

  const openInbox = () => {
    setIsOpen(true);
    void loadInbox();
  };

  const markRead = async (notification: NotificationResponse) => {
    if (notification.readAt) {
      return;
    }
    const previous = notifications;
    const now = new Date().toISOString();
    setNotifications((items) => items.map((item) => (
      item.id === notification.id ? { ...item, readAt: now } : item
    )));
    if (notification.type !== "ANNOUNCEMENT") {
      onActionableUnreadDelta(-1);
    }
    try {
      await markNotificationRead(notification.id);
    } catch {
      setNotifications(previous);
      if (notification.type !== "ANNOUNCEMENT") {
        onActionableUnreadDelta(1);
      }
    }
  };

  const dismiss = async (notification: NotificationResponse) => {
    const previous = notifications;
    setNotifications((items) => items.filter((item) => item.id !== notification.id));
    try {
      await dismissNotification(notification.id);
    } catch {
      setNotifications(previous);
    }
  };

  const rows = (
    <div className="min-h-0 flex-1 overflow-y-auto">
      {isLoading ? <p className="px-4 py-6 text-sm text-muted-foreground">Loading notifications…</p> : null}
      {hasLoadError ? (
        <div className="space-y-3 px-4 py-6 text-sm">
          <p className="text-muted-foreground">Could not load notifications.</p>
          <button type="button" className="rounded-md border border-border px-3 py-2" onClick={() => void loadInbox()}>
            Retry
          </button>
        </div>
      ) : null}
      {!isLoading && !hasLoadError && notifications.length === 0 ? (
        <p className="px-4 py-6 text-sm text-muted-foreground">Your inbox is empty.</p>
      ) : null}
      {!isLoading && !hasLoadError ? notifications.map((notification) => (
        <article key={notification.id} className="border-b border-border px-4 py-3 last:border-b-0">
          <div className="flex items-start gap-3">
            <div className="min-w-0 flex-1">
              <p className={notification.readAt ? "text-sm font-medium" : "text-sm font-semibold"}>{notification.title}</p>
              {notification.body ? <p className="mt-1 text-sm text-muted-foreground">{notification.body}</p> : null}
              <div className="mt-3 flex items-center gap-3">
                {notification.ctaPath ? (
                  <Link
                    href={notification.ctaPath}
                    className="text-sm font-medium text-primary underline-offset-4 hover:underline"
                    onClick={() => void markRead(notification)}
                  >
                    {notification.ctaLabel ?? "Open"}
                  </Link>
                ) : null}
                {!notification.readAt ? (
                  <button type="button" className="text-sm text-muted-foreground underline-offset-4 hover:underline" onClick={() => void markRead(notification)}>
                    Mark read
                  </button>
                ) : null}
              </div>
            </div>
            <button
              type="button"
              className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
              aria-label={`Dismiss ${notification.title}`}
              onClick={() => void dismiss(notification)}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </article>
      )) : null}
    </div>
  );

  return (
    <div className="relative">
      <button
        type="button"
        className="relative inline-flex h-9 w-9 items-center justify-center rounded-md border border-border text-foreground hover:bg-muted"
        aria-label="Open notifications"
        onClick={openInbox}
      >
        <Bell className="h-4 w-4" />
        {actionableUnreadCount > 0 ? (
          <span className="absolute -right-1 -top-1 inline-flex min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold leading-4 text-primary-foreground" aria-label={`${actionableUnreadCount} unread notifications`}>
            {actionableUnreadCount}
          </span>
        ) : null}
      </button>

      {isOpen && !isMobile ? (
        <section className="motion-dropdown-panel absolute right-0 top-11 z-20 flex max-h-[32rem] w-96 flex-col overflow-hidden rounded-md border border-border bg-background shadow-lg" aria-label="Notifications">
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <h2 className="text-sm font-semibold">Notifications</h2>
            <button type="button" className="text-sm text-muted-foreground hover:text-foreground" onClick={() => setIsOpen(false)}>Close</button>
          </div>
          {rows}
        </section>
      ) : null}

      <AppModal
        isOpen={isOpen && isMobile}
        onClose={() => setIsOpen(false)}
        title="Notifications"
        variant="sheet"
        panelClassName="p-0"
        contentClassName="min-h-0 flex-1 overflow-hidden p-0"
      >
        {rows}
      </AppModal>
    </div>
  );
}
