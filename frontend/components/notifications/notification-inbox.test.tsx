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
  type: "REVIEW_SET_UPDATE",
  actionable: true,
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
      actionable: false,
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
      actionable: false,
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

  it("closes the desktop panel when a click lands outside it", async () => {
    renderInbox(1);
    fireEvent.click(screen.getByLabelText("Open notifications"));
    await screen.findByText("Someone wants to connect");

    fireEvent.mouseDown(document.body);

    await waitFor(() => expect(screen.queryByText("Someone wants to connect")).not.toBeInTheDocument());
  });

  it("closes the desktop panel on Escape", async () => {
    renderInbox(1);
    fireEvent.click(screen.getByLabelText("Open notifications"));
    await screen.findByText("Someone wants to connect");

    fireEvent.keyDown(document, { key: "Escape" });

    await waitFor(() => expect(screen.queryByText("Someone wants to connect")).not.toBeInTheDocument());
  });

  it("closes the desktop panel when the CTA is activated, not just marks it read", async () => {
    // ⚠️ A5. Of the three existing close-path tests here (outside click, Escape, bell toggle), NONE
    // covered the close path a learner actually takes — following the CTA. The bug: the CTA <Link>
    // called markRead and never setIsOpen(false), so the panel stayed open over the destination page.
    renderInbox(1);
    fireEvent.click(screen.getByLabelText("Open notifications"));
    await screen.findByText("Someone wants to connect");

    fireEvent.click(screen.getByRole("link", { name: "Review" }));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(screen.queryByText("Someone wants to connect")).not.toBeInTheDocument();
  });

  it("closes the mobile sheet when the CTA is activated too, sharing the same fix", async () => {
    // ⚠️ A5, mobile half. Desktop and mobile render the same `rows` block, so one fix covers both —
    // but every OTHER test in this file mocks matchMedia to matches: false, so isMobile is never
    // true when the suite runs. This is the one test that actually exercises the AppModal sheet
    // branch, matching the repo's own rule: a behaviour change with no test that runs the path it
    // touched is unverified, not "covered by construction".
    globalThis.matchMedia = jest.fn().mockReturnValue({
      matches: true,
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    }) as unknown as typeof globalThis.matchMedia;

    renderInbox(1);
    fireEvent.click(screen.getByLabelText("Open notifications"));
    await screen.findByText("Someone wants to connect");

    fireEvent.click(screen.getByRole("link", { name: "Review" }));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(screen.queryByText("Someone wants to connect")).not.toBeInTheDocument();
  });

  it("closes exactly once when the bell itself is clicked while open, without refetching", async () => {
    // ⚠️⚠️ THE LOAD-BEARING GUARD, AND ITS EVENT SEQUENCE *IS* THE GUARD. A real click fires mousedown
    // THEN click, so the outside-click handler and the toggle both see it. If the ref wrapped only the
    // panel, the bell would count as "outside": mousedown closes, then click re-opens AND refetches, so
    // the panel flickers instead of closing.
    //
    // ⚠️ fireEvent.click alone does NOT fire mousedown, and this project has no @testing-library/
    // user-event — so a test that only clicks passes under the bug by construction. Both events are
    // dispatched explicitly below for that reason.
    //
    // ⚠️ Asserting the REQUEST COUNT, not the DOM: the panel can close and reopen within one sequence
    // and still read as "open", so visible state cannot tell the two behaviours apart.
    renderInbox(1);
    const bell = screen.getByLabelText("Open notifications");

    fireEvent.click(bell);
    await screen.findByText("Someone wants to connect");
    expect(listNotifications).toHaveBeenCalledTimes(1);

    fireEvent.mouseDown(bell);
    fireEvent.click(bell);

    await waitFor(() => expect(screen.queryByText("Someone wants to connect")).not.toBeInTheDocument());
    expect(listNotifications).toHaveBeenCalledTimes(1);
  });

  it("still loads the inbox on a closed to open transition", async () => {
    // ⚠️ The other half of the toggle. Only the re-click WHILE OPEN skips the fetch; opening always loads.
    renderInbox(1);
    const bell = screen.getByLabelText("Open notifications");

    fireEvent.click(bell);
    await waitFor(() => expect(listNotifications).toHaveBeenCalledTimes(1));

    fireEvent.mouseDown(bell);
    fireEvent.click(bell);
    await waitFor(() => expect(screen.queryByText("Someone wants to connect")).not.toBeInTheDocument());

    fireEvent.click(bell);

    await waitFor(() => expect(listNotifications).toHaveBeenCalledTimes(2));
  });

  it("tracks open state on the trigger with aria-expanded and a stable label", async () => {
    // ⚠️ The label stays put and aria-expanded carries the state — the convention already used by
    // theme-toggle.tsx and export-dropdown-menu.tsx. Swapping both announces the same fact twice.
    renderInbox(1);
    const bell = screen.getByLabelText("Open notifications");
    expect(bell).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(bell);

    await screen.findByText("Someone wants to connect");
    expect(screen.getByLabelText("Open notifications")).toHaveAttribute("aria-expanded", "true");
  });

  it("no longer renders a Close button, because closing no longer depends on one", async () => {
    renderInbox(1);
    fireEvent.click(screen.getByLabelText("Open notifications"));

    await screen.findByText("Someone wants to connect");
    expect(screen.queryByRole("button", { name: "Close" })).not.toBeInTheDocument();
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

  it("decrements the badge when an actionable notification is marked read", async () => {
    const onDelta = renderInbox(1);

    fireEvent.click(screen.getByLabelText("Open notifications"));
    fireEvent.click(await screen.findByRole("button", { name: "Mark read" }));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalled());
    expect(onDelta).toHaveBeenCalledTimes(1);
    expect(onDelta).toHaveBeenCalledWith(-1);
  });

  it("does not change the badge when a non-actionable notification is marked read", async () => {
    const nonActionable = {
      ...actionable,
      id: "n-non-actionable",
      type: "ANNOUNCEMENT",
      actionable: false,
    };
    (listNotifications as jest.Mock).mockResolvedValue([nonActionable]);
    const onDelta = renderInbox(0);

    fireEvent.click(screen.getByLabelText("Open notifications"));
    fireEvent.click(await screen.findByRole("button", { name: "Mark read" }));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalled());
    expect(onDelta).not.toHaveBeenCalled();
  });

  // ⚠️ THE DISCRIMINATING TEST FOR THIS RELEASE. Every other fixture sets `actionable` to agree with
  // `type`, so the old `type !== "ANNOUNCEMENT"` branch and the new `actionable` branch return the
  // same answer for all of them -- they pass identically against both implementations. This fixture
  // is the one where the two DISAGREE: a non-actionable type whose name is not "ANNOUNCEMENT" is
  // exactly the case the taxonomy split exists to fix, and the old code decremented the badge for it.
  it("does not change the badge for a non-actionable type that is not ANNOUNCEMENT", async () => {
    (listNotifications as jest.Mock).mockResolvedValue([{
      ...actionable,
      id: "n-future-non-actionable",
      type: "IMPACT_MILESTONE",
      actionable: false,
    }]);
    const onDelta = renderInbox(0);

    fireEvent.click(screen.getByLabelText("Open notifications"));
    fireEvent.click(await screen.findByRole("button", { name: "Mark read" }));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalled());
    expect(onDelta).not.toHaveBeenCalled();
  });

  it("does not restore the badge on a failed non-actionable mark-read", async () => {
    (listNotifications as jest.Mock).mockResolvedValue([{
      ...actionable,
      id: "n-non-actionable",
      type: "ANNOUNCEMENT",
      actionable: false,
    }]);
    (markNotificationRead as jest.Mock).mockRejectedValue(new Error("offline"));
    const onDelta = renderInbox(0);

    fireEvent.click(screen.getByLabelText("Open notifications"));
    fireEvent.click(await screen.findByRole("button", { name: "Mark read" }));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalled());
    expect(onDelta).not.toHaveBeenCalled();
  });
});
