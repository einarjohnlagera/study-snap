import { fireEvent, render, screen } from "@testing-library/react";
import { ContinueSpotlight } from "./continue-spotlight";
import { trackAnalyticsEvent } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

describe("ContinueSpotlight", () => {
  beforeEach(() => {
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockResolvedValue(undefined);
  });

  it("shows the note title, metadata, and quick-review resume route", () => {
    render(
      <ContinueSpotlight
        recommendation={{
          studyPackId: "pack-1",
          noteId: "note-1",
          noteTitle: "Biology Review",
          subject: "Biology",
          courseProgram: "Nursing",
          summaryPreview: "Cell theory review",
          resumeType: "QUICK_REVIEW",
          reason: "RESUME_REVIEW",
          lastScorePercentage: null,
          lastReviewedAt: "2026-04-01T10:00:00Z",
          lastOpenedAt: null,
          createdAt: "2026-03-31T10:00:00Z",
          currentQuestionIndex: 1,
          totalQuestions: 8,
          currentRound: "INITIAL",
          remainingQuestions: null,
          resumeState: "QUESTION_IN_PROGRESS",
        }}
      />,
    );

    expect(screen.getByText("Biology Review")).toBeInTheDocument();
    expect(screen.getByText("Biology • Nursing")).toBeInTheDocument();
    expect(screen.getByText("You left off on Question 2 of 8.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Resume Quick Review" })).toHaveAttribute(
      "href",
      "/notes/note-1/quick-review",
    );
  });

  it("switches the label and route for adaptive practice", () => {
    render(
      <ContinueSpotlight
        recommendation={{
          studyPackId: "pack-2",
          noteId: "note-2",
          noteTitle: "Statics Midterm Review",
          subject: "Engineering Mechanics",
          courseProgram: "Civil Engineering",
          summaryPreview: null,
          resumeType: "ADAPTIVE",
          reason: "RESUME_REVIEW",
          lastScorePercentage: null,
          lastReviewedAt: "2026-04-02T09:00:00Z",
          lastOpenedAt: null,
          createdAt: "2026-03-30T09:00:00Z",
          currentQuestionIndex: 0,
          totalQuestions: 6,
          currentRound: "INITIAL",
          remainingQuestions: null,
          resumeState: "QUESTION_IN_PROGRESS",
        }}
      />,
    );

    expect(screen.getByRole("link", { name: "Resume Adaptive Practice" })).toHaveAttribute(
      "href",
      "/notes/note-2/adaptive-practice?entry=dashboard-continue",
    );
  });

  it("uses professional label overrides for challenge and long exam resumes", () => {
    const baseRecommendation = {
      studyPackId: "pack-3",
      noteId: "note-3",
      noteTitle: "AWS Certification Review",
      subject: "Cloud",
      courseProgram: "Solutions Architect",
      summaryPreview: null,
      reason: "RESUME_REVIEW" as const,
      lastScorePercentage: null,
      lastReviewedAt: null,
      lastOpenedAt: null,
      createdAt: "2026-04-03T09:00:00Z",
      currentQuestionIndex: 0,
      totalQuestions: 20,
      currentRound: "INITIAL" as const,
      remainingQuestions: null,
      resumeState: "QUESTION_IN_PROGRESS" as const,
    };

    const { rerender } = render(
      <ContinueSpotlight
        profileType="PROFESSIONAL"
        recommendation={{
          ...baseRecommendation,
          resumeType: "CHALLENGE",
        }}
      />,
    );

    expect(screen.getByRole("link", { name: "Continue Certification Review" })).toHaveAttribute(
      "href",
      "/notes/note-3/challenge-quiz",
    );

    rerender(
      <ContinueSpotlight
        profileType="PROFESSIONAL"
        recommendation={{
          ...baseRecommendation,
          resumeType: "LONG_EXAM",
        }}
      />,
    );

    expect(screen.getByRole("link", { name: "Continue Full Practice Exam" })).toHaveAttribute(
      "href",
      "/notes/note-3/long-exam",
    );
  });

  it("uses try-it framing and tracks the suggested Challenge Quiz funnel", () => {
    render(
      <ContinueSpotlight
        recommendation={{
          studyPackId: "pack-4",
          noteId: "note-4",
          noteTitle: "Cardiology Review",
          subject: "Cardiology",
          courseProgram: "Nursing",
          summaryPreview: null,
          resumeType: "CHALLENGE",
          reason: "SUGGESTED_CHALLENGE",
          lastScorePercentage: null,
          lastReviewedAt: null,
          lastOpenedAt: "2026-04-04T09:00:00Z",
          createdAt: "2026-04-01T09:00:00Z",
          currentQuestionIndex: null,
          totalQuestions: null,
          currentRound: null,
          remainingQuestions: null,
          resumeState: null,
        }}
      />,
    );

    const link = screen.getByRole("link", { name: "Try Challenge Quiz" });
    expect(link).toHaveAttribute("href", "/notes/note-4/challenge-quiz");
    expect(screen.queryByText("Resume Challenge Quiz")).not.toBeInTheDocument();
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "DASHBOARD_RECOMMENDATION_SHOWN",
      entityId: "note-4",
      metadata: {
        reason: "SUGGESTED_CHALLENGE",
        resumeType: "CHALLENGE",
      },
    });

    fireEvent.click(link);

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "DASHBOARD_RECOMMENDATION_CTA_CLICKED",
      entityId: "note-4",
      metadata: {
        reason: "SUGGESTED_CHALLENGE",
        resumeType: "CHALLENGE",
      },
    });
  });
});
