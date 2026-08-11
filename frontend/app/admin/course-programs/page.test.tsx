import { render, screen } from "@testing-library/react";
import AdminCourseProgramsPage from "./page";

const routerMock = {
  replace: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAdminUser: jest.fn(),
}));

jest.mock("@/components/admin/admin-course-program-catalog-section", () => ({
  AdminCourseProgramCatalogSection: () => <div>Course Program Catalog Section</div>,
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
    requireAdminUser.mockReset();
  });

  it("renders both curation sections for an admin, with a route back to Admin", () => {
    requireAdminUser.mockReturnValue(true);

    render(<AdminCourseProgramsPage />);

    expect(screen.getByRole("heading", { name: "Course / Programs" })).toBeInTheDocument();
    expect(screen.getByText("Course Program Catalog Section")).toBeInTheDocument();
    expect(screen.getByText("Note Applicable Programs Section")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "← Admin" })).toHaveAttribute("href", "/admin");
  });

  it("runs the admin guard on mount, which is what redirects a non-admin away", () => {
    // Redirect-only, matching the other admin sub-pages: the guard navigates rather than gating the
    // render. Both sections read ADMIN-only endpoints, so a brief render leaks no data.
    requireAdminUser.mockReturnValue(false);

    render(<AdminCourseProgramsPage />);

    expect(requireAdminUser).toHaveBeenCalledWith(routerMock);
  });
});
