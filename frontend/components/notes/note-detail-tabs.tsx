"use client";

import { Brain, FileText, ListChecks, Lock, NotebookText } from "lucide-react";
import { type NoteDetailTab } from "@/lib/note-entry";
import { cn } from "@/lib/utils";

const TABS: Array<{
  label: string;
  tab: NoteDetailTab;
  icon: typeof FileText;
}> = [
  { label: "Summary", tab: "summary", icon: FileText },
  { label: "Key Concepts", tab: "key-concepts", icon: ListChecks },
  { label: "Quiz", tab: "quiz", icon: Brain },
  { label: "Full Notes", tab: "full-notes", icon: NotebookText },
];

type NoteDetailTabsProps = Readonly<{
  activeTab: NoteDetailTab;
  onChange: (tab: NoteDetailTab) => void;
  quizLocked?: boolean;
  ariaLabel?: string;
  className?: string;
}>;

export function NoteDetailTabs({
  activeTab,
  onChange,
  quizLocked = false,
  ariaLabel = "Note detail views",
  className,
}: NoteDetailTabsProps) {
  return (
    <div className={cn("overflow-x-auto border-b border-border", className)}>
      <div className="flex min-w-max items-center gap-5 sm:gap-6" role="tablist" aria-label={ariaLabel}>
        {TABS.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.tab;
          const isLocked = item.tab === "quiz" && quizLocked;

          return (
            <button
              key={item.tab}
              type="button"
              role="tab"
              aria-label={isLocked ? `${item.label}, locked` : item.label}
              aria-selected={isActive}
              onClick={() => onChange(item.tab)}
              className={cn(
                "inline-flex min-h-10 items-center justify-center gap-2 border-b-2 border-transparent px-1 pb-3 pt-1 text-sm font-medium transition-colors",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 focus-visible:ring-offset-background",
                isActive
                  ? "border-blue-600 text-foreground dark:border-blue-400"
                  : "text-foreground/55 hover:text-foreground/80",
              )}
            >
              <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{item.label}</span>
              {isLocked ? <Lock className="h-3.5 w-3.5 shrink-0" aria-hidden="true" /> : null}
            </button>
          );
        })}
      </div>
    </div>
  );
}
