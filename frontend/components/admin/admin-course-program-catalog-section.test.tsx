import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { AdminCourseProgramCatalogSection } from "./admin-course-program-catalog-section";
import { createCourseProgram, createProgramFamily, findSimilarCoursePrograms, getCourseProgramCatalog, listProgramFamilies } from "@/lib/api";

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
  createProgramFamily: jest.fn(),
  findSimilarCoursePrograms: jest.fn(),
  getCourseProgramCatalog: jest.fn(),
  listProgramFamilies: jest.fn(),
}));

const civil = { id: "program-a", name: "Civil Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" };

describe("AdminCourseProgramCatalogSection", () => {
  beforeEach(() => {
    (getCourseProgramCatalog as jest.Mock).mockReset();
    (createCourseProgram as jest.Mock).mockReset();
    (findSimilarCoursePrograms as jest.Mock).mockReset();
    (findSimilarCoursePrograms as jest.Mock).mockResolvedValue([]);
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([civil]);
    (listProgramFamilies as jest.Mock).mockReset();
    (listProgramFamilies as jest.Mock).mockResolvedValue([{ id: "family-engineering", name: "Engineering" }]);
    (createProgramFamily as jest.Mock).mockReset();
  });

  /**
   * ⚠️ THE FAMILY OPTIONS COME FROM THE FAMILIES ENDPOINT, NOT FROM THE CATALOG.
   *
   * The fixture is the discriminating one: the catalog contains ONLY an Engineering program, while the
   * families endpoint also returns an EMPTY family. A component that derived its options from the
   * catalog — which is what it used to do, and what the authoring combobox still correctly does —
   * would silently omit the empty one, which is precisely the family a curator just created and is
   * about to assign a program to.
   */
  it("offers a family that has no members yet", async () => {
    (listProgramFamilies as jest.Mock).mockResolvedValue([
      { id: "family-engineering", name: "Engineering" },
      { id: "family-empty", name: "Health Sciences" },
    ]);
    render(<AdminCourseProgramCatalogSection />);
    await screen.findByText("Civil Engineering");

    const picker = screen.getByLabelText("Program Family (optional)");
    expect(within(picker).getByRole("option", { name: "Health Sciences" })).toBeInTheDocument();
  });

  it("creates a family, selects it, and keeps it available for the next program", async () => {
    (createProgramFamily as jest.Mock).mockResolvedValue({ id: "family-health", name: "Health Sciences" });
    render(<AdminCourseProgramCatalogSection />);
    await screen.findByText("Civil Engineering");

    fireEvent.change(screen.getByLabelText("New Program Family"), { target: { value: "  Health Sciences  " } });
    fireEvent.click(screen.getByRole("button", { name: "Add family" }));

    await waitFor(() => expect(createProgramFamily).toHaveBeenCalledWith("Health Sciences"));

    // It joins the picker AND becomes the current selection, because a curator creates a family in
    // order to put the program they are adding into it.
    const picker = screen.getByLabelText("Program Family (optional)") as HTMLSelectElement;
    await waitFor(() => expect(picker.value).toBe("family-health"));
    expect(within(picker).getByRole("option", { name: "Health Sciences" })).toBeInTheDocument();
    // ⚠️ No refetch: the list is updated locally, matching how program creation already behaves.
    expect(listProgramFamilies).toHaveBeenCalledTimes(1);
  });

  it("shows a visible error when creating a family fails, and keeps the typed name", async () => {
    (createProgramFamily as jest.Mock).mockRejectedValue(new Error("A Program Family named \"Engineering\" already exists."));
    render(<AdminCourseProgramCatalogSection />);
    await screen.findByText("Civil Engineering");

    const input = screen.getByLabelText("New Program Family") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "Engineering" } });
    fireEvent.click(screen.getByRole("button", { name: "Add family" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("already exists");
    // The curator must not lose what they typed in order to correct it.
    expect(input.value).toBe("Engineering");
  });

  it("creates a program and adds it to the list without refetching", async () => {
    const created = { id: "program-new", name: "Chemical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" };
    (createCourseProgram as jest.Mock).mockResolvedValue(created);
    render(<AdminCourseProgramCatalogSection />);
    await screen.findByText("Civil Engineering");

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: created.name } });
    fireEvent.change(screen.getByLabelText("Program Family (optional)"), { target: { value: "family-engineering" } });
    fireEvent.click(screen.getByRole("button", { name: "Add to catalog" }));

    expect(await screen.findByText("Chemical Engineering")).toBeInTheDocument();
    expect(getCourseProgramCatalog).toHaveBeenCalledTimes(1);
  });

  it("renders a duplicate as an existing-program path", async () => {
    const { ApiRequestError } = jest.requireMock("@/lib/api") as typeof import("@/lib/api");
    (createCourseProgram as jest.Mock).mockRejectedValue(new ApiRequestError(
      "A Course / Program named \"Civil Engineering\" already exists.",
      { code: "COURSE_PROGRAM_CATALOG_NAME_CONFLICT", details: "Civil Engineering", status: 409 },
    ));
    render(<AdminCourseProgramCatalogSection />);
    await screen.findByText("Civil Engineering");
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: " civil engineering " } });
    fireEvent.click(screen.getByRole("button", { name: "Add to catalog" }));

    await waitFor(() => expect(screen.getByText(/Use the existing catalog program/)).toBeInTheDocument());
  });

  it("shows near matches while preserving the proposed name", async () => {
    (findSimilarCoursePrograms as jest.Mock).mockResolvedValue([civil]);
    render(<AdminCourseProgramCatalogSection />);
    await screen.findByText("Civil Engineering");

    const nameInput = screen.getByLabelText("Name");
    fireEvent.change(nameInput, { target: { value: "Civil Engineer" } });

    expect(await screen.findByText("Similar catalog programs already exist:")).toBeInTheDocument();
    expect(nameInput).toHaveValue("Civil Engineer");
  });
});
