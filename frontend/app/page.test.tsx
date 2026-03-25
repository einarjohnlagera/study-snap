import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import Home, { metadata } from "./page";

jest.mock("@/components/billing/pricing-plans-section", () => ({
  PricingPlansSection: () => (
    <section>
      <h2>Move from note-taking to exam prep</h2>
      <p>Pricing section placeholder</p>
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
  it("renders the core marketing sections and CTAs", () => {
    const { container } = render(<Home />);

    expect(screen.getByRole("heading", {
      name: "Turn your notes into summaries, quizzes, and reviewers in seconds.",
    })).toBeInTheDocument();
    expect(
      screen.getByText("Study smarter with AI-powered summaries, key concepts, and practice quizzes."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Get Started Free" })).toHaveAttribute("href", "/auth");
    expect(screen.getAllByRole("link", { name: "Try Demo" })[0]).toHaveAttribute("href", "/demo");

    expect(screen.getByText("Create or Import Notes")).toBeInTheDocument();
    expect(screen.getAllByText("Generate Study Pack")).not.toHaveLength(0);
    expect(screen.getByText("Review and Practice")).toBeInTheDocument();

    expect(screen.getAllByText("Summaries")).not.toHaveLength(0);
    expect(screen.getAllByText("Key Concepts")).not.toHaveLength(0);
    expect(screen.getByText("Quick Review")).toBeInTheDocument();
    expect(screen.getByText("Challenge Quiz")).toBeInTheDocument();
    expect(screen.getByText("Adaptive Practice")).toBeInTheDocument();

    expect(screen.getByText("Move from note-taking to exam prep")).toBeInTheDocument();
    expect(screen.getByText("Try a demo Study Pack now — no signup required.")).toBeInTheDocument();
    expect(screen.getByText("Start studying smarter today.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Free Account" })).toHaveAttribute("href", "/auth");
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
      title: "NoteLib – Turn Notes into Study Packs, Summaries, and Quizzes",
      description: "Turn your notes into summaries, key concepts, and practice questions so you can study smarter.",
      alternates: {
        canonical: "https://www.notelib.app/",
      },
      openGraph: expect.objectContaining({
        type: "website",
        url: "https://www.notelib.app/",
        siteName: "NoteLib",
        images: expect.arrayContaining([
          expect.objectContaining({ url: "https://www.notelib.app/og-image.png" }),
        ]),
      }),
      twitter: expect.objectContaining({
        card: "summary_large_image",
        images: ["https://www.notelib.app/og-image.png"],
      }),
    });
  });
});
