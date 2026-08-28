import { render, screen } from "@testing-library/react";
import LinkedLearnerProgressPage from "./page";
import { getLinkedLearnerProgress } from "@/lib/api";

jest.mock("next/navigation", () => ({
  useParams: () => ({ relationshipId: "relationship-1" }),
}));

jest.mock("@/lib/api", () => ({
  getLinkedLearnerProgress: jest.fn(),
}));

const activity = {
  relationshipId: "relationship-1",
  learnerDisplayName: "Alex Learner",
  quizPerformance: { averageRecentScore: 78, bestRecentScore: 92, studyPacksReviewed: 4 },
  readiness: { totalConcepts: 10, masteredConcepts: 5, dueConcepts: 2, notStartedConcepts: 3, readinessPercentage: 50 },
  collectionProgress: { collectionCount: 2, totalItems: 7, readyItems: 5, practicedItems: 4 },
  hasActivity: true,
} as const;

beforeEach(() => {
  jest.mocked(getLinkedLearnerProgress).mockReset();
});

it("shows privacy-safe aggregate progress for an accepted linked learner", async () => {
  jest.mocked(getLinkedLearnerProgress).mockResolvedValue(activity);

  render(<LinkedLearnerProgressPage />);

  expect(await screen.findByRole("heading", { name: "Alex Learner's progress" })).toBeInTheDocument();
  expect(screen.getByText("50% ready")).toBeInTheDocument();
  expect(screen.getByText("5 mastered · 2 due · 3 not started")).toBeInTheDocument();
  expect(screen.getByText("78%")).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Study activity" })).not.toBeInTheDocument();
  expect(screen.queryByText(/streak|study days this week/i)).not.toBeInTheDocument();
  expect(screen.queryByText(/concept name/i)).not.toBeInTheDocument();
  expect(getLinkedLearnerProgress).toHaveBeenCalledWith("relationship-1");
});

it("shows an explicit no-activity state instead of empty charts", async () => {
  jest.mocked(getLinkedLearnerProgress).mockResolvedValue({
    ...activity,
    quizPerformance: { averageRecentScore: null, bestRecentScore: null, studyPacksReviewed: 0 },
    readiness: { totalConcepts: 0, masteredConcepts: 0, dueConcepts: 0, notStartedConcepts: 0, readinessPercentage: 0 },
    collectionProgress: { collectionCount: 0, totalItems: 0, readyItems: 0, practicedItems: 0 },
    hasActivity: false,
  });

  render(<LinkedLearnerProgressPage />);

  expect(await screen.findByRole("heading", { name: "No learning activity yet" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Readiness" })).not.toBeInTheDocument();
});
