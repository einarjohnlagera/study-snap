import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LongExamPage from "./page";
import { getAuthUser } from "@/lib/auth";
import {
  forfeitLongExamSession,
  getActiveLongExamSession,
  getCollection,
  getMe,
  getNote,
  listNotes,
  startLongExam,
} from "@/lib/api";

const pushMock = jest.fn();
const replaceMock = jest.fn();
let searchParamsMock = new URLSearchParams();

jest.mock("next/navigation", () => ({
  useParams: () => ({ id: "note-1" }),
  usePathname: () => "/notes/note-1/long-exam",
  useSearchParams: () => searchParamsMock,
  useRouter: () => ({
    push: pushMock,
    replace: replaceMock,
  }),
}));

jest.mock("@/components/study-pack/quiz-session-guard", () => ({
  useQuizSessionGuard: () => ({
    requestLeave: jest.fn(),
    LeaveQuizModal: () => null,
  }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeLongExamSession: jest.fn(),
  forfeitLongExamSession: jest.fn(),
  getActiveLongExamSession: jest.fn(),
  getCollection: jest.fn(),
  getLongExamSession: jest.fn(),
  getMe: jest.fn(),
  getNote: jest.fn(),
  listNotes: jest.fn(),
  resumeLongExamSession: jest.fn(),
  saveLongExamProgress: jest.fn(),
  startLongExam: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("LongExamPage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    searchParamsMock = new URLSearchParams();
    (forfeitLongExamSession as jest.Mock).mockReset();
    (getCollection as jest.Mock).mockReset();
    (startLongExam as jest.Mock).mockReset();
    (getActiveLongExamSession as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
    });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Comprehensive Biology",
      subject: "Biology",
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
    });
    // No active session, but the endpoint still returns the caller's Long Exam quota
    // (mirrors the backend's buildEmptyStartResponse). monthlyLimit must be high enough
    // that selecting extra source notes does not trip the per-note quota gate.
    (getActiveLongExamSession as jest.Mock).mockResolvedValue({
      sessionId: null,
      status: null,
      usedThisMonth: 0,
      monthlyLimit: 10,
    });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (getCollection as jest.Mock).mockResolvedValue({
      id: "collection-1",
      items: [],
    });
    (startLongExam as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "GENERATING",
      quiz: [],
      totalQuestions: 25,
      difficulty: "mixed",
      canResume: false,
      timeLimitSeconds: 0,
      timerStartedAtEpochSeconds: 0,
      sourceNoteRefs: [],
    });
  });

  it("renders the polished Long Exam prestart setup rhythm", async () => {
    render(<LongExamPage />);

    expect(await screen.findByRole("heading", { name: "Long Exam" })).toBeInTheDocument();
    expect(screen.getByText("A comprehensive mastery sitting built from your note. Treat it as a focused study session, not a quick quiz.")).toBeInTheDocument();
    expect(screen.getByText("Comprehensive Biology")).toBeInTheDocument();
    expect(screen.getByText("What to expect")).toBeInTheDocument();
    expect(screen.getByText("Fixed question set")).toBeInTheDocument();
    expect(screen.getByText("Generated upfront — no progressive add. 25 questions from this note.")).toBeInTheDocument();
    expect(screen.getByText("The clock does not pause")).toBeInTheDocument();
    expect(screen.getByText("Scored against every question")).toBeInTheDocument();
    expect(screen.getByText("Mastery report at the end")).toBeInTheDocument();
    expect(screen.getByText("1 note · 25 questions")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Begin Long Exam" })).toBeInTheDocument();
  });

  it("shows Long Exam setup to free users and opens the paywall from the Start CTA", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      profileType: "STUDENT",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });

    render(<LongExamPage />);

    expect(await screen.findByRole("heading", { name: "Long Exam" })).toBeInTheDocument();
    expect(screen.getByText("What to expect")).toBeInTheDocument();
    expect(getActiveLongExamSession).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog", { name: "Long Exam Mode" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Unlock Long Exam - Pro" }));

    expect(await screen.findByRole("dialog", { name: "Long Exam Mode" })).toBeInTheDocument();
    expect(startLongExam).not.toHaveBeenCalled();
  });

  it("routes Choose another mode back to the shared mode picker", async () => {
    render(<LongExamPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Choose another mode" }));

    expect(pushMock).toHaveBeenCalledWith("/study-packs/sp-1/challenge-quiz");
  });

  it("renders selectable same-subject source notes", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-2",
        title: "Cell Transport",
        subject: "Biology",
        studyPackId: "sp-2",
        studyPackStatus: "STUDY_PACK_READY",
      },
      {
        id: "note-3",
        title: "Organic Chemistry",
        subject: "Chemistry",
        studyPackId: "sp-3",
        studyPackStatus: "STUDY_PACK_READY",
      },
    ]);

    render(<LongExamPage />);

    expect(await screen.findByText("Span this exam across more notes")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Cell Transport/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Organic Chemistry/ })).not.toBeInTheDocument();
  });

  it("scopes collection launches to plan notes and preselects up to the Long Exam cap", async () => {
    searchParamsMock = new URLSearchParams("collectionId=collection-1");
    (getCollection as jest.Mock).mockResolvedValue({
      id: "collection-1",
      items: [
        { noteId: "note-1", position: 0, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-1" },
        { noteId: "note-2", position: 1, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-2" },
        { noteId: "note-3", position: 2, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-3" },
        { noteId: "note-4", position: 3, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-4" },
        { noteId: "note-5", position: 4, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-5" },
      ],
    });
    (listNotes as jest.Mock).mockResolvedValue([
      { id: "note-2", title: "Plan Two", subject: "Biology", studyPackId: "sp-2", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-3", title: "Plan Three", subject: "Biology", studyPackId: "sp-3", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-4", title: "Plan Four", subject: "Biology", studyPackId: "sp-4", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-5", title: "Plan Five", subject: "Biology", studyPackId: "sp-5", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-9", title: "Outside Plan", subject: "Biology", studyPackId: "sp-9", studyPackStatus: "STUDY_PACK_READY" },
    ]);

    render(<LongExamPage />);

    expect(await screen.findByRole("button", { name: /Plan Two/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Plan Three/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Plan Four/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Plan Five/ })).toHaveAttribute("aria-pressed", "false");
    expect(screen.queryByRole("button", { name: /Outside Plan/ })).not.toBeInTheDocument();
    expect(screen.getByText("4 notes · 25 questions")).toBeInTheDocument();
  });

  it("falls back to the normal source picker when collection lookup fails", async () => {
    searchParamsMock = new URLSearchParams("collectionId=missing");
    (getCollection as jest.Mock).mockRejectedValue(new Error("Not found"));
    (listNotes as jest.Mock).mockResolvedValue([
      { id: "note-2", title: "Fallback Same Subject", subject: "Biology", studyPackId: "sp-2", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-3", title: "Other Subject", subject: "Chemistry", studyPackId: "sp-3", studyPackStatus: "STUDY_PACK_READY" },
    ]);

    render(<LongExamPage />);

    expect(await screen.findByRole("button", { name: /Fallback Same Subject/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Other Subject/ })).not.toBeInTheDocument();
    expect(screen.getByText("1 note · 25 questions")).toBeInTheDocument();
  });

  it("updates source summary and sends selected additional study pack ids", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-2",
        title: "Cell Transport",
        subject: "Biology",
        studyPackId: "sp-2",
        studyPackStatus: "STUDY_PACK_READY",
      },
      {
        id: "note-3",
        title: "Genetics Lab",
        subject: "Biology",
        studyPackId: "sp-3",
        studyPackStatus: "STUDY_PACK_READY",
      },
    ]);

    render(<LongExamPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Cell Transport/ }));
    fireEvent.click(screen.getByRole("button", { name: /Genetics Lab/ }));
    expect(screen.getByText("3 notes · 25 questions")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Begin Long Exam" }));

    expect(startLongExam).toHaveBeenCalledWith("sp-1", {
      additionalStudyPackIds: ["sp-2", "sp-3"],
    });
  });

  it("omits additional study pack ids when no extra notes are selected", async () => {
    render(<LongExamPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Begin Long Exam" }));

    expect(startLongExam).toHaveBeenCalledWith("sp-1", {});
  });

  it("silently forfeits an IN_PROGRESS session whose timer has expired so the prompt is not shown", async () => {
    const startedAt = Math.floor(Date.now() / 1000) - 60 * 60 * 3; // 3 hours ago
    (getActiveLongExamSession as jest.Mock).mockResolvedValue({
      sessionId: "stale-session-1",
      status: "IN_PROGRESS",
      quiz: [],
      totalQuestions: 25,
      difficulty: "mixed",
      canResume: true,
      timeLimitSeconds: 25 * 90,
      timerStartedAtEpochSeconds: startedAt,
      sourceNoteRefs: [],
      usedThisMonth: 1,
      monthlyLimit: 10,
    });
    (forfeitLongExamSession as jest.Mock).mockResolvedValue({ message: "ok" });

    render(<LongExamPage />);

    await screen.findByRole("heading", { name: "Long Exam" });
    await waitFor(() => {
      expect(forfeitLongExamSession).toHaveBeenCalledWith("stale-session-1");
    });
    expect(screen.queryByText("You have an active session.")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Begin Long Exam" })).toBeInTheDocument();
  });

  it("keeps the resume prompt when the IN_PROGRESS session's timer is still valid", async () => {
    const startedAt = Math.floor(Date.now() / 1000) - 30; // 30 seconds ago, plenty of time left
    (getActiveLongExamSession as jest.Mock).mockResolvedValue({
      sessionId: "active-session-1",
      status: "IN_PROGRESS",
      quiz: [],
      totalQuestions: 25,
      difficulty: "mixed",
      canResume: true,
      timeLimitSeconds: 25 * 90,
      timerStartedAtEpochSeconds: startedAt,
      sourceNoteRefs: [],
      usedThisMonth: 1,
      monthlyLimit: 10,
    });
    (forfeitLongExamSession as jest.Mock).mockResolvedValue({ message: "ok" });

    render(<LongExamPage />);

    expect(await screen.findByText("You have an active session.")).toBeInTheDocument();
    expect(forfeitLongExamSession).not.toHaveBeenCalled();
  });
});
