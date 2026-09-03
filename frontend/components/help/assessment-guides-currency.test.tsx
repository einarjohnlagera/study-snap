import { render, screen } from "@testing-library/react";
import { BoardExamGuide } from "./board-exam-guide";
import { QuizModesGuide } from "./quiz-modes-guide";

/**
 * ⚠️ THIS FILE EXISTS BECAUSE THESE GUIDES WENT STALE FOR FOUR RELEASES AND NOTHING NOTICED.
 *
 * `v0.104.0`–`v0.107.0` changed what a Board Exam, a Long Exam and an Adaptive Practice session ARE.
 * At the `v0.109.0` kickoff `board-exam-guide.tsx` contained the string "Review Set" ZERO times — the
 * guide for Board Exam did not mention the thing a Board Exam is built from — and still advised
 * organising "one note per topic", which had become the SMALLEST possible Board Exam.
 *
 * That is the sweep-by-surface rule failing in the direction AGENTS.md says it always fails: a file
 * that explains a feature is exactly the file that never changes when the feature does. A copy
 * release has no failing test to catch a false claim, so these are the pinned strings that give one.
 *
 * ⚠️ WHAT THESE ASSERTIONS PROVE, STATED HONESTLY: that the rendered copy still describes the SCOPE
 * each mode has today. They do NOT prove the copy is accurate in every other respect, and they are
 * not a substitute for anchoring a new claim to code before writing it.
 */
describe("assessment guides describe the product that exists", () => {
  it("tells learners a Board Exam is drawn from a whole Review Set", () => {
    render(<BoardExamGuide />);

    // The scope claim. Board Exam was note-capped until v0.106.0 made the Review Set the syllabus.
    expect(screen.getAllByText(/Review Set/i).length).toBeGreaterThan(0);
    // Launching from a Subject Plan resolves UP to its parent Review Set — the part learners would
    // otherwise get wrong by assuming they must start from the top.
    expect(screen.getByText(/covers the whole Review Set it belongs to/i)).toBeInTheDocument();
  });

  it("never tells learners one note per topic is the path to a good Board Exam", () => {
    render(<BoardExamGuide />);

    // ⚠️ The exact stale sentence this file was created for. A single-note Board Exam is now the
    // smallest one available, so advising it inverts the guidance.
    expect(screen.queryByText(/one note per topic works best/i)).not.toBeInTheDocument();
  });

  it("does not describe Long Exam as covering a single note", () => {
    render(<QuizModesGuide />);

    // v0.105.0 made Long Exam Subject-Plan sourced and sampled across the curriculum.
    expect(screen.queryByText(/covering your full note/i)).not.toBeInTheDocument();
    expect(screen.getByText(/Subject Plan or Study Plan/i)).toBeInTheDocument();
  });

  it("describes Adaptive Practice by what drives it, not by one prior quiz", () => {
    render(<QuizModesGuide />);

    // v0.077.0 moved the trigger to ConceptHealth (due or persistently weak), and v0.107.0 gave it
    // plan scope — so "concepts you missed in Challenge Quiz" was wrong on both counts.
    expect(screen.queryByText(/concepts you missed in Challenge Quiz/i)).not.toBeInTheDocument();
    expect(screen.getByText(/due for review or that you keep missing/i)).toBeInTheDocument();
    expect(screen.getByText(/Subject Plan or Review Set at once/i)).toBeInTheDocument();
  });
});
