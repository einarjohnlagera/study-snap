import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NotificationInbox } from "./notification-inbox";
import {
  dismissNotification,
  listNotifications,
  markNotificationRead,
} from "@/lib/api";

jest.mock("@/lib/api", () => ({
  listNotifications: jest.fn(),
  markNotificationRead: jest.fn(),
  dismissNotification: jest.fn(),
}));

const actionable = {
  id: "n-1",
  type: "ACTION_REQUIRED",
  title: "Someone wants to connect",
  body: null,
  ctaLabel: "Review",
  ctaPath: "/linked-learners",
  createdAt: "2026-09-07T00:00:00Z",
  readAt: null,
  dismissedAt: null,
};

function renderInbox(count = 0) {
  const onDelta = jest.fn();
  render(<NotificationInbox actionableUnreadCount={count} onActionableUnreadDelta={onDelta} />);
  return onDelta;
}

describe("NotificationInbox", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (listNotifications as jest.Mock).mockResolvedValue([actionable]);
    (markNotificationRead as jest.Mock).mockResolvedValue({ ...actionable, readAt: "2026-09-07T01:00:00Z" });
    (dismissNotification as jest.Mock).mockResolvedValue({ ...actionable, dismissedAt: "2026-09-07T01:00:00Z" });
    globalThis.matchMedia = jest.fn().mockReturnValue({
      matches: false,
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    }) as unknown as typeof globalThis.matchMedia;
  });

  it("renders NO badge element at all when the actionable count is zero", () => {
    // ⚠️ Not a "0" and not an empty circle. A permanent zero on a bell trains people to ignore it,
    // which degrades the actionable half this badge exists for.
    renderInbox(0);

    expect(screen.queryByText("0")).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/unread notifications/)).not.toBeInTheDocument();
  });

  it("still renders when matchMedia is unavailable, instead of taking down the app shell", () => {
    // ⚠️ Blast radius, not tidiness: this component renders inside the app shell header, so throwing
    // here breaks EVERY authenticated page. matchMedia is genuinely absent in jsdom without a
    // polyfill and on non-browser render paths. Falls back to the desktop popover.
    (globalThis as unknown as { matchMedia?: unknown }).matchMedia = undefined;

    expect(() => renderInbox(2)).not.toThrow();
    expect(screen.getByLabelText("Open notifications")).toBeInTheDocument();
  });

  it("renders the count once there is something actionable", () => {
    renderInbox(3);

    expect(screen.getByLabelText("3 unread notifications")).toBeInTheDocument();
  });

  it("does NOT mark everything read when the panel opens", async () => {
    // ⚠️ Mark-all-on-open silently discards the one signal a learner needs for a pending request.
    renderInbox(1);

    fireEvent.click(screen.getByLabelText("Open notifications"));

    await waitFor(() => expect(listNotifications).toHaveBeenCalled());
    expect(markNotificationRead).not.toHaveBeenCalled();
  });

  it("renders an announcement row but never lets it reach the badge", async () => {
    (listNotifications as jest.Mock).mockResolvedValue([{
      ...actionable,
      id: "n-announcement",
      type: "ANNOUNCEMENT",
      title: "Board Exam Mode is here",
      ctaLabel: "Try it",
      ctaPath: "/dashboard?tab=exams",
    }]);
    renderInbox(0);
    fireEvent.click(screen.getByLabelText("Open notifications"));

    expect(await screen.findByText("Board Exam Mode is here")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Try it" })).toHaveAttribute("href", "/dashboard?tab=exams");
    // ⚠️ Guard 8, from the render side: an unread announcement with zero actionable leaves NO badge
    // element — not a "0".
    expect(screen.queryByLabelText(/unread notifications/)).not.toBeInTheDocument();
  });

  it("refuses to render a CTA that is not a same-origin relative path", async () => {
    // ⚠️ THE STORED VALUE IS UNTRUSTED AT RENDER TIME. next/link renders an absolute URL as a live
    // external anchor, so an off-site cta_path — written before the write-side rule existed, or by any
    // future path that forgets it — must produce NO link rather than a link off-site.
    (listNotifications as jest.Mock).mockResolvedValue([{
      ...actionable,
      id: "n-phishy",
      type: "ANNOUNCEMENT",
      title: "Suspicious",
      ctaLabel: "Click here",
      ctaPath: "https://evil.example",
    }]);
    renderInbox(0);
    fireEvent.click(screen.getByLabelText("Open notifications"));

    expect(await screen.findByText("Suspicious")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Click here" })).not.toBeInTheDocument();
  });

  it("distinguishes a failed load from an empty inbox", async () => {
    // ⚠️ Conflating them tells a learner they have nothing when they may have a pending request.
    (listNotifications as jest.Mock).mockRejectedValue(new Error("offline"));
    renderInbox(1);

    fireEvent.click(screen.getByLabelText("Open notifications"));

    expect(await screen.findByText("Could not load notifications.")).toBeInTheDocument();
    expect(screen.queryByText("Your inbox is empty.")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("restores the row and the badge when a dismiss fails", async () => {
    // ⚠️ A failed dismiss must not hide the row -- that would lose the notification silently.
    (dismissNotification as jest.Mock).mockRejectedValue(new Error("offline"));
    renderInbox(1);

    fireEvent.click(screen.getByLabelText("Open notifications"));
    fireEvent.click(await screen.findByLabelText("Dismiss Someone wants to connect"));

    await waitFor(() => expect(dismissNotification).toHaveBeenCalled());
    expect(await screen.findByText("Someone wants to connect")).toBeInTheDocument();
  });

  it("reverts the optimistic unread delta when marking read fails", async () => {
    (markNotificationRead as jest.Mock).mockRejectedValue(new Error("offline"));
    const onDelta = renderInbox(1);

    fireEvent.click(screen.getByLabelText("Open notifications"));
    fireEvent.click(await screen.findByRole("button", { name: "Mark read" }));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalled());
    expect(onDelta).toHaveBeenNthCalledWith(1, -1);
    expect(onDelta).toHaveBeenNthCalledWith(2, 1);
  });
});
