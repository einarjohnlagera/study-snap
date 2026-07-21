import { render, waitFor } from "@testing-library/react";
import { trackAnalyticsEvent } from "@/lib/api";
import { AnalyticsPageViewTracker } from "./page-view-tracker";

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

describe("AnalyticsPageViewTracker", () => {
  beforeEach(() => {
    (trackAnalyticsEvent as jest.Mock).mockReset();
    Object.defineProperty(document, "referrer", {
      configurable: true,
      value: "https://www.google.com/search?q=notelib",
    });
  });

  it("merges the coarse referrer source with call-site metadata", async () => {
    render(<AnalyticsPageViewTracker eventType="LANDING_PAGE_VIEWED" metadata={{ page: "landing" }} />);

    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "LANDING_PAGE_VIEWED",
        entityId: null,
        metadata: {
          page: "landing",
          referrerSource: "google",
        },
      });
    });
  });
});
