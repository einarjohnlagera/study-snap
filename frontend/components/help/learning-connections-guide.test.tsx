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
