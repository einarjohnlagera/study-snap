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
  useSearchParams: () => new URLSearchParams(window.location.search),
}));

jest.mock("next/image", () => ({
  __esModule: true,
  default: ({ alt }: { alt: string }) => <span role="img" aria-label={alt} />,
}));

jest.mock("@/lib/auth", () => {
  const actual = jest.requireActual("@/lib/auth");
  return {
    ...actual,
    getAuthUser: jest.fn(() => currentAuthUser),
    setAuthUser: (user: Record<string, unknown>) => setAuthUserMock(user),
  };
});

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

const secondVerifiedAuthUser = {
  ...verifiedAuthUser,
  id: "user-2",
  email: "[email protected]",
  displayName: "Second",
};

describe("AuthPage", () => {
  beforeEach(() => {
    currentAuthUser = null;
    window.localStorage.clear();
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

    fireEvent.change(screen.getByLabelText("Email or username"), {
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
    window.history.replaceState({}, "", "/login?reason=session_expired&redirect=%2Fnotes%2Fnote-1%3Ftab%3Dquiz");
    window.sessionStorage.setItem("notelib-session-expired-user-id", "user-1");
    (login as jest.Mock).mockResolvedValue(verifiedAuthUser);

    const { container } = render(<AuthPage />);

    expect(screen.getByText("Your session expired. Please log in again.")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Email or username"), {
      target: { value: "note@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "password123" },
    });
    fireEvent.submit(container.querySelector("form") as HTMLFormElement);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/notes/note-1?tab=quiz");
    });
    await waitFor(() => {
      expect(screen.queryByText("Your session expired. Please log in again.")).not.toBeInTheDocument();
    });
  });

  it("shows no status message after manual logout", () => {
    window.history.replaceState({}, "", "/login?reason=logged_out");

    render(<AuthPage />);

    expect(screen.queryByText("You have been logged out.")).not.toBeInTheDocument();
    expect(screen.queryByText("Your session expired. Please log in again.")).not.toBeInTheDocument();
  });

  it("redirects manual logout relogin to the dashboard even when a stale redirect query remains", async () => {
    window.history.replaceState(
      {},
      "",
      "/login?reason=logged_out&redirect=%2Fstudy-packs%2Fnote-1%2Fchallenge-quiz",
    );
    (login as jest.Mock).mockResolvedValue(verifiedAuthUser);

    const { container } = render(<AuthPage />);

    fireEvent.change(screen.getByLabelText("Email or username"), {
      target: { value: "note@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "password123" },
    });
    fireEvent.submit(container.querySelector("form") as HTMLFormElement);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
  });

  it("does not leak a stale protected redirect across accounts after manual logout", async () => {
    window.history.replaceState({}, "", "/login?reason=logged_out&redirect=%2Fnotes%2Fnote-1");
    (login as jest.Mock).mockResolvedValue(secondVerifiedAuthUser);

    const { container } = render(<AuthPage />);

    fireEvent.change(screen.getByLabelText("Email or username"), {
      target: { value: "second@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "password123" },
    });
    fireEvent.submit(container.querySelector("form") as HTMLFormElement);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
  });

  it("shows a neutral prompt for protected-route access while logged out", () => {
    window.history.replaceState({}, "", "/login?reason=auth_required&redirect=%2Flibrary");

    render(<AuthPage />);

    expect(screen.getByText("Please log in to continue.")).toBeInTheDocument();
    expect(screen.queryByText("Your session expired. Please log in again.")).not.toBeInTheDocument();
  });

  it("redirects authenticated visitors away from the login page", async () => {
    currentAuthUser = verifiedAuthUser;
    window.history.replaceState({}, "", "/login?redirect=%2Fprofile");

    render(<AuthPage />);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/profile");
    });
    expect(screen.queryByRole("heading", { name: "Log in to NoteLib" })).not.toBeInTheDocument();
  });

  it("sends authenticated visitors to the dashboard when the login page came from manual logout", async () => {
    currentAuthUser = verifiedAuthUser;
    window.history.replaceState({}, "", "/login?reason=logged_out&redirect=%2Fprofile");

    render(<AuthPage />);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
    expect(screen.queryByRole("heading", { name: "Log in to NoteLib" })).not.toBeInTheDocument();
  });

  it("redirects a successful login to the requested page when redirect query is present", async () => {
    window.history.replaceState({}, "", "/login?redirect=%2Fnotes%2Fnote-1%3Ftab%3Dsummary");
    (login as jest.Mock).mockResolvedValue(verifiedAuthUser);

    const { container } = render(<AuthPage />);

    fireEvent.change(screen.getByLabelText("Email or username"), {
      target: { value: "note@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "password123" },
    });
    fireEvent.submit(container.querySelector("form") as HTMLFormElement);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/notes/note-1?tab=summary");
    });
  });

  it("falls back to the dashboard when login has no explicit redirect", async () => {
    (login as jest.Mock).mockResolvedValue(verifiedAuthUser);

    const { container } = render(<AuthPage />);

    fireEvent.change(screen.getByLabelText("Email or username"), {
      target: { value: "note@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "password123" },
    });
    fireEvent.submit(container.querySelector("form") as HTMLFormElement);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
  });
});
