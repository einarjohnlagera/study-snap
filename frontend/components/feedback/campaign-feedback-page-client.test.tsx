import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CampaignFeedbackPageClient } from "./campaign-feedback-page-client";
import {
  ApiRequestError,
  getCampaignFeedbackStatus,
  submitCampaignFeedback,
} from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

const replaceMock = jest.fn();
const routerMock = { replace: replaceMock };

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getCampaignFeedbackStatus: jest.fn(),
  submitCampaignFeedback: jest.fn(),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: jest.fn(),
}));

const statusMock = getCampaignFeedbackStatus as jest.Mock;
const submitMock = submitCampaignFeedback as jest.Mock;
const authGuardMock = requireAuthenticatedOnboardedUser as jest.Mock;

describe("CampaignFeedbackPageClient", () => {
  beforeEach(() => {
    replaceMock.mockReset();
    statusMock.mockReset();
    submitMock.mockReset();
    authGuardMock.mockReset();
    authGuardMock.mockReturnValue(true);
    statusMock.mockResolvedValue({ submitted: false, campaignOpen: true });
    submitMock.mockResolvedValue(undefined);
  });

  async function renderOpenForm() {
    render(<CampaignFeedbackPageClient />);
    await screen.findByText("What gets in the way when you study with NoteLib?");
  }

  it("renders the form for authenticated direct navigation and redirects unauthenticated users", async () => {
    await renderOpenForm();
    expect(screen.getByRole("button", { name: "Send feedback" })).toBeDisabled();
    expect(screen.getByText("Choose at least one option or add a comment.")).toBeInTheDocument();

    authGuardMock.mockReturnValue(false);
    statusMock.mockClear();
    render(<CampaignFeedbackPageClient />);
    await waitFor(() => expect(authGuardMock).toHaveBeenCalled());
    expect(statusMock).not.toHaveBeenCalled();
  });

  it("enforces Nothing major exclusivity while preserving free text", async () => {
    await renderOpenForm();
    const freeText = screen.getByRole("textbox", { name: /Anything else/ });
    fireEvent.change(freeText, { target: { value: "Keep this comment" } });
    fireEvent.click(screen.getByRole("checkbox", { name: "Studying takes too many steps" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Nothing major — NoteLib works well for me" }));
    expect(screen.getByRole("checkbox", { name: "Studying takes too many steps" })).toHaveAttribute("aria-checked", "false");
    expect(freeText).toHaveValue("Keep this comment");

    fireEvent.click(screen.getByRole("checkbox", { name: "A feature I need is missing" }));
    expect(screen.getByRole("checkbox", { name: "Nothing major — NoteLib works well for me" })).toHaveAttribute("aria-checked", "false");
  });

  it("reveals conditionals, keeps plan selection singular, and strips hidden answers", async () => {
    await renderOpenForm();
    fireEvent.click(screen.getByRole("checkbox", { name: "The quiz questions could be better" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Some questions or answers seem incorrect" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "The paid plans aren't right for me" }));
    fireEvent.click(screen.getByRole("radio", { name: "I can't comfortably afford it" }));
    fireEvent.click(screen.getByRole("radio", { name: "I'm happy with the free version" }));
    expect(screen.getByRole("radio", { name: "I can't comfortably afford it" })).toHaveAttribute("aria-checked", "false");
    expect(screen.getByRole("radio", { name: "I'm happy with the free version" })).toHaveAttribute("aria-checked", "true");

    fireEvent.click(screen.getByRole("checkbox", { name: "The quiz questions could be better" }));
    fireEvent.click(screen.getByRole("button", { name: "Send feedback" }));
    await waitFor(() => expect(submitMock).toHaveBeenCalled());
    expect(submitMock.mock.calls[0][0]).toEqual(expect.objectContaining({ quizIssues: [] }));
  });

  it("supports free-text-only submission and replaces the form with thank-you copy", async () => {
    await renderOpenForm();
    fireEvent.change(screen.getByRole("textbox", { name: /Anything else/ }), { target: { value: "A focused comment" } });
    const submitButton = screen.getByRole("button", { name: "Send feedback" });
    await waitFor(() => expect(submitButton).toBeEnabled());
    fireEvent.click(submitButton);

    await waitFor(() => expect(submitMock).toHaveBeenCalled());
    expect(await screen.findByText("Thanks for helping us improve NoteLib 💙")).toBeInTheDocument();
    expect(submitMock).toHaveBeenCalledWith(expect.objectContaining({ primaryBlockers: [], freeText: "A focused comment" }));
    expect(screen.getByRole("link", { name: "Back to studying" })).toHaveAttribute("href", "/dashboard");
  });

  it("submits one blocker without free text", async () => {
    await renderOpenForm();
    fireEvent.click(screen.getByRole("checkbox", { name: "I want more practice questions" }));
    fireEvent.click(screen.getByRole("button", { name: "Send feedback" }));

    await waitFor(() => expect(submitMock).toHaveBeenCalledWith(expect.objectContaining({
      primaryBlockers: ["WANT_MORE_PRACTICE"],
      freeText: null,
    })));
  });

  it("keeps the form populated and places validation errors with their field", async () => {
    submitMock.mockRejectedValueOnce(new ApiRequestError(
      "missingFeatureText must be 200 characters or fewer.",
      { status: 400 },
    ));
    await renderOpenForm();
    fireEvent.click(screen.getByRole("checkbox", { name: "A feature I need is missing" }));
    const field = screen.getByRole("textbox", { name: "What are you looking for?" });
    fireEvent.change(field, { target: { value: "still here" } });
    fireEvent.click(screen.getByRole("button", { name: "Send feedback" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("missingFeatureText");
    expect(field).toHaveValue("still here");
    expect(screen.getByText("What gets in the way when you study with NoteLib?")).toBeInTheDocument();
  });

  it("renders distinct already-responded and closed states", async () => {
    statusMock.mockResolvedValueOnce({ submitted: true, campaignOpen: false });
    const first = render(<CampaignFeedbackPageClient />);
    expect(await screen.findByText("You've already shared your feedback 💙")).toBeInTheDocument();
    first.unmount();

    statusMock.mockResolvedValueOnce({ submitted: false, campaignOpen: false });
    render(<CampaignFeedbackPageClient />);
    expect(await screen.findByText("This feedback campaign has ended.")).toBeInTheDocument();
    expect(screen.queryByText("You've already shared your feedback 💙")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to studying" })).toHaveAttribute("href", "/dashboard");
  });

  it("fails soft on status errors and shows closed when submit returns 409", async () => {
    statusMock.mockRejectedValueOnce(new Error("network"));
    submitMock.mockRejectedValueOnce(new ApiRequestError("closed", { status: 409 }));
    render(<CampaignFeedbackPageClient />);
    await screen.findByText("What gets in the way when you study with NoteLib?");
    fireEvent.click(screen.getByRole("checkbox", { name: "Something else" }));
    const submitButton = screen.getByRole("button", { name: "Send feedback" });
    await waitFor(() => expect(submitButton).toBeEnabled());
    fireEvent.click(submitButton);
    await waitFor(() => expect(submitMock).toHaveBeenCalled());
    expect(await screen.findByText("This feedback campaign has ended.")).toBeInTheDocument();
  });

  it("supports arrow-key movement in the plan radiogroup", async () => {
    await renderOpenForm();
    fireEvent.click(screen.getByRole("checkbox", { name: "The paid plans aren't right for me" }));
    const first = screen.getByRole("radio", { name: "I can't comfortably afford it" });
    first.focus();
    fireEvent.keyDown(screen.getByRole("radiogroup"), { key: "ArrowDown" });
    expect(screen.getByRole("radio", { name: "I don't see enough value to pay for it" })).toHaveFocus();
  });
});
