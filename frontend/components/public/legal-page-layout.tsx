import type { ReactNode } from "react";
import { PublicFooter } from "@/components/public/public-footer";

type LegalSection = {
  title: string;
  content: ReactNode;
};

type LegalPageLayoutProps = {
  title: string;
  lastUpdated: string;
  intro: ReactNode;
  sections: LegalSection[];
};

export function LegalPageLayout({
  title,
  lastUpdated,
  intro,
  sections,
}: LegalPageLayoutProps) {
  return (
    <main className="mx-auto w-full max-w-4xl space-y-8 px-4 py-8 sm:px-6 sm:py-12">
      <header className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Legal</p>
        <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
        <p className="text-sm text-foreground/65">Last updated: {lastUpdated}</p>
        <div className="max-w-3xl text-sm leading-relaxed text-foreground/80 sm:text-base">{intro}</div>
      </header>

      <div className="space-y-6">
        {sections.map((section) => (
          <section key={section.title} className="space-y-2 rounded-xl border border-border bg-muted/20 p-5">
            <h2 className="text-xl font-semibold text-foreground">{section.title}</h2>
            <div className="space-y-3 text-sm leading-relaxed text-foreground/80 sm:text-base">{section.content}</div>
          </section>
        ))}
      </div>
      <PublicFooter className="mt-8" />
    </main>
  );
}
