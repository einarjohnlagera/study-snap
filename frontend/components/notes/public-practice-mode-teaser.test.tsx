import { render, screen } from "@testing-library/react";
import { PublicPracticeModeTeaser } from "./public-practice-mode-teaser";

describe("PublicPracticeModeTeaser", () => {
  it("lists all four modes, including the Memorization teaser", () => {
    render(<PublicPracticeModeTeaser />);

    expect(screen.getByText("Challenge Quiz")).toBeInTheDocument();
    expect(screen.getByText("Adaptive Practice")).toBeInTheDocument();
    expect(screen.getByText("Board Exam Mode")).toBeInTheDocument();
    expect(screen.getByText("Memorization")).toBeInTheDocument();
  });

  it("does not show a Pro badge for Memorization", () => {
    render(<PublicPracticeModeTeaser />);

    const memorizationCard = screen.getByText("Memorization").closest("div");
    expect(memorizationCard).not.toBeNull();
    expect(memorizationCard?.textContent).not.toContain("Pro");
  });
});
