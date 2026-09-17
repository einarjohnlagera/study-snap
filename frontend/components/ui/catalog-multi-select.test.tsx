import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { CatalogMultiSelect } from "./catalog-multi-select";

const items = [
  { id: "nursing", name: "Nursing" },
  { id: "medicine", name: "Medicine" },
];

function Harness({ summary = "chips" }: { summary?: "chips" | "count" }) {
  const [selectedIds, setSelectedIds] = useState<string[]>(["nursing"]);
  return <CatalogMultiSelect id="catalog" label="Course / Programs" items={items}
    selectedIds={selectedIds} onChange={setSelectedIds} selectedSummary={summary} />;
}

describe("CatalogMultiSelect", () => {
  it("uses real checked state and keeps a selected item visible under a nonmatching search", () => {
    render(<Harness />);
    const nursing = screen.getByRole("checkbox", { name: "Nursing" });
    expect(nursing).toBeChecked();
    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "Medicine" } });
    expect(screen.getByRole("checkbox", { name: "Nursing" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Medicine" })).toBeInTheDocument();
  });

  it("announces an empty search result", () => {
    render(<CatalogMultiSelect id="empty" label="Course / Programs" items={items}
      selectedIds={[]} onChange={jest.fn()} />);
    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "xyz" } });
    expect(screen.getByText('No programs match "xyz".')).toHaveAttribute("aria-live", "polite");
  });

  it("removing a chip deselects its checkbox", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: "Remove Nursing" }));
    expect(screen.getByRole("checkbox", { name: "Nursing" })).not.toBeChecked();
    expect(screen.getByRole("searchbox")).toHaveFocus();
  });

  it("clears every selection in count mode", () => {
    render(<Harness summary="count" />);
    fireEvent.click(screen.getByRole("button", { name: "Clear all selected" }));
    expect(screen.getByRole("checkbox", { name: "Nursing" })).not.toBeChecked();
  });
});
