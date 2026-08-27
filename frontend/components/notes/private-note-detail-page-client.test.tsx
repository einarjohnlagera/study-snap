import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PrivateNoteDetailPageClient } from "./private-note-detail-page-client";
import {
  addCollectionItems,
  createStudyPackFromNote,
  completeProductOnboarding,
  copyNote,
  createCollection,
  createPremiumCheckoutSession,
  deleteNote,
  generateGeneratedQuiz,
  getConceptHealth,
  getCourseProgramCatalog,
  getBillingPricing,
  getMyPlan,
  getMe,
  getChallengeQuizPerformanceSummary,
  getChallengeQuizSessionReview,
  getNote,
  getNoteShares,
  getLinkedLearners,
  getNoteApplicablePrograms,
  getMyStudyPack,
  getQuickReviewPerformanceSummary,
  getQuickReviewSessionReview,
  listCollections,
  listCoursePrograms,
  listRecentQuizSessions,
  listSubjects,
  replaceNoteApplicablePrograms,
  replaceNoteShares,
  startQuickReviewSession,
  trackAnalyticsEvent,
  updateNote,
  updateNoteVisibility,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

jest.mock("@/lib/checkout-redirect", () => ({
  redirectToCheckoutUrl: jest.fn(),
}));

const pushMock = jest.fn();
const replaceMock = jest.fn();
const clipboardWriteText = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};
let searchParamValues: Record<string, string> = {};
function createSearchParamsMock() {
  return {
    get: (key: string) => searchParamValues[key] ?? null,
    toString: () => new URLSearchParams(searchParamValues).toString(),
  };
}

let searchParamsMock = createSearchParamsMock();

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1",
  useSearchParams: () => searchParamsMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  setAuthUser: jest.fn(),
}));

jest.mock("@/components/ui/summary-markdown", () => ({
  SummaryMarkdown: ({ content }: { content: string }) => <div>{content}</div>,
}));

jest.mock("@/lib/api", () => ({
  addCollectionItems: jest.fn(),
  completeProductOnboarding: jest.fn(),
  copyNote: jest.fn(),
  createCollection: jest.fn(),
  listCollections: jest.fn(),
  createPremiumCheckoutSession: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  deleteNote: jest.fn(),
  generateGeneratedQuiz: jest.fn(),
  getConceptHealth: jest.fn(),
  getCourseProgramCatalog: jest.fn(),
  getBillingPricing: jest.fn(),
  getMyPlan: jest.fn(),
  getChallengeQuizPerformanceSummary: jest.fn(),
  getChallengeQuizSessionReview: jest.fn(),
  getMe: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  getNoteShares: jest.fn(),
  getLinkedLearners: jest.fn(),
  getNoteApplicablePrograms: jest.fn(),
  listCoursePrograms: jest.fn(),
  listRecentQuizSessions: jest.fn(),
  listSubjects: jest.fn(),
  replaceNoteApplicablePrograms: jest.fn(),
  replaceNoteShares: jest.fn(),
  isEmailNotVerifiedError: () => false,
  trackAnalyticsEvent: jest.fn(),
  updateNote: jest.fn(),
  updateNoteVisibility: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
  getQuickReviewSessionReview: jest.fn(),
  startQuickReviewSession: jest.fn(),
}));

const baseNote = {
  id: "note-1",
  title: "Test Note",
  subject: "Biology",
  courseProgram: "Nursing",
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
  quizMastered: false,
  quizMasteredAt: null,
  generatedQuiz: null,
  lastUsedTargetLearnerLevel: null,
  quizCount: 0,
  quickReviewAvailable: false,
  challengeQuizAvailable: false,
  adaptivePracticeAvailable: false,
};

function createQuiz(questionCount: number) {
  return Array.from({ length: questionCount }, (_, index) => ({
    question: `Question ${index + 1}`,
    choices: ["Correct", "Incorrect A", "Incorrect B", "Incorrect C"],
    correctAnswerIndex: 0,
    explanation: `Explanation ${index + 1}`,
  }));
}

describe("PrivateNoteDetailPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    searchParamValues = {};
    searchParamsMock = createSearchParamsMock();
    window.localStorage.clear();
    window.sessionStorage.clear();
    clipboardWriteText.mockReset();
    clipboardWriteText.mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: clipboardWriteText },
      configurable: true,
    });
    (getNote as jest.Mock).mockReset();
    (getNoteShares as jest.Mock).mockReset().mockResolvedValue([]);
    (getLinkedLearners as jest.Mock).mockReset().mockResolvedValue([]);
    (getCourseProgramCatalog as jest.Mock).mockReset();
    (getNoteApplicablePrograms as jest.Mock).mockReset();
    (replaceNoteApplicablePrograms as jest.Mock).mockReset();
    (replaceNoteShares as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (createStudyPackFromNote as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (copyNote as jest.Mock).mockReset();
    (deleteNote as jest.Mock).mockReset();
    (generateGeneratedQuiz as jest.Mock).mockReset();
    (getConceptHealth as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (getChallengeQuizPerformanceSummary as jest.Mock).mockReset();
    (getChallengeQuizSessionReview as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getMyStudyPack as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
    (getQuickReviewSessionReview as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockReset();
    (listRecentQuizSessions as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    (createPremiumCheckoutSession as jest.Mock).mockReset();
    (startQuickReviewSession as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset().mockResolvedValue(undefined);
    (updateNote as jest.Mock).mockReset();
    (updateNoteVisibility as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry"]);
    (listCoursePrograms as jest.Mock).mockResolvedValue(["Nursing", "Senior High – STEM"]);
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([
      { id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null },
      { id: "program-pharmacy", name: "Pharmacy", programFamilyId: null, programFamilyName: null },
    ]);
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      programs: [{ id: "program-nursing", name: "Nursing" }],
      courseProgramShadowed: true,
    });
    (replaceNoteApplicablePrograms as jest.Mock).mockImplementation(async (_noteId: string, ids: string[]) => (
      ids.map((id) => ({ id, name: id === "program-pharmacy" ? "Pharmacy" : "Nursing" }))
    ));
    (getConceptHealth as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        quizShareLinksPerMonth: 3,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        quizShareLinksUsed: 1,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 5,
        quizShareLinksRemaining: 2,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getQuickReviewPerformanceSummary as jest.Mock).mockResolvedValue({
      attempts: 1,
      bestScorePercentage: 80,
      lastScorePercentage: 80,
      lastReviewedAt: "2026-03-21T10:30:00Z",
    });
    (getChallengeQuizPerformanceSummary as jest.Mock).mockResolvedValue({
      attempts: 1,
      bestScorePercentage: 75,
      lastScorePercentage: 75,
      lastCompletedAt: "2026-03-21T10:30:00Z",
      latestPerformanceLevel: "Good",
      latestWeakConcepts: ["Cells"],
    });
    (getChallengeQuizSessionReview as jest.Mock).mockResolvedValue({
      sessionId: "challenge-1",
      studyPackId: "sp-1",
      sessionMode: "CHALLENGE",
      status: "COMPLETED",
      totalQuestions: 10,
      correctAnswers: 8,
      scorePercentage: 80,
      retryCount: 0,
      durationSeconds: 120,
      weakConcepts: ["Cells"],
      conceptBreakdown: [],
      quiz: [],
      selectedChoices: {},
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:30:00Z",
    });
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue({
      sessionId: "quick-1",
      studyPackId: "sp-1",
      sessionMode: "QUICK_REVIEW",
      status: "COMPLETED",
      totalQuestions: 10,
      correctAnswers: 8,
      scorePercentage: 80,
      retryCount: 1,
      durationSeconds: 120,
      weakConcepts: ["Cells"],
      conceptBreakdown: [],
      quiz: [],
      selectedChoices: {},
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:30:00Z",
    });
    (listRecentQuizSessions as jest.Mock).mockResolvedValue([]);
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
    (createStudyPackFromNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "GENERATING",
      studyPackId: null,
      quickReviewAvailable: false,
      challengeQuizAvailable: false,
      adaptivePracticeAvailable: false,
    });
    (generateGeneratedQuiz as jest.Mock).mockResolvedValue({
      noteId: "note-1",
      questions: [
        {
          question: "What is the nucleus?",
          choices: ["Control center", "Energy source", "Cell wall", "Waste product"],
          correctIndex: 0,
          concept: "Cells",
          explanation: "The nucleus controls cell activity.",
        },
      ],
      generatedAt: "2026-04-17T09:00:00Z",
    });
    (getMyStudyPack as jest.Mock).mockResolvedValue({
      id: "sp-1",
      noteId: "note-1",
      title: "Suggested Title",
      subject: "Biology",
      tags: ["cells"],
    });
    (listRecentQuizSessions as jest.Mock).mockResolvedValue([]);
    (copyNote as jest.Mock).mockResolvedValue({ id: "note-copy-1" });
    (deleteNote as jest.Mock).mockResolvedValue(undefined);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("routes Edit to note editor for draft note", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    const editButton = screen.getByRole("menuitem", { name: "Edit" });
    fireEvent.click(editButton);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/edit");
  });

  it("shows a skeleton card instead of plain text while the note is first loading", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    let resolveNote: (value: typeof baseNote) => void = () => {};
    (getNote as jest.Mock).mockReturnValue(
      new Promise((resolve) => {
        resolveNote = resolve;
      }),
    );

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(screen.getByText("Loading your note...")).toBeInTheDocument();
    expect(screen.queryByText("Loading note...")).not.toBeInTheDocument();

    resolveNote({ ...baseNote, studyPackStatus: "DRAFT" });
    await screen.findByText("Test Note");
  });

  it("shows a compact note actions menu for long mobile note headers", async () => {
    const longTitle = "This is a very long note title that should wrap cleanly on mobile without pushing action buttons outside the header container";
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, title: longTitle, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const title = await screen.findByRole("heading", { name: longTitle });
    const menuTrigger = screen.getByRole("button", { name: "Open note actions" });
    const menuAnchor = menuTrigger.parentElement;
    const titleColumn = title.parentElement;

    expect(title).toHaveClass("wrap-break-word");
    expect(menuTrigger).toBeVisible();
    expect(menuAnchor).toHaveClass("relative", "shrink-0", "self-start");
    expect(titleColumn).toHaveClass("min-w-0", "flex-1", "space-y-3");

    fireEvent.click(menuTrigger);

    expect(screen.getByRole("menu", { name: "Note actions" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Edit" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Make a Copy" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Share" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Delete" })).toBeInTheDocument();
  });

  it("closes the note actions menu on outside click", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    expect(screen.getByRole("menu", { name: "Note actions" })).toBeInTheDocument();

    fireEvent.mouseDown(document.body);

    await waitFor(() => {
      expect(screen.queryByRole("menu", { name: "Note actions" })).not.toBeInTheDocument();
    });
  });

  it("disables Generate Study Pack and visibility toggle for unverified users", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: null });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PRIVATE" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    const generateButton = screen.getByRole("button", { name: "Generate Study Pack" });
    const visibilityButton = screen.getByRole("button", { name: /private/i });

    expect(generateButton).toBeDisabled();
    expect(visibilityButton).toBeDisabled();
  });

  it("for generated notes, Edit enables inline metadata editing instead of routing", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([
      { id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null },
      { id: "program-civil", name: "Civil Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
      { id: "program-electrical", name: "Electrical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
      { id: "program-mechanical", name: "Mechanical Engineering", programFamilyId: "family-engineering", programFamilyName: "Engineering" },
    ]);
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    expect(pushMock).not.toHaveBeenCalledWith("/notes/note-1/edit");
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, tags, Domain Context, and Authored Depth.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Who is this note for?")).not.toBeInTheDocument();
    expect(await screen.findByLabelText("Add a course or program")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add all 3 Engineering programs" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Share" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Quick Review" })).not.toBeInTheDocument();
  });

  // A single program names the note's applicability directly; a multi-program note opens the
  // explicit viewer rather than implying one program is primary.
  it("omits the reach count when the note has a single applicable program", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue(baseNote);

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Nursing", { selector: "p" })).toBeInTheDocument();
    expect(screen.queryByText(/Applies to/)).not.toBeInTheDocument();
    expect(screen.queryByText("Applicable Programs")).not.toBeInTheDocument();
    expect(getNoteApplicablePrograms).toHaveBeenCalledWith("note-1");
  });

  it("opens the program viewer above one applicable program", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue(baseNote);
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      programs: [
        { id: "program-nursing", name: "Nursing" },
        { id: "program-pharmacy", name: "Pharmacy" },
        { id: "program-medicine", name: "Medicine" },
      ],
      courseProgramShadowed: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const viewerButton = await screen.findByRole("button", { name: "Applies to 3 programs" });
    expect(screen.queryByText("Pharmacy")).not.toBeInTheDocument();
    expect(screen.queryByText("Medicine")).not.toBeInTheDocument();
    fireEvent.click(viewerButton);
    expect(await screen.findByText("Pharmacy")).toBeInTheDocument();
    expect(screen.getByText("Medicine")).toBeInTheDocument();
  });

  it("keeps a non-teacher route to quiz creation, in the note actions menu not the practice row", async () => {
    // v0.89.0 opened share-link creation to every onboarded user, but the control that PRODUCES a
    // generated quiz lived only in the teacher branch — so the population the change was made for
    // had no way to reach it and the backend fix reached nobody. A cold pressure test caught it at
    // signoff. This pins the route open.
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    // Ratified 2026-08-20: the action lives in the note-actions menu, NOT the practice row —
    // it is a support/share action, and beside Start Quick Review it risked becoming an avoidance
    // path. The route must still exist for a non-teacher; only its placement moved.
    fireEvent.click(await screen.findByRole("button", { name: "Open note actions" }));
    expect(await screen.findByRole("menuitem", { name: /Quiz for someone/i })).toBeInTheDocument();
    // ⚠️ It is gated on NOTHING — no connection, no profile. A shared-quiz recipient needs neither
    // an account nor a relationship, so sharing must never depend on one existing.
    expect(screen.getByRole("button", { name: /Start Quick Review/i })).toBeInTheDocument();
    // And it must NOT be back in the practice row.
    expect(screen.queryByRole("button", { name: /^Quiz for someone$/i })).not.toBeInTheDocument();
  });

  it("still gives a learner an editable Course / Program when the note is not shadowed", async () => {
    // The ordinary learner case, which lost coverage when the default mock started returning
    // courseProgramShadowed: true for every test. It also proves the #note-course-program-inline
    // assertions elsewhere are meaningful rather than matching a selector that never exists.
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      courseProgram: "BS Nursing",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
    });
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      programs: [],
      courseProgramShadowed: false,
    });
    (updateNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      courseProgram: "Marine Biology",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    // Not shadowed: the learner's own program is shown as an ordinary value, with no provenance
    // sentence claiming someone else set it.
    expect(await screen.findByText("BS Nursing")).toBeInTheDocument();
    expect(screen.queryByText(
      "Set by the note this was copied from. Your own course or program is on your profile.",
    )).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));
    const courseProgramInput = await screen.findByLabelText(/Course \/ Program\(s\)/);
    const subjectInput = screen.getByLabelText("Subject");
    expect(courseProgramInput).toHaveValue("BS Nursing");
    expect(subjectInput).toHaveAttribute("maxLength", "64");
    expect(courseProgramInput).toHaveAttribute("maxLength", "120");
    fireEvent.click(screen.getByLabelText("Toggle course program suggestions"));
    expect(screen.getAllByRole("option").slice(0, 2).map((option) => option.textContent)).toEqual([
      "Nursing",
      "Pharmacy",
    ]);
    fireEvent.change(courseProgramInput, { target: { value: "Marine Biology" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        courseProgramText: "Marine Biology",
      }));
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "COURSE_PROGRAM_VALUE_SELECTED",
        metadata: { surface: "note-detail", matchedCatalog: false },
      });
    });
  });

  it("does not block a learner when the course program catalog fails to load", async () => {
    // The catalog is a curator-only dependency. Before the fix it shared a Promise.all with the
    // shadow flag, so its failure left the flag null forever -- hiding the learner's own field and
    // skipping its required validation.
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, courseProgram: "BS Nursing" });
    (getCourseProgramCatalog as jest.Mock).mockRejectedValue(new Error("catalog down"));
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      programs: [],
      courseProgramShadowed: false,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    // The catalog rejection must not swallow the shadow flag: the learner still sees their own
    // program rather than an empty labelled block.
    expect(await screen.findByText("BS Nursing")).toBeInTheDocument();
  });

  it("shows copied applicable programs as read-only provenance for a learner on refresh", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
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

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Civil Engineering · Mechanical Engineering")).toBeInTheDocument();
    expect(screen.getByText(
      "Set by the note this was copied from. Your own course or program is on your profile.",
    )).toBeInTheDocument();
    // Assert on the learner input's own id: the previous check used the CURATOR control's aria-label,
    // which can never render for a STUDENT, so it passed vacuously.
    expect(document.querySelector("#note-course-program-inline")).toBeNull();
  });

  it("does not claim copy provenance for an owner self-copy", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
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

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Nursing", { selector: "p" })).toBeInTheDocument();
    expect(screen.queryByText(/Set by the note this was copied from/)).not.toBeInTheDocument();
  });

  it("saves a shadowed learner note without requiring a personal course or program", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    const shadowedNote = {
      ...baseNote,
      courseProgram: null,
      studyPackStatus: "STUDY_PACK_READY" as const,
      studyPackId: "sp-1",
    };
    (getNote as jest.Mock).mockResolvedValue(shadowedNote);
    (updateNote as jest.Mock).mockResolvedValue(shadowedNote);
    (getNoteApplicablePrograms as jest.Mock).mockResolvedValue({
      programs: [{ id: "program-nursing", name: "Nursing" }],
      courseProgramShadowed: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Nursing", { selector: "p" });
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));
    // Assert on the learner input's own id: the previous check used the CURATOR control's aria-label,
    // which can never render for a STUDENT, so it passed vacuously.
    expect(document.querySelector("#note-course-program-inline")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        courseProgramText: null,
      }));
    });
  });

  it("keeps applicable-program load errors recoverable without blocking save", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    const noteWithoutProgram = {
      ...baseNote,
      courseProgram: null,
      studyPackStatus: "STUDY_PACK_READY" as const,
      studyPackId: "sp-1",
    };
    (getNote as jest.Mock).mockResolvedValue(noteWithoutProgram);
    (getNoteApplicablePrograms as jest.Mock).mockRejectedValue(new Error("Could not load Course / Program(s)."));
    (updateNote as jest.Mock).mockResolvedValue(noteWithoutProgram);

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Could not load Course / Program(s)." )).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(updateNote).toHaveBeenCalled());
  });

  it("seeds the authoring axes from the note and saves the corrected values for a teacher", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      domainContext: "NURSING",
      learnerLevel: "COLLEGE",
    });
    (updateNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      domainContext: "ENGINEERING_MATHEMATICS",
      learnerLevel: "BOARD_EXAM_REVIEW",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    const domainContextSelect = screen.getByLabelText("Domain Context (optional)");
    const learnerLevelSelect = screen.getByLabelText("Authored Depth (optional)");
    expect(domainContextSelect).toHaveValue("NURSING");
    expect(learnerLevelSelect).toHaveValue("COLLEGE");
    expect(screen.getByText(/nursing-framed Pharmacology/)).toBeInTheDocument();

    fireEvent.change(domainContextSelect, { target: { value: "ENGINEERING_MATHEMATICS" } });
    expect(screen.getByText(/Engineering Economics/)).toBeInTheDocument();
    expect(screen.queryByText(/nursing-framed Pharmacology/)).not.toBeInTheDocument();
    fireEvent.change(learnerLevelSelect, { target: { value: "BOARD_EXAM_REVIEW" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        domainContext: "ENGINEERING_MATHEMATICS",
        learnerLevel: "BOARD_EXAM_REVIEW",
      }));
    });
  });

  it("clears an authoring axis when the teacher selects the fallback option", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      domainContext: "NURSING",
      learnerLevel: "COLLEGE",
    });
    (updateNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      domainContext: null,
      learnerLevel: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    fireEvent.change(screen.getByLabelText("Domain Context (optional)"), { target: { value: "" } });
    fireEvent.change(screen.getByLabelText("Authored Depth (optional)"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        domainContext: null,
        learnerLevel: null,
      }));
    });
  });

  it("keeps the selected programs and inline edit open when save fails", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    const readyNote = {
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY" as const,
      studyPackId: "sp-1",
    };
    (getNote as jest.Mock).mockResolvedValue(readyNote);
    (updateNote as jest.Mock).mockRejectedValue(new Error("Could not save Course / Program(s)."));

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));
    await screen.findByRole("button", { name: "Remove Nursing" });
    const programToggles = screen.getAllByLabelText("Toggle course program suggestions");
    fireEvent.click(programToggles[programToggles.length - 1]);
    fireEvent.click(screen.getByRole("option", { name: "Pharmacy" }));
    await screen.findByText(/You've added more than one program/);
    fireEvent.change(screen.getByRole("combobox", { name: /Domain Context/ }), { target: { value: "NURSING" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Could not save Course / Program(s).")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Nursing" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Pharmacy" })).toBeInTheDocument();
  });

  it("exposes the authoring axes to an admin on a non-teacher profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
      role: "ADMIN",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      domainContext: "ACCOUNTANCY",
      learnerLevel: "COLLEGE",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    expect(screen.getByLabelText("Domain Context (optional)")).toHaveValue("ACCOUNTANCY");
    expect(screen.getByLabelText("Authored Depth (optional)")).toHaveValue("COLLEGE");
  });

  it("preserves the authoring axes when a non-teacher saves note details", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "STUDENT" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      domainContext: "NURSING",
      learnerLevel: "SENIOR_HIGH",
    });
    (updateNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      domainContext: "NURSING",
      learnerLevel: "SENIOR_HIGH",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    expect(screen.queryByLabelText("Domain Context (optional)")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Authored Depth (optional)")).not.toBeInTheDocument();
    // Assert on the learner input's own id: the previous check used the CURATOR control's aria-label,
    // which can never render for a STUDENT, so it passed vacuously.
    expect(document.querySelector("#note-course-program-inline")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        domainContext: "NURSING",
        learnerLevel: "SENIOR_HIGH",
      }));
    });
  });

  it("nudges the teacher when the inline subject matches the selected Domain Context", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      subject: "Nursing",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      domainContext: "NURSING",
      learnerLevel: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    expect(screen.getByText(/Subject matches the Domain Context/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Domain Context (optional)"), { target: { value: "GENERAL_EDUCATION" } });

    expect(screen.queryByText(/Subject matches the Domain Context/)).not.toBeInTheDocument();
  });

  it("navigates to the dedicated session review page from Recent Sessions on note detail", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });
    (listRecentQuizSessions as jest.Mock).mockResolvedValue([
      {
        sessionId: "quick-1",
        sessionMode: "QUICK_REVIEW",
        totalQuestions: 10,
        correctAnswers: 8,
        scorePercentage: 80,
        retryCount: 1,
        performanceLevel: null,
        weakConcepts: ["Cells"],
        participatingNoteCount: 1,
        createdAt: "2026-04-11T10:00:00Z",
        completedAt: "2026-04-11T10:05:00Z",
      },
      {
        sessionId: "challenge-1",
        sessionMode: "CHALLENGE",
        totalQuestions: 12,
        correctAnswers: 9,
        scorePercentage: 75,
        retryCount: 0,
        performanceLevel: "Good",
        weakConcepts: ["Genetics"],
        participatingNoteCount: 1,
        createdAt: "2026-04-10T10:00:00Z",
        completedAt: "2026-04-10T10:12:00Z",
      },
    ]);
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue({
      sessionId: "quick-1",
      studyPackId: "sp-1",
      sessionMode: "QUICK_REVIEW",
      status: "COMPLETED",
      totalQuestions: 10,
      correctAnswers: 8,
      scorePercentage: 80,
      retryCount: 1,
      durationSeconds: 120,
      weakConcepts: ["Cells"],
      conceptBreakdown: [
        {
          concept: "Cells",
          correctAnswers: 1,
          totalQuestions: 2,
          accuracyPercentage: 50,
        },
      ],
      quiz: [
        {
          question: "Which organelle produces ATP?",
          choices: ["Mitochondria", "Nucleus", "Ribosome", "Golgi body"],
          correctIndex: 0,
          concept: "Cells",
          explanation: "Mitochondria are the main ATP producers.",
        },
      ],
      selectedChoices: { 0: 1 },
      createdAt: "2026-04-11T10:00:00Z",
      completedAt: "2026-04-11T10:05:00Z",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Recent Sessions")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Show Sessions" }));
    const quickReviewSessionButton = screen.getAllByText("Quick Review")
      .map((element) => element.closest("button"))
      .find((button) => button !== null);
    expect(quickReviewSessionButton).not.toBeNull();

    fireEvent.click(quickReviewSessionButton as HTMLButtonElement);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/sessions/quick-1?mode=quick-review&tab=summary");
    expect(getQuickReviewSessionReview).not.toHaveBeenCalled();
    expect(screen.queryByText("Currently reviewing")).not.toBeInTheDocument();
    expect(screen.queryByText("Loading session review...")).not.toBeInTheDocument();
  });

  it("keeps the same dedicated session review route behavior on smaller screens", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });
    (listRecentQuizSessions as jest.Mock).mockResolvedValue([
      {
        sessionId: "quick-1",
        sessionMode: "QUICK_REVIEW",
        totalQuestions: 10,
        correctAnswers: 8,
        scorePercentage: 80,
        retryCount: 1,
        performanceLevel: null,
        weakConcepts: ["Cells"],
        participatingNoteCount: 1,
        createdAt: "2026-04-11T10:00:00Z",
        completedAt: "2026-04-11T10:05:00Z",
      },
    ]);

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Recent Sessions")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Show Sessions" }));
    const quickReviewSessionButton = screen.getAllByText("Quick Review")
      .map((element) => element.closest("button"))
      .find((button) => button !== null);
    expect(quickReviewSessionButton).not.toBeNull();

    fireEvent.click(quickReviewSessionButton as HTMLButtonElement);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/sessions/quick-1?mode=quick-review&tab=summary");
    expect(getQuickReviewSessionReview).not.toHaveBeenCalled();
    expect(screen.queryByText("Loading session review...")).not.toBeInTheDocument();
  });

  it("shows private-share modal and then opens share-link modal after making note public", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PRIVATE" });
    (updateNoteVisibility as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PUBLIC" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Share" }));

    expect(screen.getByText("This note is private")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Publish & Share Link" }));

    expect(updateNoteVisibility).toHaveBeenCalledWith("note-1", "PUBLIC");
    expect(await screen.findByText("Share this note")).toBeInTheDocument();
    await waitFor(() => {
      expect(clipboardWriteText).toHaveBeenCalled();
      expect(screen.getByText("Copied ✓")).toBeInTheDocument();
    });
    expect(screen.getAllByText("Link copied to clipboard").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Copy Link" })).not.toBeInTheDocument();
  });

  it("restores persisted recipients and revokes them when Private is selected", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, visibility: "PRIVATE" });
    (getNoteShares as jest.Mock).mockResolvedValue([
      {
        relationshipId: "relationship-1",
        granteeDisplayName: "Maria Santos",
        granteeEmail: "maria@example.com",
        createdAt: "2026-08-27T00:00:00Z",
      },
    ]);
    (getLinkedLearners as jest.Mock).mockResolvedValue([
      {
        id: "relationship-1",
        counterpartyDisplayName: "Maria Santos",
        counterpartyEmail: "maria@example.com",
        status: "ACCEPTED",
      },
    ]);
    (replaceNoteShares as jest.Mock).mockResolvedValue([]);

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const sharedAccessButton = await screen.findByRole("button", { name: "Shared" });
    fireEvent.click(sharedAccessButton);
    fireEvent.click(screen.getByRole("button", { name: /Share with connections/ }));
    expect(await screen.findByRole("checkbox")).toBeChecked();
    fireEvent.click(sharedAccessButton);
    fireEvent.click(sharedAccessButton);
    fireEvent.click(screen.getByRole("button", { name: /Private/ }));
    expect(screen.getByText("Maria Santos will lose access to this note.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Make Private" }));

    await waitFor(() => expect(replaceNoteShares).toHaveBeenCalledWith("note-1", []));
    expect(updateNoteVisibility).not.toHaveBeenCalled();
  });

  it("refuses to go Private while the share list is unknown, instead of silently keeping the grants live", async () => {
    // ⚠️ BLOCKING REGRESSION GUARD. Revocation is orchestrated client-side — the server cannot infer intent
    // from the visibility value, since opening the recipient picker on a public note also sets PRIVATE. So
    // with `noteShares` still [] after a FAILED shares load, selecting Private used to set the note private,
    // skip the confirmation, leave every grant live, and report success. The chip read "Private" while three
    // people could still read the note, and shipped copy in the Help Center says access is removed.
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, visibility: "PRIVATE" });
    (getNoteShares as jest.Mock).mockRejectedValue(new Error("network"));

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    // The chip must not claim "Private" on an unresolved list.
    const accessButton = await screen.findByRole("button", { name: /Checking access/ });
    fireEvent.click(accessButton);

    const privateOption = screen.getByRole("button", { name: /Private/ });
    expect(privateOption).toBeDisabled();
    fireEvent.click(privateOption);

    await waitFor(() => expect(updateNoteVisibility).not.toHaveBeenCalled());
    expect(replaceNoteShares).not.toHaveBeenCalled();
  });

  it("publishes without revoking persisted connection shares", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, visibility: "PRIVATE" });
    (getNoteShares as jest.Mock).mockResolvedValue([
      {
        relationshipId: "relationship-1",
        granteeDisplayName: "Maria Santos",
        granteeEmail: "maria@example.com",
        createdAt: "2026-08-27T00:00:00Z",
      },
    ]);
    (updateNoteVisibility as jest.Mock).mockResolvedValue({ ...baseNote, visibility: "PUBLIC" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Shared" }));
    fireEvent.click(screen.getByRole("button", { name: /Public/ }));
    fireEvent.click(screen.getByRole("button", { name: "Make Public" }));

    await waitFor(() => expect(updateNoteVisibility).toHaveBeenCalledWith("note-1", "PUBLIC"));
    expect(replaceNoteShares).not.toHaveBeenCalled();
  });

  it("supports Make a Copy and Delete from the note actions menu", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PRIVATE" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Make a Copy" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1");
    });
    expect(pushMock).toHaveBeenCalledWith("/notes/note-copy-1?copied=1");

    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Delete" }));

    expect(screen.getByText("Delete this note?")).toBeInTheDocument();
  });

  it("adds the note to an existing Study Plan from the note actions menu", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });
    (listCollections as jest.Mock).mockResolvedValue([
      { id: "collection-1", title: "Cell Biology", itemCount: 2 },
    ]);
    (addCollectionItems as jest.Mock).mockResolvedValue({ id: "collection-1", title: "Cell Biology" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Add to Study Plan" }));

    await screen.findByText("Cell Biology");
    fireEvent.click(screen.getByRole("button", { name: "Add here" }));

    await waitFor(() => {
      expect(addCollectionItems).toHaveBeenCalledWith("collection-1", ["note-1"]);
    });
    await waitFor(() => {
      expect(screen.queryByText(/Add to a Study Plan/)).not.toBeInTheDocument();
    });
    expect(screen.getByText("Added to Cell Biology.")).toBeInTheDocument();
  });

  it("creates a new Study Plan from the note actions menu when none exist", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });
    (listCollections as jest.Mock).mockResolvedValue([]);
    (createCollection as jest.Mock).mockResolvedValue({ id: "collection-2", title: "New Plan" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Add to Study Plan" }));

    await screen.findByPlaceholderText("Study Plan title");
    fireEvent.change(screen.getByPlaceholderText("Study Plan title"), { target: { value: "New Plan" } });
    fireEvent.click(screen.getByRole("button", { name: "Create new Study Plan" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({ title: "New Plan", noteIds: ["note-1"] });
    });
    expect(screen.getByText("Added to New Plan.")).toBeInTheDocument();
  });

  it("routes free users into the shared Challenge Quiz mode-selection entry without showing the premium modal", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz?entry=mode-selection");
    expect(screen.queryByText("Adaptive Practice is a Pro feature")).not.toBeInTheDocument();
  });

  it("navigates to quiz mode selection when a free user exhausted Challenge Quiz credits", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
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
        challengeQuizzesUsed: 5,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 0,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz?entry=mode-selection");
    expect(screen.queryByText("You've reached your quiz limit")).not.toBeInTheDocument();
  });

  it("shows the over-quota paywall when a free user has no Adaptive Practice sessions remaining", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 3,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 3,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Adaptive Practice" }));

    expect(await screen.findByText("You've used your free Adaptive Practice sessions")).toBeInTheDocument();
  });

  it("routes premium users with exhausted Adaptive Practice usage into the limit flow", async () => {
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
        adaptivePracticeUsed: 30,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 98,
        challengeQuizzesRemaining: 50,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 100,
      },
      features: {
        adaptivePracticeAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
    });
    (getChallengeQuizPerformanceSummary as jest.Mock).mockResolvedValue({
      attempts: 1,
      bestScorePercentage: 40,
      lastScorePercentage: 40,
      latestWeakConcepts: ["Cells"],
      lastReviewedAt: "2026-03-21T10:30:00Z",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Adaptive Practice" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/adaptive-practice?entry=note-detail");
    expect(screen.queryByText("Adaptive Practice is a Pro feature")).not.toBeInTheDocument();
  });

  it.each(["STUDENT", "BOARD_EXAM", "PARENT", "PROFESSIONAL"] as const)(
    "shows the review-surface entries on the Key Concepts tab for %s note detail",
    async (profileType) => {
      (getAuthUser as jest.Mock).mockReturnValue({
        planType: profileType === "PROFESSIONAL" ? "PRO" : "FREE",
        emailVerifiedAt: "2026-03-21T09:00:00Z",
        profileType,
      });
      (getNote as jest.Mock).mockResolvedValue({
        ...baseNote,
        studyPackStatus: "STUDY_PACK_READY",
        studyPackId: "sp-1",
        summary: "Generated summary",
        keyConcepts: ["Cells"],
        quiz: [
          {
            question: "What is the nucleus?",
            choices: ["Control center", "Energy source", "Cell wall", "Waste product"],
            correctIndex: 0,
            concept: "Cells",
            explanation: "The nucleus controls cell activity.",
          },
        ],
        quickReviewAvailable: true,
        challengeQuizAvailable: true,
        adaptivePracticeAvailable: false,
      });

      const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

      expect(screen.queryByRole("button", { name: "Flashcards" })).not.toBeInTheDocument();

      fireEvent.click(await screen.findByRole("tab", { name: "Key Concepts" }));
      searchParamValues = { tab: "key-concepts" };
      searchParamsMock = createSearchParamsMock();
      rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

      const flashcardsButton = await screen.findByRole("button", { name: "Flashcards" });
      const memorizationButton = await screen.findByRole("button", { name: "Memorization" });
      expect(flashcardsButton).toBeInTheDocument();
      expect(memorizationButton).toBeInTheDocument();

      fireEvent.click(flashcardsButton);

      expect(pushMock).toHaveBeenCalledWith("/notes/note-1/flashcards");
      fireEvent.click(memorizationButton);
      expect(pushMock).toHaveBeenCalledWith("/notes/note-1/memorization");
    },
  );

  it("hides review-surface entries on the Key Concepts tab in teacher mode", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("tab", { name: "Key Concepts" }));
    searchParamValues = { tab: "key-concepts" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Cells")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Flashcards" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Memorization" })).not.toBeInTheDocument();
  });

  it("renders teacher note detail with Study Pack tabs visible and student-only sections hidden", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("button", { name: "Generate Quiz" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Summary" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Key Concepts" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Quiz" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Full Notes" })).toBeInTheDocument();
    expect(screen.getByText("Teacher mode keeps quiz work separate from student quiz sessions. Generate, review, and export from the dedicated quiz preview.")).toBeInTheDocument();
    expect(screen.queryByText("Performance Overview")).not.toBeInTheDocument();
    expect(screen.queryByText("Recent Sessions")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Quick Review" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Flashcards" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Memorization" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Challenge Quiz" })).not.toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Quiz question count" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Generate Quiz" }));

    expect(screen.getByRole("dialog", { name: "Generate quiz" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Quiz question count" })).toBeInTheDocument();
    expect(screen.getByText("Higher counts cover more material. Plus unlocks 20 and 30 questions.")).toBeInTheDocument();
    expect(screen.getByText("Target Level")).toBeInTheDocument();
    expect(screen.getByText("From your profile: College")).toBeInTheDocument();
    expect(screen.getByText("AI quizzes left this month")).toBeInTheDocument();
    expect(screen.getByText("Share links left this month")).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "Generate Quiz" }).at(-1) as HTMLButtonElement);

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1", 10, "COLLEGE");
    });
  });

  it("shows a distinct Needs work chip for struggling concepts", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PLUS",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PLUS",
      limits: {
        studyPacksPerMonth: 50,
        challengeQuizzesPerMonth: 20,
        adaptivePracticePerMonth: 10,
        ocrPerMonth: 50,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 48,
        challengeQuizzesRemaining: 20,
        adaptivePracticeRemaining: 10,
        ocrRemaining: 50,
      },
      features: {
        adaptivePracticeAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells", "Genetics"],
      generatedQuiz: null,
    });
    (getConceptHealth as jest.Mock).mockResolvedValue([
      {
        concept: "  cells  ",
        readinessStatus: "DUE",
        lastCorrectAt: "2026-03-20T10:00:00Z",
        lastIncorrectAt: "2026-03-21T10:00:00Z",
        isStruggling: true,
        isDue: true,
        daysSinceReview: 1,
      },
      {
        concept: "Genetics",
        readinessStatus: "MASTERED",
        lastCorrectAt: "2026-03-21T10:00:00Z",
        lastIncorrectAt: "2026-03-20T10:00:00Z",
        isStruggling: false,
        isDue: false,
        daysSinceReview: 0,
      },
    ]);

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("tab", { name: "Key Concepts" }));
    searchParamValues = { tab: "key-concepts" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Needs work")).toBeInTheDocument();
    expect(screen.getByText("Due — 1d ago")).toBeInTheDocument();
  });

  it("shows note readiness signal to Free while keeping review timing gated", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells", "Genetics", "Evolution"],
      generatedQuiz: null,
    });
    (getConceptHealth as jest.Mock).mockResolvedValue([
      {
        concept: "Cells",
        readinessStatus: "MASTERED",
        lastCorrectAt: null,
        lastIncorrectAt: null,
        isStruggling: false,
        isDue: false,
        daysSinceReview: null,
      },
      {
        concept: "Genetics",
        readinessStatus: "DUE",
        lastCorrectAt: null,
        lastIncorrectAt: null,
        isStruggling: false,
        isDue: true,
        daysSinceReview: null,
      },
      {
        concept: "Evolution",
        readinessStatus: "NOT_STARTED",
        lastCorrectAt: null,
        lastIncorrectAt: null,
        isStruggling: false,
        isDue: true,
        daysSinceReview: null,
      },
    ]);

    const { container, rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Note readiness")).toBeInTheDocument();
    expect(screen.getByText((content) => (
      content.includes("33% ready") && content.includes("1/3 mastered") && content.includes("1 due")
    ))).toBeInTheDocument();
    const readinessIndex = container.textContent!.indexOf("Note readiness");
    const performanceIndex = container.textContent!.indexOf("Performance Overview");
    expect(readinessIndex).toBeGreaterThan(-1);
    expect(performanceIndex).toBeGreaterThan(readinessIndex);

    fireEvent.click(screen.getByRole("tab", { name: "Key Concepts" }));
    searchParamValues = { tab: "key-concepts" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Mastered")).toBeInTheDocument();
    expect(screen.getByText("Due")).toBeInTheDocument();
    expect(screen.getByText("Not started")).toBeInTheDocument();
    expect(screen.queryByText(/Due — \d+d ago/)).not.toBeInTheDocument();
    expect(screen.getByText("Review timing for 1 due concept is available on Plus and Pro.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "See review timing — get Plus" }));
    expect(await screen.findByRole("heading", { name: "Know what is slipping before you forget it" })).toBeInTheDocument();
    expect(screen.getByText("Note readiness")).toBeInTheDocument();
  });

  it("derives readiness from isDue when readinessStatus is absent", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells", "Genetics", "Evolution"],
      generatedQuiz: null,
    });
    // readinessStatus omitted — the fallback uses the visible lastCorrectAt signal
    // to distinguish genuinely due concepts from concepts that have not been started.
    (getConceptHealth as jest.Mock).mockResolvedValue([
      {
        concept: "Cells",
        lastCorrectAt: null,
        lastIncorrectAt: null,
        isStruggling: false,
        isDue: false,
        daysSinceReview: null,
      },
      {
        concept: "Genetics",
        lastCorrectAt: "2026-05-01T09:00:00Z",
        lastIncorrectAt: null,
        isStruggling: false,
        isDue: true,
        daysSinceReview: null,
      },
      {
        concept: "Evolution",
        lastCorrectAt: "2026-05-01T09:00:00Z",
        lastIncorrectAt: null,
        isStruggling: false,
        isDue: true,
        daysSinceReview: null,
      },
    ]);

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Note readiness")).toBeInTheDocument();
    // Cells (not due) → mastered; Genetics and Evolution (due + lastCorrectAt) → due.
    expect(screen.getByText((content) => (
      content.includes("33% ready") && content.includes("1/3 mastered") && content.includes("2 due")
    ))).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Key Concepts" }));
    searchParamValues = { tab: "key-concepts" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Mastered")).toBeInTheDocument();
    expect(screen.getAllByText("Due")).toHaveLength(2);
    expect(screen.queryByText("Not started")).not.toBeInTheDocument();
  });

  it("keeps note content visible when note readiness cannot load", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });
    (getConceptHealth as jest.Mock).mockRejectedValue(new Error("network"));

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Generated summary")).toBeInTheDocument();
    expect(await screen.findByText("Readiness is unavailable right now. Your note content is still available.")).toBeInTheDocument();
  });

  it("keeps Target Level out of non-teacher note detail", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByRole("tab", { name: "Summary" });
    expect(screen.queryByRole("button", { name: "Generate Quiz" })).not.toBeInTheDocument();
    expect(screen.queryByText("Target Level")).not.toBeInTheDocument();
  });

  it("prefills teacher quiz Target Level from the last generation on the note", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      lastUsedTargetLearnerLevel: "JUNIOR_HIGH",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));

    expect(screen.getByRole("combobox", { name: "Target Level" })).toHaveValue("JUNIOR_HIGH");
    expect(screen.getByText("Last used: Junior High")).toBeInTheDocument();
  });

  it("keeps teacher quiz generation disabled when Target Level has no fallback", async () => {
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: null });
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));

    expect(screen.getAllByRole("button", { name: "Generate Quiz" }).at(-1)).toBeDisabled();
    expect(screen.getByText("Choose the default quiz difficulty before generating.")).toBeInTheDocument();
  });

  it("opens the longer teacher quiz paywall when a Free teacher clicks a locked count", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));
    expect(screen.getByRole("dialog", { name: "Generate quiz" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "20 Plus" }));

    expect(await screen.findByText("Unlock longer teacher quizzes")).toBeInTheDocument();
    expect(screen.getByText("Plus unlocks 20- and 30-question quizzes so you can match chapter quizzes and longer unit assessments.")).toBeInTheDocument();
    expect(generateGeneratedQuiz).not.toHaveBeenCalled();
  });

  it("generates the selected longer quiz count for Plus teachers", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PLUS",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PLUS",
      limits: {
        studyPacksPerMonth: 50,
        challengeQuizzesPerMonth: 25,
        adaptivePracticePerMonth: 10,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 48,
        challengeQuizzesRemaining: 25,
        adaptivePracticeRemaining: 10,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));
    expect(screen.getByRole("dialog", { name: "Generate quiz" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "30" }));
    fireEvent.change(screen.getByRole("combobox", { name: "Target Level" }), {
      target: { value: "JUNIOR_HIGH" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "Generate Quiz" }).at(-1) as HTMLButtonElement);

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1", 30, "JUNIOR_HIGH");
    });
  });

  it("shows the paywall modal for teachers who exhausted free quiz credits before generating", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
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
        challengeQuizzesUsed: 5,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 0,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));

    expect(await screen.findByText("You've reached your quiz generation limit")).toBeInTheDocument();
    expect(generateGeneratedQuiz).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("warns before generation when share links are exhausted without blocking quiz creation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 20,
        quizShareLinksPerMonth: 3,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 4,
        quizShareLinksUsed: 3,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 16,
        quizShareLinksRemaining: 0,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));
    expect(screen.getByText("16")).toBeInTheDocument();
    expect(screen.getByText("You can still make and export this quiz, but you can't create another share link until your quota resets. Upgrade to Plus.")).toBeInTheDocument();
    const generateButton = screen.getAllByRole("button", { name: "Generate Quiz" }).at(-1) as HTMLButtonElement;
    expect(generateButton).toBeEnabled();
    fireEvent.click(generateButton);

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1", 10, "COLLEGE");
    });
  });

  it("keeps quiz generation available when plan usage cannot load", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getMyPlan as jest.Mock).mockRejectedValue(new Error("network"));
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));
    expect(screen.queryByLabelText("Quiz allowances")).not.toBeInTheDocument();
    const generateButton = screen.getAllByRole("button", { name: "Generate Quiz" }).at(-1) as HTMLButtonElement;
    expect(generateButton).toBeEnabled();
    fireEvent.click(generateButton);

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1", 10, "COLLEGE");
    });
  });

  it("renders unlimited share links for Pro before quiz generation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PRO",
      limits: {
        studyPacksPerMonth: 100,
        challengeQuizzesPerMonth: 200,
        quizShareLinksPerMonth: null,
        adaptivePracticePerMonth: 30,
        ocrPerMonth: 100,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 4,
        quizShareLinksUsed: 12,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 98,
        challengeQuizzesRemaining: 196,
        quizShareLinksRemaining: null,
        adaptivePracticeRemaining: 30,
        ocrRemaining: 100,
      },
      features: {
        adaptivePracticeAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));
    expect(screen.getByText("Unlimited")).toBeInTheDocument();
  });

  it("renders teacher note detail with View Quiz and regenerate confirmation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: {
        noteId: "note-1",
        questions: [
          {
            question: "What is the nucleus?",
            choices: ["Control center", "Energy source", "Cell wall", "Waste product"],
            correctIndex: 0,
            concept: "Cells",
            explanation: "The nucleus controls cell activity.",
          },
        ],
        generatedAt: "2026-04-17T09:00:00Z",
      },
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("button", { name: "View Quiz" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Regenerate" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Export" })).not.toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Summary" })).toBeInTheDocument();
    expect(screen.queryByText("Performance Overview")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "View Quiz" }));
    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/quiz");

    fireEvent.click(screen.getByRole("button", { name: "Regenerate" }));
    expect(screen.getByRole("dialog", { name: "Regenerate quiz?" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Quiz question count" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Regenerate Quiz" }));

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1", 10, "COLLEGE");
    });
  });

  it("shows the generate study pack guide for first-time users after creating a note", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "saved-note" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: null,
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Step 2: Generate your Study Pack")).toBeInTheDocument();
    await act(async () => {
      fireEvent.click(screen.getAllByRole("button", { name: "Generate Study Pack" }).at(-1) as HTMLButtonElement);
    });

    expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
  });

  it("auto-generates when the page is opened with generate=1", async () => {
    searchParamValues = { generate: "1" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await waitFor(() => {
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
    });
    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1", { scroll: false });
  });

  it("skips copied-note auto-generation when the copied note is already ready and starts Quick Review", async () => {
    searchParamValues = { generate: "1", startQuickReview: "1" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue({ sessionId: "qr-1" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await waitFor(() => {
      expect(startQuickReviewSession).toHaveBeenCalledWith("note-1");
    });
    expect(createStudyPackFromNote).not.toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/quick-review?sessionId=qr-1");
  });

  it("starts Quick Review automatically after copied-note generation finishes when requested", async () => {
    searchParamValues = { startQuickReview: "1" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue({ sessionId: "qr-1" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await waitFor(() => {
      expect(startQuickReviewSession).toHaveBeenCalledWith("note-1");
    });
    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1", { scroll: false });
    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/quick-review?sessionId=qr-1");
  });

  it("resolves generated metadata suggestions before an automatic Quick Review redirect", async () => {
    searchParamValues = { generate: "1", startQuickReview: "1" };
    window.sessionStorage.setItem("notelib-awaiting-suggestion:note-1", "1");
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
    });
    (getMyStudyPack as jest.Mock).mockResolvedValue({
      id: "sp-1",
      noteId: "note-1",
      title: "Suggested title",
      subject: "Biology",
      tags: ["cells"],
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue({ sessionId: "qr-1" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("AI Suggestions")).toBeInTheDocument();
    expect(startQuickReviewSession).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Skip" }));

    await waitFor(() => {
      expect(startQuickReviewSession).toHaveBeenCalledWith("note-1");
      expect(pushMock).toHaveBeenCalledWith("/notes/note-1/quick-review?sessionId=qr-1");
    });
  });

  it("shows copied Study Pack regeneration guidance only for copied ready notes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      copiedFromPublic: true,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("This Study Pack was copied. If the difficulty doesn't match your level, regenerate it to get a version tailored to you.")).toBeInTheDocument();
  });

  it("confirms before regenerating an owned ready Study Pack", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });
    (createStudyPackFromNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "GENERATING",
      studyPackId: "sp-1",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Regenerate" }));
    expect(screen.getByRole("dialog", { name: "Regenerate Study Pack?" })).toBeInTheDocument();
    expect(screen.getByText("This will replace the current summary, key concepts, and quiz with a new version tailored to your level. Your quiz history is preserved.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(createStudyPackFromNote).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Regenerate" }));
    fireEvent.click(screen.getByRole("button", { name: "Regenerate" }));

    await waitFor(() => {
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
    });
  });

  it("shows a first-study success banner after the first Study Pack is ready", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "study-pack-ready" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: null,
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Your Study Pack is ready!")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz?entry=mode-selection");
  });

  it("shows the generating state immediately after starting generation from note detail", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "BOARD_EXAM",
      productOnboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const generateButton = await screen.findByRole("button", { name: "Generate Study Pack" });
    await act(async () => {
      fireEvent.click(generateButton);
    });

    await waitFor(() => {
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
    });
    expect(await screen.findByText("Your Study Pack is being generated...")).toBeInTheDocument();
    expect(screen.getByText("Building your Study Pack...")).toBeInTheDocument();
  });

  it("polls a generating Study Pack until ready, then shows metadata suggestions and stops polling", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock)
      .mockResolvedValueOnce({ ...baseNote, studyPackStatus: "DRAFT" })
      .mockResolvedValueOnce({
        ...baseNote,
        courseProgram: null,
        studyPackStatus: "STUDY_PACK_READY",
        studyPackId: "sp-1",
        quickReviewAvailable: true,
        challengeQuizAvailable: true,
        summary: "Generated summary",
        keyConcepts: ["Cells"],
        quiz: [],
      });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const generateButton = await screen.findByRole("button", { name: "Generate Study Pack" });
    jest.useFakeTimers();
    await act(async () => {
      fireEvent.click(generateButton);
    });
    expect(await screen.findByText("Your Study Pack is being generated...")).toBeInTheDocument();

    await act(async () => {
      jest.advanceTimersByTime(3000);
    });
    expect(await screen.findByText("AI Suggestions")).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Quiz question count" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Skip" }));

    await waitFor(() => {
      expect(updateNote).not.toHaveBeenCalled();
      expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?created=1&tab=summary");
    });
    const getNoteCallsAfterReady = (getNote as jest.Mock).mock.calls.length;
    await act(async () => {
      jest.advanceTimersByTime(6000);
    });
    expect(getNote).toHaveBeenCalledTimes(getNoteCallsAfterReady);
    jest.useRealTimers();
  });

  it("shows a recoverable failure state and retries generation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "FAILED" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("We couldn't generate the Study Pack this time.")).toBeInTheDocument();
    await act(async () => {
      fireEvent.click(screen.getAllByRole("button", { name: "Retry Generation" })[0]);
    });

    await waitFor(() => {
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
    });
    expect(await screen.findByText("Your Study Pack is being generated...")).toBeInTheDocument();
  });

  it("applies selected AI metadata choices from note detail after async generation completes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock)
      .mockResolvedValueOnce({
        ...baseNote,
        courseProgram: null,
        title: "My Note",
        subject: "General Science",
        tags: ["review"],
        studyPackStatus: "DRAFT",
      })
      .mockResolvedValueOnce({
        ...baseNote,
        courseProgram: null,
        title: "My Note",
        subject: "General Science",
        tags: ["review"],
        studyPackStatus: "STUDY_PACK_READY",
        studyPackId: "sp-1",
        quickReviewAvailable: true,
        challengeQuizAvailable: true,
      });
    (getMyStudyPack as jest.Mock).mockResolvedValue({
      id: "sp-1",
      noteId: "note-1",
      title: "Suggested Title",
      subject: "Biology",
      tags: ["Review", "cells", "Memory"],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const generateButton = await screen.findByRole("button", { name: "Generate Study Pack" });
    jest.useFakeTimers();
    await act(async () => {
      fireEvent.click(generateButton);
    });
    expect(await screen.findByText("Your Study Pack is being generated...")).toBeInTheDocument();
    await act(async () => {
      jest.advanceTimersByTime(3000);
    });

    expect(await screen.findByText("AI Suggestions")).toBeInTheDocument();
    expect(screen.getAllByText("Memory").length).toBeGreaterThan(0);
    expect(screen.getByText("Already on your note")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Use AI Subject"));
    fireEvent.click(screen.getByLabelText("Merge My Tags + AI Tags"));
    fireEvent.click(screen.getByRole("button", { name: "Apply Changes" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        title: "My Note",
        subject: "Biology",
        courseProgramText: null,
        tags: ["review", "cells", "Memory"],
      }));
      expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?created=1&tab=summary");
    });
    jest.useRealTimers();
  });

  it("shows the premium monthly-limit modal when Generate is clicked at the premium limit", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PRO",
      limits: {
        studyPacksPerMonth: 100,
        challengeQuizzesPerMonth: 50,
        adaptivePracticePerMonth: 30,
        ocrPerMonth: 100,
      },
      usage: {
        studyPacksUsed: 100,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 1,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 0,
        challengeQuizzesRemaining: 49,
        adaptivePracticeRemaining: 29,
        ocrRemaining: 100,
      },
      usageCycle: {
        startsAt: "2026-03-20T00:00:00Z",
        endsAt: "2026-04-20T00:00:00Z",
      },
      features: {
        adaptivePracticeAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByRole("dialog", { name: "You’ve reached your study pack limit for this month" })).toBeInTheDocument();
    expect(screen.getByText(/Your study pack limit resets on April 20\./)).toBeInTheDocument();
  });

  it("shows the upgrade paywall modal when Generate is clicked at the free Study Pack limit", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
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
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 0,
        challengeQuizzesRemaining: 4,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      usageCycle: {
        startsAt: "2026-03-20T00:00:00Z",
        endsAt: "2026-04-20T00:00:00Z",
      },
      features: {
        adaptivePracticeAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("You've reached your Study Pack limit")).toBeInTheDocument();
    expect(screen.queryByText("You've reached your Study Pack limit for this month")).not.toBeInTheDocument();
  });

  it("collapses Performance Overview stats by default for a ready note and expands them on demand", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });
    (getQuickReviewPerformanceSummary as jest.Mock).mockResolvedValue({ attempts: 3, lastScorePercentage: 90 });
    (getChallengeQuizPerformanceSummary as jest.Mock).mockResolvedValue({ attempts: 2, bestScorePercentage: 85 });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Performance Overview")).toBeInTheDocument();
    expect(screen.queryByText(/Sessions: 3/)).not.toBeInTheDocument();
    const toggle = screen.getByRole("button", { name: "Show Performance" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(toggle);

    expect(screen.getByText(/Sessions: 3/)).toBeInTheDocument();
    expect(screen.getByText(/Sessions: 2/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Hide Performance" })).toHaveAttribute("aria-expanded", "true");
  });

  it("shows quiz view when tab=quiz is requested", async () => {
    searchParamValues = { tab: "quiz" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quizMastered: true,
      quizMasteredAt: "2026-03-21T10:30:00Z",
      quizCount: 1,
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("tab", { name: "Quiz" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "Key Concepts" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Full Notes" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Summary" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Practice Quiz" })).toBeInTheDocument();
  });

  it("keeps the locked Quiz tab reachable and renders the mastery panel without mounting answers", async () => {
    searchParamValues = { tab: "quiz" };
    (getAuthUser as jest.Mock).mockReturnValue({
      role: "USER",
      profileType: "STUDENT",
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      quizMastered: false,
      quizCount: 3,
      quiz: createQuiz(3),
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue({ sessionId: "quick-1" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const quizTab = await screen.findByRole("tab", { name: "Quiz, locked" });
    expect(quizTab).toHaveAttribute("aria-selected", "true");
    expect(quizTab).not.toBeDisabled();
    quizTab.focus();
    expect(quizTab).toHaveFocus();
    expect(screen.getByRole("heading", { name: "Quiz locked" })).toBeInTheDocument();
    expect(screen.getByText("Score 3/3 on Quick Review to unlock the Quiz.")).toBeInTheDocument();
    // Must name the button that actually exists on the Quick Review results screen
    // (quick-review/page.tsx). "Redo Mistakes" is not a label anywhere in the product.
    expect(screen.getByText(/Retry Incorrect Questions/)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Practice Quiz" })).not.toBeInTheDocument();
    expect(screen.queryByText("Question 1")).not.toBeInTheDocument();
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(expect.objectContaining({
      eventType: "STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK",
    }));

    fireEvent.click(screen.getAllByRole("button", { name: "Start Quick Review" }).at(-1)!);
    await waitFor(() => expect(startQuickReviewSession).toHaveBeenCalledWith("note-1"));
  });

  it.each([0, undefined])("uses length-agnostic lock copy when quizCount is %s", async (quizCount) => {
    searchParamValues = { tab: "quiz" };
    (getAuthUser as jest.Mock).mockReturnValue({ role: "USER", profileType: "STUDENT" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quizMastered: undefined,
      quizCount,
      quiz: createQuiz(1),
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Answer every Quick Review question correctly to unlock the Quiz.")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Practice Quiz" })).not.toBeInTheDocument();
  });

  it("renders an unlocked learner's quiz and tracks the tab open only once across re-renders", async () => {
    searchParamValues = { tab: "quiz" };
    (getAuthUser as jest.Mock).mockReturnValue({ role: "USER", profileType: "STUDENT" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quizMastered: true,
      quizMasteredAt: "2026-03-21T10:30:00Z",
      quizCount: 1,
      quiz: createQuiz(1),
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Practice Quiz" })).toBeInTheDocument();
    await waitFor(() => expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK",
      entityId: "sp-1",
      metadata: { noteId: "note-1" },
    }));
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect((trackAnalyticsEvent as jest.Mock).mock.calls.filter(([event]) => (
      event.eventType === "STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK"
    ))).toHaveLength(1);

    fireEvent.click(screen.getByRole("tab", { name: "Summary" }));
    searchParamValues = { tab: "summary" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);
    fireEvent.click(screen.getByRole("tab", { name: "Quiz" }));
    searchParamValues = { tab: "quiz" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    await waitFor(() => expect((trackAnalyticsEvent as jest.Mock).mock.calls.filter(([event]) => (
      event.eventType === "STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK"
    ))).toHaveLength(2));
  });

  it.each([
    ["teacher", { role: "USER", profileType: "TEACHER" }],
    ["admin", { role: "ADMIN", profileType: "STUDENT" }],
  ])("lets an unmastered %s curator view the quiz without unlock analytics", async (_label, authUser) => {
    searchParamValues = { tab: "quiz" };
    (getAuthUser as jest.Mock).mockReturnValue(authUser);
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quizMastered: false,
      quizCount: 1,
      quiz: createQuiz(1),
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Practice Quiz" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Quiz locked" })).not.toBeInTheDocument();
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(expect.objectContaining({
      eventType: "STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK",
    }));
  });

  it("keeps the existing empty Quiz state instead of showing the mastery lock", async () => {
    searchParamValues = { tab: "quiz" };
    (getAuthUser as jest.Mock).mockReturnValue({ role: "USER", profileType: "STUDENT" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quizMastered: false,
      quizCount: 0,
      quiz: [],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Practice Quiz" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Quiz locked" })).not.toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Quiz" })).not.toBeDisabled();
  });

  it("nudges toward Full Notes on the Quiz tab until the learner has viewed it", async () => {
    searchParamValues = { tab: "quiz" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quizMastered: true,
      quizMasteredAt: "2026-03-21T10:30:00Z",
      quizCount: 1,
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Haven't reviewed the full notes yet? Skim the source material before testing yourself.")).toBeInTheDocument();

    // Switch to Full Notes via the tab bar directly (not the tip's own action button),
    // so this exercises the condition (hasViewedFullNotes) rather than the tip's dismiss path.
    fireEvent.click(screen.getByRole("tab", { name: "Full Notes" }));
    searchParamValues = { tab: "full-notes" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(screen.getByRole("tab", { name: "Quiz" }));
    searchParamValues = { tab: "quiz" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(screen.queryByText("Haven't reviewed the full notes yet? Skim the source material before testing yourself.")).not.toBeInTheDocument();
  });

  it("sorts Key Concepts by readiness — struggling and due first, mastered last", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells", "Genetics", "Evolution", "Ecology"],
    });
    (getConceptHealth as jest.Mock).mockResolvedValue([
      { concept: "Cells", readinessStatus: "MASTERED", lastCorrectAt: null, lastIncorrectAt: null, isStruggling: false, isDue: false, daysSinceReview: null },
      { concept: "Genetics", readinessStatus: "DUE", lastCorrectAt: null, lastIncorrectAt: null, isStruggling: false, isDue: true, daysSinceReview: null },
      { concept: "Evolution", readinessStatus: "NOT_STARTED", lastCorrectAt: null, lastIncorrectAt: null, isStruggling: false, isDue: true, daysSinceReview: null },
      { concept: "Ecology", readinessStatus: "DUE", lastCorrectAt: null, lastIncorrectAt: null, isStruggling: true, isDue: true, daysSinceReview: null },
    ]);

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("tab", { name: "Key Concepts" }));
    searchParamValues = { tab: "key-concepts" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    const items = await screen.findAllByRole("listitem");
    const conceptOrder = items
      .map((item) => item.textContent ?? "")
      .filter((text) => ["Cells", "Genetics", "Evolution", "Ecology"].some((concept) => text.includes(concept)));

    expect(conceptOrder[0]).toContain("Ecology");
    expect(conceptOrder[1]).toContain("Genetics");
    expect(conceptOrder[2]).toContain("Evolution");
    expect(conceptOrder[3]).toContain("Cells");
  });

  it("scrolls to and highlights the first matching key concept on a direct concept link", async () => {
    const previousPath = `${globalThis.location.pathname}${globalThis.location.search}${globalThis.location.hash}`;
    const originalRequestAnimationFrame = globalThis.requestAnimationFrame;
    const originalCancelAnimationFrame = globalThis.cancelAnimationFrame;
    const originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
    const scrollIntoView = jest.fn();

    globalThis.requestAnimationFrame = ((callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    }) as typeof globalThis.requestAnimationFrame;
    globalThis.cancelAnimationFrame = jest.fn();
    HTMLElement.prototype.scrollIntoView = scrollIntoView;
    globalThis.history.replaceState({}, "", "/notes/note-1?tab=key-concepts#concept-cells");
    searchParamValues = { tab: "key-concepts" };
    searchParamsMock = createSearchParamsMock();
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      keyConcepts: ["Cells", "  cells  ", "Genetics"],
    });

    try {
      render(<PrivateNoteDetailPageClient routeId="note-1" />);

      const concepts = await screen.findAllByText(/cells/i);
      const firstConcept = concepts.find((element) => element.textContent === "Cells");
      expect(firstConcept?.closest("li")).toHaveAttribute("id", "concept-cells");
      await waitFor(() => {
        expect(scrollIntoView).toHaveBeenCalledWith({ behavior: "smooth", block: "start" });
      });
      expect(document.getElementById("concept-cells")).toBe(firstConcept?.closest("li"));
      expect(firstConcept?.closest("li")).toHaveClass("bg-amber-500/15");
    } finally {
      globalThis.requestAnimationFrame = originalRequestAnimationFrame;
      globalThis.cancelAnimationFrame = originalCancelAnimationFrame;
      HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
      globalThis.history.replaceState({}, "", previousPath || "/");
      searchParamValues = {};
      searchParamsMock = createSearchParamsMock();
    }
  });

  it("re-scrolls to the deep-linked concept after the readiness sort relocates it", async () => {
    const previousPath = `${globalThis.location.pathname}${globalThis.location.search}${globalThis.location.hash}`;
    const originalRequestAnimationFrame = globalThis.requestAnimationFrame;
    const originalCancelAnimationFrame = globalThis.cancelAnimationFrame;
    const originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
    const scrollIntoView = jest.fn();
    let resolveConceptHealth: (entries: unknown[]) => void = () => {};

    globalThis.requestAnimationFrame = ((callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    }) as typeof globalThis.requestAnimationFrame;
    globalThis.cancelAnimationFrame = jest.fn();
    HTMLElement.prototype.scrollIntoView = scrollIntoView;
    globalThis.history.replaceState({}, "", "/notes/note-1?tab=key-concepts#concept-cells");
    searchParamValues = { tab: "key-concepts" };
    searchParamsMock = createSearchParamsMock();
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      // "Cells" is deep-linked but sorts last (generation order) until ConceptHealth
      // loads and its "isStruggling" flag promotes it to the top of the list.
      keyConcepts: ["Genetics", "Evolution", "Ecology", "Cells"],
    });
    (getConceptHealth as jest.Mock).mockImplementation(
      () => new Promise((resolve) => {
        resolveConceptHealth = resolve;
      }),
    );

    try {
      render(<PrivateNoteDetailPageClient routeId="note-1" />);

      // Initial scroll(s) fire against the unsorted (generation-order) position,
      // before ConceptHealth has loaded — exact call count isn't the contract here,
      // only that a scroll happened and landed on "Cells" pre-sort.
      await waitFor(() => {
        expect(scrollIntoView.mock.calls.length).toBeGreaterThanOrEqual(1);
      });
      const cellsAnchor = document.getElementById("concept-cells");
      expect(cellsAnchor).toHaveClass("bg-amber-500/15");
      const callsBeforeSort = scrollIntoView.mock.calls.length;

      await act(async () => {
        resolveConceptHealth([
          { concept: "Genetics", readinessStatus: "NOT_STARTED", lastCorrectAt: null, lastIncorrectAt: null, isStruggling: false, isDue: true, daysSinceReview: null },
          { concept: "Evolution", readinessStatus: "NOT_STARTED", lastCorrectAt: null, lastIncorrectAt: null, isStruggling: false, isDue: true, daysSinceReview: null },
          { concept: "Ecology", readinessStatus: "NOT_STARTED", lastCorrectAt: null, lastIncorrectAt: null, isStruggling: false, isDue: true, daysSinceReview: null },
          { concept: "Cells", readinessStatus: "DUE", lastCorrectAt: null, lastIncorrectAt: null, isStruggling: true, isDue: true, daysSinceReview: null },
        ]);
      });

      // The readiness sort promotes the struggling "Cells" concept to the top of the
      // list — a real DOM relocation, not just a re-render in place.
      const items = await screen.findAllByRole("listitem");
      expect(items[0]).toHaveAttribute("id", "concept-cells");

      // The scroll/highlight effect must re-run against the concept's new position,
      // not leave the page scrolled to where it used to sit before the sort applied.
      await waitFor(() => {
        expect(scrollIntoView.mock.calls.length).toBeGreaterThan(callsBeforeSort);
      });
      expect(document.getElementById("concept-cells")).toHaveClass("bg-amber-500/15");
    } finally {
      globalThis.requestAnimationFrame = originalRequestAnimationFrame;
      globalThis.cancelAnimationFrame = originalCancelAnimationFrame;
      HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
      globalThis.history.replaceState({}, "", previousPath || "/");
      searchParamValues = {};
      searchParamsMock = createSearchParamsMock();
    }
  });

  it("switches study pack views through tabs without reloading", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quizMastered: true,
      quizMasteredAt: "2026-03-21T10:30:00Z",
      quizCount: 1,
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const summaryTab = await screen.findByRole("tab", { name: "Summary" });
    const quizTab = screen.getByRole("tab", { name: "Quiz" });

    expect(summaryTab).toHaveAttribute("aria-selected", "true");
    expect(getNote).toHaveBeenCalledTimes(1);

    fireEvent.click(quizTab);
    searchParamValues = { tab: "quiz" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=quiz", { scroll: false });
    expect(getNote).toHaveBeenCalledTimes(1);
    expect(screen.queryByText("Loading note...")).not.toBeInTheDocument();
    expect(await screen.findByRole("tab", { name: "Quiz" })).toHaveAttribute("aria-selected", "true");
  });

  it("uses the summary CTA to switch to Full Notes without refetching", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      content: "Original note body for review.",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "View Full Notes →" }));
    searchParamValues = { tab: "full-notes" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=full-notes", { scroll: false });
    expect(getNote).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("tab", { name: "Full Notes" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText(/Original note body for review\./i)).toBeInTheDocument();
  });

  it("shows the full original note content in the Full Notes tab without refetching", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      content: "Line one of the original note.\n\nLine two stays visible.",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("tab", { name: "Full Notes" }));
    searchParamValues = { tab: "full-notes" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=full-notes", { scroll: false });
    expect(getNote).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("tab", { name: "Full Notes" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("heading", { name: "Full Notes" })).toBeInTheDocument();
    expect(screen.getByText(/Line one of the original note\./i)).toBeInTheDocument();
    expect(screen.getByText(/Line two stays visible\./i)).toBeInTheDocument();
  });

  it("routes paid users into the shared Challenge Quiz mode-selection entry without showing the paywall modal", async () => {
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
        studyPacksUsed: 12,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 1,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 88,
        challengeQuizzesRemaining: 49,
        adaptivePracticeRemaining: 29,
        ocrRemaining: 100,
      },
      features: {
        adaptivePracticeAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz?entry=mode-selection");
    expect(screen.queryByText("Unlock Exam Mode")).not.toBeInTheDocument();
  });
});
