import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { AdminProgramFamiliesSection } from "./admin-program-families-section";
import { createProgramFamily, getCourseProgramCatalog, listProgramFamilies, updateProgramFamily } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error { code = null; },
  createProgramFamily: jest.fn(), getCourseProgramCatalog: jest.fn(),
  listProgramFamilies: jest.fn(), updateProgramFamily: jest.fn(),
}));

describe("AdminProgramFamiliesSection", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (listProgramFamilies as jest.Mock).mockResolvedValue([
      { id: "family-empty", name: "Accounting" },
      { id: "family-engineering", name: "Engineering" },
    ]);
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([{
      id: "inactive-program", name: "Retired Engineering Program", isActive: false,
      programFamilies: [{ id: "family-engineering", name: "Engineering" }],
      programFamilyId: "family-engineering", programFamilyName: "Engineering",
    }]);
  });

  it("counts inactive members and keeps zero-member families editable", async () => {
    render(<AdminProgramFamiliesSection />);
    const accounting = await screen.findByRole("row", { name: /Accounting 0 Edit/ });
    const engineering = screen.getByRole("row", { name: /Engineering 1 Edit/ });
    expect(accounting).toBeInTheDocument();
    expect(engineering).toBeInTheDocument();
    expect(within(accounting).getByRole("button", { name: "Edit" })).toBeEnabled();
  });

  it("creates an empty family without blocking on an empty selection", async () => {
    (createProgramFamily as jest.Mock).mockResolvedValue({ id: "family-health", name: "Health Sciences" });
    render(<AdminProgramFamiliesSection />);
    expect((await screen.findAllByText("Accounting")).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("button", { name: "+ New family" }));
    const dialog = screen.getByRole("dialog", { name: "New Program Family" });
    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: "Health Sciences" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Create family" }));
    await waitFor(() => expect(createProgramFamily).toHaveBeenCalledWith("Health Sciences", []));
  });

  it("saves a rename-only edit without touching family membership", async () => {
    // ⚠️ THE RENAME-ONLY PATH MUST OMIT programIds, NOT RE-SEND THE PRE-POPULATED SET. A cold-agent
    // falsification pass found that always sending the full array turns an ordinary rename into a
    // lost-update hazard: if another admin added a program to this family between this modal opening
    // and Save, the stale snapshot silently overwrites that addition. `membershipDirty` already exists
    // to distinguish "the picker was never touched" from "the picker was edited" -- this asserts the
    // save path actually honors it for an untouched picker.
    (updateProgramFamily as jest.Mock).mockResolvedValue({ id: "family-engineering", name: "Engineering & Technology" });
    render(<AdminProgramFamiliesSection />);
    const row = await screen.findByRole("row", { name: /Engineering 1 Edit/ });
    fireEvent.click(within(row).getByRole("button", { name: "Edit" }));
    const dialog = screen.getByRole("dialog", { name: "Edit Program Family" });
    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: "Engineering & Technology" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(updateProgramFamily).toHaveBeenCalledWith("family-engineering", {
      name: "Engineering & Technology", programIds: null,
    }));
  });

  it("saves the full family-side membership set once the picker is actually edited", async () => {
    (updateProgramFamily as jest.Mock).mockResolvedValue({ id: "family-engineering", name: "Engineering" });
    render(<AdminProgramFamiliesSection />);
    const row = await screen.findByRole("row", { name: /Engineering 1 Edit/ });
    fireEvent.click(within(row).getByRole("button", { name: "Edit" }));
    const dialog = screen.getByRole("dialog", { name: "Edit Program Family" });
    fireEvent.click(within(dialog).getByRole("checkbox", { name: "Retired Engineering Program" }));
    fireEvent.click(within(dialog).getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(updateProgramFamily).toHaveBeenCalledWith("family-engineering", {
      name: "Engineering", programIds: [],
    }));
  });
});
