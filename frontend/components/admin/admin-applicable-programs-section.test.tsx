import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AdminApplicableProgramsSection } from "./admin-applicable-programs-section";
import {
  getAdminNoteApplicablePrograms,
  getCourseProgramCatalog,
  replaceNoteApplicablePrograms,
} from "@/lib/api";

jest.mock("@/lib/api", () => ({
  getAdminNoteApplicablePrograms: jest.fn(),
  getCourseProgramCatalog: jest.fn(),
  replaceNoteApplicablePrograms: jest.fn(),
}));

const catalog = [
  { id: "program-a", name: "Civil Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
  { id: "program-b", name: "Mechanical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
  { id: "program-c", name: "Electrical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
];

describe("AdminApplicableProgramsSection", () => {
  beforeEach(() => {
    (getAdminNoteApplicablePrograms as jest.Mock).mockReset();
    (getCourseProgramCatalog as jest.Mock).mockReset();
    (replaceNoteApplicablePrograms as jest.Mock).mockReset();
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue(catalog);
    (getAdminNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      items: [{
        noteId: "note-1",
        title: "Engineering Algebra",
        ownerEmail: "teacher@example.com",
        courseProgram: "Civil Engineering",
        applicablePrograms: [{ id: "program-a", name: "Civil Engineering" }],
      }],
      page: 0,
      size: 25,
      totalElements: 26,
    });
  });

  it("loads a capped page and requests the next page at the boundary", async () => {
    render(<AdminApplicableProgramsSection />);

    expect(await screen.findByText("Engineering Algebra")).toBeInTheDocument();
    expect(getAdminNoteApplicablePrograms).toHaveBeenCalledWith(0, 25);
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(getAdminNoteApplicablePrograms).toHaveBeenCalledWith(1, 25));
  });

  it("reconciles the full selected set and shows the persisted result", async () => {
    (replaceNoteApplicablePrograms as jest.Mock).mockResolvedValue([
      { id: "program-a", name: "Civil Engineering" },
      { id: "program-c", name: "Electrical Engineering" },
    ]);
    render(<AdminApplicableProgramsSection />);

    await screen.findByText("Engineering Algebra");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.click(screen.getByRole("button", { name: "Add all 2 Engineering programs" }));
    fireEvent.click(screen.getByRole("button", { name: "Remove Mechanical Engineering" }));
    fireEvent.click(screen.getByRole("button", { name: "Save Applicable Programs" }));

    await waitFor(() => expect(replaceNoteApplicablePrograms).toHaveBeenCalledWith(
      "note-1",
      ["program-a", "program-c"],
    ));
    expect(await screen.findByText("Civil Engineering, Electrical Engineering")).toBeInTheDocument();
  });

  it("keeps the modal open and restores the saved selection after a failed PUT", async () => {
    (replaceNoteApplicablePrograms as jest.Mock).mockRejectedValue(new Error("Save failed"));
    render(<AdminApplicableProgramsSection />);

    await screen.findByText("Engineering Algebra");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.click(screen.getByRole("button", { name: "Add all 2 Engineering programs" }));
    fireEvent.click(screen.getByRole("button", { name: "Save Applicable Programs" }));

    expect(await screen.findAllByText("Save failed")).not.toHaveLength(0);
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Civil Engineering" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove Mechanical Engineering" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove Electrical Engineering" })).not.toBeInTheDocument();
  });
});
