import Link from "next/link";

type PublicFooterProps = {
  className?: string;
};

export function PublicFooter({ className }: PublicFooterProps) {
  return (
    <footer className={`border-t border-border/70 pt-6 text-sm text-foreground/70 ${className ?? ""}`}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p>NoteLib helps students turn notes into study packs, summaries, and quizzes.</p>
        <nav aria-label="Legal and contact" className="flex flex-wrap items-center gap-4">
          <Link href="/privacy" className="transition hover:text-foreground">
            Privacy Policy
          </Link>
          <Link href="/terms" className="transition hover:text-foreground">
            Terms of Service
          </Link>
          <a href="mailto:support@mail.notelib.app" className="transition hover:text-foreground">
            Contact
          </a>
        </nav>
      </div>
    </footer>
  );
}
