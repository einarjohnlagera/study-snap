import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NoteEditorPageClient } from "./note-editor-page-client";
import {
  completeProductOnboarding,
  copyNote,
  createPremiumCheckoutSession,
  createNote,
  createStudyPackFromNote,
  extractNoteTextFromFile,
  generateNoteFromTopic,
  getBillingPricing,
  getMe,
  getMyPlan,
  getNote,
  listCoursePrograms,
  listSubjects,
  updateNote,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";

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
  getCurrentPathWithQuery: () => `${window.location.pathname}${window.location.search}`,
  getSafeRedirectPath: (path: string | null | undefined) => (
    path && path.startsWith("/") && !path.startsWith("//") ? path : null
  ),
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/checkout-redirect", () => ({
  redirectToCheckoutUrl: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeProductOnboarding: jest.fn(),
  copyNote: jest.fn(),
  createPremiumCheckoutSession: jest.fn(),
  createNote: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  extractNoteTextFromFile: jest.fn(),
  generateNoteFromTopic: jest.fn(),
  getBillingPricing: jest.fn(),
  getMe: jest.fn(),
  getMyPlan: jest.fn(),
  getNote: jest.fn(),
  isNoteGenerationLimitReachedError: (error: unknown) => error instanceof Error && error.message === "NOTE_GENERATION_LIMIT_REACHED",
  listCoursePrograms: jest.fn(),
  listSubjects: jest.fn(),
  isEmailNotVerifiedError: (error: unknown) => error instanceof Error && error.message === "EMAIL_VERIFICATION_REQUIRED",
  isOcrLimitReachedError: (error: unknown) => error instanceof Error && error.message === "OCR_LIMIT_REACHED",
  trackAnalyticsEvent: jest.fn(),
  updateNote: jest.fn(),
}));

const baseNote = {
  id: "note-1",
  title: "Draft Note",
  subject: "Biology",
  courseProgram: "Nursing",
  targetProfileType: "STUDENT" as const,
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
  generatedQuiz: null,
  quizCount: 0,
  quickReviewAvailable: false,
  challengeQuizAvailable: false,
  adaptivePracticeAvailable: false,
  difficultySelectionAvailable: false,
};

async function selectImportNotesMode() {
  fireEvent.click(await screen.findByRole("button", { name: /Import notes/i }));
}

describe("NoteEditorPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    window.localStorage.clear();
    window.sessionStorage.clear();
    (createStudyPackFromNote as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (copyNote as jest.Mock).mockReset();
    (createNote as jest.Mock).mockReset();
    (extractNoteTextFromFile as jest.Mock).mockReset();
    (generateNoteFromTopic as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    (createPremiumCheckoutSession as jest.Mock).mockReset();
    (redirectToCheckoutUrl as jest.Mock).mockReset();
    (updateNote as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockResolvedValue(["Anatomy", "Biology", "Chemistry"]);
    (listCoursePrograms as jest.Mock).mockResolvedValue(["Nursing", "Senior High – STEM"]);
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
        noteGenerationsPerMonth: 5,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
        noteGenerationsUsed: 1,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
        noteGenerationsRemaining: 4,
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
    (createPremiumCheckoutSession as jest.Mock).mockResolvedValue({
      checkoutUrl: "https://checkout.xendit.test/invoice_123",
    });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
      courseProgram: "Nursing",
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
      ...baseNote,
      id: "note-created",
      studyPackStatus: "GENERATING",
    });
    (copyNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      id: "note-copy",
      studyPackStatus: "DRAFT",
    });
    (generateNoteFromTopic as jest.Mock).mockResolvedValue({
      content: "Generated topic note content",
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
    const courseProgramInput = screen.getByLabelText("Course / Program (optional)");
    const contentInput = screen.getByLabelText("Content");
    await waitFor(() => {
      expect(titleInput).toHaveValue("Draft Note");
      expect(subjectInput).toHaveValue("Biology");
      expect(courseProgramInput).toHaveValue("Nursing");
    });

    expect(titleInput).not.toBeDisabled();
    expect(subjectInput).not.toBeDisabled();
    expect(courseProgramInput).not.toBeDisabled();
    expect(contentInput).not.toHaveAttribute("readonly");
    expect(screen.getByRole("button", { name: /\+ Add Tag/i })).toBeInTheDocument();
    expect(screen.getByText("Select an existing subject or type your own.")).toBeInTheDocument();
    await waitFor(() => {
      expect(listSubjects).toHaveBeenCalledWith("mine");
      expect(listCoursePrograms).toHaveBeenCalledWith("mine");
    });

    fireEvent.click(screen.getByRole("button", { name: "Toggle subject suggestions" }));
    expect(await screen.findByRole("option", { name: "Anatomy" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Biology" })).toBeInTheDocument();
  });

  it("shows edit-note labels and actions for existing draft notes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<NoteEditorPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Edit Note" })).toBeInTheDocument();
    expect(screen.getByText("Update your note details and content.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save Note" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate Study Pack" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
    expect(screen.queryByText("Create or import your notes first, then generate a Study Pack when you are ready.")).not.toBeInTheDocument();
  });

  it("keeps Add details collapsed by default on the new note page", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    const addDetailsButton = await screen.findByRole("button", { name: "Add details (optional)" });
    expect(addDetailsButton).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByLabelText("Title (optional)")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Subject (optional)")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Course / Program (optional)")).not.toBeInTheDocument();
  });

  it("shows write, generate, and import start options on the new note page", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    expect(await screen.findByRole("button", { name: /Write your own note/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Generate from topic/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Import notes/i })).toBeInTheDocument();
  });

  it("shows optional metadata fields when Add details is expanded", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details (optional)" }));

    expect(await screen.findByLabelText("Title (optional)")).toBeInTheDocument();
    expect(screen.getByLabelText("Subject (optional)")).toBeInTheDocument();
    expect(screen.getByLabelText("Course / Program (optional)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /\+ Add Tag/i })).toBeInTheDocument();
  });

  it("expands Add details from the sticky helper link", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details like title, subject, course, or tags anytime." }));

    expect(await screen.findByLabelText("Title (optional)")).toBeInTheDocument();
    expect(screen.getByLabelText("Subject (optional)")).toBeInTheDocument();
  });

  it("defaults create-note course/program from the user profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    const addDetailsButton = await screen.findByRole("button", { name: "Add details (optional)" });
    fireEvent.click(addDetailsButton);

    const courseProgramInput = await screen.findByLabelText("Course / Program (optional)");
    await waitFor(() => {
      expect(courseProgramInput).toHaveValue("Nursing");
    });
    expect(getMe).toHaveBeenCalled();
    expect(
      screen.getByText("Enter your degree like Engineering, Nursing, Accountancy, etc. This note can use a different value from your profile."),
    ).toBeInTheDocument();
  });

  it("returns to note detail after saving changes in edit mode", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });
    (updateNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      id: "note-1",
      title: "Updated title",
      studyPackStatus: "DRAFT",
    });

    render(<NoteEditorPageClient noteId="note-1" />);

    const titleInput = await screen.findByLabelText("Title (optional)");
    fireEvent.change(titleInput, { target: { value: "Updated title" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        title: "Updated title",
      }));
      expect(pushMock).toHaveBeenCalledWith("/notes/note-1?saved=1");
    });
  });

  it("renders the note back link in edit mode", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<NoteEditorPageClient noteId="note-1" />);

    expect(await screen.findByRole("link", { name: "Note" })).toHaveAttribute("href", "/notes/note-1");
  });

  it("saves a custom subject even when it is not in backend suggestions", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details (optional)" }));

    const subjectInput = await screen.findByLabelText("Subject (optional)");
    const contentInput = screen.getByLabelText("Content");
    await waitFor(() => {
      expect(listSubjects).toHaveBeenCalledWith("mine");
    });
    fireEvent.change(subjectInput, { target: { value: "Microbiology Lab" } });
    fireEvent.change(contentInput, { target: { value: "Custom subject note" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalled();
    });
    expect((createNote as jest.Mock).mock.calls[0][0]).toEqual(expect.objectContaining({
      subject: "Microbiology Lab",
      courseProgram: "Nursing",
      targetProfileType: "STUDENT",
    }));
  });

  it("saves a new note without expanding Add details", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    fireEvent.change(await screen.findByLabelText("Content"), { target: { value: "Simple note content" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalledWith(expect.objectContaining({
        title: null,
        subject: null,
        courseProgram: "Nursing",
        tags: [],
        targetProfileType: "STUDENT",
        content: "Simple note content",
      }));
      expect(pushMock).toHaveBeenCalledWith("/notes/note-created");
    });
  });

  it("saves the note, starts generation, and redirects without requiring Add details", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (createNote as jest.Mock).mockResolvedValueOnce({
      ...baseNote,
      id: "note-created",
      title: "",
      subject: null,
      courseProgram: "Nursing",
      content: "Cell structure content",
    });

    render(<NoteEditorPageClient />);

    fireEvent.change(screen.getByLabelText("Content"), { target: { value: "Cell structure content" } });

    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalledWith(expect.objectContaining({
        title: null,
        subject: null,
        courseProgram: "Nursing",
        tags: [],
        targetProfileType: "STUDENT",
      }));
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-created");
      expect(pushMock).toHaveBeenCalledWith("/notes/note-created?from=notes&generating=1&tab=summary");
    });
    expect(updateNote).not.toHaveBeenCalled();
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

  it("supports generating a note draft from a topic before saving", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByText("Generate from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), { target: { value: "Newton's Laws of Motion" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate Note" }));

    await waitFor(() => {
      expect(generateNoteFromTopic).toHaveBeenCalledWith("Newton's Laws of Motion");
      expect(screen.getByLabelText("Content")).toHaveValue("Generated topic note content");
    });
    fireEvent.click(screen.getByRole("button", { name: "Add details (optional)" }));
    expect(screen.getByLabelText("Title (optional)")).toHaveValue("Newton's Laws of Motion");
    expect(screen.getByRole("button", { name: "Generate Again" })).toBeInTheDocument();
    expect(
      screen.getByText("Not quite right? Try refining your topic before generating again."),
    ).toBeInTheDocument();
  });

  it("resets the topic-generation button label when the topic is cleared", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByText("Generate from topic"));
    const topicInput = screen.getByLabelText("Topic");

    fireEvent.change(topicInput, { target: { value: "Newton's Laws of Motion" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate Note" }));

    await screen.findByRole("button", { name: "Generate Again" });

    fireEvent.change(topicInput, { target: { value: "" } });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Generate Note" })).toBeInTheDocument();
    });
    expect(
      screen.queryByText("Not quite right? Try refining your topic before generating again."),
    ).not.toBeInTheDocument();
  });

  it("shows repeat-generation loading feedback without adding another action", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    let resolveSecondGeneration: ((value: { content: string }) => void) | null = null;
    const secondGenerationPromise = new Promise<{ content: string }>((resolve) => {
      resolveSecondGeneration = resolve;
    });
    (generateNoteFromTopic as jest.Mock)
      .mockResolvedValueOnce({ content: "First generated topic note" })
      .mockReturnValueOnce(secondGenerationPromise);

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByText("Generate from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), { target: { value: "Newton's Laws of Motion" } });

    fireEvent.click(screen.getByRole("button", { name: "Generate Note" }));
    await screen.findByDisplayValue("First generated topic note");

    fireEvent.click(screen.getByRole("button", { name: "Generate Again" }));

    expect(screen.getByRole("button", { name: "Generating..." })).toBeInTheDocument();
    expect(screen.getByText("Creating a new version...")).toBeInTheDocument();

    resolveSecondGeneration?.({ content: "Second generated topic note" });

    await screen.findByDisplayValue("Second generated topic note");
    expect(screen.getAllByRole("button", { name: "Generate Again" })).toHaveLength(1);
  });

  it("disables topic note generation at the free plan limit and opens the paywall", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1", planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
        noteGenerationsPerMonth: 5,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
        noteGenerationsUsed: 5,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
        noteGenerationsRemaining: 0,
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByText("Generate from topic"));

    expect(screen.getByText("You've reached your topic note generation limit for this month.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate Note" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Upgrade to Plus" }));

    expect(await screen.findByText("You’ve reached your note generation limit")).toBeInTheDocument();
  });

  it("uses the student generate label and helper text by default", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "STUDENT" });

    render(<NoteEditorPageClient />);

    expect(await screen.findByRole("button", { name: "Generate Study Pack" })).toBeInTheDocument();
    expect(screen.getByText("Save your note or generate a Study Pack when ready.")).toBeInTheDocument();
  });

  it("uses the teacher generate label and helper text for teacher note creation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });

    render(<NoteEditorPageClient initialMode="quiz" />);

    expect(await screen.findByRole("button", { name: "Generate Study Pack" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add details (optional)" }));
    expect(screen.getByLabelText("Who is this note for?")).toHaveValue("");
    expect(screen.getByText("Choose the learner audience for this note.")).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Teacher" })).not.toBeInTheDocument();
  });

  it("requires teachers to select an audience before saving or generating", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });

    render(<NoteEditorPageClient initialMode="quiz" />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Teacher note content" } });

    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("Please select an audience")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add details (optional)" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByLabelText("Who is this note for?")).toBeInTheDocument();
    expect(createNote).not.toHaveBeenCalled();
    expect(createStudyPackFromNote).not.toHaveBeenCalled();
  });

  it("uses the board exam generate label and helper text for board exam users", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "BOARD_EXAM" });

    render(<NoteEditorPageClient />);

    expect(await screen.findByRole("button", { name: "Generate Study Pack" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add details (optional)" }));
    expect(screen.queryByLabelText("Who is this note for?")).not.toBeInTheDocument();
  });

  it("shows target audience inside Add details for admin note creation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", role: "ADMIN" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details (optional)" }));
    expect(screen.getByLabelText("Who is this note for?")).toBeInTheDocument();
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
      screen.getByText("Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, and tags."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save Note" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Make a Copy" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Generate Study Pack" })).not.toBeInTheDocument();
  });

  it("keeps make-a-copy available from generated-note edit mode", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      id: "note-generated",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
    });

    render(<NoteEditorPageClient noteId="note-generated" />);

    fireEvent.click(await screen.findByRole("button", { name: "Make a Copy" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-generated");
      expect(pushMock).toHaveBeenCalledWith("/notes/note-copy?copied=1");
    });
  });

  it("keeps import available for unverified users while Generate stays disabled", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: null });

    render(<NoteEditorPageClient />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Some note content" } });

    expect(screen.getByRole("button", { name: "Generate Study Pack" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: /Generate from topic/i }));
    expect(screen.getByRole("button", { name: "Generate Note" })).toBeDisabled();

    await selectImportNotesMode();
    const uploadInput = document.getElementById("note-import-file") as HTMLInputElement | null;

    expect(uploadInput).not.toBeNull();
    expect(uploadInput).not.toBeDisabled();
  });

  it("shows the upgrade paywall modal for free users at their monthly Study Pack cap", async () => {
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
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("You’ve reached your study pack limit")).toBeInTheDocument();
  });

  it("shows the exact remaining Study Pack count in the near-limit banner", async () => {
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
        studyPacksUsed: 9,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 1,
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

    expect(await screen.findByText("You have 1 Study Pack left this month on the Free plan.")).toBeInTheDocument();
  });

  it("saves a draft note before starting checkout from the Study Pack paywall", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1", planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
        noteGenerationsPerMonth: 5,
      },
      usage: {
        studyPacksUsed: 10,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
        noteGenerationsUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 0,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
        noteGenerationsRemaining: 5,
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
    fireEvent.change(contentInput, { target: { value: "Saved before checkout" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));
    fireEvent.click(await screen.findByRole("button", { name: "Upgrade to Plus" }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalled();
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ planType: "PLUS", returnUrl: "/notes/note-created/edit" });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
    });
  });

  it("shows the import panel only when Import notes is selected", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    expect(document.getElementById("note-import-file")).toBeNull();

    await selectImportNotesMode();

    expect(document.getElementById("note-import-file")).toBeInTheDocument();
    expect(screen.getByText("Supported formats: PNG, JPG, JPEG, WEBP, TXT, PDF, DOCX.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Write your own note/i }));

    expect(document.getElementById("note-import-file")).toBeNull();
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
    await selectImportNotesMode();
    const uploadInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const image = new File(["fake"], "note.png", { type: "image/png" });
    fireEvent.change(uploadInput as HTMLInputElement, { target: { files: [image] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("Low confidence OCR text");
    });

    expect(
      screen.getByText("OCR may be inaccurate. Please review and edit the extracted text before saving or generating a Study Pack."),
    ).toBeInTheDocument();
    expect(screen.getByText("Text imported. Review and edit it before continuing.")).toBeInTheDocument();
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
    await selectImportNotesMode();
    const uploadInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const image = new File(["fake"], "note.png", { type: "image/png" });
    fireEvent.change(uploadInput as HTMLInputElement, { target: { files: [image] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("High confidence OCR text");
    });

    expect(screen.getByText("Text imported. Review and edit it before continuing.")).toBeInTheDocument();
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
    await selectImportNotesMode();
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
    await selectImportNotesMode();
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
    await selectImportNotesMode();
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
    await selectImportNotesMode();
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
    await selectImportNotesMode();
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
    await selectImportNotesMode();
    const firstInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["text"], "notes.txt", { type: "text/plain" });
    fireEvent.change(firstInput as HTMLInputElement, { target: { files: [file] } });

    expect(await screen.findAllByText("We couldn’t extract text from this file. Try another image or file.")).not.toHaveLength(0);
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
    await selectImportNotesMode();
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
    await selectImportNotesMode();
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["img"], "note.png", { type: "image/png" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    expect(await screen.findByText("OCR limit reached")).toBeInTheDocument();
    expect(screen.getByText(/You've reached your image-to-text limit for this month\./i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Upgrade to Plus" }));
    await waitFor(() => {
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
    });
  });

  it("shows the premium OCR limit modal without upgrade CTA", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PRO",
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
    await selectImportNotesMode();
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["img"], "note.png", { type: "image/png" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    expect(await screen.findByText("OCR limit reached")).toBeInTheDocument();
    expect(screen.getByText(/Your limits will reset on your next billing date\./i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Upgrade to Plus" })).not.toBeInTheDocument();
  });

  it("saves a new note after importing content", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockResolvedValue({
      inputType: "txt",
      extractedText: "Imported save-ready content",
      meta: { ocrConfidence: null, lowConfidence: false },
    });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    await selectImportNotesMode();
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["text"], "notes.txt", { type: "text/plain" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("Imported save-ready content");
    });

    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalledWith(expect.objectContaining({
        content: "Imported save-ready content",
      }));
      expect(pushMock).toHaveBeenCalledWith("/notes/note-created");
    });
  });

  it("generates a Study Pack after importing content", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockResolvedValue({
      inputType: "txt",
      extractedText: "Imported content for generation",
      meta: { ocrConfidence: null, lowConfidence: false },
    });

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    await selectImportNotesMode();
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["text"], "notes.txt", { type: "text/plain" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Content")).toHaveValue("Imported content for generation");
    });

    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalledWith(expect.objectContaining({
        content: "Imported content for generation",
      }));
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-created");
      expect(pushMock).toHaveBeenCalledWith("/notes/note-created?from=notes&generating=1&tab=summary");
    });
  });

  it("redirects queued quiz-focused generation to the practice quiz section", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });

    render(<NoteEditorPageClient initialMode="quiz" />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Generated from teacher flow" } });
    fireEvent.click(screen.getByRole("button", { name: "Add details (optional)" }));
    fireEvent.change(screen.getByLabelText("Who is this note for?"), { target: { value: "STUDENT" } });

    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/notes/note-created?from=notes&generating=1&tab=quiz");
    });
  });

  it("redirects queued student note generation to the summary view by default", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "STUDENT" });

    render(<NoteEditorPageClient />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Student note content" } });

    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/notes/note-created?from=notes&generating=1&tab=summary");
    });
  });

  it("focuses the upload input when opened in upload mode", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });

    render(<NoteEditorPageClient initialSource="upload" />);

    expect(await screen.findByRole("heading", { name: "Upload Material" })).toBeInTheDocument();

    await waitFor(() => {
      expect(document.getElementById("note-import-file")).toHaveFocus();
    });
  });
});
