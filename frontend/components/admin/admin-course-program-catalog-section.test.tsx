import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AdminCourseProgramCatalogSection } from "./admin-course-program-catalog-section";
import { createCourseProgram, findSimilarCoursePrograms, getCourseProgramCatalog } from "@/lib/api";

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
  getCourseProgramCatalog: jest.fn(),
}));

const civil = { id: "program-a", name: "Civil Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" };

describe("AdminCourseProgramCatalogSection", () => {
  beforeEach(() => {
    (getCourseProgramCatalog as jest.Mock).mockReset();
    (createCourseProgram as jest.Mock).mockReset();
    (findSimilarCoursePrograms as jest.Mock).mockReset();
    (findSimilarCoursePrograms as jest.Mock).mockResolvedValue([]);
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([civil]);
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
