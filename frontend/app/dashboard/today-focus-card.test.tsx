import { fireEvent, render, screen } from "@testing-library/react";
import { TodayFocusCard } from "./today-focus-card";

const dueConcepts = [{
  concept: "Antibiotics",
  noteId: "note-1",
  noteTitle: "Pharmacology Review",
}];

describe("TodayFocusCard", () => {
  it("gives a paid learner Adaptive Practice for due concepts", () => {
    render(
      <TodayFocusCard
        focus={{
          type: "DUE_CONCEPTS_REVIEW",
          studyPackId: "pack-1",
          noteId: "note-1",
          title: "Due concepts to review",
          message: "1 concept is due for review in \"Pharmacology Review\".",
          actionLabel: "Practice Due Concepts",
          concepts: dueConcepts,
          adaptivePracticeAvailable: true,
        }}
        onUnlockAdaptivePractice={jest.fn()}
      />,
    );

    expect(screen.getByText("Antibiotics")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Pharmacology Review" })).toHaveAttribute("href", "/notes/note-1");
    expect(screen.getByRole("link", { name: "Practice Due Concepts" })).toHaveAttribute(
      "href",
      "/notes/note-1/adaptive-practice",
    );
  });

  it("gives a Free learner a source-note revisit path instead of an Adaptive Practice launch", () => {
    render(
      <TodayFocusCard
        focus={{
          type: "DUE_CONCEPTS_REVIEW",
          studyPackId: "pack-1",
          noteId: "note-1",
          title: "Due concepts to review",
          message: "1 concept is due for review in \"Pharmacology Review\".",
          actionLabel: "Practice Due Concepts",
          concepts: dueConcepts,
          adaptivePracticeAvailable: false,
        }}
        onUnlockAdaptivePractice={jest.fn()}
      />,
    );

    expect(screen.getByRole("link", { name: "Revisit Note" })).toHaveAttribute("href", "/notes/note-1");
    expect(screen.queryByRole("link", { name: "Practice Due Concepts" })).not.toBeInTheDocument();
  });

  it("keeps the locked Adaptive Practice action available when no source note can be resolved", () => {
    const onUnlockAdaptivePractice = jest.fn();
    render(
      <TodayFocusCard
        focus={{
          type: "DUE_CONCEPTS_REVIEW",
          studyPackId: "pack-1",
          noteId: null,
          title: "Due concepts to review",
          message: "1 concept is due for review.",
          actionLabel: "Practice Due Concepts",
          concepts: [{ ...dueConcepts[0], noteId: null }],
          adaptivePracticeAvailable: false,
        }}
        onUnlockAdaptivePractice={onUnlockAdaptivePractice}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Unlock Adaptive Practice" }));
    expect(onUnlockAdaptivePractice).toHaveBeenCalledTimes(1);
  });
});
