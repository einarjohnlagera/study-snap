import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NearLimitBanner } from "./near-limit-banner";
import { trackAnalyticsEvent } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

describe("NearLimitBanner", () => {
  beforeEach(() => {
    window.history.replaceState(null, "", "/library");
    (trackAnalyticsEvent as jest.Mock).mockReset();
  });

  it("renders free-plan near-limit messaging", () => {
    render(
      <NearLimitBanner
        planType="FREE"
        remainingCredits={2}
        resetDateLabel="April 15"
        analyticsSource="test_near_limit_banner"
      />,
    );

    expect(screen.getByRole("status")).toHaveTextContent("You’re making progress this month — 2 Study Packs still ready to use on the Free plan.");
  });

  it("renders free-plan reached-limit messaging with reset date", () => {
    render(
      <NearLimitBanner
        planType="FREE"
        remainingCredits={0}
        resetDateLabel="April 15"
        analyticsSource="test_near_limit_banner"
      />,
    );

    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("You’ve reached your Free plan limit for this month.");
    expect(status).toHaveTextContent("Resets on April 15.");
  });

  it("renders pro reached-limit messaging with reset date", () => {
    render(
      <NearLimitBanner
        planType="PRO"
        remainingCredits={0}
        resetDateLabel="April 20"
        analyticsSource="test_near_limit_banner"
      />,
    );

    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("You’ve used all your Study Packs this month.");
    expect(status).toHaveTextContent("Resets on April 20.");
  });

  it("tracks one source-tagged view across unrelated re-renders and tracks CTA clicks", async () => {
    const onUpgrade = jest.fn();
    const { rerender } = render(
      <NearLimitBanner
        planType="FREE"
        remainingCredits={0}
        resetDateLabel="April 15"
        analyticsSource="test_near_limit_banner"
        onUpgrade={onUpgrade}
      />,
    );

    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "PAYWALL_VIEWED",
        metadata: {
          source: "test_near_limit_banner",
          feature: "near_limit",
          path: "/library",
          currentPlan: "FREE",
          remaining: 0,
          ctaContext: "study-pack-limit",
        },
      });
    });

    rerender(
      <NearLimitBanner
        planType="FREE"
        remainingCredits={0}
        resetDateLabel="April 15"
        analyticsSource="test_near_limit_banner"
        onUpgrade={onUpgrade}
        className="mt-4"
      />,
    );
    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "Get More Study Packs" }));

    expect(trackAnalyticsEvent).toHaveBeenLastCalledWith({
      eventType: "UPGRADE_CLICKED",
      metadata: {
        source: "test_near_limit_banner",
        feature: "near_limit",
        path: "/library",
        currentPlan: "FREE",
        remaining: 0,
        ctaContext: "study-pack-limit",
        target: "upgrade_surface",
      },
    });
    expect(onUpgrade).toHaveBeenCalledTimes(1);
  });
});
