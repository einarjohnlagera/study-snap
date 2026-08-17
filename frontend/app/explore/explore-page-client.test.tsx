import { fireEvent, render, screen } from "@testing-library/react";
import { ExplorePageClient } from "./explore-page-client";
import { trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const replaceMock = jest.fn();
let searchParams = new URLSearchParams();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
  useSearchParams: () => searchParams,
}));

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

const publishedPlansPageClientMock = jest.fn((props: unknown) => {
  void props;
  return <div>Review Sets Data</div>;
});

jest.mock("@/app/collections/published/published-plans-page-client", () => ({
  PublishedPlansPageClient: (props: unknown) => publishedPlansPageClientMock(props),
}));

jest.mock("@/components/notes/public-library-page-client", () => ({
  PublicLibraryPageClient: () => <div>Notes Data</div>,
}));

describe("ExplorePageClient", () => {
  beforeEach(() => {
    searchParams = new URLSearchParams();
    replaceMock.mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue(null);
    publishedPlansPageClientMock.mockClear();
  });

  it("labels the Review Sets tab distinctly from BOARD_EXAM's Collections nav label", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });

    render(<ExplorePageClient />);

    expect(await screen.findByRole("tab", { name: "Official Review Sets" })).toHaveAttribute("aria-selected", "true");
    expect(screen.queryByRole("tab", { name: "Review Sets" })).not.toBeInTheDocument();
  });

  it("renders anonymous visitors with learner vocabulary and no auth redirect", async () => {
    render(<ExplorePageClient />);

    expect(await screen.findByRole("heading", { name: "Explore" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Official Study Plans" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Review Sets Data")).toBeInTheDocument();
    expect(screen.getByText("Notes Data")).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("gives the selected tab a solid, theme-safe background instead of bg-background", async () => {
    // Regression guard for a dark-mode contrast bug: `bg-background` on the selected tab was
    // visually indistinguishable from the tablist's own `bg-surface-alt` container in dark mode
    // (both resolve to the same near-black), so the selected state was invisible. Fixed to match
    // the Settings page's billing-cycle toggle pattern: an explicit blue pill in both themes.
    render(<ExplorePageClient />);

    const selectedTab = await screen.findByRole("tab", { name: "Official Study Plans" });
    expect(selectedTab.className).toContain("bg-blue-600");
    expect(selectedTab.className).not.toContain("bg-background");
    // The focus ring (ring-blue-600) now matches the selected tab's own fill color, so without an
    // offset the ring becomes invisible against the tab it's supposed to highlight — a coupling a
    // future fill-color change could easily reintroduce without this assertion catching it.
    expect(selectedTab.className).toContain("focus-visible:ring-offset-2");
  });

  it("renders both independent discovery sources with Review Sets selected by default", async () => {
    render(<ExplorePageClient />);

    expect(await screen.findByRole("heading", { name: "Explore" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Official Study Plans" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Review Sets Data")).toBeInTheDocument();
    expect(screen.getByText("Notes Data")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse Exam Hubs" })).toHaveAttribute("href", "/exam");
  });

  it("switches to Notes through URL state and tracks the action non-blocking", async () => {
    render(<ExplorePageClient />);

    fireEvent.click(await screen.findByRole("tab", { name: "Notes" }));

    expect(replaceMock).toHaveBeenCalledWith("/explore?tab=notes", { scroll: false });
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "EXPLORE_TAB_SWITCHED",
      metadata: { tab: "notes" },
    });
  });

  it("hydrates the Notes tab from the query parameter without firing switch analytics", async () => {
    searchParams = new URLSearchParams({ tab: "notes" });

    render(<ExplorePageClient />);

    expect(await screen.findByRole("tab", { name: "Notes" })).toHaveAttribute("aria-selected", "true");
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "EXPLORE_VIEWED",
      entityId: null,
      metadata: { referrerSource: "direct" },
    });
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(
      expect.objectContaining({ eventType: "EXPLORE_TAB_SWITCHED" }),
    );
  });

  it("fires a single EXPLORE_VIEWED page-view event on mount", async () => {
    render(<ExplorePageClient />);

    await screen.findByRole("heading", { name: "Explore" });

    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "EXPLORE_VIEWED",
      entityId: null,
      metadata: { referrerSource: "direct" },
    });
  });

  it("tracks the pointer source exactly once across unrelated rerenders", async () => {
    searchParams = new URLSearchParams({ source: "collections" });
    const { rerender } = render(<ExplorePageClient />);

    await screen.findByRole("heading", { name: "Explore" });
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "EXPLORE_VIEWED",
      entityId: null,
      metadata: { source: "collections", referrerSource: "direct" },
    });

    searchParams = new URLSearchParams({ tab: "notes", source: "collections" });
    rerender(<ExplorePageClient />);
    expect(await screen.findByRole("tab", { name: "Notes" })).toHaveAttribute("aria-selected", "true");

    const explorePageViewCalls = (trackAnalyticsEvent as jest.Mock).mock.calls
      .filter(([event]) => event.eventType === "EXPLORE_VIEWED");
    expect(explorePageViewCalls).toHaveLength(1);
  });

  it("threads the pointer source into PublishedPlansPageClient as discoveryMetadata, keyed distinctly from `source`", async () => {
    searchParams = new URLSearchParams({ source: "dashboard" });

    render(<ExplorePageClient />);

    await screen.findByRole("heading", { name: "Explore" });

    expect(publishedPlansPageClientMock).toHaveBeenCalledWith(
      expect.objectContaining({
        discoverySource: "explore",
        discoveryMetadata: { pointerSource: "dashboard" },
      }),
    );
  });

  it("passes no discoveryMetadata when there is no pointer source", async () => {
    render(<ExplorePageClient />);

    await screen.findByRole("heading", { name: "Explore" });

    expect(publishedPlansPageClientMock).toHaveBeenCalledWith(
      expect.objectContaining({ discoveryMetadata: undefined }),
    );
  });

  it("renders a normal catalog notice when a saved plan can no longer be adopted", async () => {
    searchParams = new URLSearchParams({ discoveryNotice: "adopt-unavailable" });

    render(<ExplorePageClient />);

    expect(await screen.findByRole("status")).toHaveTextContent(
      "We couldn't start that study plan — it may no longer be available",
    );
    expect(screen.getByText("Review Sets Data")).toBeInTheDocument();
  });

  it("keeps the authenticated default tab and discovery sources unchanged", async () => {
    // BOARD_EXAM on purpose. Mocking STUDENT made this byte-identical to the anonymous
    // expectation, so it passed even when the anonymous fallback was forced onto every viewer —
    // the exact regression its name claims to guard. A profile whose vocabulary differs from the
    // anonymous default is the only fixture that can discriminate.
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });
    render(<ExplorePageClient />);

    expect(await screen.findByRole("tab", { name: "Official Review Sets" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Review Sets Data")).toBeInTheDocument();
    expect(screen.getByText("Notes Data")).toBeInTheDocument();
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "EXPLORE_VIEWED",
      entityId: null,
      metadata: { referrerSource: "direct" },
    });
  });
});
