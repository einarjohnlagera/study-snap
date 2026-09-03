import { fireEvent, render, screen } from "@testing-library/react";
import { DashboardFocusAreasCard } from "./dashboard-focus-areas-card";

describe("DashboardFocusAreasCard", () => {
  it("attributes Adaptive Practice launches to Dashboard Focus Areas", () => {
    render(
      <DashboardFocusAreasCard
        focusAreas={{
          concepts: [{ conceptName: "Electrolytes", accuracyPercentage: 35 }],
          practiceNoteId: "note-1",
      practiceCollectionId: null,
      practiceCollectionTitle: null,
          adaptivePracticeAvailable: true,
        }}
        onUnlockAdaptivePractice={jest.fn()}
      />,
    );

    expect(screen.getByRole("link", { name: "Practice Weak Concepts" })).toHaveAttribute(
      "href",
      "/notes/note-1/adaptive-practice?entry=dashboard-focus-areas",
    );
  });

  it("offers the plan-scoped action only when the server resolved a plan", () => {
    const onPracticeAcrossPlan = jest.fn();
    const { rerender } = render(
      <DashboardFocusAreasCard
        focusAreas={{
          concepts: [{ conceptName: "Shear Force", accuracyPercentage: 40 }],
          practiceNoteId: "note-1",
          practiceCollectionId: null,
          practiceCollectionTitle: null,
          adaptivePracticeAvailable: true,
        }}
        onUnlockAdaptivePractice={jest.fn()}
        onPracticeAcrossPlan={onPracticeAcrossPlan}
      />,
    );
    // No plan resolved server-side -> no plan action. The client must never pick one itself.
    expect(screen.queryByRole("button", { name: "Practice Across This Plan" })).not.toBeInTheDocument();

    rerender(
      <DashboardFocusAreasCard
        focusAreas={{
          concepts: [{ conceptName: "Shear Force", accuracyPercentage: 40 }],
          practiceNoteId: "note-1",
          practiceCollectionId: "collection-1",
          practiceCollectionTitle: "CE Board Review",
          adaptivePracticeAvailable: true,
        }}
        onUnlockAdaptivePractice={jest.fn()}
        onPracticeAcrossPlan={onPracticeAcrossPlan}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Practice Across This Plan" }));
    expect(onPracticeAcrossPlan).toHaveBeenCalledWith("collection-1");
  });


  it("surfaces the server's message instead of silently doing nothing", () => {
    // The button's render predicate is wider than server eligibility, so a decline is reachable.
    // Without a visible notice the button just un-spins and the learner has no idea why.
    render(
      <DashboardFocusAreasCard
        focusAreas={{
          concepts: [{ conceptName: "Shear Force", accuracyPercentage: 40 }],
          practiceNoteId: "note-1",
          practiceCollectionId: "collection-1",
          practiceCollectionTitle: "CE Board Review",
          adaptivePracticeAvailable: true,
        }}
        onUnlockAdaptivePractice={jest.fn()}
        onPracticeAcrossPlan={jest.fn()}
        planPracticeNotice="You have another Adaptive Practice session in progress on one of this plan's notes."
      />,
    );
    expect(
      screen.getByText(/another Adaptive Practice session in progress/i),
    ).toBeInTheDocument();
  });

});
