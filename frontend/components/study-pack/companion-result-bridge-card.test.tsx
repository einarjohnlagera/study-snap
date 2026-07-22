import { render, screen } from "@testing-library/react";
import { CompanionResultBridgeCard } from "./companion-result-bridge-card";

describe("CompanionResultBridgeCard", () => {
  it("renders nothing when there is no companion", () => {
    const { container } = render(
      <CompanionResultBridgeCard companion={null} reviewSetLabel="Review Set" />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when the companion has no Common Mistakes or Study Strategy content", () => {
    const { container } = render(
      <CompanionResultBridgeCard
        companion={{
          overview: "Some overview",
          studyStrategy: "  ",
          commonMistakes: null,
          faq: [],
          mentorTips: [],
        }}
        reviewSetLabel="Review Set"
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("prefers Common Mistakes over Study Strategy when both are present", () => {
    render(
      <CompanionResultBridgeCard
        companion={{
          overview: null,
          studyStrategy: "Study a little every day.",
          commonMistakes: "Don't confuse mitosis with meiosis.",
          faq: [],
          mentorTips: [],
        }}
        reviewSetLabel="Review Set"
      />,
    );

    expect(screen.getByText(/Don't confuse mitosis with meiosis\./)).toBeInTheDocument();
    expect(screen.getByText(/Common Mistakes/)).toBeInTheDocument();
    expect(screen.queryByText(/Study a little every day\./)).not.toBeInTheDocument();
  });

  it("falls back to Study Strategy when Common Mistakes is empty", () => {
    render(
      <CompanionResultBridgeCard
        companion={{
          overview: null,
          studyStrategy: "Study a little every day.",
          commonMistakes: "   ",
          faq: [],
          mentorTips: [],
        }}
        reviewSetLabel="Review Set"
      />,
    );

    expect(screen.getByText(/Study a little every day\./)).toBeInTheDocument();
    expect(screen.getByText(/Study Strategy/)).toBeInTheDocument();
  });

  it("labels the card with the profile-aware review set label", () => {
    render(
      <CompanionResultBridgeCard
        companion={{
          overview: null,
          studyStrategy: null,
          commonMistakes: "Don't confuse mitosis with meiosis.",
          faq: [],
          mentorTips: [],
        }}
        reviewSetLabel="Study Plan"
      />,
    );

    expect(screen.getByText(/From your Study Plan's Companion/)).toBeInTheDocument();
  });
});
