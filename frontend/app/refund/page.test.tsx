import { render, screen } from "@testing-library/react";
import RefundPage, { metadata } from "./page";

describe("RefundPage", () => {
  it("renders the refund and cancellation policy sections", () => {
    render(<RefundPage />);

    expect(screen.getByRole("heading", { name: "Refund & Cancellation Policy" })).toBeInTheDocument();
    expect(screen.getByText("Last updated: May 25, 2026")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "1. Cancellation" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "2. Refund Policy" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "3. How to Request a Refund" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "4. How to Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "5. Contact" })).toBeInTheDocument();
    expect(screen.getByText(/We will review your request within 2 business days/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "support@mail.notelib.app" })).toHaveAttribute("href", "mailto:support@mail.notelib.app");
  });

  it("exports refund metadata", () => {
    expect(metadata).toMatchObject({
      title: "Refund & Cancellation Policy — NoteLib",
      alternates: {
        canonical: "https://www.notelib.app/refund",
      },
    });
  });
});
