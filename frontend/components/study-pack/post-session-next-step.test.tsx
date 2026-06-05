import { fireEvent, render, screen } from "@testing-library/react";
import { PostSessionNextStep } from "./post-session-next-step";
import type { PostSessionNextStepResponse } from "@/lib/api";

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
};

describe("PostSessionNextStep", () => {
  it("renders the primary route CTA and focus areas when a response is provided", () => {
    render(
      <PostSessionNextStep
        response={baseResponse}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={jest.fn()}
      />,
    );

    expect(screen.getByText("Recommended next step")).toBeInTheDocument();
    expect(screen.getByText("Mitosis")).toBeInTheDocument();
    expect(screen.getByText("Meiosis")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Practice Weak Concepts" })).toHaveAttribute(
      "href",
      "/notes/note-1/adaptive-practice",
    );
  });

  it("renders the plan-aware upgrade CTA instead of a route when adaptive quota is exhausted", () => {
    const onOpenPaywall = jest.fn();
    render(
      <PostSessionNextStep
        response={{ ...baseResponse, adaptivePracticeRemaining: 0 }}
        currentPlan="FREE"
        noteId="note-1"
        onOpenPaywall={onOpenPaywall}
      />,
    );

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
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
