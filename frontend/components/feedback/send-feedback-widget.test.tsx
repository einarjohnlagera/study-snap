import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { SendFeedbackWidget } from "./send-feedback-widget";
import { submitFeedback } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  submitFeedback: jest.fn(),
}));

describe("SendFeedbackWidget", () => {
  beforeEach(() => {
    (submitFeedback as jest.Mock).mockReset();
  });

  it("submits feedback and shows a success message", async () => {
    (submitFeedback as jest.Mock).mockResolvedValue({
      message: "Thanks! Your feedback helps improve NoteLib.",
    });

    render(<SendFeedbackWidget />);

    fireEvent.click(screen.getByRole("button", { name: /Send Feedback/i }));
    fireEvent.change(screen.getByLabelText("Message"), {
      target: { value: "The note detail page is hard to scan on mobile." },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "Send Feedback" })[1]);

    await waitFor(() => {
      expect(submitFeedback).toHaveBeenCalledWith(
        { message: "The note detail page is hard to scan on mobile." },
        "http://localhost/",
      );
    });

    expect(await screen.findByText("Thanks! Your feedback helps improve NoteLib.")).toBeInTheDocument();
  });

  it("renders an API error when submit fails", async () => {
    (submitFeedback as jest.Mock).mockRejectedValue(new Error("Could not send feedback."));

    render(<SendFeedbackWidget />);

    fireEvent.click(screen.getByRole("button", { name: /Send Feedback/i }));
    fireEvent.change(screen.getByLabelText("Message"), {
      target: { value: "The dashboard card layout feels cramped." },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "Send Feedback" })[1]);

    expect(await screen.findByText("Could not send feedback.")).toBeInTheDocument();
  });
});
