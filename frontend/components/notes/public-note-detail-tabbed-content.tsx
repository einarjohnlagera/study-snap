"use client";

import { useState } from "react";
import { Card } from "@/components/ui/card";
import { NoteDetailTabs } from "@/components/notes/note-detail-tabs";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import type { NoteStudyPackStatus, QuizItem } from "@/lib/api";
import { getDisplayedQuizChoices, resolveQuizCorrectIndex } from "@/lib/quiz";
import type { NoteDetailTab } from "@/lib/note-entry";

type PublicNoteDetailTabbedContentProps = Readonly<{
  studyPackStatus: NoteStudyPackStatus;
  summary: string | null;
  keyConcepts: string[];
  quiz: QuizItem[];
  content: string;
  quizMode?: "preview" | "full";
}>;

export function PublicNoteDetailTabbedContent({
  studyPackStatus,
  summary,
  keyConcepts,
  quiz,
  content,
  quizMode = "preview",
}: PublicNoteDetailTabbedContentProps) {
  const [activeTab, setActiveTab] = useState<NoteDetailTab>("summary");

  const isDraft = studyPackStatus !== "STUDY_PACK_READY";
  const fullContent = content.trim().length > 0 ? content : "No content yet.";
  const quizPreview = quiz.slice(0, 3);

  return (
    <div className="space-y-6">
      <Card className="space-y-3 p-4 sm:p-6">
        <NoteDetailTabs
          activeTab={activeTab}
          onChange={setActiveTab}
          ariaLabel="Public note detail views"
        />
        <p className="text-xs text-foreground/60">
          Review the generated study tools or inspect the full original note before deciding to copy it.
        </p>
      </Card>

      {activeTab === "summary" ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
          <p className="text-sm leading-relaxed text-foreground/80">
            {isDraft ? "This public note does not have a generated summary yet." : (summary ?? "No summary available yet.")}
          </p>
        </Card>
      ) : null}

      {activeTab === "key-concepts" ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <h2 className="text-lg font-semibold sm:text-xl">Key Concepts</h2>
          {isDraft || keyConcepts.length === 0 ? (
            <p className="text-sm text-foreground/75">No generated key concepts yet.</p>
          ) : (
            <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
              {keyConcepts.map((concept) => (
                <li key={concept}>{concept}</li>
              ))}
            </ul>
          )}
        </Card>
      ) : null}

      {activeTab === "quiz" ? (
        quizMode === "full" ? (
          isDraft ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-lg font-semibold sm:text-xl">Practice Quiz</h2>
              <p className="text-sm text-foreground/75">No generated quiz yet.</p>
            </Card>
          ) : (
            <PracticeQuizCard quiz={quiz} />
          )
        ) : (
          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Practice Questions Preview</h2>
            <p className="text-sm text-foreground/70">
              Copy this note or open your own version to try the interactive quiz, see answers, and track your score.
            </p>
            {isDraft || quizPreview.length === 0 ? (
              <p className="text-sm text-foreground/75">No quiz preview available yet.</p>
            ) : (
              <ol className="space-y-4">
                {quizPreview.map((item, index) => (
                  <li key={`${item.question}-${index}`} className="space-y-2 text-sm text-foreground/85">
                    <p className="font-medium text-foreground">
                      {index + 1}. {item.question}
                    </p>
                    <ul className="space-y-1">
                      {getDisplayedQuizChoices({
                        ...item,
                        correctIndex: resolveQuizCorrectIndex(item),
                      }).map((choice) => (
                        <li key={`${item.question}-${choice.label}-${choice.text}`}>
                          <span className="mr-2 font-semibold text-foreground">{choice.label}.</span>
                          <span>{choice.text}</span>
                        </li>
                      ))}
                    </ul>
                  </li>
                ))}
              </ol>
            )}
            <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-3 text-sm text-foreground/70">
              Preview only. The full quiz experience, answer reveal, and score tracking are available from your own
              note in the app workspace.
            </div>
          </Card>
        )
      ) : null}

      {activeTab === "full-notes" ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <h2 className="text-lg font-semibold sm:text-xl">Full Notes</h2>
          <div className="rounded-2xl border border-border bg-background px-4 py-4">
            <p className="whitespace-pre-wrap text-sm leading-7 text-foreground/85">
              {fullContent}
            </p>
          </div>
        </Card>
      ) : null}
    </div>
  );
}
