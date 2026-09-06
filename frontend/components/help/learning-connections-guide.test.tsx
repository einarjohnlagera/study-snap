import { render, screen } from "@testing-library/react";
import { LearningConnectionsGuide } from "./learning-connections-guide";
import { pricingConfig } from "@/lib/pricing-config";

describe("LearningConnectionsGuide", () => {
  it("documents directional activity sharing, which v0.92.0 shipped", () => {
    render(<LearningConnectionsGuide />);

    // ⚠️ v0.91.0 recorded the absent supporter Help section as one of the two reasons nobody could
    // discover this capability. Phase 2 shipped a new capability into the same hole, so this pins
    // that the guide covers it — and covers the direction rule, which is the part users get wrong.
    expect(screen.getByText("Show someone you are studying")).toBeInTheDocument();
    expect(
      screen.getByText(/sharing yours does not make theirs visible to you/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/Connecting on its own shares nothing/i)).toBeInTheDocument();
  });

  it("never claims activity sharing shows scores or notes", () => {
    render(<LearningConnectionsGuide />);

    // The privacy line is absolute and is the reason learners write honestly.
    expect(
      screen.getByText(/never your scores, your notes or what you studied/i),
    ).toBeInTheDocument();
  });

  it("names the share-a-quiz control for BOTH populations, not just the learner one", () => {
    render(<LearningConnectionsGuide />);

    // ⚠️ THE FIXED DEFECT: this read "From a note's actions menu, choose Quiz for someone", but that
    // menu item is gated `!isTeacherMode` (private-note-detail-page-client.tsx:2548) -- a teacher has no
    // such item and reaches the SAME capability through a button labelled "Generate Quiz". So the guide
    // instructed teachers to do something they cannot.
    //
    // ⚠️ THIS IS THE EXACT CLASS `v0.110.0` ALREADY PAID FOR, MIRRORED: `guidance.md` records
    // `teacher-generate-quiz-multi-note` being replaced because it "named a TEACHER-gated CTA, so most of
    // its readers were told to do something they could not." Here it was the non-teacher CTA.
    //
    // Found by SWEEPING BY SURFACE in v0.122.0 rather than by diff -- this file was not in the release's
    // diff and has never been in one when the behaviour it describes changed.
    expect(screen.getByText(/choose Quiz for someone/i)).toBeInTheDocument();
    expect(screen.getByText(/teachers see the same action as Generate Quiz/i)).toBeInTheDocument();
    expect(screen.queryByText(/From a note's actions menu/i)).not.toBeInTheDocument();
  });

  it("takes share-link numbers from pricing config rather than retyping them", () => {
    render(<LearningConnectionsGuide />);

    // ⚠️ What this actually proves: the rendered copy AGREES with pricing-config, so changing a plan
    // limit without updating the copy fails here — which is the drift this fix removes. It does not
    // prove the copy reads the config: a hardcoded value that happens to match would still pass.
    // Structural enforcement is the import in the component, not this assertion.
    expect(
      screen.getByText(
        `Free plans can have ${pricingConfig.free.quizShareLinksPerMonth} share links a month, ` +
          `Plus ${pricingConfig.plus.quizShareLinksPerMonth}, Pro unlimited`,
      ),
    ).toBeInTheDocument();
  });
});
