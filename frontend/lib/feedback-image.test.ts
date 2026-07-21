import { FeedbackImageValidationError, prepareFeedbackImage } from "./feedback-image";

describe("prepareFeedbackImage", () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("rejects unsupported image types before decoding", async () => {
    const createImageBitmapMock = jest.fn();
    Object.defineProperty(globalThis, "createImageBitmap", {
      configurable: true,
      value: createImageBitmapMock,
    });

    await expect(prepareFeedbackImage(new File(["text"], "notes.txt", { type: "text/plain" })))
      .rejects.toEqual(new FeedbackImageValidationError("Choose a PNG, JPEG, or WebP screenshot."));
    expect(createImageBitmapMock).not.toHaveBeenCalled();
  });

  it("rejects an oversized image that needs no resizing, without claiming it was resized", async () => {
    const close = jest.fn();
    Object.defineProperty(globalThis, "createImageBitmap", {
      configurable: true,
      value: jest.fn().mockResolvedValue({ width: 800, height: 600, close }),
    });
    const oversizedImage = new File(
      [new Uint8Array((2 * 1024 * 1024) + 1)],
      "large.png",
      { type: "image/png" },
    );

    await expect(prepareFeedbackImage(oversizedImage)).rejects.toEqual(
      new FeedbackImageValidationError("Screenshot is over 2 MB. Choose a smaller image."),
    );
    expect(close).toHaveBeenCalled();
  });

  it("downscales the longest edge to 1600 pixels", async () => {
    const close = jest.fn();
    const drawImage = jest.fn();
    Object.defineProperty(globalThis, "createImageBitmap", {
      configurable: true,
      value: jest.fn().mockResolvedValue({ width: 3200, height: 1600, close }),
    });
    const canvas = {
      width: 0,
      height: 0,
      getContext: jest.fn(() => ({ drawImage })),
      toBlob: jest.fn((callback: BlobCallback) => callback(new Blob(["resized"], { type: "image/jpeg" }))),
    } as unknown as HTMLCanvasElement;
    const originalCreateElement = document.createElement.bind(document);
    jest.spyOn(document, "createElement").mockImplementation((tagName: string) => (
      tagName === "canvas" ? canvas : originalCreateElement(tagName)
    ));

    const result = await prepareFeedbackImage(new File(["original"], "screen.jpg", { type: "image/jpeg" }));

    expect(canvas.width).toBe(1600);
    expect(canvas.height).toBe(800);
    expect(drawImage).toHaveBeenCalled();
    expect(result.type).toBe("image/jpeg");
    expect(close).toHaveBeenCalled();
  });

  it("rejects an image that remains over two megabytes after resizing", async () => {
    const close = jest.fn();
    const drawImage = jest.fn();
    Object.defineProperty(globalThis, "createImageBitmap", {
      configurable: true,
      value: jest.fn().mockResolvedValue({ width: 3200, height: 1600, close }),
    });
    const canvas = {
      width: 0,
      height: 0,
      getContext: jest.fn(() => ({ drawImage })),
      toBlob: jest.fn((callback: BlobCallback) => callback(
        new Blob([new Uint8Array((2 * 1024 * 1024) + 1)], { type: "image/jpeg" }),
      )),
    } as unknown as HTMLCanvasElement;
    const originalCreateElement = document.createElement.bind(document);
    jest.spyOn(document, "createElement").mockImplementation((tagName: string) => (
      tagName === "canvas" ? canvas : originalCreateElement(tagName)
    ));

    await expect(prepareFeedbackImage(new File(["original"], "screen.jpg", { type: "image/jpeg" })))
      .rejects.toEqual(
        new FeedbackImageValidationError("Screenshot is still over 2 MB after resizing. Choose a smaller image."),
      );
    expect(close).toHaveBeenCalled();
  });
});
