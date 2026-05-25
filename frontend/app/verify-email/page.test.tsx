import { render, screen, waitFor } from "@testing-library/react";
import VerifyEmailPage from "./page";
import { copyNoteOnSignup, getMe, getMyPlan, verifyEmailToken } from "@/lib/api";

const routerMock = {
  replace: jest.fn(),
};

let currentAuthUser: Record<string, unknown> | null = null;
let currentToken = "verify-token";
const setAuthUserMock = jest.fn((user: Record<string, unknown>) => {
  currentAuthUser = user;
});

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  useSearchParams: () => ({
    get: (key: string) => (key === "token" ? currentToken : null),
  }),
}));

jest.mock("@/lib/auth", () => ({
  buildLoginPath: jest.fn(() => "/login?redirectTo=%2Fverify-email"),
  getAuthUser: jest.fn(() => currentAuthUser),
  resolveAuthenticatedHome: jest.fn(() => "/dashboard"),
  setAuthUser: (user: Record<string, unknown>) => setAuthUserMock(user),
}));

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error {},
  copyNoteOnSignup: jest.fn(),
  getMe: jest.fn(),
  getMyPlan: jest.fn(),
  requestEmailVerification: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  verifyEmailToken: jest.fn(),
}));

describe("VerifyEmailPage", () => {
  beforeEach(() => {
    routerMock.replace.mockReset();
    setAuthUserMock.mockClear();
    document.cookie = "notelib-copy-intent=; path=/; max-age=0; SameSite=Strict";
    currentToken = "verify-token";
    currentAuthUser = {
      id: "user-1",
      email: "note@example.com",
      displayName: "Note",
      profileType: "STUDENT",
      emailVerifiedAt: null,
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: null,
      planType: "FREE",
    };
    (verifyEmailToken as jest.Mock).mockReset();
    (copyNoteOnSignup as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (verifyEmailToken as jest.Mock).mockResolvedValue({ message: "Email verified successfully." });
    (copyNoteOnSignup as jest.Mock).mockResolvedValue({ noteId: "copied-note-1" });
    (getMyPlan as jest.Mock).mockResolvedValue(null);
  });

  it("shows first-study activation actions after verifying a first-time user", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      id: "user-1",
      email: "note@example.com",
      pendingEmail: null,
      firstName: "Note",
      lastName: "Lib",
      displayName: "Note",
      countryCode: "PH",
      profileType: "STUDENT",
      examDate: null,
      engagementMode: "BALANCED",
      inactivityRemindersEnabled: true,
      weakConceptRemindersEnabled: true,
      emailVerifiedAt: "2026-03-31T08:00:00Z",
      onboardingCompletedAt: "2026-03-31T08:05:00Z",
      productOnboardingCompletedAt: null,
      studyPackCount: 0,
      role: "USER",
      status: "ACTIVE",
      planType: "FREE",
      subscription: null,
    });

    render(<VerifyEmailPage />);

    expect(await screen.findByRole("heading", { name: "Welcome to NoteLib!" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create First Note" })).toHaveAttribute("href", "/notes/new");
    expect(screen.getByRole("link", { name: "Go to Dashboard" })).toHaveAttribute("href", "/dashboard");
    await waitFor(() => {
      expect(setAuthUserMock).toHaveBeenCalled();
    });
  });

  it("copies the intended public note after verification and routes into quick review", async () => {
    document.cookie = "notelib-copy-intent=public-note-1; path=/; max-age=1800; SameSite=Strict";
    (getMe as jest.Mock).mockResolvedValue({
      id: "user-1",
      email: "note@example.com",
      pendingEmail: null,
      firstName: "Note",
      lastName: "Lib",
      displayName: "Note",
      countryCode: "PH",
      profileType: "STUDENT",
      examDate: null,
      engagementMode: "BALANCED",
      inactivityRemindersEnabled: true,
      weakConceptRemindersEnabled: true,
      emailVerifiedAt: "2026-03-31T08:00:00Z",
      onboardingCompletedAt: null,
      productOnboardingCompletedAt: null,
      studyPackCount: 0,
      role: "USER",
      status: "ACTIVE",
      planType: "FREE",
      subscription: null,
    });

    render(<VerifyEmailPage />);

    await waitFor(() => {
      expect(copyNoteOnSignup).toHaveBeenCalledWith("public-note-1");
      expect(routerMock.replace).toHaveBeenCalledWith("/notes/copied-note-1?copied=1&generate=1&startQuickReview=1");
    });
    expect(document.cookie).not.toContain("notelib-copy-intent=");
  });
});
