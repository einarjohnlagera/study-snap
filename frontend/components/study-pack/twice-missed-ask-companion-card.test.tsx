import { render, screen } from "@testing-library/react";
import { shouldRenderTwiceMissedCta, TwiceMissedAskCompanionCard } from "./twice-missed-ask-companion-card";

const RENDERABLE_COMPANION = {
  overview: "Review the core concepts in order.",
  studyStrategy: null,
  commonMistakes: null,
  resources: null,
  faq: [],
  mentorTips: [],
};

describe("TwiceMissedAskCompanionCard", () => {
  it("shows the shared plan-aware Ask Companion upgrade nudge to Free learners", () => {
    render(
      <TwiceMissedAskCompanionCard
        twiceMissedConcepts={["Cell respiration"]}
        currentPlan="FREE"
        primaryCollectionId={null}
        companion={null}
      />,
    );

    expect(screen.getByText("Unlock Ask Companion — get Plus")).toHaveAttribute(
      "href",
      "/settings?section=plans",
    );
  });

  it("links paid learners to the eligible collection with a prefilled draft", () => {
    render(
      <TwiceMissedAskCompanionCard
        twiceMissedConcepts={["Cell respiration"]}
        currentPlan="PLUS"
        primaryCollectionId="collection-1"
        companion={RENDERABLE_COMPANION}
      />,
    );

    expect(screen.getByRole("link", { name: "Ask Companion about this" })).toHaveAttribute(
      "href",
      "/collections/collection-1?askCompanionDraft=Can+you+explain+Cell+respiration+a+different+way%3F",
    );
  });

  it.each(["Unknown", "Uncategorized", "  "])(
    "renders nothing for a paid learner when the only twice-missed entry is the placeholder label %s",
    (placeholder) => {
      const { container } = render(
        <TwiceMissedAskCompanionCard
          twiceMissedConcepts={[placeholder]}
          currentPlan="PLUS"
          primaryCollectionId="collection-1"
          companion={RENDERABLE_COMPANION}
        />,
      );

      expect(container).toBeEmptyDOMElement();
    },
  );

  it("skips a placeholder label and links to the next real twice-missed concept", () => {
    render(
      <TwiceMissedAskCompanionCard
        twiceMissedConcepts={["Unknown", "Cell respiration"]}
        currentPlan="PLUS"
        primaryCollectionId="collection-1"
        companion={RENDERABLE_COMPANION}
      />,
    );

    expect(screen.getByRole("link", { name: "Ask Companion about this" })).toHaveAttribute(
      "href",
      "/collections/collection-1?askCompanionDraft=Can+you+explain+Cell+respiration+a+different+way%3F",
    );
  });

  it.each([
    { concepts: [], collectionId: "collection-1", companion: RENDERABLE_COMPANION },
    { concepts: ["Cell respiration"], collectionId: null, companion: RENDERABLE_COMPANION },
    { concepts: ["Cell respiration"], collectionId: "collection-1", companion: null },
  ])("renders nothing for a paid learner when eligibility is incomplete", ({
    concepts,
    collectionId,
    companion,
  }) => {
    const { container } = render(
      <TwiceMissedAskCompanionCard
        twiceMissedConcepts={concepts}
        currentPlan="PRO"
        primaryCollectionId={collectionId}
        companion={companion}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});

describe("shouldRenderTwiceMissedCta", () => {
  // Mirrors the component's own render matrix above one-for-one — this predicate exists so
  // pages can pre-check without duplicating TwiceMissedAskCompanionCard's internal gate, so it
  // must never drift from what the component actually renders.
  it("agrees with the component: FREE always renders (the upgrade nudge) once a real concept exists", () => {
    expect(shouldRenderTwiceMissedCta(["Cell respiration"], "FREE", null, null)).toBe(true);
    expect(shouldRenderTwiceMissedCta(["Unknown"], "FREE", null, null)).toBe(false);
  });

  it("agrees with the component: paid plans need a real concept, a collection, and renderable companion content", () => {
    expect(shouldRenderTwiceMissedCta(["Cell respiration"], "PLUS", "collection-1", RENDERABLE_COMPANION)).toBe(true);
    expect(shouldRenderTwiceMissedCta([], "PRO", "collection-1", RENDERABLE_COMPANION)).toBe(false);
    expect(shouldRenderTwiceMissedCta(["Cell respiration"], "PRO", null, RENDERABLE_COMPANION)).toBe(false);
    expect(shouldRenderTwiceMissedCta(["Cell respiration"], "PRO", "collection-1", null)).toBe(false);
  });

  it("agrees with the component: a placeholder-only concept list never renders, even on FREE", () => {
    expect(shouldRenderTwiceMissedCta(["Unknown", "Uncategorized"], "FREE", null, null)).toBe(false);
  });
});
