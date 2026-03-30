import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NoteEditorPageClient } from "./note-editor-page-client";
import {
  completeProductOnboarding,
  createNote,
  createStudyPackFromNote,
  extractNoteTextFromFile,
  getBillingPricing,
  getMyPlan,
  getNote,
  joinPremiumWaitlist,
  updateNote,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
    refresh: jest.fn(),
  }),
  usePathname: () => "/notes/new",
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeProductOnboarding: jest.fn(),
  createNote: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  extractNoteTextFromFile: jest.fn(),
  getBillingPricing: jest.fn(),
  getMyPlan: jest.fn(),
  getNote: jest.fn(),
  isEmailNotVerifiedError: (error: unknown) => error instanceof Error && error.message === "EMAIL_VERIFICATION_REQUIRED",
  joinPremiumWaitlist: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
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
  difficultySelectionAvailable: false,
};

describe("NoteEditorPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    window.localStorage.clear();
    window.sessionStorage.clear();
    (createStudyPackFromNote as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (createNote as jest.Mock).mockReset();
    (extractNoteTextFromFile as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (joinPremiumWaitlist as jest.Mock).mockReset();
    (updateNote as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
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
    (joinPremiumWaitlist as jest.Mock).mockResolvedValue({
      message: "You're on the list! We'll notify you when Premium launches.",
    });
    (createNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      id: "note-created",
      title: "",
      subject: null,
      tags: [],
      content: "Generated from teacher flow",
    });
    (createStudyPackFromNote as jest.Mock).mockResolvedValue({
      title: "Suggested Title",
      subject: "Biology",
      tags: ["cells"],
    });
    (updateNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      id: "note-created",
      title: "Suggested Title",
      subject: "Biology",
      tags: ["cells"],
      content: "Generated from teacher flow",
      studyPackStatus: "STUDY_PACK_READY",
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

  it("shows the first-study hint on the create note page when onboarding is in progress", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "create-note" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: null,
    });

    render(<NoteEditorPageClient />);

    expect(await screen.findByText("Step 1: Add your notes here.")).toBeInTheDocument();
    expect(screen.getByLabelText("Content")).toHaveClass("border-blue-500");
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
      difficultySelectionAvailable: false,
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

  it("keeps import available for unverified users while Generate stays disabled", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: null });

    render(<NoteEditorPageClient />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Some note content" } });

    const generateButton = screen.getByRole("button", { name: /Generate Study Pack/i });
    const uploadInput = document.getElementById("note-import-file") as HTMLInputElement | null;

    expect(generateButton).toBeDisabled();
    expect(uploadInput).not.toBeNull();
    expect(uploadInput).not.toBeDisabled();
  });

  it("shows a limit-reached paywall modal for free users at their monthly Study Pack cap", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1", planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 10,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 0,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<NoteEditorPageClient />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Some note content" } });
    fireEvent.click(screen.getByRole("button", { name: /Generate Study Pack/i }));

    expect(await screen.findByText("You’ve reached your study pack limit")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Upgrade to Premium" }));
    expect(pushMock).toHaveBeenCalledWith("/settings#plan-billing");
  });

  it("imports image OCR text into Content without generating a Study Pack and shows an inline warning for low confidence", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockResolvedValue({
      inputType: "image",
      extractedText: "Low confidence OCR text",
      meta: {
        ocrConfidence: 0.42,
        lowConfidence: true,
      },
    });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const uploadInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const image = new File(["fake"], "note.png", { type: "image/png" });
    fireEvent.change(uploadInput as HTMLInputElement, { target: { files: [image] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("Low confidence OCR text");
    });

    expect(
      screen.getByText("OCR may be inaccurate. Please review and edit the extracted text before saving or generating a Study Pack."),
    ).toBeInTheDocument();
    expect(createStudyPackFromNote).not.toHaveBeenCalled();
  });

  it("imports image OCR text into Content for standard OCR responses", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockResolvedValue({
      inputType: "image",
      extractedText: "High confidence OCR text",
      meta: {
        ocrConfidence: 0.98,
        lowConfidence: false,
      },
    });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const uploadInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const image = new File(["fake"], "note.png", { type: "image/png" });
    fireEvent.change(uploadInput as HTMLInputElement, { target: { files: [image] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("High confidence OCR text");
    });

    expect(createStudyPackFromNote).not.toHaveBeenCalled();
  });

  it("imports TXT file content into the Content field", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockResolvedValue({
      inputType: "txt",
      extractedText: "Imported TXT notes",
      meta: { ocrConfidence: null, lowConfidence: false },
    });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["text"], "notes.txt", { type: "text/plain" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("Imported TXT notes");
    });
  });

  it("imports DOCX file content into the Content field", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockResolvedValue({
      inputType: "docx",
      extractedText: "Imported DOCX notes",
      meta: { ocrConfidence: null, lowConfidence: false },
    });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["docx"], "notes.docx", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("Imported DOCX notes");
    });
  });

  it("imports text-based PDF content into the Content field", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockResolvedValue({
      inputType: "pdf",
      extractedText: "Imported PDF notes",
      meta: { ocrConfidence: null, lowConfidence: false },
    });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["pdf"], "notes.pdf", { type: "application/pdf" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("Imported PDF notes");
    });
  });

  it("shows an unsupported file type validation message", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockRejectedValue(
      new Error("Unsupported file type. Upload PNG, JPG, JPEG, WEBP, TXT, PDF, or DOCX."),
    );

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["bad"], "notes.csv", { type: "text/csv" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    expect(
      await screen.findAllByText("Unsupported file type. Upload PNG, JPG, JPEG, WEBP, TXT, PDF, or DOCX."),
    ).not.toHaveLength(0);
  });

  it("shows a scanned PDF error when text cannot be extracted", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockRejectedValue(
      new Error("This PDF appears to be scanned or image-based. Please upload images for OCR instead."),
    );

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["pdf"], "scan.pdf", { type: "application/pdf" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    expect(
      await screen.findAllByText("This PDF appears to be scanned or image-based. Please upload images for OCR instead."),
    ).not.toHaveLength(0);
  });

  it("allows retrying the same file after an import error", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock)
      .mockRejectedValueOnce(new Error("Could not import this file."))
      .mockResolvedValueOnce({
        inputType: "txt",
        extractedText: "Recovered content",
        meta: { ocrConfidence: null, lowConfidence: false },
      });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const firstInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["text"], "notes.txt", { type: "text/plain" });
    fireEvent.change(firstInput as HTMLInputElement, { target: { files: [file] } });

    expect(await screen.findAllByText("Could not import this file.")).not.toHaveLength(0);
    expect(extractNoteTextFromFile).toHaveBeenCalledTimes(1);

    const secondInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    fireEvent.change(secondInput as HTMLInputElement, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("Recovered content");
    });
    expect(extractNoteTextFromFile).toHaveBeenCalledTimes(2);
  });

  it("shows a verification message when an unverified user uploads an image", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: null });
    (extractNoteTextFromFile as jest.Mock).mockRejectedValue(new Error("EMAIL_VERIFICATION_REQUIRED"));

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["img"], "note.png", { type: "image/png" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    expect(await screen.findAllByText("Verify your email before using OCR upload.")).not.toHaveLength(0);
  });

  it("shows the free OCR limit modal and upgrade path", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockRejectedValue(new Error("OCR_LIMIT_REACHED"));

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["img"], "note.png", { type: "image/png" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    expect(await screen.findByText("OCR limit reached")).toBeInTheDocument();
    expect(screen.getByText(/You’ve reached your image-to-text limit for this month\./i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Upgrade to Premium" }));
    expect(pushMock).toHaveBeenCalledWith("/settings#plan-billing");
  });

  it("shows the premium OCR limit modal without upgrade CTA", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PREMIUM",
      limits: {
        studyPacksPerMonth: 100,
        challengeQuizzesPerMonth: 50,
        adaptivePracticePerMonth: 30,
        ocrPerMonth: 100,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 100,
      },
      remaining: {
        studyPacksRemaining: 98,
        challengeQuizzesRemaining: 50,
        adaptivePracticeRemaining: 30,
        ocrRemaining: 0,
      },
      features: {
        adaptivePracticeAvailable: true,
        difficultySelectionAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (extractNoteTextFromFile as jest.Mock).mockRejectedValue(new Error("OCR_LIMIT_REACHED"));

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["img"], "note.png", { type: "image/png" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    expect(await screen.findByText("OCR limit reached")).toBeInTheDocument();
    expect(screen.getByText(/Your limits will reset on your next billing date\./i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Upgrade to Premium" })).not.toBeInTheDocument();
  });

  it("redirects generated quiz-focused notes to the practice quiz section", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient initialFocus="quiz" />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Generated from teacher flow" } });

    fireEvent.click(screen.getByRole("button", { name: /Generate Study Pack/i }));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/notes/note-created?from=notes&created=1#practice-quiz");
    });
  });
});
