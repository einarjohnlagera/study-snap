import { fireEvent, render, screen } from "@testing-library/react";
import { ApplicableProgramsCombobox } from "./applicable-programs-combobox";

const catalog = [
  { id: "program-a", name: "Civil Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
  { id: "program-b", name: "Mechanical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
];

describe("ApplicableProgramsCombobox", () => {
  it("selects only catalog rows and removes selected programs", () => {
    const onChange = jest.fn();
    const { rerender } = render(
      <ApplicableProgramsCombobox
        id="applicable-programs"
        catalog={catalog}
        selectedIds={["program-a"]}
        onChange={onChange}
      />,
    );

    expect(screen.getByText("Civil Engineering")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Toggle course program suggestions"));
    fireEvent.click(screen.getByRole("option", { name: "Mechanical Engineering" }));
    expect(onChange).toHaveBeenCalledWith(["program-a", "program-b"]);

    rerender(
      <ApplicableProgramsCombobox
        id="applicable-programs"
        catalog={catalog}
        selectedIds={["program-a", "program-b"]}
        onChange={onChange}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Remove Civil Engineering" }));
    expect(onChange).toHaveBeenLastCalledWith(["program-b"]);
    expect(screen.queryByText("Custom")).not.toBeInTheDocument();
  });

  it("disables the control and offers retry when loading fails", () => {
    const onRetry = jest.fn();
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-error"
        catalog={[]}
        selectedIds={[]}
        onChange={jest.fn()}
        error="Could not load the course program catalog."
        onRetry={onRetry}
      />,
    );

    expect(screen.getByLabelText("Add an applicable program")).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
