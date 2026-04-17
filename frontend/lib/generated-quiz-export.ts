import type { QuizItem } from "@/lib/api";
import { resolveQuizCorrectIndex } from "@/lib/quiz";

export type GeneratedQuizExportType = "pdf" | "with-answers";

type GeneratedQuizExportInput = {
  exportType: GeneratedQuizExportType;
  noteTitle: string | null | undefined;
  noteSubject: string | null | undefined;
  quiz: QuizItem[];
  exportedAt?: Date;
};

type PdfFont = "regular" | "bold";

type PdfTextStyle = {
  font: PdfFont;
  fontSize: number;
  color?: [number, number, number];
};

type PdfParagraphOptions = {
  x?: number;
  width?: number;
  spacingAfter?: number;
};

const PDF_PAGE_WIDTH = 595.28;
const PDF_PAGE_HEIGHT = 841.89;
const PDF_TOP_MARGIN = 56;
const PDF_SIDE_MARGIN = 52;
const PDF_BOTTOM_MARGIN = 56;
const PDF_FOOTER_Y = 28;
const PDF_CONTENT_WIDTH = PDF_PAGE_WIDTH - (PDF_SIDE_MARGIN * 2);
const PDF_LINE_GAP_FACTOR = 1.38;
const PDF_SECTION_GAP = 16;
const PDF_QUESTION_GAP = 18;
const PDF_MUTED_TEXT: [number, number, number] = [0.33, 0.33, 0.33];
const PDF_LIGHT_STROKE: [number, number, number] = [0.82, 0.82, 0.82];
const PDF_ACCENT_GREEN: [number, number, number] = [0.1, 0.5, 0.22];
const PDF_ACCENT_BLUE: [number, number, number] = [0.14, 0.36, 0.84];
const PDF_DATE_FORMATTER = new Intl.DateTimeFormat("en-US", {
  year: "numeric",
  month: "long",
  day: "numeric",
});
const FILENAME_MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function formatPdfDate(date: Date): string {
  return PDF_DATE_FORMATTER.format(date);
}

function normalizePdfText(value: string | null | undefined): string {
  if (!value) {
    return "";
  }

  return value
    .replaceAll(/[\r\n]+/g, "\n")
    .replaceAll(/[\u2018\u2019]/g, "'")
    .replaceAll(/[\u201C\u201D]/g, "\"")
    .replaceAll(/[\u2013\u2014]/g, "-")
    .replaceAll("\u2026", "...")
    .replaceAll("\u00A0", " ")
    .replaceAll(/[^\x09\x0A\x0D\x20-\x7E]/g, "?")
    .trim();
}

function escapePdfString(value: string): string {
  return value
    .replaceAll("\\", "\\\\")
    .replaceAll("(", String.raw`\(`)
    .replaceAll(")", String.raw`\)`);
}

function formatNumber(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}

function estimateTextWidth(text: string, fontSize: number): number {
  let width = 0;
  for (const character of text) {
    if (character === " ") {
      width += fontSize * 0.28;
    } else if (/[A-Z0-9]/.test(character)) {
      width += fontSize * 0.56;
    } else if (/[.,;:!?'"()[\]-]/.test(character)) {
      width += fontSize * 0.3;
    } else {
      width += fontSize * 0.5;
    }
  }
  return width;
}

function wrapText(text: string, maxWidth: number, fontSize: number): string[] {
  const normalized = normalizePdfText(text);
  if (!normalized) {
    return [];
  }

  const paragraphs = normalized.split("\n");
  const lines: string[] = [];

  for (const paragraph of paragraphs) {
    const trimmedParagraph = paragraph.trim();
    if (!trimmedParagraph) {
      lines.push("");
      continue;
    }

    const words = trimmedParagraph.split(/\s+/);
    let currentLine = "";

    for (const word of words) {
      const candidate = currentLine ? `${currentLine} ${word}` : word;
      if (!currentLine || estimateTextWidth(candidate, fontSize) <= maxWidth) {
        currentLine = candidate;
        continue;
      }

      lines.push(currentLine);
      currentLine = word;
    }

    if (currentLine) {
      lines.push(currentLine);
    }
  }

  return lines;
}

function sanitizeFilenameSegment(value: string): string {
  const normalized = normalizePdfText(value)
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, "-")
    .replaceAll(/^-+|-+$/g, "")
    .slice(0, 64);
  return normalized || "untitled-note";
}

function formatFilenameDate(date: Date): string {
  const month = FILENAME_MONTHS[date.getUTCMonth()] ?? "Jan";
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${month}-${day}`;
}

function drawPdfText({
  text,
  x,
  y,
  style,
}: {
  text: string;
  x: number;
  y: number;
  style: PdfTextStyle;
}): string {
  const color = style.color ?? [0, 0, 0];
  const fontName = style.font === "bold" ? "F2" : "F1";
  return [
    "BT",
    `/${fontName} ${formatNumber(style.fontSize)} Tf`,
    `${formatNumber(color[0])} ${formatNumber(color[1])} ${formatNumber(color[2])} rg`,
    `1 0 0 1 ${formatNumber(x)} ${formatNumber(y)} Tm`,
    `(${escapePdfString(normalizePdfText(text))}) Tj`,
    "ET",
  ].join("\n");
}

function drawPdfLine(
  x1: number,
  y1: number,
  x2: number,
  y2: number,
  color: [number, number, number],
): string {
  return [
    `${formatNumber(color[0])} ${formatNumber(color[1])} ${formatNumber(color[2])} RG`,
    "0.6 w",
    `${formatNumber(x1)} ${formatNumber(y1)} m`,
    `${formatNumber(x2)} ${formatNumber(y2)} l`,
    "S",
  ].join("\n");
}

class SimplePdfDocumentBuilder {
  private pages: string[][] = [[]];
  private currentY = PDF_PAGE_HEIGHT - PDF_TOP_MARGIN;

  addSpacer(size: number) {
    this.currentY -= Math.max(0, size);
  }

  ensureSpace(estimatedHeight: number) {
    if (this.currentY - estimatedHeight < PDF_BOTTOM_MARGIN + 36) {
      this.pages.push([]);
      this.currentY = PDF_PAGE_HEIGHT - PDF_TOP_MARGIN;
    }
  }

  addParagraph(text: string, style: PdfTextStyle, options: PdfParagraphOptions = {}) {
    const x = options.x ?? PDF_SIDE_MARGIN;
    const width = options.width ?? PDF_CONTENT_WIDTH;
    const lines = wrapText(text, width, style.fontSize);
    if (lines.length === 0) {
      return;
    }

    const lineHeight = style.fontSize * PDF_LINE_GAP_FACTOR;
    this.ensureSpace((lines.length * lineHeight) + (options.spacingAfter ?? 0));

    for (const line of lines) {
      if (!line) {
        this.currentY -= lineHeight;
        continue;
      }
      this.pages[this.pages.length - 1].push(drawPdfText({
        text: line,
        x,
        y: this.currentY,
        style,
      }));
      this.currentY -= lineHeight;
    }

    this.currentY -= options.spacingAfter ?? 0;
  }

  drawDivider() {
    this.ensureSpace(18);
    this.pages[this.pages.length - 1].push(drawPdfLine(
      PDF_SIDE_MARGIN,
      this.currentY,
      PDF_PAGE_WIDTH - PDF_SIDE_MARGIN,
      this.currentY,
      PDF_LIGHT_STROKE,
    ));
    this.currentY -= 14;
  }

  buildPages(footerText: string): string[] {
    return this.pages.map((commands, index) => {
      const footerCommands = [
        drawPdfLine(PDF_SIDE_MARGIN, PDF_FOOTER_Y + 12, PDF_PAGE_WIDTH - PDF_SIDE_MARGIN, PDF_FOOTER_Y + 12, PDF_LIGHT_STROKE),
        drawPdfText({
          text: footerText,
          x: PDF_SIDE_MARGIN,
          y: PDF_FOOTER_Y,
          style: { font: "regular", fontSize: 8.5, color: PDF_MUTED_TEXT },
        }),
        drawPdfText({
          text: `Page ${index + 1}`,
          x: PDF_PAGE_WIDTH - PDF_SIDE_MARGIN - 42,
          y: PDF_FOOTER_Y,
          style: { font: "regular", fontSize: 8.5, color: PDF_MUTED_TEXT },
        }),
      ];
      return [...commands, ...footerCommands].join("\n");
    });
  }
}

function buildHeader(
  builder: SimplePdfDocumentBuilder,
  noteTitle: string,
  noteSubject: string | null,
  exportedAt: Date,
) {
  builder.addParagraph("NoteLib", {
    font: "bold",
    fontSize: 10,
    color: PDF_MUTED_TEXT,
  }, { spacingAfter: 10 });

  builder.addParagraph("Quiz Preview Export", {
    font: "bold",
    fontSize: 22,
  }, { spacingAfter: 6 });

  builder.addParagraph(noteTitle, {
    font: "regular",
    fontSize: 12,
    color: PDF_MUTED_TEXT,
  }, { spacingAfter: 3 });

  if (noteSubject) {
    builder.addParagraph(`Subject: ${noteSubject}`, {
      font: "regular",
      fontSize: 11,
      color: PDF_MUTED_TEXT,
    }, { spacingAfter: 3 });
  }

  builder.addParagraph(`Exported: ${formatPdfDate(exportedAt)}`, {
    font: "regular",
    fontSize: 11,
    color: PDF_MUTED_TEXT,
  }, { spacingAfter: PDF_SECTION_GAP });

  builder.drawDivider();
}

function buildQuestionBlock(
  builder: SimplePdfDocumentBuilder,
  question: QuizItem,
  index: number,
  includeAnswers: boolean,
) {
  const correctIndex = resolveQuizCorrectIndex(question);
  const correctChoice = question.choices[correctIndex] ?? "";

  builder.ensureSpace(includeAnswers ? 120 : 84);
  builder.addParagraph(`Question ${index + 1}`, {
    font: "bold",
    fontSize: 13,
    color: PDF_ACCENT_BLUE,
  }, { spacingAfter: 6 });

  builder.addParagraph(question.question, {
    font: "bold",
    fontSize: 12,
  }, { spacingAfter: 8 });

  question.choices.forEach((choice, choiceIndex) => {
    const choiceLabel = String.fromCharCode(65 + choiceIndex);
    const isCorrect = includeAnswers && choiceIndex === correctIndex;
    builder.addParagraph(
      `${choiceLabel}. ${choice}${isCorrect ? " (Correct answer)" : ""}`,
      {
        font: isCorrect ? "bold" : "regular",
        fontSize: 11,
        color: isCorrect ? PDF_ACCENT_GREEN : undefined,
      },
      {
        x: PDF_SIDE_MARGIN + 12,
        width: PDF_CONTENT_WIDTH - 12,
        spacingAfter: 3,
      },
    );
  });

  if (includeAnswers) {
    builder.addParagraph(`Correct answer: ${String.fromCharCode(65 + correctIndex)}. ${correctChoice}`, {
      font: "bold",
      fontSize: 10.5,
      color: PDF_ACCENT_GREEN,
    }, {
      x: PDF_SIDE_MARGIN + 10,
      width: PDF_CONTENT_WIDTH - 10,
      spacingAfter: 4,
    });

    builder.addParagraph("Explanation", {
      font: "bold",
      fontSize: 10.5,
      color: PDF_MUTED_TEXT,
    }, {
      x: PDF_SIDE_MARGIN + 10,
      spacingAfter: 2,
    });

    builder.addParagraph(question.explanation || "No explanation provided.", {
      font: "regular",
      fontSize: 10,
      color: PDF_MUTED_TEXT,
    }, {
      x: PDF_SIDE_MARGIN + 10,
      width: PDF_CONTENT_WIDTH - 10,
      spacingAfter: 8,
    });
  }

  builder.drawDivider();
  builder.addSpacer(PDF_QUESTION_GAP - 8);
}

function buildGeneratedQuizPdf(input: GeneratedQuizExportInput): Uint8Array {
  const exportedAt = input.exportedAt ?? new Date();
  const noteTitle = normalizePdfText(input.noteTitle) || "Untitled note";
  const noteSubject = normalizePdfText(input.noteSubject) || null;
  const includeAnswers = input.exportType === "with-answers";
  const builder = new SimplePdfDocumentBuilder();

  buildHeader(builder, noteTitle, noteSubject, exportedAt);

  builder.addParagraph(
    includeAnswers ? "Questions, answers, and explanations" : "Questions only",
    {
      font: "bold",
      fontSize: 15,
    },
    { spacingAfter: 8 },
  );
  builder.addParagraph(
    includeAnswers
      ? "Ready for teacher review and answer-key export."
      : "Classroom-ready questions without visible answers.",
    {
      font: "regular",
      fontSize: 11,
      color: PDF_MUTED_TEXT,
    },
    { spacingAfter: PDF_SECTION_GAP },
  );

  builder.drawDivider();

  input.quiz.forEach((question, index) => {
    buildQuestionBlock(builder, question, index, includeAnswers);
  });

  const pageContents = builder.buildPages("Generated by NoteLib");
  const objects: string[] = [];
  objects.push(
    "<< /Type /Catalog /Pages 2 0 R >>",
    "",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
  );

  const pageObjectIds: number[] = [];
  for (const pageContent of pageContents) {
    const contentId = objects.length + 1;
    const contentBytes = new TextEncoder().encode(pageContent);
    objects.push(`<< /Length ${contentBytes.length} >>\nstream\n${pageContent}\nendstream`);

    const pageId = objects.length + 1;
    pageObjectIds.push(pageId);
    objects.push(
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${formatNumber(PDF_PAGE_WIDTH)} ${formatNumber(PDF_PAGE_HEIGHT)}] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${contentId} 0 R >>`,
    );
  }

  objects[1] = `<< /Type /Pages /Kids [${pageObjectIds.map((id) => `${id} 0 R`).join(" ")}] /Count ${pageObjectIds.length} >>`;

  let pdf = "%PDF-1.4\n";
  const offsets = [0];

  objects.forEach((objectBody, index) => {
    offsets.push(pdf.length);
    pdf += `${index + 1} 0 obj\n${objectBody}\nendobj\n`;
  });

  const xrefStart = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n`;
  pdf += "0000000000 65535 f \n";
  offsets.slice(1).forEach((offset) => {
    pdf += `${String(offset).padStart(10, "0")} 00000 n \n`;
  });
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF`;

  return new TextEncoder().encode(pdf);
}

export function buildGeneratedQuizExportFilename(
  noteTitle: string | null | undefined,
  exportType: GeneratedQuizExportType,
  exportedAt: Date,
): string {
  const exportLabel = exportType === "with-answers" ? "quiz-with-answers" : "quiz";
  return `${sanitizeFilenameSegment(noteTitle || "untitled-note")}_${exportLabel}_${formatFilenameDate(exportedAt)}.pdf`;
}

export async function exportGeneratedQuizDocument(input: GeneratedQuizExportInput): Promise<{ filename: string }> {
  const exportedAt = input.exportedAt ?? new Date();
  const pdfBytes = buildGeneratedQuizPdf({ ...input, exportedAt });
  const filename = buildGeneratedQuizExportFilename(input.noteTitle, input.exportType, exportedAt);
  const pdfBuffer = pdfBytes.buffer.slice(
    pdfBytes.byteOffset,
    pdfBytes.byteOffset + pdfBytes.byteLength,
  ) as ArrayBuffer;
  const blob = new Blob([pdfBuffer], { type: "application/pdf" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.rel = "noopener";
  link.style.display = "none";
  document.body.appendChild(link);
  link.click();
  link.remove();
  globalThis.setTimeout(() => URL.revokeObjectURL(url), 1000);
  return { filename };
}
