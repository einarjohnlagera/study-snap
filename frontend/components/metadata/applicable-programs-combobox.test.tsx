import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { ApplicableProgramsCombobox } from "./applicable-programs-combobox";
import { createCourseProgram, findSimilarCoursePrograms, listProgramFamilies } from "@/lib/api";

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
  listProgramFamilies: jest.fn(),
}));

const catalog = [
  { id: "program-a", name: "Civil Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering", isActive: true },
  { id: "program-b", name: "Mechanical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering", isActive: true },
];

const familyCatalog = [
  ...catalog,
  { id: "program-c", name: "Electrical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering", isActive: true },
  { id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null, isActive: true },
];

/**
 * ⚠️ EVERY FIXTURE ABOVE HOLDS EXACTLY ONE FAMILY, AND UNTIL v0.133.0 SO DID PRODUCTION.
 *
 * Engineering was the only family with members, so the component's multi-family paths — which family a
 * button expands, whether one expansion disturbs another family's selections, whether two families can
 * be mixed — were never exercised by anything. `V142` seeds Education and makes them real.
 *
 * A single-family fixture cannot fail on a component that ignores which family was clicked and expands
 * everything, so it proves nothing about family SCOPING. That is what this catalog is for.
 */
const twoFamilyCatalog = [
  { id: "program-a", name: "Civil Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering", isActive: true },
  { id: "program-b", name: "Mechanical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering", isActive: true },
  { id: "program-elem", name: "Elementary Education", programFamilyId: "family-education", programFamilyName: "Education", isActive: true },
  { id: "program-sec", name: "Secondary Education", programFamilyId: "family-education", programFamilyName: "Education", isActive: true },
  { id: "program-ece", name: "Early Childhood Education", programFamilyId: "family-education", programFamilyName: "Education", isActive: true },
  { id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null, isActive: true },
];

describe("ApplicableProgramsCombobox", () => {
  beforeEach(() => {
    (createCourseProgram as jest.Mock).mockReset();
    (findSimilarCoursePrograms as jest.Mock).mockReset();
    (listProgramFamilies as jest.Mock).mockReset();
    (findSimilarCoursePrograms as jest.Mock).mockResolvedValue([]);
    (listProgramFamilies as jest.Mock).mockResolvedValue([
      { id: "family-engineering", name: "Engineering" },
      { id: "family-health", name: "Health Sciences" },
    ]);
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

    expect(screen.getByRole("button", { name: "Engineering · 2 remaining" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Engineering · 2 remaining" }));
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
    expect(screen.getByLabelText("Engineering — all 3 programs added")).toHaveTextContent("✓ Engineering · 3");

    fireEvent.click(screen.getByRole("button", { name: "Remove Mechanical Engineering" }));
    expect(onChange).toHaveBeenLastCalledWith(["program-nursing", "program-a", "program-c"]);
  });

  it("keeps the full 18-member Engineering expansion", () => {
    const engineeringCatalog = Array.from({ length: 18 }, (_, index) => ({
      id: `engineering-${index + 1}`,
      name: `Engineering Program ${index + 1}`,
      programFamilies: [{ id: "family-engineering", name: "Engineering" }],
      programFamilyId: "family-engineering",
      programFamilyName: "Engineering",
      isActive: true,
    }));
    const onChange = jest.fn();
    render(<ApplicableProgramsCombobox id="engineering-18" catalog={engineeringCatalog} selectedIds={[]} onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Engineering · 18" }));
    expect(onChange).toHaveBeenCalledWith(engineeringCatalog.map((program) => program.id));
  });

  it("keeps the full 8-member Education expansion", () => {
    const educationCatalog = Array.from({ length: 8 }, (_, index) => ({
      id: `education-${index + 1}`,
      name: `Education Program ${index + 1}`,
      programFamilies: [{ id: "family-education", name: "Education" }],
      programFamilyId: "family-education",
      programFamilyName: "Education",
      isActive: true,
    }));
    const onChange = jest.fn();
    render(<ApplicableProgramsCombobox id="education-8" catalog={educationCatalog} selectedIds={[]} onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Education · 8" }));
    expect(onChange).toHaveBeenCalledWith(educationCatalog.map((program) => program.id));
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
    fireEvent.click(screen.getByRole("button", { name: "Engineering · 2" }));
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
    fireEvent.click(screen.getByRole("button", { name: "Engineering · 3" }));
    expect(threeMemberExpansion).toHaveBeenCalledWith(["program-a", "program-b", "program-c"]);
  });

  it("offers each family its own expansion and adds only that family's members", () => {
    const onChange = jest.fn();
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-two-families"
        catalog={twoFamilyCatalog}
        selectedIds={[]}
        onChange={onChange}
      />,
    );

    // Both families are offered, each counting only its own unselected members.
    expect(screen.getByRole("button", { name: "Engineering · 2" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Education · 3" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Education · 3" }));

    // ⚠️ THE ASSERTION IS THE ABSENCE AS MUCH AS THE PRESENCE: a component that expanded every family
    // would pass a presence-only check while quietly selecting Engineering too.
    expect(onChange).toHaveBeenCalledWith(["program-elem", "program-sec", "program-ece"]);
  });

  it("keeps a mixed-family selection intact when another family is expanded", () => {
    const onChange = jest.fn();
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-mixed"
        catalog={twoFamilyCatalog}
        selectedIds={["program-a", "program-elem", "program-nursing"]}
        onChange={onChange}
      />,
    );

    // Engineering has one member left; Education has two. Both counts are family-scoped.
    expect(screen.getByRole("button", { name: "Engineering · 1 remaining" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Engineering · 1 remaining" }));

    // The Education pick and the family-less pick survive, and nothing is duplicated.
    expect(onChange).toHaveBeenCalledWith(["program-a", "program-elem", "program-nursing", "program-b"]);
  });

  it("deduplicates a program shared by two expanded families", () => {
    const onChange = jest.fn();
    const overlapping = [
      { id: "shared", name: "Computer Engineering", programFamilies: [
        { id: "family-engineering", name: "Engineering" },
        { id: "family-computing", name: "Computing & Technology" },
      ], programFamilyId: "family-computing", programFamilyName: "Computing & Technology", isActive: true },
      { id: "civil", name: "Civil Engineering", programFamilies: [{ id: "family-engineering", name: "Engineering" }], programFamilyId: "family-engineering", programFamilyName: "Engineering", isActive: true },
    ];
    const { rerender } = render(<ApplicableProgramsCombobox id="overlap" catalog={overlapping} selectedIds={[]} onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Computing & Technology · 1" }));
    expect(onChange).toHaveBeenLastCalledWith(["shared"]);
    rerender(<ApplicableProgramsCombobox id="overlap" catalog={overlapping} selectedIds={["shared"]} onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Engineering · 1 remaining" }));
    expect(onChange).toHaveBeenLastCalledWith(["shared", "civil"]);
  });

  it("keeps a full family visible and inert while leaving the other family actionable", () => {
    const onChange = jest.fn();
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-exhausted"
        catalog={twoFamilyCatalog}
        selectedIds={["program-elem", "program-sec", "program-ece"]}
        onChange={onChange}
      />,
    );

    const fullFamily = screen.getByLabelText("Education — all 3 programs added");
    expect(fullFamily).toHaveTextContent("✓ Education · 3");
    expect(fullFamily).not.toHaveAttribute("aria-pressed");
    fireEvent.click(fullFamily);
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Engineering · 2" })).toBeInTheDocument();
  });

  it("reverts a full family to its partial state after a member is removed", () => {
    const onChange = jest.fn();
    const { rerender } = render(
      <ApplicableProgramsCombobox
        id="applicable-programs-reopen-family"
        catalog={familyCatalog}
        selectedIds={["program-a", "program-b", "program-c"]}
        onChange={onChange}
      />,
    );

    expect(screen.getByLabelText("Engineering — all 3 programs added")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remove Mechanical Engineering" }));
    expect(onChange).toHaveBeenCalledWith(["program-a", "program-c"]);

    rerender(
      <ApplicableProgramsCombobox
        id="applicable-programs-reopen-family"
        catalog={familyCatalog}
        selectedIds={["program-a", "program-c"]}
        onChange={onChange}
      />,
    );
    expect(screen.getByRole("button", { name: "Engineering · 1 remaining" })).toBeInTheDocument();
  });

  it("excludes inactive programs from individual suggestions and family expansion", () => {
    const onChange = jest.fn();
    const catalogWithInactiveMember = [
      catalog[0],
      { ...catalog[1], isActive: false },
    ];
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-inactive"
        catalog={catalogWithInactiveMember}
        selectedIds={[]}
        onChange={onChange}
      />,
    );

    fireEvent.click(screen.getByLabelText("Toggle course program suggestions"));
    expect(screen.getByRole("option", { name: "Civil Engineering" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Mechanical Engineering" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Engineering · 1" }));
    expect(onChange).toHaveBeenCalledWith(["program-a"]);
  });

  it("keeps an already-selected inactive program visible and removable", () => {
    const onChange = jest.fn();
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-selected-inactive"
        catalog={[{ ...catalog[1], isActive: false }]}
        selectedIds={["program-b"]}
        onChange={onChange}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Remove Mechanical Engineering" }));
    expect(onChange).toHaveBeenCalledWith([]);
    expect(screen.queryByLabelText("Program family shortcuts")).not.toBeInTheDocument();
  });

  it("treats a stale catalog item without isActive as active", () => {
    const staleCatalog = [{
      id: "program-stale",
      name: "Pharmacy",
      programFamilyId: null,
      programFamilyName: null,
    }];
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-stale-cache"
        catalog={staleCatalog as unknown as typeof catalog}
        selectedIds={[]}
        onChange={jest.fn()}
      />,
    );

    fireEvent.click(screen.getByLabelText("Toggle course program suggestions"));
    expect(screen.getByRole("option", { name: "Pharmacy" })).toBeInTheDocument();
  });

  // v0.149.0: a mobile-only collapse (initially rendered 8 of N chips, behind a "Show all" toggle) was
  // built and shipped on an unverified "measured browser check" claim, then removed at audit -- read
  // through the actual layout, all four consumers either sit in normal page flow (scrolling to a Save
  // button below a tall chip row is ordinary, expected mobile behavior, not a bug) or inside `AppModal`,
  // whose own `flex-1 overflow-y-auto` content region plus `shrink-0` actions row already guarantees the
  // actions stay visible regardless of how much content renders above them. There is no viewport where
  // this component needs to hide a chip to keep Save reachable, so it renders every selected program
  // unconditionally, at any width. This test is the "no collapse" companion to the acceptance check
  // the plan itself asked for, not a screenshot -- it can't observe wrapping, but proves the component
  // itself imposes no artificial limit.
  it("renders every selected program regardless of viewport width, with no artificial limit", () => {
    const largeCatalog = Array.from({ length: 18 }, (_, index) => ({
      id: `engineering-${index}`,
      name: `Engineering Program ${index + 1}`,
      programFamilyId: "family-engineering",
      programFamilyName: "Engineering",
      isActive: true,
    }));

    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-mobile-overflow"
        catalog={largeCatalog}
        selectedIds={largeCatalog.map((program) => program.id)}
        onChange={jest.fn()}
      />,
    );

    expect(screen.getAllByRole("button", { name: /^Remove Engineering Program/ })).toHaveLength(18);
    expect(screen.queryByRole("button", { name: /^Show all/ })).not.toBeInTheDocument();
  });

  it("keeps programs without a family individually selectable and renders no family affordance", () => {
    const onChange = jest.fn();
    render(
      <ApplicableProgramsCombobox
        id="applicable-programs-no-families"
        catalog={[{ id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null, isActive: true }]}
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

  it("does not offer an inactive near match as a selection candidate", async () => {
    (findSimilarCoursePrograms as jest.Mock).mockResolvedValue([{ ...catalog[0], isActive: false }]);
    render(<ApplicableProgramsCombobox id="inactive-near-match" catalog={catalog} selectedIds={[]} onChange={jest.fn()} canCreateCatalogProgram />);

    fireEvent.focus(screen.getByLabelText("Add a course or program"));
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: "Civil Engineer" } });

    await waitFor(() => expect(findSimilarCoursePrograms).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: "Select Civil Engineering" })).not.toBeInTheDocument();
  });

  it("creates and selects a catalog program without losing existing selections", async () => {
    const onChange = jest.fn();
    const created = { id: "program-new", name: "Chemical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering", isActive: true };
    (createCourseProgram as jest.Mock).mockResolvedValue(created);
    render(<ApplicableProgramsCombobox id="create-program" catalog={catalog} selectedIds={["program-a"]} onChange={onChange} canCreateCatalogProgram />);

    fireEvent.focus(screen.getByLabelText("Add a course or program"));
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: created.name } });
    fireEvent.click(await screen.findByRole("button", { name: /Add “Chemical Engineering” to the catalog/ }));
    const familyPicker = screen.getByLabelText("Program Families (optional)") as HTMLSelectElement;
    await within(familyPicker).findByRole("option", { name: "Engineering" });
    familyPicker.options[0].selected = true;
    fireEvent.change(familyPicker);
    fireEvent.click(screen.getByRole("button", { name: "Add and select" }));

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(["program-a", "program-new"]));
    expect(createCourseProgram).toHaveBeenCalledWith({
      name: "Chemical Engineering",
      programFamilyIds: ["family-engineering"],
      examGoalSlug: null,
    });
  });

  it("loads an empty family lazily for creation without showing an expansion chip", async () => {
    render(<ApplicableProgramsCombobox id="empty-family" catalog={catalog} selectedIds={[]} onChange={jest.fn()} canCreateCatalogProgram />);
    expect(listProgramFamilies).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: /Health Sciences/ })).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: "Public Health" } });
    fireEvent.click(await screen.findByRole("button", { name: /Add “Public Health” to the catalog/ }));
    const picker = screen.getByLabelText("Program Families (optional)");
    expect(await within(picker).findByRole("option", { name: "Health Sciences" })).toBeInTheDocument();
    expect(listProgramFamilies).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("button", { name: /Health Sciences ·/ })).not.toBeInTheDocument();
  });

  it("selects only the new program when it is created in two populated families", async () => {
    const onChange = jest.fn();
    const created = {
      id: "program-new", name: "Architectural Engineering", isActive: true,
      programFamilies: [
        { id: "family-engineering", name: "Engineering" },
        { id: "family-built", name: "Built Environment & Design" },
      ],
      programFamilyId: "family-built", programFamilyName: "Built Environment & Design",
    };
    (listProgramFamilies as jest.Mock).mockResolvedValue([
      { id: "family-engineering", name: "Engineering" },
      { id: "family-built", name: "Built Environment & Design" },
    ]);
    (createCourseProgram as jest.Mock).mockResolvedValue(created);
    render(<ApplicableProgramsCombobox id="overlap-create" catalog={catalog} selectedIds={[]} onChange={onChange} canCreateCatalogProgram />);
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: created.name } });
    fireEvent.click(await screen.findByRole("button", { name: /Add “Architectural Engineering” to the catalog/ }));
    const picker = screen.getByLabelText("Program Families (optional)") as HTMLSelectElement;
    await within(picker).findByRole("option", { name: "Built Environment & Design" });
    Array.from(picker.options).forEach((option) => { option.selected = true; });
    fireEvent.change(picker);
    fireEvent.click(screen.getByRole("button", { name: "Add and select" }));
    await waitFor(() => expect(createCourseProgram).toHaveBeenCalledWith(expect.objectContaining({
      programFamilyIds: ["family-engineering", "family-built"],
    })));
    expect(onChange).toHaveBeenCalledWith(["program-new"]);
  });

  it("still creates with zero families when the family endpoint fails", async () => {
    (listProgramFamilies as jest.Mock).mockRejectedValue(new Error("Forbidden"));
    (createCourseProgram as jest.Mock).mockResolvedValue({ id: "program-new", name: "Public Health", programFamilies: [], programFamilyId: null, programFamilyName: null, isActive: true });
    render(<ApplicableProgramsCombobox id="failed-families" catalog={catalog} selectedIds={[]} onChange={jest.fn()} canCreateCatalogProgram />);
    fireEvent.change(screen.getByLabelText("Add a course or program"), { target: { value: "Public Health" } });
    fireEvent.click(await screen.findByRole("button", { name: /Add “Public Health” to the catalog/ }));
    expect(await screen.findByText("Program Families could not be loaded.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add and select" }));
    await waitFor(() => expect(createCourseProgram).toHaveBeenCalledWith(expect.objectContaining({ programFamilyIds: [] })));
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
