import Link from "next/link";
import { BrandFullLogo } from "@/components/branding/brand-assets";

type PublicFooterProps = {
  className?: string;
};

export function PublicFooter({ className }: Readonly<PublicFooterProps>) {
  return (
    <footer className={`border-t border-border/70 pt-6 text-sm text-foreground/70 ${className ?? ""}`}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-2">
          <BrandFullLogo width={176} height={36} />
          <p>NoteLib helps students turn notes into study packs, summaries, and quizzes.</p>
        </div>
        <nav aria-label="Public links and legal" className="flex flex-wrap items-center gap-4">
          <Link href="/how-it-works" className="transition hover:text-foreground">
            How it Works
          </Link>
          <Link href="/privacy" className="transition hover:text-foreground">
            Privacy Policy
          </Link>
          <Link href="/terms" className="transition hover:text-foreground">
            Terms of Service
          </Link>
          <Link href="/refund" className="transition hover:text-foreground">
            Refund Policy
          </Link>
          <a href="mailto:support@mail.notelib.app" className="transition hover:text-foreground">
            Contact
          </a>
        </nav>
      </div>
    </footer>
  );
}
