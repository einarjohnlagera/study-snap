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

  it("hides the launcher on mobile when requested", () => {
    render(<SendFeedbackWidget mobileHidden />);

    expect(screen.getByRole("button", { name: /Send Feedback/i })).toHaveClass("hidden");
  });

  it("does not render when fully hidden", () => {
    render(<SendFeedbackWidget hidden />);

    expect(screen.queryByRole("button", { name: /Send Feedback/i })).not.toBeInTheDocument();
  });

  it("renders an icon trigger variant for compact header entry", () => {
    render(<SendFeedbackWidget variant="icon" triggerLabel="Send Feedback" />);

    fireEvent.click(screen.getByRole("button", { name: "Send Feedback" }));

    expect(screen.getByRole("dialog", { name: "Send Feedback" })).toBeInTheDocument();
  });

  it("renders inline quiz feedback actions and prefills the modal", async () => {
    (submitFeedback as jest.Mock).mockResolvedValue({
      message: "Thanks! Your feedback helps improve NoteLib.",
    });

    render(
      <SendFeedbackWidget
        variant="inline"
        title="Help improve this quiz"
        description="Tell us what felt off."
        quickActions={[
          {
            label: "Report Question",
            template: "Feedback type: Report Question\nQuiz: Challenge Quiz\n\nWhat happened?",
          },
        ]}
      />,
    );

    expect(screen.getByText("Help improve this quiz")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Report Question" }));

    expect(screen.getByRole("dialog", { name: "Help improve this quiz" })).toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toHaveValue("Feedback type: Report Question\nQuiz: Challenge Quiz\n\nWhat happened?");

    fireEvent.change(screen.getByLabelText("Message"), {
      target: { value: "Feedback type: Report Question\nQuiz: Challenge Quiz\n\nThe stem is confusing." },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "Send Feedback" })[1]);

    await waitFor(() => {
      expect(submitFeedback).toHaveBeenCalledWith(
        { message: "Feedback type: Report Question\nQuiz: Challenge Quiz\n\nThe stem is confusing." },
        "http://localhost/",
      );
    });
  });
});
