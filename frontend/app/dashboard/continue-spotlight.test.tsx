import { render, screen } from "@testing-library/react";
import { ContinueSpotlight } from "./continue-spotlight";

describe("ContinueSpotlight", () => {
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
      "/notes/note-2/adaptive-practice",
    );
  });
});
