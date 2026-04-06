import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AppShell } from "./app-shell";
import { getMe, getMyPlan, logout } from "@/lib/api";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
  refresh: jest.fn(),
};

let currentPathname = "/dashboard";
let currentAuthUser: Record<string, unknown> | null = null;
const sendFeedbackWidgetMock = jest.fn(() => null);

jest.mock("next/navigation", () => ({
  usePathname: () => currentPathname,
  useRouter: () => routerMock,
}));

jest.mock("next/image", () => ({
  __esModule: true,
  default: ({ alt }: { alt: string }) => <img alt={alt} />,
}));

jest.mock("next/link", () => ({
  __esModule: true,
  default: ({
    href,
    children,
    onClick,
    className,
  }: {
    href: string;
    children: ReactNode;
    onClick?: () => void;
    className?: string;
  }) => (
    <a href={href} onClick={onClick} className={className}>
      {children}
    </a>
  ),
}));

jest.mock("@/components/theme-toggle", () => ({
  ThemeToggle: () => <div>Theme Toggle</div>,
}));

jest.mock("@/components/feedback/send-feedback-widget", () => ({
  SendFeedbackWidget: (props: unknown) => sendFeedbackWidgetMock(props),
}));

jest.mock("@/components/navbar", () => ({
  Navbar: () => <div>Public Navbar</div>,
}));

jest.mock("@/components/ui/button", () => ({
  Button: ({
    children,
    onClick,
    disabled,
    type = "button",
  }: {
    children: ReactNode;
    onClick?: () => void;
    disabled?: boolean;
    type?: "button" | "submit" | "reset";
  }) => (
    <button type={type} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  ),
}));

jest.mock("@/lib/auth", () => ({
  buildLoginPath: jest.fn(() => "/login?reason=logged_out"),
  getAuthUser: jest.fn(() => currentAuthUser),
  needsOnboarding: jest.fn(() => false),
  resolveAuthenticatedHome: jest.fn((authUser: { emailVerifiedAt?: string | null } | null) =>
    authUser?.emailVerifiedAt ? "/dashboard" : "/verify-email",
  ),
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error {
    code: string | null;
    status: number;

    constructor(message: string, options: { code?: string | null; status: number }) {
      super(message);
      this.code = options.code ?? null;
      this.status = options.status;
    }
  },
  getMe: jest.fn(),
  getMyPlan: jest.fn(),
  logout: jest.fn(),
  requestEmailVerification: jest.fn(),
}));

const meResponse = {
  id: "user-1",
  displayName: "Note",
  firstName: "Note",
  email: "[email protected]",
  emailVerifiedAt: "2026-03-31T00:00:00Z",
  onboardingCompletedAt: "2026-03-31T00:05:00Z",
  role: "USER",
  profileType: "STUDENT",
  productOnboardingCompletedAt: null,
};

describe("AppShell", () => {
  beforeEach(() => {
    currentPathname = "/dashboard";
    window.history.replaceState({}, "", "/dashboard");
    currentAuthUser = {
      id: "user-1",
      email: "[email protected]",
      displayName: "Note",
      emailVerifiedAt: "2026-03-31T00:00:00Z",
      onboardingCompletedAt: "2026-03-31T00:05:00Z",
      role: "USER",
    };
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    routerMock.refresh.mockReset();
    sendFeedbackWidgetMock.mockClear();
    (getMe as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (logout as jest.Mock).mockReset();
    (getMe as jest.Mock).mockResolvedValue(meResponse);
    (getMyPlan as jest.Mock).mockResolvedValue(null);
    (logout as jest.Mock).mockImplementation(async () => {
      currentAuthUser = null;
      window.dispatchEvent(new Event("studysnap-auth-change"));
    });
  });

  it("does not render the authenticated shell on auth routes and redirects to dashboard", async () => {
    currentPathname = "/login";

    render(
      <AppShell>
        <div>Auth page content</div>
      </AppShell>,
    );

    expect(screen.getByText("Public Navbar")).toBeInTheDocument();
    expect(screen.queryByLabelText("Open user menu")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
  });

  it("returns authenticated users to the auth flow cleanly on logout", async () => {
    render(
      <AppShell>
        <div>Dashboard content</div>
      </AppShell>,
    );

    expect(await screen.findByAltText("NoteLib")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Library" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "My Library" })).not.toBeInTheDocument();

    fireEvent.click(await screen.findByLabelText("Open user menu"));
    fireEvent.click(screen.getByRole("button", { name: "Sign Out" }));

    await waitFor(() => {
      expect(logout).toHaveBeenCalled();
      expect(routerMock.replace).toHaveBeenCalledWith("/login?reason=logged_out");
    });
  });

  it("shows View Public Profile and Account Settings in the avatar dropdown", async () => {
    render(
      <AppShell>
        <div>Dashboard content</div>
      </AppShell>,
    );

    fireEvent.click(await screen.findByLabelText("Open user menu"));

    const viewPublicProfile = screen.getByRole("link", { name: "View Public Profile" });
    expect(viewPublicProfile).toBeInTheDocument();
    expect(viewPublicProfile).toHaveAttribute("href", "/public/profile/user-1");

    const accountSettings = screen.getByRole("link", { name: "Account Settings" });
    expect(accountSettings).toBeInTheDocument();
    expect(accountSettings).toHaveAttribute("href", "/profile");
  });

  it("hides the mobile feedback widget on note editor routes", async () => {
    currentPathname = "/notes/new";

    render(
      <AppShell>
        <div>New note content</div>
      </AppShell>,
    );

    await waitFor(() => {
      expect(sendFeedbackWidgetMock).toHaveBeenCalledWith(expect.objectContaining({ mobileHidden: true }));
    });
  });

  it("shows Edit Note as the page title on note edit routes", async () => {
    currentPathname = "/notes/note-1/edit";
    window.history.replaceState({}, "", currentPathname);

    render(
      <AppShell>
        <div>Edit note content</div>
      </AppShell>,
    );

    expect(await screen.findByRole("heading", { name: "Edit Note" })).toBeInTheDocument();
  });
});
