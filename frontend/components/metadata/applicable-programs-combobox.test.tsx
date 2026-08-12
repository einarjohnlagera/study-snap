import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { ApplicableProgramsCombobox } from "./applicable-programs-combobox";
import { createCourseProgram, findSimilarCoursePrograms } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error {
    code: string | null;
    details: string | null;
    status: number;
    constructor(message: string, options: { code?: string | null; details?: string | null; status: number }) {
      super(message);
      this.code = options.code ?? null;
      this.details = options.details ?? null;
      this.status = options.status;
    }
  },
  createCourseProgram: jest.fn(),
  findSimilarCoursePrograms: jest.fn(),
}));

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
  beforeEach(() => {
    (createCourseProgram as jest.Mock).mockReset();
    (findSimilarCoursePrograms as jest.Mock).mockReset();
    (findSimilarCoursePrograms as jest.Mock).mockResolvedValue([]);
  });
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

  it("shows catalog creation only to an admin curator when typed text has no exact match", async () => {
    const { rerender } = render(
      <ApplicableProgramsCombobox id="no-create" catalog={catalog} selectedIds={[]} onChange={jest.fn()} />,
    );
    fireEvent.focus(screen.getByLabelText("Add a course or program"));
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: "Chemical Engineering" } });
    expect(screen.queryByRole("button", { name: /Add “Chemical Engineering” to the catalog/ })).not.toBeInTheDocument();

    rerender(
      <ApplicableProgramsCombobox id="can-create" catalog={catalog} selectedIds={[]} onChange={jest.fn()} canCreateCatalogProgram />,
    );
    fireEvent.focus(screen.getByLabelText("Add a course or program"));
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: "Chemical Engineering" } });
    expect(await screen.findByRole("button", { name: /Add “Chemical Engineering” to the catalog/ })).toBeInTheDocument();
  });

  it("renders near matches before the explicit create action", async () => {
    (findSimilarCoursePrograms as jest.Mock).mockResolvedValue([catalog[0]]);
    render(<ApplicableProgramsCombobox id="near-match" catalog={catalog} selectedIds={[]} onChange={jest.fn()} canCreateCatalogProgram />);

    fireEvent.focus(screen.getByLabelText("Add a course or program"));
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: "Civil Engineer" } });

    expect(await screen.findByRole("button", { name: "Select Civil Engineering" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Add “Civil Engineer” to the catalog/ })).toBeInTheDocument();
  });

  it("creates and selects a catalog program without losing existing selections", async () => {
    const onChange = jest.fn();
    const created = { id: "program-new", name: "Chemical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" };
    (createCourseProgram as jest.Mock).mockResolvedValue(created);
    render(<ApplicableProgramsCombobox id="create-program" catalog={catalog} selectedIds={["program-a"]} onChange={onChange} canCreateCatalogProgram />);

    fireEvent.focus(screen.getByLabelText("Add a course or program"));
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: created.name } });
    fireEvent.click(await screen.findByRole("button", { name: /Add “Chemical Engineering” to the catalog/ }));
    fireEvent.change(screen.getByLabelText("Program Family (optional)"), { target: { value: "family-engineering" } });
    fireEvent.click(screen.getByRole("button", { name: "Add and select" }));

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(["program-a", "program-new"]));
    expect(createCourseProgram).toHaveBeenCalledWith({
      name: "Chemical Engineering",
      programFamilyId: "family-engineering",
      examGoalSlug: null,
    });
  });

  it("keeps typed text and current selections after a failed create", async () => {
    (createCourseProgram as jest.Mock).mockRejectedValue(new Error("Network unavailable"));
    render(<ApplicableProgramsCombobox id="failed-create" catalog={catalog} selectedIds={["program-a"]} onChange={jest.fn()} canCreateCatalogProgram />);

    const input = screen.getByLabelText("Add a course or program");
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "Chemical Engineering" } });
    fireEvent.click(await screen.findByRole("button", { name: /Add “Chemical Engineering” to the catalog/ }));
    fireEvent.click(screen.getByRole("button", { name: "Add and select" }));

    expect(await screen.findByText("Network unavailable")).toBeInTheDocument();
    expect(input).toHaveValue("Chemical Engineering");
    expect(screen.getByRole("button", { name: "Remove Civil Engineering" })).toBeInTheDocument();
  });

  it("turns a duplicate response into a select-existing action", async () => {
    const { ApiRequestError } = jest.requireMock("@/lib/api") as typeof import("@/lib/api");
    const onChange = jest.fn();
    (findSimilarCoursePrograms as jest.Mock).mockResolvedValue([catalog[0]]);
    (createCourseProgram as jest.Mock).mockRejectedValue(new ApiRequestError(
      "A Course / Program named \"Civil Engineering\" already exists.",
      { code: "COURSE_PROGRAM_CATALOG_NAME_CONFLICT", details: "Civil Engineering", status: 409 },
    ));
    render(<ApplicableProgramsCombobox id="duplicate-create" catalog={catalog} selectedIds={["program-b"]} onChange={onChange} canCreateCatalogProgram />);

    fireEvent.focus(screen.getByLabelText("Add a course or program"));
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: "Civil Engineer" } });
    await screen.findByRole("button", { name: "Select Civil Engineering" });
    fireEvent.click(screen.getByRole("button", { name: /Add “Civil Engineer” to the catalog/ }));
    fireEvent.click(screen.getByRole("button", { name: "Add and select" }));

    const dialog = await screen.findByRole("dialog", { name: "Add Course / Program" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Select Civil Engineering" }));
    expect(onChange).toHaveBeenCalledWith(["program-b", "program-a"]);
  });

  it("explains an empty selection when the author's profile programme is off-catalog", () => {
    // C8: a curator whose profile programme is not in the shared catalog got a bare "No course programs
    // selected." -- terse, and mystifying, because they HAVE a programme and cannot see why it counts
    // for nothing here. The empty state must say why and what to do instead.
    render(
      <ApplicableProgramsCombobox
        id="off-catalog"
        catalog={catalog}
        selectedIds={[]}
        onChange={jest.fn()}
        profileCourseProgram="BS Hotel Management"
      />,
    );

    expect(screen.getByText(/BS Hotel Management/)).toBeInTheDocument();
    expect(screen.getByText(/not in the shared catalog/)).toBeInTheDocument();
  });

  it("keeps the plain empty state when the profile programme IS in the catalog", () => {
    // Nothing to explain here: the author simply has not picked anything yet.
    render(
      <ApplicableProgramsCombobox
        id="on-catalog"
        catalog={catalog}
        selectedIds={[]}
        onChange={jest.fn()}
        profileCourseProgram="Civil Engineering"
      />,
    );

    expect(screen.getByText("No course programs selected.")).toBeInTheDocument();
    expect(screen.queryByText(/not in the shared catalog/)).not.toBeInTheDocument();
  });
});
