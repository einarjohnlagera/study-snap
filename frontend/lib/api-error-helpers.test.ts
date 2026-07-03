import {
  ApiRequestError,
  isOcrDisabledError,
  isOcrLimitReachedError,
  OCR_DISABLED_ERROR_CODE,
} from "./api";

describe("api error helpers", () => {
  it("detects OCR disabled errors by backend code", () => {
    const error = new ApiRequestError("OCR is paused.", {
      code: OCR_DISABLED_ERROR_CODE,
      status: 503,
    });

    expect(isOcrDisabledError(error)).toBe(true);
    expect(isOcrLimitReachedError(error)).toBe(false);
  });

  it("does not treat generic errors as OCR disabled errors", () => {
    expect(isOcrDisabledError(new Error("OCR_DISABLED"))).toBe(false);
    expect(isOcrDisabledError(new ApiRequestError("Limit reached.", {
      code: "OCR_LIMIT_REACHED",
      status: 429,
    }))).toBe(false);
  });
});
