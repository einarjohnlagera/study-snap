import { fireEvent, render, screen } from "@testing-library/react";
import { PlanPicker } from "./plan-picker";
import type { NoteCollectionSummary } from "@/lib/api";

function collection(id: string, title: string, childCount: number): NoteCollectionSummary {
  return {
    id,
    title,
    description: null,
    visibility: "PRIVATE",
    courseProgram: null,
    sourcePlanId: null,
    parentCollectionId: null,
    itemCount: 1,
    childCount,
    notesPracticed: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  };
}

const leaf = collection("leaf-1", "Biology Plan", 0);
const goal = collection("goal-1", "Board Exam Goal", 2);

describe("PlanPicker", () => {
  it("preserves the Progress picker defaults and filters selectable parent collections", () => {
    render(
      <PlanPicker
        collections={[leaf, goal]}
        selectedCollectionId={null}
        collectionsState="ready"
        onChange={jest.fn()}
      />,
    );

    expect(screen.getByLabelText("Progress view")).toHaveValue("");
    expect(screen.getByRole("option", { name: "All subjects" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Biology Plan" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Board Exam Goal" })).not.toBeInTheDocument();
  });

  it("can select among top-level official sets without changing the Progress defaults", () => {
    const onChange = jest.fn();
    render(
      <PlanPicker
        id="official-set-picker"
        label="Official Review Set"
        description="Choose a set."
        collections={[leaf, goal]}
        selectedCollectionId="leaf-1"
        collectionsState="ready"
        includeParentCollections
        showEmptyOption={false}
        onChange={onChange}
      />,
    );

    expect(screen.getByRole("option", { name: "Board Exam Goal" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Official Review Set"), { target: { value: "goal-1" } });
    expect(onChange).toHaveBeenCalledWith("goal-1");
  });
});
