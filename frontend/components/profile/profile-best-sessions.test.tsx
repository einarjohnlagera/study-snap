import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ProfileBestSessions } from "./profile-best-sessions";
import { getUserBestQuizSessions } from "@/lib/api";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

jest.mock("@/lib/api", () => ({
  getUserBestQuizSessions: jest.fn(),
}));

const BASE_SESSION = {
  noteId: "note-1",
  noteTitle: "Respiratory Physiology",
  sessionMode: "QUICK_REVIEW" as const,
  totalQuestions: 10,
  correctAnswers: 9,
  scorePercentage: 90,
  completedAt: "2026-04-10T10:00:00Z",
};

describe("ProfileBestSessions", () => {
  beforeEach(() => {
    pushMock.mockReset();
    (getUserBestQuizSessions as jest.Mock).mockReset();
  });

  it("renders the section heading and description", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([]);

    render(<ProfileBestSessions />);

    expect(await screen.findByRole("heading", { name: "Best Sessions" })).toBeInTheDocument();
    expect(screen.getByText("Your top-performing quiz attempts across all notes.")).toBeInTheDocument();
  });

  it("shows the empty state when no sessions exist", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([]);

    render(<ProfileBestSessions />);

    expect(await screen.findByText("No quiz sessions yet.")).toBeInTheDocument();
    expect(screen.getByText("Start a quiz to see your best sessions here.")).toBeInTheDocument();
  });

  it("renders session items in the order returned by the API", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([
      { ...BASE_SESSION, sessionId: "s-1", scorePercentage: 90, correctAnswers: 9, noteTitle: "Note A" },
      { ...BASE_SESSION, sessionId: "s-2", scorePercentage: 80, correctAnswers: 8, noteTitle: "Note B" },
      { ...BASE_SESSION, sessionId: "s-3", scorePercentage: 70, correctAnswers: 7, noteTitle: "Note C" },
    ]);

    render(<ProfileBestSessions />);

    const items = await screen.findAllByText(/Note [ABC]/);
    expect(items[0]).toHaveTextContent("Note A");
    expect(items[1]).toHaveTextContent("Note B");
    expect(items[2]).toHaveTextContent("Note C");
  });

  it("shows score percentage and correct/total for each session", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([
      { ...BASE_SESSION, sessionId: "s-1", scorePercentage: 90, correctAnswers: 9, totalQuestions: 10 },
    ]);

    render(<ProfileBestSessions />);

    expect(await screen.findByText(/90%/)).toBeInTheDocument();
    expect(screen.getByText(/9\/10/)).toBeInTheDocument();
  });

  it("shows Quick Review and Challenge Quiz mode labels", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([
      { ...BASE_SESSION, sessionId: "s-1", sessionMode: "QUICK_REVIEW" as const },
      { ...BASE_SESSION, sessionId: "s-2", sessionMode: "CHALLENGE" as const, noteTitle: "Cardiology" },
    ]);

    render(<ProfileBestSessions />);

    expect(await screen.findByText("Quick Review")).toBeInTheDocument();
    expect(screen.getByText("Challenge Quiz")).toBeInTheDocument();
  });

  it("shows the Top Score badge for sessions with score >= 80%", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([
      { ...BASE_SESSION, sessionId: "s-1", scorePercentage: 85, correctAnswers: 85 },
    ]);

    render(<ProfileBestSessions />);

    expect(await screen.findByText("Top Score")).toBeInTheDocument();
  });

  it("shows the Perfect badge for 100% sessions", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([
      { ...BASE_SESSION, sessionId: "s-1", scorePercentage: 100, correctAnswers: 10 },
    ]);

    render(<ProfileBestSessions />);

    expect(await screen.findByText(/Perfect/i)).toBeInTheDocument();
  });

  it("navigates to the session review page when a session is clicked", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([
      { ...BASE_SESSION, sessionId: "session-42", noteId: "note-99", sessionMode: "QUICK_REVIEW" as const },
    ]);

    render(<ProfileBestSessions />);

    fireEvent.click(await screen.findByRole("button", { name: /review session/i }));

    expect(pushMock).toHaveBeenCalledWith(
      expect.stringContaining("/notes/note-99/sessions/session-42"),
    );
    expect(pushMock).toHaveBeenCalledWith(
      expect.stringContaining("mode=quick-review"),
    );
  });

  it("navigates with the correct mode for Challenge Quiz sessions", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([
      { ...BASE_SESSION, sessionId: "session-77", noteId: "note-22", sessionMode: "CHALLENGE" as const },
    ]);

    render(<ProfileBestSessions />);

    fireEvent.click(await screen.findByRole("button", { name: /review session/i }));

    expect(pushMock).toHaveBeenCalledWith(
      expect.stringContaining("mode=challenge"),
    );
  });

  it("shows an error state if the API call fails", async () => {
    (getUserBestQuizSessions as jest.Mock).mockRejectedValue(new Error("Network error"));

    render(<ProfileBestSessions />);

    expect(await screen.findByText("Could not load best sessions.")).toBeInTheDocument();
    expect(screen.getByText("Network error")).toBeInTheDocument();
  });

  it("requests the configured limit from the API", async () => {
    (getUserBestQuizSessions as jest.Mock).mockResolvedValue([]);

    render(<ProfileBestSessions />);

    await waitFor(() => {
      expect(getUserBestQuizSessions).toHaveBeenCalledWith(5);
    });
  });
});
