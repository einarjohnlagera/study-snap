export type ImportedNoteFileKind = "txt" | "pdf" | "docx";

export type ImportedNoteFileResult = {
  kind: ImportedNoteFileKind;
  text: string;
};

const DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
const PDF_MIME = "application/pdf";
const TEXT_MIME = "text/plain";

const MAX_TXT_BYTES = 1 * 1024 * 1024;
const MAX_DOCX_BYTES = 10 * 1024 * 1024;
const MAX_PDF_BYTES = 10 * 1024 * 1024;
const MAX_PDF_PAGES = 30;
const MAX_EXTRACTED_CHARACTERS = 200_000;

function normalizeText(value: string): string {
  return value
    .replace(/\r\n/g, "\n")
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

function getFileExtension(fileName: string): string {
  const normalized = fileName.trim().toLowerCase();
  const lastDot = normalized.lastIndexOf(".");
  return lastDot >= 0 ? normalized.slice(lastDot) : "";
}

function resolveImportedFileKind(file: File): ImportedNoteFileKind | null {
  const extension = getFileExtension(file.name);
  const mimeType = (file.type ?? "").trim().toLowerCase();

  if (extension === ".txt" || mimeType === TEXT_MIME) {
    return "txt";
  }
  if (extension === ".pdf" || mimeType === PDF_MIME) {
    return "pdf";
  }
  if (extension === ".docx" || mimeType === DOCX_MIME) {
    return "docx";
  }
  return null;
}

function assertSupportedFile(file: File): ImportedNoteFileKind {
  const kind = resolveImportedFileKind(file);
  if (!kind) {
    throw new Error("Unsupported file type. Upload a TXT, PDF, or DOCX file.");
  }

  const sizeLimit = kind === "txt" ? MAX_TXT_BYTES : kind === "pdf" ? MAX_PDF_BYTES : MAX_DOCX_BYTES;
  if (file.size > sizeLimit) {
    const limitMb = Math.max(1, Math.round(sizeLimit / (1024 * 1024)));
    throw new Error(`This file is too large. Upload a ${kind.toUpperCase()} file under ${limitMb} MB.`);
  }

  return kind;
}

async function readFileAsText(file: File): Promise<string> {
  if (typeof file.text === "function") {
    return file.text();
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Could not read this file."));
    reader.onload = () => resolve(typeof reader.result === "string" ? reader.result : "");
    reader.readAsText(file);
  });
}

async function readFileAsArrayBuffer(file: File): Promise<ArrayBuffer> {
  if (typeof file.arrayBuffer === "function") {
    return file.arrayBuffer();
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Could not read this file."));
    reader.onload = () => {
      if (reader.result instanceof ArrayBuffer) {
        resolve(reader.result);
        return;
      }
      reject(new Error("Could not read this file."));
    };
    reader.readAsArrayBuffer(file);
  });
}

async function importTxt(file: File): Promise<ImportedNoteFileResult> {
  const text = normalizeText(await readFileAsText(file));
  if (!text) {
    throw new Error("This file is empty or has no readable text.");
  }
  return { kind: "txt", text };
}

async function importDocx(file: File): Promise<ImportedNoteFileResult> {
  const mammoth = await import("mammoth");
  const result = await mammoth.extractRawText({ arrayBuffer: await readFileAsArrayBuffer(file) });
  const text = normalizeText(result.value ?? "");
  if (!text) {
    throw new Error("This DOCX file is empty or has no readable text.");
  }
  if (text.length > MAX_EXTRACTED_CHARACTERS) {
    throw new Error("This document is too large to import at once. Try a shorter DOCX file.");
  }
  return { kind: "docx", text };
}

async function importPdf(file: File): Promise<ImportedNoteFileResult> {
  const pdfjs = await import("pdfjs-dist/webpack.mjs");
  const documentTask = pdfjs.getDocument({ data: new Uint8Array(await readFileAsArrayBuffer(file)) });
  const pdfDocument = await documentTask.promise;

  if (pdfDocument.numPages > MAX_PDF_PAGES) {
    throw new Error(`This PDF is too long to import at once. Try a PDF with ${MAX_PDF_PAGES} pages or fewer.`);
  }

  const pages: string[] = [];
  for (let pageNumber = 1; pageNumber <= pdfDocument.numPages; pageNumber += 1) {
    const page = await pdfDocument.getPage(pageNumber);
    const textContent = await page.getTextContent();
    const pageText = textContent.items
      .map((item) => ("str" in item && typeof item.str === "string" ? item.str : ""))
      .join(" ")
      .trim();
    if (pageText) {
      pages.push(pageText);
    }
  }

  const text = normalizeText(pages.join("\n\n"));
  if (!text) {
    throw new Error("This PDF appears to be scanned or image-based. Please upload images for OCR instead.");
  }
  if (text.length > MAX_EXTRACTED_CHARACTERS) {
    throw new Error("This PDF contains too much text to import at once. Try a shorter PDF.");
  }

  return { kind: "pdf", text };
}

export async function importNoteFile(file: File): Promise<ImportedNoteFileResult> {
  const kind = assertSupportedFile(file);

  switch (kind) {
    case "txt":
      return importTxt(file);
    case "docx":
      return importDocx(file);
    case "pdf":
      return importPdf(file);
    default:
      throw new Error("Unsupported file type. Upload a TXT, PDF, or DOCX file.");
  }
}
