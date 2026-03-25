import { render, screen } from "@testing-library/react";
import PrivacyPage, { metadata } from "./page";

describe("PrivacyPage", () => {
  it("renders the privacy policy content", () => {
    render(<PrivacyPage />);

    expect(screen.getByRole("heading", { name: "Privacy Policy" })).toBeInTheDocument();
    expect(screen.getByText("Last updated: March 25, 2026")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "2. Information We Collect" })).toBeInTheDocument();
    expect(screen.getByText(/Payments are processed via PayMongo/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "support@mail.notelib.app" })).toHaveAttribute("href", "mailto:support@mail.notelib.app");
  });

  it("exports privacy metadata", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib Privacy Policy",
      alternates: {
        canonical: "https://www.notelib.app/privacy",
      },
    });
  });
});
