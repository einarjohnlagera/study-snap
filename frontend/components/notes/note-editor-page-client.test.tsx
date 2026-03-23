import { fireEvent, render, screen } from "@testing-library/react";
import { NoteEditorPageClient } from "./note-editor-page-client";
import { getBillingPricing, getBillingUsageSummary, getNote } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
    refresh: jest.fn(),
  }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  confirmStudyPackText: jest.fn(),
  createNote: jest.fn(),
  createStudyPackFromImage: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  getBillingPricing: jest.fn(),
  getBillingUsageSummary: jest.fn(),
  getNote: jest.fn(),
  isEmailNotVerifiedError: () => false,
  isNeedsTextConfirmationResponse: () => false,
  updateNote: jest.fn(),
}));

const baseNote = {
  id: "note-1",
  title: "Draft Note",
  subject: "Biology",
  tags: ["cells"],
  content: "Cell content",
  visibility: "PRIVATE" as const,
  createdAt: "2026-03-21T10:00:00Z",
  updatedAt: "2026-03-21T10:30:00Z",
  copiedFromNoteId: null,
  copiedFromUserId: null,
  copiedFromTitle: null,
  copiedFromPublic: false,
  copiedAt: null,
  studyPackId: null,
  studyPackStatus: "DRAFT" as const,
  summary: null,
  keyConcepts: [],
  quiz: [],
  quizCount: 0,
  quickReviewAvailable: false,
  challengeQuizAvailable: false,
  adaptivePracticeAvailable: false,
};

describe("NoteEditorPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (getBillingUsageSummary as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (getBillingUsageSummary as jest.Mock).mockResolvedValue({
      planType: "FREE",
      studyPacksUsed: 2,
      studyPacksLimit: 5,
      challengeQuizUsed: 0,
      challengeQuizLimit: 0,
      adaptivePracticeUsed: 0,
      adaptivePracticeLimit: 0,
    });
    (getBillingPricing as jest.Mock).mockResolvedValue({
      region: "PH",
      currency: "PHP",
      monthlyPrice: 249,
      yearlyPrice: 1999,
      introMonthlyPrice: 199,
      hasIntroPromo: true,
      introEligible: true,
    });
  });

  it("keeps draft note title/subject/tags/content editable", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<NoteEditorPageClient noteId="note-1" />);

    const titleInput = await screen.findByLabelText("Title (optional)");
    const subjectInput = screen.getByLabelText("Subject (optional)");
    const contentInput = screen.getByLabelText("Content");

    expect(titleInput).not.toBeDisabled();
    expect(subjectInput).not.toBeDisabled();
    expect(contentInput).not.toHaveAttribute("readonly");
    expect(screen.getByRole("button", { name: /\+ Add Tag/i })).toBeInTheDocument();
  });

  it("locks content editing for generated notes but keeps metadata fields editable", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      id: "note-generated",
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: ["Concept"],
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });

    render(<NoteEditorPageClient noteId="note-generated" />);

    const titleInput = await screen.findByLabelText("Title (optional)");
    const subjectInput = screen.getByLabelText("Subject (optional)");
    const contentInput = screen.getByLabelText("Content");

    expect(titleInput).not.toBeDisabled();
    expect(subjectInput).not.toBeDisabled();
    expect(screen.getByRole("button", { name: /\+ Add Tag/i })).toBeInTheDocument();
    expect(contentInput).toHaveAttribute("readonly");
    expect(
      screen.getByText("Note content is locked after generating a Study Pack. Make a copy to change the note itself."),
    ).toBeInTheDocument();
  });

  it("disables OCR upload and Generate action when user is unverified", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: null });

    render(<NoteEditorPageClient />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Some note content" } });

    const generateButton = screen.getByRole("button", { name: /Generate Study Pack/i });
    const uploadInput = document.getElementById("note-ocr-image") as HTMLInputElement | null;

    expect(generateButton).toBeDisabled();
    expect(uploadInput).not.toBeNull();
    expect(uploadInput).toBeDisabled();
  });

  it("shows a limit-reached paywall modal for free users at their monthly Study Pack cap", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getBillingUsageSummary as jest.Mock).mockResolvedValue({
      planType: "FREE",
      studyPacksUsed: 5,
      studyPacksLimit: 5,
      challengeQuizUsed: 0,
      challengeQuizLimit: 0,
      adaptivePracticeUsed: 0,
      adaptivePracticeLimit: 0,
    });

    render(<NoteEditorPageClient />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Some note content" } });
    fireEvent.click(screen.getByRole("button", { name: /Generate Study Pack/i }));

    expect(await screen.findByText("You've reached your monthly limit")).toBeInTheDocument();
    expect(await screen.findByText("First month ₱199, then ₱249/month")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Upgrade to Premium" }));

    expect(pushMock).toHaveBeenCalledWith("/settings#plan-billing");
  });
});
