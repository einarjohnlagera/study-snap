"use client";

import Link from "next/link";
import Image from "next/image";
import { Menu, X } from "lucide-react";
import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { buttonVariants } from "./ui/button";

const PUBLIC_NAV = [
  { href: "/", label: "Home" },
  { href: "/learn", label: "Learn" },
  { href: "/pricing", label: "Pricing" },
  { href: "/login", label: "Log in" },
];

export function Navbar() {
  const pathname = usePathname();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    setMobileMenuOpen(false);
  }, [pathname]);

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
        <Link href="/" className="flex min-w-0 items-center gap-3">
          <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-border">
            <Image
              src="/notelib-logo-icon.svg"
              alt="NoteLib logo"
              width={20}
              height={20}
              priority
            />
          </span>
          <span className="truncate whitespace-nowrap text-sm font-semibold sm:text-base">NoteLib</span>
        </Link>

        <nav className="hidden items-center gap-5 md:flex">
          {PUBLIC_NAV.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="whitespace-nowrap text-sm text-foreground/80 transition-colors hover:text-foreground"
            >
              {item.label}
            </Link>
          ))}
          <Link href="/signup" className={buttonVariants({ size: "sm" })}>
            Get Started Free
          </Link>
        </nav>

        <div className="flex shrink-0 items-center gap-2 md:hidden">
          <Link href="/signup" className={buttonVariants({ size: "sm", className: "px-3" })}>
            Get Started
          </Link>
          <button
            type="button"
            className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border bg-background text-foreground transition-colors hover:bg-muted/50"
            aria-label={mobileMenuOpen ? "Close navigation menu" : "Open navigation menu"}
            aria-expanded={mobileMenuOpen}
            onClick={() => setMobileMenuOpen((open) => !open)}
          >
            {mobileMenuOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
          </button>
        </div>
      </div>

      {mobileMenuOpen ? (
        <>
          <button
            type="button"
            className="fixed inset-0 top-16 z-30 bg-black/30 md:hidden"
            aria-label="Close mobile navigation"
            onClick={() => setMobileMenuOpen(false)}
          />
          <div className="absolute inset-x-4 top-full z-40 mt-3 rounded-2xl border border-border bg-background p-3 shadow-lg md:hidden">
            <nav className="flex flex-col gap-1">
              {PUBLIC_NAV.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="rounded-lg px-3 py-2 text-sm font-medium text-foreground/85 transition-colors hover:bg-muted/50 hover:text-foreground"
                  onClick={() => setMobileMenuOpen(false)}
                >
                  {item.label}
                </Link>
              ))}
              <Link
                href="/signup"
                className={buttonVariants({ className: "mt-2 w-full justify-center" })}
                onClick={() => setMobileMenuOpen(false)}
              >
                Get Started Free
              </Link>
            </nav>
          </div>
        </>
      ) : null}
    </header>
  );
}
