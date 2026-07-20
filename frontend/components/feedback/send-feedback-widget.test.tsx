import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { SendFeedbackWidget } from "./send-feedback-widget";
import { submitFeedback, uploadFeedbackImage } from "@/lib/api";
import { FeedbackImageValidationError, prepareFeedbackImage } from "@/lib/feedback-image";

jest.mock("@/lib/api", () => ({
  submitFeedback: jest.fn(),
  uploadFeedbackImage: jest.fn(),
}));

jest.mock("@/lib/feedback-image", () => {
  class MockFeedbackImageValidationError extends Error {}
  return {
    FeedbackImageValidationError: MockFeedbackImageValidationError,
    prepareFeedbackImage: jest.fn(),
    formatFeedbackImageSize: (size: number) => `${size} bytes`,
  };
});

describe("SendFeedbackWidget", () => {
  beforeEach(() => {
    (submitFeedback as jest.Mock).mockReset();
    (uploadFeedbackImage as jest.Mock).mockReset();
    (prepareFeedbackImage as jest.Mock).mockReset();
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: jest.fn(() => "blob:feedback-preview"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: jest.fn(),
    });
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
    expect(screen.getByLabelText("Message")).toHaveValue("The dashboard card layout feels cramped.");
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
    expect(screen.getByText("Reporting: Report Question")).toBeInTheDocument();
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

  it("clears a quick-action prefill when its context chip is dismissed", () => {
    render(
      <SendFeedbackWidget
        variant="inline"
        showTriggerButton={false}
        quickActions={[{ label: "Report Question", template: "Prefilled question context" }]}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Report Question" }));
    expect(screen.getByLabelText("Message")).toHaveValue("Prefilled question context");

    fireEvent.click(screen.getByRole("button", { name: "Clear Report Question feedback context" }));

    expect(screen.getByLabelText("Message")).toHaveValue("");
    expect(screen.queryByText("Reporting: Report Question")).not.toBeInTheDocument();
  });

  it("updates the character counter and changes tone past its warning thresholds", () => {
    render(<SendFeedbackWidget />);
    fireEvent.click(screen.getByRole("button", { name: /Send Feedback/i }));

    const messageField = screen.getByLabelText("Message");
    expect(screen.getByText("0 / 4000")).toHaveClass("text-foreground/50");

    fireEvent.change(messageField, { target: { value: "a".repeat(3801) } });
    expect(screen.getByText("3801 / 4000")).toHaveClass("text-amber-600");

    fireEvent.change(messageField, { target: { value: "a".repeat(3951) } });
    expect(screen.getByText("3951 / 4000")).toHaveClass("text-red-600");
  });

  it("shows a selected screenshot preview and removes it without changing the message", async () => {
    const screenshot = new File(["png"], "dashboard.png", { type: "image/png" });
    (prepareFeedbackImage as jest.Mock).mockResolvedValue(screenshot);
    render(<SendFeedbackWidget />);
    fireEvent.click(screen.getByRole("button", { name: /Send Feedback/i }));
    fireEvent.change(screen.getByLabelText("Message"), { target: { value: "The card is clipped." } });

    fireEvent.change(screen.getByLabelText("Choose feedback screenshot"), {
      target: { files: [screenshot] },
    });

    expect(await screen.findByRole("img", { name: "Screenshot preview" })).toHaveAttribute(
      "src",
      "blob:feedback-preview",
    );
    expect(screen.getByText("dashboard.png")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Remove screenshot" }));

    expect(screen.queryByRole("img", { name: "Screenshot preview" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toHaveValue("The card is clipped.");
  });

  it("shows attachment validation errors without disturbing the feedback message", async () => {
    const invalidFile = new File(["text"], "notes.txt", { type: "text/plain" });
    (prepareFeedbackImage as jest.Mock).mockRejectedValue(
      new FeedbackImageValidationError("Choose a PNG, JPEG, or WebP screenshot."),
    );
    render(<SendFeedbackWidget />);
    fireEvent.click(screen.getByRole("button", { name: /Send Feedback/i }));
    fireEvent.change(screen.getByLabelText("Message"), { target: { value: "Keep this typed report." } });

    fireEvent.change(screen.getByLabelText("Choose feedback screenshot"), {
      target: { files: [invalidFile] },
    });

    expect(await screen.findByText("Choose a PNG, JPEG, or WebP screenshot.")).toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toHaveValue("Keep this typed report.");
    expect(screen.getAllByRole("button", { name: "Send Feedback" })[1]).toBeEnabled();
  });

  it("keeps successful text feedback when the screenshot upload fails", async () => {
    const screenshot = new File(["png"], "dashboard.png", { type: "image/png" });
    (prepareFeedbackImage as jest.Mock).mockResolvedValue(screenshot);
    (submitFeedback as jest.Mock).mockResolvedValue({
      message: "Thanks! Your feedback helps improve NoteLib.",
      feedbackId: "feedback-1",
    });
    (uploadFeedbackImage as jest.Mock).mockRejectedValue(new Error("Upload failed"));
    render(<SendFeedbackWidget />);
    fireEvent.click(screen.getByRole("button", { name: /Send Feedback/i }));
    fireEvent.change(screen.getByLabelText("Message"), { target: { value: "The card is clipped." } });
    fireEvent.change(screen.getByLabelText("Choose feedback screenshot"), {
      target: { files: [screenshot] },
    });
    await screen.findByRole("img", { name: "Screenshot preview" });

    fireEvent.click(screen.getAllByRole("button", { name: "Send Feedback" })[1]);

    expect(await screen.findByText("Thanks! Your feedback helps improve NoteLib.")).toBeInTheDocument();
    expect(screen.getByText("Note: your screenshot couldn't be attached, but your feedback was sent.")).toBeInTheDocument();
    expect(submitFeedback).toHaveBeenCalledWith(
      { message: "The card is clipped." },
      "http://localhost/",
    );
    expect(uploadFeedbackImage).toHaveBeenCalledWith("feedback-1", screenshot);
  });
});
