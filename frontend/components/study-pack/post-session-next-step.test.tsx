import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PostSessionNextStep } from "./post-session-next-step";
import { trackAnalyticsEvent, type PostSessionNextStepResponse } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

const baseResponse: PostSessionNextStepResponse = {
  type: "PRACTICE_WEAK_CONCEPT",
  studyPackId: "pack-1",
  noteId: "note-1",
  title: "Cell Biology",
  message: "2 concepts are due for review. Practice them while they are fresh.",
  actionLabel: "Practice Weak Concepts",
  actionHref: "/notes/note-1/adaptive-practice",
  concepts: ["Mitosis", "Meiosis"],
  adaptivePracticeAvailable: true,
  adaptivePracticeRemaining: 2,
  goalNudge: null,
  secondaryAction: null,
};

describe("PostSessionNextStep", () => {
  beforeEach(() => {
    (trackAnalyticsEvent as jest.Mock).mockReset().mockResolvedValue(undefined);
  });

  it("renders the primary route CTA and focus areas without a Challenge impression", async () => {
    render(
      <PostSessionNextStep
        response={baseResponse}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={jest.fn()}
        originatingQuizMode="QUICK_REVIEW"
      />,
    );

    expect(screen.getByText("Recommended next step")).toBeInTheDocument();
    expect(screen.getByText("Mitosis")).toBeInTheDocument();
    expect(screen.getByText("Meiosis")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Practice Weak Concepts" })).toHaveAttribute(
      "href",
      "/notes/note-1/adaptive-practice",
    );
    await waitFor(() => expect(trackAnalyticsEvent).not.toHaveBeenCalled());
  });

  it("renders the plan-aware upgrade CTA instead of a route when adaptive quota is exhausted", () => {
    const onOpenPaywall = jest.fn();
    render(
      <PostSessionNextStep
        response={{ ...baseResponse, adaptivePracticeRemaining: 0 }}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={onOpenPaywall}
        originatingQuizMode="QUICK_REVIEW"
      />,
    );

    expect(screen.queryByRole("link", { name: "Practice Weak Concepts" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Get More Adaptive Practice" }));

    expect(onOpenPaywall).toHaveBeenCalledTimes(1);
  });

  it("renders a demoted weak-area action while keeping Challenge primary", () => {
    render(
      <PostSessionNextStep
        response={{
          ...baseResponse,
          type: "REVIEW_PACK",
          actionLabel: "Take a Challenge",
          actionHref: "/notes/note-1/challenge-quiz",
          secondaryAction: {
            actionLabel: "Practice Weak Concepts",
            actionHref: "/notes/note-1/adaptive-practice",
            adaptivePractice: true,
          },
        }}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={jest.fn()}
        originatingQuizMode="QUICK_REVIEW"
      />,
    );

    expect(screen.getByRole("link", { name: "Take a Challenge" })).toHaveAttribute(
      "href",
      "/notes/note-1/challenge-quiz",
    );
    expect(screen.getByRole("link", { name: "Practice Weak Concepts" })).toHaveAttribute(
      "href",
      "/notes/note-1/adaptive-practice",
    );
  });

  it("keeps Challenge reachable when the secondary Adaptive action is quota-blocked", () => {
    const onOpenPaywall = jest.fn();
    render(
      <PostSessionNextStep
        response={{
          ...baseResponse,
          type: "REVIEW_PACK",
          actionLabel: "Take a Challenge",
          actionHref: "/notes/note-1/challenge-quiz",
          adaptivePracticeRemaining: 0,
          secondaryAction: {
            actionLabel: "Practice Weak Concepts",
            actionHref: "/notes/note-1/adaptive-practice",
            adaptivePractice: true,
          },
        }}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={onOpenPaywall}
        originatingQuizMode="QUICK_REVIEW"
      />,
    );

    expect(screen.getByRole("link", { name: "Take a Challenge" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Practice Weak Concepts" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Get More Adaptive Practice" }));

    expect(onOpenPaywall).toHaveBeenCalledTimes(1);
  });

  it("renders nothing when response is null", () => {
    const { container } = render(
      <PostSessionNextStep
        response={null}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={jest.fn()}
        originatingQuizMode="QUICK_REVIEW"
      />,
    );

    expect(container).toBeEmptyDOMElement();
    expect(trackAnalyticsEvent).not.toHaveBeenCalled();
  });

  it("tracks one impression when a Challenge action is actually rendered", async () => {
    const response = {
      ...baseResponse,
      type: "REVIEW_PACK" as const,
      actionLabel: "Take a Challenge",
      actionHref: "/notes/note-1/challenge-quiz",
    };
    const { rerender } = render(
      <PostSessionNextStep
        response={response}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={jest.fn()}
        originatingQuizMode="QUICK_REVIEW"
      />,
    );

    await waitFor(() => expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "POST_SESSION_CHALLENGE_CTA_IMPRESSION",
      entityId: "pack-1",
      metadata: { originatingQuizMode: "QUICK_REVIEW" },
    }));
    rerender(
      <PostSessionNextStep
        response={response}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={jest.fn()}
        originatingQuizMode="QUICK_REVIEW"
      />,
    );

    expect((trackAnalyticsEvent as jest.Mock).mock.calls.filter(([event]) => (
      event.eventType === "POST_SESSION_CHALLENGE_CTA_IMPRESSION"
    ))).toHaveLength(1);
  });

  it("tracks a secondary Challenge click without preventing navigation", async () => {
    render(
      <PostSessionNextStep
        response={{
          ...baseResponse,
          actionLabel: "Retry Incorrect Questions",
          actionHref: "/notes/note-1/quick-review",
          secondaryAction: {
            actionLabel: "Take a Challenge",
            actionHref: "/notes/note-1/challenge-quiz",
            adaptivePractice: false,
          },
        }}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={jest.fn()}
        originatingQuizMode="QUICK_REVIEW"
      />,
    );

    const challengeLink = screen.getByRole("link", { name: "Take a Challenge" });
    expect(challengeLink).toHaveAttribute("href", "/notes/note-1/challenge-quiz");
    fireEvent.click(challengeLink);

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "POST_SESSION_CHALLENGE_CTA_CLICKED",
      entityId: "pack-1",
      metadata: { originatingQuizMode: "QUICK_REVIEW" },
    });
  });
});
