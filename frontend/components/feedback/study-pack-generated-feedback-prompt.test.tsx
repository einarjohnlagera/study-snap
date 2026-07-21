import { render, screen } from "@testing-library/react";
import { StudyPackGeneratedFeedbackPrompt } from "./study-pack-generated-feedback-prompt";
import { markEarlyLifecycleFeedbackSignalShownThisSession } from "@/lib/early-lifecycle-feedback-signals";

describe("StudyPackGeneratedFeedbackPrompt", () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
    globalThis.sessionStorage.clear();
  });

  it("renders on first mount for a user who has not seen it before", async () => {
    render(<StudyPackGeneratedFeedbackPrompt userId="user-1" noteTitle="Cell Biology" />);
    expect(await screen.findByText("Does this match what you needed?")).toBeInTheDocument();
  });

  it("does not render again after being marked seen", async () => {
    const view = render(<StudyPackGeneratedFeedbackPrompt userId="user-1" noteTitle="Cell Biology" />);
    expect(await screen.findByText("Does this match what you needed?")).toBeInTheDocument();
    view.unmount();

    render(<StudyPackGeneratedFeedbackPrompt userId="user-1" noteTitle="Anatomy" />);
    expect(screen.queryByText("Does this match what you needed?")).not.toBeInTheDocument();
  });

  it("does not render if another early-lifecycle prompt already fired this session", () => {
    markEarlyLifecycleFeedbackSignalShownThisSession();
    render(<StudyPackGeneratedFeedbackPrompt userId="user-1" noteTitle="Cell Biology" />);
    expect(screen.queryByText("Does this match what you needed?")).not.toBeInTheDocument();
  });
});
