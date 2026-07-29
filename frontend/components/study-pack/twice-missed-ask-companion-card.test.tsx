import { render, screen } from "@testing-library/react";
import { TwiceMissedAskCompanionCard } from "./twice-missed-ask-companion-card";

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
