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

  it("saves a rename and full family-side membership set in one call", async () => {
    (updateProgramFamily as jest.Mock).mockResolvedValue({ id: "family-engineering", name: "Engineering & Technology" });
    render(<AdminProgramFamiliesSection />);
    const row = await screen.findByRole("row", { name: /Engineering 1 Edit/ });
    fireEvent.click(within(row).getByRole("button", { name: "Edit" }));
    const dialog = screen.getByRole("dialog", { name: "Edit Program Family" });
    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: "Engineering & Technology" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(updateProgramFamily).toHaveBeenCalledWith("family-engineering", {
      name: "Engineering & Technology", programIds: ["inactive-program"],
    }));
  });
});
