"use client";

import Link from "next/link";
import { Bell, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import {
  dismissNotification,
  listNotifications,
  markNotificationRead,
  type NotificationResponse,
} from "@/lib/api";
import { toSafeRelativePath } from "@/lib/safe-relative-path";

function formatRelativeTime(createdAt: string): string {
  const created = new Date(createdAt);
  if (Number.isNaN(created.getTime())) {
    return "";
  }

  const elapsedMilliseconds = Math.max(0, Date.now() - created.getTime());
  const elapsedMinutes = Math.floor(elapsedMilliseconds / 60_000);
  if (elapsedMinutes < 1) {
    return "Just now";
  }
  if (elapsedMinutes < 60) {
    return `${elapsedMinutes}m ago`;
  }

  const elapsedHours = Math.floor(elapsedMinutes / 60);
  if (elapsedHours < 24) {
    return `${elapsedHours}h ago`;
  }
  if (elapsedHours < 48) {
    return "Yesterday";
  }

  const elapsedDays = Math.floor(elapsedHours / 24);
  if (elapsedDays < 7) {
    return `${elapsedDays}d ago`;
  }

  const includeYear = created.getFullYear() !== new Date().getFullYear();
  return created.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    ...(includeYear ? { year: "numeric" } : {}),
  });
}

type NotificationInboxProps = {
  actionableUnreadCount: number;
  onActionableUnreadDelta: (delta: number) => void;
};

export function NotificationInbox({
  actionableUnreadCount,
  onActionableUnreadDelta,
}: Readonly<NotificationInboxProps>) {
  const [isOpen, setIsOpen] = useState(false);
  // ⚠️ THE REF WRAPS THE BELL *AND* THE PANEL, AND THAT IS THE WHOLE TRICK. If it wrapped only the
  // panel the bell would count as "outside": mousedown would close, then the bell's own click would
  // reopen and refetch, so a single click would flicker rather than close. Same placement as the
  // avatar menu (app-shell.tsx:653), which puts its ref on the wrapper for exactly this reason.
  const containerRef = useRef<HTMLDivElement | null>(null);
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

  // ⚠️ DESKTOP ONLY. The mobile path renders AppModal, which already closes on its own backdrop and on
  // Escape (app-modal.tsx:109-111); a second handler here would fight it.
  useEffect(() => {
    if (!isOpen || isMobile) {
      return;
    }
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!containerRef.current) {
        return;
      }
      if (!containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsOpen(false);
      }
    };
    globalThis.addEventListener("mousedown", closeOnOutsideClick);
    globalThis.addEventListener("keydown", closeOnEscape);
    return () => {
      globalThis.removeEventListener("mousedown", closeOnOutsideClick);
      globalThis.removeEventListener("keydown", closeOnEscape);
    };
  }, [isOpen, isMobile]);

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

  // ⚠️ A TOGGLE, NOT A RE-OPEN. This previously set open and called loadInbox() unconditionally, so
  // clicking an already-open inbox refetched it — the "refresh, not toggle" the owner reported.
  // ⚠️ Closing must NOT refetch, and opening must STILL load. Only the re-click while open skips it.
  const toggleInbox = () => {
    if (isOpen) {
      setIsOpen(false);
      return;
    }
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
    if (notification.actionable) {
      onActionableUnreadDelta(-1);
    }
    try {
      await markNotificationRead(notification.id);
    } catch {
      setNotifications(previous);
      if (notification.actionable) {
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
      {!isLoading && !hasLoadError ? notifications.map((notification) => {
        // ⚠️ THE STORED PATH IS UNTRUSTED AT RENDER TIME, not just at write time. An announcement CTA is
        // admin-authored and appears inside every recipient's inbox under NoteLib's own chrome, and
        // next/link renders an absolute URL as a live external anchor. Anything that is not a
        // same-origin relative path renders as NO link rather than as a link somewhere else.
        const ctaPath = toSafeRelativePath(notification.ctaPath);
        const titleId = `notification-title-${notification.id}`;
        const body = (
          <>
            <span className="flex items-center gap-2">
              {!notification.readAt ? (
                <span
                  className="h-2 w-2 shrink-0 rounded-full bg-primary"
                  data-testid={`unread-indicator-${notification.id}`}
                  aria-hidden="true"
                />
              ) : null}
              <span
                id={titleId}
                className={notification.readAt ? "text-sm font-normal" : "text-sm font-semibold"}
              >
                {notification.title}
              </span>
            </span>
            {notification.body ? <span className="mt-1 block text-sm text-muted-foreground">{notification.body}</span> : null}
            <span className="mt-1 block text-xs text-muted-foreground">{formatRelativeTime(notification.createdAt)}</span>
          </>
        );
        const bodyClassName = "block min-w-0 flex-1 rounded-md text-left transition-colors hover:bg-highlight focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background";

        return (
          <article
            key={notification.id}
            className={`border-b border-border px-4 py-3 last:border-b-0 ${notification.readAt ? "bg-background" : "bg-muted/40"}`}
            data-unread={!notification.readAt}
          >
            <div className="flex items-start gap-3">
              {ctaPath ? (
                <Link
                  href={ctaPath}
                  aria-labelledby={titleId}
                  className={bodyClassName}
                  onClick={() => {
                    void markRead(notification);
                    setIsOpen(false);
                  }}
                >
                  {body}
                </Link>
              ) : (
                <button
                  type="button"
                  aria-labelledby={titleId}
                  className={bodyClassName}
                  onClick={() => void markRead(notification)}
                >
                  {body}
                </button>
              )}
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
        );
      }) : null}
    </div>
  );

  return (
    <div className="relative" ref={containerRef}>
      {/* ⚠️ The label stays STABLE and aria-expanded carries the state — the convention already in this
          repo (theme-toggle.tsx:174, export-dropdown-menu.tsx:74). A label that swaps with state
          alongside aria-expanded announces the same fact twice. */}
      <button
        type="button"
        className="relative inline-flex h-9 w-9 items-center justify-center rounded-md border border-border text-foreground hover:bg-muted"
        aria-label="Open notifications"
        aria-expanded={isOpen}
        onClick={toggleInbox}
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
