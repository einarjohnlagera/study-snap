import {render, screen} from "@testing-library/react";
import {NoteDetailSummaryCard} from "./note-detail-summary-card";

const onViewFullNotes = jest.fn();

describe("NoteDetailSummaryCard", () => {
  it("shows Study Pack scope when concept and quiz data are available", () => {
    render(
      <NoteDetailSummaryCard
        summary="A concise summary."
        studyPackReady
        keyConceptCount={4}
        quizCount={3}
        onViewFullNotes={onViewFullNotes}
      />,
    );

    expect(screen.getByTestId("study-pack-scope")).toHaveTextContent("4 concepts · 3 questions · ~5 min");
  });

  it("keeps partial scope useful when the quiz is empty", () => {
    render(
      <NoteDetailSummaryCard
        summary="A concise summary."
        studyPackReady
        keyConceptCount={3}
        quizCount={0}
        onViewFullNotes={onViewFullNotes}
      />,
    );

    expect(screen.getByTestId("study-pack-scope")).toHaveTextContent("3 concepts · ~2 min");
    expect(screen.getByTestId("study-pack-scope")).not.toHaveTextContent("questions");
  });

  it("hides scope when no Study Pack is ready", () => {
    render(
      <NoteDetailSummaryCard
        summary="A concise summary."
        studyPackReady={false}
        keyConceptCount={1}
        quizCount={1}
        onViewFullNotes={onViewFullNotes}
      />,
    );

    expect(screen.queryByTestId("study-pack-scope")).not.toBeInTheDocument();
  });
});
