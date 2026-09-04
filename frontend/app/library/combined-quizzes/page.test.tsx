import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import CombinedQuizzesPage from "./page";
import { listCombinedQuizzes } from "@/lib/api";

const pushMock = jest.fn();
const routerMock = { push: pushMock };

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  listCombinedQuizzes: jest.fn(),
}));

describe("CombinedQuizzesPage", () => {
  afterEach(async () => {
    await act(async () => {
      await new Promise((resolve) => globalThis.setTimeout(resolve, 0));
    });
    cleanup();
  });

  beforeEach(() => {
    pushMock.mockReset();
    (listCombinedQuizzes as jest.Mock).mockReset().mockResolvedValue([
      {
        id: "combined-on",
        title: "Unit review",
        createdAt: "2026-09-04T12:00:00Z",
        sectionCount: 2,
        questionCount: 12,
        sharing: "SHARING_ON",
      },
      {
        id: "combined-off",
        title: "Practice set",
        createdAt: "2026-09-03T12:00:00Z",
        sectionCount: 1,
        questionCount: 4,
        sharing: "SHARING_OFF",
      },
      {
        id: "combined-none",
        title: "Unshared snapshot",
        createdAt: "2026-09-02T12:00:00Z",
        sectionCount: 3,
        questionCount: 8,
        sharing: "NO_LINK",
      },
    ]);
  });

  it("renders each summary row with its sharing state and routes to the existing detail page", async () => {
    render(<CombinedQuizzesPage />);

    const firstLink = await screen.findByRole("link", { name: /Unit review/ });
    expect(firstLink).toHaveAttribute("href", "/library/combined-quiz/combined-on");
    expect(screen.getByRole("link", { name: /Practice set/ })).toHaveAttribute("href", "/library/combined-quiz/combined-off");
    expect(screen.getByText("Sharing on")).toBeInTheDocument();
    expect(screen.getByText("Sharing off")).toBeInTheDocument();
    expect(screen.getByText("No share link")).toBeInTheDocument();
    expect(screen.getByText("2 sections · 12 questions")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /sharing|copy|delete/i })).not.toBeInTheDocument();
  });

  it("renders a real empty list separately from a failed load and directs the owner to the Library picker", async () => {
    (listCombinedQuizzes as jest.Mock).mockResolvedValue([]);
    render(<CombinedQuizzesPage />);

    expect(await screen.findByRole("heading", { name: "No combined quizzes yet" })).toBeInTheDocument();
    expect(screen.getByText(/brings the generated quizzes from several notes/)).toBeInTheDocument();
    expect(screen.queryByText("Could not load combined quizzes")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Build quiz" }));
    expect(pushMock).toHaveBeenCalledWith("/library");
  });

  it("shows a retry affordance for a transient failure without rendering the empty state", async () => {
    (listCombinedQuizzes as jest.Mock)
      .mockRejectedValueOnce(new Error("Network unavailable"))
      .mockResolvedValueOnce([]);
    render(<CombinedQuizzesPage />);

    expect(await screen.findByRole("heading", { name: "Could not load combined quizzes" })).toBeInTheDocument();
    expect(screen.getByText("Network unavailable")).toBeInTheDocument();
    expect(screen.queryByText("No combined quizzes yet")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "No combined quizzes yet" })).toBeInTheDocument());
    expect(listCombinedQuizzes).toHaveBeenCalledTimes(2);
  });
});
