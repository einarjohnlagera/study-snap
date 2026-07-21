import { render, screen } from "@testing-library/react";
import ExamHubIndexPage, { metadata } from "./page";

jest.mock("@/components/branding/brand-assets", () => ({
  BrandFullLogo: () => <div>NoteLib Logo</div>,
}));

describe("ExamHubIndexPage", () => {
  it("lists all exam hubs with links", () => {
    render(<ExamHubIndexPage />);

    expect(screen.getByRole("heading", { name: "Board exam review notes" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Architect Licensure Examination \(ALE\)/ })).toHaveAttribute("href", "/exam/ale");
    expect(screen.getByRole("link", { name: /Philippine Nurse Licensure Examination \(PNLE\)/ })).toHaveAttribute("href", "/exam/pnle");
    expect(screen.getByRole("link", { name: /Licensure Examination for Teachers \(LET\)/ })).toHaveAttribute("href", "/exam/let");
    expect(screen.getByRole("link", { name: /Certified Public Accountant Licensure Examination \(CPALE\)/ })).toHaveAttribute("href", "/exam/cpale");
  });

  it("exports SEO metadata for the index page", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib Exam Hubs – ALE, PNLE, LET, and CPALE Review Notes",
      description: "Browse NoteLib exam hubs for ALE, PNLE, LET, and CPALE public notes, summaries, and practice quizzes.",
      alternates: {
        canonical: "https://notelib.app/exam",
      },
    });
  });
});
