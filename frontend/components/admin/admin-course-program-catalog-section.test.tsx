import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { AdminCourseProgramCatalogSection } from "./admin-course-program-catalog-section";
import { createCourseProgram, findSimilarCoursePrograms, getCourseProgramCatalog,
  listProgramFamilies, updateCourseProgram } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error { code = null; details = null; },
  createCourseProgram: jest.fn(), findSimilarCoursePrograms: jest.fn(),
  getCourseProgramCatalog: jest.fn(), listProgramFamilies: jest.fn(), updateCourseProgram: jest.fn(),
}));

const civil = { id: "program-a", name: "Civil Engineering",
  programFamilies: [{ id: "family-engineering", name: "Engineering" }],
  programFamilyId: "family-engineering", programFamilyName: "Engineering", isActive: true };

describe("AdminCourseProgramCatalogSection", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([civil]);
    (listProgramFamilies as jest.Mock).mockResolvedValue([
      { id: "family-engineering", name: "Engineering" },
      { id: "family-health", name: "Health Sciences" },
    ]);
    (findSimilarCoursePrograms as jest.Mock).mockResolvedValue([]);
  });

  it("renders existing family chips under the secondary catalog view", async () => {
    render(<AdminCourseProgramCatalogSection />);
    expect((await screen.findAllByText("Civil Engineering")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Engineering").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "+ New program" })).toBeInTheDocument();
  });

  it("keeps the catalog usable when the family list fails", async () => {
    (listProgramFamilies as jest.Mock).mockRejectedValue(new Error("Forbidden"));
    render(<AdminCourseProgramCatalogSection />);
    expect((await screen.findAllByText("Civil Engineering")).length).toBeGreaterThan(0);
    expect(screen.getByText(/Program Families could not be loaded/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "+ New program" })).toBeEnabled();
  });

  it("creates a program with multiple families through the shared modal", async () => {
    const created = { ...civil, id: "program-new", name: "Computer Engineering",
      programFamilies: [
        { id: "family-engineering", name: "Engineering" },
        { id: "family-health", name: "Health Sciences" },
      ] };
    (createCourseProgram as jest.Mock).mockResolvedValue(created);
    render(<AdminCourseProgramCatalogSection />);
    await screen.findAllByText("Civil Engineering");
    fireEvent.click(screen.getByRole("button", { name: "+ New program" }));
    const dialog = screen.getByRole("dialog", { name: "New Course / Program" });
    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: created.name } });
    fireEvent.click(within(dialog).getByRole("checkbox", { name: "Engineering" }));
    fireEvent.click(within(dialog).getByRole("checkbox", { name: "Health Sciences" }));
    fireEvent.click(within(dialog).getByRole("button", { name: "Create program" }));
    await waitFor(() => expect(createCourseProgram).toHaveBeenCalledWith(expect.objectContaining({
      name: "Computer Engineering",
      programFamilyIds: ["family-engineering", "family-health"],
    })));
    expect(await screen.findAllByText("Computer Engineering")).not.toHaveLength(0);
  });

  it("clears memberships with one full-set PATCH", async () => {
    (updateCourseProgram as jest.Mock).mockResolvedValue({ ...civil,
      programFamilies: [], programFamilyId: null, programFamilyName: null });
    render(<AdminCourseProgramCatalogSection />);
    await screen.findAllByText("Civil Engineering");
    fireEvent.click(screen.getAllByRole("button", { name: "Edit" })[0]);
    const dialog = screen.getByRole("dialog", { name: "Edit Program Families" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Remove Engineering" }));
    fireEvent.click(within(dialog).getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(updateCourseProgram).toHaveBeenCalledWith(
      "program-a", { programFamilyIds: [], isActive: true }));
  });

  it("initializes the Active toggle from the program and saves its changed value", async () => {
    (updateCourseProgram as jest.Mock).mockResolvedValue({ ...civil, isActive: false });
    render(<AdminCourseProgramCatalogSection />);
    await screen.findAllByText("Civil Engineering");
    fireEvent.click(screen.getAllByRole("button", { name: "Edit" })[0]);
    const dialog = screen.getByRole("dialog", { name: "Edit Program Families" });
    const active = within(dialog).getByRole("checkbox", { name: "Active" });
    expect(active).toHaveAttribute("aria-checked", "true");

    fireEvent.click(active);
    expect(active).toHaveAttribute("aria-checked", "false");
    fireEvent.click(within(dialog).getByRole("button", { name: "Save changes" }));

    // ⚠️ Regression guard for the v0.152.0-class lost-update defect: an Active-only save must NOT
    // re-send programFamilyIds — the picker was never touched, so a concurrent admin's membership
    // edit made between load and save must survive this PATCH.
    await waitFor(() => expect(updateCourseProgram).toHaveBeenCalledWith("program-a", {
      isActive: false,
    }));
  });

  it("does not overwrite a concurrent admin's membership edit on an Active-only save", async () => {
    // Loaded snapshot says Engineering; a concurrent admin has since moved this program to Health
    // Sciences server-side. Toggling only Active must not carry the stale snapshot back over that edit.
    (updateCourseProgram as jest.Mock).mockResolvedValue({ ...civil, isActive: false,
      programFamilies: [{ id: "family-health", name: "Health Sciences" }] });
    render(<AdminCourseProgramCatalogSection />);
    await screen.findAllByText("Civil Engineering");
    fireEvent.click(screen.getAllByRole("button", { name: "Edit" })[0]);
    const dialog = screen.getByRole("dialog", { name: "Edit Program Families" });
    fireEvent.click(within(dialog).getByRole("checkbox", { name: "Active" }));
    fireEvent.click(within(dialog).getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(updateCourseProgram).toHaveBeenCalledTimes(1));
    const [, request] = (updateCourseProgram as jest.Mock).mock.calls[0];
    expect(request).not.toHaveProperty("programFamilyIds");
  });

  it("marks an inactive program in both desktop and mobile catalog layouts", async () => {
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([{ ...civil, isActive: false }]);
    render(<AdminCourseProgramCatalogSection />);

    expect(await screen.findAllByText("Inactive")).toHaveLength(2);
    fireEvent.click(screen.getAllByRole("button", { name: "Edit" })[0]);
    expect(within(screen.getByRole("dialog", { name: "Edit Program Families" }))
      .getByRole("checkbox", { name: "Active" })).toHaveAttribute("aria-checked", "false");
  });
});
