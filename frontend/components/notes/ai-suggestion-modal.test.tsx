import { fireEvent, render, screen } from "@testing-library/react";
import { AiSuggestionModal } from "./ai-suggestion-modal";

describe("AiSuggestionModal", () => {
  it("shows smart defaults and updates the preview when selections change", () => {
    render(
      <AiSuggestionModal
        open
        currentTitle="My Notes"
        currentSubject="General Science"
        currentTags={["review"]}
        suggestedTitle="Cell Structure Reviewer"
        suggestedSubject="Biology"
        suggestedTags={["cells", "anatomy"]}
        applying={false}
        onApply={() => undefined}
        onSkip={() => undefined}
      />,
    );

    expect(screen.getByLabelText("Keep My Title")).toBeChecked();
    expect(screen.getByLabelText("Keep My Subject")).toBeChecked();
    expect(screen.getByLabelText("Merge My Tags + AI Tags")).toBeChecked();

    fireEvent.click(screen.getByLabelText("Use AI Subject"));
    expect(screen.getByText("This is what will be saved if you apply the current selections.")).toBeInTheDocument();
    expect(screen.getAllByText("Biology").length).toBeGreaterThan(0);

    fireEvent.click(screen.getByLabelText("Use AI Tags Only"));
    expect(screen.getAllByText("cells").length).toBeGreaterThan(0);
    expect(screen.getAllByText("anatomy").length).toBeGreaterThan(0);
  });

  it("defaults empty metadata to AI values", () => {
    render(
      <AiSuggestionModal
        open
        currentTitle=""
        currentSubject={null}
        currentTags={[]}
        suggestedTitle="Cell Structure Reviewer"
        suggestedSubject="Biology"
        suggestedTags={["cells", "anatomy"]}
        applying={false}
        onApply={() => undefined}
        onSkip={() => undefined}
      />,
    );

    expect(screen.getByLabelText("Use AI Title")).toBeChecked();
    expect(screen.getByLabelText("Use AI Subject")).toBeChecked();
    expect(screen.getByLabelText("Use AI Tags Only")).toBeChecked();
  });

  it("shows only new AI tag suggestions and marks overlaps as already included", () => {
    render(
      <AiSuggestionModal
        open
        currentTitle="My Notes"
        currentSubject="History"
        currentTags={["history", "culture"]}
        suggestedTitle="Cinco de Mayo"
        suggestedSubject="History"
        suggestedTags={["History", "Battle of Puebla", "Culture"]}
        applying={false}
        onApply={() => undefined}
        onSkip={() => undefined}
      />,
    );

    expect(screen.getAllByText("Battle of Puebla").length).toBeGreaterThan(0);
    expect(screen.getByText("Already on your note")).toBeInTheDocument();
    expect(screen.getAllByText("History").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Culture").length).toBeGreaterThan(0);
  });

  it("shows no new tag suggestions when all AI tags already exist", () => {
    render(
      <AiSuggestionModal
        open
        currentTitle="My Notes"
        currentSubject="History"
        currentTags={["history", "culture"]}
        suggestedTitle="Cinco de Mayo"
        suggestedSubject="History"
        suggestedTags={["History", "Culture"]}
        applying={false}
        onApply={() => undefined}
        onSkip={() => undefined}
      />,
    );

    expect(screen.getByText("No new tag suggestions.")).toBeInTheDocument();
    expect(screen.getByLabelText("Keep My Tags")).toBeChecked();
    expect(screen.getByLabelText("Merge My Tags + AI Tags")).toBeDisabled();
  });
});
