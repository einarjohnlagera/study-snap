import Link from "next/link";
import { BrandFullLogo } from "@/components/branding/brand-assets";

type PublicFooterProps = {
  className?: string;
  /** compact: links only, no logo/description. Use on narrow pages like auth. */
  variant?: "full" | "compact";
};

function FacebookIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      className="h-4 w-4"
    >
      <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z" />
    </svg>
  );
}

function FooterLinks() {
  return (
    <nav aria-label="Public links and legal" className="flex flex-wrap items-center gap-4">
      <Link href="/how-it-works" className="transition hover:text-foreground">
        How it Works
      </Link>
      <Link href="/pricing" className="transition hover:text-foreground">
        Pricing
      </Link>
      <Link href="/exam" className="transition hover:text-foreground">
        Exam Hubs
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
      <a
        href="https://www.facebook.com/people/NoteLib/61575455371892/"
        target="_blank"
        rel="noopener noreferrer"
        aria-label="NoteLib on Facebook"
        className="transition hover:text-foreground"
      >
        <FacebookIcon />
      </a>
      <a href="mailto:support@mail.notelib.app" className="transition hover:text-foreground">
        Contact
      </a>
    </nav>
  );
}

export function PublicFooter({ className, variant = "full" }: Readonly<PublicFooterProps>) {
  if (variant === "compact") {
    return (
      <footer className={`border-t border-border/70 pt-6 text-sm text-foreground/70 ${className ?? ""}`}>
        <FooterLinks />
      </footer>
    );
  }

  return (
    <footer className={`border-t border-border/70 pt-6 text-sm text-foreground/70 ${className ?? ""}`}>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <BrandFullLogo width={176} height={36} />
          <p>NoteLib helps students turn notes into study packs, summaries, and quizzes.</p>
        </div>
        <FooterLinks />
      </div>
    </footer>
  );
}
