import { fireEvent, render, screen } from "@testing-library/react";
import { PublicLibraryDiscoveryFeedbackPrompt } from "./public-library-discovery-feedback-prompt";
import { FIRST_QUIZ_FEEDBACK_PROMPT_ID } from "@/components/feedback/quiz-feedback-panel";
import { getDashboardOverview } from "@/lib/api";
import { getUserScopedGuidanceId, markTipSeenThisSession } from "@/lib/guidance";
import {
  markEarlyLifecycleFeedbackSignalShownThisSession,
  markPublicLibraryNoteAdoptedThisSession,
  recordPublicNoteViewedWithoutAdopting,
} from "@/lib/early-lifecycle-feedback-signals";

jest.mock("@/lib/api", () => ({
  getDashboardOverview: jest.fn(),
}));

function viewThreeNotesWithoutAdopting() {
  recordPublicNoteViewedWithoutAdopting("note-1");
  recordPublicNoteViewedWithoutAdopting("note-2");
  recordPublicNoteViewedWithoutAdopting("note-3");
}

describe("PublicLibraryDiscoveryFeedbackPrompt", () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
    globalThis.sessionStorage.clear();
    (getDashboardOverview as jest.Mock).mockReset();
  });

  it("renders for a light user who browsed 3+ notes without adopting one", async () => {
    (getDashboardOverview as jest.Mock).mockResolvedValue({ totalNoteCount: 1 });
    viewThreeNotesWithoutAdopting();

    render(<PublicLibraryDiscoveryFeedbackPrompt userId="user-1" />);
    expect(await screen.findByText("What's making it hard to find something to study?")).toBeInTheDocument();
  });

  it("does not render for a veteran with a high note count", async () => {
    (getDashboardOverview as jest.Mock).mockResolvedValue({ totalNoteCount: 40 });
    viewThreeNotesWithoutAdopting();

    render(<PublicLibraryDiscoveryFeedbackPrompt userId="user-1" />);
    await new Promise((resolve) => { globalThis.setTimeout(resolve, 0); });
    expect(screen.queryByText("What's making it hard to find something to study?")).not.toBeInTheDocument();
  });

  it("does not render if the user has already adopted a note this session", async () => {
    (getDashboardOverview as jest.Mock).mockResolvedValue({ totalNoteCount: 1 });
    viewThreeNotesWithoutAdopting();
    markPublicLibraryNoteAdoptedThisSession();

    render(<PublicLibraryDiscoveryFeedbackPrompt userId="user-1" />);
    expect(getDashboardOverview).not.toHaveBeenCalled();
    expect(screen.queryByText("What's making it hard to find something to study?")).not.toBeInTheDocument();
  });

  it("does not render below the view threshold", () => {
    (getDashboardOverview as jest.Mock).mockResolvedValue({ totalNoteCount: 1 });
    recordPublicNoteViewedWithoutAdopting("note-1");
    recordPublicNoteViewedWithoutAdopting("note-2");

    render(<PublicLibraryDiscoveryFeedbackPrompt userId="user-1" />);
    expect(getDashboardOverview).not.toHaveBeenCalled();
    expect(screen.queryByText("What's making it hard to find something to study?")).not.toBeInTheDocument();
  });

  it("does not render if another early-lifecycle prompt already fired this session", () => {
    (getDashboardOverview as jest.Mock).mockResolvedValue({ totalNoteCount: 1 });
    viewThreeNotesWithoutAdopting();
    markEarlyLifecycleFeedbackSignalShownThisSession();

    render(<PublicLibraryDiscoveryFeedbackPrompt userId="user-1" />);
    expect(getDashboardOverview).not.toHaveBeenCalled();
    expect(screen.queryByText("What's making it hard to find something to study?")).not.toBeInTheDocument();
  });

  it("does not render if the first-quiz-ever prompt already fired this session", () => {
    (getDashboardOverview as jest.Mock).mockResolvedValue({ totalNoteCount: 1 });
    viewThreeNotesWithoutAdopting();
    markTipSeenThisSession(getUserScopedGuidanceId(FIRST_QUIZ_FEEDBACK_PROMPT_ID, "user-1"));

    render(<PublicLibraryDiscoveryFeedbackPrompt userId="user-1" />);
    expect(getDashboardOverview).not.toHaveBeenCalled();
    expect(screen.queryByText("What's making it hard to find something to study?")).not.toBeInTheDocument();
  });

  it("hides immediately when the user picks 'Just browsing for now'", async () => {
    (getDashboardOverview as jest.Mock).mockResolvedValue({ totalNoteCount: 1 });
    viewThreeNotesWithoutAdopting();

    render(<PublicLibraryDiscoveryFeedbackPrompt userId="user-1" />);
    expect(await screen.findByText("What's making it hard to find something to study?")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Just browsing for now" }));
    expect(screen.queryByText("What's making it hard to find something to study?")).not.toBeInTheDocument();
  });
});
