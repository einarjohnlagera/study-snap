import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import Home, { metadata } from "./page";

jest.mock("@/components/billing/pricing-plans-section", () => ({
  SimplePricingSection: () => (
    <section>
      <h2>Simple, Transparent Pricing</h2>
      <a href="/pricing">View Pricing</a>
    </section>
  ),
}));

jest.mock("@/components/analytics/page-view-tracker", () => ({
  AnalyticsPageViewTracker: () => null,
}));

jest.mock("@/components/analytics/tracked-link", () => ({
  TrackedLink: ({ href, className, children }: { href: string; className?: string; children: ReactNode }) => (
    <a href={href} className={className}>
      {children}
    </a>
  ),
}));

describe("LandingPage", () => {
  it("renders the updated marketing sections and CTAs", () => {
    const { container } = render(<Home />);

    expect(screen.getByRole("heading", { name: "Turn Notes Into Quizzes" })).toBeInTheDocument();
    expect(screen.getByText("Study Smarter. Not Harder.")).toBeInTheDocument();
    expect(
      screen.getByText("Generate summaries, key concepts, and practice quizzes from your notes in seconds."),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Get Started Free" })[0]).toHaveAttribute("href", "/signup");
    expect(screen.getByRole("link", { name: "Try Demo" })).toHaveAttribute("href", "/demo");

    expect(screen.getByRole("heading", { name: "From notes to practice in four steps" })).toBeInTheDocument();
    expect(screen.getByText("Add Notes")).toBeInTheDocument();
    expect(screen.getByText("Generate Study Pack")).toBeInTheDocument();
    expect(screen.getAllByText("Practice")).not.toHaveLength(0);
    expect(screen.getByText("Improve")).toBeInTheDocument();
    expect(screen.getByText("Notes")).toBeInTheDocument();
    expect(screen.getAllByText("Weak Concepts")).not.toHaveLength(0);

    expect(screen.getByText("For Students")).toBeInTheDocument();
    expect(screen.getByText("For Board Exams")).toBeInTheDocument();
    expect(screen.getByText("For Teachers")).toBeInTheDocument();

    expect(screen.getAllByText("Summaries")).not.toHaveLength(0);
    expect(screen.getAllByText("Key Concepts")).not.toHaveLength(0);
    expect(screen.getByText("Practice Quiz")).toBeInTheDocument();
    expect(screen.getByText("Weak Concept Insights")).toBeInTheDocument();
    expect(screen.getAllByText("Adaptive Practice")).not.toHaveLength(0);

    expect(screen.getByText("Simple, Transparent Pricing")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View Pricing" })).toHaveAttribute("href", "/pricing");
    expect(screen.getByRole("heading", { name: "Start Turning Your Notes Into Quizzes Today" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Privacy Policy" })).toHaveAttribute("href", "/privacy");
    expect(screen.getByRole("link", { name: "Terms of Service" })).toHaveAttribute("href", "/terms");
    expect(screen.getByRole("link", { name: "Contact" })).toHaveAttribute("href", "mailto:support@mail.notelib.app");

    const structuredData = container.querySelector("#landing-page-structured-data");
    expect(structuredData).not.toBeNull();
    expect(structuredData?.textContent).toContain('"@type":"WebSite"');
    expect(structuredData?.textContent).toContain('"name":"NoteLib"');
  });

  it("exports landing page SEO metadata", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib — Turn Notes Into Quizzes",
      description: "NoteLib helps students, board exam reviewees, and teachers turn notes into summaries, key concepts, and quizzes so they can study and prepare for exams faster.",
      alternates: {
        canonical: "https://notelib.app/",
      },
      openGraph: expect.objectContaining({
        type: "website",
        url: "https://notelib.app/",
        siteName: "NoteLib",
        images: expect.arrayContaining([
          expect.objectContaining({ url: "https://notelib.app/og-image.png" }),
        ]),
      }),
      twitter: expect.objectContaining({
        card: "summary_large_image",
        images: ["https://notelib.app/og-image.png"],
      }),
    });
  });
});
