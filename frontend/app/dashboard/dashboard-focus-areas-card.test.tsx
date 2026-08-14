import { render, screen } from "@testing-library/react";
import { DashboardFocusAreasCard } from "./dashboard-focus-areas-card";

describe("DashboardFocusAreasCard", () => {
  it("attributes Adaptive Practice launches to Dashboard Focus Areas", () => {
    render(
      <DashboardFocusAreasCard
        focusAreas={{
          concepts: [{ conceptName: "Electrolytes", accuracyPercentage: 35 }],
          practiceNoteId: "note-1",
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
});
