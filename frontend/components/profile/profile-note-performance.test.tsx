import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { ProfileNotePerformance } from "./profile-note-performance";
import { getUserNotePerformanceSummary, type NotePerformanceSummaryResponse } from "@/lib/api";

const routerMock = { push: jest.fn() };

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/api", () => ({
  getUserNotePerformanceSummary: jest.fn(),
}));

const makeNote = (overrides: Partial<NotePerformanceSummaryResponse> = {}): NotePerformanceSummaryResponse => ({
  noteId: "note-1",
  noteTitle: "Biology Review",
  bestScore: 90,
  averageScore: 78,
  attemptCount: 3,
  lastAttemptedAt: "2026-04-10T10:00:00Z",
  bestSessionId: "session-1",
  bestSessionMode: "QUICK_REVIEW",
  ...overrides,
});

describe("ProfileNotePerformance", () => {
  beforeEach(() => {
    routerMock.push.mockReset();
    (getUserNotePerformanceSummary as jest.Mock).mockReset();
  });

  it("shows loading skeletons while fetching", () => {
    (getUserNotePerformanceSummary as jest.Mock).mockReturnValue(new Promise(() => {}));
    render(<ProfileNotePerformance />);
    expect(document.querySelectorAll(".animate-pulse").length).toBeGreaterThan(0);
  });

  it("renders note performance items on success", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote()]);
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.getByText("Biology Review")).toBeInTheDocument();
    });
    expect(screen.getByText(/Best: 90%/)).toBeInTheDocument();
    expect(screen.getByText(/3 sessions/)).toBeInTheDocument();
  });

  it("shows empty state when no notes returned", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([]);
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.getByText("No quiz sessions yet.")).toBeInTheDocument();
    });
    expect(screen.getByText(/Start a quiz to see your performance/)).toBeInTheDocument();
  });

  it("shows error state on API failure", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockRejectedValue(new Error("Network error"));
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.getByText("Could not load note performance.")).toBeInTheDocument();
    });
    expect(screen.getByText("Network error")).toBeInTheDocument();
  });

  it("shows ⭐ Perfect badge at 100%", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote({ bestScore: 100 })]);
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.getByText("⭐ Perfect")).toBeInTheDocument();
    });
  });

  it("shows Top Score badge at ≥80%", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote({ bestScore: 85 })]);
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.getByText("Top Score")).toBeInTheDocument();
    });
  });

  it("shows no badge below 80%", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote({ bestScore: 70 })]);
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.queryByText("⭐ Perfect")).not.toBeInTheDocument();
      expect(screen.queryByText("Top Score")).not.toBeInTheDocument();
    });
  });

  it("shows average score when present", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote({ averageScore: 75 })]);
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.getByText(/Avg: 75%/)).toBeInTheDocument();
    });
  });

  it("omits average score when null", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote({ averageScore: null })]);
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.queryByText(/Avg:/)).not.toBeInTheDocument();
    });
  });

  it("shows singular 'attempt' for attemptCount=1", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote({ attemptCount: 1 })]);
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.getByText(/1 session[^s]/)).toBeInTheDocument();
    });
  });

  it("navigates to best session with source=profile on click", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote()]);
    render(<ProfileNotePerformance />);
    await waitFor(() => screen.getByText("Biology Review"));
    fireEvent.click(screen.getByRole("button", { name: /Biology Review/ }));
    expect(routerMock.push).toHaveBeenCalledWith(
      expect.stringContaining("source=profile"),
    );
    expect(routerMock.push).toHaveBeenCalledWith(
      expect.stringContaining("/notes/note-1/sessions/session-1"),
    );
  });

  it("navigates using mode=quick-review for QUICK_REVIEW sessions", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote({ bestSessionMode: "QUICK_REVIEW" })]);
    render(<ProfileNotePerformance />);
    await waitFor(() => screen.getByText("Biology Review"));
    fireEvent.click(screen.getByRole("button", { name: /Biology Review/ }));
    expect(routerMock.push).toHaveBeenCalledWith(expect.stringContaining("mode=quick-review"));
  });

  it("navigates using mode=challenge for CHALLENGE sessions", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([
      makeNote({ bestSessionMode: "CHALLENGE", bestSessionId: "session-c" }),
    ]);
    render(<ProfileNotePerformance />);
    await waitFor(() => screen.getByText("Biology Review"));
    fireEvent.click(screen.getByRole("button", { name: /Biology Review/ }));
    expect(routerMock.push).toHaveBeenCalledWith(expect.stringContaining("mode=challenge"));
  });

  it("requests the configured limit from the API", () => {
    (getUserNotePerformanceSummary as jest.Mock).mockReturnValue(new Promise(() => {}));
    render(<ProfileNotePerformance />);
    expect(getUserNotePerformanceSummary).toHaveBeenCalledWith(5);
  });

  it("falls back to 'Untitled note' when noteTitle is null", async () => {
    (getUserNotePerformanceSummary as jest.Mock).mockResolvedValue([makeNote({ noteTitle: null })]);
    render(<ProfileNotePerformance />);
    await waitFor(() => {
      expect(screen.getByText("Untitled note")).toBeInTheDocument();
    });
  });
});
