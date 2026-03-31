import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import AuthPage from "./page";
import { getMyPlan, login } from "@/lib/api";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
  refresh: jest.fn(),
};

let currentAuthUser: Record<string, unknown> | null = null;
const setAuthUserMock = jest.fn((user: Record<string, unknown>) => {
  currentAuthUser = user;
  window.dispatchEvent(new Event("studysnap-auth-change"));
});

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("next/image", () => ({
  __esModule: true,
  default: ({ alt }: { alt: string }) => <img alt={alt} />,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => currentAuthUser),
  LOGIN_REASON_QUERY_KEY: "reason",
  LOGIN_REASON_SESSION_EXPIRED: "session_expired",
  resolveAuthenticatedHome: jest.fn((authUser: { emailVerifiedAt?: string | null } | null) =>
    authUser?.emailVerifiedAt ? "/dashboard" : "/verify-email",
  ),
  setAuthUser: (user: Record<string, unknown>) => setAuthUserMock(user),
}));

jest.mock("@/lib/api", () => ({
  getMyPlan: jest.fn(),
  login: jest.fn(),
  signup: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

const verifiedAuthUser = {
  id: "user-1",
  email: "[email protected]",
  displayName: "Note",
  profileType: "STUDENT",
  emailVerifiedAt: "2026-03-31T00:00:00Z",
  onboardingCompletedAt: "2026-03-31T00:05:00Z",
  productOnboardingCompletedAt: null,
  role: "USER",
  planType: "FREE",
  accessToken: "access-token",
  refreshToken: "refresh-token",
  accessTokenExpiresAt: "2026-03-31T01:00:00Z",
  refreshTokenExpiresAt: "2026-04-30T01:00:00Z",
};

describe("AuthPage", () => {
  beforeEach(() => {
    currentAuthUser = null;
    setAuthUserMock.mockClear();
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    routerMock.refresh.mockReset();
    (login as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockResolvedValue(null);
    window.history.replaceState({}, "", "/login");
  });

  it("renders public footer legal links", () => {
    render(<AuthPage />);

    expect(screen.getByRole("link", { name: "Privacy Policy" })).toHaveAttribute("href", "/privacy");
    expect(screen.getByRole("link", { name: "Terms of Service" })).toHaveAttribute("href", "/terms");
    expect(screen.getByRole("link", { name: "Contact" })).toHaveAttribute("href", "mailto:support@mail.notelib.app");
  });

  it("shows signup mode while keeping legal links visible", () => {
    render(<AuthPage />);

    fireEvent.click(screen.getByRole("button", { name: "Sign up" }));

    expect(screen.getByRole("heading", { name: "Create your NoteLib account" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Privacy Policy" })).toBeInTheDocument();
  });

  it("redirects a normal successful login to the dashboard", async () => {
    (login as jest.Mock).mockResolvedValue(verifiedAuthUser);

    const { container } = render(<AuthPage />);

    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "note@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "password123" },
    });
    fireEvent.submit(container.querySelector("form") as HTMLFormElement);

    await waitFor(() => {
      expect(setAuthUserMock).toHaveBeenCalled();
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
    await waitFor(() => {
      expect(screen.queryByRole("heading", { name: "Log in to NoteLib" })).not.toBeInTheDocument();
    });
  });

  it("shows the session-expired banner and redirects cleanly after re-login", async () => {
    window.history.replaceState({}, "", "/login?reason=session_expired");
    (login as jest.Mock).mockResolvedValue(verifiedAuthUser);

    const { container } = render(<AuthPage />);

    expect(screen.getByText("Your session has expired. Please log in again.")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "note@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "password123" },
    });
    fireEvent.submit(container.querySelector("form") as HTMLFormElement);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
    await waitFor(() => {
      expect(screen.queryByText("Your session has expired. Please log in again.")).not.toBeInTheDocument();
    });
  });

  it("redirects authenticated visitors away from the login page", async () => {
    currentAuthUser = verifiedAuthUser;

    render(<AuthPage />);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
    expect(screen.queryByRole("heading", { name: "Log in to NoteLib" })).not.toBeInTheDocument();
  });
});
