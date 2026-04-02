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
  it("renders the note-library positioning sections and primary public discovery CTAs", () => {
    const { container } = render(<Home />);

    expect(
      screen.getByRole("heading", {
        name: "Build your own library of notes. Turn them into summaries and quizzes when you're ready to review.",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "NoteLib helps you organize your notes, generate summaries, extract key concepts, and practice with quizzes all in one study workspace.",
      ),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Get Started" })[0]).toHaveAttribute("href", "/signup");
    expect(screen.getAllByRole("link", { name: "View Public Library" })[0]).toHaveAttribute("href", "/public/library");
    expect(screen.getByRole("link", { name: "Try demo access" })).toHaveAttribute("href", "/demo");

    expect(screen.getByText("What Is NoteLib")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Your Notes. Your Library. Your Review Tool." })).toBeInTheDocument();
    expect(screen.getByText("Notes library first")).toBeInTheDocument();
    expect(screen.getByText("Study Pack when ready")).toBeInTheDocument();
    expect(screen.getByText("Active recall built in")).toBeInTheDocument();

    expect(screen.getByText("How It Works")).toBeInTheDocument();
    expect(screen.getByText("Create a Note")).toBeInTheDocument();
    expect(screen.getByText("Build Your Library")).toBeInTheDocument();
    expect(screen.getByText("Generate Study Pack")).toBeInTheDocument();
    expect(screen.getByText("Review & Practice")).toBeInTheDocument();

    expect(screen.getAllByText("Public Library")).not.toHaveLength(0);
    expect(screen.getByRole("heading", { name: "Explore Public Notes and Reviewers" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse Public Library" })).toHaveAttribute("href", "/public/library");

    expect(screen.getByText("Study Method")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Study Smarter with Active Recall" })).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Learn How to Study Using Active Recall" }),
    ).toHaveAttribute("href", "/learn");

    expect(screen.getByText("Simple, Transparent Pricing")).toBeInTheDocument();
    expect(screen.getByText("Pricing section placeholder")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Start building your notes library today." })).toBeInTheDocument();

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
