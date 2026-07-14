"use client";

import Link from "next/link";
import { Menu, X } from "lucide-react";
import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { BrandMonogram } from "./branding/brand-assets";
import { ThemeToggle } from "./theme-toggle";
import { buttonVariants } from "./ui/button";

const PUBLIC_NAV = [
  { href: "/", label: "Home" },
  { href: "/how-it-works", label: "How it Works" },
  { href: "/demo", label: "Demo" },
  { href: "/public/library", label: "Public Library" },
  { href: "/exam", label: "Exam Hubs" },
  { href: "/learn", label: "Learn" },
  { href: "/pricing", label: "Pricing" },
];

function isActivePublicRoute(currentPathname: string, href: string): boolean {
  if (href === "/") {
    return currentPathname === "/";
  }
  return currentPathname === href || currentPathname.startsWith(`${href}/`);
}

export function Navbar() {
  const pathname = usePathname();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const frameId = globalThis.requestAnimationFrame(() => {
      setMobileMenuOpen(false);
    });

    return () => globalThis.cancelAnimationFrame(frameId);
  }, [pathname]);

  return (
    <header className="sticky top-0 z-50 border-b border-border bg-background/95 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
        <Link href="/" className="flex min-w-0 items-center gap-3">
          <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-xl border border-border/70 bg-white shadow-sm">
            <BrandMonogram size={36} className="h-9 w-9" priority />
          </span>
          <span className="truncate whitespace-nowrap text-sm font-semibold sm:text-base">NoteLib</span>
        </Link>

        <div className="hidden items-center gap-4 md:flex">
          <nav className="flex items-center gap-2">
            {PUBLIC_NAV.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                aria-current={isActivePublicRoute(pathname, item.href) ? "page" : undefined}
                className={`inline-flex h-16 items-center border-b-2 px-2 text-sm font-medium transition-colors ${
                  isActivePublicRoute(pathname, item.href)
                    ? "border-blue-600 text-foreground dark:border-blue-400"
                    : "border-transparent text-foreground/80 hover:border-border hover:text-foreground"
                }`}
              >
                {item.label}
              </Link>
            ))}
          </nav>

          <div className="h-6 w-px bg-border/70" aria-hidden="true" />

          <div className="flex items-center">
            <ThemeToggle />
          </div>

          <div className="h-6 w-px bg-border/70" aria-hidden="true" />

          <div className="flex items-center gap-2">
            <Link
              href="/login"
              className={buttonVariants({ variant: "outline", size: "sm" })}
            >
              Login
            </Link>
            <Link href="/signup" className={buttonVariants({ size: "sm" })}>
              Get Started
            </Link>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2 md:hidden">
          {!mobileMenuOpen ? (
            <Link href="/signup" className={buttonVariants({ size: "sm", className: "px-3" })}>
              Get Started
            </Link>
          ) : null}
          <div className={`flex items-center gap-2 ${mobileMenuOpen ? "" : "border-l border-border pl-2"}`}>
            <ThemeToggle />
            <button
              type="button"
              className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border bg-background text-foreground transition-colors hover:bg-highlight"
              aria-label={mobileMenuOpen ? "Close navigation menu" : "Open navigation menu"}
              aria-expanded={mobileMenuOpen}
              onClick={() => setMobileMenuOpen((open) => !open)}
            >
              {mobileMenuOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
            </button>
          </div>
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
          <div className="motion-dropdown-panel absolute inset-x-4 top-full z-40 mt-3 rounded-2xl border border-border bg-background p-3 shadow-lg md:hidden">
            <div className="flex flex-col gap-1">
              {PUBLIC_NAV.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={isActivePublicRoute(pathname, item.href) ? "page" : undefined}
                  className={`motion-lift rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                    isActivePublicRoute(pathname, item.href)
                      ? "bg-highlight text-blue-700 dark:text-blue-200"
                      : "text-foreground/85 hover:bg-highlight hover:text-foreground active:bg-highlight-strong"
                  }`}
                  onClick={() => setMobileMenuOpen(false)}
                >
                  {item.label}
                </Link>
              ))}
            </div>

            <div className="my-3 border-t border-border" aria-hidden="true" />

            <div className="flex flex-col gap-2">
              <Link
                href="/login"
                className={buttonVariants({ variant: "outline", className: "w-full justify-center" })}
                onClick={() => setMobileMenuOpen(false)}
              >
                Login
              </Link>
              <Link
                href="/signup"
                className={buttonVariants({ className: "w-full justify-center" })}
                onClick={() => setMobileMenuOpen(false)}
              >
                Get Started
              </Link>
            </div>
          </div>
        </>
      ) : null}
    </header>
  );
}
