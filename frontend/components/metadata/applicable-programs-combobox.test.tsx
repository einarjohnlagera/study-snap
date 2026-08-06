import { fireEvent, render, screen } from "@testing-library/react";
import { ApplicableProgramsCombobox } from "./applicable-programs-combobox";

const catalog = [
  { id: "program-a", name: "Civil Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
  { id: "program-b", name: "Mechanical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
];

const familyCatalog = [
  ...catalog,
  { id: "program-c", name: "Electrical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
  { id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null },
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

  it("adds every family member as a union and shows the added programs for trimming", () => {
    const onChange = jest.fn();
    const { rerender } = render(
      <ApplicableProgramsCombobox
        id="applicable-programs-family"
        catalog={familyCatalog}
        selectedIds={["program-nursing", "program-a"]}
        onChange={onChange}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Add all 2 Engineering programs" }));
    expect(onChange).toHaveBeenCalledWith([
      "program-nursing",
      "program-a",
      "program-b",
      "program-c",
    ]);

    rerender(
      <ApplicableProgramsCombobox
        id="applicable-programs-family"
        catalog={familyCatalog}
        selectedIds={["program-nursing", "program-a", "program-b", "program-c"]}
        onChange={onChange}
      />,
    );
    expect(screen.getByRole("button", { name: "Remove Civil Engineering" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Mechanical Engineering" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Electrical Engineering" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Add all .* Engineering/ })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Remove Mechanical Engineering" }));
    expect(onChange).toHaveBeenLastCalledWith(["program-nursing", "program-a", "program-c"]);
  });

  // ADR-001 ruling 4: expansion is NEVER subject-conditioned. The structural guard is that this
  // component's props carry no note context at all -- no subject, Domain Context, or learner level --
  // so there is nothing to condition on. This test pins the behavioural half of that: what a family
  // expands to is determined solely by catalog family membership. If a future change adds a
  // note-context prop and branches on it, that is the ruling being violated, and the props are where
  // to catch it.
  it("expands to exactly the catalog's family membership and nothing else", () => {
    const twoMemberExpansion = jest.fn();
    const { unmount } = render(
      <ApplicableProgramsCombobox
        id="applicable-programs-two"
        catalog={catalog}
        selectedIds={[]}
        onChange={twoMemberExpansion}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Add all 2 Engineering programs" }));
    expect(twoMemberExpansion).toHaveBeenCalledWith(["program-a", "program-b"]);
    unmount();

    // Same family, same component, one extra member in the catalog -- membership is the only input
    // that moves the result, and the unfamilied Nursing row is never pulled in.
    const threeMemberExpansion = jest.fn();
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-three"
        catalog={familyCatalog}
        selectedIds={[]}
        onChange={threeMemberExpansion}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Add all 3 Engineering programs" }));
    expect(threeMemberExpansion).toHaveBeenCalledWith(["program-a", "program-b", "program-c"]);
  });

  it("keeps programs without a family individually selectable and renders no family affordance", () => {
    const onChange = jest.fn();
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-no-families"
        catalog={[{ id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null }]}
        selectedIds={[]}
        onChange={onChange}
      />,
    );

    expect(screen.queryByLabelText("Program family shortcuts")).not.toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Toggle course program suggestions"));
    fireEvent.click(screen.getByRole("option", { name: "Nursing" }));
    expect(onChange).toHaveBeenCalledWith(["program-nursing"]);
  });

  it("does not offer family expansion while loading or disabled", () => {
    const { rerender } = render(
      <ApplicableProgramsCombobox
        id="applicable-programs-loading"
        catalog={familyCatalog}
        selectedIds={[]}
        onChange={jest.fn()}
        loading
      />,
    );

    expect(screen.getByLabelText("Add a course or program")).toBeDisabled();
    expect(screen.queryByLabelText("Program family shortcuts")).not.toBeInTheDocument();

    rerender(
      <ApplicableProgramsCombobox
        id="applicable-programs-disabled"
        catalog={familyCatalog}
        selectedIds={[]}
        onChange={jest.fn()}
        disabled
      />,
    );
    expect(screen.queryByLabelText("Program family shortcuts")).not.toBeInTheDocument();
  });

  it("disables the control and offers retry when loading fails", () => {
    const onRetry = jest.fn();
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-error"
        catalog={familyCatalog}
        selectedIds={[]}
        onChange={jest.fn()}
        error="Could not load the course program catalog."
        onRetry={onRetry}
      />,
    );

    expect(screen.getByLabelText("Add a course or program")).toBeDisabled();
    expect(screen.queryByLabelText("Program family shortcuts")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
