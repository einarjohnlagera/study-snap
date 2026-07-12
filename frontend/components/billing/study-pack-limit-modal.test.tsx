import { fireEvent, render, screen } from "@testing-library/react";
import { StudyPackLimitModal } from "./study-pack-limit-modal";
import { trackAnalyticsEvent } from "@/lib/api";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  usePathname: () => "/notes/note-1",
  useRouter: () => ({
    push: pushMock,
  }),
}));

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

describe("StudyPackLimitModal", () => {
  beforeEach(() => {
    pushMock.mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
  });

  it("renders the free-plan limit copy and dynamic upgrade CTAs", () => {
    render(
      <StudyPackLimitModal
        isOpen
        planType="FREE"
        resetDateLabel="April 15"
        onClose={jest.fn()}
        analyticsSource="test_study_pack_limit_modal"
      />,
    );

    expect(screen.getByText("You’ve reached your study pack limit")).toBeInTheDocument();
    expect(screen.getByText(/Upgrade for more Study Packs/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get More Study Packs" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Go Pro" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });

  it("routes free-plan upgrade CTA to /settings?section=plans", () => {
    const onClose = jest.fn();
    render(
      <StudyPackLimitModal
        isOpen
        planType="FREE"
        resetDateLabel="April 15"
        onClose={onClose}
        analyticsSource="test_study_pack_limit_modal"
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Get More Study Packs" }));
    expect(pushMock).toHaveBeenCalledWith("/settings?section=plans");
  });

  it("renders the plus-plan limit copy with single Upgrade to Pro CTA", () => {
    render(
      <StudyPackLimitModal
        isOpen
        planType="PLUS"
        resetDateLabel="April 18"
        onClose={jest.fn()}
        analyticsSource="test_study_pack_limit_modal"
      />,
    );

    expect(screen.getByText("You’ve reached your study pack limit for Plus")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upgrade to Pro" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Go Pro" })).not.toBeInTheDocument();
  });

  it("renders pro-limit copy with no upgrade CTAs (Pro is the top plan)", () => {
    render(
      <StudyPackLimitModal
        isOpen
        planType="PRO"
        resetDateLabel="April 20"
        onClose={jest.fn()}
        analyticsSource="test_study_pack_limit_modal"
      />,
    );

    expect(screen.getByText("You’ve reached your study pack limit for this month")).toBeInTheDocument();
    expect(screen.getByText(/Your study pack limit resets on April 20\./)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Upgrade to Plus" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Upgrade to Pro" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Go Pro" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Got It" })).toBeInTheDocument();
  });

  it("tracks one source-tagged view when opened and the upgrade CTA click", () => {
    const onClose = jest.fn();
    const { rerender } = render(
      <StudyPackLimitModal
        isOpen={false}
        planType="FREE"
        resetDateLabel="April 15"
        onClose={onClose}
        analyticsSource="test_study_pack_limit_modal"
      />,
    );

    expect(trackAnalyticsEvent).not.toHaveBeenCalled();

    rerender(
      <StudyPackLimitModal
        isOpen
        planType="FREE"
        resetDateLabel="April 15"
        onClose={onClose}
        analyticsSource="test_study_pack_limit_modal"
      />,
    );

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "PAYWALL_VIEWED",
      metadata: {
        source: "test_study_pack_limit_modal",
        feature: "study_pack_limit",
        path: "/notes/note-1",
        currentPlan: "FREE",
      },
    });

    rerender(
      <StudyPackLimitModal
        isOpen
        planType="FREE"
        resetDateLabel="April 16"
        onClose={onClose}
        analyticsSource="test_study_pack_limit_modal"
      />,
    );
    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "Get More Study Packs" }));

    expect(trackAnalyticsEvent).toHaveBeenLastCalledWith({
      eventType: "UPGRADE_CLICKED",
      metadata: {
        source: "test_study_pack_limit_modal",
        feature: "study_pack_limit",
        path: "/notes/note-1",
        currentPlan: "FREE",
        target: "settings_plan_billing",
      },
    });
  });
});
