import { render, screen } from "@testing-library/react";
import TermsPage, { metadata } from "./page";

describe("TermsPage", () => {
  it("renders the terms content", () => {
    render(<TermsPage />);

    expect(screen.getByRole("heading", { name: "Terms of Service" })).toBeInTheDocument();
    expect(screen.getByText("Last updated: May 26, 2026")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "4. Acceptable Use" })).toBeInTheDocument();
    expect(screen.getByText(/NoteLib offers manual-renewal paid plans/i)).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "support@mail.notelib.app" })[0]).toHaveAttribute("href", "mailto:support@mail.notelib.app");
  });

  it("exports terms metadata", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib Terms of Service",
      alternates: {
        canonical: "https://notelib.app/terms",
      },
    });
  });
});
