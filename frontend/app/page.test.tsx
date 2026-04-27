import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import Home, { metadata } from "./page";

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
  it("renders the redesigned conversion-focused landing flow", () => {
    const { container } = render(<Home />);

    expect(screen.getAllByAltText("NoteLib")).not.toHaveLength(0);
    expect(
      screen.getByRole("heading", {
        name: "Turn anything into a complete study flow",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Write, paste, or generate notes - then turn them into summaries, key concepts, quizzes, and exam-ready practice."),
    ).toBeInTheDocument();
    expect(screen.getByText("Board Exam Mode · Premium")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Start for Free" })[0]).toHaveAttribute("href", "/signup");
    expect(screen.getAllByRole("link", { name: "Browse Public Library" })).toHaveLength(1);
    expect(screen.getByRole("link", { name: "Browse Public Library" })).toHaveAttribute("href", "/public/library");
    expect(screen.getByAltText("NoteLib note detail showing summary of the note")).toBeInTheDocument();

    expect(screen.getByText("Learning Loop")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "How NoteLib helps you study" })).toBeInTheDocument();
    expect(screen.getByText("Create - Understand - Practice - Challenge - Improve")).toBeInTheDocument();
    expect(screen.getByText("Create")).toBeInTheDocument();
    expect(screen.getByText("Understand")).toBeInTheDocument();
    expect(screen.getByText("Practice")).toBeInTheDocument();
    expect(screen.getByText("Challenge")).toBeInTheDocument();
    expect(screen.getByText("Improve")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View the full walkthrough" })).toHaveAttribute("href", "/how-it-works");

    expect(screen.getByText("Features")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Built for a guided study flow" })).toBeInTheDocument();
    expect(screen.getByText("Built for studying, not just exploring information.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Move from input to understanding faster" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Practice in layers" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Keep improving, not restarting" })).toBeInTheDocument();
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

    expect(screen.getByText("Why NoteLib")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Built for study, not just answers" })).toBeInTheDocument();
    expect(screen.getByText("Generic AI tools")).toBeInTheDocument();
    expect(screen.getAllByText("NoteLib")).not.toHaveLength(0);
    expect(screen.getByText("Built around your own notes")).toBeInTheDocument();
    expect(screen.getByText("Designed for active recall")).toBeInTheDocument();

    expect(screen.getByText("Who It's For")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Made for learners who need more than passive notes" })).toBeInTheDocument();
    expect(screen.getByText("Students")).toBeInTheDocument();
    expect(screen.getByText("Board Exam Takers")).toBeInTheDocument();
    expect(screen.getByText("Teachers")).toBeInTheDocument();

    expect(screen.getByText("Pricing section placeholder")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "See full pricing" })).toHaveAttribute("href", "/pricing");

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
  });

  it("exports landing page SEO metadata", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib - Turn anything into a complete study flow",
      description:
        "NoteLib is a study system that guides users from input to understanding, practice, and mastery with notes, summaries, key concepts, quizzes, and exam-ready review.",
      alternates: {
        canonical: "https://www.notelib.app",
      },
      openGraph: expect.objectContaining({
        title: "NoteLib - Turn anything into a complete study flow",
        description:
          "NoteLib is a study system that guides users from input to understanding, practice, and mastery with notes, summaries, key concepts, quizzes, and exam-ready review.",
        type: "website",
        url: "https://www.notelib.app",
        siteName: "NoteLib",
        images: expect.arrayContaining([
          expect.objectContaining({
            url: "https://www.notelib.app/og-image.png",
            alt: "Turn anything into a complete study flow with NoteLib.",
          }),
        ]),
      }),
      twitter: expect.objectContaining({
        card: "summary_large_image",
        title: "NoteLib - Turn anything into a complete study flow",
        description:
          "NoteLib is a study system that guides users from input to understanding, practice, and mastery with notes, summaries, key concepts, quizzes, and exam-ready review.",
        images: ["https://www.notelib.app/og-image.png"],
      }),
    });
  });
});
