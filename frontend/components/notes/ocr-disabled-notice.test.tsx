import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { OcrDisabledNotice } from "./ocr-disabled-notice";
import { trackAnalyticsEvent } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

describe("OcrDisabledNotice", () => {
  beforeEach(() => {
    (trackAnalyticsEvent as jest.Mock).mockReset();
  });

  it("tracks the notice once per mount and records one feedback click", async () => {
    const { rerender } = render(<OcrDisabledNotice message="" source="test_surface" />);

    expect(screen.getByText("Image reading is temporarily unavailable")).toBeInTheDocument();
    expect(screen.getByText("Image and scanned-document reading is temporarily unavailable. Try a PDF or document with selectable text instead.")).toBeInTheDocument();
    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);
    });
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "OCR_DISABLED_NOTICE_SHOWN",
      metadata: { source: "test_surface" },
    });

    rerender(<OcrDisabledNotice message="Still unavailable." source="test_surface" />);
    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "Yes, I'd like this back" }));
    expect(screen.getByRole("button", { name: "Thanks - noted!" })).toBeDisabled();
    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(2);
    expect(trackAnalyticsEvent).toHaveBeenLastCalledWith({
      eventType: "OCR_DISABLED_FEEDBACK_INTERESTED",
      metadata: { source: "test_surface" },
    });

    fireEvent.click(screen.getByRole("button", { name: "Thanks - noted!" }));
    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(2);
  });
});
