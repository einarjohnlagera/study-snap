import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AppShell } from "./app-shell";
import { ExamFocusProvider, useBottomViewportClaim, useExamFocusMode } from "./exam-mode/exam-focus-context";
import { getMe, getMyPlan, logout } from "@/lib/api";
import { needsOnboarding } from "@/lib/auth";
import {
  clearPendingLightweightProfileCompletion,
  setPendingLightweightProfileCompletion,
} from "@/lib/onboarding-v2";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
  refresh: jest.fn(),
};

let currentPathname = "/dashboard";
let currentAuthUser: Record<string, unknown> | null = null;
const sendFeedbackWidgetMock = jest.fn((_props: unknown) => {
  void _props;
  return null;
});

jest.mock("next/navigation", () => ({
  usePathname: () => currentPathname,
  useRouter: () => routerMock,
  useSearchParams: () => new URLSearchParams(),
}));

jest.mock("next/image", () => ({
  __esModule: true,
  default: ({ alt }: { alt: string }) => <span role="img" aria-label={alt} />,
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
      profileType: "STUDENT",
    };
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    routerMock.refresh.mockReset();
    sendFeedbackWidgetMock.mockClear();
    (getMe as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (logout as jest.Mock).mockReset();
    (needsOnboarding as jest.Mock).mockReset();
    (needsOnboarding as jest.Mock).mockReturnValue(false);
    clearPendingLightweightProfileCompletion("user-1");
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

  it("keeps published-plan browsing public for visitors without an account", async () => {
    currentPathname = "/collections/published";
    currentAuthUser = null;

    render(
      <AppShell>
        <div>Published plans</div>
      </AppShell>,
    );

    expect(screen.getByText("Published plans")).toBeInTheDocument();
    expect(screen.getByText("Public Navbar")).toBeInTheDocument();
    await waitFor(() => {
      expect(routerMock.replace).not.toHaveBeenCalled();
    });
  });

  it("keeps copied-note routes available for pending lightweight profile completion", async () => {
    currentPathname = "/notes/copied-note-1/quick-review";
    currentAuthUser = {
      id: "user-1",
      email: "note@example.com",
      displayName: "Note",
      emailVerifiedAt: "2026-03-31T00:00:00Z",
      onboardingCompletedAt: null,
      role: "USER",
      profileType: null,
    };
    (needsOnboarding as jest.Mock).mockReturnValue(true);
    setPendingLightweightProfileCompletion("user-1");

    render(
      <AppShell>
        <div>Copied Quick Review</div>
      </AppShell>,
    );

    expect(await screen.findByText("Copied Quick Review")).toBeInTheDocument();
    expect(routerMock.replace).not.toHaveBeenCalledWith("/onboarding");
  });

  it("falls back to the existing redirect when the copy-on-signup marker cannot be written", async () => {
    currentPathname = "/notes/copied-note-1/quick-review";
    currentAuthUser = {
      id: "user-1",
      email: "note@example.com",
      displayName: "Note",
      emailVerifiedAt: "2026-03-31T00:00:00Z",
      onboardingCompletedAt: null,
      role: "USER",
      profileType: null,
    };
    (needsOnboarding as jest.Mock).mockReturnValue(true);
    const setItemSpy = jest.spyOn(Storage.prototype, "setItem").mockImplementationOnce(() => {
      throw new Error("Storage unavailable");
    });
    setPendingLightweightProfileCompletion("user-1");
    setItemSpy.mockRestore();

    render(
      <AppShell>
        <div>Incomplete user</div>
      </AppShell>,
    );

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/onboarding");
    });
  });

  it("returns authenticated users to the auth flow cleanly on logout", async () => {
    render(
      <AppShell>
        <div>Dashboard content</div>
      </AppShell>,
    );

    expect(await screen.findByRole("img", { name: "NoteLib" })).toBeInTheDocument();
    expect((await screen.findAllByRole("link", { name: "Library" }))[0]).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "My Library" })).not.toBeInTheDocument();

    fireEvent.click(await screen.findByLabelText("Open user menu"));
    fireEvent.click(screen.getByRole("button", { name: "Sign Out" }));

    await waitFor(() => {
      expect(logout).toHaveBeenCalled();
      expect(routerMock.replace).toHaveBeenCalledWith("/login?reason=logged_out");
    });
  });

  it("shows My Profile and Settings in the avatar dropdown", async () => {
    render(
      <AppShell>
        <div>Dashboard content</div>
      </AppShell>,
    );

    fireEvent.click(await screen.findByLabelText("Open user menu"));

    const myProfile = screen.getByRole("link", { name: "My Profile" });
    expect(myProfile).toBeInTheDocument();
    expect(myProfile).toHaveAttribute("href", "/public/profile/user-1");

    const profileLinks = screen.getAllByRole("link", { name: "Profile" });
    expect(profileLinks.length).toBeGreaterThan(0);
    profileLinks.forEach((link) => {
      expect(link).toHaveAttribute("href", "/profile");
    });

    const settingsLinks = screen.getAllByRole("link", { name: "Settings" });
    expect(settingsLinks.length).toBeGreaterThan(0);
    settingsLinks.forEach((link) => {
      expect(link).toHaveAttribute("href", "/settings");
    });
  });

  it("shows the profile-aware collections nav label", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...meResponse,
      profileType: "TEACHER",
    });

    render(
      <AppShell>
        <div>Dashboard content</div>
      </AppShell>,
    );

    expect((await screen.findAllByRole("link", { name: "Lesson Plans" }))[0]).toHaveAttribute("href", "/collections");
  });

  it("uses the header feedback icon on note editor routes", async () => {
    currentPathname = "/notes/new";

    render(
      <AppShell>
        <div>New note content</div>
      </AppShell>,
    );

    await waitFor(() => {
      expect(
        sendFeedbackWidgetMock.mock.calls.some(([props]) => props && (props as { variant?: string }).variant === "icon"),
      ).toBe(true);
      expect(
        sendFeedbackWidgetMock.mock.calls.some(([props]) => Object.keys((props as Record<string, unknown>) ?? {}).length === 0),
      ).toBe(false);
    });
  });

  it("uses the header feedback icon on quiz routes", async () => {
    currentPathname = "/notes/note-1/challenge-quiz";

    render(
      <AppShell>
        <div>Challenge Quiz content</div>
      </AppShell>,
    );

    await waitFor(() => {
      expect(
        sendFeedbackWidgetMock.mock.calls.some(([props]) => props && (props as { variant?: string }).variant === "icon"),
      ).toBe(true);
      expect(
        sendFeedbackWidgetMock.mock.calls.some(([props]) => Object.keys((props as Record<string, unknown>) ?? {}).length === 0),
      ).toBe(false);
    });
  });

  it("uses the header feedback icon on dashboard routes and never the floating widget", async () => {
    currentPathname = "/dashboard";

    render(
      <AppShell>
        <div>Dashboard content</div>
      </AppShell>,
    );

    await waitFor(() => {
      expect(
        sendFeedbackWidgetMock.mock.calls.some(([props]) => props && (props as { variant?: string }).variant === "icon"),
      ).toBe(true);
      expect(
        sendFeedbackWidgetMock.mock.calls.some(([props]) => Object.keys((props as Record<string, unknown>) ?? {}).length === 0),
      ).toBe(false);
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

  it("hides the sidebar and page header when exam focus is active", async () => {
    currentPathname = "/notes/note-1/long-exam";
    window.history.replaceState({}, "", currentPathname);

    function ExamFocusActivator() {
      useExamFocusMode(true);
      return <div>Long Exam in session</div>;
    }

    render(
      <ExamFocusProvider>
        <AppShell>
          <ExamFocusActivator />
        </AppShell>
      </ExamFocusProvider>,
    );

    expect(await screen.findByText("Long Exam in session")).toBeInTheDocument();
    expect(screen.queryByLabelText("Open user menu")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Open navigation menu")).not.toBeInTheDocument();
    expect(screen.queryByText("Public Navbar")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Note" })).not.toBeInTheDocument();
    expect(screen.queryByTestId("mobile-bottom-tab-bar")).not.toBeInTheDocument();
  });

  it("hides mobile tabs while a Quick Review session owns the bottom viewport", async () => {
    currentPathname = "/study-packs/pack-1/quick-review";
    window.history.replaceState({}, "", currentPathname);

    function QuickReviewActivator() {
      useBottomViewportClaim(true);
      return <div>Quick Review in session</div>;
    }

    render(
      <ExamFocusProvider>
        <AppShell>
          <QuickReviewActivator />
        </AppShell>
      </ExamFocusProvider>,
    );

    expect(await screen.findByText("Quick Review in session")).toBeInTheDocument();
    expect(screen.getByLabelText("Open user menu")).toBeInTheDocument();
    expect(screen.queryByTestId("mobile-bottom-tab-bar")).not.toBeInTheDocument();
  });

  it("hides mobile tabs when the persisted preference is disabled", async () => {
    (getMe as jest.Mock).mockResolvedValue({ ...meResponse, mobileTabBarEnabled: false });

    render(
      <ExamFocusProvider>
        <AppShell>
          <div>Focused note detail</div>
        </AppShell>
      </ExamFocusProvider>,
    );

    expect(await screen.findByText("Focused note detail")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByTestId("mobile-bottom-tab-bar")).not.toBeInTheDocument();
    });
  });

  it("shows mobile tabs by default for accounts whose response omits the preference", async () => {
    (getMe as jest.Mock).mockResolvedValue({ ...meResponse, mobileTabBarEnabled: undefined });

    render(
      <ExamFocusProvider>
        <AppShell>
          <div>Dashboard content</div>
        </AppShell>
      </ExamFocusProvider>,
    );

    expect(await screen.findByText("Dashboard content")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId("mobile-bottom-tab-bar")).toBeInTheDocument();
    });
  });
});
