import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import StudyPageClient from "./study-page-client";
import {
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
  isNeedsTextConfirmationResponse: () => false,
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
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({ emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getCurrentUserId as jest.Mock).mockReturnValue("user-1");
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
