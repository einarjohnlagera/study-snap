import { fireEvent, render, screen, within } from "@testing-library/react";
import { RegenerateScopeModal } from "./regenerate-scope-modal";
import type { NoteResponse } from "@/lib/api";

function buildNote(overrides: Partial<NoteResponse> = {}): NoteResponse {
  return {
    id: "note-1",
    title: "Development Length of Reinforced Bars",
    subject: "Reinforced Concrete Design",
    courseProgram: "Civil Engineering",
    domainContext: "CIVIL_ENGINEERING",
    learnerLevel: "BOARD_EXAM_REVIEW",
    tags: [],
    content: "Body.",
    visibility: "PRIVATE",
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
    copiedFromNoteId: null,
    copiedFromUserId: null,
    copiedFromTitle: null,
    copiedFromPublic: false,
    copiedAt: null,
    studyPackId: "pack-1",
    studyPackStatus: "STUDY_PACK_READY",
    summary: "Summary.",
    keyConcepts: [],
    quiz: [],
    quizMastered: false,
    quizMasteredAt: null,
    generatedQuiz: null,
    lastUsedTargetLearnerLevel: null,
    quizCount: 0,
    quickReviewAvailable: true,
    challengeQuizAvailable: true,
    adaptivePracticeAvailable: true,
    ...overrides,
  } as NoteResponse;
}

function renderModal(props: Partial<React.ComponentProps<typeof RegenerateScopeModal>> = {}) {
  const onConfirm = jest.fn();
  const onEditDetails = jest.fn();
  const onClose = jest.fn();
  render(
    <RegenerateScopeModal
      isOpen
      note={buildNote()}
      isCurator={false}
      noteGenerationsRemaining={5}
      studyPacksRemaining={5}
      busy={false}
      onClose={onClose}
      onConfirm={onConfirm}
      onEditDetails={onEditDetails}
      {...props}
    />,
  );
  return { onConfirm, onEditDetails, onClose };
}

// Matched on each card's own body copy: the accessible name is the whole card, and a bare
// /Study Pack/ matches BOTH cards because one is named "Note + Study Pack".
function studyPackCard() {
  return screen.getByRole("radio", { name: /Rewrites the summary/i });
}

function combinedCard() {
  return screen.getByRole("radio", { name: /Rewrites the note itself/i });
}

describe("RegenerateScopeModal", () => {
  it("defaults to the safe scope and confirms with it", () => {
    const { onConfirm } = renderModal();

    expect(studyPackCard()).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("Uses 1 Study Pack")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Regenerate" }));
    expect(onConfirm).toHaveBeenCalledWith("STUDY_PACK");
  });

  it("confirms with the combined scope once it is selected", () => {
    const { onConfirm } = renderModal();

    fireEvent.click(combinedCard());
    expect(screen.getByText("Uses 1 topic note and 1 Study Pack")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Regenerate Note + Study Pack" }));
    expect(onConfirm).toHaveBeenCalledWith("NOTE_AND_STUDY_PACK");
  });

  /**
   * Plan section 17: the strong overwrite state is (combined scope AND learner-owned). A curator-only
   * or learner-only fixture cannot tell "always strong" from "strong for learners" apart, so both legs
   * are pinned -- the v0.115.0 lesson about a predicate that survived every test.
   */
  it("shows the strong overwrite warning and CTA for a LEARNER-owned note", () => {
    renderModal({ isCurator: false });
    fireEvent.click(combinedCard());

    expect(screen.getByText("This replaces everything written in this note")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Regenerate Note + Study Pack" })).toBeInTheDocument();
  });

  it("does NOT show it for a CURATOR-owned note, which keeps the plain CTA", () => {
    renderModal({ isCurator: true });
    fireEvent.click(combinedCard());

    expect(screen.queryByText("This replaces everything written in this note")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Regenerate" })).toBeInTheDocument();
  });

  it("warns on a PUBLIC note and stays quiet on a private one", () => {
    renderModal({ note: buildNote({ visibility: "PUBLIC" }) });
    expect(screen.getByText("This Note is public")).toBeInTheDocument();
  });

  it("does not warn about publication on a private note", () => {
    renderModal();
    expect(screen.queryByText("This Note is public")).not.toBeInTheDocument();
  });

  it("disables the combined scope on a titleless note, with the reason", () => {
    const { onConfirm } = renderModal({ note: buildNote({ title: "   " }) });

    expect(combinedCard()).toBeDisabled();
    fireEvent.click(combinedCard());
    fireEvent.click(screen.getByRole("button", { name: "Regenerate" }));
    expect(onConfirm).toHaveBeenCalledWith("STUDY_PACK");
    expect(screen.getByText(/the title is the topic we write from/i)).toBeInTheDocument();
  });

  it("disables the combined scope when the note has no Study Pack yet", () => {
    renderModal({ note: buildNote({ studyPackId: null }) });

    expect(combinedCard()).toBeDisabled();
    expect(screen.getByText("This note has no Study Pack to regenerate yet.")).toBeInTheDocument();
  });

  it("shows the generation inputs but never Applicable Programs", () => {
    renderModal();
    fireEvent.click(combinedCard());

    const details = screen.getByText("We write from these Note details").parentElement as HTMLElement;
    expect(within(details).getByText("Development Length of Reinforced Bars")).toBeInTheDocument();
    expect(within(details).getByText("Reinforced Concrete Design")).toBeInTheDocument();
    expect(within(details).getByText("Board Exam Review")).toBeInTheDocument();
    expect(screen.queryByText(/Applicable Programs/i)).not.toBeInTheDocument();
  });

  it("routes Edit Note details to the caller rather than editing behind the dialog", () => {
    const { onEditDetails } = renderModal();
    fireEvent.click(combinedCard());

    fireEvent.click(screen.getByRole("button", { name: /Edit Note details/i }));
    expect(onEditDetails).toHaveBeenCalled();
  });

  it("exposes a single-choice group with a roving tabindex that keeps it reachable by Tab", () => {
    renderModal();
    const group = screen.getByRole("radiogroup");
    const studyPack = studyPackCard();

    // The SELECTED card must carry 0 from the first render: AppModal's focus trap filters out
    // tabIndex === -1, so two -1 cards would drop the whole selector out of the Tab cycle.
    expect(studyPack).toHaveAttribute("tabindex", "0");
    expect(combinedCard()).toHaveAttribute("tabindex", "-1");

    fireEvent.keyDown(group, { key: "ArrowRight" });
    expect(combinedCard()).toHaveAttribute("aria-checked", "true");
    expect(combinedCard()).toHaveAttribute("tabindex", "0");
  });

  it("surfaces a server rejection in place instead of closing", () => {
    renderModal({ errorMessage: "A Study Pack is already being generated for this note." });
    expect(screen.getByRole("alert")).toHaveTextContent("A Study Pack is already being generated");
  });
});

describe("RegenerateScopeModal quota disclosure", () => {
  it("shows what is LEFT, not only what the scope costs", () => {
    renderModal({ noteGenerationsRemaining: 3, studyPacksRemaining: 7 });
    expect(screen.getByText(/7 Study Pack generations left this cycle/i)).toBeInTheDocument();
  });

  it("refuses the combined scope when the topic note allowance is gone, and still allows Study Pack only", () => {
    renderModal({ noteGenerationsRemaining: 0, studyPacksRemaining: 4 });

    // The discriminating half: Study-Pack-only spends NO topic note unit, so an exhausted note
    // allowance must not block it. A fixture that exhausts both passes under a version that blocks
    // everything and proves nothing.
    const combined = screen.getByRole("radio", { name: /Note \+ Study Pack/i });
    expect(combined).toBeDisabled();
    expect(screen.getByText(/no topic note allowance left/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Regenerate$/i })).toBeEnabled();
  });

  it("renders no quota copy at all before the plan summary loads", () => {
    renderModal({ noteGenerationsRemaining: null, studyPacksRemaining: null });
    expect(screen.queryByText(/left this cycle/i)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Regenerate$/i })).toBeEnabled();
  });
});
