import type { QuizSessionReviewResponse } from "@/lib/api";
import { computeScore, mapPerformanceLevel } from "@/lib/challenge-quiz-results";
import {
  getDisplayedQuizChoices,
  isQuizSelectionCorrect,
  resolveQuizCorrectIndex,
  toSelectedChoiceIndexRecord,
  toSelectedMultiChoiceIndicesRecord,
  type QuizDisplayChoice,
} from "@/lib/quiz";

type QuizSessionExportFormat = "pdf";

type QuizSessionExportInput = {
  format?: QuizSessionExportFormat;
  exportType?: "full" | "mistakes-only" | "weak-concepts" | "adaptive-practice";
  noteTitle: string | null | undefined;
  noteSubject: string | null | undefined;
  quizTypeLabel: string;
  review: QuizSessionReviewResponse;
  exportedAt?: Date;
};

type ChoiceReviewItem = {
  choice: QuizDisplayChoice;
  isCorrect: boolean;
  isSelected: boolean;
};

type ReviewedQuestionItem = {
  index: number;
  question: string;
  concept: string | null;
  explanation: string | null;
  isCorrect: boolean;
  selectedChoice: QuizDisplayChoice | null;
  correctChoice: QuizDisplayChoice | null;
  choices: ChoiceReviewItem[];
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
const PDF_ACCENT_GREEN: [number, number, number] = [0.1, 0.5, 0.22];
const PDF_ACCENT_RED: [number, number, number] = [0.7, 0.16, 0.16];
const PDF_ACCENT_BLUE: [number, number, number] = [0.14, 0.36, 0.84];
const PDF_MUTED_TEXT: [number, number, number] = [0.33, 0.33, 0.33];
const PDF_LIGHT_STROKE: [number, number, number] = [0.82, 0.82, 0.82];
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
    .replaceAll('\u2026', "...")
    .replaceAll('\u00A0', " ")
    .replaceAll(/[^\x09\x0A\x0D\x20-\x7E]/g, "?")
    .trim();
}

function escapePdfString(value: string): string {
  return value
    .replaceAll('\\', "\\\\")
    .replaceAll('(', String.raw`\(`)
    .replaceAll(')', String.raw`\)`);
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
  const month = FILENAME_MONTHS[date.getUTCMonth()];
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${month}-${day}`;
}

function buildQuestionReviewItems(review: QuizSessionReviewResponse): ReviewedQuestionItem[] {
  const selectedChoices = toSelectedChoiceIndexRecord(review.selectedChoices, review.quiz);
  const selectedMultiChoices = toSelectedMultiChoiceIndicesRecord(review.selectedMultiChoices, review.quiz);

  return review.quiz.map((item, index) => {
    const displayedChoices = getDisplayedQuizChoices(item);
    const selectedChoiceIndex = selectedChoices[index] ?? null;
    const correctIndex = resolveQuizCorrectIndex(item);
    const selectedChoice = displayedChoices.find((choice) => choice.canonicalIndex === selectedChoiceIndex) ?? null;
    const correctChoice = displayedChoices.find((choice) => choice.canonicalIndex === correctIndex) ?? null;

    const selectedForScoring = item.questionFormat === "MULTI_SELECT"
      ? selectedMultiChoices[index]
      : selectedChoiceIndex;

    return {
      index,
      question: normalizePdfText(item.question) || `Question ${index + 1}`,
      concept: normalizePdfText(item.concept) || null,
      explanation: normalizePdfText(item.explanation) || null,
      isCorrect: isQuizSelectionCorrect(item, selectedForScoring),
      selectedChoice,
      correctChoice,
      choices: displayedChoices.map((choice) => ({
        choice,
        isCorrect: choice.canonicalIndex === correctIndex,
        isSelected: item.questionFormat === "MULTI_SELECT"
          ? (selectedMultiChoices[index] ?? []).includes(choice.canonicalIndex)
          : choice.canonicalIndex === selectedChoiceIndex,
      })),
    };
  });
}

// ─── Shared PDF layout helpers ───────────────────────────────────────────────

function buildSharedPdfHeader(
  builder: SimplePdfDocumentBuilder,
  noteTitle: string,
  noteSubject: string | null,
  quizTypeLabel: string,
  review: QuizSessionReviewResponse,
): void {
  builder.addParagraph("NoteLib", {
    font: "bold",
    fontSize: 10,
    color: PDF_MUTED_TEXT,
  }, { spacingAfter: 10 });

  builder.addParagraph(noteTitle, {
    font: "bold",
    fontSize: 22,
  }, { spacingAfter: 6 });

  if (noteSubject) {
    builder.addParagraph(noteSubject, {
      font: "regular",
      fontSize: 11,
      color: PDF_MUTED_TEXT,
    }, { spacingAfter: 3 });
  }

  builder.addParagraph(`Quiz type: ${quizTypeLabel}`, {
    font: "regular",
    fontSize: 11,
    color: PDF_MUTED_TEXT,
  }, { spacingAfter: 2 });

  const rawDate = review.completedAt ?? review.createdAt;
  builder.addParagraph(
    `Date taken: ${formatPdfDate(new Date(rawDate))}`,
    { font: "regular", fontSize: 11, color: PDF_MUTED_TEXT },
    { spacingAfter: PDF_SECTION_GAP },
  );

  builder.drawDivider();
}

function buildSharedQuestionBlock(
  builder: SimplePdfDocumentBuilder,
  item: ReviewedQuestionItem,
): void {
  builder.ensureSpace(84);

  builder.addParagraph(`Question ${item.index + 1}`, {
    font: "bold",
    fontSize: 13,
  }, { spacingAfter: 6 });

  builder.addParagraph(item.question, {
    font: "bold",
    fontSize: 12,
  }, { spacingAfter: 6 });

  for (const choiceItem of item.choices) {
    const annotations: string[] = [];
    if (choiceItem.isSelected) {
      annotations.push("Your answer");
    }
    if (choiceItem.isCorrect) {
      annotations.push("Correct answer");
    }
    const suffix = annotations.length > 0 ? ` (${annotations.join(", ")})` : "";
    builder.addParagraph(
      `${choiceItem.choice.label}. ${choiceItem.choice.text}${suffix}`,
      {
        font: choiceItem.isCorrect || choiceItem.isSelected ? "bold" : "regular",
        fontSize: 11,
        color: choiceItem.isCorrect
          ? PDF_ACCENT_GREEN
          : choiceItem.isSelected && !choiceItem.isCorrect
            ? PDF_ACCENT_RED
            : [0, 0, 0],
      },
      {
        x: PDF_SIDE_MARGIN + 14,
        width: PDF_CONTENT_WIDTH - 14,
        spacingAfter: 3,
      },
    );
  }

  builder.addParagraph(
    item.selectedChoice
      ? `Your answer: ${item.selectedChoice.label} (${item.isCorrect ? "Correct" : "Incorrect"})`
      : "Your answer: Not answered",
    {
      font: "regular",
      fontSize: 10.5,
      color: item.isCorrect ? PDF_ACCENT_GREEN : PDF_ACCENT_RED,
    },
    {
      x: PDF_SIDE_MARGIN + 10,
      width: PDF_CONTENT_WIDTH - 10,
      spacingAfter: 2,
    },
  );

  builder.addParagraph(
    item.correctChoice
      ? `Correct answer: ${item.correctChoice.label}`
      : "Correct answer: Unavailable",
    {
      font: "regular",
      fontSize: 10.5,
      color: PDF_ACCENT_GREEN,
    },
    {
      x: PDF_SIDE_MARGIN + 10,
      width: PDF_CONTENT_WIDTH - 10,
      spacingAfter: 2,
    },
  );

  if (item.concept) {
    builder.addParagraph(`Concept: ${item.concept}`, {
      font: "regular",
      fontSize: 10.5,
      color: PDF_MUTED_TEXT,
    }, {
      x: PDF_SIDE_MARGIN + 10,
      width: PDF_CONTENT_WIDTH - 10,
      spacingAfter: 4,
    });
  }

  if (item.explanation) {
    builder.addParagraph("Explanation", {
      font: "bold",
      fontSize: 10.5,
      color: PDF_MUTED_TEXT,
    }, {
      x: PDF_SIDE_MARGIN + 10,
      spacingAfter: 2,
    });
    builder.addParagraph(item.explanation, {
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

// ─── Export command builders ──────────────────────────────────────────────────

function buildPdfCommands(
  review: QuizSessionReviewResponse,
  noteTitle: string,
  noteSubject: string | null,
  quizTypeLabel: string,
  exportedAt: Date,
): string[] {
  const selectedChoices = toSelectedChoiceIndexRecord(review.selectedChoices, review.quiz);
  const selectedMultiChoices = toSelectedMultiChoiceIndicesRecord(review.selectedMultiChoices, review.quiz);
  const score = computeScore(review.quiz, selectedChoices, selectedMultiChoices);
  const performanceLevel = mapPerformanceLevel(score.scorePercentage);
  const questionItems = buildQuestionReviewItems(review);
  const builder = new SimplePdfDocumentBuilder();

  buildSharedPdfHeader(builder, noteTitle, noteSubject, quizTypeLabel, review);

  builder.addParagraph("Session Summary", {
    font: "bold",
    fontSize: 15,
  }, { spacingAfter: 8 });

  builder.addParagraph(`Score: ${score.correctAnswers}/${score.totalQuestions}`, {
    font: "regular",
    fontSize: 11,
  }, { spacingAfter: 2 });
  builder.addParagraph(`Percentage: ${score.scorePercentage}%`, {
    font: "regular",
    fontSize: 11,
  }, { spacingAfter: 2 });
  builder.addParagraph(`Performance: ${performanceLevel}`, {
    font: "regular",
    fontSize: 11,
  }, { spacingAfter: 2 });
  builder.addParagraph(
    `Weak concepts: ${review.weakConcepts.length > 0 ? review.weakConcepts.join(", ") : "None identified"}`,
    { font: "regular", fontSize: 11 },
    { spacingAfter: PDF_SECTION_GAP },
  );

  builder.drawDivider();
  builder.addParagraph("Questions", {
    font: "bold",
    fontSize: 15,
  }, { spacingAfter: 10 });

  if (questionItems.length === 0) {
    builder.addParagraph("Detailed per-question review is unavailable for this session.", {
      font: "regular",
      fontSize: 11,
      color: PDF_MUTED_TEXT,
    }, { spacingAfter: PDF_SECTION_GAP });
  }

  for (const item of questionItems) {
    buildSharedQuestionBlock(builder, item);
  }

  return builder.buildPages({ footerText: "Generated by NoteLib" });
}

function buildMistakesPdfCommands(
  review: QuizSessionReviewResponse,
  noteTitle: string,
  noteSubject: string | null,
  quizTypeLabel: string,
  exportedAt: Date,
): string[] {
  const selectedChoices = toSelectedChoiceIndexRecord(review.selectedChoices, review.quiz);
  const selectedMultiChoices = toSelectedMultiChoiceIndicesRecord(review.selectedMultiChoices, review.quiz);
  const score = computeScore(review.quiz, selectedChoices, selectedMultiChoices);
  const questionItems = buildQuestionReviewItems(review);
  const mistakeItems = questionItems.filter((item) => !item.isCorrect);
  const mistakeWeakConcepts = [
    ...new Set(mistakeItems.map((item) => item.concept).filter((c): c is string => c !== null)),
  ];
  const builder = new SimplePdfDocumentBuilder();

  buildSharedPdfHeader(builder, noteTitle, noteSubject, quizTypeLabel, review);

  builder.addParagraph("Mistakes Review", {
    font: "bold",
    fontSize: 15,
  }, { spacingAfter: 8 });

  builder.addParagraph(`Mistakes: ${mistakeItems.length}/${score.totalQuestions} incorrect`, {
    font: "regular",
    fontSize: 11,
  }, { spacingAfter: 2 });

  builder.addParagraph(`Accuracy: ${score.scorePercentage}%`, {
    font: "regular",
    fontSize: 11,
  }, { spacingAfter: 2 });

  builder.addParagraph(
    `Weak concepts: ${mistakeWeakConcepts.length > 0 ? mistakeWeakConcepts.join(", ") : "None identified"}`,
    { font: "regular", fontSize: 11 },
    { spacingAfter: PDF_SECTION_GAP },
  );

  builder.drawDivider();

  if (mistakeItems.length === 0) {
    builder.addParagraph("Perfect Score!", {
      font: "bold",
      fontSize: 15,
    }, { spacingAfter: 8 });
    builder.addParagraph("You answered all questions correctly.", {
      font: "regular",
      fontSize: 11,
      color: PDF_MUTED_TEXT,
    }, { spacingAfter: PDF_SECTION_GAP });
    return builder.buildPages({ footerText: "Generated by NoteLib" });
  }

  builder.addParagraph("Incorrect Answers", {
    font: "bold",
    fontSize: 15,
  }, { spacingAfter: 10 });

  for (const item of mistakeItems) {
    buildSharedQuestionBlock(builder, item);
  }

  return builder.buildPages({ footerText: "Generated by NoteLib" });
}

function buildWeakConceptsPdfCommands(
  review: QuizSessionReviewResponse,
  noteTitle: string,
  noteSubject: string | null,
  quizTypeLabel: string,
  exportedAt: Date,
): string[] {
  const questionItems = buildQuestionReviewItems(review);
  const weakConceptsSet = new Set(review.weakConcepts);
  const weakConceptItems = questionItems.filter(
    (item) => item.concept !== null && weakConceptsSet.has(item.concept),
  );
  const builder = new SimplePdfDocumentBuilder();

  buildSharedPdfHeader(builder, noteTitle, noteSubject, quizTypeLabel, review);

  builder.addParagraph("Weak Concepts Review", {
    font: "bold",
    fontSize: 15,
  }, { spacingAfter: 8 });

  if (review.weakConcepts.length === 0) {
    builder.addParagraph("No weak concepts identified.", {
      font: "bold",
      fontSize: 13,
    }, { spacingAfter: 6 });
    builder.addParagraph("You performed well across all concepts in this session.", {
      font: "regular",
      fontSize: 11,
      color: PDF_MUTED_TEXT,
    }, { spacingAfter: PDF_SECTION_GAP });
    return builder.buildPages({ footerText: "Generated by NoteLib" });
  }

  builder.addParagraph(
    `Weak concepts: ${review.weakConcepts.join(", ")}`,
    { font: "regular", fontSize: 11 },
    { spacingAfter: 2 },
  );
  builder.addParagraph(
    `Questions covering weak concepts: ${weakConceptItems.length}`,
    { font: "regular", fontSize: 11 },
    { spacingAfter: PDF_SECTION_GAP },
  );

  builder.drawDivider();

  if (weakConceptItems.length === 0) {
    builder.addParagraph("No matching questions found for the identified weak concepts.", {
      font: "regular",
      fontSize: 11,
      color: PDF_MUTED_TEXT,
    }, { spacingAfter: PDF_SECTION_GAP });
    return builder.buildPages({ footerText: "Generated by NoteLib" });
  }

  // Group questions by weak concept for clarity
  for (const concept of review.weakConcepts) {
    const conceptQuestions = weakConceptItems.filter((item) => item.concept === concept);

    builder.ensureSpace(40);
    builder.addParagraph(concept, {
      font: "bold",
      fontSize: 13,
      color: PDF_ACCENT_BLUE,
    }, { spacingAfter: 6 });

    if (conceptQuestions.length === 0) {
      builder.addParagraph("No questions found for this concept.", {
        font: "regular",
        fontSize: 11,
        color: PDF_MUTED_TEXT,
      }, { spacingAfter: 12 });
      continue;
    }

    for (const item of conceptQuestions) {
      buildSharedQuestionBlock(builder, item);
    }
  }

  return builder.buildPages({ footerText: "Generated by NoteLib" });
}

function buildAdaptivePracticePdfCommands(
  review: QuizSessionReviewResponse,
  noteTitle: string,
  noteSubject: string | null,
  quizTypeLabel: string,
  exportedAt: Date,
): string[] {
  const selectedChoices = toSelectedChoiceIndexRecord(review.selectedChoices, review.quiz);
  const selectedMultiChoices = toSelectedMultiChoiceIndicesRecord(review.selectedMultiChoices, review.quiz);
  const score = computeScore(review.quiz, selectedChoices, selectedMultiChoices);
  const performanceLevel = mapPerformanceLevel(score.scorePercentage);
  const questionItems = buildQuestionReviewItems(review);
  const builder = new SimplePdfDocumentBuilder();

  buildSharedPdfHeader(builder, noteTitle, noteSubject, quizTypeLabel, review);

  builder.addParagraph("Adaptive Practice Review", {
    font: "bold",
    fontSize: 15,
  }, { spacingAfter: 8 });

  builder.addParagraph(`Score: ${score.correctAnswers}/${score.totalQuestions}`, {
    font: "regular",
    fontSize: 11,
  }, { spacingAfter: 2 });
  builder.addParagraph(`Percentage: ${score.scorePercentage}%`, {
    font: "regular",
    fontSize: 11,
  }, { spacingAfter: 2 });
  builder.addParagraph(`Performance: ${performanceLevel}`, {
    font: "regular",
    fontSize: 11,
  }, { spacingAfter: 2 });
  builder.addParagraph(
    `Weak concepts: ${review.weakConcepts.length > 0 ? review.weakConcepts.join(", ") : "None identified"}`,
    { font: "regular", fontSize: 11 },
    { spacingAfter: PDF_SECTION_GAP },
  );

  builder.drawDivider();
  builder.addParagraph("Questions", {
    font: "bold",
    fontSize: 15,
  }, { spacingAfter: 10 });

  if (questionItems.length === 0) {
    builder.addParagraph("Detailed per-question review is unavailable for this session.", {
      font: "regular",
      fontSize: 11,
      color: PDF_MUTED_TEXT,
    }, { spacingAfter: PDF_SECTION_GAP });
  }

  for (const item of questionItems) {
    buildSharedQuestionBlock(builder, item);
  }

  return builder.buildPages({ footerText: "Generated by NoteLib" });
}

// ─── PDF document builder ─────────────────────────────────────────────────────

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
      this.pages[this.pages.length - 1].push(
        drawPdfText({
          text: line,
          x,
          y: this.currentY,
          style,
        }),
      );
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

  buildPages({ footerText }: { footerText: string }): string[] {
    return this.pages.map((commands, index) => {
      const footerCommands = [
        drawPdfLine(PDF_SIDE_MARGIN, PDF_FOOTER_Y + 12, PDF_PAGE_WIDTH - PDF_SIDE_MARGIN, PDF_FOOTER_Y + 12, PDF_LIGHT_STROKE),
        drawPdfText({
          text: footerText,
          x: PDF_SIDE_MARGIN,
          y: PDF_FOOTER_Y,
          style: {
            font: "regular",
            fontSize: 8.5,
            color: PDF_MUTED_TEXT,
          },
        }),
        drawPdfText({
          text: `Page ${index + 1}`,
          x: PDF_PAGE_WIDTH - PDF_SIDE_MARGIN - 42,
          y: PDF_FOOTER_Y,
          style: {
            font: "regular",
            fontSize: 8.5,
            color: PDF_MUTED_TEXT,
          },
        }),
      ];
      return [...commands, ...footerCommands].join("\n");
    });
  }
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

// ─── Public API ───────────────────────────────────────────────────────────────

export function buildQuizSessionExportFilename(noteTitle: string | null | undefined, exportedAt: Date): string {
  return `${sanitizeFilenameSegment(noteTitle || "untitled-note")}_full-quiz_${formatFilenameDate(exportedAt)}.pdf`;
}

export function buildMistakesOnlyExportFilename(noteTitle: string | null | undefined, exportedAt: Date): string {
  return `${sanitizeFilenameSegment(noteTitle || "untitled-note")}_mistakes_${formatFilenameDate(exportedAt)}.pdf`;
}

export function buildWeakConceptsExportFilename(noteTitle: string | null | undefined, exportedAt: Date): string {
  return `${sanitizeFilenameSegment(noteTitle || "untitled-note")}_weak-concepts_${formatFilenameDate(exportedAt)}.pdf`;
}

export function buildAdaptivePracticeExportFilename(noteTitle: string | null | undefined, exportedAt: Date): string {
  return `${sanitizeFilenameSegment(noteTitle || "untitled-note")}_adaptive-practice_${formatFilenameDate(exportedAt)}.pdf`;
}

/**
 * Returns false when the requested export type would produce no meaningful
 * content, allowing the caller to show a user-friendly message and skip PDF
 * generation.
 */
export function hasExportableContent(
  exportType: "full" | "mistakes-only" | "weak-concepts" | "adaptive-practice",
  review: QuizSessionReviewResponse,
): boolean {
  if (exportType === "mistakes-only") {
    const selectedChoices = toSelectedChoiceIndexRecord(review.selectedChoices, review.quiz);
    const selectedMultiChoices = toSelectedMultiChoiceIndicesRecord(review.selectedMultiChoices, review.quiz);
    return review.quiz.some(
      (item, index) => !isQuizSelectionCorrect(
        item,
        item.questionFormat === "MULTI_SELECT" ? selectedMultiChoices[index] : selectedChoices[index] ?? null,
      ),
    );
  }
  if (exportType === "weak-concepts") {
    return review.weakConcepts.length > 0;
  }
  // "full" and "adaptive-practice" always have content
  return true;
}

export function buildQuizSessionPdf(input: QuizSessionExportInput): Uint8Array {
  const exportedAt = input.exportedAt ?? new Date();
  const noteTitle = normalizePdfText(input.noteTitle) || "Untitled note";
  const noteSubject = normalizePdfText(input.noteSubject) || null;

  const pageContents = input.exportType === "mistakes-only"
    ? buildMistakesPdfCommands(input.review, noteTitle, noteSubject, input.quizTypeLabel, exportedAt)
    : input.exportType === "weak-concepts"
      ? buildWeakConceptsPdfCommands(input.review, noteTitle, noteSubject, input.quizTypeLabel, exportedAt)
      : input.exportType === "adaptive-practice"
        ? buildAdaptivePracticePdfCommands(input.review, noteTitle, noteSubject, input.quizTypeLabel, exportedAt)
        : buildPdfCommands(input.review, noteTitle, noteSubject, input.quizTypeLabel, exportedAt);

  const objects: string[] = [];
  objects.push("<< /Type /Catalog /Pages 2 0 R >>",
      "",
      "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
      "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");

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

export async function exportQuizSessionReviewDocument(input: QuizSessionExportInput): Promise<{ filename: string }> {
  const format = input.format ?? "pdf";
  if (format !== "pdf") {
    throw new Error("Unsupported export format.");
  }

  const exportedAt = input.exportedAt ?? new Date();
  const pdfBytes = buildQuizSessionPdf({
    ...input,
    exportedAt,
  });
  const filename = input.exportType === "mistakes-only"
    ? buildMistakesOnlyExportFilename(input.noteTitle, exportedAt)
    : input.exportType === "weak-concepts"
      ? buildWeakConceptsExportFilename(input.noteTitle, exportedAt)
      : input.exportType === "adaptive-practice"
        ? buildAdaptivePracticeExportFilename(input.noteTitle, exportedAt)
        : buildQuizSessionExportFilename(input.noteTitle, exportedAt);
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
