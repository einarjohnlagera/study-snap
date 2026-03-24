import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NoteEditorPageClient } from "./note-editor-page-client";
import { createStudyPackFromImage, getBillingPricing, getBillingUsageSummary, getNote, isNeedsTextConfirmationResponse } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { importNoteFile } from "@/lib/note-import";

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
}));

jest.mock("@/lib/note-import", () => ({
  importNoteFile: jest.fn(),
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
  isNeedsTextConfirmationResponse: jest.fn(() => false),
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
};

describe("NoteEditorPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    (createStudyPackFromImage as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (getBillingUsageSummary as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (isNeedsTextConfirmationResponse as jest.Mock).mockReset();
    (importNoteFile as jest.Mock).mockReset();
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
    (isNeedsTextConfirmationResponse as jest.Mock).mockReturnValue(false);
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

  it("inserts OCR text directly into Content and shows an inline warning for low-confidence OCR", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (isNeedsTextConfirmationResponse as jest.Mock).mockReturnValue(true);
    (createStudyPackFromImage as jest.Mock).mockResolvedValue({
      status: "needs_text_confirmation",
      id: "draft-1",
      extractedText: "Low confidence OCR text",
      meta: {
        ocrConfidence: 0.42,
        latencyMs: 1200,
      },
    });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const uploadInput = document.getElementById("note-ocr-image") as HTMLInputElement | null;
    expect(uploadInput).not.toBeNull();

    const image = new File(["fake"], "note.png", { type: "image/png" });
    fireEvent.change(uploadInput as HTMLInputElement, { target: { files: [image] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("Low confidence OCR text");
    });

    expect(
      screen.getByText("OCR may be inaccurate. Please review and edit the extracted text before saving or generating a Study Pack."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Review Extracted Text")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Use OCR Text" })).not.toBeInTheDocument();
  });

  it("inserts extracted OCR text into Content for standard OCR responses", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (createStudyPackFromImage as jest.Mock).mockResolvedValue({
      id: "study-pack-1",
      noteId: null,
      inputType: "image",
      extractedText: "High confidence OCR text",
      sourceText: null,
      title: "Generated",
      summary: "Summary",
      subject: null,
      keyConcepts: [],
      tags: [],
      quiz: [],
      createdAt: "2026-03-21T10:00:00Z",
      meta: {
        ocrConfidence: 0.98,
        latencyMs: 800,
      },
    });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const uploadInput = document.getElementById("note-ocr-image") as HTMLInputElement | null;
    const image = new File(["fake"], "note.png", { type: "image/png" });
    fireEvent.change(uploadInput as HTMLInputElement, { target: { files: [image] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("High confidence OCR text");
    });

    expect(screen.queryByText("Review Extracted Text")).not.toBeInTheDocument();
  });

  it("imports TXT file content into the Content field", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (importNoteFile as jest.Mock).mockResolvedValue({
      kind: "txt",
      text: "Imported TXT notes",
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
    (importNoteFile as jest.Mock).mockResolvedValue({
      kind: "docx",
      text: "Imported DOCX notes",
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
    (importNoteFile as jest.Mock).mockResolvedValue({
      kind: "pdf",
      text: "Imported PDF notes",
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
    (importNoteFile as jest.Mock).mockRejectedValue(new Error("Unsupported file type. Upload a TXT, PDF, or DOCX file."));

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["bad"], "notes.csv", { type: "text/csv" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    expect(
      await screen.findAllByText("Unsupported file type. Upload a TXT, PDF, or DOCX file."),
    ).not.toHaveLength(0);
  });

  it("shows a scanned PDF error when text cannot be extracted", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (importNoteFile as jest.Mock).mockRejectedValue(
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
});
