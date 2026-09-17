import { fireEvent, render, screen } from "@testing-library/react";
import AdminCourseProgramsPage from "./page";

const routerMock = {
  replace: jest.fn(),
  push: jest.fn(),
};
let currentSearch = "";

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/admin/course-programs",
  useSearchParams: () => new URLSearchParams(currentSearch),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAdminUser: jest.fn(),
}));

jest.mock("@/components/admin/admin-course-program-catalog-section", () => ({
  AdminCourseProgramCatalogSection: () => <div>Course Program Catalog Section</div>,
}));

jest.mock("@/components/admin/admin-program-families-section", () => ({
  AdminProgramFamiliesSection: () => <div>Program Families Section</div>,
}));

jest.mock("@/components/admin/admin-applicable-programs-section", () => ({
  AdminApplicableProgramsSection: () => <div>Note Applicable Programs Section</div>,
}));

const { requireAdminUser } = jest.requireMock("@/lib/route-guards") as {
  requireAdminUser: jest.Mock;
};

describe("AdminCourseProgramsPage", () => {
  beforeEach(() => {
    routerMock.replace.mockReset();
    routerMock.push.mockReset();
    currentSearch = "";
    requireAdminUser.mockReset();
  });

  it("loads the Program Families view by default, with a route back to Admin", () => {
    requireAdminUser.mockReturnValue(true);

    render(<AdminCourseProgramsPage />);

    expect(screen.getByRole("heading", { name: "Course / Program Catalog" })).toBeInTheDocument();
    expect(screen.getByText("Program Families Section")).toBeInTheDocument();
    expect(screen.queryByText("Course Program Catalog Section")).not.toBeInTheDocument();
    expect(screen.getByText("Note Applicable Programs Section")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "← Admin" })).toHaveAttribute("href", "/admin");
  });

  it("round-trips the programs view through the query parameter", () => {
    currentSearch = "view=programs";
    render(<AdminCourseProgramsPage />);

    expect(screen.getByRole("tab", { name: "Course / Programs" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Course Program Catalog Section")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Program Families" }));
    expect(routerMock.push).toHaveBeenCalledWith("/admin/course-programs?view=families");
  });

  it("runs the admin guard on mount, which is what redirects a non-admin away", () => {
    // Redirect-only, matching the other admin sub-pages: the guard navigates rather than gating the
    // render. Both sections read ADMIN-only endpoints, so a brief render leaks no data.
    requireAdminUser.mockReturnValue(false);

    render(<AdminCourseProgramsPage />);

    expect(requireAdminUser).toHaveBeenCalledWith(routerMock);
  });
});
