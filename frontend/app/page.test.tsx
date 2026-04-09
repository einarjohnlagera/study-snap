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
        name: "Turn your notes into summaries, quizzes, and exam simulations",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Save your notes once, then turn them into Study Packs, Challenge Quizzes, and board-style practice that helps you learn through active recall instead of passive rereading.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Board Exam Mode · Free for a limited time")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Start for Free" })[0]).toHaveAttribute("href", "/signup");
    expect(screen.getByRole("link", { name: "See how it works" })).toHaveAttribute("href", "#how-it-works");
    expect(screen.getByRole("link", { name: "Try demo access" })).toHaveAttribute("href", "/demo");
    expect(screen.getByRole("link", { name: "Browse Public Library" })).toHaveAttribute("href", "/public/library");

    expect(screen.getByText("How It Works")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Go from notes to self-testing in three steps" })).toBeInTheDocument();
    expect(screen.getByText("Add notes")).toBeInTheDocument();
    expect(screen.getByText("Generate study pack")).toBeInTheDocument();
    expect(screen.getByText("Test yourself")).toBeInTheDocument();

    expect(screen.getByText("Features")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Built for review, not just one-time AI output" })).toBeInTheDocument();
    expect(screen.getByText("Study Packs")).toBeInTheDocument();
    expect(screen.getAllByText("Challenge Quiz")).not.toHaveLength(0);
    expect(screen.getAllByText("Adaptive Practice")).not.toHaveLength(0);
    expect(screen.getAllByText("Board Exam Mode")).not.toHaveLength(0);

    expect(screen.getByText("Why NoteLib")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "More useful than a generic AI answer box" })).toBeInTheDocument();
    expect(screen.getByText("Generic AI tools")).toBeInTheDocument();
    expect(screen.getAllByText("NoteLib")).not.toHaveLength(0);
    expect(screen.getByText("Starts from your own material")).toBeInTheDocument();
    expect(screen.getByText("Designed for repeated review")).toBeInTheDocument();

    expect(screen.getByText("Who It's For")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Made for learners who need more than passive notes" })).toBeInTheDocument();
    expect(screen.getByText("Students")).toBeInTheDocument();
    expect(screen.getByText("Board exam reviewees")).toBeInTheDocument();
    expect(screen.getByText("Teachers and tutors")).toBeInTheDocument();

    expect(screen.getByText("Pricing section placeholder")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "See full pricing" })).toHaveAttribute("href", "/pricing");

    expect(screen.getByRole("heading", { name: "Build a study system from the notes you already have." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View Pricing" })).toHaveAttribute("href", "/pricing");

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
});
