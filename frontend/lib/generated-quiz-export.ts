import type { QuizItem } from "@/lib/api";
import { resolveQuizCorrectIndex } from "@/lib/quiz";

export type GeneratedQuizExportType =
  | "questions-only"
  | "questions-answers"
  | "answer-key";

type GeneratedQuizExportInput = {
  exportType: GeneratedQuizExportType;
  noteTitle: string | null | undefined;
  noteSubject: string | null | undefined;
  quiz: QuizItem[];
  exportedAt?: Date;
};

const FILENAME_MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function sanitizeFilenamePart(value: string | null | undefined): string {
  const normalized = (value ?? "untitled-note")
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, "-")
    .replaceAll(/^-+|-+$/g, "");
  return normalized || "untitled-note";
}

function buildDateStamp(date: Date): string {
  const month = FILENAME_MONTHS[date.getUTCMonth()] ?? "Jan";
  return `${month}-${String(date.getUTCDate()).padStart(2, "0")}-${date.getUTCFullYear()}`;
}

function normalizeText(value: string | null | undefined): string {
  return value?.trim() || "";
}

function formatCorrectAnswer(question: QuizItem): string {
  const correctIndex = resolveQuizCorrectIndex(question);
  const correctChoice = question.choices[correctIndex] ?? "";
  const label = String.fromCharCode(65 + correctIndex);
  return `${label}. ${correctChoice}`.trim();
}

function buildQuestionsOnlyContent(input: GeneratedQuizExportInput, exportedAt: Date): string {
  const lines: string[] = [
    "NoteLib Quiz Export",
    `Title: ${normalizeText(input.noteTitle) || "Untitled note"}`,
    `Subject: ${normalizeText(input.noteSubject) || "General"}`,
    `Exported: ${exportedAt.toISOString()}`,
    "",
  ];

  input.quiz.forEach((question, index) => {
    lines.push(`${index + 1}. ${normalizeText(question.question)}`);
    question.choices.forEach((choice, choiceIndex) => {
      lines.push(`   ${String.fromCharCode(65 + choiceIndex)}. ${normalizeText(choice)}`);
    });
    lines.push("");
  });

  return lines.join("\n").trimEnd();
}

function buildQuestionsWithAnswersContent(input: GeneratedQuizExportInput, exportedAt: Date): string {
  const lines: string[] = [
    "NoteLib Quiz Export",
    `Title: ${normalizeText(input.noteTitle) || "Untitled note"}`,
    `Subject: ${normalizeText(input.noteSubject) || "General"}`,
    `Exported: ${exportedAt.toISOString()}`,
    "",
  ];

  input.quiz.forEach((question, index) => {
    lines.push(`${index + 1}. ${normalizeText(question.question)}`);
    question.choices.forEach((choice, choiceIndex) => {
      const choiceLabel = String.fromCharCode(65 + choiceIndex);
      const isCorrect = choiceIndex === resolveQuizCorrectIndex(question);
      lines.push(`   ${choiceLabel}. ${normalizeText(choice)}${isCorrect ? "  [Correct]" : ""}`);
    });
    lines.push(`   Explanation: ${normalizeText(question.explanation) || "No explanation provided."}`);
    lines.push("");
  });

  return lines.join("\n").trimEnd();
}

function buildAnswerKeyContent(input: GeneratedQuizExportInput, exportedAt: Date): string {
  const lines: string[] = [
    "NoteLib Answer Key",
    `Title: ${normalizeText(input.noteTitle) || "Untitled note"}`,
    `Subject: ${normalizeText(input.noteSubject) || "General"}`,
    `Exported: ${exportedAt.toISOString()}`,
    "",
  ];

  input.quiz.forEach((question, index) => {
    lines.push(`${index + 1}. ${formatCorrectAnswer(question)}`);
    lines.push(`   Explanation: ${normalizeText(question.explanation) || "No explanation provided."}`);
    lines.push("");
  });

  return lines.join("\n").trimEnd();
}

export function buildGeneratedQuizExportFilename(
  noteTitle: string | null | undefined,
  exportType: GeneratedQuizExportType,
  exportedAt: Date,
): string {
  const exportLabel = exportType === "questions-only"
    ? "questions"
    : exportType === "questions-answers"
      ? "questions-answers"
      : "answer-key";
  return `notelib-${exportLabel}-${sanitizeFilenamePart(noteTitle)}-${buildDateStamp(exportedAt)}.txt`;
}

export function buildGeneratedQuizExportContent(input: GeneratedQuizExportInput): string {
  const exportedAt = input.exportedAt ?? new Date();
  if (input.exportType === "questions-only") {
    return buildQuestionsOnlyContent(input, exportedAt);
  }
  if (input.exportType === "answer-key") {
    return buildAnswerKeyContent(input, exportedAt);
  }
  return buildQuestionsWithAnswersContent(input, exportedAt);
}

export async function exportGeneratedQuizDocument(input: GeneratedQuizExportInput): Promise<{ filename: string }> {
  const exportedAt = input.exportedAt ?? new Date();
  const content = buildGeneratedQuizExportContent({ ...input, exportedAt });
  const filename = buildGeneratedQuizExportFilename(input.noteTitle, input.exportType, exportedAt);
  const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
  return { filename };
}
