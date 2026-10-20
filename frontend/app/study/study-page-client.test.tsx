import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import StudyPageClient from "./study-page-client";
import {
  confirmStudyPackText,
  createNote,
  createStudyPackFromImage,
  createStudyPackFromText,
  trackAnalyticsEvent,
} from "@/lib/api";
import { getAuthUser, getCurrentUserId } from "@/lib/auth";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
  useSearchParams: () => new URLSearchParams(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  getCurrentUserId: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  confirmStudyPackText: jest.fn(),
  createNote: jest.fn(),
  createStudyPackFromImage: jest.fn(),
  createStudyPackFromText: jest.fn(),
  isEmailNotVerifiedError: (error: unknown) => error instanceof Error && error.message === "EMAIL_VERIFICATION_REQUIRED",
  isNeedsTextConfirmationResponse: (response: { status?: string }) => response.status === "needs_text_confirmation",
  isOcrDisabledError: (error: unknown) => (
    error instanceof Error && (error as Error & { code?: string }).code === "OCR_DISABLED"
  ),
  trackAnalyticsEvent: jest.fn(),
}));

describe("StudyPageClient", () => {
  beforeAll(() => {
    Object.defineProperty(globalThis.URL, "createObjectURL", {
      configurable: true,
      value: jest.fn(() => "blob:notelib-test"),
    });
    Object.defineProperty(globalThis.URL, "revokeObjectURL", {
      configurable: true,
      value: jest.fn(),
    });
  });

  beforeEach(() => {
    pushMock.mockReset();
    (createNote as jest.Mock).mockReset();
    (createStudyPackFromImage as jest.Mock).mockReset();
    (createStudyPackFromText as jest.Mock).mockReset();
    (confirmStudyPackText as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getCurrentUserId as jest.Mock).mockReturnValue("user-1");
  });

  it("offers an actionable retry after generation fails, and retries only when clicked", async () => {
    (createStudyPackFromText as jest.Mock)
      .mockRejectedValueOnce(new Error("Study Pack generation timed out. Please try again."))
      .mockReturnValueOnce(new Promise(() => {}));

    render(<StudyPageClient />);

    fireEvent.change(screen.getByLabelText("Study Notes"), { target: { value: "Photosynthesis notes" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("Couldn't Generate Study Pack")).toBeInTheDocument();
    expect(createStudyPackFromText).toHaveBeenCalledTimes(1);
    const retry = screen.getByRole("button", { name: "Try again" });

    fireEvent.click(retry);
    fireEvent.click(retry);

    await waitFor(() => expect(createStudyPackFromText).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole("button", { name: "Try again" })).not.toBeInTheDocument();
  });

  it("keeps the edited-text confirm action and hides generation retry after confirmation fails", async () => {
    (createStudyPackFromImage as jest.Mock).mockResolvedValue({
      status: "needs_text_confirmation",
      id: "pack-1",
      extractedText: "Original OCR text",
      meta: { ocrConfidence: 0.8, latencyMs: 100 },
    });
    (confirmStudyPackText as jest.Mock).mockRejectedValue(new Error("Confirmation failed"));

    render(<StudyPageClient />);
    fireEvent.change(screen.getByLabelText("Notes Photo (OCR, optional)"), {
      target: { files: [new File(["img"], "note.png", { type: "image/png" })] },
    });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));
    const confirm = await screen.findByRole("button", { name: "Continue With Edited Text" });
    fireEvent.change(screen.getByDisplayValue("Original OCR text"), { target: { value: "Edited OCR text" } });
    fireEvent.click(confirm);

    expect(await screen.findByText("Confirmation failed")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Try again" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue With Edited Text" })).toBeEnabled();
    expect(confirmStudyPackText).toHaveBeenCalledWith("pack-1", "Edited OCR text");
    expect(createStudyPackFromImage).toHaveBeenCalledTimes(1);
  });

  it("does not offer generation retry when signed out", async () => {
    (getCurrentUserId as jest.Mock).mockReturnValue(null);
    render(<StudyPageClient />);
    fireEvent.change(screen.getByLabelText("Study Notes"), { target: { value: "Photosynthesis notes" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("Sign up and log in to start generating your Study Pack.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Try again" })).not.toBeInTheDocument();
    expect(createStudyPackFromText).not.toHaveBeenCalled();
  });

  it("offers generation retry after an image failure and re-runs image generation once", async () => {
    (createStudyPackFromImage as jest.Mock)
      .mockRejectedValueOnce(new Error("No readable text"))
      .mockReturnValueOnce(new Promise(() => {}));
    render(<StudyPageClient />);
    fireEvent.change(screen.getByLabelText("Notes Photo (OCR, optional)"), {
      target: { files: [new File(["img"], "note.png", { type: "image/png" })] },
    });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    const retry = await screen.findByRole("button", { name: "Try again" });
    expect(createStudyPackFromImage).toHaveBeenCalledTimes(1);
    fireEvent.click(retry);
    await waitFor(() => expect(createStudyPackFromImage).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole("button", { name: "Try again" })).not.toBeInTheDocument();
  });

  it("renders the OCR disabled notice for photo-to-Study-Pack capture", async () => {
    (createStudyPackFromImage as jest.Mock).mockRejectedValue(
      Object.assign(
        new Error("Image and scanned-document reading is temporarily unavailable. Try a PDF or document with selectable text instead."),
        { code: "OCR_DISABLED" },
      ),
    );

    render(<StudyPageClient />);

    const imageInput = screen.getByLabelText("Notes Photo (OCR, optional)");
    const image = new File(["img"], "note.png", { type: "image/png" });
    fireEvent.change(imageInput, { target: { files: [image] } });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("Image reading is temporarily unavailable")).toBeInTheDocument();
    expect(screen.getByText("Image and scanned-document reading is temporarily unavailable. Try a PDF or document with selectable text instead.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Yes, I'd like this back" })).toBeInTheDocument();
    expect(screen.queryByText("Couldn't Process Notes Image")).not.toBeInTheDocument();
    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "OCR_DISABLED_NOTICE_SHOWN",
        metadata: { source: "study_pack_image_capture" },
      });
    });
  });
});
