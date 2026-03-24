import { importNoteFile } from "./note-import";

jest.mock("mammoth", () => ({
  extractRawText: jest.fn(),
}));

jest.mock("pdfjs-dist/webpack.mjs", () => ({
  getDocument: jest.fn(),
}));

describe("importNoteFile", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("reads TXT file content", async () => {
    const file = new File(["  Line one\n\nLine two  "], "notes.txt", { type: "text/plain" });

    await expect(importNoteFile(file)).resolves.toEqual({
      kind: "txt",
      text: "Line one\n\nLine two",
    });
  });

  it("extracts DOCX text content", async () => {
    const mammoth = await import("mammoth");
    (mammoth.extractRawText as jest.Mock).mockResolvedValue({
      value: "Docx text content",
    });
    const file = new File(["docx"], "notes.docx", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    });

    await expect(importNoteFile(file)).resolves.toEqual({
      kind: "docx",
      text: "Docx text content",
    });
  });

  it("extracts text-based PDF content", async () => {
    const pdfjs = await import("pdfjs-dist/webpack.mjs");
    (pdfjs.getDocument as jest.Mock).mockReturnValue({
      promise: Promise.resolve({
        numPages: 2,
        getPage: jest
          .fn()
          .mockResolvedValueOnce({
            getTextContent: jest.fn().mockResolvedValue({
              items: [{ str: "Page" }, { str: "one" }],
            }),
          })
          .mockResolvedValueOnce({
            getTextContent: jest.fn().mockResolvedValue({
              items: [{ str: "Page" }, { str: "two" }],
            }),
          }),
      }),
    });
    const file = new File(["pdf"], "notes.pdf", { type: "application/pdf" });

    await expect(importNoteFile(file)).resolves.toEqual({
      kind: "pdf",
      text: "Page one\n\nPage two",
    });
  });

  it("rejects unsupported file types", async () => {
    const file = new File(["csv"], "notes.csv", { type: "text/csv" });

    await expect(importNoteFile(file)).rejects.toThrow("Unsupported file type. Upload a TXT, PDF, or DOCX file.");
  });

  it("rejects scanned or image-based PDFs without text", async () => {
    const pdfjs = await import("pdfjs-dist/webpack.mjs");
    (pdfjs.getDocument as jest.Mock).mockReturnValue({
      promise: Promise.resolve({
        numPages: 1,
        getPage: jest.fn().mockResolvedValue({
          getTextContent: jest.fn().mockResolvedValue({
            items: [],
          }),
        }),
      }),
    });
    const file = new File(["pdf"], "scan.pdf", { type: "application/pdf" });

    await expect(importNoteFile(file)).rejects.toThrow(
      "This PDF appears to be scanned or image-based. Please upload images for OCR instead.",
    );
  });
});
