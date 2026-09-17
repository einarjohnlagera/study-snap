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
      "program-a", { programFamilyIds: [] }));
  });
});
