import { render, screen } from "@testing-library/react";
import LearnPage, { metadata } from "./page";

describe("LearnPage", () => {
  it("renders the categorized learn hub for students, board exams, teachers, and study tips", () => {
    render(<LearnPage />);

    expect(
      screen.getByRole("heading", {
        name: "Learn How to Turn Notes Into Quizzes",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("For Students")).toBeInTheDocument();
    expect(screen.getByText("For Board Exams")).toBeInTheDocument();
    expect(screen.getByText("For Teachers")).toBeInTheDocument();
    expect(screen.getByText("Study Tips")).toBeInTheDocument();

    expect(screen.getByText("How to Turn Notes Into Reviewers")).toBeInTheDocument();
    expect(screen.getByText("How to Use NoteLib for Board Exam Review")).toBeInTheDocument();
    expect(screen.getByText("How Teachers Can Use NoteLib to Create Quizzes")).toBeInTheDocument();
    expect(screen.getByText("Why Practice Questions Are Effective")).toBeInTheDocument();

    expect(
      screen
        .getAllByRole("link", { name: "Read Guide" })
        .some((link) => link.getAttribute("href") === "/learn/how-to-turn-notes-into-reviewers"),
    ).toBe(true);
  });

  it("exports learn page metadata for search and social previews", () => {
    expect(metadata).toMatchObject({
      title: "Study Guides — Turn Notes Into Quizzes | NoteLib",
      description:
        "Study guides for students, board exam reviewees, and teachers who want to turn notes into reviewers, practice questions, and better exam prep.",
      alternates: {
        canonical: "https://notelib.app/learn",
      },
      openGraph: expect.objectContaining({
        type: "website",
        url: "https://notelib.app/learn",
      }),
    });
  });
});
