import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ReviewCommitmentPrompt } from "./review-commitment-prompt";
import { getMe, recordReviewCommitmentPrompted, trackAnalyticsEvent, updateReviewCommitment } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  getMe: jest.fn(),
  recordReviewCommitmentPrompted: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateReviewCommitment: jest.fn(),
}));

const examLearner = {
  id: "user-1",
  examDate: "2026-11-08",
  profileType: "STUDENT",
  reviewDays: [],
  reviewCommitmentOutstanding: true,
  reviewCommitmentPromptEligible: true,
  reviewCommitmentPromptCount: 0,
};

describe("ReviewCommitmentPrompt", () => {
  beforeEach(() => {
    globalThis.sessionStorage.clear();
    (getMe as jest.Mock).mockReset();
    (recordReviewCommitmentPrompted as jest.Mock).mockReset().mockResolvedValue({ message: "recorded" });
    (trackAnalyticsEvent as jest.Mock).mockReset().mockResolvedValue(undefined);
    (updateReviewCommitment as jest.Mock).mockReset();
  });

  it("shows for an eligible learner whose first completed session was earlier", async () => {
    (getMe as jest.Mock).mockResolvedValue(examLearner);

    render(<ReviewCommitmentPrompt noteId="note-1" />);

    expect(await screen.findByText("When will you come back?")).toBeInTheDocument();
    expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({
      eventType: "REVIEW_COMMITMENT_PROMPT_SHOWN",
    }));
  });

  it("records an impression at most once across remounts", async () => {
    (getMe as jest.Mock).mockResolvedValue(examLearner);

    const firstRender = render(<ReviewCommitmentPrompt noteId="note-1" />);
    await screen.findByText("When will you come back?");
    await waitFor(() => expect(recordReviewCommitmentPrompted).toHaveBeenCalledTimes(1));
    firstRender.unmount();
    render(<ReviewCommitmentPrompt noteId="note-1" />);
    await screen.findByText("When will you come back?");

    expect(recordReviewCommitmentPrompted).toHaveBeenCalledTimes(1);
  });

  it("keeps the prompt usable when recording the impression fails", async () => {
    (getMe as jest.Mock).mockResolvedValue(examLearner);
    (recordReviewCommitmentPrompted as jest.Mock).mockRejectedValue(new Error("offline"));

    render(<ReviewCommitmentPrompt noteId="note-1" />);

    expect(await screen.findByRole("button", { name: "Set my review plan" })).toBeEnabled();
  });

  it("shows for a learner with no exam date, and hides the exam-date field", async () => {
    // There is deliberately NO profile gate: the commitment is the review DAYS, and gating on an exam
    // date would exclude every STUDENT (~27% of accounts), since onboarding only collects that date for
    // BOARD_EXAM. A test asserting a STUDENT is excluded would pin the bug this fixed.
    //
    // Note the assertion style: the previous version of this test waited only for getMe to have been
    // CALLED, then queried immediately -- so it asserted absence before the component could render and
    // passed whether or not the gate existed. findByText waits for the state update, so this can fail.
    (getMe as jest.Mock).mockResolvedValue({
      ...examLearner,
      examDate: null,
      profileType: "STUDENT",
    });

    render(<ReviewCommitmentPrompt noteId="note-1" />);

    expect(await screen.findByText("When will you come back?")).toBeInTheDocument();
    expect(screen.queryByLabelText("Exam date")).not.toBeInTheDocument();
  });

  it("shows the exam-date field for a BOARD_EXAM learner", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...examLearner,
      examDate: null,
      profileType: "BOARD_EXAM",
    });

    render(<ReviewCommitmentPrompt noteId="note-1" />);

    expect(await screen.findByLabelText("Exam date")).toBeInTheDocument();
  });

  // ⚠️ The digest preference is now part of SERVER eligibility (owner decision 2026-09-10: do not
  // ask when the digest is off, because choosing days cannot change what such a learner receives).
  // The component therefore renders on one boolean and must honour it — this is the client half of
  // that invariant; the server half is AuthServiceTest.
  it("does not render when the server says the learner is not eligible", async () => {
    (getMe as jest.Mock).mockResolvedValue({ ...examLearner, reviewCommitmentPromptEligible: false });
    render(<ReviewCommitmentPrompt noteId="note-1" />);

    await waitFor(() => expect(getMe).toHaveBeenCalled());
    expect(screen.queryByTestId("review-commitment-prompt")).not.toBeInTheDocument();
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(expect.objectContaining({
      eventType: "REVIEW_COMMITMENT_PROMPT_SHOWN",
    }));
  });

  it("lets a BOARD_EXAM learner commit while the optional exam date is empty", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...examLearner,
      examDate: null,
      profileType: "BOARD_EXAM",
    });

    render(<ReviewCommitmentPrompt noteId="note-1" />);
    await screen.findByLabelText("Exam date");
    fireEvent.click(screen.getByRole("button", { name: "Set my review plan" }));

    await waitFor(() => expect(updateReviewCommitment).toHaveBeenCalledWith({
      examDate: null,
      reviewDays: ["MONDAY", "WEDNESDAY", "FRIDAY"],
    }));
  });

  it("lets a learner with no exam date commit review days", async () => {
    // The date requirement must not block someone who cannot have a date.
    (getMe as jest.Mock).mockResolvedValue({
      ...examLearner,
      examDate: null,
      profileType: "STUDENT",
    });

    render(<ReviewCommitmentPrompt noteId="note-1" />);

    await screen.findByText("When will you come back?");
    fireEvent.click(screen.getByRole("button", { name: /set my review plan/i }));

    await waitFor(() => expect(updateReviewCommitment).toHaveBeenCalled());
    expect(screen.queryByText("Choose your exam date before setting your review plan.")).not.toBeInTheDocument();
  });

  it("does not return after reload when the server says it is resolved", async () => {
    (getMe as jest.Mock)
      .mockResolvedValueOnce(examLearner)
      .mockResolvedValueOnce({ ...examLearner, reviewCommitmentPromptEligible: false });
    const firstRender = render(<ReviewCommitmentPrompt noteId="note-1" />);
    expect(await screen.findByText("When will you come back?")).toBeInTheDocument();
    firstRender.unmount();

    render(<ReviewCommitmentPrompt noteId="note-1" />);

    // AGENTS.md "an absence assertion must be able to fail": waiting only for getMe to have been
    // CALLED is the named wrong-timing antipattern -- it is satisfied during the effect, before the
    // resolved promise's .then runs, so the DOM could not contain the prompt either way. Flush the
    // microtask queue so the resolved state has actually rendered before asserting absence. Verified
    // by mutation: with `shouldShow = true` this now fails, where the previous version passed.
    await waitFor(() => expect(getMe).toHaveBeenCalledTimes(2));
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.queryByText("When will you come back?")).not.toBeInTheDocument();
  });

  it("does not show when the server says the learner is ineligible", async () => {
    (getMe as jest.Mock).mockResolvedValue({ ...examLearner, reviewCommitmentPromptEligible: false });

    render(<ReviewCommitmentPrompt noteId="note-1" />);

    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.queryByText("When will you come back?")).not.toBeInTheDocument();
  });

  it("preserves selected days and date after a failed save", async () => {
    (getMe as jest.Mock).mockResolvedValue(examLearner);
    (updateReviewCommitment as jest.Mock).mockRejectedValue(new Error("Save failed"));
    render(<ReviewCommitmentPrompt noteId="note-1" />);
    await screen.findByText("When will you come back?");

    fireEvent.click(screen.getByRole("button", { name: "Tue" }));
    fireEvent.click(screen.getByRole("button", { name: "Set my review plan" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Save failed");
    expect(screen.getByRole("button", { name: "Tue" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Exam date")).toHaveValue("2026-11-08");
    expect(screen.getByTestId("review-commitment-prompt")).toBeInTheDocument();
  });

  it("tracks page abandonment as a dismissal with the exit taken", async () => {
    (getMe as jest.Mock).mockResolvedValue(examLearner);
    const rendered = render(<ReviewCommitmentPrompt noteId="note-1" />);
    await screen.findByText("When will you come back?");

    globalThis.dispatchEvent(new Event("pagehide"));
    rendered.unmount();

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "REVIEW_COMMITMENT_DISMISSED",
      entityId: "note-1",
      metadata: { exit: "pagehide" },
    });
  });

  it("tracks at most one dismissal per prompt impression", async () => {
    (getMe as jest.Mock).mockResolvedValue(examLearner);
    const rendered = render(<ReviewCommitmentPrompt noteId="note-1" />);
    await screen.findByText("When will you come back?");

    globalThis.dispatchEvent(new Event("pagehide"));
    globalThis.dispatchEvent(new Event("pagehide"));
    rendered.unmount();

    const dismissals = (trackAnalyticsEvent as jest.Mock).mock.calls.filter(
      ([event]) => event.eventType === "REVIEW_COMMITMENT_DISMISSED",
    );
    expect(dismissals).toHaveLength(1);
  });

  // ⚠️ Four of the five call sites pass `note?.id ?? null`, so noteId transitions null -> value while
  // mounted. If the abandonment effect re-subscribed on that change, its CLEANUP would fire a
  // dismissal the learner never performed. This fails if noteId is ever closed over again.
  it("does not report abandonment when noteId resolves while the prompt is open", async () => {
    (getMe as jest.Mock).mockResolvedValue(examLearner);
    const rendered = render(<ReviewCommitmentPrompt noteId={null} />);
    await screen.findByText("When will you come back?");

    rendered.rerender(<ReviewCommitmentPrompt noteId="note-1" />);

    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(expect.objectContaining({
      eventType: "REVIEW_COMMITMENT_DISMISSED",
    }));

    // ...and the real abandonment is still recorded afterwards, with the RESOLVED id rather than the
    // stale null — proving the state machine was not latched to "dismissed" by the transition.
    rendered.unmount();
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "REVIEW_COMMITMENT_DISMISSED",
      entityId: "note-1",
      metadata: { exit: "unmount" },
    });
  });

  it("does not report a saved decline as abandonment", async () => {
    (getMe as jest.Mock).mockResolvedValue(examLearner);
    (updateReviewCommitment as jest.Mock).mockResolvedValue(undefined);
    const rendered = render(<ReviewCommitmentPrompt noteId="note-1" />);
    await screen.findByText("When will you come back?");

    fireEvent.click(screen.getByRole("button", { name: "Not now" }));
    await waitFor(() => expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({
      eventType: "REVIEW_COMMITMENT_DECLINED",
    })));
    rendered.unmount();

    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(expect.objectContaining({
      eventType: "REVIEW_COMMITMENT_DISMISSED",
    }));
  });
});
