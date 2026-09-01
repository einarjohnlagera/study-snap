import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NoteEditorPageClient } from "./note-editor-page-client";
import {
  completeProductOnboarding,
  copyNote,
  createPremiumCheckoutSession,
  createNote,
  createStudyPackFromNote,
  extractNoteTextFromFile,
  generateNoteFromTopic,
  getCourseProgramCatalog,
  getBillingPricing,
  getMe,
  getMyPlan,
  getNote,
  getNoteApplicablePrograms,
  listCoursePrograms,
  listSubjects,
  replaceNoteApplicablePrograms,
  trackAnalyticsEvent,
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
  getCurrentPathWithQuery: () => `${globalThis.location.pathname}${globalThis.location.search}`,
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
  getCourseProgramCatalog: jest.fn(),
  getBillingPricing: jest.fn(),
  getMe: jest.fn(),
  getMyPlan: jest.fn(),
  getNote: jest.fn(),
  getNoteApplicablePrograms: jest.fn(),
  isNoteGenerationLimitReachedError: (error: unknown) => error instanceof Error && error.message === "NOTE_GENERATION_LIMIT_REACHED",
  listCoursePrograms: jest.fn(),
  listSubjects: jest.fn(),
  replaceNoteApplicablePrograms: jest.fn(),
  isEmailNotVerifiedError: (error: unknown) => error instanceof Error && error.message === "EMAIL_VERIFICATION_REQUIRED",
  isOcrLimitReachedError: (error: unknown) => error instanceof Error && error.message === "OCR_LIMIT_REACHED",
  isOcrDisabledError: (error: unknown) => (
    error instanceof Error && (error as Error & { code?: string }).code === "OCR_DISABLED"
  ),
  trackAnalyticsEvent: jest.fn(),
  updateNote: jest.fn(),
}));

const baseNote = {
  id: "note-1",
  title: "Draft Note",
  subject: "Biology",
  courseProgram: "Nursing",
  domainContext: null,
  learnerLevel: null,
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
};

async function selectImportNotesMode() {
  fireEvent.click(await screen.findByRole("button", { name: /Import notes/i }));
}

describe("NoteEditorPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    globalThis.localStorage.clear();
    globalThis.sessionStorage.clear();
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
    (getCourseProgramCatalog as jest.Mock).mockReset();
    (getNoteApplicablePrograms as jest.Mock).mockReset();
    (replaceNoteApplicablePrograms as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    (createPremiumCheckoutSession as jest.Mock).mockReset();
    (redirectToCheckoutUrl as jest.Mock).mockReset();
    (updateNote as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockResolvedValue(["Anatomy", "Biology", "Chemistry"]);
    (listCoursePrograms as jest.Mock).mockResolvedValue(["Nursing", "Senior High – STEM"]);
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([
      { id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null },
      { id: "program-pharmacy", name: "Pharmacy", programFamilyId: null, programFamilyName: null },
    ]);
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      programs: [{ id: "program-nursing", name: "Nursing" }],
      courseProgramShadowed: false,
    });
    (replaceNoteApplicablePrograms as jest.Mock).mockImplementation(async (_noteId: string, ids: string[]) => (
      ids.map((id) => ({ id, name: id === "program-pharmacy" ? "Pharmacy" : "Nursing" }))
    ));
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
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getBillingPricing as jest.Mock).mockResolvedValue({
      region: "PH",
      currency: "PHP",
      plus: {
        planType: "PLUS",
        monthly: {
          amount: 179,
          durationDays: 30,
          introAmount: 149,
          introEligible: true,
          available: true,
        },
        yearly: {
          amount: 1790,
          durationDays: 365,
          introAmount: null,
          introEligible: false,
          available: false,
        },
      },
      pro: {
        planType: "PRO",
        monthly: {
          amount: 249,
          durationDays: 30,
          introAmount: 199,
          introEligible: true,
          available: true,
        },
        yearly: {
          amount: 2490,
          durationDays: 365,
          introAmount: null,
          introEligible: false,
          available: false,
        },
      },
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
    const courseProgramInput = screen.getByLabelText(/Course \/ Program/);
    const contentInput = screen.getByLabelText("Content");
    await waitFor(() => {
      expect(titleInput).toHaveValue("Draft Note");
      expect(subjectInput).toHaveValue("Biology");
      expect(courseProgramInput).toHaveValue("Nursing");
    });

    expect(titleInput).not.toBeDisabled();
    expect(subjectInput).not.toBeDisabled();
    expect(courseProgramInput).not.toBeDisabled();
    expect(subjectInput).toHaveAttribute("maxLength", "64");
    expect(courseProgramInput).toHaveAttribute("maxLength", "120");
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
    expect(screen.queryByText("Import notes")).not.toBeInTheDocument();
    expect(document.getElementById("note-import-file")).toBeNull();
  });

  it("keeps Add details collapsed by default on the new note page", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    const addDetailsButton = await screen.findByRole("button", { name: "Add details" });
    expect(addDetailsButton).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByLabelText("Title (optional)")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Subject (optional)")).not.toBeInTheDocument();
    // Assert on the input id: querying by label text returned nothing either way once the label
    // stopped carrying htmlFor, so the old assertion could not fail.
    expect(document.querySelector("#note-course-program")).toBeNull();
  });

  it("shows write, generate, and import start options on the new note page", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    expect(await screen.findByRole("button", { name: /Write your own note/i })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Create from topic/i }));
    expect(screen.getByRole("link", { name: /Generate them all at once/i })).toHaveAttribute(
      "href",
      "/library/bulk-generate",
    );
    expect(screen.getByRole("button", { name: /Import notes/i })).toBeInTheDocument();
  });

  it("shows optional metadata fields when Add details is expanded", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));

    expect(await screen.findByLabelText("Title (optional)")).toBeInTheDocument();
    expect(screen.getByLabelText("Subject (optional)")).toBeInTheDocument();
    expect(screen.getByLabelText(/Course \/ Program/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /\+ Add Tag/i })).toBeInTheDocument();
  });

  it("expands Add details from the sticky helper link", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: null,
      courseProgram: null,
    });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details like title, subject, course, or tags anytime." }));

    expect(await screen.findByLabelText("Title (optional)")).toBeInTheDocument();
    expect(screen.getByLabelText("Subject (optional)")).toBeInTheDocument();
  });

  it("defaults create-note course/program from the user profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    const addDetailsButton = await screen.findByRole("button", { name: "Add details" });
    fireEvent.click(addDetailsButton);

    const courseProgramInput = await screen.findByLabelText(/Course \/ Program/);
    await waitFor(() => {
      expect(courseProgramInput).toHaveValue("Nursing");
    });
    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));
    const courseProgramListbox = screen.getByRole("listbox");
    await within(courseProgramListbox).findByRole("option", { name: "Pharmacy" });
    expect(within(courseProgramListbox).getAllByRole("option").slice(0, 2).map((option) => option.textContent)).toEqual([
      "Nursing",
      "Pharmacy",
    ]);
    expect(getMe).toHaveBeenCalled();
    expect(
      screen.getByText("Choose or type the course, strand, field, or topic that fits best. This note can use a different value from your profile."),
    ).toBeInTheDocument();
  });

  it("defaults create-note authored depth from the user profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "BOARD_EXAM_REVIEW", courseProgram: "Nursing" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));

    await waitFor(() => {
      expect(screen.getByLabelText("Authored Depth (optional)")).toHaveValue("BOARD_EXAM_REVIEW");
    });
  });

  it("does not submit any authored depth when a learner creates a note", async () => {
    // The Authored Depth control renders for curators only. Pre-filling it for a learner
    // would persist a depth they never saw — a client-side default write, which ADR-001
    // constraint 2 forbids. The learner path is the one that was previously untested.
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE", courseProgram: "Nursing" });

    render(<NoteEditorPageClient />);

    fireEvent.change(await screen.findByLabelText("Content"), { target: { value: "Learner note" } });
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    expect(screen.queryByLabelText("Authored Depth (optional)")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalledWith(expect.objectContaining({ learnerLevel: null }));
    });
  });

  it("round-trips a stored authored depth when a learner edits a note that has one", async () => {
    // Regression guard for the WRONG fix to the above: gating the request payload instead
    // of the pre-fill would null this note's stored depth on save, because PUT /notes/{id}
    // is a full replace. docs/features/notes.md — "hiding a field must never null it."
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE", courseProgram: "Nursing" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      learnerLevel: "BOARD_EXAM_REVIEW",
    });

    render(<NoteEditorPageClient noteId="note-1" />);

    await screen.findByLabelText("Content");
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith(
        "note-1",
        expect.objectContaining({ learnerLevel: "BOARD_EXAM_REVIEW" }),
      );
    });
  });

  it("does not prefill authored depth from the profile when editing an existing note", async () => {
    // CREATE ONLY, and this guard is the point: a depth change on an already-generated
    // note strands its Challenge-bank rows at the old level, which is the same reason
    // v0.75.0 rejected align-on-add. The profile must never reach an existing note.
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "BOARD_EXAM_REVIEW", courseProgram: "Nursing" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      learnerLevel: null,
    });

    render(<NoteEditorPageClient noteId="note-1" />);

    // Edit mode renders the detail fields directly — there is no "Add details" step.
    const depthSelect = await screen.findByLabelText("Authored Depth (optional)");
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    expect(depthSelect).toHaveValue("");
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

  it("does not track an unchanged Course / Program when saving in edit mode", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });
    (updateNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<NoteEditorPageClient noteId="note-1" />);

    await screen.findByDisplayValue("Draft Note");
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => expect(updateNote).toHaveBeenCalled());
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(
      expect.objectContaining({ eventType: "COURSE_PROGRAM_VALUE_SELECTED" }),
    );
  });

  it("does not submit the profile Course/Program when editing a note that has none", async () => {
    // Regression: profileCourseProgram was populated unconditionally while the isEditMode guard
    // gated only the draft prefill, so editing a null-program note rendered an empty field and
    // silently submitted the editor's own profile program. Profile context may assist creation;
    // it must never become an existing note's persisted metadata (ADR-001).
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
      courseProgram: "Architecture",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      courseProgram: null,
      studyPackStatus: "DRAFT",
    });

    render(<NoteEditorPageClient noteId="note-1" />);

    const courseProgramInput = await screen.findByLabelText(/Course \/ Program/);
    await waitFor(() => {
      expect(getMe).toHaveBeenCalled();
    });
    expect(courseProgramInput).toHaveValue("");

    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => {
      expect(screen.getByText(/Please complete: Course \/ Program\(s\)\./)).toBeInTheDocument();
    });
    expect(updateNote).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("shows copied applicable programs read-only and saves a shadowed learner note without a personal program", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      courseProgram: null,
      copiedFromNoteId: "source-note",
    });
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      programs: [
        { id: "program-civil", name: "Civil Engineering" },
        { id: "program-mechanical", name: "Mechanical Engineering" },
      ],
      courseProgramShadowed: true,
    });
    (updateNote as jest.Mock).mockResolvedValue({ ...baseNote, courseProgram: null });

    render(<NoteEditorPageClient noteId="note-1" />);

    expect(await screen.findByText("Civil Engineering · Mechanical Engineering")).toBeInTheDocument();
    expect(screen.getByText(
      "Set by the note this was copied from. Your own course or program is on your profile.",
    )).toBeInTheDocument();
    // Assert on the input id: querying by label text returned nothing either way once the label
    // stopped carrying htmlFor, so the old assertion could not fail.
    expect(document.querySelector("#note-course-program")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        courseProgramText: null,
      }));
    });
  });

  it("renders owner self-copy program names without copied provenance", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      courseProgram: null,
      sourceNoteId: "owner-source-note",
      copiedFromNoteId: null,
    });
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      programs: [{ id: "program-nursing", name: "Nursing" }],
      courseProgramShadowed: true,
    });

    render(<NoteEditorPageClient noteId="note-1" />);

    expect(await screen.findByText("Nursing", { selector: "p" })).toBeInTheDocument();
    expect(screen.queryByText(/Set by the note this was copied from/)).not.toBeInTheDocument();
    // Assert on the input id: querying by label text returned nothing either way once the label
    // stopped carrying htmlFor, so the old assertion could not fail.
    expect(document.querySelector("#note-course-program")).toBeNull();
  });

  it("does not block a learner save when applicable-program provenance fails to load", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, courseProgram: null });
    (getNoteApplicablePrograms as jest.Mock).mockRejectedValue(new Error("Could not load Course / Program(s)."));
    (updateNote as jest.Mock).mockResolvedValue({ ...baseNote, courseProgram: null });

    render(<NoteEditorPageClient noteId="note-1" />);

    expect(await screen.findByText("Could not load Course / Program(s)." )).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => expect(updateNote).toHaveBeenCalled());
  });

  it("saves an edited note using its own Course/Program, not the editor's profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
      courseProgram: "Architecture",
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });
    (updateNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<NoteEditorPageClient noteId="note-1" />);

    const titleInput = await screen.findByLabelText("Title (optional)");
    await waitFor(() => {
      expect(getMe).toHaveBeenCalled();
    });
    fireEvent.change(titleInput, { target: { value: "Updated title" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        courseProgramText: "Nursing",
      }));
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

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));

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
      courseProgramText: "Nursing",
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
        courseProgramText: "Nursing",
        domainContext: null,
        // This fixture is a LEARNER (no profileType), and the Authored Depth control
        // renders for curators only. A learner must therefore submit no depth at all —
        // pre-filling a field they cannot see would persist a value nobody chose.
        // See the named learner/curator pair of tests below, which assert this directly.
        learnerLevel: null,
        tags: [],
        content: "Simple note content",
      }));
      expect(pushMock).toHaveBeenCalledWith("/notes/note-created");
      // The learner never opened "Add details", so the program is the profile pre-fill the create
      // path seeded — a replay, not a selection. Emitting here would swamp the metric: learner notes
      // run ~5x the entire learner-profile population per month.
      expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(
        expect.objectContaining({ eventType: "COURSE_PROGRAM_VALUE_SELECTED" }),
      );
    });
  });

  it("shows a save error inline without clearing note content or authoring metadata", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (createNote as jest.Mock).mockRejectedValueOnce(
      new Error("domainContext must be one of the supported values."),
    );

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    fireEvent.change(screen.getByLabelText("Domain Context (optional)"), {
      target: { value: "ENGINEERING_MATHEMATICS" },
    });
    expect(screen.getByText(/Engineering Economics/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Authored Depth (optional)"), {
      target: { value: "COLLEGE" },
    });
    fireEvent.change(screen.getByLabelText("Content"), {
      target: { value: "A long engineering algebra note remains intact." },
    });
    const programInput = screen.getByLabelText("Add a course or program");
    fireEvent.change(programInput, { target: { value: "Nursing" } });
    fireEvent.click(await screen.findByRole("option", { name: "Nursing" }));
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("domainContext");
    expect(screen.getByLabelText("Content")).toHaveValue(
      "A long engineering algebra note remains intact.",
    );
    expect(screen.getByLabelText("Domain Context (optional)")).toHaveValue("ENGINEERING_MATHEMATICS");
    expect(screen.getByLabelText("Authored Depth (optional)")).toHaveValue("COLLEGE");
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
        courseProgramText: "Nursing",
        tags: [],
      }));
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-created");
      expect(pushMock).toHaveBeenCalledWith("/notes/note-created?from=notes&generating=1&tab=summary");
    });
    expect(updateNote).not.toHaveBeenCalled();
  });

  it("shows the first-study hint on the create note page when onboarding is in progress", async () => {
    globalThis.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "create-note" }));
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

    fireEvent.click(await screen.findByText("Create from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), { target: { value: "Newton's Laws of Motion" } });
    fireEvent.click(screen.getByRole("button", { name: "Create a Note" }));

    await waitFor(() => {
      expect(generateNoteFromTopic).toHaveBeenCalledWith("Newton's Laws of Motion", "Nursing");
      expect(screen.getByLabelText("Content")).toHaveValue("Generated topic note content");
    });
    fireEvent.click(screen.getByRole("button", { name: "Add details" }));
    expect(screen.getByLabelText("Title (optional)")).toHaveValue("Newton's Laws of Motion");
    expect(screen.getByRole("button", { name: "Create Again" })).toBeInTheDocument();
    expect(
      screen.getByText("Not quite right? Try refining your topic before creating again."),
    ).toBeInTheDocument();
  });

  it("uses the selected Course/Program on the first topic note generation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
      courseProgram: "Software Engineering",
    });
    (listCoursePrograms as jest.Mock).mockResolvedValue(["Software Engineering", "Civil Engineering"]);

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    const courseProgramInput = await screen.findByLabelText(/Course \/ Program/);
    await waitFor(() => {
      expect(courseProgramInput).toHaveValue("Software Engineering");
    });
    fireEvent.change(courseProgramInput, { target: { value: "Civil Engineering" } });

    fireEvent.click(await screen.findByText("Create from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), { target: { value: "Bridge Load Distribution" } });

    expect(screen.getByText(/Tailored for: Civil Engineering/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Create a Note" }));

    await waitFor(() => {
      expect(generateNoteFromTopic).toHaveBeenCalledWith("Bridge Load Distribution", "Civil Engineering");
    });
  });

  it("uses the latest changed Course/Program before clicking Generate Note", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
      courseProgram: "Software Engineering",
    });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    const courseProgramInput = await screen.findByLabelText(/Course \/ Program/);
    await waitFor(() => {
      expect(courseProgramInput).toHaveValue("Software Engineering");
    });
    fireEvent.change(courseProgramInput, { target: { value: "Civil Engineering" } });
    fireEvent.change(courseProgramInput, { target: { value: "Mechanical Engineering" } });

    fireEvent.click(await screen.findByText("Create from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), { target: { value: "Torque and Rotation" } });
    fireEvent.click(screen.getByRole("button", { name: "Create a Note" }));

    await waitFor(() => {
      expect(generateNoteFromTopic).toHaveBeenCalledWith("Torque and Rotation", "Mechanical Engineering");
    });
  });

  it("keeps the selected Course/Program for topic note regeneration", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
      courseProgram: "Software Engineering",
    });
    (generateNoteFromTopic as jest.Mock)
      .mockResolvedValueOnce({ content: "First generated topic note" })
      .mockResolvedValueOnce({ content: "Second generated topic note" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    const courseProgramInput = await screen.findByLabelText(/Course \/ Program/);
    await waitFor(() => {
      expect(courseProgramInput).toHaveValue("Software Engineering");
    });
    fireEvent.change(courseProgramInput, { target: { value: "Civil Engineering" } });

    fireEvent.click(await screen.findByText("Create from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), { target: { value: "Bridge Load Distribution" } });
    fireEvent.click(screen.getByRole("button", { name: "Create a Note" }));

    await screen.findByDisplayValue("First generated topic note");
    fireEvent.click(screen.getByRole("button", { name: "Create Again" }));

    await waitFor(() => {
      expect(generateNoteFromTopic).toHaveBeenNthCalledWith(1, "Bridge Load Distribution", "Civil Engineering");
      expect(generateNoteFromTopic).toHaveBeenNthCalledWith(2, "Bridge Load Distribution", "Civil Engineering");
    });
  });

  it("uses profile Course/Program when topic generation has no selected override", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
      courseProgram: "Software Engineering",
    });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    await waitFor(() => {
      expect(screen.getByLabelText(/Course \/ Program/)).toHaveValue("Software Engineering");
    });

    fireEvent.click(await screen.findByText("Create from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), { target: { value: "Binary Search Trees" } });
    fireEvent.click(screen.getByRole("button", { name: "Create a Note" }));

    await waitFor(() => {
      expect(generateNoteFromTopic).toHaveBeenCalledWith("Binary Search Trees", "Software Engineering");
    });
  });

  it("resets the topic-generation button label when the topic is cleared", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByText("Create from topic"));
    const topicInput = screen.getByLabelText("Topic");

    fireEvent.change(topicInput, { target: { value: "Newton's Laws of Motion" } });
    fireEvent.click(screen.getByRole("button", { name: "Create a Note" }));

    await screen.findByRole("button", { name: "Create Again" });

    fireEvent.change(topicInput, { target: { value: "" } });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Create a Note" })).toBeInTheDocument();
    });
    expect(
      screen.queryByText("Not quite right? Try refining your topic before creating again."),
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

    fireEvent.click(await screen.findByText("Create from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), { target: { value: "Newton's Laws of Motion" } });

    fireEvent.click(screen.getByRole("button", { name: "Create a Note" }));
    await screen.findByDisplayValue("First generated topic note");

    fireEvent.click(screen.getByRole("button", { name: "Create Again" }));

    expect(screen.getByRole("button", { name: "Creating..." })).toBeInTheDocument();
    expect(screen.getByText("Creating a new version...")).toBeInTheDocument();

    resolveSecondGeneration!({ content: "Second generated topic note" });

    await screen.findByDisplayValue("Second generated topic note");
    expect(screen.getAllByRole("button", { name: "Create Again" })).toHaveLength(1);
  });

  it("keeps topic note generation clickable at the free plan limit and opens the paywall", async () => {
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
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByText("Create from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), {
      target: { value: "Photosynthesis" },
    });

    expect(screen.getByRole("button", { name: "Create a Note" })).toBeEnabled();

    fireEvent.click(screen.getByRole("button", { name: "Create a Note" }));

    expect(await screen.findByText("You've reached your topic note limit")).toBeInTheDocument();
    expect(screen.getByText("More topic notes means more of your library is ready when you sit down to study.")).toBeInTheDocument();
  });

  it("uses the student generate label and helper text by default", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "STUDENT" });

    render(<NoteEditorPageClient />);

    expect(await screen.findByRole("button", { name: "Generate Study Pack" })).toBeInTheDocument();
    expect(screen.getByText("Save your note or generate a Study Pack when ready.")).toBeInTheDocument();
  });

  // Subject is surfaced in the always-visible sticky bar rather than moved up the form, so authors
  // learn it exists and is editable without "Add details" being expanded by default. Naming it as
  // missing is the point -- a silent omission teaches nothing.
  it("names Subject in the sticky bar, including when it is not set yet", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });

    render(<NoteEditorPageClient />);

    expect(await screen.findByText(/Tailored for: Nursing · no subject yet/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Add details" }));
    fireEvent.change(await screen.findByLabelText(/Subject/), { target: { value: "Algebra" } });

    expect(screen.getByText(/Tailored for: Nursing · Algebra/)).toBeInTheDocument();
  });

  // A curator's Course / Program(s) must pre-fill from their profile on a NEW note, the same way the
  // single-valued field always did. Slice 4 seeded only the learner free-text draft, so curators saw
  // "No course programs selected" while the footer read "Tailored for: Nursing".
  it("preselects the profile course/program for a curator creating a note", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue([]);

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));

    expect(await screen.findByRole("button", { name: "Remove Nursing" })).toBeInTheDocument();
    expect(screen.queryByText("No course programs selected.")).not.toBeInTheDocument();
  });

  // C5. The sticky bar read `resolvedCourseProgram` -- the LEARNER free-text axis. For a curator that is
  // empty by design (the backend nulls it) so it fell back to the PROFILE program, and the bar claimed
  // "Tailored for: Software Engineering" while zero programs were selected. The "Add details" nag was
  // gated on the same value, so it stayed hidden too: both signals said a required field was done, and
  // Save then failed on exactly that field.
  it("does not claim a curator note is tailored to the profile program when no programs are selected", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: null, courseProgram: "Software Engineering" });

    render(<NoteEditorPageClient />);

    await screen.findByRole("button", { name: "Add details" });

    expect(screen.queryByText(/Tailored for: Software Engineering/)).not.toBeInTheDocument();
  });

  it("saves several programs without Domain Context because readiness is checked at generation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue([]);

    render(<NoteEditorPageClient />);

    fireEvent.change(await screen.findByLabelText("Content"), {
      target: { value: "Some note content long enough to save." },
    });
    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    // Profile pre-fills Nursing; adding Pharmacy makes it a multi-program note with no Domain Context.
    fireEvent.change(await screen.findByLabelText("Add a course or program"), { target: { value: "Pharmacy" } });
    expect(await screen.findByRole("button", { name: "Remove Pharmacy" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Nursing" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => expect(createNote).toHaveBeenCalledWith(expect.objectContaining({
      courseProgramIds: expect.arrayContaining(["program-nursing", "program-pharmacy"]),
      domainContext: null,
    })));
  });

  it("does not preselect a profile program the catalog does not carry", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: null, courseProgram: "Software Engineering" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));

    expect(await screen.findByLabelText("Add a course or program")).toBeInTheDocument();
    // C8: the empty state now explains itself when the profile programme is off-catalog, instead of a
    // bare "No course programs selected." that leaves a curator unable to see why theirs counts for nothing.
    expect(screen.getByText(/No course programs selected\./)).toBeInTheDocument();
    expect(screen.getByText(/not in the shared catalog/)).toBeInTheDocument();
  });

  it("uses the teacher generate label and helper text for teacher note creation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([
      { id: "program-civil", name: "Civil Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
      { id: "program-electrical", name: "Electrical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
      { id: "program-mechanical", name: "Mechanical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
    ]);

    render(<NoteEditorPageClient initialMode="quiz" />);

    expect(await screen.findByRole("button", { name: "Generate Study Pack" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add details" }));
    expect(screen.queryByLabelText("Who is this note for?")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Domain Context (optional)")).toBeInTheDocument();
    expect(screen.getByLabelText("Authored Depth (optional)")).toBeInTheDocument();
    expect(await screen.findByLabelText("Add a course or program")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add all 3 Engineering programs" })).toBeInTheDocument();
  });

  it("keeps the editor usable when the Course / Program(s) catalog fails", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getCourseProgramCatalog as jest.Mock).mockRejectedValue(new Error("Catalog unavailable"));

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    expect(await screen.findByText("Catalog unavailable")).toBeInTheDocument();
    expect(screen.getByLabelText("Add a course or program")).toBeDisabled();
    expect(screen.getByLabelText("Content")).toBeEnabled();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("uses the board exam generate label and helper text for board exam users", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "BOARD_EXAM" });

    render(<NoteEditorPageClient />);

    expect(await screen.findByRole("button", { name: "Generate Study Pack" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add details" }));
    expect(screen.queryByLabelText("Who is this note for?")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Domain Context (optional)")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Authored Depth (optional)")).not.toBeInTheDocument();
    expect(document.querySelector("#note-applicable-programs")).toBeNull();
  });

  it("keeps curator authoring metadata hidden for professional note creation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "PROFESSIONAL" });

    render(<NoteEditorPageClient />);

    expect(await screen.findByRole("button", { name: "Generate Study Pack" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add details" }));
    expect(screen.queryByLabelText("Who is this note for?")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Domain Context (optional)")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Authored Depth (optional)")).not.toBeInTheDocument();
    expect(document.querySelector("#note-applicable-programs")).toBeNull();
  });

  it("shows durable authoring metadata without Target Audience for admin note creation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z", role: "ADMIN" });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    expect(screen.queryByLabelText("Who is this note for?")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Domain Context (optional)")).toBeInTheDocument();
    expect(screen.getByLabelText("Authored Depth (optional)")).toBeInTheDocument();
  });

  it("keeps authoring metadata hidden for student note creation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    expect(screen.queryByLabelText("Domain Context (optional)")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Authored Depth (optional)")).not.toBeInTheDocument();
  });

  it("hydrates and saves existing authoring metadata for a teacher", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      domainContext: "NURSING",
      learnerLevel: "COLLEGE",
    });
    (updateNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      domainContext: "ENGINEERING_SCIENCES",
      learnerLevel: "BOARD_EXAM_REVIEW",
    });

    render(<NoteEditorPageClient noteId="note-1" />);

    expect(await screen.findByLabelText("Domain Context (optional)")).toHaveValue("NURSING");
    expect(screen.getByLabelText("Authored Depth (optional)")).toHaveValue("COLLEGE");
    expect(await screen.findByRole("button", { name: "Remove Nursing" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Domain Context (optional)"), {
      target: { value: "ENGINEERING_SCIENCES" },
    });
    fireEvent.change(screen.getByLabelText("Authored Depth (optional)"), {
      target: { value: "BOARD_EXAM_REVIEW" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Note" }));

    await waitFor(() => expect(updateNote).toHaveBeenCalledWith(
      "note-1",
      expect.objectContaining({
        domainContext: "ENGINEERING_SCIENCES",
        learnerLevel: "BOARD_EXAM_REVIEW",
      }),
    ));
  });

  it("sends Domain Context to topic generation and preserves input after an API error", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (generateNoteFromTopic as jest.Mock).mockRejectedValueOnce(
      new Error("domainContext must be one of the supported values."),
    );

    render(<NoteEditorPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add details" }));
    fireEvent.change(screen.getByLabelText("Domain Context (optional)"), {
      target: { value: "ENGINEERING_MATHEMATICS" },
    });
    const programInput = screen.getByLabelText("Add a course or program");
    fireEvent.change(programInput, { target: { value: "Nursing" } });
    fireEvent.click(await screen.findByRole("option", { name: "Nursing" }));
    fireEvent.click(screen.getByText("Create from topic"));
    fireEvent.change(screen.getByLabelText("Topic"), { target: { value: "Engineering Algebra" } });
    fireEvent.click(screen.getByRole("button", { name: "Create a Note" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("domainContext");
    expect(screen.getByLabelText("Topic")).toHaveValue("Engineering Algebra");
    expect(screen.getByLabelText("Domain Context (optional)")).toHaveValue("ENGINEERING_MATHEMATICS");
    expect(generateNoteFromTopic).toHaveBeenCalledWith(
      "Engineering Algebra",
      undefined,
      "ENGINEERING_MATHEMATICS",
      ["program-nursing"],
    );
  });

  it("allows content editing for generated notes while keeping make-a-copy available", async () => {
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
    expect(contentInput).not.toHaveAttribute("readonly");
    fireEvent.change(contentInput, { target: { value: "Edited ready-note content" } });
    expect(contentInput).toHaveValue("Edited ready-note content");
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

    fireEvent.click(screen.getByRole("button", { name: /Create from topic/i }));
    expect(screen.getByRole("button", { name: "Create a Note" })).toBeDisabled();

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
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<NoteEditorPageClient />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Some note content" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("You've reached your Study Pack limit")).toBeInTheDocument();
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
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<NoteEditorPageClient />);

    expect(
      await screen.findByText("You’re making progress this month — 1 Study Pack still ready to use on the Free plan."),
    ).toBeInTheDocument();
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
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<NoteEditorPageClient />);

    const contentInput = await screen.findByLabelText("Content");
    fireEvent.change(contentInput, { target: { value: "Saved before checkout" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));
    fireEvent.click(await screen.findByRole("button", { name: /^Plus / }));
    const paywall = await screen.findByRole("dialog", { name: "You've reached your Study Pack limit" });
    fireEvent.click(within(paywall).getByRole("button", { name: "Get More Study Packs" }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalled();
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ planType: "PLUS", returnUrl: "/notes/note-created?generate=1" });
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
    expect(screen.getByRole("link", { name: "Bulk import multiple files" })).toHaveAttribute("href", "/notes/import?from=new");

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

    expect(await screen.findByText("You've reached your OCR limit")).toBeInTheDocument();
    expect(screen.getByText("Extract more text from images and files without retyping your notes.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^Plus / }));
    fireEvent.click(screen.getByRole("button", { name: "Continue with Plus" }));
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
    expect(screen.queryByRole("button", { name: "Get Plus" })).not.toBeInTheDocument();
  });

  it("renders the OCR disabled notice with the backend message during import", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (extractNoteTextFromFile as jest.Mock).mockRejectedValue(
      Object.assign(
        new Error("Image and scanned-document reading is temporarily unavailable. Try a PDF or document with selectable text instead."),
        { code: "OCR_DISABLED" },
      ),
    );

    render(<NoteEditorPageClient />);

    await screen.findByLabelText("Content");
    await selectImportNotesMode();
    const fileInput = document.getElementById("note-import-file") as HTMLInputElement | null;
    const file = new File(["img"], "note.png", { type: "image/png" });
    fireEvent.change(fileInput as HTMLInputElement, { target: { files: [file] } });

    expect(await screen.findByText("Image reading is temporarily unavailable")).toBeInTheDocument();
    expect(screen.getByText("Image and scanned-document reading is temporarily unavailable. Try a PDF or document with selectable text instead.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Yes, I'd like this back" })).toBeInTheDocument();
    expect(screen.queryByText("We couldn’t extract text from this file. Try another image or file.")).not.toBeInTheDocument();
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
    fireEvent.click(screen.getByRole("button", { name: "Add details" }));
    fireEvent.change(screen.getByLabelText("Domain Context (optional)"), {
      target: { value: "ENGINEERING_MATHEMATICS" },
    });
    fireEvent.change(screen.getByLabelText("Authored Depth (optional)"), {
      target: { value: "COLLEGE" },
    });
    const programInput = screen.getByLabelText("Add a course or program");
    fireEvent.change(programInput, { target: { value: "Nursing" } });
    fireEvent.click(await screen.findByRole("option", { name: "Nursing" }));

    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalledWith(expect.objectContaining({
        domainContext: "ENGINEERING_MATHEMATICS",
        learnerLevel: "COLLEGE",
      }));
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
