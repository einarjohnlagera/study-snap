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
        // Multi-program saves require a Domain Context, so the default fixture carries one -- without it
        // every multi-select case below would be an illegal save that the backend rejects.
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

  // C7. The endpoint validates the new program set against the note's already-persisted domainContext,
  // and this screen has no Domain Context control -- so without a client-side check the curator meets a
  // raw 400 naming a field the screen does not show, with no route to fixing it.
  it("blocks a multi-program save on a note with no Domain Context and says where to set it", async () => {
    (getAdminNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      items: [{
        noteId: "note-1",
        title: "Engineering Algebra",
        ownerEmail: "teacher@example.com",
        courseProgram: "Civil Engineering",
        domainContext: null,
        applicablePrograms: [{ id: "program-a", name: "Civil Engineering" }],
      }],
      page: 0,
      size: 25,
      totalElements: 1,
    });
    render(<AdminApplicableProgramsSection />);

    await screen.findByText("Engineering Algebra");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.click(screen.getByRole("button", { name: "Add all 2 Engineering programs" }));
    fireEvent.click(screen.getByRole("button", { name: "Save Applicable Programs" }));

    // Rendered twice by design: a toast and the inline failure line under the picker.
    expect(await screen.findAllByText(/needs a Domain Context/)).not.toHaveLength(0);
    expect(screen.getAllByText(/open the note and set Domain Context/)).not.toHaveLength(0);
    expect(replaceNoteApplicablePrograms).not.toHaveBeenCalled();
    // The selection survives, so the curator can set the domain and come back to it.
    expect(screen.getByRole("button", { name: "Remove Electrical Engineering" })).toBeInTheDocument();
  });
});
