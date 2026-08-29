import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import LinkedLearnerInvitationPage from "./page";
import {
  redeemLinkedLearnerInvitationLink,
  resolveLinkedLearnerInvitationLink,
} from "@/lib/api";
import { getAuthUser, resolveAuthenticatedHome } from "@/lib/auth";
import {
  clearLinkedLearnerRedemptionCompletion,
  setLinkedLearnerRedemptionCompletion,
} from "@/lib/linked-learner-redemption-completion";

const replace = jest.fn();
const push = jest.fn();

jest.mock("next/navigation", () => ({
  useParams: () => ({ token: "AbCdEf0123456789GhIjKl" }),
  useRouter: () => ({ replace, push }),
}));

jest.mock("@/lib/api", () => ({
  resolveLinkedLearnerInvitationLink: jest.fn(),
  redeemLinkedLearnerInvitationLink: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  resolveAuthenticatedHome: jest.fn(),
  buildLoginPath: ({ redirectTo, reason }: { redirectTo: string; reason: string }) =>
    `/login?redirect=${encodeURIComponent(redirectTo)}&reason=${reason}`,
  LOGIN_REASON_AUTH_REQUIRED: "auth_required",
}));

const authenticatedUser = {
  id: "user-1",
  emailVerifiedAt: "2026-08-28T00:00:00Z",
  onboardingCompletedAt: "2026-08-28T00:00:00Z",
  profileType: "STUDENT",
};

beforeEach(() => {
  jest.clearAllMocks();
  document.cookie = "notelib-connection-invite=; path=/; max-age=0; SameSite=Strict";
  clearLinkedLearnerRedemptionCompletion();
  jest.mocked(getAuthUser).mockReturnValue(authenticatedUser as ReturnType<typeof getAuthUser>);
  jest.mocked(resolveAuthenticatedHome).mockReturnValue("/dashboard");
  jest.mocked(resolveLinkedLearnerInvitationLink).mockResolvedValue({
    inviterName: "Morgan",
    inviterRole: "SUPPORTER",
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

it("requires authentication and stores the token before redirecting", async () => {
  jest.mocked(getAuthUser).mockReturnValue(null);

  render(<LinkedLearnerInvitationPage />);

  await waitFor(() => expect(replace).toHaveBeenCalledWith(
    "/login?redirect=%2Flinked-learners%2Finvite%2FAbCdEf0123456789GhIjKl&reason=auth_required",
  ));
  expect(document.cookie).toContain("notelib-connection-invite=AbCdEf0123456789GhIjKl");
  expect(resolveLinkedLearnerInvitationLink).not.toHaveBeenCalled();
});

it("shows only the authenticated invitation decision details", async () => {
  render(<LinkedLearnerInvitationPage />);

  expect(await screen.findByText("Connect with Morgan")).toBeInTheDocument();
  expect(screen.getByText("They would support your learning.")).toBeInTheDocument();
  expect(screen.queryByText(/@/)).not.toBeInTheDocument();
});

it("redeems into pending and collects the learner birth year", async () => {
  jest.mocked(redeemLinkedLearnerInvitationLink).mockResolvedValue({
    relationshipId: "relationship-1",
    status: "PENDING",
  });
  render(<LinkedLearnerInvitationPage />);
  await screen.findByText("Connect with Morgan");

  fireEvent.change(screen.getByLabelText(/Your birth year/), { target: { value: "2012" } });
  fireEvent.click(screen.getByRole("button", { name: "Confirm connection request" }));

  await waitFor(() => expect(redeemLinkedLearnerInvitationLink)
    .toHaveBeenCalledWith("AbCdEf0123456789GhIjKl", 2012));
  expect(await screen.findByText("Request sent")).toBeInTheDocument();
  expect(screen.getByText(/pending until the person who created the link confirms/i)).toBeInTheDocument();
  expect(screen.getByText(/stays with this pending request until then/i)).toBeInTheDocument();
  expect(screen.getByText(/deleted if either person revokes first/i)).toBeInTheDocument();
});

it("shows the already-sent state after the same user reloads a redeemed token", async () => {
  setLinkedLearnerRedemptionCompletion("AbCdEf0123456789GhIjKl", authenticatedUser.id);
  jest.mocked(resolveLinkedLearnerInvitationLink).mockRejectedValue(new Error(
    "This invitation link is not available.",
  ));

  render(<LinkedLearnerInvitationPage />);

  expect(await screen.findByRole("heading", { name: "Request sent" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "View learning connections" })).toHaveAttribute(
    "href",
    "/linked-learners",
  );
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  // ⚠️ The marker must SURVIVE the read. Clearing it on first view made the fix work exactly once,
  // so a second reload fell through to the dead-link error and the same action got two
  // contradictory answers. It expires on its own max-age instead.
  expect(document.cookie).toContain("notelib-connection-redemption=");

  cleanup();
  render(<LinkedLearnerInvitationPage />);
  expect(await screen.findByRole("heading", { name: "Request sent" })).toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

it("does not assert the connection's current state on a reload", async () => {
  setLinkedLearnerRedemptionCompletion("AbCdEf0123456789GhIjKl", authenticatedUser.id);
  jest.mocked(resolveLinkedLearnerInvitationLink).mockRejectedValue(new Error(
    "This invitation link is not available.",
  ));

  render(<LinkedLearnerInvitationPage />);

  expect(await screen.findByRole("heading", { name: "Request sent" })).toBeInTheDocument();
  // A reload knows only that this browser sent the request. The creator may have confirmed since,
  // so claiming the connection is pending — or that nothing is shared yet — would be the same
  // report-what-you-cannot-see defect this item exists to close. Pin the CLASS, not one sentence.
  expect(screen.queryByText(/is pending until/i)).not.toBeInTheDocument();
  expect(screen.queryByText(/no learning activity or progress is shared yet/i)).not.toBeInTheDocument();
});

it("does not reveal another user's redemption on the same device", async () => {
  setLinkedLearnerRedemptionCompletion("AbCdEf0123456789GhIjKl", authenticatedUser.id);
  jest.mocked(getAuthUser).mockReturnValue({
    ...authenticatedUser,
    id: "user-2",
  } as ReturnType<typeof getAuthUser>);
  jest.mocked(resolveLinkedLearnerInvitationLink).mockRejectedValue(new Error(
    "This invitation link is not available.",
  ));

  render(<LinkedLearnerInvitationPage />);

  // Pin the privacy class by state/role: user 2 gets the generic failure surface and never the
  // locally proven success surface, regardless of either sentence's wording.
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Request sent" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "View learning connections" })).not.toBeInTheDocument();
});

it("keeps redemption successful when the completion marker cannot be written", async () => {
  jest.mocked(redeemLinkedLearnerInvitationLink).mockResolvedValue({
    relationshipId: "relationship-1",
    status: "PENDING",
  });
  const first = render(<LinkedLearnerInvitationPage />);
  await screen.findByText("Connect with Morgan");
  const cookieSetter = jest.spyOn(Document.prototype, "cookie", "set").mockImplementation((value) => {
    if (value.startsWith("notelib-connection-redemption=")) {
      throw new Error("Cookies blocked");
    }
  });

  fireEvent.click(screen.getByRole("button", { name: "Confirm connection request" }));

  expect(await screen.findByRole("heading", { name: "Request sent" })).toBeInTheDocument();
  expect(redeemLinkedLearnerInvitationLink).toHaveBeenCalledTimes(1);
  first.unmount();
  cookieSetter.mockRestore();

  jest.mocked(resolveLinkedLearnerInvitationLink).mockRejectedValue(new Error(
    "This invitation link is not available.",
  ));
  render(<LinkedLearnerInvitationPage />);

  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Request sent" })).not.toBeInTheDocument();
});
