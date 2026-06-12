import { render, screen } from "@testing-library/react";
import { DashboardEmpty } from "./dashboard-empty";

describe("DashboardEmpty", () => {
  it("teaches the teacher loop with Lesson Plan and import framing", () => {
    render(<DashboardEmpty profileType="TEACHER" />);

    expect(screen.getByText("Build your first lesson set")).toBeInTheDocument();
    expect(screen.getByText(/Group them into a Lesson Plan to export and share/)).toBeInTheDocument();
  });

  it("teaches the student loop with study and quiz framing", () => {
    render(<DashboardEmpty profileType="STUDENT" />);

    expect(screen.getByText("Start studying smarter")).toBeInTheDocument();
    expect(screen.getByText("Quiz yourself and track weak topics")).toBeInTheDocument();
  });

  it("teaches the board-exam loop with a Review Set", () => {
    render(<DashboardEmpty profileType="BOARD_EXAM" />);

    expect(screen.getByText("Start your exam prep")).toBeInTheDocument();
    expect(screen.getByText(/Practice across a Review Set with quizzes/)).toBeInTheDocument();
  });

  it("offers both the import and create entry points on every profile", () => {
    render(<DashboardEmpty profileType="PROFESSIONAL" />);

    const importLink = screen.getByRole("link", { name: "Import files" });
    const createLink = screen.getByRole("link", { name: "Create a note" });
    expect(importLink).toHaveAttribute("href", "/notes/import");
    expect(createLink).toHaveAttribute("href", "/notes/new");
  });
});
