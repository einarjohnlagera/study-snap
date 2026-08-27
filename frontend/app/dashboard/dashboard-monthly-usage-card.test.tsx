import { render, screen, within } from "@testing-library/react";
import { DashboardMonthlyUsageCard } from "./dashboard-monthly-usage-card";
import type { MePlanResponse } from "@/lib/me-plan";

const usageSummary: MePlanResponse = {
  plan: "FREE",
  usageCycle: { startsAt: "2026-08-01T00:00:00Z", endsAt: "2026-09-01T00:00:00Z" },
  limits: {
    studyPacksPerMonth: 10,
    challengeQuizzesPerMonth: 20,
    quizShareLinksPerMonth: 3,
    adaptivePracticePerMonth: 3,
    ocrPerMonth: 20,
  },
  usage: {
    studyPacksUsed: 2,
    challengeQuizzesUsed: 4,
    quizShareLinksUsed: 1,
    adaptivePracticeUsed: 0,
    ocrUsed: 0,
  },
  remaining: {
    studyPacksRemaining: 8,
    challengeQuizzesRemaining: 16,
    quizShareLinksRemaining: 2,
    adaptivePracticeRemaining: 3,
    ocrRemaining: 20,
  },
  features: {
    adaptivePracticeAvailable: true,
    fileUploadAvailable: true,
    ocrAvailable: true,
  },
};

describe("DashboardMonthlyUsageCard", () => {
  it("names and explains the shared AI quiz meter", () => {
    render(<DashboardMonthlyUsageCard usageSummary={usageSummary} />);

    const card = screen.getByRole("heading", { name: "This Month" }).parentElement as HTMLElement;
    expect(within(card).getByText("AI quizzes")).toBeInTheDocument();
    expect(within(card).getByText("Challenge Quiz sessions and quizzes you make for someone.")).toBeInTheDocument();
    expect(within(card).queryByText("Challenge Quiz")).not.toBeInTheDocument();
    expect(within(card).queryByText("Quiz")).not.toBeInTheDocument();
  });
});
