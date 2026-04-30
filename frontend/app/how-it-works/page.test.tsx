import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import HowItWorksPage, { metadata } from "./page";

jest.mock("@/components/analytics/page-view-tracker", () => ({
  AnalyticsPageViewTracker: () => null,
}));

jest.mock("@/components/analytics/tracked-link", () => ({
  TrackedLink: ({
    href,
    className,
    children,
  }: {
    href: string;
    className?: string;
    children: ReactNode;
  }) => (
    <a href={href} className={className}>
      {children}
    </a>
  ),
}));

describe("HowItWorksPage", () => {
  it("renders the dedicated workflow walkthrough", () => {
    const { container } = render(<HowItWorksPage />);

    expect(screen.getAllByAltText("NoteLib")).not.toHaveLength(0);
    expect(screen.getByRole("heading", { name: "How NoteLib Works" })).toBeInTheDocument();
    expect(screen.getByText("Go from notes to self-testing in a simple study workflow.")).toBeInTheDocument();

    expect(screen.getByText("Simple 3-Step Flow")).toBeInTheDocument();
    expect(screen.getByText("Step 1")).toBeInTheDocument();
    expect(screen.getByText("Step 2")).toBeInTheDocument();
    expect(screen.getByText("Step 3")).toBeInTheDocument();

    expect(screen.getByText("Visual Walkthrough")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Add Notes" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Generate Study Pack" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Take Quiz" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Review Results" })).toBeInTheDocument();
    expect(screen.getByAltText("NoteLib note editor showing note writing and metadata entry")).toBeInTheDocument();
    expect(screen.getByAltText("NoteLib Study Pack view showing generated summary and key concepts")).toBeInTheDocument();
    expect(screen.getByAltText("NoteLib Board Exam Mode and Challenge Quiz in-progress screen")).toBeInTheDocument();
    expect(screen.getByAltText("NoteLib quiz results and weak concept review screen")).toBeInTheDocument();

    expect(screen.getByText("Board Exam Mode — Pro")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Start for Free" })).toHaveAttribute("href", "/signup");
    expect(screen.getByRole("link", { name: "View Pricing" })).toHaveAttribute("href", "/pricing");
    expect(screen.getByRole("link", { name: "How it Works" })).toHaveAttribute("href", "/how-it-works");

    const structuredData = container.querySelector("#how-it-works-structured-data");
    expect(structuredData).not.toBeNull();
    expect(structuredData?.textContent).toContain('"@type":"WebSite"');
  });

  it("exports how it works metadata", () => {
    expect(metadata).toMatchObject({
      title: "How NoteLib Works — Notes to Study Packs and Quiz Practice",
      description: "See how NoteLib turns saved notes into Study Packs, quizzes, Board Exam practice, and review insights.",
      alternates: {
        canonical: "https://notelib.app/how-it-works",
      },
    });
  });
});
