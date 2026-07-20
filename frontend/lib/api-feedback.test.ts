import { getAdminFeedbackImage, submitFeedback, uploadFeedbackImage } from "./api";

describe("feedback image API", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    jest.restoreAllMocks();
  });

  it("keeps the text feedback JSON body unchanged and reads the id response header", async () => {
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ "X-Feedback-Id": "feedback-1" }),
      json: jest.fn().mockResolvedValue({ message: "Thanks" }),
    } as unknown as Response);
    globalThis.fetch = fetchMock;

    await expect(submitFeedback({ message: "The card is clipped." }, "/dashboard"))
      .resolves.toEqual({ message: "Thanks", feedbackId: "feedback-1" });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.body).toBe(JSON.stringify({ message: "The card is clipped." }));
  });

  it("uploads the screenshot as a separate multipart request", async () => {
    const fetchMock = jest.fn().mockResolvedValue({ ok: true, status: 204 } as Response);
    globalThis.fetch = fetchMock;
    const screenshot = new File(["png"], "screen.png", { type: "image/png" });

    await uploadFeedbackImage("feedback-1", screenshot);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/feedback/feedback-1/image");
    expect(init.method).toBe("POST");
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get("image")).toBe(screenshot);
    expect(new Headers(init.headers).has("Content-Type")).toBe(false);
  });

  it("treats a missing admin screenshot as absent", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({ ok: false, status: 404 } as Response);

    await expect(getAdminFeedbackImage("feedback-1")).resolves.toBeNull();
  });
});
