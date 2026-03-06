import Link from "next/link";
import Image from "next/image";
import { ThemeToggle } from "./theme-toggle";

export function Navbar() {
  return (
    <header className="border-b border-border bg-background/95 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-5xl items-center justify-between px-6">
        <Link href="/" className="flex items-center gap-3">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-border">
            <Image
              src="/study-snap-logo-icon.svg"
              alt="Study Snap logo"
              width={20}
              height={20}
              priority
            />
          </span>
          <span className="text-sm font-semibold sm:text-base">Study Snap</span>
        </Link>
        <div className="flex items-center gap-4">
          <Link
            href="/dashboard"
            className="text-sm text-foreground/80 transition-colors hover:text-foreground"
          >
            Dashboard
          </Link>
          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
