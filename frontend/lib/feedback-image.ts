const MAX_FEEDBACK_IMAGE_BYTES = 2 * 1024 * 1024;
const MAX_FEEDBACK_IMAGE_DIMENSION = 1600;
const SUPPORTED_FEEDBACK_IMAGE_TYPES = new Set(["image/png", "image/jpeg", "image/webp"]);

const INVALID_TYPE_MESSAGE = "Choose a PNG, JPEG, or WebP screenshot.";
const TOO_LARGE_MESSAGE = "Screenshot is over 2 MB. Choose a smaller image.";
const STILL_TOO_LARGE_AFTER_RESIZE_MESSAGE = "Screenshot is still over 2 MB after resizing. Choose a smaller image.";

export class FeedbackImageValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "FeedbackImageValidationError";
  }
}

export async function prepareFeedbackImage(file: File): Promise<File> {
  if (!SUPPORTED_FEEDBACK_IMAGE_TYPES.has(file.type)) {
    throw new FeedbackImageValidationError(INVALID_TYPE_MESSAGE);
  }

  let bitmap: ImageBitmap;
  try {
    bitmap = await globalThis.createImageBitmap(file);
  } catch {
    throw new FeedbackImageValidationError(INVALID_TYPE_MESSAGE);
  }

  try {
    const longestEdge = Math.max(bitmap.width, bitmap.height);
    if (longestEdge <= MAX_FEEDBACK_IMAGE_DIMENSION) {
      assertImageSize(file.size, TOO_LARGE_MESSAGE);
      return file;
    }

    const scale = MAX_FEEDBACK_IMAGE_DIMENSION / longestEdge;
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const context = canvas.getContext("2d");
    if (!context) {
      throw new FeedbackImageValidationError("Could not resize this screenshot. Choose a smaller image.");
    }
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const resizedBlob = await canvasToBlob(canvas, file.type);
    assertImageSize(resizedBlob.size, STILL_TOO_LARGE_AFTER_RESIZE_MESSAGE);
    return new File([resizedBlob], file.name, {
      type: file.type,
      lastModified: file.lastModified,
    });
  } finally {
    bitmap.close();
  }
}

export function formatFeedbackImageSize(sizeBytes: number): string {
  if (sizeBytes >= 1024 * 1024) {
    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${Math.max(1, Math.round(sizeBytes / 1024))} KB`;
}

function assertImageSize(sizeBytes: number, message: string) {
  if (sizeBytes > MAX_FEEDBACK_IMAGE_BYTES) {
    throw new FeedbackImageValidationError(message);
  }
}

function canvasToBlob(canvas: HTMLCanvasElement, contentType: string): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) {
          resolve(blob);
          return;
        }
        reject(new FeedbackImageValidationError("Could not resize this screenshot. Choose a smaller image."));
      },
      contentType,
      contentType === "image/png" ? undefined : 0.85,
    );
  });
}
