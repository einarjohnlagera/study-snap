import { fireEvent, render, screen } from "@testing-library/react";
import { ExplorePageClient } from "./explore-page-client";
import { trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

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

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: jest.fn(),
}));

const publishedPlansPageClientMock = jest.fn((_props: unknown) => <div>Review Sets Data</div>);

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
    (requireAuthenticatedOnboardedUser as jest.Mock).mockReset();
    (requireAuthenticatedOnboardedUser as jest.Mock).mockReturnValue(true);
    publishedPlansPageClientMock.mockClear();
  });

  it("labels the Review Sets tab distinctly from BOARD_EXAM's Collections nav label", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });

    render(<ExplorePageClient />);

    expect(await screen.findByRole("tab", { name: "Official Review Sets" })).toHaveAttribute("aria-selected", "true");
    expect(screen.queryByRole("tab", { name: "Review Sets" })).not.toBeInTheDocument();
  });

  it("renders both independent discovery sources with Review Sets selected by default", async () => {
    render(<ExplorePageClient />);

    expect(await screen.findByRole("heading", { name: "Explore" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Official Collections" })).toHaveAttribute("aria-selected", "true");
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

  it("does not mount discovery data when the authenticated route guard rejects access", () => {
    (requireAuthenticatedOnboardedUser as jest.Mock).mockReturnValue(false);

    render(<ExplorePageClient />);

    expect(screen.queryByText("Review Sets Data")).not.toBeInTheDocument();
    expect(screen.queryByText("Notes Data")).not.toBeInTheDocument();
  });
});
