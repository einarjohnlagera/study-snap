import { render, screen } from "@testing-library/react";
import { LearnArticleLayout } from "./learn-article-layout";
import { getLearnGuideBySlug } from "@/lib/learn-guides";

describe("LearnArticleLayout", () => {
  it("renders the article as a public study resource with summary, key concepts, practice questions, and CTA", () => {
    const guide = getLearnGuideBySlug("how-to-turn-notes-into-reviewers");

    if (!guide) {
      throw new Error("Expected learn guide to exist.");
    }

    render(<LearnArticleLayout guide={guide} />);

    expect(screen.getByRole("heading", { name: guide.title })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Short Introduction" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Summary" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Key Concepts" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Practice Questions" })).toBeInTheDocument();
    expect(screen.getByText("A good reviewer works best when one note covers one lesson, one topic, or one concept block.")).toBeInTheDocument();
    expect(screen.getByText("Why should one note focus on one lesson or topic?")).toBeInTheDocument();
    expect(screen.getAllByText("Sample answer")).toHaveLength(3);
    expect(screen.getByText("Want to turn your own notes into summaries and quizzes?")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Free Account" })).toHaveAttribute("href", "/signup");
  });
});
