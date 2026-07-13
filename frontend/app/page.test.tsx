import type { ReactNode } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import Home, { metadata } from "./page";
import { getServerPublicNoteCount } from "@/lib/server-public-notes";

jest.mock("@/lib/server-public-notes", () => ({
  getServerPublicNoteCount: jest.fn(),
}));

jest.mock("@/components/billing/pricing-plans-section", () => ({
  SimplePricingSection: () => (
    <section>
      <h2>Simple, Transparent Pricing</h2>
      <p>Pricing section placeholder</p>
    </section>
  ),
}));

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

describe("LandingPage", () => {
  beforeEach(() => {
    (getServerPublicNoteCount as jest.Mock).mockResolvedValue(128);
  });

  it("renders the redesigned conversion-focused landing flow", async () => {
    const { container } = render(await Home());

    expect(screen.getAllByAltText("NoteLib")).not.toHaveLength(0);
    expect(
      screen.getByRole("heading", {
        name: "Build your notes library and turn notes into quizzes.",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Write, paste, or generate notes — then turn them into summaries, key concepts, quizzes, and exam-ready practice."),
    ).toBeInTheDocument();
    expect(screen.getByText("5 study modes")).toBeInTheDocument();
    expect(screen.getByText("Board Exam Mode · Pro")).toBeInTheDocument();
    expect(screen.getByText("— timed full-exam simulation.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "See how it works" })).toHaveAttribute("href", "/how-it-works");
    expect(screen.getAllByRole("link", { name: "Start for Free" })[0]).toHaveAttribute("href", "/signup");
    expect(screen.getByRole("link", { name: /Try the demo/ })).toHaveAttribute("href", "/demo");
    expect(screen.getAllByRole("link", { name: "Browse Public Library" })).toHaveLength(1);
    expect(screen.getByRole("link", { name: "Browse Public Library" })).toHaveAttribute("href", "/public/library");
    expect(screen.getByAltText("NoteLib note detail showing summary of the note")).toBeInTheDocument();
    expect(screen.getByText("128 public notes")).toBeInTheDocument();
    expect(screen.getByText("ready to explore for focused review.")).toBeInTheDocument();

    expect(screen.getByText("Who It's For")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Built for your study workflow" })).toBeInTheDocument();
    expect(screen.getByText("Create")).toBeInTheDocument();
    expect(screen.getByText("Understand")).toBeInTheDocument();
    expect(screen.getByText("Practice")).toBeInTheDocument();
    expect(screen.getByText("Challenge")).toBeInTheDocument();
    expect(screen.getByText("Improve")).toBeInTheDocument();

    expect(screen.getAllByText("Why NoteLib")).not.toHaveLength(0);
    expect(screen.getByRole("heading", { name: "Built for serious study" })).toBeInTheDocument();
    expect(screen.getByText("Every feature is designed to move you from reading to remembering.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Built for studying" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Learn from your weak points" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "From notes to mastery" })).toBeInTheDocument();
    expect(screen.queryByAltText("NoteLib Study Pack view showing generated summary and key concepts")).not.toBeInTheDocument();
    expect(screen.queryByAltText("NoteLib Board Exam Mode and Challenge Quiz in-progress screen")).not.toBeInTheDocument();
    expect(screen.queryByAltText("NoteLib quiz results and weak concept review screen")).not.toBeInTheDocument();

    expect(screen.getByText("Public Library")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Explore notes worth studying" })).toBeInTheDocument();
    expect(screen.getByText("Browse notes shared by others. Copy them into your library and turn them into summaries, key concepts, and quizzes.")).toBeInTheDocument();
    expect(screen.getByText("Start even if you don't have notes yet.")).toBeInTheDocument();
    expect(screen.getByText("Live product preview")).toBeInTheDocument();
    expect(screen.getByText("Discover curated public notes")).toBeInTheDocument();
    expect(screen.getByText("Copy notes into your own library")).toBeInTheDocument();
    expect(screen.getByText("Turn them into summaries, concepts, and quizzes")).toBeInTheDocument();
    expect(
      screen.getByAltText("NoteLib Public Library preview showing note discovery cards and subject browsing"),
    ).toBeInTheDocument();

    expect(screen.getAllByText("Why NoteLib")).not.toHaveLength(0);
    expect(screen.getByRole("heading", { name: "Built for study, not just answers" })).toBeInTheDocument();
    expect(screen.getByText("Generic AI tools")).toBeInTheDocument();
    expect(screen.getAllByText("NoteLib")).not.toHaveLength(0);
    expect(screen.getByText("Your notes stay")).toBeInTheDocument();
    expect(screen.getByText("Weak areas remembered")).toBeInTheDocument();
    expect(screen.getByText("Exam-ready flow")).toBeInTheDocument();

    expect(screen.getByRole("button", { name: "Students" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Exam Reviewers" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Teachers" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Professionals" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "See guides for students" })).toHaveAttribute("href", "/learn#students");

    fireEvent.click(screen.getByRole("button", { name: "Exam Reviewers" }));
    expect(screen.getByRole("link", { name: "See guides for board exams" })).toHaveAttribute("href", "/learn#board-exams");
    expect(screen.getByRole("link", { name: "Explore Exam Hubs" })).toHaveAttribute("href", "/exam");

    fireEvent.click(screen.getByRole("button", { name: "Teachers" }));
    expect(screen.getByRole("link", { name: "See guides for teachers" })).toHaveAttribute("href", "/learn#teachers");

    fireEvent.click(screen.getByRole("button", { name: "Professionals" }));
    expect(screen.getByRole("link", { name: "See guides for professionals" })).toHaveAttribute("href", "/learn#professionals");

    expect(screen.getByText("Study Modes")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Five study modes, one workspace" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Quick Review" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Challenge Quiz" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Adaptive Practice" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Long Exam" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Board Exam" })).toBeInTheDocument();

    expect(screen.getByText("Pricing section placeholder")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "See full pricing" })).toHaveAttribute("href", "/pricing");

    expect(screen.getByRole("heading", { name: "Frequently asked questions" })).toBeInTheDocument();
    expect(screen.getByText("Is NoteLib free?")).toBeInTheDocument();
    expect(screen.getByText("Which board exams does NoteLib support?")).toBeInTheDocument();

    expect(screen.getByRole("heading", { name: "Start building your study system today" })).toBeInTheDocument();
    expect(screen.getByText("Takes less than a minute.")).toBeInTheDocument();

    expect(screen.getByRole("link", { name: "Privacy Policy" })).toHaveAttribute("href", "/privacy");
    expect(screen.getByRole("link", { name: "Terms of Service" })).toHaveAttribute("href", "/terms");
    expect(screen.getByRole("link", { name: "Contact" })).toHaveAttribute(
      "href",
      "mailto:support@mail.notelib.app",
    );

    const structuredData = container.querySelector("#landing-page-structured-data");
    expect(structuredData).not.toBeNull();
    expect(structuredData?.textContent).toContain('"@type":"WebSite"');
    expect(structuredData?.textContent).toContain('"name":"NoteLib"');

    const faqStructuredData = container.querySelector("#landing-faq-structured-data");
    expect(faqStructuredData).not.toBeNull();
    expect(faqStructuredData?.textContent).toContain('"@type":"FAQPage"');
    expect(faqStructuredData?.textContent).toContain("Is NoteLib free?");
  });

  it("exports landing page SEO metadata", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib — Build your notes library and turn notes into quizzes",
      description:
        "NoteLib is a notes library where you can organize notes and turn them into summaries, key concepts, and practice quizzes to review more effectively.",
      alternates: {
        canonical: "https://www.notelib.app",
      },
      openGraph: expect.objectContaining({
        title: "NoteLib — Build your notes library and turn notes into quizzes",
        description:
          "NoteLib is a notes library where you can organize notes and turn them into summaries, key concepts, and practice quizzes to review more effectively.",
        type: "website",
        url: "https://www.notelib.app",
        siteName: "NoteLib",
        images: expect.arrayContaining([
          expect.objectContaining({
            url: "https://www.notelib.app/og-image.png",
            alt: "Build your notes library. Turn your notes into summaries and quizzes.",
          }),
        ]),
      }),
      twitter: expect.objectContaining({
        card: "summary_large_image",
        title: "NoteLib — Build your notes library and turn notes into quizzes",
        description:
          "NoteLib is a notes library where you can organize notes and turn them into summaries, key concepts, and practice quizzes to review more effectively.",
        images: ["https://www.notelib.app/og-image.png"],
      }),
    });
  });

  it("omits social proof when the live public-note total is unavailable", async () => {
    (getServerPublicNoteCount as jest.Mock).mockResolvedValue(null);

    render(await Home());

    expect(screen.queryByLabelText("Public library activity")).not.toBeInTheDocument();
  });
});
