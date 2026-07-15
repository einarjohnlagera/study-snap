import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import AuthPage from "./page";
import { ApiRequestError, copyNoteOnSignup, getMyPlan, login, loginWithGoogle, reactivateAccount } from "@/lib/api";
import { hasPendingLightweightProfileCompletion } from "@/lib/onboarding-v2";

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

jest.mock("@/components/auth/google-auth-button", () => ({
  GoogleAuthButton: ({
    label,
    disabled,
    onCredential,
  }: {
    label: string;
    disabled?: boolean;
    onCredential: (code: string) => void | Promise<void>;
  }) => (
    <button
      type="button"
      disabled={disabled || !process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID}
      onClick={() => void onCredential("google-code-1")}
    >
      {label}
    </button>
  ),
}));

jest.mock("@/lib/auth", () => {
  const actual = jest.requireActual("@/lib/auth");
  return {
    ...actual,
    getAuthUser: jest.fn(() => currentAuthUser),
    setAuthUser: (user: Record<string, unknown>) => setAuthUserMock(user),
  };
});

jest.mock("@/lib/api", () => {
  const actual = jest.requireActual("@/lib/api");
  return {
    ...actual,
    copyNoteOnSignup: jest.fn(),
    getMyPlan: jest.fn(),
    login: jest.fn(),
    loginWithGoogle: jest.fn(),
    reactivateAccount: jest.fn(),
    signup: jest.fn(),
    trackAnalyticsEvent: jest.fn(),
  };
});

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
    document.cookie = "notelib-copy-intent=; path=/; max-age=0; SameSite=Strict";
    document.cookie = "notelib-exam-intent=; path=/; max-age=0; SameSite=Strict";
    setAuthUserMock.mockClear();
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    routerMock.refresh.mockReset();
    (login as jest.Mock).mockReset();
    (loginWithGoogle as jest.Mock).mockReset();
    (reactivateAccount as jest.Mock).mockReset();
    (copyNoteOnSignup as jest.Mock).mockReset();
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

  it("restates the save-note intent for copy-note signup arrivals", () => {
    window.history.replaceState({}, "", "/auth?mode=signup&intent=copy-note");

    render(<AuthPage />);

    expect(screen.getByText("Sign up to save this note to your library.")).toBeInTheDocument();
  });

  it("restates the learning intent for Learn guide signup arrivals", () => {
    window.history.replaceState({}, "", "/auth?mode=signup&intent=learn");

    render(<AuthPage />);

    expect(screen.getByText("Sign up to keep learning with more free study guides.")).toBeInTheDocument();
  });

  it("prioritizes the copy-note description when multiple signup intents are present", () => {
    window.history.replaceState({}, "", "/auth?mode=signup&intent=learn&intent=copy-note");

    render(<AuthPage />);

    expect(screen.getByText("Sign up to save this note to your library.")).toBeInTheDocument();
  });

  it("keeps the generic signup copy when no specific intent is present", () => {
    window.history.replaceState({}, "", "/auth?mode=signup");

    render(<AuthPage />);

    expect(screen.getByText("Sign up to generate and save Study Packs.")).toBeInTheDocument();
  });

  it("renders the Google auth option without removing email/password login", () => {
    render(<AuthPage />);

    expect(screen.getByRole("button", { name: "Continue with Google" })).toBeInTheDocument();
    expect(screen.getByLabelText("Email or username")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toBeInTheDocument();
  });

  it("persists exam intent from the auth query for signup continuation", async () => {
    window.history.replaceState({}, "", "/auth?mode=signup&intent=exam&exam=ale");

    render(<AuthPage />);

    await waitFor(() => {
      expect(document.cookie).toContain("notelib-exam-intent=ale");
    });
    expect(screen.getByRole("heading", { name: "Create your NoteLib account" })).toBeInTheDocument();
  });

  it("disables Google login when it is not configured", () => {
    render(<AuthPage />);

    expect(screen.getByRole("button", { name: "Continue with Google" })).toBeDisabled();
  });

  it("redirects a normal successful login to the dashboard", async () => {
    document.cookie = "notelib-copy-intent=stale-note; path=/; max-age=1800; SameSite=Strict";
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
      expect(document.cookie).not.toContain("notelib-copy-intent=");
    });
    await waitFor(() => {
      expect(screen.queryByRole("heading", { name: "Log in to NoteLib" })).not.toBeInTheDocument();
    });
  });

  it("copies the intended public note after Google signup and marks lightweight profile completion as pending", async () => {
    const originalClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
    process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID = "test-google-client-id";
    try {
      document.cookie = "notelib-copy-intent=public-note-1; path=/; max-age=1800; SameSite=Strict";
      (loginWithGoogle as jest.Mock).mockResolvedValue({
        ...verifiedAuthUser,
        id: "user-google-1",
        onboardingCompletedAt: null,
      });
      (copyNoteOnSignup as jest.Mock).mockResolvedValue({ noteId: "copied-note-1" });

      render(<AuthPage />);

      fireEvent.click(screen.getByRole("button", { name: "Continue with Google" }));

      await waitFor(() => {
        expect(copyNoteOnSignup).toHaveBeenCalledWith("public-note-1");
        expect(routerMock.replace).toHaveBeenCalledWith("/notes/copied-note-1?copied=1&startQuickReview=1");
      });
      expect(hasPendingLightweightProfileCompletion("user-google-1")).toBe(true);
      expect(document.cookie).not.toContain("notelib-copy-intent=");
    } finally {
      process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID = originalClientId;
    }
  });

  it("offers account reactivation when login returns pending deletion", async () => {
    (login as jest.Mock).mockRejectedValue(new ApiRequestError(
      "This account is scheduled for deletion. Reactivate to keep it.",
      { code: "ACCOUNT_PENDING_DELETION", status: 403 },
    ));
    (reactivateAccount as jest.Mock).mockResolvedValue(verifiedAuthUser);

    const { container } = render(<AuthPage />);

    fireEvent.change(screen.getByLabelText("Email or username"), {
      target: { value: "note@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "password123" },
    });
    fireEvent.submit(container.querySelector("form") as HTMLFormElement);

    expect(await screen.findByRole("button", { name: "Reactivate account" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Reactivate account" }));

    await waitFor(() => {
      expect(reactivateAccount).toHaveBeenCalledWith({
        email: "note@example.com",
        password: "password123",
        keepSignedIn: false,
      });
      expect(setAuthUserMock).toHaveBeenCalled();
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
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
