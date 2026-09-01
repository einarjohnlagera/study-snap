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
        courseProgram: "Civil Engineering",
        domainContext: "ENGINEERING_SCIENCES",
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

  it("renders only note metadata columns because every listed note belongs to the viewer", async () => {
    render(<AdminApplicableProgramsSection />);

    expect(await screen.findByText("Engineering Algebra")).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Owner" })).not.toBeInTheDocument();
    expect(screen.queryByText("teacher@example.com")).not.toBeInTheDocument();
    expect(screen.getByText(/your canonical notes/)).toBeInTheDocument();
  });

  it("shows an owned-note empty state when the requesting admin has no notes", async () => {
    (getAdminNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      items: [],
      page: 0,
      size: 25,
      totalElements: 0,
    });

    render(<AdminApplicableProgramsSection />);

    expect(await screen.findByText("No owned notes found.")).toBeInTheDocument();
  });

  it("reconciles the full selected set and shows the persisted result", async () => {
    (replaceNoteApplicablePrograms as jest.Mock).mockResolvedValue([
      { id: "program-a", name: "Civil Engineering" },
      { id: "program-c", name: "Electrical Engineering" },
    ]);
    render(<AdminApplicableProgramsSection />);

    await screen.findByText("Engineering Algebra");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    expect(screen.getByText(/Applicable Programs determine where this note applies and is discoverable/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add all 2 Engineering programs" }));
    fireEvent.click(screen.getByRole("button", { name: "Remove Mechanical Engineering" }));
    fireEvent.click(screen.getByRole("button", { name: "Save Applicable Programs" }));

    await waitFor(() => expect(replaceNoteApplicablePrograms).toHaveBeenCalledWith(
      "note-1",
      ["program-a", "program-c"],
    ));
    expect(await screen.findByText("Civil Engineering, Electrical Engineering")).toBeInTheDocument();
  });

  // C7. This previously asserted the opposite -- that a failed save reset the selection to what was
  // persisted. That is the defect, not the contract: a curator who family-expanded to several programs
  // lost every pick and had to re-choose them all before they could even retry. Keep the work.
  it("keeps the modal open and preserves the attempted selection after a failed PUT", async () => {
    (replaceNoteApplicablePrograms as jest.Mock).mockRejectedValue(new Error("Save failed"));
    render(<AdminApplicableProgramsSection />);

    await screen.findByText("Engineering Algebra");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.click(screen.getByRole("button", { name: "Add all 2 Engineering programs" }));
    fireEvent.click(screen.getByRole("button", { name: "Save Applicable Programs" }));

    expect(await screen.findAllByText("Save failed")).not.toHaveLength(0);
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Civil Engineering" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Mechanical Engineering" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Electrical Engineering" })).toBeInTheDocument();
  });

  it("saves multiple Applicable Programs without Domain Context for later generation readiness", async () => {
    (getAdminNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      items: [{
        noteId: "note-1",
        title: "Engineering Algebra",
        courseProgram: "Civil Engineering",
        domainContext: null,
        applicablePrograms: [{ id: "program-a", name: "Civil Engineering" }],
      }],
      page: 0,
      size: 25,
      totalElements: 1,
    });
    (replaceNoteApplicablePrograms as jest.Mock).mockResolvedValue([
      { id: "program-a", name: "Civil Engineering" },
      { id: "program-b", name: "Mechanical Engineering" },
      { id: "program-c", name: "Electrical Engineering" },
    ]);
    render(<AdminApplicableProgramsSection />);

    await screen.findByText("Engineering Algebra");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.click(screen.getByRole("button", { name: "Add all 2 Engineering programs" }));
    fireEvent.click(screen.getByRole("button", { name: "Save Applicable Programs" }));

    await waitFor(() => expect(replaceNoteApplicablePrograms).toHaveBeenCalledWith(
      "note-1",
      ["program-a", "program-b", "program-c"],
    ));
    expect(await screen.findByText("Civil Engineering, Mechanical Engineering, Electrical Engineering"))
      .toBeInTheDocument();
  });
});
