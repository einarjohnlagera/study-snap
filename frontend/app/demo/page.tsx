"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowRight, BookOpen, FileText, Loader2, Sparkles } from "lucide-react";
import { DEMO_GENERATION_DELAY_MS, DEMO_NOTES, DEMO_STUDY_PACK_RESULT } from "@/app/study/demo-content";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import type { QuizItem } from "@/lib/api";
import { resolveQuizCorrectIndex } from "@/lib/quiz";

type DemoStep = "choose" | "input" | "note" | "pack-loading" | "results";

function StepIndicator({ step }: Readonly<{ step: DemoStep }>) {
  const steps: { key: DemoStep; label: string }[] = [
    { key: "choose", label: "Choose" },
    { key: "input", label: "Input" },
    { key: "note", label: "Note" },
    { key: "results", label: "Study Pack" },
  ];
  const activeIndex = step === "pack-loading" ? 3 : steps.findIndex((s) => s.key === step);

  return (
    <ol className="flex items-center gap-2 text-xs">
      {steps.map((s, i) => (
        <li key={s.key} className="flex items-center gap-2">
          <span
            className={`flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-semibold ${
              i <= activeIndex
                ? "bg-blue-600 text-white dark:bg-blue-500"
                : "bg-muted text-foreground/50"
            }`}
          >
            {i + 1}
          </span>
          <span className={i <= activeIndex ? "font-medium text-foreground" : "text-foreground/50"}>
            {s.label}
          </span>
          {i < steps.length - 1 ? <ArrowRight className="h-3 w-3 text-foreground/30" aria-hidden="true" /> : null}
        </li>
      ))}
    </ol>
  );
}

function ChooseStep({ onChoose }: Readonly<{ onChoose: (method: "topic" | "paste") => void }>) {
  return (
    <section className="space-y-4">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold">How do you want to start?</h2>
        <p className="text-sm text-foreground/70">Pick a starting point. NoteLib works either way.</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <button
          type="button"
          onClick={() => onChoose("topic")}
          className="group flex flex-col items-start gap-3 rounded-2xl border border-border bg-background p-5 text-left shadow-sm transition-[border-color,box-shadow,transform] duration-150 hover:-translate-y-0.5 hover:border-blue-400 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
        >
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-600 group-hover:bg-blue-100 dark:bg-blue-950/40 dark:text-blue-400 dark:group-hover:bg-blue-950/60">
            <Sparkles className="h-5 w-5" />
          </span>
          <div className="space-y-1">
            <p className="font-semibold text-foreground">Enter a topic</p>
            <p className="text-sm text-foreground/65">Type a subject and NoteLib generates a note draft instantly.</p>
          </div>
          <span className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 dark:text-blue-400">
            Try it <ArrowRight className="h-3 w-3" />
          </span>
        </button>

        <button
          type="button"
          onClick={() => onChoose("paste")}
          className="group flex flex-col items-start gap-3 rounded-2xl border border-border bg-background p-5 text-left shadow-sm transition-[border-color,box-shadow,transform] duration-150 hover:-translate-y-0.5 hover:border-blue-400 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
        >
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-600 group-hover:bg-blue-100 dark:bg-blue-950/40 dark:text-blue-400 dark:group-hover:bg-blue-950/60">
            <FileText className="h-5 w-5" />
          </span>
          <div className="space-y-1">
            <p className="font-semibold text-foreground">Paste your notes</p>
            <p className="text-sm text-foreground/65">Already have notes? Paste them in and generate a Study Pack.</p>
          </div>
          <span className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 dark:text-blue-400">
            Try it <ArrowRight className="h-3 w-3" />
          </span>
        </button>
      </div>
    </section>
  );
}

function InputStep({
  method,
  topic,
  onTopicChange,
  notes,
  onNotesChange,
  onBack,
  onGenerate,
  loading,
}: Readonly<{
  method: "topic" | "paste";
  topic: string;
  onTopicChange: (v: string) => void;
  notes: string;
  onNotesChange: (v: string) => void;
  onBack: () => void;
  onGenerate: () => void;
  loading: boolean;
}>) {
  return (
    <section className="space-y-4">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold">
          {method === "topic" ? "What topic do you want to study?" : "Paste your notes"}
        </h2>
        <p className="text-sm text-foreground/70">
          {method === "topic"
            ? "NoteLib will generate a note draft from your topic."
            : "NoteLib will turn your notes into a Study Pack."}
        </p>
      </div>

      <Card className="space-y-4 p-4 sm:p-6">
        {method === "topic" ? (
          <div className="space-y-2">
            <label htmlFor="demo-topic" className="text-sm font-medium text-foreground">
              Topic
            </label>
            <input
              id="demo-topic"
              type="text"
              value={topic}
              onChange={(e) => onTopicChange(e.target.value)}
              className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground placeholder:text-foreground/50 focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. Photosynthesis"
            />
          </div>
        ) : (
          <div className="space-y-2">
            <label htmlFor="demo-notes" className="text-sm font-medium text-foreground">
              Your notes
            </label>
            <textarea
              id="demo-notes"
              rows={6}
              value={notes}
              onChange={(e) => onNotesChange(e.target.value)}
              className="w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground placeholder:text-foreground/50 focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Paste your notes here..."
            />
          </div>
        )}

        <div className="flex flex-col gap-3 sm:flex-row">
          <button
            type="button"
            onClick={onGenerate}
            disabled={loading || (method === "topic" ? !topic.trim() : !notes.trim())}
            className={buttonVariants({ className: "w-full sm:w-auto" })}
          >
            {loading ? (
              <span className="inline-flex items-center gap-2">
                <Loader2 className="h-4 w-4 animate-spin" />
                Generating note…
              </span>
            ) : (
              "Generate Note"
            )}
          </button>
          <button
            type="button"
            onClick={onBack}
            disabled={loading}
            className={buttonVariants({ variant: "ghost", className: "w-full sm:w-auto" })}
          >
            Back
          </button>
        </div>
      </Card>
    </section>
  );
}

function NoteStep({
  onGeneratePack,
  loading,
}: Readonly<{ onGeneratePack: () => void; loading: boolean }>) {
  return (
    <section className="space-y-4">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold">Your note is ready</h2>
        <p className="text-sm text-foreground/70">Review the generated note, then turn it into a Study Pack.</p>
      </div>

      <Card className="space-y-3 p-4 sm:p-6">
        <div className="flex items-center gap-2">
          <BookOpen className="h-4 w-4 text-blue-600 dark:text-blue-400" />
          <p className="font-semibold text-foreground">Photosynthesis Basics</p>
        </div>
        <p className="whitespace-pre-line text-sm leading-relaxed text-foreground/80">{DEMO_NOTES}</p>
      </Card>

      <div className="flex flex-col gap-3 sm:flex-row">
        <button
          type="button"
          onClick={onGeneratePack}
          disabled={loading}
          className={buttonVariants({ className: "w-full sm:w-auto" })}
        >
          {loading ? (
            <span className="inline-flex items-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin" />
              Generating Study Pack…
            </span>
          ) : (
            <span className="inline-flex items-center gap-2">
              <Sparkles className="h-4 w-4" />
              Generate Study Pack
            </span>
          )}
        </button>
      </div>
    </section>
  );
}

function DemoQuizQuestion({ item, index }: Readonly<{ item: QuizItem; index: number }>) {
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const revealed = selectedIndex !== null;
  const correctIndex = resolveQuizCorrectIndex(item);

  return (
    <Card className="space-y-3 p-4 sm:p-6">
      <CardTitle className="text-base">
        {index + 1}. {item.question}
      </CardTitle>
      <QuizChoiceList
        questionKey={item.question}
        choices={item.choices}
        correctIndex={correctIndex}
        selectedChoiceIndex={selectedIndex}
        revealAnswer={revealed}
        onSelectChoice={revealed ? undefined : setSelectedIndex}
      />
      {revealed ? (
        <CardDescription className="text-sm">
          <span className="font-medium text-foreground">Explanation:</span> {item.explanation}
        </CardDescription>
      ) : null}
    </Card>
  );
}

function ResultsStep() {
  const pack = DEMO_STUDY_PACK_RESULT;

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">Your Study Pack</h2>

      <Card className="space-y-2 border-blue-500/40 bg-blue-50/70 p-4 dark:bg-blue-950/20 sm:p-5">
        <CardDescription className="text-blue-900 dark:text-blue-200">
          This is a sample Study Pack to show how NoteLib works. Your own notes will generate similar results.
        </CardDescription>
      </Card>

      <Card className="p-4 sm:p-6">
        <CardTitle className="mb-2">Summary</CardTitle>
        <CardDescription>{pack.summary}</CardDescription>
      </Card>

      <Card className="p-4 sm:p-6">
        <CardTitle className="mb-3">Key Concepts</CardTitle>
        <ul className="list-disc space-y-1.5 pl-5 text-sm leading-relaxed text-foreground/80">
          {pack.keyConcepts.map((concept) => (
            <li key={concept}>{concept}</li>
          ))}
        </ul>
      </Card>

      <div className="space-y-3">
        <div className="space-y-1">
          <p className="font-semibold text-foreground">Now test what you remember (like a real exam)</p>
          <p className="text-sm text-foreground/65">
            No instant feedback in real exams — select your answer first, then see if you got it right.
          </p>
        </div>

        <Card className="space-y-4 border-emerald-500/40 p-4 sm:p-6">
          <CardTitle>Practice Quiz</CardTitle>
          <div className="space-y-6">
            {pack.quiz.map((item, index) => (
              <DemoQuizQuestion key={`${item.question}-${index}`} item={item} index={index} />
            ))}
          </div>
        </Card>
      </div>

      <Card className="space-y-4 border-sky-500/30 bg-sky-50/50 p-4 dark:bg-sky-950/20 sm:p-6">
        <div className="space-y-1">
          <CardTitle>Ready to create your own Study Pack?</CardTitle>
          <CardDescription>Start free — no credit card required.</CardDescription>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row">
          <Link href="/signup" className={buttonVariants({ className: "w-full sm:w-auto" })}>
            Start for Free
          </Link>
          <Link href="/signup" className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}>
            Create your own Study Pack
          </Link>
        </div>
      </Card>
    </section>
  );
}

export default function DemoPage() {
  const [step, setStep] = useState<DemoStep>("choose");
  const [method, setMethod] = useState<"topic" | "paste">("topic");
  const [topic, setTopic] = useState("Photosynthesis");
  const [notes, setNotes] = useState(DEMO_NOTES);
  const [loading, setLoading] = useState(false);

  const handleChoose = (m: "topic" | "paste") => {
    setMethod(m);
    setStep("input");
  };

  const handleGenerateNote = () => {
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setStep("note");
    }, DEMO_GENERATION_DELAY_MS);
  };

  const handleGeneratePack = () => {
    setLoading(true);
    setStep("pack-loading");
    setTimeout(() => {
      setLoading(false);
      setStep("results");
    }, DEMO_GENERATION_DELAY_MS);
  };

  return (
    <main className="mx-auto w-full max-w-3xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <section className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-foreground sm:text-3xl">Try NoteLib</h1>
          <span className="inline-flex items-center rounded-full border border-blue-500/40 bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 dark:bg-blue-950/30 dark:text-blue-300">
            Demo
          </span>
        </div>
        <p className="text-base leading-relaxed text-foreground/75">
          Turn any note into a Study Pack in seconds.
        </p>
        <StepIndicator step={step} />
      </section>

      {step === "choose" && <ChooseStep onChoose={handleChoose} />}

      {step === "input" && (
        <InputStep
          method={method}
          topic={topic}
          onTopicChange={setTopic}
          notes={notes}
          onNotesChange={setNotes}
          onBack={() => setStep("choose")}
          onGenerate={handleGenerateNote}
          loading={loading}
        />
      )}

      {step === "note" && (
        <NoteStep onGeneratePack={handleGeneratePack} loading={loading} />
      )}

      {step === "pack-loading" && (
        <section className="space-y-4">
          <Card className="space-y-3 p-4 sm:p-6">
            <div className="flex items-center gap-3">
              <Loader2 className="h-5 w-5 animate-spin text-blue-600 dark:text-blue-400" />
              <CardTitle>Generating your Study Pack…</CardTitle>
            </div>
            <ul className="space-y-1 text-sm text-foreground/70">
              <li>Summarizing notes</li>
              <li>Extracting key concepts</li>
              <li>Generating practice quiz</li>
            </ul>
          </Card>
        </section>
      )}

      {step === "results" && <ResultsStep />}

      <div className="border-t border-border pt-6 text-center">
        <p className="text-sm text-foreground/65">
          No account needed for the demo.{" "}
          <Link href="/signup" className="font-medium text-blue-700 underline-offset-2 hover:underline dark:text-blue-300">
            Sign up free
          </Link>{" "}
          to save notes and generate your own Study Packs.
        </p>
      </div>
    </main>
  );
}
