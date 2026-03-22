import { fireEvent, render, screen } from "@testing-library/react";
import { PrivateNoteDetailPageClient } from "./private-note-detail-page-client";
import { getNote, updateNote, updateNoteVisibility } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};
const searchParamsMock = {
  get: () => null,
  toString: () => "",
};

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
}));

jest.mock("@/lib/api", () => ({
  copyNote: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  deleteNote: jest.fn(),
  getChallengeQuizPerformanceSummary: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  isEmailNotVerifiedError: () => false,
  updateNote: jest.fn(),
  updateNoteVisibility: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
  startQuickReviewSession: jest.fn(),
}));

const baseNote = {
  id: "note-1",
  title: "Test Note",
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

describe("PrivateNoteDetailPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (getNote as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (updateNote as jest.Mock).mockReset();
    (updateNoteVisibility as jest.Mock).mockReset();
  });

  it("routes Edit to note editor for draft note", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    const editButton = screen.getByRole("button", { name: "Edit" });
    fireEvent.click(editButton);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/edit");
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
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));

    expect(pushMock).not.toHaveBeenCalledWith("/notes/note-1/edit");
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Note content cannot be edited after generating a Study Pack. You can still update the title, subject, and tags.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Share" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Quick Review" })).not.toBeInTheDocument();
  });

  it("shows private-share modal and then opens share-link modal after making note public", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PRIVATE" });
    (updateNoteVisibility as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PUBLIC" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Share" }));

    expect(screen.getByText("This note is private")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Make Public & Share" }));

    expect(updateNoteVisibility).toHaveBeenCalledWith("note-1", "PUBLIC");
    expect(await screen.findByText("Share this note")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copy Link" })).toBeInTheDocument();
  });
});
